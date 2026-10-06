import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function MembershipCheckout() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [saveCard, setSaveCard] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Guard: if navigated here directly without state, go back
  if (!state?.plan) {
    navigate("/dashboard/membership", { replace: true });
    return null;
  }

  const { plan } = state;

  async function handleConfirm() {
    setError("");
    setLoading(true);
    try {
      const res = await authFetch("/api/v1/billing/checkout-session", {
        method: "POST",
        body: JSON.stringify({ planCode: plan.code, saveCard }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not start checkout");
      }
      const { sessionUrl } = await res.json();
      window.location.href = sessionUrl;
    } catch (err) {
      setError(err.message || "Something went wrong.");
      setLoading(false);
    }
  }

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button onClick={() => navigate(-1)}>Back</button>
        <p>Review your plan</p>
      </div>

      <div className="Dash3">
        <div className="Dash4">
          <p><strong>{plan.name}</strong></p>
          <p>{plan.price}</p>
          <div className="colla5">
            {plan.features.map((f) => (
              <p key={f}>{f}</p>
            ))}
          </div>
        </div>
      </div>

      {plan.code === "DAY_PASS" && (
        <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.85rem" }}>
          <input
            type="checkbox"
            checked={saveCard}
            onChange={(e) => setSaveCard(e.target.checked)}
          />
          Save card for future payments
        </label>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}

      <div className="Dash5 checkout-actions">
        <button className="checkout-primary-button" onClick={handleConfirm} disabled={loading}>
          {loading ? "Redirecting to payment…" : "Proceed to payment"}
        </button>
        <button className="checkout-secondary-button" onClick={() => navigate(-1)} disabled={loading}>
          Cancel
        </button>
      </div>
    </div>
  );
}
