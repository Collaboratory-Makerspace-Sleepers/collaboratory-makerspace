import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 6;

export default function UserReservedClasses() {
  const { authFetch } = useAuth();
  const [reservations, setReservations] = useState([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await authFetch("/api/v1/reservations");
        if (!res.ok) throw new Error("Could not load reservations.");
        const data = await res.json();
        
        if (cancelled) return;
        
        // Filter only ACTIVE/PENDING reservations and only classes (equipment with category CLASS)
        const classReservations = Array.isArray(data) 
          ? data.filter(r => 
              (r.status === "ACTIVE" || r.status === "PENDING") && 
              r.category === "CLASS"
            )
          : [];
        
        setReservations(classReservations);
        setError("");
      } catch (err) {
        if (!cancelled) {
          setError(err.message || "Could not load class reservations.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    
    load();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const totalPages = Math.ceil(reservations.length / PAGE_SIZE);
  const visible = reservations.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  
  const previousPage = () => setPage((p) => Math.max(0, p - 1));
  const nextPage = () => setPage((p) => Math.min(totalPages - 1, p + 1));

  return (
    <div className="urc-wrapper">
      <div className="urc-card">
        <div className="urc-header">
          <div className="urc-title">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z" fill="currentColor" />
            </svg>
            <h2>User Reserved Classes</h2>
          </div>

          {reservations.length > 0 && (
            <div className="urc-pagination">
              <button
                className="urc-page-btn"
                onClick={previousPage}
                disabled={loading || page === 0}
                aria-label="Previous page"
              >
                ‹
              </button>
              <span className="urc-page-info">
                {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
              </span>
              <button
                className="urc-page-btn"
                onClick={nextPage}
                disabled={loading || totalPages === 0 || page >= totalPages - 1}
                aria-label="Next page"
              >
                ›
              </button>
            </div>
          )}
        </div>

        {loading ? (
          <p className="urc-state" role="status">Loading class reservations…</p>
        ) : error ? (
          <p className="urc-state urc-error" role="alert">{error}</p>
        ) : reservations.length === 0 ? (
          <p className="urc-state">No upcoming class reservations.</p>
        ) : (
          <div className="urc-grid">
            {visible.map((reservation) => (
              <div key={reservation.id} className="urc-card-item">
                <div className="urc-card-header">
                  <h3>{reservation.equipmentName}</h3>
                  <span className={`urc-status ${reservation.status.toLowerCase()}`}>
                    {reservation.status}
                  </span>
                </div>
                
                <div className="urc-card-body">
                  <p className="urc-user">
                    <strong>User:</strong> {reservation.userName || reservation.userEmail || "Unknown"}
                  </p>
                  <p className="urc-time">
                    <strong>Date:</strong> {new Date(reservation.startTime).toLocaleDateString(undefined, {
                      weekday: "short", month: "short", day: "numeric"
                    })}
                  </p>
                  <p className="urc-time">
                    <strong>Time:</strong> {new Date(reservation.startTime).toLocaleTimeString(undefined, { 
                      hour: "numeric", minute: "2-digit" 
                    })} – {new Date(reservation.endTime).toLocaleTimeString(undefined, { 
                      hour: "numeric", minute: "2-digit" 
                    })}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
