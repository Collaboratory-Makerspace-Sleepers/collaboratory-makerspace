import React, { useState } from "react";
import { Link } from "react-router-dom";

export default function SignUp() {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [user, setUser] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setUser(null);

    if (password !== confirm) {
      setError("Passwords do not match");
      return;
    }

    setLoading(true);
    try {
      const res = await fetch("/api/v1/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firstName, lastName, email, password }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || (res.status === 403 ? "Access denied" : "Sign up failed"));
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
          <p>Sign Up</p>
          <p>Welcome to the collaboratory!</p>
        </div>

        <div className="Or">
          <div className="line"></div>
          <p>Or use email</p>
          <div className="line"></div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="SignUpNames">
            <div className="inputColumn">
              <label>First Name</label>
              <input
                type="text"
                placeholder="John"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                required
              />
            </div>

            <div className="inputColumn">
              <label>Last Name</label>
              <input
                type="text"
                placeholder="Doe"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                required
              />
            </div>
          </div>

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

          <div className="SignUpNames">
            <div className="inputColumn">
              <label>Password</label>
              <input
                type="password"
                placeholder="Secret Password!"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                maxLength={72}
              />
            </div>

            <div className="inputColumn">
              <label>Confirm Password</label>
              <input
                type="password"
                placeholder="Secret Password!"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="checkRow">
            <div className="checkBox">
              <input type="checkbox" />
              <label>Sign Up for newsletter</label>
            </div>
            <div className="checkBox">
              <input type="checkbox" />
              <label>Agree to terms & services</label>
            </div>
          </div>

          <button type="submit" disabled={loading}>
            {loading ? "Signing up…" : "Sign Up"}
          </button>
        </form>

        {error && <p style={{ color: "red" }}>{error}</p>}

        {user && (
          <div className="authResult">
            <p style={{ color: "green", fontWeight: "bold" }}>Account created: {user.firstName} {user.lastName} ({user.email})</p>
            <pre>{JSON.stringify(user, null, 2)}</pre>
          </div>
        )}

        <div className="sign-in">
          <p>Already a User? <Link to="/signin">Sign In here</Link></p>
        </div>
      </div>
    </div>
  );
}