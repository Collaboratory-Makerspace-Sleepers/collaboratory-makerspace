import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

const CATALOG_CHANGED = "makerspace-catalog-changed";

export function notifyCatalogChanged() {
  window.dispatchEvent(new Event(CATALOG_CHANGED));
  if ("BroadcastChannel" in window) {
    const channel = new BroadcastChannel(CATALOG_CHANGED);
    channel.postMessage("refresh");
    channel.close();
  }
}

export function useCatalog(path) {
  const { authFetch } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    let inFlight = false;
    let refreshPending = false;
    async function load() {
      if (controller.signal.aborted) return;
      if (inFlight) {
        refreshPending = true;
        return;
      }
      inFlight = true;
      try {
        const response = await authFetch(path, {
          cache: "no-store",
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]),
        });
        if (!response.ok) throw new Error(`Could not refresh catalog (HTTP ${response.status}).`);
        const result = await response.json();
        if (!Array.isArray(result)) throw new Error("Invalid catalog response.");
        if (!controller.signal.aborted) {
          setItems(result);
          setError("");
        }
      } catch (error) {
        if (!controller.signal.aborted) setError(error.message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
        inFlight = false;
        if (refreshPending && !controller.signal.aborted) {
          refreshPending = false;
          load();
        }
      }
    }
    const onVisible = () => { if (!document.hidden) load(); };
    load();
    const timer = setInterval(onVisible, 5000);
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);
    window.addEventListener(CATALOG_CHANGED, load);
    const channel = "BroadcastChannel" in window ? new BroadcastChannel(CATALOG_CHANGED) : null;
    if (channel) channel.onmessage = load;
    return () => {
      controller.abort();
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
      window.removeEventListener(CATALOG_CHANGED, load);
      channel?.close();
    };
  }, [authFetch, path]);
  return { items, loading, error };
}
