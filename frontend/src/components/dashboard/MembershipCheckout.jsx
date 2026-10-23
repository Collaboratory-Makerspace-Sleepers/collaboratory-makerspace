import { useState, useEffect } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const PLAN_DETAILS = {
  MONTHLY:       { name: "Monthly Membership",  features: ["Equipment access at member rates","Access to common space","50% off classes","Access to events and discounts"] },
  ANNUAL:        { name: "Annual Membership",   features: ["Equipment access at member rates","Access to common space","50% off classes","Access to events and discounts","Billed once per year"] },
  STUDENT:       { name: "Student Membership",  features: ["Equipment access at member rates","50% off classes","Access to events and discounts","Requires .edu email address"] },
  DAY_PASS:      { name: "Day Pass",            features: ["Full access for one day","Equipment at standard rates","Class access"] },
  STUDIO_SMALL:  { name: "Small Studio",        features: ["Private small maker studio","Machine rate waiver — equipment included","Access to common space","50% off classes","Access to events and discounts"] },
  STUDIO_MEDIUM: { name: "Medium Studio",       features: ["Private medium maker studio","Machine rate waiver — equipment included","Access to common space","50% off classes","Access to events and discounts"] },
  STUDIO_LARGE:  { name: "Large Studio",        features: ["Private large maker studio","Machine rate waiver — equipment included","Access to common space","50% off classes","Access to events and discounts"] },
};

function formatPrice(amountCents, billingInterval) {
  const amount = (amountCents / 100).toLocaleString("en-US", {
    style: "currency", currency: "USD", minimumFractionDigits: 0,
  });
  return billingInterval ? `${amount}/${billingInterval.toLowerCase()}` : amount;
}

export default function MembershipCheckout() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { authFetch, user } = useAuth();
  const isStudentEligible = user?.email?.toLowerCase().endsWith(".edu") ?? false;
  const [searchParams] = useSearchParams();

  const planCodeFromUrl = searchParams.get("plan");
  const [plan, setPlan] = useState(state?.plan ?? null);
  const [planLoading, setPlanLoading] = useState(!state?.plan && !!planCodeFromUrl);
  const [planError, setPlanError] = useState("");

  const [saveCard, setSaveCard] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (state?.plan || !planCodeFromUrl) return;
    fetch("/api/v1/billing/plans")
      .then((r) => { if (!r.ok) throw new Error(); return r.json(); })
      .then((plans) => {
        const match = plans.find((p) => p.code === planCodeFromUrl);
        if (!match) throw new Error("Plan not found");
        const details = PLAN_DETAILS[match.code] ?? { name: match.code, features: [] };
        setPlan({ ...match, ...details, price: formatPrice(match.amountCents, match.billingInterval) });
      })
      .catch(() => setPlanError("Could not load plan details."))
      .finally(() => setPlanLoading(false));
  }, [planCodeFromUrl, state?.plan]);

  if (planLoading) {
    return <div className="DashHome1"><p>Loading…</p></div>;
  }

  if (planError) {
    return (
      <div className="DashHome1">
        <p>{planError}</p>
        <button onClick={() => navigate("/dashboard/membership")}>Back to Membership</button>
      </div>
    );
  }

  if (!plan) {
    navigate("/dashboard/membership", { replace: true });
    return null;
  }

  if (plan.code === "STUDENT" && !isStudentEligible) {
    navigate("/dashboard/membership", { replace: true });
    return null;
  }

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
