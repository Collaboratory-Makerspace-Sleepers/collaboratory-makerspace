import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

// Feature bullets are UI copy — not data — so they stay hardcoded here.
const PLAN_FEATURES = {
  MONTHLY: [
    "Equipment access at member rates",
    "Access to common space",
    "50% off classes",
    "Access to events and discounts",
  ],
  STUDENT: [
    "Equipment access at member rates",
    "50% off classes",
    "Access to events and discounts",
    "Requires .edu email address",
  ],
  DAY_PASS: [
    "Full access for one day",
    "Equipment at standard rates",
    "Class access",
  ],
  STUDIO_SMALL: [
    "Private small maker studio",
    "Machine rate waiver — equipment included",
    "Access to common space",
    "50% off classes",
    "Access to events and discounts",
  ],
  STUDIO_MEDIUM: [
    "Private medium maker studio",
    "Machine rate waiver — equipment included",
    "Access to common space",
    "50% off classes",
    "Access to events and discounts",
  ],
  STUDIO_LARGE: [
    "Private large maker studio",
    "Machine rate waiver — equipment included",
    "Access to common space",
    "50% off classes",
    "Access to events and discounts",
  ],
};

function formatPrice(amountCents, billingInterval) {
  const amount = (amountCents / 100).toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
  });
  return billingInterval ? `${amount}/${billingInterval.toLowerCase()}` : amount;
}

const STATUS_LABELS = {
  ACTIVE: "Active",
  TRIALING: "Trial",
  PAST_DUE: "Past due",
  GRACE: "Grace period",
  CANCELED: "Canceled",
  INCOMPLETE: "Incomplete",
  PAUSED: "Paused",
};

const STATUS_COLORS = {
  ACTIVE: { color: "green" },
  TRIALING: { color: "green" },
  PAST_DUE: { color: "orange" },
  GRACE: { color: "orange" },
  CANCELED: { color: "red" },
  INCOMPLETE: { color: "red" },
  PAUSED: { color: "gray" },
};

