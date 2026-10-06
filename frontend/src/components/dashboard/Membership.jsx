import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  useDashboard,
  todaySentence,
  reservationSentence,
} from "../../hooks/useDashboard";

const PLANS = [
  {
    code: "MONTHLY",
    name: "Member",
    price: "$95/Month",
    features: [
      "Rent equipment for free",
      "Access to common space",
      "Book classes for free",
      "Access to events and discounts",
    ],
  },
  {
    code: "ANNUAL",
    name: "Member with Private Studio Space",
    price: "$350-750/Month",
    features: [
      "Rent equipment for free",
      "Access to common space",
      "Book classes for free",
      "Access to events and discounts",
      "Private studio space",
    ],
  },
  {
    code: "STUDENT",
    name: "Student",
    price: "$45/Month",
    features: [
      "Rent equipment for free",
      "Book classes for free",
      "Access to events and discounts",
    ],
  },
  {
    code: "DAY_PASS",
    name: "Day Pass",
    price: "$25",
    features: [
      "Full access for one day",
      "Rent equipment",
      "Book classes",
    ],
  },
];

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
  const { authFetch } = useAuth();
  const { reservations, loading, error } = useDashboard();
  const location = useLocation();
  const navigate = useNavigate();

  const [membership, setMembership] = useState(undefined); // undefined = loading, null = none
  const [membershipError, setMembershipError] = useState("");
  const [checkoutLoading, setCheckoutLoading] = useState(null);
  const [checkoutError, setCheckoutError] = useState("");
  const [cancelConfirm, setCancelConfirm] = useState(false);

  const banner = location.state?.banner;

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

  const hasActiveMembership =
    membership &&
    ["ACTIVE", "TRIALING", "PAST_DUE"].includes(membership.status);

  function handleUpgrade(plan) {
    navigate("/dashboard/membership/checkout", { state: { plan } });
  }

  async function handlePortal() {
    setCheckoutError("");
    setCheckoutLoading("portal");
    try {
      const res = await authFetch("/api/v1/billing/portal-session", {
        method: "POST",
      });
      if (!res.ok) throw new Error("Could not open billing portal");
      const { portalUrl } = await res.json();
      window.location.href = portalUrl;
    } catch (err) {
      setCheckoutError(err.message || "Something went wrong.");
      setCheckoutLoading(null);
    }
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
      <div className="Dash2">
        <p>Membership</p>
        <p>
          Welcome back. {todaySentence()}{" "}
          {!loading && !error && reservationSentence(reservations)}
        </p>
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
          {membership === null && <p>Guest — no active membership</p>}
          {membership && (
            <>
              <p>{PLANS.find((p) => p.code === membership.planCode)?.name ?? membership.planCode}</p>
              <p style={STATUS_COLORS[membership.status]}>
                {STATUS_LABELS[membership.status] || membership.status}
              </p>
              {membership.currentPeriodEnd && (
                <p>
                  {["CANCELED", "GRACE"].includes(membership.status)
                    ? "Expired"
                    : membership.cancelAtPeriodEnd
                    ? "Cancels"
                    : "Renews"}{" "}
                  {new Date(membership.currentPeriodEnd).toLocaleDateString()}
                </p>
              )}
            </>
          )}
          {hasActiveMembership && (
            <div className="Dash5">
              {checkoutError && <p style={{ color: "red" }}>{checkoutError}</p>}
              <button
                onClick={handlePortal}
                disabled={!!checkoutLoading}
              >
                {checkoutLoading === "portal" ? "Opening…" : "Manage billing"}
              </button>
              {!membership?.cancelAtPeriodEnd && (
                cancelConfirm ? (
                  <>
                    <p>Cancel at end of billing period?</p>
                    <button
                      onClick={handleCancel}
                      disabled={checkoutLoading === "cancel"}
                      style={{ color: "red" }}
                    >
                      {checkoutLoading === "cancel" ? "Cancelling…" : "Yes, cancel"}
                    </button>
                    <button onClick={() => setCancelConfirm(false)} disabled={!!checkoutLoading}>
                      Keep membership
                    </button>
                  </>
                ) : (
                  <button onClick={() => setCancelConfirm(true)} disabled={!!checkoutLoading}>
                    Cancel membership
                  </button>
                )
              )}
            </div>
          )}
        </div>
      </div>


      <div className="CollaR1">
        {PLANS.map((plan) => {
          const isCurrent =
            membership?.planCode === plan.code && hasActiveMembership;
          return (
            <div className="colla6" key={plan.code}>
              <div className="collar2">
                <div className="collar3">
                  <p>{isCurrent ? "Current Plan" : "Membership"}</p>
                </div>
                <div className="colla4">
                  <p>{plan.name}</p>
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
                {isCurrent ? "Current Plan" : "Upgrade"}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}