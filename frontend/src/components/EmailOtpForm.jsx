import { useEffect, useId, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const RESEND_COOLDOWN_MS = 60_000;

/**
 * Passwordless email sign-in: request a one-time code, then exchange it for a
 * session token.
 *
 * `codeAlreadySent` lets registration continue to verification without
 * requesting a second code and triggering the resend cooldown.
 */
export default function EmailOtpForm({
  initialEmail = "",
  codeAlreadySent = false,
  redirectNewToSignUp = false,
}) {
  const location = useLocation();
  const startingEmail = initialEmail || location.state?.email || "";
  const [email, setEmail] = useState(startingEmail);
  const [code, setCode] = useState("");
  const [step, setStep] = useState(codeAlreadySent ? "code" : "email");
  const [error, setError] = useState("");
  const [message, setMessage] = useState(
    codeAlreadySent ? "A one-time code was sent to your email." : "",
  );
  const [pending, setPending] = useState(null);
  const [now, setNow] = useState(Date.now);
  const [cooldowns, setCooldowns] = useState(() => codeAlreadySent
    ? { [startingEmail.trim().toLowerCase()]: Date.now() + RESEND_COOLDOWN_MS }
    : {});
  const fieldId = useId();
  const { setToken } = useAuth();
  const navigate = useNavigate();
  const from = location.state?.from?.pathname ?? "/dashboard";
  const emailKey = email.trim().toLowerCase();
  const resendAt = cooldowns[emailKey] ?? 0;
  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));

  useEffect(() => {
    if (!resendAt) return;
    const timer = setInterval(() => {
      const currentTime = Date.now();
      setNow(currentTime);
      if (currentTime >= resendAt) clearInterval(timer);
    }, 250);
    return () => clearInterval(timer);
  }, [resendAt]);

  function startCooldown() {
    const currentTime = Date.now();
    setNow(currentTime);
    setCooldowns((current) => ({ ...current, [emailKey]: currentTime + RESEND_COOLDOWN_MS }));
  }

  async function handleRequest(e) {
    e.preventDefault();
    if (pending) return;
    if (resendAt > Date.now()) {
      setStep("code");
      setError("");
      setMessage("Enter the code you already received, or resend it when the timer ends.");
      return;
    }
    setError("");
    setMessage("");
    setPending("send");
    try {
      if (redirectNewToSignUp && step === "email") {
        const check = await fetch("/api/v1/auth/otp/exists", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email }),
        });
        if (check.ok && !(await check.json()).exists) {
          navigate("/signup", { state: { email } });
          return;
        }
      }
      const res = await fetch("/api/v1/auth/otp/send", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const data = await res.json().catch(() => null);
      if (res.status === 429) {
        startCooldown();
        setStep("code");
        setMessage("A code was requested recently. Enter it below if you received it, or try again when the timer ends.");
        return;
      }
      if (res.status === 410) {
        navigate("/account-closed");
        return;
      }
      if (!res.ok) {
        const message = data?.message || (res.status >= 500
            ? "The server could not send a code. Check that its Redis and email services are available."
            : "Could not send a code to that email.");
        throw new Error(message);
      }
      startCooldown();
      setStep("code");
      setCode("");
      setMessage("A one-time code was sent to your email.");
    } catch (err) {
      setError(err.message);
    } finally {
      setPending(null);
    }
  }

  async function handleVerify(e) {
    e.preventDefault();
    if (pending) return;
    setError("");
    setPending("verify");
    try {
      const res = await fetch("/api/v1/auth/otp/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code }),
      });
      const data = await res.json().catch(() => null);
      if (res.status === 410) {
        navigate("/account-closed");
        return;
      }
      if (!res.ok) {
        throw new Error(data?.message || (res.status >= 500
          ? "The server could not verify your code. Please try again."
          : "That code is invalid or expired. Request a new one and try again."));
      }
      setToken(data.access_token);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setPending(null);
    }
  }

  if (step === "code") {
    return (
      <form onSubmit={handleVerify} className="otp-form">
        {message && <p className="otp-message" role="status">{message}</p>}
        <div className="inputColumn">
          <label htmlFor={fieldId}>One-Time Code</label>
          <input
            id={fieldId}
            type="text"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            placeholder="6-digit code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            required
          />
        </div>
        <div className="sign-in">
          <button type="submit" disabled={Boolean(pending)} className="signButton">
            {pending === "verify" ? "Signing in…" : "Continue"}
          </button>
        </div>
        <button type="button" className="otp-back" disabled={Boolean(pending) || secondsLeft > 0}
          onClick={handleRequest}>
          {pending === "send" ? "Sending code…" : secondsLeft > 0
            ? `Resend code in ${secondsLeft}s`
            : "Resend code"}
        </button>
        <button
          type="button"
          className="otp-back"
          disabled={Boolean(pending)}
          onClick={() => {
            setStep("email");
            setCode("");
            setMessage("");
            setError("");
          }}
        >
          Use a different email
        </button>
        {error && <p style={{ color: "red" }}>{error}</p>}
      </form>
    );
  }

  return (
    <form onSubmit={handleRequest} className="otp-form">
      <div className="inputColumn">
        <label htmlFor={fieldId}>Email</label>
        <input
          id={fieldId}
          type="email"
          placeholder="Enter your email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={Boolean(pending)}
          required
        />
      </div>
      <div className="sign-in">
        <button type="submit" disabled={Boolean(pending)} className="signButton">
          {pending === "send" ? "Sending code…" : secondsLeft > 0 ? "Enter existing code" : "Send Code"}
        </button>
      </div>
      {error && <p style={{ color: "red" }}>{error}</p>}
    </form>
  );
}