import { useEffect, useState } from "react";

function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

function currentBooking(reservations, equipmentId, now) {
  return (reservations || [])
    .filter((r) => r.status === "ACTIVE" && r.equipmentId === equipmentId
      && new Date(r.endTime).getTime() > now)
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))[0];
}

function duration(ms) {
  const minutes = Math.max(1, Math.ceil(ms / 60000));
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

const time = (iso) =>
  new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });

/** Status pill for an equipment/class card; shows the user's own booking when there is one. */
export function BookingBadge({ reservations, equipmentId, fallback }) {
  const now = useNow();
  const booking = currentBooking(reservations, equipmentId, now);
  if (!booking) {
    return <div className="Rent4"><p>{fallback}</p></div>;
  }
  const inUse = new Date(booking.startTime).getTime() <= now;
  return (
    <div className="Rent4 booked"><p>{inUse ? "In use by you" : "Booked by you"}</p></div>
  );
}

/** Booking time and countdown line shown on a card the user has booked. */
export function BookingDetail({ reservations, equipmentId }) {
  const now = useNow();
  const booking = currentBooking(reservations, equipmentId, now);
  if (!booking) return null;
  const start = new Date(booking.startTime);
  const inUse = start.getTime() <= now;
  const day = start.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
  return (
    <p className="BookingDetail">
      {day}, {time(booking.startTime)} – {time(booking.endTime)}
      <br />
      {inUse
        ? `Available again in ${duration(new Date(booking.endTime).getTime() - now)}`
        : `Starts in ${duration(start.getTime() - now)}`}
    </p>
  );
}
