import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

function formatDateTime(value) {
  return new Date(value).toLocaleString(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function formatDuration(start, end) {
  const minutes = Math.round((new Date(end) - new Date(start)) / 60000);
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m > 0 ? `${h}h ${m}m` : `${h}h`;
}

function formatPrice(cents) {
  if (cents === 0) return "Free";
  return `$${(cents / 100).toFixed(2)}`;
}

export default function ClassSessionDetail() {
  const { id } = useParams();
  const { authFetch } = useAuth();
  const navigate = useNavigate();

  const [session, setSession] = useState(null);
  const [myEnrollment, setMyEnrollment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [enrolling, setEnrolling] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [freeSuccess, setFreeSuccess] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      authFetch(`/api/v1/class-sessions/${id}`).then((r) => r.ok ? r.json() : null),
      authFetch("/api/v1/class-sessions/me").then((r) => r.ok ? r.json() : []),
    ])
      .then(([sessionData, enrollments]) => {
        if (cancelled) return;
        if (!sessionData) { setError("Session not found."); return; }
        setSession(sessionData);
        const mine = (enrollments || []).find((e) => e.classSessionId === sessionData.id);
        setMyEnrollment(mine || null);
      })
      .catch(() => { if (!cancelled) setError("Unable to load session."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [authFetch, id]);

  async function handleEnroll() {
    setEnrolling(true);
    try {
      const res = await authFetch(`/api/v1/class-sessions/${id}/enroll`, { method: "POST" });
      const data = await res.json();
      if (!res.ok) {
        setError(data.message || "Enrollment failed.");
        return;
      }
      if (data.free) {
        setFreeSuccess(true);
        setMyEnrollment({ classSessionId: session.id, status: "ENROLLED" });
      } else {
        window.location.href = data.sessionUrl;
      }
    } catch {
      setError("Enrollment failed. Please try again.");
    } finally {
      setEnrolling(false);
    }
  }

  async function handleCancel() {
    setCancelling(true);
    try {
      const res = await authFetch(`/api/v1/class-sessions/${id}/enroll`, { method: "DELETE" });
      if (res.ok) {
        setMyEnrollment(null);
      } else {
        setError("Could not cancel enrollment.");
      }
    } catch {
      setError("Could not cancel enrollment.");
    } finally {
      setCancelling(false);
    }
  }

  if (loading) return <div className="DashHome1"><p>Loading…</p></div>;
  if (error && !session) return <div className="DashHome1"><p>{error}</p></div>;
  if (!session) return null;

  const isFull = session.maxCapacity > 0 && session.spotsRemaining === 0 && session.spotsRemaining !== -1;
  const isEnrolled = myEnrollment?.status === "ENROLLED";
  const isCancelled = session.status === "CANCELLED";

  return (
    <div className="DashHome1">
      <button className="ra-link" onClick={() => navigate("/dashboard/bookclasses")} style={{ marginBottom: "1rem" }}>
        ← Back to Classes
      </button>

      <div className="Dash2">
        <p>{session.title}</p>
        {session.status !== "SCHEDULED" && (
          <span className="badge-cancelled">{session.status}</span>
        )}
      </div>

      {session.imageUrl && (
        <img
          src={session.imageUrl}
          alt={session.title}
          style={{ maxWidth: "100%", borderRadius: "8px", marginBottom: "1rem" }}
        />
      )}

      <div className="Dash3" style={{ gap: "0.5rem" }}>
        {session.description && <p>{session.description}</p>}

        <dl className="ra-profile-details">
          <div>
            <dt>Date &amp; Time</dt>
            <dd>{formatDateTime(session.startTime)}</dd>
          </div>
          <div>
            <dt>Duration</dt>
            <dd>{formatDuration(session.startTime, session.endTime)}</dd>
          </div>
          {session.location && (
            <div>
              <dt>Location</dt>
              <dd>{session.location}</dd>
            </div>
          )}
          {session.instructorName && (
            <div>
              <dt>Instructor</dt>
              <dd>{session.instructorName}</dd>
            </div>
          )}
          <div>
            <dt>Spots Remaining</dt>
            <dd>
              {session.spotsRemaining === -1
                ? "Unlimited"
                : isFull
                ? "Full"
                : `${session.spotsRemaining} of ${session.maxCapacity}`}
            </dd>
          </div>
          <div>
            <dt>Price</dt>
            <dd>
              {formatPrice(session.resolvedPriceCents)}
              {session.resolvedPriceCents < session.priceCents && session.priceCents > 0 && (
                <span style={{ marginLeft: "0.5rem", color: "green", fontSize: "0.85em" }}>
                  (50% studio discount applied)
                </span>
              )}
              {session.resolvedPriceCents === session.memberPriceCents
                && session.memberPriceCents < session.priceCents
                && session.priceCents > 0 && (
                <span style={{ marginLeft: "0.5rem", color: "green", fontSize: "0.85em" }}>
                  (member price)
                </span>
              )}
            </dd>
          </div>
        </dl>
      </div>

      {error && <p style={{ color: "red" }}>{error}</p>}
      {freeSuccess && (
        <p style={{ color: "green" }}>You&apos;re enrolled! See you at {session.title}.</p>
      )}

      <div style={{ marginTop: "1.5rem", display: "flex", gap: "1rem", flexWrap: "wrap" }}>
        {isEnrolled ? (
          <>
            <span className="badge-enrolled">You&apos;re enrolled</span>
            <button
              className="signButton"
              style={{ background: "#c0392b" }}
              onClick={handleCancel}
              disabled={cancelling || isCancelled}
            >
              {cancelling ? "Cancelling…" : "Cancel Enrollment"}
            </button>
          </>
        ) : isCancelled ? (
          <p>This session has been cancelled.</p>
        ) : isFull ? (
          <button className="signButton" disabled>Session Full</button>
        ) : (
          <button
            className="signButton"
            onClick={handleEnroll}
            disabled={enrolling}
          >
            {enrolling ? "Processing…" : "Enroll"}
          </button>
        )}
      </div>
    </div>
  );
}
