import { useState, useEffect } from "react";
import { useAuth } from "../../context/AuthContext";

const DEFAULT_AVATAR =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'>
      <rect width='120' height='120' fill='#e5e5e5'/>
      <circle cx='60' cy='46' r='22' fill='#bdbdbd'/>
      <ellipse cx='60' cy='112' rx='40' ry='30' fill='#bdbdbd'/>
    </svg>`,
  );

const AVAILABLE_ROLES = ["GUEST", "STUDENT", "RENTEE", "MEMBER", "INSTRUCTOR", "STAFF", "ADMIN"];

const PAYMENT_KIND_LABELS = {
  MEMBERSHIP: "Membership",
  DAY_PASS: "Day Pass",
  CLASS: "Class",
  RENTAL: "Rental",
  REFUND: "Refund",
};

const PAYMENT_STATUS_COLORS = {
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

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString(undefined, { dateStyle: "long" });
}

function PanelIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="M9 3v18" />
      <path d="M13 9l3 3-3 3" />
    </svg>
  );
}

function InfoRow({ label, children }) {
  return (
    <div className="user-panel__row">
      <dt className="user-panel__label">{label}</dt>
      <dd className="user-panel__value">{children}</dd>
    </div>
  );
}

export default function UserInformation({ user, onClose: onClosed, onUserUpdated }) {
  const { authFetch } = useAuth();
  const [closing, setClosing] = useState(false);
  const onClose = () => setClosing(true);
  const [localUser, setLocalUser] = useState(user);
  const [comments, setComments] = useState([]);
  const [isWriting, setIsWriting] = useState(false);
  const [draft, setDraft] = useState("");
  const [actionError, setActionError] = useState("");
  const [editingRoles, setEditingRoles] = useState(false);
  const [pendingRoles, setPendingRoles] = useState(user.roles ?? []);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState("info");
  const [billingPayments, setBillingPayments] = useState(null);
  const [billingError, setBillingError] = useState("");
  const [showAllPayments, setShowAllPayments] = useState(false);

  useEffect(() => {
    if (activeTab !== "billing") return;
    let cancelled = false;
    setBillingPayments(null);
    setBillingError("");
    authFetch(`/api/v1/billing/admin/users/${user.id}/payments`)
      .then((res) => {
        if (!res.ok) throw new Error("Could not load billing history");
        return res.json();
      })
      .then((data) => { if (!cancelled) setBillingPayments(data); })
      .catch(() => { if (!cancelled) setBillingError("Could not load billing history."); });
    return () => { cancelled = true; };
  }, [activeTab, user.id, authFetch]);

  const isActive = !!localUser.allowed;
  const name = localUser.name || localUser.email || "Unknown user";

  const saveComment = () => {
    const text = draft.trim();
    if (!text) return;
    setComments((prev) => [...prev, text]);
    setDraft("");
    setIsWriting(false);
  };

  const toggleStatus = async () => {
    if (isActive && !window.confirm(`Disable ${name}?`)) return;
    setActionError("");
    try {
      const url = isActive
        ? `/api/v1/users/${localUser.id}`
        : `/api/v1/users/${localUser.id}/restore`;
      const resp = await authFetch(url, { method: isActive ? "DELETE" : "POST" });
      if (!resp.ok) throw new Error(isActive ? "Could not disable user." : "Could not enable user.");
      onUserUpdated?.();
      setClosing(true);
    } catch (err) {
      setActionError(err.message || "Action failed.");
    }
  };

  const toggleRole = (role) => {
    setPendingRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  };

  const saveRoles = async () => {
    setSaving(true);
    setActionError("");
    try {
      const resp = await authFetch(`/api/v1/users/${localUser.id}/role`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ roleCodes: pendingRoles }),
      });
      if (!resp.ok) throw new Error("Could not update roles.");
      setLocalUser((prev) => ({ ...prev, roles: pendingRoles }));
      setEditingRoles(false);
      onUserUpdated?.();
    } catch (err) {
      setActionError(err.message || "Could not update roles.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <aside
      className={`user-panel${closing ? " user-panel--closing" : ""}`}
      onAnimationEnd={(e) => {
        if (closing && e.target === e.currentTarget) onClosed();
      }}
    >
      <header className="user-panel__header">
        <button
          type="button"
          className="user-panel__toggle"
          onClick={onClose}
          aria-label="Close user information"
        >
          <PanelIcon />
        </button>
        <h2 className="user-panel__title">User Information</h2>
      </header>

      {/* Tab navigation */}
      <div style={{ display: "flex", borderBottom: "1px solid #e5e5e5", padding: "0 1rem" }}>
        {["info", "billing"].map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderBottom: activeTab === tab ? "2px solid #333" : "2px solid transparent",
              background: "transparent",
              cursor: "pointer",
              fontWeight: activeTab === tab ? "600" : "400",
              fontSize: "0.9rem",
              textTransform: "capitalize",
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      <>
        {activeTab === "billing" ? (
          <section style={{ padding: "1rem" }}>
            <p style={{ fontWeight: 600, marginBottom: "0.75rem" }}>Payment History</p>
            {billingError && <p style={{ color: "red", fontSize: "0.85rem" }}>{billingError}</p>}
            {billingPayments === null && !billingError && <p style={{ fontSize: "0.85rem" }}>Loading…</p>}
            {billingPayments?.length === 0 && <p style={{ fontSize: "0.85rem", color: "#666" }}>No payments found.</p>}
            {billingPayments && billingPayments.length > 0 && (
              <>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.82rem" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid #e5e5e5", textAlign: "left" }}>
                      <th style={{ padding: "0.3rem 0.4rem" }}>Date</th>
                      <th style={{ padding: "0.3rem 0.4rem" }}>Kind</th>
                      <th style={{ padding: "0.3rem 0.4rem" }}>Amount</th>
                      <th style={{ padding: "0.3rem 0.4rem" }}>Status</th>
                      <th style={{ padding: "0.3rem 0.4rem" }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {(showAllPayments ? billingPayments : billingPayments.slice(0, 5)).map((p, i) => (
                      <tr key={i} style={{ borderBottom: "1px solid #f0f0f0" }}>
                        <td style={{ padding: "0.3rem 0.4rem", whiteSpace: "nowrap" }}>
                          {new Date(p.occurredAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                        </td>
                        <td style={{ padding: "0.3rem 0.4rem" }}>
                          {PAYMENT_KIND_LABELS[p.kind] ?? p.kind}
                        </td>
                        <td style={{ padding: "0.3rem 0.4rem", whiteSpace: "nowrap" }}>
                          {formatAmount(p.amountCents, p.currency)}
                        </td>
                        <td style={{ padding: "0.3rem 0.4rem", ...PAYMENT_STATUS_COLORS[p.status] }}>
                          {p.status.charAt(0) + p.status.slice(1).toLowerCase()}
                        </td>
                        <td style={{ padding: "0.3rem 0.4rem" }}>
                          {p.stripeReceiptUrl && (
                            <a href={p.stripeReceiptUrl} target="_blank" rel="noopener noreferrer" style={{ fontSize: "0.78rem" }}>
                              Receipt
                            </a>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {billingPayments.length > 5 && (
                  <button
                    type="button"
                    className="ra-link"
                    style={{ marginTop: "0.5rem", fontSize: "0.82rem" }}
                    onClick={() => setShowAllPayments((v) => !v)}
                  >
                    {showAllPayments ? "Show less" : `Show all ${billingPayments.length} payments`}
                  </button>
                )}
              </>
            )}
          </section>
        ) : (
        <>
        <section className="user-panel__profile">
          <img className="user-panel__avatar" src={DEFAULT_AVATAR} alt="" />
          <h3 className="user-panel__name">{name}</h3>
          <p className="user-panel__role">
            {localUser.membership || "No membership"}
          </p>
        </section>

        <dl className="user-panel__details">
          <InfoRow label="Status">
            <span className={isActive ? "" : "user-panel__status--disabled"}>
              {isActive ? "Active" : "Disabled"}
            </span>
          </InfoRow>
          <InfoRow label="Email">
            <a className="user-panel__link" href={`mailto:${localUser.email}`}>
              {localUser.email}
            </a>
          </InfoRow>
          <InfoRow label="Student">
            {localUser.student ? "Yes (.edu email)" : "No"}
          </InfoRow>
          <InfoRow label="Waiver">
            {localUser.waiverSigned ? "Signed" : "Not signed"}
          </InfoRow>
          <InfoRow label="Member since">{formatDate(localUser.createdAt)}</InfoRow>
          <InfoRow label="Roles">
            {editingRoles ? (
              <div className="user-panel__roles-editor">
                {AVAILABLE_ROLES.map((role) => (
                  <label key={role} className="user-panel__role-check">
                    <input
                      type="checkbox"
                      checked={pendingRoles.includes(role)}
                      onChange={() => toggleRole(role)}
                    />
                    {role}
                  </label>
                ))}
                <div className="user-panel__role-actions">
                  <button
                    type="button"
                    className="ui-btn ui-btn--outline"
                    disabled={saving}
                    onClick={() => {
                      setEditingRoles(false);
                      setPendingRoles(localUser.roles ?? []);
                    }}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="ui-btn ui-btn--primary"
                    disabled={saving}
                    onClick={saveRoles}
                  >
                    {saving ? "Saving…" : "Save"}
                  </button>
                </div>
              </div>
            ) : (
              <span>
                {(localUser.roles ?? []).length === 0
                  ? "None"
                  : (localUser.roles ?? []).join(", ")}
                {" "}
                <button
                  type="button"
                  className="ra-link"
                  onClick={() => setEditingRoles(true)}
                >
                  Edit
                </button>
              </span>
            )}
          </InfoRow>
          <InfoRow label="Comments">
            {comments.length === 0 ? (
              "-"
            ) : (
              <ul className="user-panel__comments">
                {comments.map((c, i) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            )}
          </InfoRow>
        </dl>

        {actionError && (
          <p className="ra-state ra-error" role="alert" style={{ margin: "0 1rem 0.5rem" }}>
            {actionError}
          </p>
        )}

        <footer className="user-panel__actions">
          {isWriting && (
            <div className="user-panel__composer">
              <textarea
                className="user-panel__textarea"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Write a comment"
                rows={3}
                autoFocus
              />
              <div className="user-panel__composer-buttons">
                <button
                  type="button"
                  className="ui-btn ui-btn--outline"
                  onClick={() => {
                    setIsWriting(false);
                    setDraft("");
                  }}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="ui-btn ui-btn--primary"
                  onClick={saveComment}
                  disabled={!draft.trim()}
                >
                  Save
                </button>
              </div>
            </div>
          )}

          {!isWriting && (
            <button
              type="button"
              className="ui-btn ui-btn--outline ui-btn--block"
              onClick={() => setIsWriting(true)}
            >
              Add Comment
            </button>
          )}

          <button
            type="button"
            className="ui-btn ui-btn--primary ui-btn--block"
            onClick={toggleStatus}
          >
            {isActive ? "Disable Member" : "Enable Member"}
          </button>
          <button
            type="button"
            className="ui-btn ui-btn--outline ui-btn--block"
            onClick={onClose}
          >
            Close
          </button>
        </footer>
        </>
        )}
      </>
    </aside>
  );
}