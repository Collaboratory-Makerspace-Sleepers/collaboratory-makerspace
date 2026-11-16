import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useCatalog } from "../../hooks/useCatalog";
import { sortNewest } from "../../utils/catalog";
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
  const { items: sessions, loading, error: dataError } = useCatalog("/api/v1/class-sessions");
  const { items: myEnrollments, loading: myLoading, error: enrollmentError } = useCatalog("/api/v1/class-sessions/me");
  const [search, setSearch] = useState("");
  const visible = sortNewest(sessions).filter((s) =>
    s.title.toLowerCase().includes(search.toLowerCase())
  );

  const upcomingSessionIds = new Set(myEnrollments.map((e) => e.classSessionId));
  const upcomingSessions = sessions.filter((s) => upcomingSessionIds.has(s.id));

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Book Classes</p>
        <p>Welcome back. {todaySentence()}</p>
      </div>

      <div className="Dash3">
        <p>Your Upcoming Classes</p>
        {myLoading && <p>Loading…</p>}
        {enrollmentError && <p role="alert">{enrollmentError}</p>}
        {!myLoading && !enrollmentError && upcomingSessions.length === 0 && (
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

      <WaiverStatusPanel />

      <input
        type="text"
        placeholder="Search classes"
        className="SearchInput"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {dataError && <p role="alert">{dataError} Displayed classes may be outdated.</p>}

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
