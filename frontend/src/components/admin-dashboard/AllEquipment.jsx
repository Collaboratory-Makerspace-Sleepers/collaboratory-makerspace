import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const STATUSES = ["AVAILABLE", "IN_USE", "MAINTENANCE", "RETIRED"];
const PAGE_SIZE = 10;

const timeFmt = (date) =>
  date.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });

const dateTimeFmt = (value) =>
  new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

function describeAvailability(equipment, reservations, now) {
  const upcoming = reservations
    .filter((r) => r.status === "ACTIVE" && new Date(r.endTime) > now)
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
  const current = upcoming.find((r) => new Date(r.startTime) <= now);
  const next = upcoming.find((r) => new Date(r.startTime) > now);

  if (equipment.status === "MAINTENANCE") return { label: "In maintenance", reservation: null };
  if (equipment.status === "RETIRED") return { label: "Retired", reservation: null };

  if (current) {
    return {
      label: `In use until ${timeFmt(new Date(current.endTime))}`,
      reservation: current,
    };
  }
  if (equipment.status === "IN_USE") return { label: "In use", reservation: null };

  if (next) {
    const start = new Date(next.startTime);
    const minutes = (start - now) / 60000;
    if (minutes <= 60) return { label: "Reserved for the next hour", reservation: next };
    if (start.toDateString() === now.toDateString()) {
      return { label: `Available until ${timeFmt(start)}`, reservation: next };
    }
  }
  return { label: "Available for walk-ins", reservation: null };
}

