import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const KIND_LABELS = {
  MEMBERSHIP: "Membership",
  DAY_PASS: "Day Pass",
  CLASS: "Class",
  RENTAL: "Rental",
  REFUND: "Refund",
};

const STATUS_COLORS = {
  SUCCEEDED: { color: "green" },
  PENDING: { color: "orange" },
  FAILED: { color: "red" },
  REFUNDED: { color: "gray" },
};

function formatAmount(cents, currency) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency?.toUpperCase() ?? "USD",
  }).format(cents / 100);
}

export default function BillingHistory() {
  const { authFetch } = useAuth();
  const [payments, setPayments] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/billing/me/payments")
      .then((res) => {
        if (!res.ok) throw new Error("Could not load payment history");
        return res.json();
      })
      .then((data) => { if (!cancelled) setPayments(data); })
      .catch(() => { if (!cancelled) setError("Could not load payment history."); });
    return () => { cancelled = true; };
  }, [authFetch]);

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Billing History</p>
      </div>

      <div className="Dash3">
        {error && <p style={{ color: "red" }}>{error}</p>}
        {payments === null && !error && <p>Loading…</p>}
        {payments?.length === 0 && <p>No payments yet.</p>}
        {payments?.map((p, i) => (
          <div className="Dash4" key={i} style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <p><strong>{KIND_LABELS[p.kind] ?? p.kind}</strong></p>
              <p style={{ fontSize: "0.85rem", color: "#666" }}>{p.description}</p>
              <p style={{ fontSize: "0.8rem", color: "#999" }}>
                {new Date(p.occurredAt).toLocaleDateString("en-US", {
                  year: "numeric", month: "short", day: "numeric",
                })}
              </p>
            </div>
            <div style={{ textAlign: "right" }}>
              <p><strong>{formatAmount(p.amountCents, p.currency)}</strong></p>
              <p style={{ fontSize: "0.85rem", ...STATUS_COLORS[p.status] }}>
                {p.status.charAt(0) + p.status.slice(1).toLowerCase()}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}