import { Link } from "react-router-dom";
import OAuthButtons from "../components/OAuthButtons";
import EmailOtpForm from "../components/EmailOtpForm";

export default function SignUp() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p>Sign Up</p>
          <p>Welcome to the collaboratory!</p>
        </div>

        <OAuthButtons />

        <div className="Or">
          <div className="line"></div>
          <p>Or use email</p>
          <div className="line"></div>
        </div>

        <div className="otp-hint">
          <p>
            Use your email to create an account. We’ll send you a one-time code —
            no password needed.
          </p>
        </div>
        <EmailOtpForm />

        <div className="sign-in">
          <p>Already a User? <Link to="/signin">Sign In here</Link></p>
        </div>
      </div>
    </div>
  );
}