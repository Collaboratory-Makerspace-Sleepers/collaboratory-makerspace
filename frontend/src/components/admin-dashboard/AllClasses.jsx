import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const dateTimeFmt = (value) =>
  new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

const PAGE_SIZE = 10;

const STATUS_LABELS = {
  SCHEDULED: { label: "Scheduled", color: "green" },
  CANCELLED: { label: "Cancelled", color: "red" },
  COMPLETED: { label: "Completed", color: "#888" },
};

const ALL_TIMES = Array.from({ length: 48 }, (_, i) => {
  const h = Math.floor(i / 2);
  const m = i % 2 === 0 ? "00" : "30";
  const label = `${h % 12 === 0 ? 12 : h % 12}:${m} ${h < 12 ? "AM" : "PM"}`;
  return { value: `${String(h).padStart(2, "0")}:${m}`, label };
});

const START_TIME_OPTIONS = ALL_TIMES.filter(({ value }) => value >= "06:00" && value <= "22:30");
const END_TIME_OPTIONS   = ALL_TIMES.filter(({ value }) => value >= "06:30" && value <= "23:00");

function toLocalDate(isoString) {
  if (!isoString) return "";
  const d = new Date(isoString);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function toLocalTime(isoString) {
  if (!isoString) return "";
  const d = new Date(isoString);
  const h = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${h}:${min}`;
}

export function ClassSessionForm({ initial, onSave, onClose }) {
  const [form, setForm] = useState({
    title: initial?.title ?? "",
    description: initial?.description ?? "",
    date: initial?.startTime ? toLocalDate(initial.startTime) : "",
    startTime: initial?.startTime ? toLocalTime(initial.startTime) : "",
    endTime: initial?.endTime ? toLocalTime(initial.endTime) : "",
    maxCapacity: initial?.maxCapacity ?? 10,
    priceDollars: initial ? (initial.priceCents / 100).toFixed(2) : "0.00",
    memberPriceDollars: initial ? (initial.memberPriceCents / 100).toFixed(2) : "0.00",
    location: initial?.location ?? "",
    imageUrl: initial?.imageUrl ?? "",
    instructorId: initial?.instructorId ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState("");

  function change(field) {
    return (e) => {
      const value = e.target.value;
      setForm((f) => {
        const next = { ...f, [field]: value };
        if (field === "priceDollars") {
          const half = (parseFloat(value) / 2) || 0;
          next.memberPriceDollars = half.toFixed(2);
        }
        return next;
      });
    };
  }

  async function submit(e) {
    e.preventDefault();
    setErr("");

    if (form.endTime <= form.startTime) {
      setErr("End time must be after start time.");
      return;
    }

    const startISO = new Date(`${form.date}T${form.startTime}`).toISOString();
    const endISO = new Date(`${form.date}T${form.endTime}`).toISOString();

    setSaving(true);
    try {
      await onSave({
        title: form.title,
        description: form.description || null,
        startTime: startISO,
        endTime: endISO,
        maxCapacity: parseInt(form.maxCapacity, 10) || 0,
        priceCents: Math.round(parseFloat(form.priceDollars) * 100) || 0,
        memberPriceCents: Math.round(parseFloat(form.memberPriceDollars) * 100) || 0,
        location: form.location || null,
        imageUrl: form.imageUrl || null,
        instructorId: form.instructorId ? parseInt(form.instructorId, 10) : null,
      });
    } catch (ex) {
      setErr(ex.message || "Save failed.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="ra-profile-backdrop" onClick={onClose}>
      <section
        className="ra-profile-dialog"
        onClick={(ev) => ev.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <header className="ra-profile-header">
          <h3>{initial ? "Edit Session" : "Create Class Session"}</h3>
          <button onClick={onClose}>Close</button>
        </header>
        <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
          <label>
            Title *
            <input required value={form.title} onChange={change("title")} className="au-search" />
          </label>
          <label>
            Description
            <textarea value={form.description} onChange={change("description")} rows={3} className="au-search" />
          </label>
          <label>
            Date *
            <input required type="date" value={form.date} onChange={change("date")} className="au-search" />
          </label>
          <label>
            Start Time *
            <select required value={form.startTime} onChange={change("startTime")} className="au-search">
              <option value="">-- select --</option>
              {START_TIME_OPTIONS.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </label>
          <label>
            End Time *
            <select required value={form.endTime} onChange={change("endTime")} className="au-search">
              <option value="">-- select --</option>
              {END_TIME_OPTIONS.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </label>
          <label>
            Location
            <input value={form.location} onChange={change("location")} className="au-search" />
          </label>
          <label>
            Max Capacity (0 = unlimited)
            <input type="number" min="0" value={form.maxCapacity} onChange={change("maxCapacity")} className="au-search" />
          </label>
          <label>
            Full Price ($)
            <input type="number" min="0" step="0.01" value={form.priceDollars} onChange={change("priceDollars")} className="au-search" />
          </label>
          <label>
            Member Price ($)
            <input type="number" min="0" step="0.01" value={form.memberPriceDollars} onChange={change("memberPriceDollars")} className="au-search" />
          </label>
          <label>
            Instructor User ID
            <input type="number" min="1" value={form.instructorId} onChange={change("instructorId")} className="au-search" placeholder="optional" />
          </label>
          <label>
            Image URL
            <input value={form.imageUrl} onChange={change("imageUrl")} className="au-search" placeholder="optional" />
          </label>
          {err && <p style={{ color: "red" }}>{err}</p>}
          <button type="submit" className="signButton" disabled={saving}>
            {saving ? "Saving…" : initial ? "Save Changes" : "Create Session"}
          </button>
        </form>
      </section>
    </div>
  );
}

export default function AllClasses({ createOpen, onCreateClose }) {
  const { authFetch } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editTarget, setEditTarget] = useState(null);
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await authFetch("/api/v1/class-sessions?all=true");
      if (!res.ok) throw new Error("Could not load sessions.");
      setSessions(await res.json());
      setError("");
    } catch (ex) {
      setError(ex.message || "Could not load sessions.");
    } finally {
      setLoading(false);
    }
  }, [authFetch]);

  useEffect(() => { load(); }, [load]);

  const rows = useMemo(() => {
    const term = query.trim().toLowerCase();
    return sessions.filter((s) => !term || s.title.toLowerCase().includes(term));
  }, [sessions, query]);

  const totalPages = Math.ceil(rows.length / PAGE_SIZE);
  const visible = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  async function handleCreate(data) {
    const res = await authFetch("/api/v1/class-sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || "Create failed.");
    }
    onCreateClose?.();
    await load();
  }

  async function handleEdit(data) {
    const res = await authFetch(`/api/v1/class-sessions/${editTarget.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || "Save failed.");
    }
    setEditTarget(null);
    await load();
  }

  async function handleCancelSession(sessionId) {
    if (!window.confirm("Cancel this session? Enrolled users will not be automatically notified.")) return;
    const res = await authFetch(`/api/v1/class-sessions/${sessionId}`, { method: "DELETE" });
    if (res.ok) await load();
    else setError("Could not cancel session.");
  }

  async function openRoster(sessionId) {
    setRosterLoading(true);
    setRoster({ sessionId, users: null });
    const res = await authFetch(`/api/v1/class-sessions/${sessionId}/roster`);
    setRoster({ sessionId, users: res.ok ? await res.json() : [] });
    setRosterLoading(false);
  }

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <h2>All Class Sessions</h2>
          </div>
          <div className="ra-pagination">
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              aria-label="Previous page"
            >‹</button>
            <span className="ra-page-info">
              {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
            </span>
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={totalPages === 0 || page >= totalPages - 1}
              aria-label="Next page"
            >›</button>
          </div>
        </div>

        <input
          type="search"
          className="au-search"
          placeholder="Search by title"
          aria-label="Search sessions"
          value={query}
          onChange={(ev) => { setQuery(ev.target.value); setPage(0); }}
        />

        {loading ? (
          <p className="ra-state" role="status">Loading…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : rows.length === 0 ? (
          <p className="ra-state">{query ? "No sessions match." : "No class sessions yet."}</p>
        ) : (
          <div className="ra-table-scroll ae-scroll">
            <table className="ra-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Date / Time</th>
                  <th>Instructor</th>
                  <th>Spots (enrolled/max)</th>
                  <th>Price</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((s) => {
                  const { label, color } = STATUS_LABELS[s.status] ?? { label: s.status, color: "#888" };
                  const spotsLabel = s.spotsRemaining === -1
                    ? `${s.enrolledCount} / ∞`
                    : `${s.enrolledCount} / ${s.maxCapacity}`;
                  return (
                    <tr key={s.id}>
                      <td>{s.title}</td>
                      <td>{dateTimeFmt(s.startTime)}</td>
                      <td>{s.instructorName ?? "—"}</td>
                      <td>{spotsLabel}</td>
                      <td>{s.priceCents === 0 ? "Free" : `$${(s.priceCents / 100).toFixed(2)}`}</td>
                      <td><span style={{ color }}>{label}</span></td>
                      <td className="ae-cell">
                        <div className="ae-actions">
                          <button className="ra-link" onClick={() => openRoster(s.id)}>Roster</button>
                          <button className="ra-link" onClick={() => setEditTarget(s)}>Edit</button>
                          {s.status === "SCHEDULED" && (
                            <button className="ra-link" style={{ color: "red" }} onClick={() => handleCancelSession(s.id)}>
                              Cancel
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {createOpen && (
        <ClassSessionForm onSave={handleCreate} onClose={onCreateClose} />
      )}
      {editTarget && (
        <ClassSessionForm initial={editTarget} onSave={handleEdit} onClose={() => setEditTarget(null)} />
      )}

      {roster && (
        <div className="ra-profile-backdrop" onClick={() => setRoster(null)}>
          <section
            className="ra-profile-dialog"
            onClick={(ev) => ev.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <header className="ra-profile-header">
              <h3>Roster</h3>
              <button onClick={() => setRoster(null)}>Close</button>
            </header>
            {rosterLoading || roster.users === null ? (
              <p>Loading…</p>
            ) : roster.users.length === 0 ? (
              <p>No one is enrolled yet.</p>
            ) : (
              <dl className="ra-profile-details">
                {roster.users.map((u) => (
                  <div key={u.enrollmentId}>
                    <dt>{u.name || u.email}</dt>
                    <dd>{u.email}</dd>
                  </div>
                ))}
              </dl>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
