import { Link, useNavigate } from "react-router-dom";

export default function TermsOfService() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <title>Terms of Service | The Collaboratory</title>
      <div className="secondDiv">
        {/* Navigation Bar */}
        <div className="NavBar">
          <Link to="/">
            <img src="/makerspace_logo.png" alt="The Collaboratory" style={{ height: "40px" }} />
          </Link>

          <div className="links">
            <Link to="/" className="nav-link">Home</Link>
            <a href="/#memberships" className="nav-link">Pricing</a>
            <button className="nav-btn" onClick={() => navigate("/signup")}>Sign Up</button>
            <button className="nav-btn" onClick={() => navigate("/signin")}>Sign In</button>
          </div>
        </div>

        {/* Content Container */}
        <div className="WhiteDiv WaiverContent">
          <section className="WaiverSection">
            <h2>Acknowledgment of Risk</h2>
            <p>
              I understand that operating or being near makerspace equipment involves inherent risks, including but not limited to burns, fire hazards, exposure to fumes, eye injury, and damage to personal property. I voluntarily assume all risks associated with the use of the makerspace and its equipment.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Training & Proper Use</h2>
            <p>
              I confirm that I have received proper training or instruction on the safe use of the equipment I intend to use, or I agree to seek assistance before operating any machinery. I will follow all posted rules, safety guidelines, and instructions provided by staff.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Personal Responsibility</h2>
            <p>
              I agree to use all equipment responsibly and only for its intended purpose. I will not operate machinery under the influence of drugs or alcohol, and I will wear required safety equipment at all times.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Materials & Safety Compliance</h2>
            <p>
              I understand that only approved materials may be used with makerspace equipment. I will not use hazardous, flammable, or prohibited materials and will confirm material safety if unsure.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Supervision & Access</h2>
            <p>
              I understand that access to equipment may be restricted and that supervision may be required. I agree to comply with all access policies.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Release of Liability</h2>
            <p>
              I hereby release, waive, and discharge Collaboratory Makerspace, its staff, affiliates, and representatives from any and all liability, claims, demands, or causes of action arising out of or related to any loss, damage, or injury, including serious injury or death, that may occur while using the facility or equipment.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Indemnification</h2>
            <p>
              I agree to indemnify and hold harmless Collaboratory Makerspace from any claims resulting from my actions, misuse of equipment, or violation of safety rules.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Medical Treatment Authorization</h2>
            <p>
              In the event of an emergency, I authorize staff to obtain medical treatment on my behalf if I am unable to do so.
            </p>
          </section>

          <section className="WaiverSection">
            <h2>Agreement & Understanding</h2>
            <p>
              I have read this waiver, fully understand its terms, and sign it freely and voluntarily. I understand that I am giving up certain legal rights.
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
                equipment, class bookings, and private studios.
              </p>
            </div>

            <div className="FootDivGrid">
              <Link to="/">Home</Link>
              <Link to="/signin">Sign In</Link>
              <a href="/#memberships">Pricing</a>
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