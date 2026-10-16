import { Link } from "react-router-dom";
import EmailOtpForm from "../components/EmailOtpForm";
import OAuthButtons from "../components/OAuthButtons";

export default function SignUp() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p className="signin-title">Sign Up</p>
          <p className="signin-subtitle">Welcome to the collaboratory!</p>
        </div>

        <OAuthButtons />

        <div className="Or">
          <div className="line"></div>
          <p>Or</p>
          <div className="line"></div>
        </div>

        <EmailOtpForm />

        <p className="signin-subtitle">
          By continuing, you agree to our{" "}
          <Link to="/terms" target="_blank" rel="noopener noreferrer">
            Terms of Service
          </Link>
          .
        </p>

        <div className="forgot">
          <p>
            Already a user? <Link to="/signin">Sign in here</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
