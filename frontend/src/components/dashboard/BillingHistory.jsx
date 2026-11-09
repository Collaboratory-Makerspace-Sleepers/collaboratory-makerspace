import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const KIND_LABELS = {
  MEMBERSHIP: "Membership",
  DAY_PASS: "Day Pass",
  CLASS: "Class",
  RENTAL: "Rental",
  REFUND: "Refund",
};

const KIND_FILTERS = [
  { key: "ALL", label: "All" },
  { key: "MEMBERSHIP", label: "Membership" },
  { key: "DAY_PASS", label: "Day Pass" },
  { key: "RENTAL", label: "Equipment" },
  { key: "CLASS", label: "Classes" },
];

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
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [portalLoading, setPortalLoading] = useState(false);
  const [portalError, setPortalError] = useState("");

  async function openInvoices() {
    setPortalError("");
    setPortalLoading(true);
    try {
      const res = await authFetch("/api/v1/billing/portal-session", { method: "POST" });
      if (!res.ok) throw new Error("Could not open invoices");
      const { portalUrl } = await res.json();
      window.location.href = portalUrl;
    } catch (err) {
      setPortalError(err.message || "Something went wrong.");
      setPortalLoading(false);
    }
  }

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

  const filtered = payments?.filter(
    (p) => activeFilter === "ALL" || p.kind === activeFilter
  );

  return (
    <div className="DashHome1">
      <div className="Dash2" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <p>Billing History</p>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "0.25rem" }}>
          <button
            onClick={openInvoices}
            disabled={portalLoading}
            style={{ fontSize: "0.85rem" }}
          >
            {portalLoading ? "Opening…" : "View invoices →"}
          </button>
          {portalError && <p style={{ color: "red", fontSize: "0.8rem", margin: 0 }}>{portalError}</p>}
        </div>
      </div>

      <div className="Dash3">
        {/* Filter tabs */}
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginBottom: "1rem" }}>
          {KIND_FILTERS.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveFilter(key)}
              style={{
                padding: "0.3rem 0.8rem",
                borderRadius: "1rem",
                border: "1px solid #ccc",
                background: activeFilter === key ? "#333" : "transparent",
                color: activeFilter === key ? "#fff" : "inherit",
                cursor: "pointer",
                fontSize: "0.85rem",
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {error && <p style={{ color: "red" }}>{error}</p>}
        {payments === null && !error && <p>Loading…</p>}

        {filtered?.length === 0 && (
          <p style={{ color: "#666" }}>
            {activeFilter === "ALL"
              ? "No payments yet."
              : `No ${KIND_LABELS[activeFilter] ?? activeFilter} payments found.`}
          </p>
        )}

        {filtered?.map((p, i) => (
          <div
            className="Dash4"
            key={i}
            style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}
          >
            <div>
              <p><strong>{KIND_LABELS[p.kind] ?? p.kind}</strong></p>
              <p style={{ fontSize: "0.85rem", color: "#666" }}>{p.description}</p>
              <p style={{ fontSize: "0.8rem", color: "#999" }}>
                {new Date(p.occurredAt).toLocaleDateString("en-US", {
                  year: "numeric", month: "short", day: "numeric",
                })}
                {p.referenceId && (
                  <span style={{ marginLeft: "0.5rem" }}>· #{p.referenceId}</span>
                )}
              </p>
            </div>
            <div style={{ textAlign: "right", flexShrink: 0 }}>
              <p><strong>{formatAmount(p.amountCents, p.currency)}</strong></p>
              <p style={{ fontSize: "0.85rem", ...STATUS_COLORS[p.status] }}>
                {p.status.charAt(0) + p.status.slice(1).toLowerCase()}
              </p>
              {p.stripeReceiptUrl && (
                <a
                  href={p.stripeReceiptUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ fontSize: "0.8rem" }}
                >
                  View receipt →
                </a>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
