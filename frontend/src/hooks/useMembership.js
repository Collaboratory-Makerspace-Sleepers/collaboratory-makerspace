import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

export function useMembership() {
  const { authFetch } = useAuth();
  const [membership, setMembership] = useState(undefined);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = useCallback(() => setRefreshKey((value) => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let inFlight = false;
    async function load(sync = false) {
      if (inFlight) return;
      inFlight = true;
      try {
        const response = await authFetch(`/api/v1/billing/me/subscription${sync ? "/sync" : ""}`, {
          method: sync ? "POST" : "GET",
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(30000)]),
          cache: "no-store",
        });
        if (!response.ok) throw new Error("Could not refresh membership. Please check again.");
        const data = response.status === 204 ? null : await response.json();
        if (!controller.signal.aborted) {
          setMembership(data);
          setError("");
        }
      } catch (err) {
        if (!controller.signal.aborted) setError(err.message);
      } finally {
        inFlight = false;
      }
    }
    load(true);
    const onVisible = () => { if (!document.hidden) load(); };
    const timer = setInterval(onVisible, 5000);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      controller.abort();
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [authFetch, refreshKey]);

  const active = !!membership
    && ["ACTIVE", "TRIALING", "PAST_DUE"].includes(membership.status)
    && new Date(membership.currentPeriodEnd).getTime() > Date.now();
  return { membership, error, refresh, active };
}
