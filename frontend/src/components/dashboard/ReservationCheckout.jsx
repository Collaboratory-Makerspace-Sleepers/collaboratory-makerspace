import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const PENDING_RESERVATION_KEY = "pendingReservationAfterPayment";
const PENDING_RESERVATION_TTL_MS = 2 * 60 * 60 * 1000;

function readPendingReservation() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(PENDING_RESERVATION_KEY));
    if (!saved) return null;
    if (Date.now() - saved.savedAt > PENDING_RESERVATION_TTL_MS) return null;
    return saved.reservation;
  } catch {
    return null;
  }
}

export default function ReservationCheckout() {
  const { state: routerState } = useLocation();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  // After a Stripe redirect, router state is gone — fall back to sessionStorage.
  const state = routerState ?? readPendingReservation();

  const [status, setStatus] = useState("idle"); // idle | confirming | success | needs_membership | error
  const [errorMsg, setErrorMsg] = useState("");
  const [daypassLoading, setDaypassLoading] = useState(false);

  if (!state?.equipmentId) {
    navigate("/dashboard/rentequipment", { replace: true });
    return null;
  }

  if (state.waiverRequired && !state.waiverSigned) {
    navigate(`/dashboard/rentequipment/${state.equipmentId}/waiver`, {
      replace: true,
      state,
    });
    return null;
  }

  const {
    equipmentId, equipmentName, startTime, endTime,
    displayDate, displayTime, hourlyRateCents, waiverSignatureId,
  } = state;

  const hours = (new Date(endTime) - new Date(startTime)) / (1000 * 60 * 60);
  const totalCents = (hourlyRateCents ?? 0) * hours;
  const isFree = totalCents === 0;
  const priceLabel = isFree ? "Free" : `$${(totalCents / 100).toFixed(2)}`;

  async function handleConfirm() {
    setStatus("confirming");
    setErrorMsg("");
    try {
      const res = await authFetch("/api/v1/reservations", {
        method: "POST",
        body: JSON.stringify({ equipmentId, startTime, endTime, waiverSignatureId }),
      });

      if (res.status === 402) {
        setStatus("needs_membership");
        return;
      }
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(
          body.message ||
            (res.status === 409
              ? "That time slot is no longer available. Please pick a different time."
              : "Unable to complete your reservation.")
        );
      }

      sessionStorage.removeItem(PENDING_RESERVATION_KEY);
      setStatus("success");
    } catch (err) {
      setErrorMsg(err.message || "Something went wrong.");
      setStatus("error");
    }
  }

  async function handleDayPass() {
    setErrorMsg("");
    setDaypassLoading(true);
    try {
      sessionStorage.setItem(PENDING_RESERVATION_KEY, JSON.stringify({
        reservation: state,
        savedAt: Date.now(),
      }));
      const res = await authFetch("/api/v1/billing/checkout-session", {
        method: "POST",
        body: JSON.stringify({ planCode: "DAY_PASS", saveCard: false }),
      });
      if (!res.ok) {
        sessionStorage.removeItem(PENDING_RESERVATION_KEY);
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not start checkout");
      }
      const { sessionUrl } = await res.json();
      window.location.href = sessionUrl;
    } catch (err) {
      setErrorMsg(err.message || "Something went wrong.");
      setDaypassLoading(false);
    }
  }

  if (status === "success") {
    return (
      <div className="DashHome1">
        <div className="Dash4 confirmation-banner">
          <p>
            Confirmed! Your reservation for <strong>{equipmentName}</strong> is
            booked for {displayDate} at {displayTime}.
          </p>
          <div className="Dash5">
            <button onClick={() => navigate("/dashboard/reservations")}>
              View my reservations
            </button>
            <button onClick={() => navigate("/dashboard/rentequipment")}>
              Back to equipment
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button onClick={() => navigate(-1)}>Back</button>
        <p>Confirm your reservation</p>
      </div>

      <div className="Dash3">
        <div className="Dash4">
          <p><strong>{equipmentName}</strong></p>
          <p>{displayDate}</p>
          <p>{displayTime}</p>
          <p><strong>{priceLabel}</strong>{!isFree && " estimated total"}</p>
          {state.requiresTraining && (
            <p>
              This machine requires training. Your reservation will be pending until you watch the
              training video in your account and confirm you watched it.
            </p>
          )}
        </div>
      </div>

      {status === "needs_membership" ? (
        <div className="Dash3">
          <div className="Dash4">
            <p>You don't have an active membership.</p>
            <p>Members reserve equipment at the member rate.</p>
          </div>
          {errorMsg && <p style={{ color: "red" }}>{errorMsg}</p>}
          <div className="Dash5 checkout-actions">
            <button
              onClick={() => {
                sessionStorage.setItem(PENDING_RESERVATION_KEY, JSON.stringify({
                  reservation: state,
                  savedAt: Date.now(),
                }));
                navigate("/dashboard/membership");
              }}
              disabled={daypassLoading}
            >
              Get a membership
            </button>
            <button
              className="checkout-primary-button"
              onClick={handleDayPass}
              disabled={daypassLoading}
            >
              {daypassLoading ? "Redirecting to payment…" : `Pay ${priceLabel} (day pass)`}
            </button>
          </div>
        </div>
      ) : (
        <div className="Dash5 checkout-actions">
          {status === "error" && <p style={{ color: "red" }}>{errorMsg}</p>}
          <button
            className="checkout-primary-button"
            onClick={handleConfirm}
            disabled={status === "confirming"}
          >
            {status === "confirming" ? "Confirming…" : "Confirm reservation"}
          </button>
          <button
            className="checkout-secondary-button"
            onClick={() => navigate(-1)}
            disabled={status === "confirming"}
          >
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}
