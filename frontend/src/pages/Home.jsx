import { Link, useNavigate } from "react-router-dom";
import MembershipComparison from "../components/MembershipComparison";

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <title>The Collaboratory</title>
      <div className="secondDiv">
        <div className="NavBar">
          <Link to="/">
            <img src="/makerspace_logo.png" alt="The Collaboratory" style={{ height: "40px" }} />
          </Link>

          <div className="links">
            <Link to="/" className="nav-link active">Home</Link>
            <a href="#memberships" className="nav-link">Pricing</a>
            <button className="nav-btn" onClick={() => navigate("/signup")}>Sign Up</button>
            <button className="nav-btn" onClick={() => navigate("/signin")}>Sign In</button>
          </div>
        </div>

        <div className="HeroSection">
          <img src="/makerspace_logo.png" alt="The Collaboratory" className="hero-logo" />

          <div className="Colla">
            <p className="hero-description">
              The collaboratory is a makerspace that offers state-of-the-art
              equipment, class bookings, and private studios
            </p>
            <div className="CollaRow">
              <button className="btn-primary" onClick={() => navigate("/signup")}>
                <span>↗</span> Try for free today
              </button>
              <button className="btn-secondary" onClick={() => navigate("/signin")}>
                <span>↗</span> Sign In
              </button>
            </div>
          </div>
        </div>

        <div className="WhiteDiv">
          <div className="WhiteDiv1">
            <div className="WhiteDivTextGroup">
              <h2 className="section-title">Rent Equipment, Book Classes</h2>
              <p className="WhiteDivText">
                The collaboratory is a makerspace where you can rent
                top-notch equipment at budget-friendly prices, along
                with options for class bookings and private studios.
              </p>
            </div>
            <button className="btn-primary" onClick={() => navigate("/signup")}>
              <span>↗</span> Start your project today
            </button>
          </div>

        </div>

        <div className="PrivateDiv">
          <div className="SquareRow">
            <div className="Square1"></div>
            <div className="Square2"></div>
          </div>
          <div className="MiddleDiv">
            <p className="section-title">Private Studios</p>
            <p className="MiddeText">
              The collaboratory is a vibrant makerspace where you can easily
              book classes to learn and create using top-notch equipment and
              private studios.
            </p>
            <button className="btn-primary" onClick={() => document.getElementById("memberships")?.scrollIntoView({ behavior: "smooth" })}>
              View Membership Options
            </button>
          </div>
          <div className="SquareRow">
            <div className="Square3"></div>
            <div className="Square4"></div>
          </div>
        </div>

        <div className="MembershipsDiv" id="memberships">
          <h2 className="section-title">Membership Options</h2>
          <MembershipComparison />
        </div>

        <div className="FootDiv">
          <div className="FootDiv12">
            <div className="FootDiv2">
              <h2 className="footer-logo">The Collaboratory</h2>
              <p className="footer-description">
                The collaboratory is a makerspace that offers state-of-the-art
                equipment, class bookings, and private studios
              </p>
            </div>

            <div className="FootDivGrid">
              {/* Column 1 */}
              <Link to="/">Home</Link>
              <a href="#about">About</a>
              <a href="#memberships">Pricing</a>

              {/* Column 2 */}
              <Link to="/signin">Sign In</Link>
              <Link to="/signup">Sign Up</Link>
              <a href="#contact">Contact</a>

              {/* Column 3 */}
              <a href="#youtube">Youtube</a>
              <a href="#facebook">Facebook</a>
              <a href="#instagram">Instagram</a>
            </div>
          </div>

          <div className="FootDiv3">
            <p>All Rights Reserved, 2026</p>
            <Link to="/terms">Terms of Service</Link>
            <a href="#top">Back to Top</a>
          </div>
        </div>
      </div>
    </div>
  );
}