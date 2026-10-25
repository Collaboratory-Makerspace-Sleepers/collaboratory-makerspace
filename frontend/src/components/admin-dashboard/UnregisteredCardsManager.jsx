import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

export default function UnregisteredCardsManager() {
  const { authFetch } = useAuth();
  const [unregisteredCards, setUnregisteredCards] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [assigningId, setAssigningId] = useState(null);
  const [assignEmail, setAssignEmail] = useState("");
  const [sendingOtpId, setSendingOtpId] = useState(null);

  const load = useCallback(async () => {
    try {
      setError("");
      const res = await authFetch("/api/v1/hardware/unregistered-cards");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setUnregisteredCards(await res.json());
    } catch (err) {
      setError(err.message);
    }
  }, [authFetch]);

  useEffect(() => {
    const timer = setTimeout(load, 0);
    return () => clearTimeout(timer);
  }, [load]);

  // Auto-refresh every 10 seconds to see new cards as they tap
  useEffect(() => {
    const interval = setInterval(load, 10000);
    return () => clearInterval(interval);
  }, [load]);

  async function assignCardToEmail(cardId, email) {
    try {
      setError("");
      const res = await authFetch(`/api/v1/hardware/unregistered-cards/${cardId}/assign`, {
        method: "POST",
        body: JSON.stringify({ email: email.trim() }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${res.status}`);
      }
      setSuccess("Card assigned to email. Now send OTP to activate.");
      setAssigningId(null);
      setAssignEmail("");
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function sendOtpToUser(cardId) {
    try {
      setError("");
      setSendingOtpId(cardId);
      const res = await authFetch(`/api/v1/hardware/unregistered-cards/${cardId}/send-otp`, {
        method: "POST",
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${res.status}`);
      }
      setSuccess("OTP has been sent to the user's email!");
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSendingOtpId(null);
    }
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return "—";
    return new Date(dateStr).toLocaleString();
  };

  return (
    <div className="HardwareCheckInsContainer">
      <h2>Unregistered Cards</h2>
      <p className="CardMuted">
        New cards that have been tapped but not yet assigned to a member. Tap "Assign" to assign to an email, 
        then "Send OTP" to send an activation code. The user will sign in with that code and tap the card again to activate.
      </p>

      {error && <div className="HardwareError">{error}</div>}
      {success && <div className="HardwareSuccess">{success}</div>}

      {unregisteredCards.length === 0 ? (
        <div className="CardMuted" style={{ padding: "20px", textAlign: "center" }}>
          No unregistered cards found. New cards will appear here when they are first tapped.
        </div>
      ) : (
        <table className="CardTable">
          <thead>
            <tr>
              <th>Card UID</th>
              <th>Status</th>
              <th>Assigned Email</th>
              <th>First Tap</th>
              <th>Last Tap</th>
              <th>Taps</th>
              <th>Device</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {unregisteredCards.map((card) => (
              <tr key={card.id}>
                <td className="CardUid">{card.cardUid}</td>
                <td>
                  <span style={{
                    padding: "4px 8px",
                    borderRadius: "4px",
                    backgroundColor: card.status === "VERIFIED" ? "#d4edda" : 
                                     card.status === "ASSIGNED" ? "#fff3cd" : "#e7e7e7",
                  }}>
                    {card.status}
                  </span>
                </td>
                <td>{card.assignedToEmail || "—"}</td>
                <td>{formatDate(card.firstScannedAt)}</td>
                <td>{formatDate(card.lastScannedAt)}</td>
                <td>{card.scanCount}</td>
                <td>{card.deviceId || "—"}</td>
                <td>
                  {assigningId === card.id ? (
                    <div style={{ display: "flex", gap: "4px", flexDirection: "column" }}>
                      <input
                        type="email"
                        placeholder="user@example.com"
                        value={assignEmail}
                        onChange={(e) => setAssignEmail(e.target.value)}
                        autoFocus
                      />
                      <div style={{ display: "flex", gap: "4px" }}>
                        <button
                          className="ViewBtn"
                          onClick={() => assignCardToEmail(card.id, assignEmail)}
                          disabled={!assignEmail.trim()}
                        >
                          Assign
                        </button>
                        <button
                          className="ViewBtn"
                          onClick={() => {
                            setAssigningId(null);
                            setAssignEmail("");
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div style={{ display: "flex", gap: "4px" }}>
                      {!card.assignedToEmail && (
                        <button
                          className="ViewBtn"
                          onClick={() => {
                            setAssigningId(card.id);
                            setAssignEmail("");
                          }}
                        >
                          Assign Email
                        </button>
                      )}
                      {card.assignedToEmail && !card.otpVerified && (
                        <button
                          className="ViewBtn"
                          onClick={() => sendOtpToUser(card.id)}
                          disabled={sendingOtpId === card.id}
                        >
                          {sendingOtpId === card.id ? "Sending..." : "Send OTP"}
                        </button>
                      )}
                      {card.otpVerified && (
                        <span style={{ color: "green", fontWeight: "bold" }}>✓ OTP Verified</span>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
