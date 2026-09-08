import React, { useState } from "react";
import { Link } from "react-router-dom";

export default function SignIn() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [user, setUser] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setUser(null);
    setLoading(true);
    try {
      const res = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || (res.status === 403 ? "Access denied" : "Sign in failed"));
      }
      setUser(data);
    } catch (err) {
      setError(err.message);
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

        <div className="Or">
          <div className="line"></div>
          <p>Or use email</p>
          <div className="line"></div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="inputs">
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
            <div className="inputColumn">
              <label>Password</label>
              <input
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          </div>

          <button type="submit" disabled={loading}>
            {loading ? "Signing in…" : "Sign In"}
          </button>
        </form>

        {error && <p style={{ color: "red" }}>{error}</p>}

        {user && (
          <div className="authResult">
            <p style={{ color: "green", fontWeight: "bold" }}>Signed in as {user.firstName} {user.lastName} ({user.email})</p>
            <pre>{JSON.stringify(user, null, 2)}</pre>
          </div>
        )}

        <div className="forgot">
          <p>Forgot Password?</p>
          <p>New User? <Link to="/signup">Sign up here</Link></p>
        </div>
      </div>
    </div>
  );
}