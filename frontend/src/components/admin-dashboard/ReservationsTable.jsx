import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 10;

function duration(start, end) {
  const minutes = Math.round((new Date(end) - new Date(start)) / 60000);
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} hr ${m} min` : `${h} hr`;
}

/** Every booking with who reserved it, the day, time and length. `isClass` picks classes vs equipment. */
export default function ReservationsTable({ isClass, title }) {
  const { authFetch } = useAuth();
  const [rows, setRows] = useState([]);
  const [users, setUsers] = useState({});
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [resRes, userRes] = await Promise.all([
        authFetch("/api/v1/reservations/admin/all?page=0&size=500"),
        authFetch("/api/v1/users/activity?page=0&size=500"),
      ]);
      if (!resRes.ok) throw new Error("Could not load reservations.");
      const body = await resRes.json();
      setRows(Array.isArray(body.content) ? body.content : []);
      if (userRes.ok) {
        const map = {};
        ((await userRes.json()).content || []).forEach((u) => { map[u.id] = u; });
        setUsers(map);
      }
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, [authFetch]);

  useEffect(() => {
    const first = setTimeout(load, 0);
    const timer = setInterval(() => { if (!document.hidden) load(); }, 15000);
    return () => { clearTimeout(first); clearInterval(timer); };
  }, [load]);

  const filtered = useMemo(
    () => rows
      .filter((r) => (r.equipmentCategory === "CLASS") === isClass)
      .sort((a, b) => new Date(b.startTime) - new Date(a.startTime)),
    [rows, isClass]
  );
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const visible = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const who = (id) => users[id]?.name || users[id]?.email || `User #${id}`;

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title"><h2>{title}</h2></div>
          <div className="ra-pagination">
            <button className="ra-page-btn" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="Previous page">‹</button>
            <span className="ra-page-info">{totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}</span>
            <button className="ra-page-btn" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)} aria-label="Next page">›</button>
          </div>
        </div>
        {error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : filtered.length === 0 ? (
          <p className="ra-state">No reservations yet.</p>
        ) : (
          <div className="ra-table-scroll">
            <table className="ra-table">
              <thead>
                <tr><th>{isClass ? "Class" : "Equipment"}</th><th>Reserved by</th><th>Date</th><th>Time</th><th>Length</th><th>Status</th></tr>
              </thead>
              <tbody>
                {visible.map((r) => (
                  <tr key={r.id}>
                    <td>{r.equipmentName}</td>
                    <td>{who(r.userId)}</td>
                    <td>{new Date(r.startTime).toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric", year: "numeric" })}</td>
                    <td>
                      {new Date(r.startTime).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}
                      {" – "}
                      {new Date(r.endTime).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}
                    </td>
                    <td>{duration(r.startTime, r.endTime)}</td>
                    <td>{r.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