export default function Membership() {
  const { authFetch, user } = useAuth();

  const isStudentEligible = user?.email?.toLowerCase().endsWith(".edu") ?? false;
  const location = useLocation();
  const navigate = useNavigate();

  const [membership, setMembership] = useState(undefined); // undefined = loading, null = none
  const [membershipError, setMembershipError] = useState("");
  const [plans, setPlans] = useState([]);
  const [plansError, setPlansError] = useState("");
  const [checkoutLoading, setCheckoutLoading] = useState(null);
  const [checkoutError, setCheckoutError] = useState("");
  const [cancelConfirm, setCancelConfirm] = useState(false);

  const banner = location.state?.banner;

  function loadMembership() {
    setMembership(undefined);
    setMembershipError("");
    authFetch("/api/v1/billing/me/subscription")
      .then((res) => {
        if (res.status === 404) return null;
        if (!res.ok) throw new Error("Could not load membership");
        return res.json();
      })
      .then((data) => setMembership(data))
      .catch(() => setMembershipError("Could not load membership status."));
  }

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/billing/me/subscription")
      .then((res) => {
        if (res.status === 404) return null;
        if (!res.ok) throw new Error("Could not load membership");
        return res.json();
      })
      .then((data) => {
        if (cancelled) return;
        setMembership(data);
      })
      .catch(() => {
        if (cancelled) return;
        setMembershipError("Could not load membership status.");
      });
    return () => { cancelled = true; };
  }, [authFetch]);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/v1/billing/plans")
      .then((res) => {
        if (!res.ok) throw new Error("Could not load plans");
        return res.json();
      })
      .then((data) => {
        if (!cancelled) setPlans(data);
      })
      .catch(() => {
        if (!cancelled) setPlansError("Could not load membership prices.");
      });
    return () => { cancelled = true; };
  }, []);

  const hasActiveMembership =
    membership &&
    ["ACTIVE", "TRIALING", "PAST_DUE"].includes(membership.status);

  function handleUpgrade(plan) {
    navigate("/dashboard/membership/checkout", { state: { plan, currentMembership: membership } });
  }

  async function handleCancel() {
    setCheckoutError("");
    setCheckoutLoading("cancel");
    try {
      const res = await authFetch("/api/v1/billing/cancel", { method: "POST" });
      if (!res.ok) throw new Error("Could not cancel membership");
      setMembership((m) => ({ ...m, cancelAtPeriodEnd: true }));
      setCancelConfirm(false);
    } catch (err) {
      setCheckoutError(err.message || "Something went wrong.");
    } finally {
      setCheckoutLoading(null);
    }
  }

  return (
    <div className="DashHome1">
      {cancelConfirm && (
        <div
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", zIndex: 999, display: "flex", alignItems: "center", justifyContent: "center" }}
          onClick={() => setCancelConfirm(false)}
        >
          <div
            style={{ background: "#fff", borderRadius: "8px", padding: "1.5rem", maxWidth: "380px", width: "90%", display: "flex", flexDirection: "column", gap: "1rem" }}
            onClick={(e) => e.stopPropagation()}
          >
            <p style={{ fontWeight: 600, margin: 0 }}>Cancel membership?</p>
            <p style={{ margin: 0, color: "#555", fontSize: "0.9rem" }}>
              Your access continues until{" "}
              {membership?.currentPeriodEnd
                ? new Date(membership.currentPeriodEnd).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })
                : "the end of your billing period"}.
            </p>
            {checkoutError && <p style={{ color: "red", margin: 0, fontSize: "0.85rem" }}>{checkoutError}</p>}
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
              <button onClick={() => setCancelConfirm(false)} disabled={!!checkoutLoading}>
                Keep membership
              </button>
              <button
                onClick={handleCancel}
                disabled={checkoutLoading === "cancel"}
                style={{ color: "red" }}
              >
                {checkoutLoading === "cancel" ? "Cancelling…" : "Yes, cancel"}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="Dash2">
        <h1>Membership</h1>
      </div>

      {banner && (
        <div className="Dash4" style={{ borderLeft: "4px solid orange" }}>
          <p>{banner}</p>
        </div>
      )}

      <div className="Dash3">
        <p>Current Plan</p>
        <div className="Dash4">
          {membership === undefined && !membershipError && <p>Loading…</p>}
          {membershipError && <p>{membershipError}</p>}
          {membership === null && (
            <>
              <p>No active membership</p>
              <button onClick={loadMembership} style={{ marginTop: "0.5rem", fontSize: "0.85rem" }}>
                Check again
              </button>
            </>
          )}
          {membership && (
            <>
              <p>
                <strong>{membership.planName ?? membership.planCode}</strong>
                {membership.amountCents != null && membership.billingInterval && (
                  <span style={{ marginLeft: "0.5rem", color: "#555" }}>
                    · {new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 0 }).format(membership.amountCents / 100)}/{membership.billingInterval.toLowerCase()}
                  </span>
                )}
              </p>
              <p style={STATUS_COLORS[membership.status]}>
                {STATUS_LABELS[membership.status] || membership.status}
              </p>
              {membership.currentPeriodStart && membership.currentPeriodEnd && (
                <p style={{ fontSize: "0.85rem", color: "#666" }}>
                  Billing period: {new Date(membership.currentPeriodStart).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
                  {" – "}
                  {new Date(membership.currentPeriodEnd).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                </p>
              )}
              {membership.currentPeriodEnd && (
                <p style={{ fontSize: "0.85rem" }}>
                  {["CANCELED", "GRACE"].includes(membership.status)
                    ? "Expired"
                    : membership.cancelAtPeriodEnd
                    ? "Cancels"
                    : "Next billing"}{" "}
                  {new Date(membership.currentPeriodEnd).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                </p>
              )}
              {membership.createdAt && (
                <p style={{ fontSize: "0.8rem", color: "#999" }}>
                  Member since {new Date(membership.createdAt).toLocaleDateString("en-US", { month: "long", year: "numeric" })}
                </p>
              )}
            </>
          )}
          {hasActiveMembership && !membership?.cancelAtPeriodEnd && (
            <div className="Dash5">
              <button onClick={() => setCancelConfirm(true)} disabled={!!checkoutLoading}>
                Cancel membership
              </button>
            </div>
          )}
        </div>
      </div>


      <div className="CollaR1">
        {plansError && <p role="alert">{plansError}</p>}
        {plans.map((catalogPlan) => {
          const features = PLAN_FEATURES[catalogPlan.code];
          if (!features) return null;
          if (catalogPlan.code === "STUDENT" && !isStudentEligible) return null;
          const plan = {
            ...catalogPlan,
            features,
            price: formatPrice(catalogPlan.amountCents, catalogPlan.billingInterval),
          };
          const isCurrent =
            membership?.planCode === plan.code && hasActiveMembership;
          const sameInterval =
            catalogPlan.billingInterval === membership?.billingInterval;
          const isUpgrade =
            !isCurrent && hasActiveMembership && sameInterval &&
            catalogPlan.amountCents > (membership?.amountCents ?? 0);
          const isDowngrade =
            !isCurrent && hasActiveMembership && sameInterval &&
            catalogPlan.amountCents < (membership?.amountCents ?? 0);
          const buttonLabel = isCurrent
            ? "Current Plan"
            : isUpgrade
            ? "Upgrade"
            : isDowngrade
            ? "Downgrade"
            : "Select";
          return (
            <div className="colla6" key={plan.code}>
              <div className="collar2">
                <div className="collar3">
                  <p>{isCurrent ? "Current Plan" : "Membership"}</p>
                </div>
                <div className="colla4">
                  <p>{plan.displayName}</p>
                  <p>{plan.price}</p>
                </div>
              </div>
              <div className="colla5">
                {plan.features.map((feature) => (
                  <p key={feature}>{feature}</p>
                ))}
              </div>
              <button
                disabled={isCurrent}
                onClick={() => !isCurrent && handleUpgrade(plan)}
              >
                {buttonLabel}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}