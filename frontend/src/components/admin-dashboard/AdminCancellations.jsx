import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";

const STATUS_LABELS = {
  PENDING:              { label: "Pending",         cls: "cancel-badge--pending"  },
  APPROVED_WITH_REFUND: { label: "Awaiting Stripe", cls: "cancel-badge--waiting"  },
  APPROVED_NO_REFUND:   { label: "Cancelled",       cls: "cancel-badge--resolved" },
  REJECTED:             { label: "Rejected",        cls: "cancel-badge--refund-exp" },
  WITHDRAWN:            { label: "Withdrawn",       cls: "cancel-badge--resolved" },
  COMPLETED:            { label: "Completed",       cls: "cancel-badge--resolved" },
};

const FILTERS = [
  { key: "ALL",                  label: "All" },
  { key: "PENDING",              label: "Pending" },
  { key: "APPROVED_WITH_REFUND", label: "Awaiting Stripe" },
  { key: "RESOLVED",            label: "Resolved" },
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

const REASON_PREVIEW_LENGTH = 120;

function MemberReason({ reason, collapsible = false }) {
  if (!reason?.trim()) return <em>No reason provided</em>;
  if (!collapsible) return <span className="cancel-comment">{reason}</span>;

  const preview = Array.from(reason.trim().replace(/\s+/g, " "));
  if (preview.length <= REASON_PREVIEW_LENGTH && !/[\r\n]/.test(reason.trim())) {
    return <span className="cancel-comment">{reason}</span>;
  }

  return (
    <details className="cancel-reason-details">
      <summary>
        <span className="cancel-reason-show">Show full reason</span>
        <span className="cancel-reason-hide">Hide full reason</span>
        <span className="cancel-reason-preview">
          {preview.slice(0, REASON_PREVIEW_LENGTH).join("")}
          {preview.length > REASON_PREVIEW_LENGTH && "..."}
        </span>
      </summary>
      <div className="cancel-reason-full cancel-comment" tabIndex={0} role="region" aria-label="Full member reason">
        {reason}
      </div>
    </details>
  );
}

function ReviewModal({ request, decision, onClose, onConfirm, busy, error }) {
  const [decisionNote, setDecisionNote] = useState("");
  const refund = decision === "APPROVE_WITH_REFUND";
  const reject = decision === "REJECT";
  const title = refund ? "Approve cancellation with refund" : reject ? "Reject cancellation" : "Approve without refund";
  const windowExpired = new Date(request.refundEligibleUntil) < new Date();
  const dollars = request.refundAmountCents > 0
    ? `$${(request.refundAmountCents / 100).toFixed(2)}`
    : "Unknown";

  return (
    <div className="ra-profile-backdrop" onClick={busy ? undefined : onClose}>
      <form className="ra-profile-dialog nu-dialog cancel-dialog" role="dialog" aria-modal="true"
        aria-labelledby="admin-cancel-title" onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => { e.preventDefault(); if (!busy) onConfirm(decision, decisionNote.trim()); }}>
        <h2 id="admin-cancel-title">{title}</h2>
        <p>{request.equipmentName} · {request.userName || request.userEmail}</p>
        <section className="cancel-review-reason" aria-labelledby="admin-cancel-reason">
          <h3 id="admin-cancel-reason">Member’s reason</h3>
          <MemberReason reason={request.reason} />
        </section>

        {refund && <p><strong>Refund amount:</strong> {dollars}</p>}

        {refund && windowExpired && (
          <p style={{ color: "#dc2626", fontWeight: 600 }}>
            The 48-hour refund window has passed. Proceeding anyway.
          </p>
        )}

        {refund && (
          <p>Approving will automatically issue a Stripe refund of {dollars}. The status will update to
            Completed once Stripe confirms.</p>
        )}

        {reject && <p>The existing reservation will not be changed, and no refund will be issued.</p>}
        {!refund && !reject && <p>The cancellation will be approved without issuing a refund.</p>}

        <label className="nu-field">
          <span>{reject ? "Message to member (optional)" : "Internal decision note (optional)"}</span>
          <textarea
            rows={3}
            maxLength={reject ? 1000 : undefined}
            value={decisionNote}
            onChange={(e) => setDecisionNote(e.target.value)}
            disabled={busy}
          />
          {reject && <small>The member can read this message in My Reservations.</small>}
        </label>

        {error && <p className="nu-message" role="alert">{error}</p>}
        <div className="nu-actions">
          <button type="button" className="nu-cancel" onClick={onClose} disabled={busy}>Back</button>
          <button type="submit" disabled={busy}>
            {busy ? "Saving…" : reject ? "Reject request" : "Confirm approval"}
          </button>
        </div>
      </form>
    </div>
  );
}

