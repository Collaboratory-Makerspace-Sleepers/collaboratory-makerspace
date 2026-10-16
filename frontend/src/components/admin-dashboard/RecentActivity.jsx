import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 6;

function formatCreatedAt(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

const TableIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2.2" />
    <path d="M3 9h18M3 15h18M12 3v18" stroke="currentColor" strokeWidth="2.2" />
  </svg>
);

export default function RecentActivity() {
  const { authFetch } = useAuth();
  const [users, setUsers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedUser, setSelectedUser] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch(`/api/v1/users/activity?page=${page}&size=${PAGE_SIZE}&sort=createdAt,desc`)
      .then(async (response) => {
        if (!response.ok) throw new Error("Could not load recent user activity.");
        return response.json();
      })
      .then((result) => {
        if (cancelled) return;
        setUsers(Array.isArray(result.content) ? result.content : []);
        setTotalPages(Number.isInteger(result.totalPages) ? result.totalPages : 0);
      })
      .catch((loadError) => {
        if (!cancelled) setError(loadError.message || "Could not load recent user activity.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authFetch, page]);

  function navigatePage(nextPage) {
    setError("");
    setLoading(true);
    setPage(nextPage);
  }

  const previousPage = () => navigatePage(Math.max(0, page - 1));
  const nextPage = () => navigatePage(Math.min(totalPages - 1, page + 1));

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <TableIcon />
            <h2>Recent User Activity</h2>
          </div>

          <div className="ra-pagination">
            <button
              className="ra-page-btn"
              onClick={previousPage}
              disabled={loading || page === 0}
              aria-label="Previous page"
            >
              ‹
            </button>
            <span className="ra-page-info">
              {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
            </span>
            <button
              className="ra-page-btn"
              onClick={nextPage}
              disabled={loading || totalPages === 0 || page >= totalPages - 1}
              aria-label="Next page"
            >
              ›
            </button>
          </div>
        </div>

        {loading ? (
          <p className="ra-state" role="status">Loading users…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : users.length === 0 ? (
          <p className="ra-state">No recent activity.</p>
        ) : (
          <div className="ra-table-scroll">
            <table className="ra-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Time</th>
                  <th>Membership</th>
                  <th>Allowed</th>
                  <th>Waiver</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  return (
                    <tr key={user.id}>
                      <td>{user.name || user.email || "Unknown user"}</td>
                      <td>{formatCreatedAt(user.createdAt)}</td>
                      <td>{user.membership || "None"}</td>
                      <td>{user.allowed || "Not allowed"}</td>
                      <td>{user.waiverSigned ? "Signed" : "Not signed"}</td>
                      <td>
                        <button className="ra-link" onClick={() => setSelectedUser(user)}>
                          View Profile
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedUser && (
        <div className="ra-profile-backdrop" onClick={() => setSelectedUser(null)}>
          <section
            aria-labelledby="ra-profile-title"
            aria-modal="true"
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="ra-profile-header">
              <h3 id="ra-profile-title">User Profile</h3>
              <button onClick={() => setSelectedUser(null)}>Close</button>
            </header>
            <dl className="ra-profile-details">
              <div><dt>Name</dt><dd>{selectedUser.name || "Unknown user"}</dd></div>
              <div><dt>Email</dt><dd>{selectedUser.email}</dd></div>
              <div><dt>Membership</dt><dd>{selectedUser.membership || "None"}</dd></div>
              <div><dt>Allowed</dt><dd>{selectedUser.allowed || "Not allowed"}</dd></div>
              <div><dt>Waiver</dt><dd>{selectedUser.waiverSigned ? "Signed" : "Not signed"}</dd></div>
              <div><dt>Joined</dt><dd>{formatCreatedAt(selectedUser.createdAt)}</dd></div>
            </dl>
          </section>
        </div>
      )}
    </div>
  );
}