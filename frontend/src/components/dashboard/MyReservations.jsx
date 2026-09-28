import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const TIME_SLOTS = [
  "9:00 AM", "10:00 AM", "11:00 AM",
  "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM",
];
const SLOT_HOURS = {
  "9:00 AM": 9, "10:00 AM": 10, "11:00 AM": 11,
  "1:00 PM": 13, "2:00 PM": 14, "3:00 PM": 15, "4:00 PM": 16,
};

function RescheduleCalendar({ reservationId, onConfirm, onCancel, busy }) {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);

  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const firstWeekday = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const cells = [
    ...Array(firstWeekday).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  function goPrev() {
    if (viewMonth === 0) { setViewYear((y) => y - 1); setViewMonth(11); }
    else setViewMonth((m) => m - 1);
  }
  function goNext() {
    if (viewMonth === 11) { setViewYear((y) => y + 1); setViewMonth(0); }
    else setViewMonth((m) => m + 1);
  }

  function handleConfirm() {
    if (!selectedDate || !selectedSlot) return;
    const hour = SLOT_HOURS[selectedSlot];
    const start = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), hour, 0, 0);
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    onConfirm(reservationId, start.toISOString(), end.toISOString());
  }

  return (
    <div className="CalendarWrap">
      <div className="CalendarHeader">
        <p>{MONTHS[viewMonth]} {viewYear}</p>
        <div className="CalendarArrows">
          <button onClick={goPrev}>&lt;</button>
          <button onClick={goNext}>&gt;</button>
        </div>
      </div>
      <div className="CalendarWeekdays">
        {WEEKDAYS.map((d) => <p key={d}>{d}</p>)}
      </div>
      <div className="CalendarDays">
        {cells.map((day, i) =>
          day === null ? (
            <div key={`blank-${i}`} className="CalendarDayEmpty" />
          ) : (
            <button
              key={day}
              disabled={new Date(viewYear, viewMonth, day) < todayStart}
              className={
                new Date(viewYear, viewMonth, day) < todayStart
                  ? "CalendarDay CalendarDayDisabled"
                  : selectedDate?.getDate() === day && selectedDate?.getMonth() === viewMonth
                  ? "CalendarDay CalendarDaySelected"
                  : "CalendarDay"
              }
              onClick={() => { setSelectedDate(new Date(viewYear, viewMonth, day)); setSelectedSlot(null); }}
            >
              {day}
            </button>
          )
        )}
      </div>
      {selectedDate && (
        <div className="AvailableTimesGrid">
          {TIME_SLOTS.map((time) => (
            <button
              key={time}
              className={selectedSlot === time ? "TimeSlotButton TimeSlotSelected" : "TimeSlotButton"}
              onClick={() => setSelectedSlot(time)}
            >
              {time}
            </button>
          ))}
        </div>
      )}
      <div className="Dash5">
        <button
          disabled={!selectedDate || !selectedSlot || busy}
          onClick={handleConfirm}
        >
          {busy ? "Rescheduling…" : "Confirm reschedule"}
        </button>
        <button onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}

export default function MyReservations() {
  const { authFetch } = useAuth();
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [reschedulingId, setReschedulingId] = useState(null);
  const [busy, setBusy] = useState(null);

  const loadReservations = useCallback(() => {
    authFetch("/api/v1/reservations/me")
      .then((res) => {
        if (!res.ok) throw new Error();
        return res.json();
      })
      .then((data) => { setReservations(data); setLoading(false); })
      .catch(() => { setError("Could not load reservations."); setLoading(false); });
  }, [authFetch]);

  useEffect(() => { loadReservations(); }, [loadReservations]);

  async function handleCancel(id) {
    setActionError("");
    setBusy(id + "-cancel");
    try {
      const res = await authFetch(`/api/v1/reservations/${id}/cancel`, { method: "PATCH" });
      if (!res.ok) throw new Error("Could not cancel reservation");
      loadReservations();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  async function handleReschedule(id, startIso, endIso) {
    setActionError("");
    setBusy(id + "-reschedule");
    try {
      const res = await authFetch(`/api/v1/reservations/${id}/reschedule`, {
        method: "PATCH",
        body: JSON.stringify({ startTime: startIso, endTime: endIso }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || "Could not reschedule reservation");
      }
      setReschedulingId(null);
      loadReservations();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  const active = reservations.filter((r) => r.status === "ACTIVE");
  const past = reservations.filter((r) => r.status !== "ACTIVE");

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>My Reservations</p>
        <p>View, reschedule, or cancel your upcoming bookings.</p>
      </div>

      {error && <p>{error}</p>}
      {actionError && <p style={{ color: "red" }}>{actionError}</p>}
      {loading && <p>Loading…</p>}
      {!loading && active.length === 0 && <p>No upcoming reservations.</p>}

      {active.map((r) => (
        <div className="Dash3" key={r.id}>
          <div className="Dash4">
            <p>{r.equipmentName}</p>
            <p>
              {new Date(r.startTime).toLocaleDateString(undefined, {
                weekday: "long", month: "long", day: "numeric", year: "numeric",
              })}{" "}
              {new Date(r.startTime).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}
              {" – "}
              {new Date(r.endTime).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}
            </p>
            <div className="Dash5">
              <button
                onClick={() => setReschedulingId(reschedulingId === r.id ? null : r.id)}
              >
                {reschedulingId === r.id ? "Cancel reschedule" : "Reschedule"}
              </button>
              <button
                onClick={() => handleCancel(r.id)}
                disabled={busy === r.id + "-cancel"}
              >
                {busy === r.id + "-cancel" ? "Cancelling…" : "Cancel reservation"}
              </button>
            </div>
          </div>

          {reschedulingId === r.id && (
            <RescheduleCalendar
              reservationId={r.id}
              onConfirm={handleReschedule}
              onCancel={() => setReschedulingId(null)}
              busy={busy === r.id + "-reschedule"}
            />
          )}
        </div>
      ))}

      {past.length > 0 && (
        <>
          <div className="Dash2"><p>Past reservations</p></div>
          {past.map((r) => (
            <div className="Dash3" key={r.id}>
              <div className="Dash4">
                <p>{r.equipmentName}</p>
                <p>
                  {new Date(r.startTime).toLocaleDateString(undefined, {
                    weekday: "long", month: "long", day: "numeric", year: "numeric",
                  })}
                </p>
                <p style={{ color: r.status === "CANCELLED" ? "red" : "gray" }}>{r.status}</p>
              </div>
            </div>
          ))}
        </>
      )}
    </div>
  );
}