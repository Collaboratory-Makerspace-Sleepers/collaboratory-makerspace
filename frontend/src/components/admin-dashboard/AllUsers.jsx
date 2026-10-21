import { useEffect, useMemo, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 10;
const FETCH_SIZE = 500;

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function fullName(user) {
  return user.name || "";
}

export default function AllUsers({ onViewProfile, selectedId, refreshKey = 0 }) {
  const { authFetch } = useAuth();
  const [users, setUsers] = useState([]);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [resendNote, setResendNote] = useState("");

  const deleteUser = async (user) => {
    const label = user.email || fullName(user);
    if (!window.confirm(`Delete ${label}? They will lose access to the makerspace.`)) return;
    setResendNote("");
    try {
      const res = await authFetch(`/api/v1/users/${user.id}`, { method: "DELETE" });
      if (res.ok) {
        setUsers((prev) => prev.filter((u) => u.id !== user.id));
        setResendNote(`Deleted ${label}.`);
      } else {
        const data = await res.json().catch(() => ({}));
        setResendNote(data.message || `Could not delete ${label}.`);
      }
    } catch {
      setResendNote("Could not delete the user.");
    }
  };

  const resendCode = async (user) => {
    setResendNote("");
    try {
      const res = await authFetch(`/api/v1/admin/registrations/${user.id}/resend-invite`, { method: "POST" });
      setResendNote(
        res.ok
          ? `Sign-in code emailed to ${user.email || fullName(user)}.`
          : `Could not send the email to ${user.email || fullName(user)}. Check the mail settings and try again.`
      );
    } catch {
      setResendNote("Could not send the email.");
    }
  };

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      authFetch(`/api/v1/users/activity?page=0&size=${FETCH_SIZE}&sort=createdAt,desc`)
        .then(async (response) => {
          if (!response.ok) throw new Error("Could not load users.");
          return response.json();
        })
        .then((result) => {
          if (cancelled) return;
          setUsers(Array.isArray(result.content) ? result.content : []);
          setError("");
        })
        .catch((loadError) => {
          if (!cancelled) setError(loadError.message || "Could not load users.");
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    load();
    // Poll so people who sign in or get created show up without a manual reload.
    const timer = setInterval(() => {
      if (!document.hidden) load();
    }, 10000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [authFetch, refreshKey]);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return users;
    return users.filter(
      (user) =>
        fullName(user).toLowerCase().includes(term) ||
        (user.roles ?? []).some((role) => role.toLowerCase().includes(term))
    );
  }, [users, query]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const visible = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <h2>All Users</h2>
          </div>

          <div className="ra-pagination">
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              aria-label="Previous page"
            >
              ‹
            </button>
            <span className="ra-page-info">
              {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
            </span>
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={totalPages === 0 || page >= totalPages - 1}
              aria-label="Next page"
            >
              ›
            </button>
          </div>
        </div>

        <input
          type="search"
          className="au-search"
          placeholder="Search by name or role"
          aria-label="Search users by name"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
        />

        {loading ? (
          <p className="ra-state" role="status">Loading users…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : visible.length === 0 ? (
          <p className="ra-state">{query ? "No users match your search." : "No users yet."}</p>
        ) : (
          <div className="ra-table-scroll">
            {resendNote && <p className="ra-state" role="status">{resendNote}</p>}
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
                {visible.map((user) => (
                  <tr key={user.id} className={user.id === selectedId ? "au-selected" : undefined}>
                    <td>{fullName(user) || user.email || "Unknown user"}</td>
                    <td>{formatDate(user.createdAt)}</td>
                    <td>{user.membership || "None"}</td>
                    <td>{user.allowed || "Not allowed"}</td>
                    <td>{user.waiverSigned ? "Signed" : "Not signed"}</td>
                    <td>
                      <button className="ra-link" onClick={() => onViewProfile(user)}>
                        View Profile
                      </button>{" "}
                      <button className="ra-link" onClick={() => resendCode(user)}>
                        Resend code
                      </button>{" "}
                      <button className="ra-link" onClick={() => deleteUser(user)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

    </div>
  );
}
