import React, { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const OAUTH_PROVIDERS = [
  { id: "google",    label: "Sign in with Google" },
  { id: "microsoft", label: "Sign in with Microsoft" },
  { id: "apple",     label: "Sign in with Apple" },
];

export default function SignIn() {
  const [step, setStep]       = useState("email"); // 'email' | 'code'
  const [email, setEmail]     = useState("");
  const [code, setCode]       = useState("");
  const [error, setError]     = useState("");
  const [loading, setLoading] = useState(false);

  const { setToken } = useAuth();
  const navigate     = useNavigate();
  const location     = useLocation();
  const from         = location.state?.from?.pathname ?? "/dashboard";

  async function handleSend(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await fetch("/api/v1/auth/otp/send", {
        method:  "POST",
        headers: { "Content-Type": "application/json" },
        body:    JSON.stringify({ email }),
      });
      if (res.status === 429) { setError("Please wait 60 seconds before requesting another code."); return; }
      if (!res.ok)            { setError("Failed to send code. Please try again."); return; }
      setStep("code");
    } catch {
      setError("Network error. Please try again.");
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
        method:      "POST",
        credentials: "include",
        headers:     { "Content-Type": "application/json" },
        body:        JSON.stringify({ email, code }),
      });
      if (res.status === 401) { setError("Invalid or expired code."); return; }
      if (res.status === 403) { setError("This account has been closed."); return; }
      if (!res.ok)            { setError("Something went wrong. Please try again."); return; }
      const data = await res.json();
      setToken(data.access_token);
      navigate(from, { replace: true });
    } catch {
      setError("Network error. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p>Sign In</p>
          <p>Welcome Back!</p>
        </div>

        {OAUTH_PROVIDERS.map(({ id, label }) => (
          <a key={id} href={`/oauth2/authorization/${id}`} className="google">
            {label}
          </a>
        ))}

        <div className="Or">
          <div className="line"></div>
          <p>Or use email</p>
          <div className="line"></div>
        </div>

        {step === "email" && (
          <form onSubmit={handleSend}>
            <div className="inputs">
              <div className="inputColumn">
                <label>Email</label>
                <input
                  type="email"
                  placeholder="Enter your email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoFocus
                />
              </div>
            </div>
            <div className="sign-in">
              <button type="submit" disabled={loading} className="signButton">
                {loading ? "Sending…" : "Send Code"}
              </button>
            </div>
          </form>
        )}

        {step === "code" && (
          <form onSubmit={handleVerify}>
            <div className="inputs">
              <div className="inputColumn">
                <label>Enter the 6-digit code sent to {email}</label>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="\d{6}"
                  maxLength={6}
                  placeholder="000000"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  required
                  autoFocus
                />
              </div>
            </div>
            <div className="sign-in">
              <button type="submit" disabled={loading} className="signButton">
                {loading ? "Verifying…" : "Sign In"}
              </button>
            </div>
            <div className="sign-in" style={{ paddingTop: "8px" }}>
              <button
                type="button"
                onClick={() => { setStep("email"); setCode(""); setError(""); }}
                style={{ background: "none", border: "none", cursor: "pointer", textDecoration: "underline" }}
              >
                Use a different email
              </button>
            </div>
          </form>
        )}

        {error && <p style={{ color: "red" }}>{error}</p>}

        <div className="forgot">
          <p>
            New User? <Link to="/signup">Sign up here</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
