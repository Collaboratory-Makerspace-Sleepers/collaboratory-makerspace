import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { todaySentence } from "../../hooks/useDashboard";
import WaiverStatusPanel from "./WaiverStatusPanel";

function formatDateTime(value) {
  return new Date(value).toLocaleString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function formatPrice(cents) {
  if (cents === 0) return "Free";
  return `$${(cents / 100).toFixed(2)}`;
}

export default function BookClasses() {
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [sessions, setSessions] = useState([]);
  const [myEnrollments, setMyEnrollments] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [myLoading, setMyLoading] = useState(true);
  const [dataError, setDataError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/class-sessions")
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => { if (!cancelled) setSessions(data); })
      .catch(() => { if (!cancelled) setDataError("Unable to load classes."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [authFetch]);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/class-sessions/me")
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => { if (!cancelled) setMyEnrollments(data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setMyLoading(false); });
    return () => { cancelled = true; };
  }, [authFetch]);

  const visible = sessions.filter((s) =>
    s.title.toLowerCase().includes(search.toLowerCase())
  );

  const upcomingSessionIds = new Set(myEnrollments.map((e) => e.classSessionId));
  const upcomingSessions = sessions.filter((s) => upcomingSessionIds.has(s.id));

  return (
    <div className="DashHome1">
      <WaiverStatusPanel />
      <div className="Dash2">
        <p>Book Classes</p>
        <p>Welcome back. {todaySentence()}</p>
      </div>

      <div className="Dash3">
        <p>Your Upcoming Classes</p>
        {myLoading && <p>Loading…</p>}
        {!myLoading && upcomingSessions.length === 0 && (
          <p>You don&apos;t have any upcoming classes booked.</p>
        )}
        {!myLoading &&
          upcomingSessions.map((s) => (
            <div
              className="Dash4"
              key={s.id}
              style={{ cursor: "pointer" }}
              onClick={() => navigate(`/dashboard/bookclasses/session/${s.id}`)}
            >
              <p>{s.title}</p>
              <p className="Dash5-sub">{formatDateTime(s.startTime)}</p>
            </div>
          ))}
      </div>

      <input
        type="text"
        placeholder="Search classes"
        className="SearchInput"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {dataError && <p>{dataError}</p>}

      <div className="RentGrid">
        {loading && <p>Loading classes…</p>}
        {!loading &&
          visible.map((s) => (
            <div className="Rent1" key={s.id}>
              <div>
                <img
                  src={s.imageUrl || "https://picsum.photos/seed/class/480/360"}
                  alt={s.title}
                />
              </div>
              <div className="Rent2">
                <div className="Rent3">
                  <div className="Rent4">
                    <p>Class</p>
                  </div>
                  <p>{s.title}</p>
                  {s.instructorName && (
                    <p style={{ fontSize: "0.85em", color: "#666" }}>
                      with {s.instructorName}
                    </p>
                  )}
                  <p style={{ fontSize: "0.85em", color: "#444" }}>
                    {formatDateTime(s.startTime)}
                  </p>
                  <p style={{ fontSize: "0.85em", color: s.spotsRemaining === 0 && s.spotsRemaining !== -1 ? "red" : "#444" }}>
                    {s.spotsRemaining === -1
                      ? "Unlimited spots"
                      : s.spotsRemaining === 0
                      ? "Full"
                      : `${s.spotsRemaining} spot${s.spotsRemaining !== 1 ? "s" : ""} left`}
                  </p>
                </div>
                <div className="Rent5">
                  <p>{formatPrice(s.resolvedPriceCents)}</p>
                  <button onClick={() => navigate(`/dashboard/bookclasses/session/${s.id}`)}>
                    View
                  </button>
                </div>
              </div>
            </div>
          ))}
        {!loading && visible.length === 0 && !dataError && (
          <p>No classes match your search.</p>
        )}
      </div>
    </div>
  );
}
