import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const ADMIN_PERMISSIONS = ["MANAGE_USERS", "MANAGE_ROLES"];

function StatusBanner({ error, notice }) {
  if (error) return <p className="AdminBanner AdminBannerError">{error}</p>;
  if (notice) return <p className="AdminBanner AdminBannerOk">{notice}</p>;
  return null;
}

// ---------------------------------------------------------------------------
// Users — requires MANAGE_USERS to list, MANAGE_ROLES to reassign.
// ---------------------------------------------------------------------------
function UsersTab() {
  const { authFetch, user: me, hasPermission } = useAuth();
  const [data, setData] = useState(null);
  const [roles, setRoles] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyId, setBusyId] = useState(null);

  const canEditRoles = hasPermission("MANAGE_ROLES");

  const load = useCallback(async () => {
    setError("");
    try {
      const res = await authFetch("/api/v1/users?size=100");
      if (!res.ok) throw new Error("Could not load users");
      setData(await res.json());
    } catch (e) {
      setError(e.message);
    }
  }, [authFetch]);

  useEffect(() => {
    load();
  }, [load]);

  // Role codes are needed for the reassign dropdown. That endpoint is gated on
  // MANAGE_ROLES, so a MANAGE_USERS-only admin simply doesn't get a picker.
  useEffect(() => {
    if (!canEditRoles) return;
    authFetch("/api/v1/admin/roles")
      .then((res) => (res.ok ? res.json() : []))
      .then((list) => setRoles(Array.isArray(list) ? list : []))
      .catch(() => setRoles([]));
  }, [authFetch, canEditRoles]);

  async function changeRole(targetUser, roleCode) {
    setBusyId(targetUser.id);
    setError("");
    setNotice("");
    try {
      const res = await authFetch(`/api/v1/users/${targetUser.id}/role`, {
        method: "PATCH",
        body: JSON.stringify({ roleCodes: roleCode ? [roleCode] : [] }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || "Could not update role");
      }
      setNotice(`Updated ${targetUser.email}.`);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  async function removeUser(targetUser) {
    if (!window.confirm(`Soft-delete ${targetUser.email}? Their data is retained.`)) return;
    setBusyId(targetUser.id);
    setError("");
    setNotice("");
    try {
      const res = await authFetch(`/api/v1/users/${targetUser.id}`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || "Could not delete user");
      }
      setNotice(`Soft-deleted ${targetUser.email}.`);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="AdminSection">
      <header className="AdminSectionHead">
        <h2>Users</h2>
        <button className="AdminBtnGhost" onClick={load}>Refresh</button>
      </header>

      <StatusBanner error={error} notice={notice} />

      {!data ? (
        <p className="AdminEmpty">Loading users…</p>
      ) : data.content.length === 0 ? (
        <p className="AdminEmpty">No users found.</p>
      ) : (
        <table className="AdminTable">
          <thead>
            <tr>
              <th>Email</th>
              <th>Name</th>
              <th>Roles</th>
              {canEditRoles && <th>Reassign</th>}
              <th />
            </tr>
          </thead>
          <tbody>
            {data.content.map((u) => {
              const isSelf = me && u.id === me.id;
              return (
                <tr key={u.id}>
                  <td>
                    {u.email}
                    {isSelf && <span className="AdminYou">you</span>}
                  </td>
                  <td>{[u.firstName, u.lastName].filter(Boolean).join(" ") || "—"}</td>
                  <td>
                    {u.roles.length === 0
                      ? <span className="AdminMuted">none</span>
                      : u.roles.map((r) => (
                          <span className="AdminChip" key={r}>{r}</span>
                        ))}
                  </td>
                  {canEditRoles && (
                    <td>
                      {/* The API refuses self-role changes and last-admin removal;
                          disabling the control makes that visible up front. */}
                      <select
                        className="AdminSelect"
                        value={u.roles[0] || ""}
                        disabled={isSelf || busyId === u.id}
                        onChange={(e) => changeRole(u, e.target.value)}
                      >
                        <option value="">none</option>
                        {roles.map((r) => (
                          <option key={r.code} value={r.code}>{r.code}</option>
                        ))}
                      </select>
                    </td>
                  )}
                  <td>
                    {!isSelf && (
                      <button
                        className="AdminBtnDanger"
                        disabled={busyId === u.id}
                        onClick={() => removeUser(u)}
                      >
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {data && data.totalElements > data.content.length && (
        <p className="AdminMuted">
          Showing {data.content.length} of {data.totalElements}.
        </p>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Roles & permissions — requires MANAGE_ROLES.
// ---------------------------------------------------------------------------
function RolesTab() {
  const { authFetch } = useAuth();
  const [roles, setRoles] = useState([]);
  const [allPermissions, setAllPermissions] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [newCode, setNewCode] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      const [rolesRes, permsRes] = await Promise.all([
        authFetch("/api/v1/admin/roles"),
        authFetch("/api/v1/admin/permissions"),
      ]);
      if (!rolesRes.ok) throw new Error("Could not load roles");
      setRoles(await rolesRes.json());
      setAllPermissions(permsRes.ok ? await permsRes.json() : []);
    } catch (e) {
      setError(e.message);
    }
  }, [authFetch]);

  useEffect(() => {
    load();
  }, [load]);

  async function togglePermission(role, permission, granted) {
    setBusy(true);
    setError("");
    setNotice("");
    const next = granted
      ? role.permissions.filter((p) => p !== permission)
      : [...role.permissions, permission];
    try {
      const res = await authFetch(`/api/v1/admin/roles/${role.code}/permissions`, {
        method: "PUT",
        body: JSON.stringify({ permissions: next }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || "Could not update permissions");
      }
      setNotice(`Updated ${role.code}.`);
      await load();
    } catch (e) {
      setError(e.message);
      // Permission edits are all-or-nothing server-side, so resync to the
      // authoritative state rather than leaving a stale checkbox.
      await load();
    } finally {
      setBusy(false);
    }
  }

  async function createRole() {
    const code = newCode.trim().toUpperCase();
    if (!code) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await authFetch("/api/v1/admin/roles", {
        method: "POST",
        body: JSON.stringify({ code, description: `${code} role` }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || "Could not create role");
      }
      setNotice(`Created ${code}.`);
      setNewCode("");
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteRole(role) {
    if (!window.confirm(`Delete role ${role.code}?`)) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await authFetch(`/api/v1/admin/roles/${role.code}`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || "Could not delete role");
      }
      setNotice(`Deleted ${role.code}.`);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="AdminSection">
      <header className="AdminSectionHead">
        <h2>Roles &amp; Permissions</h2>
        <button className="AdminBtnGhost" onClick={load}>Refresh</button>
      </header>

      <StatusBanner error={error} notice={notice} />

      <div className="AdminNewRole">
        <input
          className="AdminSelect"
          placeholder="NEW_ROLE_CODE"
          value={newCode}
          onChange={(e) => setNewCode(e.target.value)}
        />
        <button
          className="AdminBtnPrimary"
          disabled={busy || !newCode.trim()}
          onClick={createRole}
        >
          Create role
        </button>
      </div>

      {roles.length === 0 ? (
        <p className="AdminEmpty">No roles found.</p>
      ) : (
        roles.map((role) => (
          <div className="AdminRoleCard" key={role.code}>
            <div className="AdminRoleHead">
              <div>
                <strong>{role.code}</strong>
                {role.isSystem && <span className="AdminChip">system</span>}
                <p className="AdminMuted">{role.description}</p>
              </div>
              {!role.isSystem && (
                <button
                  className="AdminBtnDanger"
                  disabled={busy}
                  onClick={() => deleteRole(role)}
                >
                  Delete
                </button>
              )}
            </div>

            <div className="AdminPermGrid">
              {allPermissions.map((p) => {
                const granted = role.permissions.includes(p);
                return (
                  <label className="AdminPerm" key={p}>
                    <input
                      type="checkbox"
                      checked={granted}
                      disabled={busy}
                      onChange={() => togglePermission(role, p, granted)}
                    />
                    <span>{p}</span>
                  </label>
                );
              })}
            </div>
          </div>
        ))
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
export default function AdminPanel() {
  const { hasAnyPermission } = useAuth();
  const [tab, setTab] = useState("users");

  if (!hasAnyPermission(ADMIN_PERMISSIONS)) {
    return (
      <div className="AdminDenied">
        <h2>Access denied</h2>
        <p>Your account does not have permission to view this page.</p>
      </div>
    );
  }

  return (
    <div className="AdminPanel">
      <h1 className="AdminTitle">Admin</h1>

      <nav className="AdminTabs">
        <button
          className={tab === "users" ? "AdminTab active" : "AdminTab"}
          onClick={() => setTab("users")}
        >
          Users
        </button>
        <button
          className={tab === "roles" ? "AdminTab active" : "AdminTab"}
          onClick={() => setTab("roles")}
        >
          Roles &amp; Permissions
        </button>
      </nav>

      {tab === "users" ? <UsersTab /> : <RolesTab />}
    </div>
  );
}
