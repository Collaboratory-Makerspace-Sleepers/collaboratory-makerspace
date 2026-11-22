import { useCallback, useEffect, useId, useMemo, useState } from "react";
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
  const filterId = useId();
  const [users, setUsers] = useState([]);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [resendNote, setResendNote] = useState("");
  const [showDeleted, setShowDeleted] = useState(false);
  const [roleFilter, setRoleFilter] = useState("");
  const [membershipFilter, setMembershipFilter] = useState("");
  const [waiverFilter, setWaiverFilter] = useState("");

  const load = useCallback(() => {
    const url = `/api/v1/users/activity?page=0&size=${FETCH_SIZE}&sort=createdAt,desc${showDeleted ? "&includeDeleted=true" : ""}`;
    return authFetch(url)
      .then(async (response) => {
        if (!response.ok) throw new Error("Could not load users.");
        return response.json();
      })
      .then((result) => {
        setUsers(Array.isArray(result.content) ? result.content : []);
        setError("");
      })
      .catch((loadError) => {
        setError(loadError.message || "Could not load users.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [authFetch, showDeleted]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const wrappedLoad = () => {
      if (cancelled) return;
      load();
    };
    wrappedLoad();
    const timer = setInterval(() => {
      if (!document.hidden && !cancelled) load();
    }, 10000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [load, refreshKey]);

  const membershipOptions = useMemo(() => {
    const plans = new Set(users.map((u) => u.membership).filter((m) => m && m !== "None"));
    return Array.from(plans).sort();
  }, [users]);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    return users.filter((user) => {
      if (term && !fullName(user).toLowerCase().includes(term) && !(user.email || "").toLowerCase().includes(term) &&
          !(user.roles ?? []).some((role) => role.toLowerCase().includes(term))) return false;
      if (roleFilter && !(user.roles ?? []).includes(roleFilter)) return false;
      if (membershipFilter && user.membership !== membershipFilter) return false;
      if (waiverFilter === "signed" && !user.waiverSigned) return false;
      if (waiverFilter === "unsigned" && user.waiverSigned) return false;
      return true;
    });
  }, [users, query, roleFilter, membershipFilter, waiverFilter]);

  const activeFiltered = filtered.filter((u) => !u.deleted);
  const deletedFiltered = filtered.filter((u) => u.deleted);

  const totalPages = Math.ceil(activeFiltered.length / PAGE_SIZE);
  const visible = activeFiltered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const hasFilters = Boolean(query.trim() || roleFilter || membershipFilter || waiverFilter);

  const deleteUser = async (user) => {
    const label = user.email || fullName(user);
    if (!window.confirm(`Delete ${label}? They will lose access to the makerspace.`)) return;
    setResendNote("");
    try {
      const res = await authFetch(`/api/v1/users/${user.id}`, { method: "DELETE" });
      if (res.ok) {
        await load();
        setResendNote(`Deleted ${label}.`);
      } else {
        const data = await res.json().catch(() => ({}));
        setResendNote(data.message || `Could not delete ${label}.`);
      }
    } catch {
      setResendNote("Could not delete the user.");
    }
  };

  const restoreUser = async (user) => {
    const label = user.email || fullName(user);
    setResendNote("");
    try {
      const res = await authFetch(`/api/v1/users/${user.id}/restore`, { method: "POST" });
      if (res.ok) {
        await load();
        setResendNote(`Restored ${label}.`);
      } else {
        const data = await res.json().catch(() => ({}));
        setResendNote(data.message || `Could not restore ${label}.`);
      }
    } catch {
      setResendNote("Could not restore the user.");
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

  function resetFilters() {
    setQuery("");
    setRoleFilter("");
    setMembershipFilter("");
    setWaiverFilter("");
    setPage(0);
  }

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

        <div className="au-filters" role="group" aria-label="User filters">
          <div className="au-filter-fields">
            <label className="au-filter-field au-filter-field--search">
              Search users
              <input
                type="search"
                placeholder="Name, email, or role"
                value={query}
                onChange={(e) => { setQuery(e.target.value); setPage(0); }}
              />
            </label>

            <div className="au-filter-field">
              <label htmlFor={`${filterId}-role`}>Role</label>
              <select id={`${filterId}-role`} value={roleFilter} onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}>
                <option value="">All roles</option>
                {["MEMBER", "STAFF", "INSTRUCTOR", "ADMIN"].map((r) => (
                  <option key={r} value={r}>{r[0] + r.slice(1).toLowerCase()}</option>
                ))}
              </select>
            </div>

            <div className="au-filter-field">
              <label htmlFor={`${filterId}-membership`}>Membership</label>
              <select id={`${filterId}-membership`} value={membershipFilter} onChange={(e) => { setMembershipFilter(e.target.value); setPage(0); }}>
                <option value="">All memberships</option>
                <option value="None">None</option>
                {membershipOptions.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>

            <div className="au-filter-field">
              <label htmlFor={`${filterId}-waiver`}>Waiver status</label>
              <select id={`${filterId}-waiver`} value={waiverFilter} onChange={(e) => { setWaiverFilter(e.target.value); setPage(0); }}>
                <option value="">All waivers</option>
                <option value="signed">Signed</option>
                <option value="unsigned">Not signed</option>
              </select>
            </div>
          </div>

          <div className="au-filter-actions">
            <label className="au-deleted-filter">
              <input
                type="checkbox"
                checked={showDeleted}
                onChange={(e) => { setShowDeleted(e.target.checked); setPage(0); }}
              />
              Deleted accounts only
            </label>

            {hasFilters && (
              <button type="button" className="ra-link" onClick={resetFilters}>Clear filters</button>
            )}
          </div>
        </div>

        {loading ? (
          <p className="ra-state" role="status">Loading users…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : (
          <>
            {resendNote && <p className="ra-state" role="status">{resendNote}</p>}

            {!showDeleted && (
              visible.length === 0 ? (
                <p className="ra-state">{hasFilters ? "No users match your filters." : "No users yet."}</p>
              ) : (
                <div className="ra-table-scroll">
                  <table className="ra-table au-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Email</th>
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
                        <tr
                          key={user.id}
                          className={user.id === selectedId ? "au-selected" : undefined}
                        >
                          <td>{user.id}</td>
                          <td title={user.email}>{user.email || "—"}</td>
                          <td>{fullName(user) || user.email || "Unknown user"}</td>
                          <td>{formatDate(user.createdAt)}</td>
                          <td>{user.membership || "None"}</td>
                          <td>{user.allowed || "Not allowed"}</td>
                          <td>{user.waiverSigned ? "Signed" : "Not signed"}</td>
                          <td className="ae-cell">
                            <div className="ae-actions">
                              <button className="ra-link" onClick={() => onViewProfile(user)}>View Profile</button>
                              <button className="ra-link" onClick={() => resendCode(user)}>Resend code</button>
                              <button className="ra-link" onClick={() => deleteUser(user)}>Delete</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            )}

            {showDeleted && (
              deletedFiltered.length === 0 ? (
                <p className="ra-state">{hasFilters ? "No deleted accounts match your filters." : "No deleted accounts."}</p>
              ) : (
                <div className="ra-table-scroll">
                  <table className="ra-table au-table" style={{ opacity: 0.75 }}>
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Email</th>
                        <th>Name</th>
                        <th>Time</th>
                        <th>Membership</th>
                        <th>Allowed</th>
                        <th>Waiver</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {deletedFiltered.map((user) => (
                        <tr key={user.id}>
                          <td>{user.id}</td>
                          <td title={user.email}>{user.email || "—"}</td>
                          <td>{fullName(user) || user.email || "Unknown user"}</td>
                          <td>{formatDate(user.createdAt)}</td>
                          <td>{user.membership || "None"}</td>
                          <td>{user.allowed || "Not allowed"}</td>
                          <td>{user.waiverSigned ? "Signed" : "Not signed"}</td>
                          <td className="ae-cell">
                            <div className="ae-actions">
                              <button className="ra-link" onClick={() => restoreUser(user)}>Restore</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            )}
          </>
        )}
      </div>

    </div>
  );
}
