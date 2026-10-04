import React from "react";
import { Link, useNavigate } from "react-router-dom";

export default function TermsOfService() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <div className="secondDiv">
        {/* Navigation Bar */}
        <div className="NavBar">
          <p className="logo" onClick={() => navigate("/")} style={{ cursor: "pointer" }}>
            The Collaboratory
          </p>

          <div className="links">
            <Link to="/" className="nav-link">Home</Link>
            <Link to="/pricing" className="nav-link">Pricing</Link>
            <button className="nav-btn" onClick={() => navigate("/signup")}>Sign Up</button>
            <button className="nav-btn" onClick={() => navigate("/signin")}>Sign In</button>
          </div>
        </div>

        {/* Header Title Section */}
        <div className="CollaD">
          <div className="PricingColla">
            <p className="section-title">Terms of Service</p>
            <p className="signin-subtitle">Last updated: June 2027 • Version 1.0</p>
          </div>
        </div>

        {/* Content Container */}
        <div className="WhiteDiv TermsContent">
          <section className="TermsSection">
            <h2>1. Memberships & Billing</h2>
            <p>
              Memberships at The Collaboratory renew automatically on a recurring monthly or annual basis depending on your selected plan. Memberships may be canceled at any time to prevent future automated renewals[cite: 2]. Cancellation stops future billing cycles but does not automatically issue a refund for the current billing period[cite: 2].
            </p>
          </section>

          <section className="TermsSection">
            <h2>2. Cancellation & Refund Policies</h2>
            <ul className="TermsList">
              <li><strong>Equipment Reservations:</strong> Cancellations made at least 24 hours prior to the scheduled start time are eligible for a full refund or account credit[cite: 2]. Cancellations within 24 hours are non-refundable[cite: 2].</li>
              <li><strong>Classes & Events:</strong> Cancellations made at least 48 hours prior to the event are eligible for a refund or credit[cite: 2]. If The Collaboratory cancels a class or event, you will receive a full refund or credit[cite: 2].</li>
              <li><strong>Exceptions:</strong> Partial-month refunds are generally not provided[cite: 2]. Exceptions may be made at the discretion of management[cite: 2].</li>
            </ul>
          </section>

          <section className="TermsSection">
            <h2>3. Reservations & No-Show Policy</h2>
            <p>
              Reservations can be made up to 30 days in advance and up to 1 hour before the start time[cite: 2]. Members may hold up to 3 concurrent future equipment reservations[cite: 2].
            </p>
            <div className="TermsNotice">
              <strong>No-Show Rule:</strong> If you do not check in within 15 minutes of your scheduled reservation start time, your slot will be considered a no-show and may be released to other members[cite: 2].
            </div>
          </section>

          <section className="TermsSection">
            <h2>4. Safety & Certifications</h2>
            <ul className="TermsList">
              <li><strong>Age Requirements:</strong> Independent membership requires users to be at least 18 years old[cite: 2]. Minor accounts require a parent or legal guardian signature on all liability waivers[cite: 2].</li>
              <li><strong>Certifications:</strong> Certain specialized equipment requires safety training prior to booking[cite: 2]. Operating restricted machinery without active certifications or staff authorization is strictly prohibited[cite: 2].</li>
            </ul>
          </section>

          <section className="TermsSection">
            <h2>5. Operating Hours</h2>
            <p>
              General makerspace access is available 24/7 for qualified members[cite: 2]. Equipment requiring specific training or oversight is available from 6:00 AM to 11:00 PM, 7 days a week[cite: 2].
            </p>
          </section>
        </div>

        {/* Footer */}
        <div className="FootDiv">
          <div className="FootDiv12">
            <div className="FootDiv2">
              <h2 className="footer-logo">The Collaboratory</h2>
              <p className="footer-description">
                The collaboratory is a makerspace that offers state-of-the-art
                equipment, class bookings, and private studios[cite: 6].
              </p>
            </div>

            <div className="FootDivGrid">
              <Link to="/">Home</Link>
              <Link to="/signin">Sign In</Link>
              <Link to="/pricing">Pricing</Link>
              <Link to="/terms">Terms of Service</Link>
            </div>
          </div>

          <div className="FootDiv3">
            <p>All Rights Reserved, 2026</p>
            <a href="#top">Back to Top</a>
          </div>
        </div>
      </div>
    </div>
  );
}