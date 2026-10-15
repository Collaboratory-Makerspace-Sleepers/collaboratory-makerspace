import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const dateTimeFmt = (value) =>
  new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

const PAGE_SIZE = 10;

export default function AllClasses() {
  const { authFetch } = useAuth();
  const [classes, setClasses] = useState([]);
  const [reservations, setReservations] = useState([]);
  const [usersById, setUsersById] = useState({});
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [classList, setClassList] = useState(null);
  const [details, setDetails] = useState(null);

  const load = useCallback(async () => {
    try {
      const [classRes, resRes, userRes] = await Promise.all([
        authFetch("/api/v1/equipment/category/CLASS"),
        authFetch("/api/v1/reservations/admin/all?page=0&size=500"),
        authFetch("/api/v1/users/activity?page=0&size=500"),
      ]);
      if (!classRes.ok) throw new Error("Could not load classes.");
      setClasses(await classRes.json());
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
      setError(loadError.message || "Could not load classes.");
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
    return classes
      .filter((c) => !term || (c.name || "").toLowerCase().includes(term))
      .map((c) => {
        const booked = reservations
          .filter((r) => r.equipmentId === c.id && r.status === "ACTIVE")
          .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
        const next = booked.find((r) => new Date(r.endTime) > now);
        return { cls: c, booked, next };
      });
  }, [classes, reservations, query]);

  const totalPages = Math.ceil(rows.length / PAGE_SIZE);
  const visible = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const userName = (userId) => {
    const u = usersById[userId];
    return u ? u.name || u.email : `User #${userId}`;
  };

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <h2>All Classes</h2>
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
          placeholder="Search by class name"
          aria-label="Search classes by name"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
        />

        {loading ? (
          <p className="ra-state" role="status">Loading classes…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : rows.length === 0 ? (
          <p className="ra-state">{query ? "No classes match your search." : "No classes yet."}</p>
        ) : (
          <div className="ra-table-scroll ae-scroll">
            <table className="ra-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Date</th>
                  <th>Instructor</th>
                  <th>Machine Used</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map(({ cls, booked, next }) => (
                  <tr key={cls.id}>
                    <td>{cls.name}</td>
                    <td>{next ? dateTimeFmt(next.startTime) : "Not scheduled"}</td>
                    <td>—</td>
                    <td>—</td>
                    <td className="ae-cell">
                      <div className="ae-actions">
                        <button className="ra-link" onClick={() => setClassList({ cls, booked })}>
                          Class list
                        </button>
                        <button className="ra-link" onClick={() => setDetails(cls)}>
                          More
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

      {classList && (
        <div className="ra-profile-backdrop" onClick={() => setClassList(null)}>
          <section
            aria-modal="true"
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="ra-profile-header">
              <h3>{classList.cls.name} – class list</h3>
              <button onClick={() => setClassList(null)}>Close</button>
            </header>
            {classList.booked.length === 0 ? (
              <p>No one is signed up yet.</p>
            ) : (
              <dl className="ra-profile-details">
                {classList.booked.map((r) => (
                  <div key={r.id}>
                    <dt>{userName(r.userId)}</dt>
                    <dd>{dateTimeFmt(r.startTime)}</dd>
                  </div>
                ))}
              </dl>
            )}
          </section>
        </div>
      )}

      {details && (
        <div className="ra-profile-backdrop" onClick={() => setDetails(null)}>
          <section
            aria-modal="true"
            className="ra-profile-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="ra-profile-header">
              <h3>{details.name}</h3>
              <button onClick={() => setDetails(null)}>Close</button>
            </header>
            <dl className="ra-profile-details">
              <div><dt>Description</dt><dd>{details.description || "—"}</dd></div>
              <div><dt>Status</dt><dd>{details.status}</dd></div>
              <div><dt>Training required</dt><dd>{details.trainingRequired ? "Yes" : "No"}</dd></div>
            </dl>
          </section>
        </div>
      )}
    </div>
  );
}
