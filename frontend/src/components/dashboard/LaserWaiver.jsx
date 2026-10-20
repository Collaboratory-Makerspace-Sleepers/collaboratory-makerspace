import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import RabbitSignaturePad from "../admin-dashboard/RabbitSignaturePad";

import { WAIVER_SECTIONS } from "./waiverSections";

const POLL_MS = 4000;

export default function LaserWaiver() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { id } = useParams();
  const { authFetch } = useAuth();
  const [signerName, setSignerName] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [showPad, setShowPad] = useState(false);
  const [request, setRequest] = useState(null);
  const [signError, setSignError] = useState("");
  const equipmentId = state?.equipmentId;
  const status = request?.status;
  const awaitingStaff = status === "WAIVER_SIGNED" || status === "NEEDS_WAIVER";

  const refresh = useCallback(async () => {
    if (!equipmentId) return;
    try {
      const res = await authFetch(`/api/v1/hardware/checkins/me/equipment/${equipmentId}`);
      if (res.status === 204) setRequest(null);
      else if (res.ok) setRequest(await res.json());
    } catch {
      // the next poll retries
    }
  }, [authFetch, equipmentId]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [refresh]);

  useEffect(() => {
    if (!awaitingStaff) return undefined;
    const timer = setInterval(() => {
      if (!document.hidden) refresh();
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [awaitingStaff, refresh]);

  if (!state?.equipmentId || !state.waiverRequired) {
    navigate(`/dashboard/rentequipment/${id}`, { replace: true });
    return null;
  }

  const signed = status === "WAIVER_SIGNED" || status === "WAIVER_APPROVED";
  const approved = status === "WAIVER_APPROVED";
  const denied = status === "DENIED";
  const signature = signed
    ? { name: signerName.trim() || "Signed", signedAt: new Date().toISOString() }
    : null;

  async function handleSigned(checkIn) {
    setShowPad(false);
    setRequest(checkIn);
    // Keep the existing training-waiver record so checkout still sees the signature.
    await authFetch(`/api/v1/training-waivers/${state.equipmentId}/sign`, {
      method: "POST",
      body: JSON.stringify({ signerName: signerName.trim() || "Signed with signature pad" }),
    }).catch(() => {});
  }

  function handleSign(event) {
    event.preventDefault();
    if (!signerName.trim() || !agreed) return;
    setSignError("");
    setShowPad(true);
  }

  function handleContinueToCheckout() {
    if (!approved) return;
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

      {status === "WAIVER_SIGNED" && (
        <div className="waiver-waiting-card" role="status">
          <div className="waiver-waiting-spinner" aria-hidden="true" />
          <h2>Waiting for staff approval</h2>
          <p>
            Your signed waiver was sent to an admin or staff member. Keep this page open —
            it updates automatically and unlocks checkout once approved.
          </p>
        </div>
      )}

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
            readOnly={signed}
            required
            value={signerName}
          />
          <label className="laser-waiver-agreement">
            <input
              checked={agreed || signed}
              disabled={signed}
              onChange={(event) => setAgreed(event.target.checked)}
              type="checkbox"
            />
            <span>I have read and agree to this waiver.</span>
          </label>
          <button disabled={!signerName.trim() || !agreed || signed || denied} type="submit">
            {signed ? "Signed" : "Sign with signature pad"}
          </button>
          {signError && <p className="training-task-error" role="alert">{signError}</p>}
          {status === "WAIVER_SIGNED" && (
            <p className="laser-waiver-signed" role="status">
              Waiver signed. Waiting for an admin or staff member to approve it — this page
              updates automatically. You can continue once it is approved.
            </p>
          )}
          {approved && (
            <p className="laser-waiver-signed" role="status">
              Approved by staff. You can continue to checkout.
            </p>
          )}
          {denied && (
            <p className="training-task-error" role="alert">
              Staff did not approve this waiver. Please see staff for help.
            </p>
          )}
        </form>
      </article>

      {showPad && (
        <div className="laser-waiver-pad-overlay" role="dialog" aria-modal="true">
          <RabbitSignaturePad
            submitUrl="/api/v1/hardware/waiver-requests"
            extraBody={{ equipmentId: state.equipmentId }}
            onSignatureSaved={handleSigned}
            onCancel={() => setShowPad(false)}
          />
        </div>
      )}

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
        disabled={!approved}
        onClick={handleContinueToCheckout}
      >
        {approved ? "Continue to checkout" : "Waiting for staff approval"}
      </button>
    </div>
  );
}