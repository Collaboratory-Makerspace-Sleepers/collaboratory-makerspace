import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

function formatPrice(amountCents, billingInterval) {
  const dollars = (amountCents / 100).toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
  });
  if (!billingInterval) return dollars;
  return `${dollars}/${billingInterval}`;
}

export default function Pricing() {
  const navigate = useNavigate();
  const [plans, setPlans] = useState([]);
  const [plansError, setPlansError] = useState("");

  useEffect(() => {
    fetch("/api/v1/billing/plans")
      .then((res) => {
        if (!res.ok) throw new Error("Could not load plans");
        return res.json();
      })
      .then(setPlans)
      .catch(() => setPlansError("Could not load pricing. Please try again later."));
  }, []);

  return (
    <div className="bigdiv">
      <div className="secondDiv">
        <div className="NavBar">
          <p>The Collaboratory</p>

          <div className="links">
            <Link to="/">Home</Link>
            <Link to="/pricing">Pricing</Link>
            <button className="nav-btn" onClick={() => navigate("/signup")}>Sign Up</button>
            <button className="nav-btn" onClick={() => navigate("/signin")}>Sign in</button>
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
              <button className="btn-primary">Try for free today</button>
              <button className="btn-secondary" onClick={() => navigate("/signin")}>Sign in</button>
            </div>

            <div className="CollaR1">
              {plansError && <p>{plansError}</p>}
              {plans.map((plan) => (
                <div className="colla6" key={plan.code}>
                  <div className="collar2">
                    <div className="collar3">
                      <p>Membership</p>
                    </div>
                    <div className="colla4">
                      <p>{plan.displayName}</p>
                      <p>{formatPrice(plan.amountCents, plan.billingInterval)}</p>
                    </div>
                  </div>
                  <button className="btn-primary" onClick={() => navigate("/signin")}>Get started</button>
                </div>
              ))}
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
