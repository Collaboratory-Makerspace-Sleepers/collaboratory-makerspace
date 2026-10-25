import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

export default function UnregisteredCardsManager() {
  const { authFetch } = useAuth();
  const [unregisteredCards, setUnregisteredCards] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [assigningId, setAssigningId] = useState(null);
  const [assignEmail, setAssignEmail] = useState("");
  const [assigningRoleId, setAssigningRoleId] = useState(null);
  const [assignRole, setAssignRole] = useState("MEMBER");
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
      setSuccess("Card assigned to email. Now assign role and send OTP.");
      setAssigningId(null);
      setAssignEmail("");
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function assignCardRole(cardId, role) {
    try {
      setError("");
      const res = await authFetch(`/api/v1/hardware/unregistered-cards/${cardId}/assign-role`, {
        method: "POST",
        body: JSON.stringify({ role: role.trim() }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${res.status}`);
      }
      setSuccess(`Role assigned as ${role}. User will see appropriate dashboard after sign-in.`);
      setAssigningRoleId(null);
      setAssignRole("MEMBER");
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

  async function deleteCard(cardId, cardUid) {
    if (!window.confirm(`Delete card ${cardUid}? This will remove it from the queue for testing.`)) {
      return;
    }
    try {
      setError("");
      const res = await authFetch(`/api/v1/hardware/unregistered-cards/${cardId}`, {
        method: "DELETE",
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${res.status}`);
      }
      setSuccess(`Card ${cardUid} has been deactivated and removed.`);
      await load();
    } catch (err) {
      setError(err.message);
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
        New cards that have been tapped but not yet assigned to a member. 
        <strong>Steps:</strong>
        <ol>
          <li>Click "Assign Email" to associate card with user email</li>
          <li>Click "Assign Role" to set user's access level (Regular Member, Admin, or Staff)</li>
          <li>Click "Send OTP" to email the activation code</li>
          <li>User signs in with email + OTP and taps card to activate</li>
        </ol>
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
              <th>Assigned Role</th>
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
                <td>
                  <span style={{
                    padding: "4px 8px",
                    borderRadius: "4px",
                    backgroundColor: card.assignedRole ? "#e3f2fd" : "transparent",
                    fontWeight: card.assignedRole ? "bold" : "normal",
                  }}>
                    {card.assignedRole || "—"}
                  </span>
                </td>
                <td>{formatDate(card.firstScannedAt)}</td>
                <td>{formatDate(card.lastScannedAt)}</td>
                <td>{card.scanCount}</td>
                <td>{card.deviceId || "—"}</td>
                <td style={{ minWidth: "200px" }}>
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                    {/* Email Assignment Row */}
                    {assigningId === card.id ? (
                      <div style={{ display: "flex", gap: "4px", flexDirection: "column" }}>
                        <input
                          type="email"
                          placeholder="user@example.com"
                          value={assignEmail}
                          onChange={(e) => setAssignEmail(e.target.value)}
                          autoFocus
                          style={{ padding: "4px 8px", fontSize: "12px" }}
                        />
                        <div style={{ display: "flex", gap: "4px" }}>
                          <button
                            className="ViewBtn"
                            onClick={() => assignCardToEmail(card.id, assignEmail)}
                            disabled={!assignEmail.trim()}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Assign
                          </button>
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setAssigningId(null);
                              setAssignEmail("");
                            }}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        {!card.assignedToEmail && (
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setAssigningId(card.id);
                              setAssignEmail("");
                            }}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Assign Email
                          </button>
                        )}
                      </>
                    )}

                    {/* Role Assignment Row */}
                    {card.assignedToEmail && !card.assignedRole && assigningRoleId === card.id ? (
                      <div style={{ display: "flex", gap: "4px", flexDirection: "column" }}>
                        <select
                          value={assignRole}
                          onChange={(e) => setAssignRole(e.target.value)}
                          autoFocus
                          style={{ padding: "4px 8px", fontSize: "12px" }}
                        >
                          <option value="MEMBER">Regular Member</option>
                          <option value="STAFF">Staff</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                        <div style={{ display: "flex", gap: "4px" }}>
                          <button
                            className="ViewBtn"
                            onClick={() => assignCardRole(card.id, assignRole)}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Set Role
                          </button>
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setAssigningRoleId(null);
                              setAssignRole("MEMBER");
                            }}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        {card.assignedToEmail && !card.assignedRole && (
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setAssigningRoleId(card.id);
                              setAssignRole("MEMBER");
                            }}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Assign Role
                          </button>
                        )}
                      </>
                    )}

                    {/* Send OTP Row */}
                    {card.assignedToEmail && card.assignedRole && !card.otpVerified && (
                      <button
                        className="ViewBtn"
                        onClick={() => sendOtpToUser(card.id)}
                        disabled={sendingOtpId === card.id}
                        style={{ fontSize: "12px", padding: "4px 8px" }}
                      >
                        {sendingOtpId === card.id ? "Sending..." : "Send OTP"}
                      </button>
                    )}

                    {/* OTP Verified Indicator */}
                    {card.otpVerified && (
                      <span style={{ color: "green", fontWeight: "bold", textAlign: "center", fontSize: "12px" }}>
                        ✓ OTP Verified
                      </span>
                    )}

                    {/* Delete Button - Always Available for Testing */}
                    <button
                      className="ViewBtn"
                      onClick={() => deleteCard(card.id, card.cardUid)}
                      style={{ 
                        fontSize: "12px", 
                        padding: "4px 8px",
                        backgroundColor: "#ffebee",
                        color: "#c62828",
                        border: "1px solid #ef5350"
                      }}
                    >
                      🗑️ Delete & Reset
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
