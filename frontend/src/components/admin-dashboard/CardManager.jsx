import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import MemberSelect from "./MemberSelect";

export default function CardManager() {
  const { authFetch } = useAuth();
  const [cards, setCards] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [uid, setUid] = useState("");
  const [label, setLabel] = useState("");
  const [memberId, setMemberId] = useState("");
  const [reassigning, setReassigning] = useState(null);
  const [newMemberId, setNewMemberId] = useState("");

  const load = useCallback(async () => {
    try {
      const res = await authFetch("/api/v1/hardware/cards");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setCards(await res.json());
    } catch (err) {
      setError(err.message);
    }
  }, [authFetch]);

  useEffect(() => {
    const timer = setTimeout(load, 0);
    return () => clearTimeout(timer);
  }, [load]);

  async function send(url, method, body) {
    setError("");
    const res = await authFetch(url, { method, body: body ? JSON.stringify(body) : undefined });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      setError(data.message || `Request failed (HTTP ${res.status})`);
      return false;
    }
    setSuccess("✓ Success");
    setTimeout(() => setSuccess(""), 3000);
    await load();
    return true;
  }

  async function assign(event) {
    event.preventDefault();
    const ok = await send("/api/v1/hardware/cards", "POST", {
      cardUid: uid, userId: Number(memberId), label,
    });
    if (ok) {
      setUid("");
      setLabel("");
      setMemberId("");
    }
  }

  async function reassign(card) {
    if (await send(`/api/v1/hardware/cards/${card.id}`, "PATCH", { userId: Number(newMemberId) })) {
      setReassigning(null);
      setNewMemberId("");
    }
  }

  const handleCreateUser = (email) => {
    const message = `To create a new user, go to Admin Dashboard > Users section. You can then assign this card to them.\n\nEmail: ${email}`;
    alert(message);
    // Optional: redirect to users page
    window.location.href = "/admin-dashboard/users";
  };

  return (
    <div className="HardwareCheckInsContainer">
      <h2>Assign a Card to Member</h2>
      
      <div style={{ marginBottom: "20px", padding: "12px", background: "#f9f9f9", borderRadius: "4px", borderLeft: "3px solid #0066cc" }}>
        <p style={{ margin: "0 0 8px 0", fontSize: "13px" }}>
          <strong>How to assign:</strong>
        </p>
        <ol style={{ margin: "0", paddingLeft: "20px", fontSize: "13px" }}>
          <li>Enter card UID or tap card on reader</li>
          <li>Search and select a member</li>
          <li>Click "Assign" to link card to member</li>
        </ol>
      </div>

      <form className="CardForm" onSubmit={assign}>
        <div style={{ marginBottom: "12px" }}>
          <label style={{ display: "block", fontSize: "12px", fontWeight: "500", marginBottom: "4px" }}>
            Card UID
          </label>
          <input 
            placeholder="e.g. 85F10CD4" 
            value={uid} 
            onChange={(e) => setUid(e.target.value)} 
            required 
            style={{ width: "100%" }}
          />
        </div>

        <div style={{ marginBottom: "12px" }}>
          <label style={{ display: "block", fontSize: "12px", fontWeight: "500", marginBottom: "4px" }}>
            Label (optional)
          </label>
          <input 
            placeholder="e.g. Work Card" 
            value={label} 
            onChange={(e) => setLabel(e.target.value)}
            style={{ width: "100%" }}
          />
        </div>

        <div style={{ marginBottom: "12px" }}>
          <label style={{ display: "block", fontSize: "12px", fontWeight: "500", marginBottom: "4px" }}>
            Select Member
          </label>
          <MemberSelect 
            value={memberId} 
            onChange={setMemberId}
            onCreateUser={handleCreateUser}
          />
          <div style={{ fontSize: "11px", color: "#666", marginTop: "4px" }}>
            Can't find the member? <a href="/admin-dashboard/users" style={{ color: "#0066cc", textDecoration: "none" }}>Create new user</a>
          </div>
        </div>

        <button 
          className="ViewBtn" 
          type="submit" 
          disabled={!uid.trim() || !memberId}
          style={{ width: "100%" }}
        >
          Assign Card
        </button>
      </form>

      {error && <div className="HardwareError" style={{ marginTop: "12px" }}>{error}</div>}
      {success && <div className="HardwareSuccess" style={{ marginTop: "12px" }}>{success}</div>}

      <div style={{ marginTop: "30px" }}>
        <h3>Assigned Cards ({cards.length})</h3>
        <table className="CardTable">
          <thead>
            <tr>
              <th>Card UID</th>
              <th>Member</th>
              <th>Label</th>
              <th>Status</th>
              <th>Last Seen</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {cards.length === 0 && (
              <tr>
                <td colSpan={6} className="CardMuted">No cards assigned yet.</td>
              </tr>
            )}
            {cards.map((c) => (
              <tr key={c.id}>
                <td className="CardUid">{c.cardUid}</td>
                <td>
                  <div style={{ fontWeight: "500" }}>{c.memberName}</div>
                  <div style={{ fontSize: "12px", color: "#666" }}>{c.memberEmail}</div>
                </td>
                <td>{c.label || "—"}</td>
                <td>
                  <span style={{
                    padding: "4px 8px",
                    borderRadius: "3px",
                    fontSize: "12px",
                    background: c.active ? "#d4edda" : "#f8d7da",
                    color: c.active ? "#155724" : "#721c24"
                  }}>
                    {c.active ? "Active" : "Deactivated"}
                  </span>
                </td>
                <td style={{ fontSize: "12px" }}>
                  {c.lastSeenAt ? new Date(c.lastSeenAt).toLocaleString() : "Never"}
                </td>
                <td>
                  {reassigning === c.id ? (
                    <div style={{ display: "flex", gap: "4px", flexDirection: "column" }}>
                      <div style={{ fontSize: "11px", marginBottom: "4px" }}>Reassign to:</div>
                      <MemberSelect value={newMemberId} onChange={setNewMemberId} />
                      <div style={{ display: "flex", gap: "4px", marginTop: "4px" }}>
                        <button 
                          className="ViewBtn" 
                          disabled={!newMemberId} 
                          onClick={() => reassign(c)}
                          style={{ flex: 1, fontSize: "12px", padding: "4px" }}
                        >
                          Save
                        </button>
                        <button 
                          className="ViewBtn" 
                          onClick={() => setReassigning(null)}
                          style={{ flex: 1, fontSize: "12px", padding: "4px" }}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div style={{ display: "flex", gap: "4px", flexWrap: "wrap" }}>
                      <button 
                        className="ViewBtn" 
                        onClick={() => send(`/api/v1/hardware/cards/${c.id}`, "PATCH", { active: !c.active })}
                        style={{ fontSize: "11px", padding: "4px 8px" }}
                      >
                        {c.active ? "Deactivate" : "Activate"}
                      </button>
                      <button 
                        className="ViewBtn" 
                        onClick={() => { setReassigning(c.id); setNewMemberId(""); }}
                        style={{ fontSize: "11px", padding: "4px 8px" }}
                      >
                        Reassign
                      </button>
                      <button
                        className="ViewBtn"
                        onClick={() => window.confirm("Remove this card?") && send(`/api/v1/hardware/cards/${c.id}`, "DELETE")}
                        style={{ fontSize: "11px", padding: "4px 8px", color: "#d9534f" }}
                      >
                        Remove
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
