import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";

const STATUS_LABELS = {
  PENDING:              { label: "Pending",         color: "orange" },
  APPROVED_WITH_REFUND: { label: "Awaiting Stripe", color: "#2563eb" },
  APPROVED_NO_REFUND:   { label: "Cancelled",       color: "#6b7280" },
  REJECTED:             { label: "Rejected",        color: "#6b7280" },
  COMPLETED:            { label: "Completed",       color: "green"  },
};

const FILTERS = [
  { key: "ALL",                  label: "All" },
  { key: "PENDING",              label: "Pending" },
  { key: "APPROVED_WITH_REFUND", label: "Awaiting Stripe" },
  { key: "APPROVED_NO_REFUND",   label: "Resolved" },
];

function StatusBadge({ status }) {
  const { label, color } = STATUS_LABELS[status] || { label: status, color: "#6b7280" };
  return (
    <span style={{
      background: color, color: "#fff", borderRadius: "4px",
      padding: "2px 8px", fontSize: "0.78rem", fontWeight: 600,
    }}>
      {label}
    </span>
  );
}

function RefundBadge({ until }) {
  const eligible = new Date(until) > new Date();
  return (
    <span style={{
      background: eligible ? "green" : "#dc2626", color: "#fff",
      borderRadius: "4px", padding: "2px 8px", fontSize: "0.78rem", fontWeight: 600,
    }}>
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
    <div className="DashHome1">
      <div className="Dash2">
        <p className="admin-page-title">Cancellation Requests</p>
      </div>

      {error && <p style={{ color: "red" }}>{error}</p>}
      {actionError && <p style={{ color: "red" }}>{actionError}</p>}

      <div style={{ display: "flex", gap: "8px", marginBottom: "16px", flexWrap: "wrap" }}>
        {FILTERS.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            style={{ fontWeight: filter === f.key ? 700 : 400 }}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading ? (
        <p>Loading…</p>
      ) : filtered.length === 0 ? (
        <p>No requests found.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="ra-table cancellations-table" style={{ width: "100%", borderCollapse: "collapse" }}>
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
                  <td>
                    {r.status === "PENDING" && (
                      <div style={{ display: "flex", gap: "4px", flexWrap: "wrap" }}>
                        <button
                          disabled={busy === r.id}
                          onClick={() => setApproveModal(r)}
                        >
                          Approve + Refund
                        </button>
                        <button
                          disabled={busy === r.id}
                          onClick={() => confirmInline(r.id, "APPROVE_NO_REFUND")}
                        >
                          Approve, No Refund
                        </button>
                        <button
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