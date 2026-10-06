import { useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";

export default function TrainingTasks() {
  const { authFetch } = useAuth();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyTaskId, setBusyTaskId] = useState(null);
  const [error, setError] = useState("");
  const watchedUntil = useRef({});
  const trainingVideoUrl = import.meta.env.VITE_LASER_TRAINING_VIDEO_URL;

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/training-tasks/me")
      .then(async (response) => {
        if (!response.ok) throw new Error("Unable to load training tasks.");
        return response.json();
      })
      .then((data) => {
        if (!cancelled) setTasks(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || "Unable to load training tasks.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  async function recordVideoCompletion(task, event) {
    const video = event.currentTarget;
    const completedThrough = watchedUntil.current[task.id] ?? 0;
    if (!Number.isFinite(video.duration) || video.duration - completedThrough > 1.5) {
      video.currentTime = completedThrough;
      return;
    }

    setBusyTaskId(task.id);
    setError("");
    try {
      const response = await authFetch(`/api/v1/training-tasks/${task.id}/video-completed`, {
        method: "POST",
      });
      if (!response.ok) throw new Error("Unable to save video completion.");
      const updatedTask = await response.json();
      setTasks((current) => current.map((item) => item.id === task.id ? updatedTask : item));
    } catch (err) {
      setError(err.message || "Unable to save video completion.");
    } finally {
      setBusyTaskId(null);
    }
  }

  function trackVideoProgress(taskId, event) {
    const video = event.currentTarget;
    const previous = watchedUntil.current[taskId] ?? 0;
    if (!video.paused && video.currentTime <= previous + 1.5) {
      watchedUntil.current[taskId] = Math.max(previous, video.currentTime);
    }
  }

  function preventSeekingAhead(taskId, event) {
    const video = event.currentTarget;
    const completedThrough = watchedUntil.current[taskId] ?? 0;
    if (video.currentTime > completedThrough + 1.5) {
      video.currentTime = completedThrough;
    }
  }

  async function markTaskComplete(task) {
    setBusyTaskId(task.id);
    setError("");
    try {
      const response = await authFetch(`/api/v1/training-tasks/${task.id}/complete`, {
        method: "POST",
      });
      if (!response.ok) throw new Error("Watch the full training video before completing this task.");
      const updatedTask = await response.json();
      setTasks((current) => current.map((item) => item.id === task.id ? updatedTask : item));
    } catch (err) {
      setError(err.message || "Unable to complete this task.");
    } finally {
      setBusyTaskId(null);
    }
  }

  return (
    <section aria-labelledby="training-tasks-title" className="training-tasks">
      <div className="training-tasks-heading">
        <h2 id="training-tasks-title">Required training</h2>
        <span>{tasks.filter((task) => !task.completed).length} pending</span>
      </div>

      {loading && <p>Loading training tasks…</p>}
      {error && <p className="training-task-error" role="alert">{error}</p>}
      {!loading && !error && tasks.length === 0 && <p>No training tasks are pending.</p>}

      {tasks.map((task) => (
        <article className="training-task" key={task.id}>
          <div className="training-task-heading">
            <div>
              <h3>{task.equipmentName} safety training</h3>
              <p>{task.completed ? "Completed" : task.videoCompleted ? "Video watched" : "Pending"}</p>
            </div>
            {!task.completed && <span className="training-task-pending">Required</span>}
          </div>

          {task.completed ? (
            <p className="training-task-complete">Training completed.</p>
          ) : task.videoCompleted ? (
            <button
              disabled={busyTaskId === task.id}
              onClick={() => markTaskComplete(task)}
            >
              {busyTaskId === task.id ? "Saving…" : "Mark complete"}
            </button>
          ) : trainingVideoUrl ? (
            <div className="training-task-player">
              <video
                controls
                onEnded={(event) => recordVideoCompletion(task, event)}
                onSeeking={(event) => preventSeekingAhead(task.id, event)}
                onTimeUpdate={(event) => trackVideoProgress(task.id, event)}
                preload="metadata"
                src={trainingVideoUrl}
              >
                Your browser does not support video playback.
              </video>
            </div>
          ) : (
            <p className="training-task-unavailable">
              Training video is unavailable. This task cannot be completed yet.
            </p>
          )}
        </article>
      ))}
    </section>
  );
}