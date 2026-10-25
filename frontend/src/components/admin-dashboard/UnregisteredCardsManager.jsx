import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import MemberSelect from "./MemberSelect";

export default function UnregisteredCardsManager() {
  const { authFetch } = useAuth();
  const [unregisteredCards, setUnregisteredCards] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [activatingId, setActivatingId] = useState(null);
  const [activateUserId, setActivateUserId] = useState("");
  const [activateRole, setActivateRole] = useState("MEMBER");

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
      setSuccess(`✓ Card activated successfully! User can now tap card to access.`);
      setActivatingId(null);
      setActivateUserId("");
      setActivateRole("MEMBER");
      setTimeout(() => setSuccess(""), 3000);
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
      setTimeout(() => setSuccess(""), 3000);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  const handleCreateUser = (email) => {
    const message = `To create a new user, go to Admin Dashboard > Users section.\n\nEmail to assign: ${email}`;
    alert(message);
    window.location.href = "/admin-dashboard/users";
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return "—";
    return new Date(dateStr).toLocaleString();
  };

  const pendingCards = unregisteredCards.filter(c => c.status === "PENDING");

  return (
    <div className="HardwareCheckInsContainer">
      <h2>New Cards Awaiting Assignment</h2>
      <p className="CardMuted">
        When a new card is tapped for the first time, it appears here. Select the member and role, then click "Activate" to enable immediate access.
      </p>

      {error && <div className="HardwareError">{error}</div>}
      {success && <div className="HardwareSuccess">{success}</div>}

      {pendingCards.length === 0 ? (
        <div className="CardMuted" style={{ padding: "20px", textAlign: "center", background: "#f9f9f9", borderRadius: "4px" }}>
          No new cards awaiting assignment. Cards will appear here when they're tapped for the first time.
        </div>
      ) : (
        <div>
          <table className="CardTable" style={{ marginBottom: "20px" }}>
            <thead>
              <tr>
                <th>Card UID</th>
                <th>First Tap</th>
                <th>Last Tap</th>
                <th>Taps</th>
                <th>Device</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingCards.map((card) => (
                <tr key={card.id}>
                  <td className="CardUid">{card.cardUid}</td>
                  <td style={{ fontSize: "12px" }}>{formatDate(card.firstScannedAt)}</td>
                  <td style={{ fontSize: "12px" }}>{formatDate(card.lastScannedAt)}</td>
                  <td style={{ textAlign: "center" }}>{card.scanCount}</td>
                  <td style={{ fontSize: "12px" }}>{card.deviceId || "—"}</td>
                  <td style={{ minWidth: "350px" }}>
                    {activatingId === card.id ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div style={{ fontSize: "12px", fontWeight: "500", marginBottom: "4px" }}>
                          Assign this card:
                        </div>
                        <MemberSelect 
                          value={activateUserId}
                          onChange={setActivateUserId}
                          onCreateUser={handleCreateUser}
                        />
                        <select
                          value={activateRole}
                          onChange={(e) => setActivateRole(e.target.value)}
                          style={{ 
                            padding: "8px", 
                            fontSize: "12px",
                            border: "1px solid #ddd",
                            borderRadius: "4px"
                          }}
                        >
                          <option value="MEMBER">Member</option>
                          <option value="STAFF">Staff</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                        <div style={{ display: "flex", gap: "6px" }}>
                          <button
                            className="ViewBtn"
                            onClick={() => handleActivateClick(card.id)}
                            disabled={!activateUserId}
                            style={{ flex: 1, fontSize: "12px", padding: "8px" }}
                          >
                            ✓ Activate Card
                          </button>
                          <button
                            className="ViewBtn"
                            onClick={() => {
                              setActivatingId(null);
                              setActivateUserId("");
                              setActivateRole("MEMBER");
                            }}
                            style={{ flex: 1, fontSize: "12px", padding: "8px" }}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div style={{ display: "flex", gap: "6px" }}>
                        <button
                          className="ViewBtn"
                          onClick={() => {
                            setActivatingId(card.id);
                            setActivateUserId("");
                            setActivateRole("MEMBER");
                          }}
                          style={{ flex: 1, fontSize: "12px", padding: "6px 8px" }}
                        >
                          Activate Card
                        </button>
                        <button
                          className="ViewBtn"
                          onClick={() => deleteCard(card.id, card.cardUid)}
                          style={{ 
                            fontSize: "12px", 
                            padding: "6px 8px",
                            color: "#d9534f"
                          }}
                          title="Delete & Reset"
                        >
                          🗑️
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Show activated cards in a separate section */}
      {unregisteredCards.filter(c => c.status === "ACTIVATED").length > 0 && (
        <div style={{ marginTop: "30px" }}>
          <h3>Activated Cards ({unregisteredCards.filter(c => c.status === "ACTIVATED").length})</h3>
          <table className="CardTable">
            <thead>
              <tr>
                <th>Card UID</th>
                <th>Status</th>
                <th>First Tap</th>
                <th>Last Tap</th>
                <th>Taps</th>
              </tr>
            </thead>
            <tbody>
              {unregisteredCards.filter(c => c.status === "ACTIVATED").map((card) => (
                <tr key={card.id}>
                  <td className="CardUid">{card.cardUid}</td>
                  <td>
                    <span style={{
                      padding: "4px 8px",
                      borderRadius: "4px",
                      background: "#d4edda",
                      color: "#155724",
                      fontSize: "12px",
                      fontWeight: "500"
                    }}>
                      ✓ Activated
                    </span>
                  </td>
                  <td style={{ fontSize: "12px" }}>{formatDate(card.firstScannedAt)}</td>
                  <td style={{ fontSize: "12px" }}>{formatDate(card.lastScannedAt)}</td>
                  <td style={{ textAlign: "center" }}>{card.scanCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