export default function AllEquipment() {
  const { authFetch } = useAuth();
  const [equipment, setEquipment] = useState([]);
  const [reservations, setReservations] = useState([]);
  const [usersById, setUsersById] = useState({});
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [viewUser, setViewUser] = useState(null);
  const [availabilityFor, setAvailabilityFor] = useState(null);
  const [editing, setEditing] = useState(null);
  const [saveError, setSaveError] = useState("");

  const load = useCallback(async () => {
    try {
      const [eqRes, resRes, userRes] = await Promise.all([
        authFetch("/api/v1/equipment"),
        authFetch("/api/v1/reservations/admin/all?page=0&size=500"),
        authFetch("/api/v1/users/activity?page=0&size=500"),
      ]);
      if (!eqRes.ok) throw new Error("Could not load equipment.");
      setEquipment(await eqRes.json());
      if (resRes.ok) {
        const body = await resRes.json();
        setReservations(Array.isArray(body.content) ? body.content : []);
      }
      if (userRes.ok) {
        const body = await userRes.json();
        const map = {};
        (body.content || []).forEach((u) => {
          map[u.id] = u;
        });
        setUsersById(map);
      }
      setError("");
    } catch (loadError) {
      setError(loadError.message || "Could not load equipment.");
    } finally {
      setLoading(false);
    }
  }, [authFetch]);

  useEffect(() => {
    load();
  }, [load]);

  const rows = useMemo(() => {
    const now = new Date();
    const term = query.trim().toLowerCase();
    return equipment
      .filter((e) => !term || (e.name || "").toLowerCase().includes(term))
      .map((e) => ({
        equipment: e,
        ...describeAvailability(
          e,
          reservations.filter((r) => r.equipmentId === e.id),
          now
        ),
      }));
  }, [equipment, reservations, query]);

  const visible = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.ceil(rows.length / PAGE_SIZE);

  const userName = (userId) => {
    const u = usersById[userId];
    return u ? u.name || u.email : `User #${userId}`;
  };

  async function saveEquipment(event) {
    event.preventDefault();
    setSaveError("");
    const response = await authFetch(`/api/v1/equipment/${editing.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(editing),
    });
    if (!response.ok) {
      setSaveError("Could not save equipment.");
      return;
    }
    setEditing(null);
    load();
  }

  const setField = (field) => (event) =>
    setEditing((prev) => ({ ...prev, [field]: event.target.value }));

  const availabilityList = availabilityFor
    ? reservations
        .filter(
          (r) =>
            r.equipmentId === availabilityFor.id &&
            r.status === "ACTIVE" &&
            new Date(r.endTime) > new Date()
        )
        .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
    : [];

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <h2>All Equipment</h2>
          </div>

          <div className="ra-pagination">
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              aria-label="Previous page"
            >
              ‹
            </button>
            <span className="ra-page-info">
              {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
            </span>
            <button
              className="ra-page-btn"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={totalPages === 0 || page >= totalPages - 1}
              aria-label="Next page"
            >
              ›
            </button>
          </div>
        </div>

        <input
          type="search"
          className="au-search"
          placeholder="Search by equipment name"
          aria-label="Search equipment by name"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
        />

        {loading ? (
          <p className="ra-state" role="status">Loading equipment…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : visible.length === 0 ? (
          <p className="ra-state">{query ? "No equipment matches your search." : "No equipment yet."}</p>
        ) : (
          <div className="ra-table-scroll ae-scroll">
            <table className="ra-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Current Availability</th>
                  <th>Reserved By</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map(({ equipment: e, label, reservation }) => (
                  <tr key={e.id}>
                    <td>{e.name}</td>
                    <td>{label}</td>
                    <td>
                      {reservation ? (
                        <>
                          {userName(reservation.userId)}{" "}
                          <button
                            className="ra-link"
                            onClick={() =>
                              setViewUser(usersById[reservation.userId] || { name: userName(reservation.userId) })
                            }
                          >
                            View user
                          </button>
                        </>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="ae-cell">
                      <div className="ae-actions">
                        <button className="ra-link" onClick={() => setEditing({ ...e })}>
                          Edit equipment
                        </button>
                        <button className="ra-link" onClick={() => setAvailabilityFor(e)}>
                          View availability
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {viewUser && (
        <div className="ra-profile-backdrop" onClick={() => setViewUser(null)}>
          <section
            aria-modal="true"
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="ra-profile-header">
              <h3>User Profile</h3>
              <button onClick={() => setViewUser(null)}>Close</button>
            </header>
            <dl className="ra-profile-details">
              <div><dt>Name</dt><dd>{viewUser.name || "Unknown user"}</dd></div>
              <div><dt>Email</dt><dd>{viewUser.email || "—"}</dd></div>
              <div><dt>Membership</dt><dd>{viewUser.membership || "None"}</dd></div>
              <div><dt>Allowed</dt><dd>{viewUser.allowed || "Not allowed"}</dd></div>
              <div><dt>Waiver</dt><dd>{viewUser.waiverSigned ? "Signed" : "Not signed"}</dd></div>
            </dl>
          </section>
        </div>
      )}

      {availabilityFor && (
        <div className="ra-profile-backdrop" onClick={() => setAvailabilityFor(null)}>
          <section
            aria-modal="true"
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="ra-profile-header">
              <h3>{availabilityFor.name} availability</h3>
              <button onClick={() => setAvailabilityFor(null)}>Close</button>
            </header>
            {availabilityList.length === 0 ? (
              <p>No upcoming reservations.</p>
            ) : (
              <dl className="ra-profile-details">
                {availabilityList.map((r) => (
                  <div key={r.id}>
                    <dt>{userName(r.userId)}</dt>
                    <dd>{dateTimeFmt(r.startTime)} – {dateTimeFmt(r.endTime)}</dd>
                  </div>
                ))}
              </dl>
            )}
          </section>
        </div>
      )}

      {editing && (
        <div className="ra-profile-backdrop" onClick={() => setEditing(null)}>
          <form
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            onSubmit={saveEquipment}
          >
            <header className="ra-profile-header">
              <h3>Edit equipment</h3>
              <button type="button" onClick={() => setEditing(null)}>Close</button>
            </header>
            <dl className="ra-profile-details">
              <div>
                <dt>Name</dt>
                <dd><input required value={editing.name || ""} onChange={setField("name")} /></dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd><input value={editing.category || ""} onChange={setField("category")} /></dd>
              </div>
              <div>
                <dt>Description</dt>
                <dd><input value={editing.description || ""} onChange={setField("description")} /></dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>
                  <select value={editing.status} onChange={setField("status")}>
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </dd>
              </div>
            </dl>
            {saveError && <p className="ra-error" role="alert">{saveError}</p>}
            <button type="submit">Save</button>
          </form>
        </div>
      )}
    </div>
  );
}
