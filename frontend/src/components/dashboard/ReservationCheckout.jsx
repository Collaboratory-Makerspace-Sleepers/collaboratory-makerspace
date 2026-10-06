import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const HOURLY_RATE = 7;

export default function ReservationCheckout() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [status, setStatus] = useState("idle"); // idle | confirming | success | needs_membership | error
  const [errorMsg, setErrorMsg] = useState("");

  // Guard: if navigated here directly without state, go back
  if (!state?.equipmentId) {
    navigate("/dashboard/rentequipment", { replace: true });
    return null;
  }

  if (state.trainingRequired && !state.waiverSigned) {
    navigate(`/dashboard/rentequipment/${state.equipmentId}/waiver`, {
      replace: true,
      state,
    });
    return null;
  }

  const { equipmentId, equipmentName, startTime, endTime, displayDate, displayTime } = state;

  const hours =
    (new Date(endTime) - new Date(startTime)) / (1000 * 60 * 60);
  const price = HOURLY_RATE * hours;

  async function handleConfirm() {
    setStatus("confirming");
    setErrorMsg("");
    try {
      const res = await authFetch("/api/v1/reservations", {
        method: "POST",
        body: JSON.stringify({ equipmentId, startTime, endTime }),
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

      setStatus("success");
    } catch (err) {
      setErrorMsg(err.message || "Something went wrong.");
      setStatus("error");
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

      {/* Booking summary */}
      <div className="Dash3">
        <div className="Dash4">
          <p><strong>{equipmentName}</strong></p>
          <p>{displayDate}</p>
          <p>{displayTime} · {hours} hr</p>
          <p><strong>${price.toFixed(2)}</strong> without membership</p>
        </div>
      </div>

      {/* Payment options */}
      {status === "needs_membership" ? (
        <div className="Dash3">
          <div className="Dash4">
            <p>You don't have an active membership.</p>
            <p>Members reserve equipment for free.</p>
          </div>
          <div className="Dash5 checkout-actions">
            <button onClick={() => navigate("/dashboard/membership")}>
              Get a membership
            </button>
            <button disabled title="Coming soon">
              Pay ${price.toFixed(2)}
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