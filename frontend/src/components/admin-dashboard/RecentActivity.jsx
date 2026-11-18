import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 8;

function formatCreatedAt(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

const TableIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2.2" />
    <path d="M3 9h18M3 15h18M12 3v18" stroke="currentColor" strokeWidth="2.2" />
  </svg>
);

const TYPE_LABELS = {
  RESERVATION: "Booked",
  RESERVATION_CANCELLED: "Cancelled booking",
  WAIVER: "Waiver",
  PAYMENT: "Payment",
  CARD_TAP: "Card tap",
  CLASS_ENROLLMENT: "Class booking",
  TRAINING: "Training completed",
};

const REFRESH_MS = 5000;

export default function RecentActivity({ refreshKey = 0 }) {
  const { authFetch } = useAuth();
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    let inFlight = false;
    const controller = new AbortController();
    const load = () => {
      if (inFlight) return;
      inFlight = true;
      return authFetch("/api/v1/users/activity/feed?limit=60", { signal: controller.signal, cache: "no-store" })
        .then(async (response) => {
          if (!response.ok) throw new Error("Could not load recent user activity.");
          return response.json();
        })
        .then((result) => {
          if (cancelled) return;
          if (!Array.isArray(result)) throw new Error("Invalid activity response.");
          setItems(result);
          setPage((current) => Math.min(current, Math.max(0, Math.ceil(result.length / PAGE_SIZE) - 1)));
          setError("");
        })
        .catch((loadError) => {
          if (!cancelled) setError(loadError.message || "Could not load recent user activity.");
        })
        .finally(() => {
          inFlight = false;
          if (!cancelled) setLoading(false);
        });
    };
    const onVisible = () => {
      if (!document.hidden) load();
    };
    load();
    const timer = setInterval(onVisible, REFRESH_MS);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      cancelled = true;
      controller.abort();
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [authFetch, refreshKey]);

  const totalPages = Math.ceil(items.length / PAGE_SIZE);
  const visible = items.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const previousPage = () => setPage((p) => Math.max(0, p - 1));
  const nextPage = () => setPage((p) => Math.min(totalPages - 1, p + 1));

  return (
    <div className="ra-wrapper">
      <div className="ra-card">
        <div className="ra-header">
          <div className="ra-title">
            <TableIcon />
            <h2>Recent User Activity</h2>
          </div>

          <div className="ra-pagination">
            <button
              className="ra-page-btn"
              onClick={previousPage}
              disabled={loading || page === 0}
              aria-label="Previous page"
            >
              ‹
            </button>
            <span className="ra-page-info">
              {totalPages === 0 ? "0 of 0" : `${page + 1} of ${totalPages}`}
            </span>
            <button
              className="ra-page-btn"
              onClick={nextPage}
              disabled={loading || totalPages === 0 || page >= totalPages - 1}
              aria-label="Next page"
            >
              ›
            </button>
          </div>
        </div>

        {loading ? (
          <p className="ra-state" role="status">Loading activity…</p>
        ) : error ? (
          <p className="ra-state ra-error" role="alert">{error}</p>
        ) : items.length === 0 ? (
          <p className="ra-state">No recent activity.</p>
        ) : (
          <div className="ra-table-scroll">
            <table className="ra-table">
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Activity</th>
                  <th>Details</th>
                  <th>Time</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((item, index) => (
                  <tr key={`${item.type}-${item.time}-${index}`}>
                    <td>
                      {item.userName || item.userEmail || "Unknown"}
                      {item.userName && item.userEmail && <div>{item.userEmail}</div>}
                    </td>
                    <td>{TYPE_LABELS[item.type] || item.type}</td>
                    <td>
                      {item.detail}
                      {item.receiptUrl && <div><a href={item.receiptUrl} target="_blank" rel="noopener noreferrer">View receipt</a></div>}
                    </td>
                    <td>{formatCreatedAt(item.time)}</td>
                    <td>{item.status}</td>
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
