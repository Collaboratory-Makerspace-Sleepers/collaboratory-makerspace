import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const WAIVER_SECTIONS = [
  {
    title: "Acknowledgment of Risk",
    text: "I understand that operating or being near laser cutting equipment involves inherent risks, including but not limited to burns, fire hazards, exposure to fumes, eye injury, and damage to personal property. I voluntarily assume all risks associated with the use of the makerspace and its equipment.",
  },
  {
    title: "Training & Proper Use",
    text: "I confirm that I have received proper training or instruction on the safe use of laser cutting equipment, or I agree to seek assistance before operating any machinery. I will follow all posted rules, safety guidelines, and instructions provided by staff.",
  },
  {
    title: "Personal Responsibility",
    text: "I agree to use all equipment responsibly and only for its intended purpose. I will not operate machinery under the influence of drugs or alcohol, and I will wear required safety equipment at all times.",
  },
  {
    title: "Materials & Safety Compliance",
    text: "I understand that only approved materials may be used in the laser cutter. I will not cut hazardous, flammable, or prohibited materials and will confirm material safety if unsure.",
  },
  {
    title: "Supervision & Access",
    text: "I understand that access to the laser cutter may be restricted and that supervision may be required. I agree to comply with all access policies.",
  },
  {
    title: "Release of Liability",
    text: "I hereby release, waive, and discharge Collaboratory Makerspace, its staff, affiliates, and representatives from any and all liability, claims, demands, or causes of action arising out of or related to any loss, damage, or injury, including serious injury or death, that may occur while using the facility or equipment.",
  },
  {
    title: "Indemnification",
    text: "I agree to indemnify and hold harmless Collaboratory Makerspace from any claims resulting from my actions, misuse of equipment, or violation of safety rules.",
  },
  {
    title: "Medical Treatment Authorization",
    text: "In the event of an emergency, I authorize staff to obtain medical treatment on my behalf if I am unable to do so.",
  },
  {
    title: "Agreement & Understanding",
    text: "I have read this waiver, fully understand its terms, and sign it freely and voluntarily. I understand that I am giving up certain legal rights.",
  },
];

export default function LaserWaiver() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { id } = useParams();
  const { authFetch } = useAuth();
  const [signerName, setSignerName] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [signature, setSignature] = useState(null);
  const [signing, setSigning] = useState(false);
  const [signError, setSignError] = useState("");

  if (!state?.equipmentId || !state.trainingRequired) {
    navigate(`/dashboard/rentequipment/${id}`, { replace: true });
    return null;
  }

  async function handleSign(event) {
    event.preventDefault();
    const name = signerName.trim();
    if (!name || !agreed) return;
    setSigning(true);
    setSignError("");
    try {
      const response = await authFetch(`/api/v1/training-waivers/${state.equipmentId}/sign`, {
        method: "POST",
        body: JSON.stringify({ signerName: name }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || "Could not save your waiver signature.");
      }
      setSignature({ name, signedAt: new Date().toISOString() });
    } catch (err) {
      setSignError(err.message || "Could not save your waiver signature.");
    } finally {
      setSigning(false);
    }
  }

  function handleContinueToCheckout() {
    if (!signature) return;
    navigate(`/dashboard/rentequipment/${state.equipmentId}/checkout`, {
      state: { ...state, waiverSigned: true, waiverSignature: signature },
    });
  }

  return (
    <div className="DashHome1 laser-waiver-page">
      <div className="ReserveHeader">
        <button onClick={() => navigate(-1)}>Back</button>
        <p>Laser Cutting Waiver</p>
        <p>Read the waiver and sign before continuing your reservation.</p>
      </div>

      <article className="laser-waiver-document">
        <h1>Laser Cutting Waiver</h1>
        <p className="laser-waiver-intro">
          By signing this waiver, I acknowledge and agree to the following:
        </p>

        <div className="laser-waiver-sections">
          {WAIVER_SECTIONS.map(({ title, text }) => (
            <section className="laser-waiver-section" key={title}>
              <h2>{title}</h2>
              <p>{text}</p>
            </section>
          ))}
        </div>

        <form className="laser-waiver-signature" onSubmit={handleSign}>
          <label htmlFor="waiver-signer-name">Full legal name</label>
          <input
            autoComplete="name"
            id="waiver-signer-name"
            onChange={(event) => setSignerName(event.target.value)}
            readOnly={Boolean(signature)}
            required
            value={signature?.name ?? signerName}
          />
          <label className="laser-waiver-agreement">
            <input
              checked={agreed}
              disabled={Boolean(signature)}
              onChange={(event) => setAgreed(event.target.checked)}
              type="checkbox"
            />
            <span>I have read and agree to this waiver.</span>
          </label>
          <button disabled={!signerName.trim() || !agreed || Boolean(signature) || signing} type="submit">
            {signing ? "Saving signature…" : signature ? "Signed electronically" : "Sign electronically"}
          </button>
          {signError && <p className="training-task-error" role="alert">{signError}</p>}
          {signature && (
            <p className="laser-waiver-signed" role="status">
              Signed by {signature.name} on {new Date(signature.signedAt).toLocaleString()}.
            </p>
          )}
        </form>
      </article>

      <button
        className="laser-waiver-finish"
        disabled={!signature}
        onClick={handleContinueToCheckout}
      >
        Continue to checkout
      </button>
    </div>
  );
}