export default function AdminCancellations() {
  const { authFetch } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [review, setReview] = useState(null);
  const [busy, setBusy] = useState(null);
  const [actionError, setActionError] = useState("");

  const load = useCallback(() => {
    setLoading(true);
    authFetch("/api/v1/admin/cancellation-requests?status=ALL")
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((data) => { setRequests(data); setError(""); setLoading(false); })
      .catch(() => { setError("Could not load cancellation requests."); setLoading(false); });
  }, [authFetch]);

  useEffect(() => { load(); }, [load]);

  const filtered = filter === "ALL"
    ? requests
    : requests.filter((r) => filter === "RESOLVED"
      ? ["APPROVED_NO_REFUND", "REJECTED", "WITHDRAWN", "COMPLETED"].includes(r.status)
      : r.status === filter);

  async function resolve(requestId, decision, decisionNote) {
    setActionError("");
    if (decision === "REJECT" && decisionNote && !Object.hasOwn(review.request, "rejectionReason")) {
      setActionError("The backend must be updated before it can save a message to the member.");
      return;
    }
    setBusy(requestId);
    try {
      const res = await authFetch(`/api/v1/admin/cancellation-requests/${requestId}/resolve`, {
        method: "POST",
        body: JSON.stringify({ decision, ...(decision === "REJECT"
          ? { rejectionReason: decisionNote }
          : { adminNote: decisionNote }) }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not resolve request");
      }
      setReview(null);
      load();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  function openReview(request, decision) {
    setActionError("");
    setReview({ request, decision });
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
      <title>Cancellations | The Collaboratory</title>
      <h1 className="admin-page-title">Cancellations</h1>

      <div className="AdAct">
        <div className="ra-wrapper">
          <div className="ra-card">
            <div className="ra-header">
              <div className="ra-title"><h2 id="cancellation-requests-title">Cancellation Requests</h2></div>
            </div>

            <div className="catalog-filters cancel-filters" role="group" aria-label="Filter cancellation requests">
              {FILTERS.map((f) => (
                <button
                  key={f.key}
                  className="cancel-filter"
                  aria-pressed={filter === f.key}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {error && <p style={{ color: "red", padding: "0 24px" }}>{error}</p>}

            {!loading && filtered.length > 0 && (
              <p id="cancel-table-hint" className="cancel-table-hint">
                Review buttons are available for pending requests. Scroll sideways to see all columns.
              </p>
            )}

            {loading ? (
              <p className="ra-state">Loading…</p>
            ) : filtered.length === 0 ? (
              <p className="ra-state">No requests found.</p>
            ) : (
              <div className="ra-table-scroll cancel-table-scroll" tabIndex={0} role="region"
                aria-label="Cancellation requests table" aria-describedby="cancel-table-hint">
                <table className="ra-table cancellations-table" aria-labelledby="cancellation-requests-title">
                  <thead>
                    <tr>
                      <th scope="col">User</th>
                      <th scope="col">Equipment</th>
                      <th scope="col">Service Period</th>
                      <th scope="col" className="cancel-reason-cell">Member’s reason</th>
                      <th scope="col">Refund Window</th>
                      <th scope="col">Stripe PI</th>
                      <th scope="col">Amount</th>
                      <th scope="col">Status</th>
                      <th scope="col" className="cancel-note-cell">Decision notes</th>
                      <th scope="col" className="cancel-actions-cell">Actions</th>
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
                        <td className="cancel-reason-cell">
                          <MemberReason reason={r.reason} collapsible />
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
                        <td>
                          {r.refundAmountCents > 0
                            ? "$" + (r.refundAmountCents / 100).toFixed(2)
                            : "—"}
                        </td>
                        <td><StatusBadge status={r.status} /></td>
                        <td className="cancel-note-cell">
                          {r.status === "REJECTED" || r.adminNote?.trim() ? (
                            <div className="cancel-note-scroll" tabIndex={0} role="region"
                              aria-label={`Decision notes for request ${r.id}`}>
                              {r.status === "REJECTED" && (
                                <div><strong>Message to member:</strong>{" "}
                                  <span className="cancel-comment">{r.rejectionReason || "None"}</span>
                                </div>
                              )}
                              {r.adminNote?.trim() && (
                                <div><strong>Internal:</strong>{" "}<span className="cancel-comment">{r.adminNote}</span></div>
                              )}
                            </div>
                          ) : "—"}
                        </td>
                        <td className="cancel-actions-cell">
                          {r.status === "PENDING" ? (
                            <div className="cancel-row-actions">
                              <button
                                type="button"
                                className="cancel-action cancel-action-refund"
                                disabled={busy === r.id || !r.stripePaymentIntentId}
                                onClick={() => openReview(r, "APPROVE_WITH_REFUND")}
                              >
                                Approve + Refund
                              </button>
                              <button
                                type="button"
                                className="cancel-action cancel-action-approve"
                                disabled={busy === r.id}
                                onClick={() => openReview(r, "APPROVE_NO_REFUND")}
                              >
                                Approve, No Refund
                              </button>
                              <button
                                type="button"
                                className="cancel-action cancel-action-reject"
                                disabled={busy === r.id}
                                onClick={() => openReview(r, "REJECT")}
                              >
                                Reject
                              </button>
                            </div>
                          ) : <span className="cancel-no-action">No review actions</span>}
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

      {review && (
        <ReviewModal
          request={review.request}
          decision={review.decision}
          onClose={() => setReview(null)}
          onConfirm={(decision, decisionNote) => resolve(review.request.id, decision, decisionNote)}
          busy={busy === review.request.id}
          error={actionError}
        />
      )}
    </div>
  );
}