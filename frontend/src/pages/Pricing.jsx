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

        <div className="CollaD">
          <div className="PricingColla">
            <p>Collaboratory's Pricing</p>
            <p>
              The collaboratory is a makerspace that offers state-of-the-art
              equipment, class bookings, and private studios
            </p>
            <div className="CollaR">
              <button>Try for free today</button>
              <button onClick={() => navigate("/signin")}>Sign in</button>
            </div>

            <div className="CollaR1">
              <div className="colla6">
                <div className="collar2">
                  <div className="collar3">
                    <p>Current Plan</p>
                  </div>

                  <div className="colla4">
                    <p>Guest</p>
                    <p>$0/Month</p>
                  </div>
                </div>
                <div className="colla5">
                  <p>Ability to rent equipment</p>
                  <p>Ability to book classes</p>
                  <p>Access to events and discounts</p>
                  <p>Rent Equipment for free</p>
                  <p>Book classes for free</p>
                  <p>Access to common space</p>
                  <p>Private studio space</p>
                </div>

                <button>Current Plan</button>
              </div>
              <div className="colla6">
                <div className="collar2">
                  <div className="collar3">
                    <p>Current Plan</p>
                  </div>

                  <div className="colla4">
                    <p>Guest</p>
                    <p>$0/Month</p>
                  </div>
                </div>
                <div className="colla5">
                  <p>Ability to rent equipment</p>
                  <p>Ability to book classes</p>
                  <p>Access to events and discounts</p>
                  <p>Rent Equipment for free</p>
                  <p>Book classes for free</p>
                  <p>Access to common space</p>
                  <p>Private studio space</p>
                </div>

                <button>Current Plan</button>
              </div>

              <div className="colla6">
                <div className="collar2">
                  <div className="collar3">
                    <p>Current Plan</p>
                  </div>

                  <div className="colla4">
                    <p>Guest</p>
                    <p>$0/Month</p>
                  </div>
                </div>
                <div className="colla5">
                  <p>Ability to rent equipment</p>
                  <p>Ability to book classes</p>
                  <p>Access to events and discounts</p>
                  <p>Rent Equipment for free</p>
                  <p>Book classes for free</p>
                  <p>Access to common space</p>
                  <p>Private studio space</p>
                </div>

                <button>Current Plan</button>
              </div>
            </div>
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
