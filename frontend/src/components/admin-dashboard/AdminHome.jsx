import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import RecentActivity from "./RecentActivity";
import { firstName, useDashboard } from "../../hooks/useDashboard";
import { useAuth } from "../../context/AuthContext";

export default function AdminHome() {
  const { user, loading } = useDashboard();
  const { authFetch } = useAuth();
  const navigate = useNavigate();

  const [inUseIds, setInUseIds] = useState([]);
  const [visitsToday, setVisitsToday] = useState(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const [inUseRes, feedRes] = await Promise.all([
          authFetch("/api/v1/reservations/in-use-now"),
          authFetch("/api/v1/users/activity/feed?limit=200"),
        ]);

        if (!cancelled && inUseRes.ok) {
          const ids = await inUseRes.json();
          setInUseIds(Array.isArray(ids) ? ids : []);
        }

        if (!cancelled && feedRes.ok) {
          const feed = await feedRes.json();
          const today = new Date().toDateString();
          const count = (Array.isArray(feed) ? feed : []).filter(
            (item) => item.type === "CARD_TAP" && new Date(item.time).toDateString() === today
          ).length;
          setVisitsToday(count);
        }
      } catch {
        // leave defaults
      }
    };

    load();
    const timer = setInterval(() => { if (!document.hidden) load(); }, 30000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [authFetch]);

  return (
    <div className="DashHome1">
      <div className="AdHome">
        <div className="admin-welcome">
          <p className="admin-welcome-name">Welcome back, {loading ? "..." : firstName(user) || "Admin"}</p>
          <p className="admin-welcome-role">Makerspace Admin</p>
        </div>
      </div>

      <div className="admin-stat-row">
        <div className="admin-stat-card">
          <p className="admin-stat-card__label">Machines In Use</p>
          <p className="admin-stat-card__value">{inUseIds.length}</p>
          <button onClick={() => navigate("/admin-dashboard/rentequipment")}>View</button>
        </div>

        <div className="admin-stat-card">
          <p className="admin-stat-card__label">Visits Today</p>
          <p className="admin-stat-card__value">{visitsToday === null ? "—" : visitsToday}</p>
          <button onClick={() => navigate("/admin-dashboard/home")}>View</button>
        </div>

        <div className="admin-stat-card">
          <p className="admin-stat-card__label">Cancellation Requests</p>
          <p className="admin-stat-card__value">0</p>
          <span className="admin-stat-card__badge">Coming soon</span>
        </div>
      </div>

      <div className="AdAct">
        <RecentActivity />
      </div>
    </div>
  );
}