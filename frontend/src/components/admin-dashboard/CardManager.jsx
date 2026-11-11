import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import MemberSelect from "./MemberSelect";

export default function CardManager() {
  const { authFetch } = useAuth();
  const [cards, setCards] = useState([]);
  const [error, setError] = useState("");
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

  return (
    <div className="HardwareCheckInsContainer">
      <h2>Assign a card</h2>
      <form className="CardForm" onSubmit={assign}>
        <input placeholder="Card UID (e.g. 04:A3:2B:1C)" value={uid} onChange={(e) => setUid(e.target.value)} required />
        <input placeholder="Label (optional)" value={label} onChange={(e) => setLabel(e.target.value)} />
        <MemberSelect value={memberId} onChange={setMemberId} />
        <button className="ViewBtn" type="submit" disabled={!uid.trim() || !memberId}>Assign</button>
      </form>
      <p className="CardMuted">
        Tip: tap the card on the reader first, then use “Assign card” on the Card taps tab — no typing needed.
        Membership, training and permissions are read live, so changing them never needs a rescan.
      </p>
      {error && <div className="HardwareError">{error}</div>}

      <table className="CardTable">
        <thead>
          <tr><th>Card UID</th><th>Member</th><th>Label</th><th>Status</th><th>Last seen</th><th /></tr>
        </thead>
        <tbody>
          {cards.length === 0 && <tr><td colSpan={6} className="CardMuted">No cards assigned yet.</td></tr>}
          {cards.map((c) => (
            <tr key={c.id}>
              <td className="CardUid">{c.cardUid}</td>
              <td>{c.memberName}<br /><small className="CardMuted">{c.memberEmail}</small></td>
              <td>{c.label || "—"}</td>
              <td>{c.active ? "Active" : "Deactivated"}</td>
              <td>{c.lastSeenAt ? new Date(c.lastSeenAt).toLocaleString() : "Never"}</td>
              <td>
                {reassigning === c.id ? (
                  <div className="CardForm">
                    <MemberSelect value={newMemberId} onChange={setNewMemberId} />
                    <button className="ViewBtn" disabled={!newMemberId} onClick={() => reassign(c)}>Save</button>
                    <button className="ViewBtn" onClick={() => setReassigning(null)}>Cancel</button>
                  </div>
                ) : (
                  <div className="CardActions">
                    <button className="ViewBtn" onClick={() => send(`/api/v1/hardware/cards/${c.id}`, "PATCH", { active: !c.active })}>
                      {c.active ? "Deactivate" : "Activate"}
                    </button>
                    <button className="ViewBtn" onClick={() => { setReassigning(c.id); setNewMemberId(""); }}>
                      Reassign
                    </button>
                    <button
                      className="ViewBtn"
                      onClick={() => window.confirm("Remove this card?") && send(`/api/v1/hardware/cards/${c.id}`, "DELETE")}
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
  );
}
