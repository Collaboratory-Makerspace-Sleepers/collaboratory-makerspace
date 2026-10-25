import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

export default function UnregisteredCardsManager() {
  const { authFetch } = useAuth();
  const [unregisteredCards, setUnregisteredCards] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [users, setUsers] = useState([]);
  const [activatingId, setActivatingId] = useState(null);
  const [activateUserId, setActivateUserId] = useState("");
  const [activateRole, setActivateRole] = useState("MEMBER");
  const [loadingUsers, setLoadingUsers] = useState(false);

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

  const loadUsers = useCallback(async (query = "") => {
    try {
      setLoadingUsers(true);
      const res = await authFetch(`/api/v1/hardware/cards/members?q=${encodeURIComponent(query)}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setUsers(await res.json());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoadingUsers(false);
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

  const handleActivateClick = async (cardId) => {
    if (!activateUserId) {
      setError("Please select a user");
      return;
    }

    try {
      setError("");
      const res = await authFetch(`/api/v1/hardware/unregistered-cards/${cardId}/activate-directly`, {
        method: "POST",
        body: JSON.stringify({ 
          userId: Number(activateUserId),
          role: activateRole.trim(),
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${res.status}`);
      }
      setSuccess(`Card activated successfully! User can now tap card to access.`);
      setActivatingId(null);
      setActivateUserId("");
      setActivateRole("MEMBER");
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

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
        New cards that have been tapped but not yet activated.
        <strong> Quick Setup:</strong> Select a user, choose their role (Member, Staff, or Admin), 
        then click "Activate Card". Card will work immediately on next tap.
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
                    backgroundColor: card.status === "ACTIVATED" ? "#d4edda" : 
                                     card.status === "ASSIGNED" ? "#fff3cd" : "#e7e7e7",
                  }}>
                    {card.status}
                  </span>
                </td>
                <td>{formatDate(card.firstScannedAt)}</td>
                <td>{formatDate(card.lastScannedAt)}</td>
                <td>{card.scanCount}</td>
                <td>{card.deviceId || "—"}</td>
                <td style={{ minWidth: "300px" }}>
                  {card.status === "PENDING" || card.status === "ASSIGNED" ? (
                    activatingId === card.id ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <input
                          type="text"
                          placeholder="Search user by name or email..."
                          onChange={(e) => {
                            loadUsers(e.target.value);
                            setActivateUserId("");
                          }}
                          style={{ padding: "4px 8px", fontSize: "12px" }}
                          autoFocus
                        />
                        {loadingUsers && <div style={{ fontSize: "12px", color: "#666" }}>Loading...</div>}
                        {users.length > 0 && (
                          <select
                            value={activateUserId}
                            onChange={(e) => setActivateUserId(e.target.value)}
                            style={{ padding: "4px 8px", fontSize: "12px" }}
                          >
                            <option value="">— Select User —</option>
                            {users.map((u) => (
                              <option key={u.id} value={u.id}>
                                {u.name} ({u.email})
                              </option>
                            ))}
                          </select>
                        )}
                        <select
                          value={activateRole}
                          onChange={(e) => setActivateRole(e.target.value)}
                          style={{ padding: "4px 8px", fontSize: "12px" }}
                        >
                          <option value="MEMBER">Regular Member</option>
                          <option value="STAFF">Staff</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                        <div style={{ display: "flex", gap: "4px" }}>
                          <button
                            className="ViewBtn"
                            onClick={() => handleActivateClick(card.id)}
                            disabled={!activateUserId}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            ✓ Activate
                          </button>
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setActivatingId(null);
                              setActivateUserId("");
                              setActivateRole("MEMBER");
                            }}
                            style={{ fontSize: "12px", padding: "4px 8px" }}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div style={{ display: "flex", gap: "4px" }}>
                        <button
                          className="ViewBtn"
                          onClick={() => {
                            setActivatingId(card.id);
                            setActivateUserId("");
                            setActivateRole("MEMBER");
                            loadUsers("");
                          }}
                          style={{ fontSize: "12px", padding: "4px 8px" }}
                        >
                          Activate Card
                        </button>
                        <button
                          className="ViewBtn"
                          onClick={() => deleteCard(card.id, card.cardUid)}
                          style={{ fontSize: "12px", padding: "4px 8px" }}
                          title="Delete & Reset"
                        >
                          🗑️
                        </button>
                      </div>
                    )
                  ) : (
                    <span style={{ color: "#666", fontSize: "12px" }}>✓ Activated</span>
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
