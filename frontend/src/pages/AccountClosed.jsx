import { Link } from "react-router-dom";

export default function AccountClosed() {
  return (
    <div className="bigdiv2">
      <title>Account Closed | The Collaboratory</title>
      <div className="SignIn">
        <div className="SignInText">
          <p className="signin-title">Account Closed</p>
          <p className="signin-subtitle">
            Your account has been closed. Contact staff to restore access.
          </p>
        </div>
        <div className="forgot" style={{ marginTop: "1.5rem" }}>
          <p>
            <Link to="/signin">Back to sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
