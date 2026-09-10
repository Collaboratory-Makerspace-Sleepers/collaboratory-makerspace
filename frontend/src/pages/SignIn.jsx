import OAuthButtons from "../components/OAuthButtons";
import EmailOtpForm from "../components/EmailOtpForm";

export default function SignIn() {
  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p>Sign In</p>
          <p>Welcome Back!</p>
        </div>

        <OAuthButtons />

        <div className="Or">
          <div className="line"></div>
          <p>Or use email</p>
          <div className="line"></div>
        </div>

        <EmailOtpForm />
      </div>
    </div>
  );
}