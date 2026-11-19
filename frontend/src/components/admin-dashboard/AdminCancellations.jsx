import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";

const STATUS_LABELS = {
  PENDING:              { label: "Pending",         cls: "cancel-badge--pending"  },
  APPROVED_WITH_REFUND: { label: "Awaiting Stripe", cls: "cancel-badge--waiting"  },
  APPROVED_NO_REFUND:   { label: "Cancelled",       cls: "cancel-badge--resolved" },
  REJECTED:             { label: "Rejected",        cls: "cancel-badge--resolved" },
  COMPLETED:            { label: "Completed",       cls: "cancel-badge--resolved" },
};

const FILTERS = [
  { key: "ALL",                  label: "All" },
  { key: "PENDING",              label: "Pending" },
  { key: "APPROVED_WITH_REFUND", label: "Awaiting Stripe" },
  { key: "APPROVED_NO_REFUND",   label: "Resolved" },
];

function StatusBadge({ status }) {
  const { label, cls } = STATUS_LABELS[status] || { label: status, cls: "cancel-badge--resolved" };
  return <span className={`cancel-badge ${cls}`}>{label}</span>;
}

function RefundBadge({ until }) {
  const eligible = new Date(until) > new Date();
  return (
    <span className={`cancel-badge ${eligible ? "cancel-badge--refund-ok" : "cancel-badge--refund-exp"}`}>
      {eligible ? "Eligible" : "Expired"}
    </span>
  );
}

