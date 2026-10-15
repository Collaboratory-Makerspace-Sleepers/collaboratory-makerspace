import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import AllUsers from "./AllUsers";
import UserInformation from "./UserInformation";

export default function AdminUsers() {
  const [selectedUser, setSelectedUser] = useState(null);
  const [invite, setInvite] = useState(null);
  const [form, setForm] = useState({ fullName: "", email: "" });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const { authFetch } = useAuth();

  const openInvite = (roleCodes) => {
    setInvite({ roleCodes });
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
        body: JSON.stringify({ ...form, roleCodes: invite.roleCodes, sendInvite: true }),
      });
      if (!response.ok) throw new Error("Unable to send the invite.");
      setMessage(`Invite link sent to ${form.email}.`);
      setForm({ fullName: "", email: "" });
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
            <button onClick={() => openInvite(["STAFF"])}>Create New Staff</button>
          </div>
          <div className="AdUser2">
            <div className="AdUser3">
              <p>Create a new user</p>
              <p>Create a new user to access the makerspace</p>
            </div>
            <button onClick={() => openInvite([])}>Create New User</button>
          </div>
        </div>

        <div className="AdAct">
          <AllUsers
            onViewProfile={setSelectedUser}
            selectedId={selectedUser?.id}
          />
        </div>
      </div>
      {invite && (
        <div className="ra-profile-backdrop" onClick={() => setInvite(null)}>
          <form
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            onSubmit={sendInvite}
          >
            <header className="ra-profile-header">
              <h3>{invite.roleCodes.length ? "Invite Staff" : "Invite User"}</h3>
              <button type="button" onClick={() => setInvite(null)}>Close</button>
            </header>
            <p>Enter the person's details. An invite link is emailed so they can confirm their account.</p>
            <label>
              Full name
              <input
                required
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              />
            </label>
            <label>
              Email
              <input
                required
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </label>
            {message && <p role="status">{message}</p>}
            <button type="submit" disabled={busy}>
              {busy ? "Sending…" : "Send Invite"}
            </button>
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
