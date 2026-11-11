import AdminCalendar from "./AdminCalendar";
import RecentActivity from "./RecentActivity";
import { firstName, useDashboard } from "../../hooks/useDashboard";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function AdminHome() {
  const { user, loading } = useDashboard();
  const { authFetch } = useAuth();
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let inFlight = false;
    async function load() {
      if (inFlight) return;
      inFlight = true;
      try {
        const response = await authFetch("/api/v1/users/activity/summary", {
          signal: controller.signal,
          cache: "no-store",
        });
        if (!response.ok) throw new Error("Could not refresh dashboard totals.");
        const result = await response.json();
        if (!controller.signal.aborted) {
          setSummary(result);
          setError("");
        }
      } catch (err) {
        if (!controller.signal.aborted) setError(err.message);
      } finally {
        inFlight = false;
      }
    }
    function onVisible() {
      if (!document.hidden) load();
    }
    load();
    const timer = setInterval(onVisible, 5000);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      controller.abort();
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [authFetch, refreshKey]);

  const totals = [
    { label: "Upcoming equipment bookings", value: summary?.upcomingReservations, path: "/admin-dashboard/rentequipment" },
    { label: "Waivers awaiting staff approval", value: summary?.pendingWaivers, path: "/admin-dashboard/hardware" },
    { label: "Upcoming class enrollments", value: summary?.classEnrollments, path: "/admin-dashboard/bookclasses" },
    { label: "Successful payments", value: summary?.successfulPayments },
  ];

  return (
    <div className="DashHome1">
      <div className="AdHome">
        <p>Welcome back {loading ? "..." : firstName(user) || "User"}</p>
        <p>Makerspace Admin</p>
        <button type="button" onClick={() => setRefreshKey((key) => key + 1)}>Refresh dashboard</button>
        <p>Database activity refreshes every 5 seconds.</p>
        {error && <p role="alert">{error} Displayed totals may be outdated.</p>}
      </div>

      <div className="AdRow">
        <div className="AdCon">
          <AdminCalendar />
        </div>
        <div className="AdCon">
          <div className="AdR">
            {totals.map((total) => (
              <div className="AdCol" key={total.label}>
                <div>
                  <p>{total.label}</p>
                  <p>{total.value ?? (error ? "Unavailable" : "...")}</p>
                </div>
                {total.path && <Link to={total.path}>View</Link>}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="AdAct">
        <RecentActivity refreshKey={refreshKey} />
      </div>
    </div>
  );
}