function ApproveRefundModal({ request, onClose, onConfirm, busy }) {
  const [adminNote, setAdminNote] = useState("");
  const windowExpired = new Date(request.refundEligibleUntil) < new Date();
  const dollars = request.refundAmountCents > 0
    ? `$${(request.refundAmountCents / 100).toFixed(2)}`
    : "Unknown";

  return (
    <div className="ra-profile-backdrop" onClick={onClose}>
      <div className="nu-dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Approve Cancellation + Refund</h2>

        <p><strong>Refund amount:</strong> {dollars}</p>

        {windowExpired && (
          <p style={{ color: "#dc2626", fontWeight: 600 }}>
            The 48-hour refund window has passed. Proceeding anyway.
          </p>
        )}

        {request.stripePaymentIntentId ? (
          <button
            onClick={() =>
              window.open(
                `https://dashboard.stripe.com/payments/${request.stripePaymentIntentId}`,
                "_blank"
              )
            }
          >
            Open in Stripe
          </button>
        ) : (
          <p style={{ color: "#6b7280" }}>No Stripe payment found for this reservation.</p>
        )}

        <p style={{ fontSize: "0.9rem", marginTop: "8px" }}>
          Issue the refund in Stripe, then click Confirm. The request will auto-complete when
          Stripe confirms.
        </p>

        <label style={{ display: "block", marginTop: "12px" }}>
          <span>Admin note (optional)</span>
          <textarea
            rows={3}
            style={{ width: "100%", marginTop: "4px" }}
            value={adminNote}
            onChange={(e) => setAdminNote(e.target.value)}
          />
        </label>

        <div style={{ display: "flex", gap: "8px", marginTop: "16px" }}>
          <button onClick={onClose} disabled={busy}>Cancel</button>
          <button
            onClick={() => onConfirm("APPROVE_WITH_REFUND", adminNote)}
            disabled={busy}
          >
            {busy ? "Saving…" : "Confirm — I've issued the refund"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function AdminCancellations() {
  const { authFetch } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [approveModal, setApproveModal] = useState(null);
  const [busy, setBusy] = useState(null);
  const [actionError, setActionError] = useState("");

  const load = useCallback(() => {
    setLoading(true);
    authFetch("/api/v1/admin/cancellation-requests?status=ALL")
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((data) => { setRequests(data); setLoading(false); })
      .catch(() => { setError("Could not load cancellation requests."); setLoading(false); });
  }, [authFetch]);

  useEffect(() => { load(); }, [load]);

  const filtered = filter === "ALL"
    ? requests
    : requests.filter((r) => r.status === filter);

  async function resolve(requestId, decision, adminNote) {
    setActionError("");
    setBusy(requestId);
    try {
      const res = await authFetch(`/api/v1/admin/cancellation-requests/${requestId}/resolve`, {
        method: "POST",
        body: JSON.stringify({ decision, adminNote }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not resolve request");
      }
      setApproveModal(null);
      load();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  function confirmInline(requestId, decision) {
    const label = decision === "APPROVE_NO_REFUND" ? "Approve (No Refund)" : "Reject";
    if (window.confirm(`${label} this request?`)) {
      resolve(requestId, decision, "");
    }
  }

  function fmtDate(iso) {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString(undefined, {
      month: "short", day: "numeric", year: "numeric",
    });
  }

  function truncatePi(pi) {
    if (!pi) return "—";
    return "…" + pi.slice(-8);
  }

  return (
    <div className="AdUser admin-classes-page">
      <h1 className="admin-page-title">Cancellations</h1>

      <div className="AdAct">
        <div className="ra-wrapper">
          <div className="ra-card">
            <div className="ra-header">
              <div className="ra-title"><h2>Cancellation Requests</h2></div>
            </div>

            <div className="catalog-filters" style={{ padding: "0 24px 16px" }}>
              {FILTERS.map((f) => (
                <button
                  key={f.key}
                  className={`ra-link au-toggle-btn${filter === f.key ? " au-toggle-btn--active" : ""}`}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {error && <p style={{ color: "red", padding: "0 24px" }}>{error}</p>}
            {actionError && <p style={{ color: "red", padding: "0 24px" }}>{actionError}</p>}

            {loading ? (
              <p className="ra-state">Loading…</p>
            ) : filtered.length === 0 ? (
              <p className="ra-state">No requests found.</p>
            ) : (
              <div className="ra-table-scroll">
                <table className="ra-table cancellations-table">
                  <thead>
                    <tr>
                      <th>User</th>
                      <th>Equipment</th>
                      <th>Service Period</th>
                      <th>Reason</th>
                      <th>Refund Window</th>
                      <th>Stripe PI</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((r) => (
                      <tr key={r.id}>
                        <td>
                          <div>{r.userName}</div>
                          <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{r.userEmail}</div>
                        </td>
                        <td>{r.equipmentName || "—"}</td>
                        <td>
                          {fmtDate(r.startTime)} – {fmtDate(r.endTime)}
                        </td>
                        <td style={{ maxWidth: "180px", wordBreak: "break-word" }}>
                          {r.reason || <em style={{ color: "#9ca3af" }}>None provided</em>}
                        </td>
                        <td><RefundBadge until={r.refundEligibleUntil} /></td>
                        <td>
                          {r.stripePaymentIntentId ? (
                            <span
                              title={r.stripePaymentIntentId}
                              style={{ cursor: "pointer", textDecoration: "underline dotted" }}
                              onClick={() => {
                                navigator.clipboard?.writeText(r.stripePaymentIntentId);
                              }}
                            >
                              {truncatePi(r.stripePaymentIntentId)}
                            </span>
                          ) : "—"}
                        </td>
                        <td><StatusBadge status={r.status} /></td>
                        <td className="ae-cell">
                          {r.status === "PENDING" && (
                            <div className="ae-actions">
                              <button
                                className="ra-link"
                                disabled={busy === r.id}
                                onClick={() => setApproveModal(r)}
                              >
                                Approve + Refund
                              </button>
                              <button
                                className="ra-link"
                                disabled={busy === r.id}
                                onClick={() => confirmInline(r.id, "APPROVE_NO_REFUND")}
                              >
                                Approve, No Refund
                              </button>
                              <button
                                className="ra-link"
                                disabled={busy === r.id}
                                onClick={() => confirmInline(r.id, "REJECT")}
                              >
                                Reject
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {approveModal && (
        <ApproveRefundModal
          request={approveModal}
          onClose={() => setApproveModal(null)}
          onConfirm={(decision, adminNote) => resolve(approveModal.id, decision, adminNote)}
          busy={busy === approveModal.id}
        />
      )}
    </div>
  );
}