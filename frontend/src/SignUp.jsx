import React from "react";
import { Link } from "react-router-dom";

export default function SignUp() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p>Sign Up</p>
          <p>Welcome to the collaboratory!</p>
        </div>

        <button className="google">Sign up with Google</button>

        <div className="Or">
          <div className="line"></div>
          <p>Or</p>
          <div className="line"></div>
        </div>

        <div className="SignUpNames">
          <div className="inputColumn">
            <label>First Name</label>
            <input type="text" placeholder="John" />
          </div>

          <div className="inputColumn">
            <label>LastName</label>
            <input type="text" placeholder="Doe" />
          </div>
        </div>

        <div className="inputColumn">
          <label>Email</label>
          <input type="email" placeholder="Enter your email" />
        </div>

        <div className="SignUpNames">
          <div className="inputColumn">
            <label>Password</label>
            <input type="password" placeholder="Secret Password!" />
          </div>

          <div className="inputColumn">
            <label>Confirm Password</label>
            <input type="password" placeholder="Secret Password!" />
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

        <button>Sign In</button>

        <div className="sign-in">
          <p>Already a User? <Link to="/signin">Sign In here</Link></p>
        </div>
      </div>
    </div>
  );
}
