import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

export default function AdminRoles() {
  const { authFetch } = useAuth();
  const [roles, setRoles] = useState([]);
  const [allPerms, setAllPerms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({ code: "", description: "" });
  const [createBusy, setCreateBusy] = useState(false);
  const [createMsg, setCreateMsg] = useState("");

  const [editRole, setEditRole] = useState(null);
  const [checkedPerms, setCheckedPerms] = useState([]);
  const [editBusy, setEditBusy] = useState(false);
  const [editMsg, setEditMsg] = useState("");

  const loadRoles = async () => {
    try {
      const [rolesRes, permsRes] = await Promise.all([
        authFetch("/api/v1/admin/roles"),
        authFetch("/api/v1/admin/permissions"),
      ]);
      if (rolesRes.ok) setRoles(await rolesRes.json());
      if (permsRes.ok) setAllPerms(await permsRes.json());
      setError("");
    } catch {
      setError("Could not load roles.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadRoles(); }, [authFetch]);

  const openCreate = () => {
    setCreateForm({ code: "", description: "" });
    setCreateMsg("");
    setCreateOpen(true);
  };

  const submitCreate = async (e) => {
    e.preventDefault();
    setCreateBusy(true);
    setCreateMsg("");
    try {
      const res = await authFetch("/api/v1/admin/roles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code: createForm.code.toUpperCase(), description: createForm.description }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || "Could not create role.");
      }
      setCreateOpen(false);
      loadRoles();
    } catch (err) {
      setCreateMsg(err.message);
    } finally {
      setCreateBusy(false);
    }
  };

  const openEdit = (role) => {
    setEditRole(role);
    setCheckedPerms(Array.isArray(role.permissions) ? [...role.permissions] : []);
    setEditMsg("");
  };

  const submitEdit = async (e) => {
    e.preventDefault();
    setEditBusy(true);
    setEditMsg("");
    try {
      const res = await authFetch(`/api/v1/admin/roles/${editRole.code}/permissions`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ permissions: checkedPerms }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || "Could not update permissions.");
      }
      setEditRole(null);
      loadRoles();
    } catch (err) {
      setEditMsg(err.message);
    } finally {
      setEditBusy(false);
    }
  };

  const deleteRole = async (role) => {
    if (!window.confirm(`Delete role "${role.code}"? This cannot be undone.`)) return;
    try {
      const res = await authFetch(`/api/v1/admin/roles/${role.code}`, { method: "DELETE" });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError(data.message || `Could not delete role ${role.code}.`);
        return;
      }
      loadRoles();
    } catch {
      setError("Could not delete role.");
    }
  };

  const togglePerm = (perm) => {
    setCheckedPerms((prev) =>
      prev.includes(perm) ? prev.filter((p) => p !== perm) : [...prev, perm]
    );
  };

  return (
    <div className="AdUser">
      <h1 className="admin-page-title">Roles</h1>
      <div className="admin-actions-row">
        <button onClick={openCreate}>Create Role</button>
      </div>

      {loading ? (
        <p className="ra-state" role="status">Loading roles…</p>
      ) : error ? (
        <p className="ra-state ra-error" role="alert">{error}</p>
      ) : (
        <div className="AdAct">
          <div className="ra-wrapper">
            <div className="ra-card">
              <div className="ra-table-scroll">
                <table className="ra-table roles-table">
                  <thead>
                    <tr>
                      <th>Code</th>
                      <th>Description</th>
                      <th>Permissions</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roles.map((role) => (
                      <tr key={role.code}>
                        <td><strong>{role.code}</strong></td>
                        <td>{role.description || "—"}</td>
                        <td className="roles-perms">
                          {(role.permissions?.length ?? 0) === 0
                            ? <span className="roles-perm-none">None</span>
                            : role.permissions.map((p) => (
                              <span key={p} className="roles-perm-badge">{p}</span>
                            ))
                          }
                        </td>
                        <td>
                          <button className="ra-link" onClick={() => openEdit(role)}>
                            Edit Permissions
                          </button>{" "}
                          <button
                            className="ra-link"
                            onClick={() => deleteRole(role)}
                            disabled={role.isSystem}
                            title={role.isSystem ? "System role — cannot be deleted" : undefined}
                            style={role.isSystem ? { opacity: 0.4, cursor: "not-allowed" } : undefined}
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {createOpen && (
        <div className="ra-profile-backdrop" onClick={() => setCreateOpen(false)}>
          <form
            className="ra-profile-dialog nu-dialog"
            onClick={(e) => e.stopPropagation()}
            onSubmit={submitCreate}
          >
            <header className="nu-header">
              <div>
                <h3>Create Role</h3>
                <p>Define a new permission role for the makerspace.</p>
              </div>
              <button type="button" className="nu-close" aria-label="Close" onClick={() => setCreateOpen(false)}>×</button>
            </header>
            <label className="nu-field">
              Code
              <input
                required
                placeholder="e.g. INSTRUCTOR"
                value={createForm.code}
                onChange={(e) => setCreateForm({ ...createForm, code: e.target.value.toUpperCase() })}
              />
            </label>
            <label className="nu-field">
              Description
              <input
                value={createForm.description}
                onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
              />
            </label>
            {createMsg && <p className="nu-message" role="alert">{createMsg}</p>}
            <div className="nu-actions">
              <button type="button" className="nu-cancel" onClick={() => setCreateOpen(false)}>Cancel</button>
              <button type="submit" disabled={createBusy}>{createBusy ? "Creating…" : "Create Role"}</button>
            </div>
          </form>
        </div>
      )}

      {editRole && (
        <div className="ra-profile-backdrop" onClick={() => setEditRole(null)}>
          <form
            className="ra-profile-dialog nu-dialog"
            onClick={(e) => e.stopPropagation()}
            onSubmit={submitEdit}
          >
            <header className="nu-header">
              <div>
                <h3>Edit Permissions — {editRole.code}</h3>
                <p>Check the permissions this role should have.</p>
              </div>
              <button type="button" className="nu-close" aria-label="Close" onClick={() => setEditRole(null)}>×</button>
            </header>
            <div className="roles-perm-list">
              {allPerms.map((perm) => (
                <label key={perm} className="nu-check">
                  <input
                    type="checkbox"
                    checked={checkedPerms.includes(perm)}
                    onChange={() => togglePerm(perm)}
                  />
                  {perm}
                </label>
              ))}
            </div>
            {editMsg && <p className="nu-message" role="alert">{editMsg}</p>}
            <div className="nu-actions">
              <button type="button" className="nu-cancel" onClick={() => setEditRole(null)}>Cancel</button>
              <button type="submit" disabled={editBusy}>{editBusy ? "Saving…" : "Save Permissions"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}