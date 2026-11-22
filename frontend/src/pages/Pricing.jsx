import { Link, useNavigate } from "react-router-dom";
import MembershipComparison from "../components/MembershipComparison";

export default function Pricing() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <title>Pricing | The Collaboratory</title>
      <div className="secondDiv">
        <div className="NavBar">
          <Link to="/">
            <img src="/makerspace_logo.png" alt="The Collaboratory" style={{ height: "40px" }} />
          </Link>

          <div className="links">
            <Link to="/">Home</Link>
            <Link to="/pricing">Pricing</Link>
            <button onClick={() => navigate("/signup")}>Sign Up</button>
            <button onClick={() => navigate("/signin")}>Sign in</button>
          </div>
        </div>

        <div className="CollaD">
          <div className="PricingColla">
            <p>Collaboratory's Pricing</p>
            <p>
              The collaboratory is a makerspace that offers state-of-the-art
              equipment, class bookings, and private studios
            </p>
            <MembershipComparison />
          </div>
        </div>

        <div className="FootDiv">
          <div className="FootDiv12">
            <div className="FootDiv2">
              <p>The Collaboratory</p>
              <p>
                The collaboratory is a makerspace that offers state-of-the-art
                equipment, class bookings, and private studios.
              </p>
            </div>

            <div className="FootDivGrid">
              <Link to="/">Home</Link>
              <Link to="/signin">Sign in</Link>
              <a>Youtube</a>
              <a>About</a>
              <Link to="/signup">Sign Up</Link>
              <a>Facebook</a>
              <Link to="/pricing">Pricing</Link>
              <a>Contact</a>
              <a>Instagram</a>
            </div>
          </div>
          <div className="FootDiv3">
            <p>All Rights Reserved, 2026</p>
            <a>Back to Top</a>
          </div>
        </div>
      </div>
    </div>
  );
}
