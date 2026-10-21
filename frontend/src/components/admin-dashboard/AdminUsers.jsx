import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import AllUsers from "./AllUsers";
import UserInformation from "./UserInformation";

const ROLE_OPTIONS = [
  { value: "MEMBER", label: "Regular member" },
  { value: "STAFF", label: "Staff", elevated: true },
  { value: "INSTRUCTOR", label: "Instructor", elevated: true },
  { value: "ADMIN", label: "Admin", elevated: true },
];

export default function AdminUsers() {
  const [selectedUser, setSelectedUser] = useState(null);
  const [invite, setInvite] = useState(null);
  const [form, setForm] = useState({ fullName: "", email: "" });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [usersVersion, setUsersVersion] = useState(0);
  const { authFetch, hasPermission } = useAuth();
  const canAssignElevated = hasPermission("MANAGE_ROLES");
  const roleOptions = canAssignElevated ? ROLE_OPTIONS : ROLE_OPTIONS.filter((o) => !o.elevated);

  const openInvite = (role) => {
    setInvite({ role });
    setForm({ fullName: "", email: "" });
    setMessage("");
  };

  const sendInvite = async (event) => {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const response = await authFetch("/api/v1/admin/registrations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, roleCodes: [invite.role], sendInvite: true }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Unable to create the account.");
      setMessage(
        data.inviteSent
          ? `Account created. A sign-in code was emailed to ${form.email}.`
          : `Account created for ${form.email}, but the email could not be delivered (the mail provider rejected it; with a Mailtrap demo account only the account owner's address works). They can still sign in by entering their email on the sign-in page and the code will be shown/sent the same way.`
      );
      setForm({ fullName: "", email: "" });
      setUsersVersion((v) => v + 1);
    } catch (err) {
      setMessage(err.message || "Unable to send the invite.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="AdUserRow">
      <div className="AdUser">
        <p>Users</p>

        <div className="AdUser1">
          <div className="AdUser2">
            <div className="AdUser3">
              <p>Create Staff/Instructor</p>
              <p>Create a new staff employee or instructor for classes</p>
            </div>
            <button onClick={() => openInvite("STAFF")}>Create New Staff</button>
          </div>
          <div className="AdUser2">
            <div className="AdUser3">
              <p>Create a new user</p>
              <p>Create a new user to access the makerspace</p>
            </div>
            <button onClick={() => openInvite("MEMBER")}>Create New User</button>
          </div>
        </div>

        <div className="AdAct">
          <AllUsers
            refreshKey={usersVersion}
            onViewProfile={setSelectedUser}
            selectedId={selectedUser?.id}
          />
        </div>
      </div>
      {invite && (
        <div className="ra-profile-backdrop" onClick={() => setInvite(null)}>
          <form
            className="ra-profile-dialog nu-dialog"
            onClick={(event) => event.stopPropagation()}
            onSubmit={sendInvite}
          >
            <header className="nu-header">
              <div>
                <h3>{invite.role === "MEMBER" ? "Create new user" : "Create staff / admin"}</h3>
                <p>They'll get an email with a sign-in code and land on the dashboard that matches their role.</p>
              </div>
              <button type="button" className="nu-close" aria-label="Close" onClick={() => setInvite(null)}>×</button>
            </header>
            <label className="nu-field">
              Full name
              <input
                required
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              />
            </label>
            <label className="nu-field">
              Email
              <input
                required
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label className="nu-field">
              Role
              <select value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value })}>
                {roleOptions.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </label>
            {message && <p className="nu-message" role="status">{message}</p>}
            <div className="nu-actions">
              <button type="button" className="nu-cancel" onClick={() => setInvite(null)}>Cancel</button>
              <button type="submit" disabled={busy}>
                {busy ? "Creating…" : "Create & email code"}
              </button>
            </div>
          </form>
        </div>
      )}
      {selectedUser && (
        <UserInformation
          key={selectedUser.id}
          user={selectedUser}
          onClose={() => setSelectedUser(null)}
        />
      )}
    </div>
  );
}
