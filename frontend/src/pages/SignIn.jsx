import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function SignIn() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const { setToken } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const res = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          email,
          password,
          rememberMe,
        }),
      });

      if (res.status === 401) {
        setError("Invalid email or password.");
        return;
      }

      if (!res.ok) {
        setError("Something went wrong. Please try again.");
        return;
      }

      const data = await res.json();
      setToken(data.access_token);

      navigate("/dashboard", { replace: true });
    } catch {
      setError("Network error. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bigdiv2">
      <div className="SignIn">
        {/* Header */}
        <div className="SignInText">
          <p className="signin-title">Sign In</p>
          <p className="signin-subtitle">Welcome back to the collaboratory!</p>
        </div>

        {/* Google Sign In */}
        <a href="/oauth2/authorization/google" className="google">
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
          </svg>
          Sign in with Google
        </a>

        {/* Divider */}
        <div className="Or">
          <div className="line"></div>
          <p>Or</p>
          <div className="line"></div>
        </div>

        {/* Sign In Form */}
        <form onSubmit={handleSubmit}>
          <div className="inputs">
            {/* Email & Password Side-by-Side on Desktop */}
            <div className="inputRow">
              <div className="inputColumn">
                <label htmlFor="signInEmail">Email</label>
                <input
                  id="signInEmail"
                  type="email"
                  placeholder="johndoe@gmail.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <div className="inputColumn">
                <label htmlFor="signInPassword">Password</label>
                <input
                  id="signInPassword"
                  type="password"
                  placeholder="Secret Password!"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Checkbox */}
            <div className="checkboxGroup">
              <label className="checkboxLabel">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                Remember me
              </label>
            </div>
          </div>

          {/* Error Message */}
          {error && <p className="auth-error">{error}</p>}

          {/* Submit Button */}
          <div className="sign-in">
            <button
              type="submit"
              disabled={loading}
              className="signButton"
            >
              {loading ? "Signing in..." : "Sign In"}
            </button>
          </div>
        </form>

        {/* Footer Links */}
        <div className="forgot">
          <p>
            Need an account? <Link to="/signup">Sign up here</Link>
          </p>
        </div>
      </div>
    </div>
  );
}