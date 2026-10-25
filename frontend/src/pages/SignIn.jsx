import { useState } from "react";
import { Link } from "react-router-dom";
import EmailOtpForm from "../components/EmailOtpForm";
import CardSignInForm from "../components/CardSignInForm";
import OAuthButtons from "../components/OAuthButtons";

import { useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Short codes emitted by OAuth2FailureHandler. Deliberately vague on screen —
// the precise cause is a server-side concern and belongs in the logs.
const OAUTH_ERRORS = {
  redirect_mismatch:
    "Google rejected the return address. The redirect URI registered for this app does not match the one this server sent.",
  code_rejected:
    "Google did not accept the sign-in. This usually means the app is still in testing mode and your Google account is not listed as a test user.",
  id_token_invalid: "Google returned an identity token this app could not verify.",
  unknown_provider: "That sign-in provider is not configured on the server.",
  server: "The server hit an error while setting up your account.",
  failed: "Sign-in did not complete. Please try again.",
};

export default function SignIn() {
  const [params] = useSearchParams();
  const { authError } = useAuth();
  const [signInMode, setSignInMode] = useState("oauth"); // oauth | email | card
  const errorCode = params.get("error");
  const errorMessage = errorCode ? OAUTH_ERRORS[errorCode] ?? OAUTH_ERRORS.failed : null;

  return (
    <div className="bigdiv2">
      <div className="SignIn">
        {/* Header */}
        <div className="SignInText">
          <p className="signin-title">Sign In</p>
          <p className="signin-subtitle">Welcome back to the collaboratory!</p>
        </div>

        {(errorMessage || authError) && (
          <p className="auth-error" role="alert">
            {errorMessage || authError}
          </p>
        )}

        {/* Sign-in mode tabs */}
        <div className="signin-tabs">
          <button
            className={`tab ${signInMode === "oauth" ? "active" : ""}`}
            onClick={() => setSignInMode("oauth")}
          >
            OAuth
          </button>
          <button
            className={`tab ${signInMode === "email" ? "active" : ""}`}
            onClick={() => setSignInMode("email")}
          >
            Email
          </button>
          <button
            className={`tab ${signInMode === "card" ? "active" : ""}`}
            onClick={() => setSignInMode("card")}
          >
            Card
          </button>
        </div>

        {/* OAuth Sign In */}
        {signInMode === "oauth" && (
          <>
            <OAuthButtons />

            {/* Divider */}
            <div className="Or">
              <div className="line"></div>
              <p>Or</p>
              <div className="line"></div>
            </div>

            {/* Email OTP Form */}
            <EmailOtpForm redirectNewToSignUp />
          </>
        )}

        {/* Email OTP Sign In */}
        {signInMode === "email" && (
          <>
            <EmailOtpForm redirectNewToSignUp />
          </>
        )}

        {/* Card-based Sign In */}
        {signInMode === "card" && (
          <>
            <CardSignInForm />
          </>
        )}

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