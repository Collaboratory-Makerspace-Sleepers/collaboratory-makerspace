import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import "./HardwareDevices.css";

const BASE = "/api/v1/hardware/devices";
const newDevice = { name: "", area: "", equipmentId: "", enabled: true, doorEnabled: false, unlockSeconds: 5 };
const dateLabel = (value) => value ? new Date(value).toLocaleString() : "Never";
const isDevice = (value) => value && typeof value.id === "string"
  && typeof value.name === "string" && typeof value.area === "string"
  && typeof value.enabled === "boolean" && typeof value.doorEnabled === "boolean"
  && typeof value.connected === "boolean" && Number.isInteger(value.unlockSeconds);

async function readResponse(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || body?.detail || `Request failed (HTTP ${response.status}).`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function DeviceDialog({ title, children, busy, onClose }) {
  const ref = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    ref.current?.focus();
    return () => previous?.focus();
  }, []);
  function keyboard(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      if (!busy) onClose();
    }
    if (event.key === "Tab") {
      const controls = [...ref.current.querySelectorAll("button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex='0']")];
      const first = controls[0];
      const last = controls.at(-1);
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === ref.current)) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === ref.current)) {
        event.preventDefault(); first.focus();
      }
    }
  }
  return (
    <div className="HDOverlay">
      <div className="HDDialog" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} ref={ref} onKeyDown={keyboard}>
        <h3>{title}</h3>
        {children}
        <button type="button" disabled={busy} onClick={onClose}>Close</button>
      </div>
    </div>
  );
}

function AssignmentPicker({ authFetch, busy, onSelect }) {
  const [query, setQuery] = useState("");
  const [members, setMembers] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const data = await readResponse(await authFetch(`/api/v1/hardware/cards/members?q=${encodeURIComponent(query)}`, { signal: controller.signal }));
        if (!Array.isArray(data) || data.some((member) => member.id == null || typeof member.name !== "string")) {
          throw new Error("Invalid member search response.");
        }
        if (!controller.signal.aborted) { setMembers(data); setError(""); }
      } catch (err) {
        if (!controller.signal.aborted) setError(err.message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 250);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [authFetch, query]);
  return (
    <>
      <label>Search member name or email
        <input value={query} disabled={busy} onChange={(event) => {
          setQuery(event.target.value); setLoading(true); setMembers([]); onSelect(null);
        }} />
      </label>
      {loading && <p role="status">Searching members…</p>}
      {error && <p className="HDError" role="alert">{error}</p>}
      <label>Member
        <select required defaultValue="" disabled={busy || loading || !!error} key={query} onChange={(event) => {
          onSelect(members.find((member) => String(member.id) === event.target.value) || null);
        }}>
          <option value="">Select member…</option>
          {members.map((member) => <option key={member.id} value={member.id}>{member.name} ({member.email})</option>)}
        </select>
      </label>
      {!loading && !error && members.length === 0 && <p>No matching members.</p>}
    </>
  );
}

export default function HardwareDevices() {
  const { authFetch, hasPermission } = useAuth();
  return hasPermission("MANAGE_EQUIPMENT")
    ? <DeviceManagement authFetch={authFetch} />
    : <p role="alert">Device management requires MANAGE_EQUIPMENT permission.</p>;
}

