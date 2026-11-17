import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import "./MemberHardware.css";

const BASE = "/api/v1/hardware";
async function read(response) {
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || data?.detail || `Request failed (${response.status}).`);
  return data;
}

export default function MemberHardware({ userId }) {
  const { authFetch, hasPermission } = useAuth();
  return hasPermission("MANAGE_EQUIPMENT")
    ? <MemberHardwareControls key={userId} userId={userId} authFetch={authFetch}
        canOverride={hasPermission("MANAGE_USERS")} />
    : <p>Card setup requires equipment-management permission.</p>;
}

function MemberHardwareControls({ userId, authFetch, canOverride }) {
  const [devices, setDevices] = useState([]);
  const [cards, setCards] = useState([]);
  const [overrides, setOverrides] = useState([]);
  const [deviceId, setDeviceId] = useState("");
  const [pending, setPending] = useState(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const [showOverride, setShowOverride] = useState(false);
  const [reason, setReason] = useState("");
  const [minutes, setMinutes] = useState("60");
  const loading = useRef(false);
  const mutation = useRef(false);
  const revision = useRef(0);
  const pendingRef = useRef(null);
  const request = useCallback(async (path, method = "GET", body) => read(await authFetch(BASE + path, {
    method, cache: "no-store", ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })), [authFetch]);
  const refresh = useCallback(async () => {
    if (loading.current || mutation.current) return;
    loading.current = true;
    const version = revision.current;
    try {
      const [pis, allCards, grants] = await Promise.all([
        request("/devices"), request("/cards"),
        canOverride ? request(`/members/${userId}/door-overrides`) : Promise.resolve([]),
      ]);
      if (version !== revision.current) return;
      if (!Array.isArray(pis) || !Array.isArray(allCards) || !Array.isArray(grants)) throw new Error("Invalid hardware response.");
      const mine = allCards.filter(c => String(c.userId) === String(userId));
      setDevices(pis); setCards(mine); setOverrides(grants); setReady(true); setError("");
      setDeviceId(old => old || pis.find(d => d.enabled && d.connected)?.id || "");
      const waiting = pendingRef.current;
      if (waiting) {
        const pi = pis.find(d => d.id === waiting.id);
        if (!pi || String(pi.assignmentUserId) !== String(userId) || new Date(pi.assignmentExpiresAt).getTime() <= Date.now()) {
          const assigned = mine.find(c => new Date(c.assignedAt).getTime() >= waiting.started);
          setMessage(assigned ? `Card ${assigned.cardUid} assigned. Remove it and tap again to check access.`
            : "Assignment ended or expired. Click Assign card to try again.");
          pendingRef.current = null; setPending(null);
        }
      }
    } catch (err) { if (version === revision.current) { setError(err.message); setReady(false); } }
    finally { loading.current = false; }
  }, [request, userId, canOverride]);
  useEffect(() => {
    const initial = setTimeout(refresh, 0);
    const interval = setInterval(refresh, 2500);
    return () => { clearTimeout(initial); clearInterval(interval); };
  }, [refresh]);
  async function action(fn) {
    if (mutation.current) return;
    mutation.current = true; revision.current += 1;
    setBusy(true); setError("");
    try { await fn(); }
    catch (err) { setError(err.message); return; }
    finally { mutation.current = false; setBusy(false); }
    await refresh();
  }
  const selected = devices.find(d => d.id === deviceId);
  const activeGrants = overrides.filter(o => !o.revokedAt && new Date(o.expiresAt).getTime() > Date.now());
  return <section className="MemberHardware" aria-label="Member cards and door access">
    <h3>Card &amp; door access</h3>
    <label>Scanner
      <select value={deviceId} disabled={busy || !!pending} onChange={e => setDeviceId(e.target.value)}>
        <option value="">Select a Pi…</option>
        {devices.map(d => <option key={d.id} value={d.id}>{d.name} — {d.area}{!d.enabled ? " (disabled)" : !d.connected ? " (offline)" : ""}</option>)}
      </select>
    </label>
    {!devices.length && ready && <p>Register and pair a Pi in Hardware Access → Pis &amp; areas first.</p>}
    {pending ? <div role="status">
      <p><strong>Tap the card on {pending.name}.</strong> This request expires after two minutes. Assignment does not open the door. Cancel it before closing if you do not want the next tap assigned.</p>
      <button type="button" disabled={busy} onClick={() => action(async () => {
        await request(`/devices/${pending.id}/assignment`, "DELETE");
        pendingRef.current = null; setPending(null); setMessage("Card assignment cancelled.");
      })}>Cancel assignment</button>
    </div> : <button type="button" disabled={busy || !ready || !selected?.enabled || !selected?.connected}
      onClick={() => action(async () => {
        const result = await request(`/devices/${deviceId}/assignment`, "POST", { userId });
        if (String(result?.assignmentUserId) !== String(userId)) throw new Error("Assignment was not confirmed. Refresh and check the Pi status.");
        const waiting = { id: deviceId, name: selected.name, started: new Date(result.assignmentExpiresAt).getTime() - 120000 };
        pendingRef.current = waiting; setPending(waiting); setMessage("");
      })}>{busy ? "Please wait…" : "Assign card"}</button>}
    {message && <p role="status">{message}</p>}
    {error && <p role="alert" className="MemberHardwareError">{error} <button type="button" onClick={refresh}>Retry</button></p>}
    <ul>{cards.map(card => <li key={card.id}>
      <strong>{card.cardUid}</strong> — {card.active ? "Enabled" : "Disabled"}
      <button type="button" disabled={busy || !ready} onClick={() => action(async () => {
        await request(`/cards/${card.id}`, "PATCH", { active: !card.active });
        setMessage(`Card ${card.active ? "disabled" : "enabled"}. Access rules still apply.`);
      })}>{card.active ? "Disable card" : "Enable card"}</button>
    </li>)}</ul>
    {canOverride && <>
      <button type="button" disabled={busy} onClick={() => setShowOverride(!showOverride)}>Temporary door-access override</button>
      {showOverride && <div className="MemberHardwareOverride">
        <p>Allows this member through the selected general-entry Pi while membership or waivers are pending. The account and card must remain enabled. Equipment permissions are never bypassed.</p>
        <label>Reason<input maxLength={200} value={reason} onChange={e => setReason(e.target.value)} /></label>
        <label>Duration in minutes (1–10080)<input type="number" min="1" max="10080" step="1" value={minutes} onChange={e => setMinutes(e.target.value)} /></label>
        <button type="button" disabled={busy || !ready || !selected?.enabled || selected?.equipmentId != null || !reason.trim() || !Number.isInteger(Number(minutes)) || Number(minutes) < 1 || Number(minutes) > 10080}
          onClick={() => action(async () => {
            await request(`/members/${userId}/door-overrides`, "POST", { deviceId, reason: reason.trim(), minutes: Number(minutes) });
            setReason(""); setShowOverride(false); setMessage("Temporary override saved. Tap the assigned card to check access.");
          })}>Grant temporary access</button>
        {selected?.equipmentId != null && <p>Select a general-entry Pi without equipment binding.</p>}
      </div>}
      {activeGrants.map(grant => <div key={grant.id} className="MemberHardwareOverride">
        <p><strong>{devices.find(d => d.id === grant.deviceId)?.name || "Removed Pi"}</strong>: {grant.reason}<br />Expires {new Date(grant.expiresAt).toLocaleString()}</p>
        <button type="button" disabled={busy || !ready} onClick={() => action(async () => {
          await request(`/members/${userId}/door-overrides/${grant.id}`, "DELETE"); setMessage("Override revoked.");
        })}>Revoke override</button>
      </div>)}
    </>}
  </section>;
}
