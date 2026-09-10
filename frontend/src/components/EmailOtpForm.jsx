import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function EmailOtpForm() {
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState("email");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const { setToken } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from?.pathname ?? "/dashboard";

  async function handleRequest(e) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    try {
      const res = await fetch("/api/v1/auth/otp/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || "Could not send a code to that email");
      }
      setStep("code");
      setMessage("A one-time code was sent to your email.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await fetch("/api/v1/auth/otp/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || "Invalid or expired code");
      }
      setToken(data.access_token);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  if (step === "code") {
    return (
      <form onSubmit={handleVerify} className="otp-form">
        {message && <p className="otp-message">{message}</p>}
        <div className="inputColumn">
          <label>One-Time Code</label>
          <input
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
          <button type="submit" disabled={loading} className="signButton">
            {loading ? "Signing in…" : "Continue"}
          </button>
        </div>
        <button
          type="button"
          className="otp-back"
          onClick={() => {
            setStep("email");
            setCode("");
            setMessage("");
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
        <label>Email</label>
        <input
          type="email"
          placeholder="Enter your email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </div>
      <div className="sign-in">
        <button type="submit" disabled={loading} className="signButton">
          {loading ? "Sending code…" : "Send Code"}
        </button>
      </div>
      {error && <p style={{ color: "red" }}>{error}</p>}
    </form>
  );
}