function DeviceManagement({ authFetch }) {
  const [devices, setDevices] = useState([]);
  const [events, setEvents] = useState([]);
  const [equipment, setEquipment] = useState([]);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState(null);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState(null);
  const [form, setForm] = useState(newDevice);
  const [member, setMember] = useState(null);
  const [label, setLabel] = useState("");
  const [pairing, setPairing] = useState(null);
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(Date.now);
  const inFlight = useRef(false);
  const mutation = useRef(false);
  const revision = useRef(0);

  const load = useCallback(async (signal) => {
    if (inFlight.current || mutation.current || signal?.aborted) return;
    inFlight.current = true;
    const version = revision.current;
    try {
      await Promise.all([
        ["devices", BASE, setDevices, isDevice],
        ["events", `${BASE}/events?limit=50`, setEvents, (value) => value && value.id != null
          && typeof value.type === "string" && ["SERVER", "DEVICE"].includes(value.source)],
      ].map(async ([key, url, setter, validate]) => {
        try {
          const data = await readResponse(await authFetch(url, { signal, cache: "no-store" }));
          if (!Array.isArray(data) || !data.every(validate)) throw new Error(`Invalid ${key} response.`);
          if (!signal?.aborted && version === revision.current) {
            setter(data);
            setErrors((previous) => ({ ...previous, [key]: "" }));
            if (key === "devices") setUpdatedAt(Date.now());
          }
        } catch (err) {
          if (!signal?.aborted && version === revision.current) setErrors((previous) => ({ ...previous, [key]: err.message }));
        }
      }));
    } finally {
      inFlight.current = false;
      if (!signal?.aborted) setLoading(false);
    }
  }, [authFetch]);

  useEffect(() => {
    const controller = new AbortController();
    const refresh = () => { if (!document.hidden) load(controller.signal); };
    load(controller.signal);
    const timer = setInterval(refresh, 5000);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      controller.abort(); clearInterval(timer); clearInterval(clock);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [load]);

  const loadEquipment = useCallback(async (signal) => {
    try {
      const data = await readResponse(await authFetch("/api/v1/equipment", { signal, cache: "no-store" }));
      if (!Array.isArray(data) || data.some((item) => item.id == null || typeof item.name !== "string")) {
        throw new Error("Invalid equipment response.");
      }
      if (!signal?.aborted) {
        setEquipment(data); setErrors((previous) => ({ ...previous, equipment: "" }));
      }
    } catch (err) {
      if (!signal?.aborted) setErrors((previous) => ({ ...previous, equipment: err.message }));
    }
  }, [authFetch]);
  useEffect(() => {
    const controller = new AbortController();
    loadEquipment(controller.signal);
    return () => controller.abort();
  }, [loadEquipment]);

  const activePairing = pairing && new Date(pairing.expiresAt).getTime() > now;
  useEffect(() => {
    if (pairing && !activePairing) {
      const timer = setTimeout(() => {
        setPairing(null);
        setNotice("Pairing code expired and has been hidden. Generate a new code to pair the Pi.");
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [pairing, activePairing]);

  async function mutate(url, method, body, resultType = "device") {
    if (mutation.current) return;
    mutation.current = true; revision.current += 1; setBusy(true);
    setErrors((previous) => ({ ...previous, mutation: "" })); setNotice("");
    try {
      const response = await authFetch(url, { method, body: body === undefined ? undefined : JSON.stringify(body) });
      const data = await readResponse(response);
      if (resultType === "delete") {
        if (response.status !== 204) throw new Error("Invalid revocation response; expected HTTP 204.");
        setDevices((previous) => previous.filter((device) => `${BASE}/${device.id}` !== url));
        if (pairing && `${BASE}/${pairing.device.id}` === url) setPairing(null);
        setNotice("Device revoked.");
      } else {
        const device = resultType === "pairing" ? data?.device : data;
        if (!isDevice(device)) throw new Error("Invalid device response. Refresh to verify the operation.");
        if (url.endsWith("/assignment")) {
          const validAssignment = method === "DELETE" ? device.assignmentUserId == null
            : String(device.assignmentUserId) === String(body.userId)
              && new Date(device.assignmentExpiresAt).getTime() > Date.now();
          if (!validAssignment) throw new Error("Assignment status was not confirmed. Refresh and check device events before trying again.");
        }
        if (resultType === "pairing") {
          if (!/^\d{8}$/.test(data.code) || typeof data.code !== "string"
            || !(new Date(data.expiresAt).getTime() > Date.now())) {
            throw new Error("Invalid or expired pairing code response. Regenerate a code to try again.");
          }
          setPairing(data);
        }
        setDevices((previous) => previous.some((item) => item.id === device.id)
          ? previous.map((item) => item.id === device.id ? device : item)
          : [...previous, device]);
        setNotice(resultType === "pairing" ? "Pairing code issued. It will not be shown again after dismissal."
          : url.endsWith("/assignment") ? (method === "DELETE" ? "Pending next-tap assignment cancelled." : "Next-tap assignment armed. Await a tap and check status/events for the outcome.")
          : "Device updated.");
      }
      setDialog(null);
    } catch (err) {
      setErrors((previous) => ({ ...previous, mutation: err.message }));
    } finally {
      mutation.current = false; setBusy(false);
      load();
    }
  }

  function open(kind, device = null) {
    setErrors((previous) => ({ ...previous, mutation: "" }));
    setDialog({ kind, device });
    if (kind === "edit") setForm(device ? { ...device, equipmentId: device.equipmentId ?? "" } : { ...newDevice });
    if (kind === "assign") { setMember(null); setLabel(""); }
  }
  function save(event) {
    event.preventDefault();
    if (!form.name.trim() || !form.area.trim() || !Number.isInteger(Number(form.unlockSeconds))
      || Number(form.unlockSeconds) < 1 || Number(form.unlockSeconds) > 30) {
      setErrors((previous) => ({ ...previous, mutation: "Name, area and an integer unlock duration from 1–30 seconds are required." }));
      return;
    }
    const selectedEquipment = equipment.find((item) => String(item.id) === String(form.equipmentId));
    const body = {
      name: form.name.trim(), area: form.area.trim(),
      equipmentId: form.equipmentId === "" ? null : (selectedEquipment?.id ?? form.equipmentId),
      enabled: form.enabled, doorEnabled: form.doorEnabled, unlockSeconds: Number(form.unlockSeconds),
    };
    mutate(dialog.device ? `${BASE}/${dialog.device.id}` : BASE, dialog.device ? "PATCH" : "POST", body, dialog.device ? "device" : "pairing");
  }

  return (
    <section className="HardwareDevices">
      <header className="HDHeader">
        <div><h2>Pi devices</h2><p>Auto-refresh every 5 seconds while visible, and on focus. Last device refresh: {dateLabel(updatedAt)}.</p></div>
        <div className="HDActions">
          <button disabled={busy} onClick={() => load()}>Refresh</button>
          <button disabled={busy || loading || !!errors.devices} onClick={() => open("edit")}>Register Pi</button>
        </div>
      </header>
      <p className="HDWarning">Door output is off by default. Enabling it requires locally configured and physically verified hardware. A software setting alone does not establish safe wiring or operation.</p>
      {Object.entries(errors).filter(([key, value]) => value && (key !== "mutation" || !dialog)).map(([key, value]) => <p key={key} className="HDError" role="alert">{key}: {value}</p>)}
      {notice && <p role="status">{notice}</p>}
      {activePairing && (
        <aside className="HDPairing" aria-label="One-time pairing code">
          <h3>Pair {pairing.device.name}</h3>
          <p>Enter this code on the Pi. Shown only now; do not share it publicly.</p>
          <strong className="HDCode">{pairing.code}</strong>
          <p>Expires {dateLabel(pairing.expiresAt)} ({Math.ceil((new Date(pairing.expiresAt).getTime() - now) / 1000)} seconds remaining).</p>
          <p>Regenerating immediately revokes the existing device key and old pairing. Dismissing hides this code permanently.</p>
          <button disabled={busy} onClick={() => setPairing(null)}>Dismiss code</button>
        </aside>
      )}
      {loading && <p role="status">Loading Pi devices…</p>}
      {!loading && !errors.devices && devices.length === 0 && <p>No Pi devices registered.</p>}
      {errors.devices && devices.length > 0 && <p>Showing previously loaded devices; status may be stale.</p>}
      <div className="HDGrid">
        {devices.map((device) => {
          const assigning = device.assignmentUserId != null && new Date(device.assignmentExpiresAt).getTime() > now;
          return (
            <article className="HDCard" key={device.id}>
              <h3>{device.name}</h3>
              <p><span className={`HDStatus ${device.connected ? "online" : "offline"}`}>{device.connected ? "Connected" : "Offline"}</span> · {device.enabled ? "Enabled" : "Disabled"}</p>
              <dl>
                <dt>Area</dt><dd>{device.area}</dd>
                <dt>Equipment</dt><dd>{device.equipmentId == null ? "Unbound" : equipment.find((item) => String(item.id) === String(device.equipmentId))?.name || `ID ${device.equipmentId}`}</dd>
                <dt>Hostname</dt><dd>{device.hostname || "Not reported"}</dd>
                <dt>Reader</dt><dd>{device.readerStatus || "Not reported"}</dd>
                <dt>Last seen</dt><dd>{dateLabel(device.lastSeenAt)}</dd>
                <dt>Door output</dt><dd>{device.doorEnabled ? `Enabled · ${device.unlockSeconds}s` : "Off"}</dd>
                <dt>Pairing</dt><dd>{device.pairingExpiresAt && new Date(device.pairingExpiresAt).getTime() > now ? `Code expires ${dateLabel(device.pairingExpiresAt)}` : "No active code"}</dd>
                <dt>Next-tap assignment</dt><dd>{assigning ? `Waiting for member ID ${device.assignmentUserId}; expires ${dateLabel(device.assignmentExpiresAt)}` : device.assignmentUserId != null ? "Expired — arm again to assign" : "Not armed"}</dd>
                <dt>Device ID</dt><dd>{device.id}</dd>
              </dl>
              <div className="HDActions">
                <button disabled={busy || !!errors.devices} onClick={() => open("edit", device)}>Edit</button>
                <button disabled={busy || !!errors.devices} onClick={() => open("pair", device)}>Regenerate pairing code</button>
                <button disabled={busy || !!errors.devices || !device.enabled || !device.connected || assigning} onClick={() => open("assign", device)}>Assign next tap</button>
                {device.assignmentUserId != null && <button disabled={busy || !!errors.devices} onClick={() => mutate(`${BASE}/${device.id}/assignment`, "DELETE")}>Cancel assignment</button>}
                <button className="HDDanger" disabled={busy || !!errors.devices} onClick={() => open("revoke", device)}>Revoke</button>
              </div>
            </article>
          );
        })}
      </div>
      <h3>Recent device events</h3>
      <p>Latest 50 events. SERVER records confirm backend processing, including access decisions; DEVICE records are reported telemetry, not confirmation of access. Neither verifies physical door operation. Next-tap assignment outcomes appear here; use Cards to manage assigned cards.</p>
      {errors.events && events.length > 0 && <p>Showing previously loaded events.</p>}
      <div className="HDTableScroll">
        <table>
          <thead><tr><th>Occurred / received</th><th>Device</th><th>Source</th><th>Type / outcome</th><th>Detail</th><th>Card / scan</th></tr></thead>
          <tbody>
            {!loading && !errors.events && events.length === 0 && <tr><td colSpan={6}>No device events.</td></tr>}
            {events.map((event) => <tr key={event.id}>
              <td>{dateLabel(event.occurredAt)}<br /><small>Received: {dateLabel(event.receivedAt)}</small></td>
              <td>{event.deviceName || event.deviceId}</td>
              <td>{event.source === "SERVER" ? "SERVER · backend-confirmed record" : "DEVICE · reported telemetry"}</td>
              <td>{event.type}<br />{event.outcome || "Not reported"}</td>
              <td>{event.detail || "—"}</td>
              <td>{event.cardUid || "—"}<br /><small>{event.scanId || "—"}</small></td>
            </tr>)}
          </tbody>
        </table>
      </div>
      {dialog && (
        <DeviceDialog title={dialog.kind === "edit" ? (dialog.device ? "Edit Pi device" : "Register Pi device") : `${dialog.kind === "assign" ? "Assign next tap on" : dialog.kind === "pair" ? "Regenerate pairing for" : "Revoke"} ${dialog.device.name}`} busy={busy} onClose={() => setDialog(null)}>
          {errors.mutation && <p className="HDError" role="alert">{errors.mutation}</p>}
          {dialog.kind === "edit" && <form onSubmit={save}>
            <fieldset disabled={busy}>
              <label>Name<input autoComplete="off" required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
              <label>Area name (plain text)<input required value={form.area} onChange={(event) => setForm({ ...form, area: event.target.value })} /></label>
              <label>Equipment (optional)<select value={form.equipmentId} onChange={(event) => setForm({ ...form, equipmentId: event.target.value })}>
                <option value="">No equipment binding</option>
                {form.equipmentId !== "" && !equipment.some((item) => String(item.id) === String(form.equipmentId)) && <option value={form.equipmentId}>Current equipment ID {form.equipmentId}</option>}
                {equipment.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
              </select></label>
              {errors.equipment && <p className="HDError" role="alert">Equipment binding list unavailable: {errors.equipment} <button type="button" onClick={() => loadEquipment()}>Retry equipment</button></p>}
              <label className="HDCheckbox"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />Device enabled</label>
              <p className="HDWarning">Only enable door output after hardware is locally configured and physically verified. This setting does not test the door or its safety.</p>
              <label className="HDCheckbox"><input type="checkbox" checked={form.doorEnabled} onChange={(event) => setForm({ ...form, doorEnabled: event.target.checked })} />Enable door output</label>
              <label>Unlock seconds (1–30)<input type="number" required min="1" max="30" step="1" value={form.unlockSeconds} onChange={(event) => setForm({ ...form, unlockSeconds: event.target.value })} /></label>
              <button type="submit">{busy ? "Saving…" : dialog.device ? "Save changes" : "Register and issue pairing code"}</button>
            </fieldset>
          </form>}
          {dialog.kind === "assign" && <form onSubmit={(event) => {
            event.preventDefault();
            if (member) mutate(`${BASE}/${dialog.device.id}/assignment`, "POST", { userId: member.id, ...(label.trim() ? { label: label.trim() } : {}) });
          }}>
            <p>The next card tapped on this chosen Pi will be assigned to the selected member. Assignment expires after 120 seconds. Wait for the device status/events to confirm completion. You can cancel the pending assignment.</p>
            <AssignmentPicker authFetch={authFetch} busy={busy} onSelect={setMember} />
            <label>Card label (optional)<input value={label} disabled={busy} onChange={(event) => setLabel(event.target.value)} /></label>
            <button type="submit" disabled={busy || !member}>{busy ? "Arming…" : "Arm next-tap assignment"}</button>
          </form>}
          {dialog.kind === "pair" && <>
            <p>Regenerating immediately revokes the existing device key and old pairing, disconnecting the Pi until it is paired again. The new eight-digit code is shown only once and expires after 600 seconds. Re-pair the Pi locally using the new code.</p>
            <button disabled={busy} onClick={() => { setPairing(null); mutate(`${BASE}/${dialog.device.id}/pairing-code`, "POST", undefined, "pairing"); }}>{busy ? "Generating…" : "Revoke existing key and generate code"}</button>
          </>}
          {dialog.kind === "revoke" && <>
            <p>Revoke this Pi? Its device access will be revoked and it will be removed from this list.</p>
            <button className="HDDanger" disabled={busy} onClick={() => mutate(`${BASE}/${dialog.device.id}`, "DELETE", undefined, "delete")}>{busy ? "Revoking…" : "Confirm revocation"}</button>
          </>}
        </DeviceDialog>
      )}
    </section>
  );
}
