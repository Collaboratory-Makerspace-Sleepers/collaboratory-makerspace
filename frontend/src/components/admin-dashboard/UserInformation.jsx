import { useState } from "react";

const DEFAULT_AVATAR =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'>
      <rect width='120' height='120' fill='#e5e5e5'/>
      <circle cx='60' cy='46' r='22' fill='#bdbdbd'/>
      <ellipse cx='60' cy='112' rx='40' ry='30' fill='#bdbdbd'/>
    </svg>`,
  );

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

export default function UserInformation({ user, onClose: onClosed }) {
  const [closing, setClosing] = useState(false);
  const onClose = () => setClosing(true);
  const [status, setStatus] = useState(user.allowed ? "Active" : "Disabled");
  const [comments, setComments] = useState([]);
  const [isWriting, setIsWriting] = useState(false);
  const [draft, setDraft] = useState("");

  const isActive = status === "Active";
  const name = user.name || user.email || "Unknown user";

  const saveComment = () => {
    const text = draft.trim();
    if (!text) return;
    setComments((prev) => [...prev, text]);
    setDraft("");
    setIsWriting(false);
  };

  const toggleStatus = () => {
    if (isActive && !window.confirm(`Disable ${name}?`)) return;
    setStatus(isActive ? "Disabled" : "Active");
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

      {
        <>
          <section className="user-panel__profile">
            <img className="user-panel__avatar" src={DEFAULT_AVATAR} alt="" />
            <h3 className="user-panel__name">{name}</h3>
            <p className="user-panel__role">
              {user.membership || "No membership"}
            </p>
          </section>

          <dl className="user-panel__details">
            <InfoRow label="Status">
              <span className={isActive ? "" : "user-panel__status--disabled"}>
                {status}
              </span>
            </InfoRow>
            <InfoRow label="Email">
              <a className="user-panel__link" href={`mailto:${user.email}`}>
                {user.email}
              </a>
            </InfoRow>
            <InfoRow label="Student">
              {user.student ? "Yes (.edu email)" : "No"}
            </InfoRow>
            <InfoRow label="Waiver">
              {user.waiverSigned ? "Signed" : "Not signed"}
            </InfoRow>
            <InfoRow label="Member since">{formatDate(user.createdAt)}</InfoRow>
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
      }
    </aside>
  );
}
