import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

/** IDs of equipment/classes that someone has booked for right now; refreshed every 15 seconds. */
export default function useInUseNow() {
  const { authFetch } = useAuth();
  const [ids, setIds] = useState([]);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      authFetch("/api/v1/reservations/in-use-now")
        .then((res) => (res.ok ? res.json() : []))
        .then((data) => { if (!cancelled) setIds(data); })
        .catch(() => {});
    load();
    const timer = setInterval(load, 15000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [authFetch]);

  return ids;
}
