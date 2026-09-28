import { Link } from "react-router-dom";
import EmailOtpForm from "../components/EmailOtpForm";
import OAuthButtons from "../components/OAuthButtons";

export default function SignIn() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        {/* Header */}
        <div className="SignInText">
          <p className="signin-title">Sign In</p>
          <p className="signin-subtitle">Welcome back to the collaboratory!</p>
        </div>

        {/* OAuth Sign In */}
        <OAuthButtons />

        {/* Divider */}
        <div className="Or">
          <div className="line"></div>
          <p>Or</p>
          <div className="line"></div>
        </div>

        {/* OTP Form */}
        <EmailOtpForm />

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