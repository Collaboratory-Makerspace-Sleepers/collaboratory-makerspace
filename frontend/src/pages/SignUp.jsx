import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function SignUp() {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [newsletter, setNewsletter] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(false);

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const { setToken } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();

    setError("");

    if (!agreeTerms) {
      setError("You must agree to the terms & services.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const res = await fetch("/api/v1/auth/register", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          firstName,
          lastName,
          email,
          password,
          newsletter,
        }),
      });

      if (res.status === 409) {
        setError("An account with this email already exists.");
        return;
      }

      if (res.status === 400) {
        setError("Please check your information and try again.");
        return;
      }

      if (!res.ok) {
        setError("Something went wrong. Please try again.");
        return;
      }

      const data = await res.json();

      setToken(data.access_token);

      navigate("/dashboard", {
        replace: true,
      });
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
          <p className="signin-title">Sign Up</p>
          <p className="signin-subtitle">Welcome to the collaboratory!</p>
        </div>

        {/* Google Sign Up */}
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

        {/* Signup Form */}
        <form onSubmit={handleSubmit}>
          <div className="inputs">
            {/* First Name & Last Name (Side by Side) */}
            <div className="inputRow">
              <div className="inputColumn">
                <label htmlFor="firstName">First Name</label>
                <input
                  id="firstName"
                  type="text"
                  placeholder="John"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  required
                />
              </div>

              <div className="inputColumn">
                <label htmlFor="lastName">Last Name</label>
                <input
                  id="lastName"
                  type="text"
                  placeholder="Doe"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Email */}
            <div className="inputColumn">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                placeholder="johndoe@gmail.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            {/* Password & Confirm Password (Side by Side) */}
            <div className="inputRow">
              <div className="inputColumn">
                <label htmlFor="password">Password</label>
                <input
                  id="password"
                  type="password"
                  placeholder="Secret Password!"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </div>

              <div className="inputColumn">
                <label htmlFor="confirmPassword">Confirm Password</label>
                <input
                  id="confirmPassword"
                  type="password"
                  placeholder="Secret Password!"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </div>
            </div>

            {/* Checkboxes */}
            <div className="checkboxGroup">
              <label className="checkboxLabel">
                <input
                  type="checkbox"
                  checked={newsletter}
                  onChange={(e) => setNewsletter(e.target.checked)}
                />
                Sign Up for newsletter
              </label>

              <label className="checkboxLabel">
                <input
                  type="checkbox"
                  checked={agreeTerms}
                  onChange={(e) => setAgreeTerms(e.target.checked)}
                  required
                />
                Agree to terms & services
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

        {/* Footer Link */}
        <div className="forgot">
          <p>
            Already a user? <Link to="/signin">Sign in here</Link>
          </p>
        </div>
      </div>
    </div>
  );
}