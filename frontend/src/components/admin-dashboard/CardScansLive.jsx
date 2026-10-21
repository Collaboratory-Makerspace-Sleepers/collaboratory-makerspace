import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import MemberSelect from "./MemberSelect";

const POLL_MS = 2000;
const BANNER_MS = 8000;

export default function CardScansLive({ onAssigned }) {
  const { authFetch } = useAuth();
  const [scans, setScans] = useState([]);
  const [now, setNow] = useState(() => Date.now());
  const [error, setError] = useState("");
  const [assigning, setAssigning] = useState(null);
  const [memberId, setMemberId] = useState("");

  const load = useCallback(async () => {
    try {
      const res = await authFetch("/api/v1/hardware/card-scans/recent?limit=25");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setScans(await res.json());
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, [authFetch]);

  useEffect(() => {
    const first = setTimeout(load, 0);
    const poll = setInterval(() => {
      if (!document.hidden) load();
    }, POLL_MS);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      clearTimeout(first);
      clearInterval(poll);
      clearInterval(tick);
    };
  }, [load]);

  async function assign(scan) {
    if (!memberId) return;
    const res = await authFetch("/api/v1/hardware/cards", {
      method: "POST",
      body: JSON.stringify({ cardUid: scan.cardUid, userId: Number(memberId) }),
    });
    if (res.ok) {
      setAssigning(null);
      setMemberId("");
      onAssigned?.();
    } else {
      const data = await res.json().catch(() => ({}));
      setError(data.message || `Could not assign card (HTTP ${res.status})`);
    }
  }

  const latest = scans[0];
  const fresh = latest && now - new Date(latest.scannedAt).getTime() < BANNER_MS;
  const bannerClass = !fresh ? "idle" : latest.approved ? "ok" : "denied";

  return (
    <div className="HardwareCheckInsContainer">
      <div className={`CardTapBanner ${bannerClass}`} role="status" aria-live="polite">
        {!fresh && (
          <>
            <h2>Waiting for a card tap…</h2>
            <p>Tap a card on the reader to see the result here.</p>
          </>
        )}
        {fresh && latest.approved && (
          <>
            <h2>✓ Access granted</h2>
            <p>{latest.memberName}{latest.equipmentName ? ` · ${latest.equipmentName}` : ""}</p>
          </>
        )}
        {fresh && !latest.approved && (
          <>
            <h2>✕ Access denied</h2>
            <p>
              {latest.memberName ? `${latest.memberName} — ` : ""}
              {latest.reason}
              <span className="CardUid"> ({latest.cardUid})</span>
            </p>
          </>
        )}
      </div>

      {error && <div className="HardwareError">{error}</div>}

      <table className="CardTable">
        <thead>
          <tr><th>Time</th><th>Card UID</th><th>Member</th><th>Result</th><th /></tr>
        </thead>
        <tbody>
          {scans.length === 0 && (
            <tr><td colSpan={5} className="CardMuted">No card taps yet.</td></tr>
          )}
          {scans.map((s) => (
            <tr key={s.id} className={s.approved ? "ok" : "denied"}>
              <td>{new Date(s.scannedAt).toLocaleTimeString()}</td>
              <td className="CardUid">{s.cardUid}</td>
              <td>{s.memberName || "Unknown card"}</td>
              <td>{s.approved ? "Approved" : `Denied — ${s.reason}`}</td>
              <td>
                {!s.memberName && s.reason === "Card not registered" && (
                  assigning === s.id ? (
                    <div className="CardForm">
                      <MemberSelect value={memberId} onChange={setMemberId} />
                      <button className="ViewBtn" disabled={!memberId} onClick={() => assign(s)}>Save</button>
                      <button className="ViewBtn" onClick={() => setAssigning(null)}>Cancel</button>
                    </div>
                  ) : (
                    <button className="ViewBtn" onClick={() => { setAssigning(s.id); setMemberId(""); }}>
                      Assign card
                    </button>
                  )
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
