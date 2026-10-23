import { useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";

const PLACEHOLDER_VIDEO_URL =
  "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";

/**
 * Shows a card for every TRAINING_PENDING reservation so the user can watch the
 * required safety video and activate their booking.
 *
 * Props:
 *   reservations – array from useDashboard() (may be empty while loading)
 *   loading      – boolean from useDashboard()
 */
export default function TrainingTasks({ reservations, loading }) {
  const { authFetch } = useAuth();

  // Derived list of pending tasks; updated when parent reservations change.
  const [pendingTasks, setPendingTasks] = useState([]);
  // { [reservationId]: true } — set client-side when video finishes.
  const [videoWatched, setVideoWatched] = useState({});
  // Checkbox "I watched the full video" per task.
  const [confirmed, setConfirmed] = useState({});
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState("");
  // Tracks how far through the video the user has watched (prevents skip-ahead).
  const watchedUntil = useRef({});

  useEffect(() => {
    setPendingTasks(
      (reservations || []).filter((r) => r.status === "TRAINING_PENDING")
    );
  }, [reservations]);

  function trackVideoProgress(reservationId, event) {
    const video = event.currentTarget;
    const previous = watchedUntil.current[reservationId] ?? 0;
    if (!video.paused && video.currentTime <= previous + 1.5) {
      watchedUntil.current[reservationId] = Math.max(previous, video.currentTime);
    }
  }

  function preventSeekingAhead(reservationId, event) {
    const video = event.currentTarget;
    const completedThrough = watchedUntil.current[reservationId] ?? 0;
    if (video.currentTime > completedThrough + 1.5) {
      video.currentTime = completedThrough;
    }
  }

  function handleVideoEnded(reservationId, event) {
    const video = event.currentTarget;
    const completedThrough = watchedUntil.current[reservationId] ?? 0;
    // Guard against seeking to the very end to bypass the check.
    if (Number.isFinite(video.duration) && video.duration - completedThrough > 1.5) {
      video.currentTime = completedThrough;
      return;
    }
    setVideoWatched((prev) => ({ ...prev, [reservationId]: true }));
  }

  async function handleComplete(task) {
    setBusyId(task.id);
    setError("");
    try {
      const res = await authFetch(`/api/v1/reservations/${task.id}/training-attest`, {
        method: "POST",
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not complete training.");
      }
      // Remove the completed task from the list.
      setPendingTasks((prev) => prev.filter((t) => t.id !== task.id));
    } catch (err) {
      setError(err.message || "Could not complete training.");
    } finally {
      setBusyId(null);
    }
  }

  // Don't render anything while loading or when there are no pending tasks.
  if (loading || pendingTasks.length === 0) return null;

  return (
    <section aria-labelledby="training-tasks-title" className="training-tasks">
      <div className="training-tasks-heading">
        <h2 id="training-tasks-title">Required training</h2>
        <span>{pendingTasks.length} pending</span>
      </div>

      {error && <p className="training-task-error" role="alert">{error}</p>}

      {pendingTasks.map((task) => {
        const watched = Boolean(videoWatched[task.id]);
        const videoUrl = task.trainingVideoUrl || PLACEHOLDER_VIDEO_URL;

        return (
          <article className="training-task" key={task.id}>
            <div className="training-task-heading">
              <div>
                <h3>{task.equipmentName} safety training</h3>
                <p>{watched ? "Video watched — confirm below" : "Watch the video to activate your reservation"}</p>
              </div>
              <span className="training-task-pending">Required</span>
            </div>

            {watched ? (
              <div className="training-task-confirm">
                <label>
                  <input
                    checked={Boolean(confirmed[task.id])}
                    onChange={(e) =>
                      setConfirmed((c) => ({ ...c, [task.id]: e.target.checked }))
                    }
                    type="checkbox"
                  />
                  <span>I watched the full training video and understand safe operation.</span>
                </label>
                <button
                  disabled={busyId === task.id || !confirmed[task.id]}
                  onClick={() => handleComplete(task)}
                >
                  {busyId === task.id ? "Saving…" : "Mark complete"}
                </button>
              </div>
            ) : (
              <div className="training-task-player">
                <video
                  controls
                  onEnded={(e) => handleVideoEnded(task.id, e)}
                  onSeeking={(e) => preventSeekingAhead(task.id, e)}
                  onTimeUpdate={(e) => trackVideoProgress(task.id, e)}
                  preload="metadata"
                  src={videoUrl}
                >
                  Your browser does not support video playback.
                </video>
              </div>
            )}
          </article>
        );
      })}
    </section>
  );
}