import { useCallback, useEffect, useRef, useState } from "react";

const POLL_MS = 4000;

/**
 * Tracks the signed-in user's latest hardware access request (waiver needed,
 * waiting for approval, approved, denied) by polling the backend.
 * `justApproved` is true once the request flips from signed to approved while
 * the dashboard is open, so the UI can tell the user without a page reload.
 */
export default function useHardwareRequest(authFetch, enabled) {
  const [current, setCurrent] = useState(null);
  const [loaded, setLoaded] = useState(false);
  const [justApproved, setJustApproved] = useState(false);
  const previous = useRef(null);

  const refresh = useCallback(async () => {
    try {
      const res = await authFetch("/api/v1/hardware/checkins/me/current");
      const next = res.status === 204 ? null : res.ok ? await res.json() : undefined;
      if (next === undefined) return;

      const prev = previous.current;
      if (prev && next && prev.id === next.id
          && prev.status === "WAIVER_SIGNED" && next.status === "WAIVER_APPROVED") {
        setJustApproved(true);
      }
      previous.current = next;
      setCurrent(next);
    } catch {
      // keep the last known state; the next poll retries
    } finally {
      setLoaded(true);
    }
  }, [authFetch]);

  useEffect(() => {
    if (!enabled) return undefined;
    refresh();
    const id = setInterval(() => {
      if (!document.hidden) refresh();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [enabled, refresh]);

  const dismissApproved = useCallback(() => setJustApproved(false), []);

  return { current, loaded, refresh, justApproved, dismissApproved };
}
