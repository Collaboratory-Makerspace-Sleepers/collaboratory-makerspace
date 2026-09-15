import { Link, useNavigate } from "react-router-dom";
import "../App.css";

export default function Pricing() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <div className="secondDiv">
        <div className="NavBar">
          <p>The Collaboratory</p>

          <div className="links">
            <Link to="/">Home</Link>
            <Link to="/pricing">Pricing</Link>
            <button onClick={() => navigate("/signup")}>Sign Up</button>
            <button onClick={() => navigate("/signin")}>Sign in</button>
          </div>
        </div>

        <div className="Colla">
          <p>Pricing</p>
          <p>Membership and rental pricing details coming soon.</p>
        </div>
      </div>
    </div>
  );
}
