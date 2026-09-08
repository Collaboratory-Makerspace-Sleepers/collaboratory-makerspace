import React from "react";
import { Link } from "react-router-dom";

export default function SignIn() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p>Sign In</p>
          <p>Welcome Back!</p>
        </div>

        <button className="google">Sign in with Google</button>

        <div className="Or">
          <div className="line"></div>
          <p>Or</p>
          <div className="line"></div>
        </div>

        <div className="inputs">
          <div className="inputColumn">
            <label>Email</label>
            <input type="email" placeholder="Enter your email" />
          </div>
          <div className="inputColumn">
            {" "}
            <label>Password</label>
            <input type="password" placeholder="Enter your password" />
          </div>
        </div>

        <button>Sign In</button>

        <div className="forgot">
          <p>Forgot Password?</p>
          <p>New User? <Link to="/signup">Sign up here</Link></p>
        </div>
      </div>
    </div>
  );
}
