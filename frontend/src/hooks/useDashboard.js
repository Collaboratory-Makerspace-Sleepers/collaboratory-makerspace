import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

export function useDashboard() {
  const { authFetch } = useAuth();
  const [user, setUser] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      authFetch("/api/v1/users/me").then((res) => {
        if (!res.ok) throw new Error("Could not load your profile");
        return res.json();
      }),
      authFetch("/api/v1/reservations/me").then((res) => {
        if (!res.ok) throw new Error("Could not load your reservations");
        return res.json();
      }),
    ])
      .then(([userData, reservationData]) => {
        if (cancelled) return;
        setUser(userData);
        setReservations(reservationData || []);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message || "Something went wrong");
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return { user, reservations, loading, error };
}

function ordinal(n) {
  const s = ["th", "st", "nd", "rd"];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}

export function todaySentence() {
  const now = new Date();
  const month = now.toLocaleString(undefined, { month: "long" });
  const year = now.getFullYear();
  return `Today is ${month} ${ordinal(now.getDate())} ${year}.`;
}

export function formatTime(iso) {
  const date = new Date(iso);
  return date.toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  });
}

export function upcomingReservations(reservations) {
  const now = Date.now();
  return (reservations || [])
    .filter((r) => r.status === "ACTIVE" && new Date(r.startTime).getTime() >= now)
    .slice()
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
}

export function reservationSentence(reservations) {
  const upcoming = upcomingReservations(reservations);
  if (upcoming.length === 0) {
    return "You have no upcoming reservations.";
  }
  if (upcoming.length === 1) {
    const { equipmentName, startTime } = upcoming[0];
    const start = new Date(startTime);
    const sameDay = new Date().toDateString() === start.toDateString();
    const time = formatTime(startTime);
    const when = sameDay ? `today at ${time}` : `on ${start.toLocaleDateString()} at ${time}`;
    return `You have 1 upcoming reservation for ${equipmentName} ${when}.`;
  }
  const next = upcoming[0];
  return `You have ${upcoming.length} upcoming reservations. Next one for ${next.equipmentName} at ${formatTime(next.startTime)}.`;
}

export function displayName(user) {
  if (!user) return "";
  if (user.firstName) {
    return user.lastName ? `${user.firstName} ${user.lastName}` : user.firstName;
  }
  return user.email ? user.email.split("@")[0] : "";
}

export function firstName(user) {
  if (!user) return "";
  if (user.firstName) return user.firstName;
  return user.email ? user.email.split("@")[0] : "";
}