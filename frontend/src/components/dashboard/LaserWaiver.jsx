import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

import { WAIVER_SECTIONS } from "./waiverSections";

const POLL_MS = 4000;
const RABBITSIGN_URL =
  import.meta.env.VITE_RABBITSIGN_WAIVER_URL ||
  "https://www.rabbitsign.com/template/link/SzNLl1tmVh80eu086cmInx";

export default function LaserWaiver() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { id } = useParams();
  const { authFetch } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [request, setRequest] = useState(null);
  const [signError, setSignError] = useState("");
  const equipmentId = state?.equipmentId;
  const slotStart = state?.startTime;
  const status = request?.status;
  const awaitingStaff = status === "WAIVER_SIGNED" || status === "NEEDS_WAIVER";

  const refresh = useCallback(async () => {
    if (!equipmentId) return;
    try {
      const query = slotStart ? `?start=${encodeURIComponent(slotStart)}` : "";
      const res = await authFetch(`/api/v1/hardware/checkins/me/equipment/${equipmentId}${query}`);
      if (res.status === 204) setRequest(null);
      else if (res.ok) setRequest(await res.json());
    } catch {
      // the next poll retries
    }
  }, [authFetch, equipmentId, slotStart]);

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
    ? { name: request?.signedName || "RabbitSign", signedAt: request?.signedAt }
    : null;

  async function handleOpenRabbitSign() {
    if (submitting) return;
    setSignError("");
    setSubmitting(true);
    // Open synchronously so the browser doesn't block the popup.
    window.open(RABBITSIGN_URL, "_blank", "noopener,noreferrer");
    try {
      const res = await authFetch("/api/v1/hardware/waiver-requests", {
        method: "POST",
        body: JSON.stringify({
          equipmentId: state.equipmentId,
          startTime: state.startTime,
          endTime: state.endTime,
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setSignError(data.message || "Could not submit the waiver. Please try again.");
        return;
      }
      setRequest(await res.json());
      // Keep the existing training-waiver record so checkout still sees the signature.
      await authFetch(`/api/v1/training-waivers/${state.equipmentId}/sign`, {
        method: "POST",
        body: JSON.stringify({ signerName: "RabbitSign" }),
      }).catch(() => {});
    } catch {
      setSignError("Could not reach the server. Please try again.");
    } finally {
      setSubmitting(false);
    }
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

        <div className="laser-waiver-signature">
          <p className="laser-waiver-hint">
            {state.equipmentName}
            {state.displayDate ? ` · ${state.displayDate}` : ""}
            {state.displayTime ? ` · ${state.displayTime}` : ""}
          </p>
          {!signed && !denied && (
            <button
              className="laser-waiver-rabbitsign"
              disabled={submitting}
              onClick={handleOpenRabbitSign}
              type="button"
            >
              {submitting ? "Opening…" : "Open the waiver in RabbitSign ↗"}
            </button>
          )}
          {signError && <p className="training-task-error" role="alert">{signError}</p>}
          {status === "WAIVER_SIGNED" && (
            <p className="laser-waiver-signed" role="status">
              Pending staff review. Finish signing in the RabbitSign tab if you haven't —
              this page updates automatically once staff approve.
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
        </div>
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
        disabled={!approved}
        onClick={handleContinueToCheckout}
      >
        {approved ? "Continue to checkout" : "Waiting for staff approval"}
      </button>
    </div>
  );
}