import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

import { WAIVER_SECTIONS } from "./waiverSections";

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

  if (!state?.equipmentId || !state.waiverRequired) {
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
      <header className="laser-waiver-header">
        <button type="button" onClick={() => navigate(-1)}>↖ Back</button>
        <h1>Makerspace Equipment Waiver</h1>
        <p>Read the waiver and sign before continuing your reservation.</p>
      </header>

      <article className="laser-waiver-document">
        <div className="laser-waiver-box">
          <p className="laser-waiver-intro">
            By signing this waiver, I acknowledge and agree to the following:
          </p>
          <ol className="laser-waiver-sections">
            {WAIVER_SECTIONS.map(({ title, text }) => (
              <li className="laser-waiver-section" key={title}>
                <strong>{title}</strong>
                <span>{text}</span>
              </li>
            ))}
          </ol>
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

      <section className="laser-waiver-training">
        <h2>Equipment Training</h2>
        <p>Please watch the full training video. You will confirm it in your account.</p>
        <video
          controls
          preload="metadata"
          src={import.meta.env.VITE_LASER_TRAINING_VIDEO_URL || "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"}
        >
          Your browser does not support video playback.
        </video>
      </section>

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