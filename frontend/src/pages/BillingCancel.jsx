import { Link } from "react-router-dom";

export default function BillingCancel() {
  return (
    <div className="bigdiv2">
      <title>Billing Cancelled | The Collaboratory</title>
      <div className="SignIn">
        <div className="SignInText">
          <p className="signin-title">Checkout cancelled</p>
          <p className="signin-subtitle">
            No charge was made. You can start a new checkout whenever you're
            ready.
          </p>
        </div>
        <div className="sign-in">
          <Link to="/dashboard/membership">
            <button className="signButton">Back to membership</button>
          </Link>
        </div>
      </div>
    </div>
  );
}