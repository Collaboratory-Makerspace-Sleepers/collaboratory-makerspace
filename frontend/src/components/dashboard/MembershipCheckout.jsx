import { useState, useEffect } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const PLAN_DETAILS = {
  MONTHLY:       { name: "Monthly Membership",  features: ["Equipment access at member rates","Access to common space","50% off classes","Access to events and discounts"] },
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

function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString("en-US", {
    month: "long", day: "numeric", year: "numeric",
  });
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

  const currentMembership = state?.currentMembership ?? null;
  const isPlanChange = !!currentMembership?.stripeSubscriptionId
    && plan?.billingInterval != null;

  const [changePreview, setChangePreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");

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

  useEffect(() => {
    if (!isPlanChange || !plan?.code) return;
    setPreviewLoading(true);
    setPreviewError("");
    setChangePreview(null);
    authFetch(`/api/v1/billing/me/subscription/plan-change-preview?planCode=${plan.code}`)
      .then((r) => {
        if (!r.ok) return r.json().then((b) => { throw new Error(b.message || "Could not load preview"); });
        return r.json();
      })
      .then((data) => setChangePreview(data))
      .catch((err) => setPreviewError(err.message || "Could not load plan change preview."))
      .finally(() => setPreviewLoading(false));
  }, [isPlanChange, plan?.code]);

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
      if (isPlanChange) {
        const res = await authFetch("/api/v1/billing/me/subscription/plan-change", {
          method: "POST",
          body: JSON.stringify({ planCode: plan.code }),
        });
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.message || "Could not change plan");
        }
        const banner = changePreview?.direction === "UPGRADE"
          ? `Plan upgraded to ${plan.name ?? plan.displayName}. The charge will appear in your billing history shortly.`
          : `Plan change scheduled. You'll switch to ${plan.name ?? plan.displayName} on ${formatDate(changePreview?.effectiveDate)}.`;
        navigate("/dashboard/membership", { state: { banner } });
      } else {
        const res = await authFetch("/api/v1/billing/checkout-session", {
          method: "POST",
          body: JSON.stringify({ planCode: plan.code, saveCard }),
        });
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(
            body.detail ||
              body.message ||
              (res.status === 502
                ? "The payment provider rejected checkout. Check the backend Stripe key and plan price configuration."
                : "Could not start checkout")
          );
        }
        const { sessionUrl } = await res.json();
        window.location.href = sessionUrl;
      }
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
          <p><strong>{plan.name ?? plan.displayName}</strong></p>
          <p>{plan.price ?? formatPrice(plan.amountCents, plan.billingInterval)}</p>
          <div className="colla5">
            {(plan.features ?? []).map((f) => (
              <p key={f}>{f}</p>
            ))}
          </div>
        </div>
      </div>

      {isPlanChange && (
        <div className="Dash3">
          <div className="Dash4">
            {previewLoading && <p style={{ color: "#666" }}>Calculating…</p>}
            {previewError && <p style={{ color: "red" }}>{previewError}</p>}
            {changePreview && changePreview.direction === "UPGRADE" && (
              <p>
                You'll be charged{" "}
                <strong>
                  {new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
                    changePreview.proratedAmountCents / 100
                  )}
                </strong>{" "}
                today (prorated for the remaining days in your billing period).
              </p>
            )}
            {changePreview && changePreview.direction === "DOWNGRADE" && (
              <p>
                Your plan will change to <strong>{plan.name ?? plan.displayName}</strong> on{" "}
                <strong>{formatDate(changePreview.effectiveDate)}</strong>. No charge today.
              </p>
            )}
          </div>
        </div>
      )}

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
        <button
          className="checkout-primary-button"
          onClick={handleConfirm}
          disabled={loading || (isPlanChange && previewLoading)}
        >
          {loading
            ? isPlanChange ? "Confirming…" : "Redirecting to payment…"
            : isPlanChange ? "Confirm plan change" : "Proceed to payment"}
        </button>
        <button className="checkout-secondary-button" onClick={() => navigate(-1)} disabled={loading}>
          Cancel
        </button>
      </div>
    </div>
  );
}