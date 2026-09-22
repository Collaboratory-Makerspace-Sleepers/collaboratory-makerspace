import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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
const SLOT_HOURS = { "9:00 AM": 9, "10:00 AM": 10, "11:00 AM": 11, "1:00 PM": 13, "2:00 PM": 14, "3:00 PM": 15, "4:00 PM": 16 };

export default function ReserveEquipment() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [item, setItem] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmation, setConfirmation] = useState(null);
  const [submitError, setSubmitError] = useState(null);

  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());
  const [selectedDate, setSelectedDate] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch(`/api/v1/equipment/${id}`)
      .then((res) => {
        if (!res.ok) throw new Error("not found");
        return res.json();
      })
      .then((data) => {
        if (cancelled) return;
        setItem(data);
      })
      .catch(() => {
        if (cancelled) return;
        setLoadError("Equipment not found.");
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, id]);

  function goPrevMonth() {
    setViewMonth((m) => {
      if (m === 0) {
        setViewYear((y) => y - 1);
        return 11;
      }
      return m - 1;
    });
  }

  function goNextMonth() {
    setViewMonth((m) => {
      if (m === 11) {
        setViewYear((y) => y + 1);
        return 0;
      }
      return m + 1;
    });
  }

  const firstWeekday = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const calendarCells = [
    ...Array(firstWeekday).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());

  function isPastDay(day) {
    return new Date(viewYear, viewMonth, day) < todayStart;
  }

  function isTodaySelected() {
    return (
      selectedDate &&
      selectedDate.getFullYear() === today.getFullYear() &&
      selectedDate.getMonth() === today.getMonth() &&
      selectedDate.getDate() === today.getDate()
    );
  }

  function slotDisabled(time) {
    return isTodaySelected() && SLOT_HOURS[time] <= today.getHours();
  }

  async function handleReserve() {
    if (!selectedDate || !selectedSlot) return;
    const hour = SLOT_HOURS[selectedSlot];
    const start = new Date(
      selectedDate.getFullYear(),
      selectedDate.getMonth(),
      selectedDate.getDate(),
      hour,
      0,
      0
    );
    const end = new Date(start.getTime() + 60 * 60 * 1000);

    setSubmitting(true);
    setSubmitError(null);
    setConfirmation(null);
    try {
      const res = await authFetch("/api/v1/reservations", {
        method: "POST",
        body: JSON.stringify({
          equipmentId: Number(id),
          startTime: start.toISOString(),
          endTime: end.toISOString(),
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(
          body.message ||
            (body.status === 400
              ? "That date/time is no longer available. Please pick a future time."
              : "Unable to complete your reservation.")
        );
      }
      await res.json();
      setConfirmation({
        name: item.name,
        date: selectedDate.toLocaleDateString(undefined, {
          weekday: "long",
          month: "long",
          day: "numeric",
          year: "numeric",
        }),
        time: selectedSlot,
      });
    } catch (err) {
      setSubmitError(err.message || "Something went wrong.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loadError) {
    return (
      <div className="DashHome1">
        <p>{loadError}</p>
        <button onClick={() => navigate("/dashboard/rentequipment")}>Back</button>
      </div>
    );
  }

  if (!item) {
    return (
      <div className="DashHome1">
        <p>Loading…</p>
      </div>
    );
  }

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button onClick={() => navigate("/dashboard/rentequipment")}>Back</button>
        <p>{item.name}</p>
        <p>Select a date</p>
      </div>

      {confirmation && (
        <div className="Dash4 confirmation-banner">
          <p>
            Confirmed! Your reservation for {confirmation.name} is booked for{" "}
            {confirmation.date} at {confirmation.time}. It now shows under your
            upcoming reservations.
          </p>
          <div className="Dash5">
            <button onClick={() => navigate("/dashboard/home")}>View my reservations</button>
          </div>
        </div>
      )}

      {submitError && <p className="error-text">{submitError}</p>}

      <div className="CalendarWrap">
        <div className="CalendarHeader">
          <p>
            {MONTHS[viewMonth]} {viewYear}
          </p>
          <div className="CalendarArrows">
            <button onClick={goPrevMonth}>&lt;</button>
            <button onClick={goNextMonth}>&gt;</button>
          </div>
        </div>

        <div className="CalendarWeekdays">
          {WEEKDAYS.map((day) => (
            <p key={day}>{day}</p>
          ))}
        </div>

        <div className="CalendarDays">
          {calendarCells.map((day, index) =>
            day === null ? (
              <div key={`blank-${index}`} className="CalendarDayEmpty" />
            ) : (
              <button
                key={day}
                disabled={isPastDay(day)}
                className={
                  isPastDay(day)
                    ? "CalendarDay CalendarDayDisabled"
                    : selectedDate &&
                      selectedDate.getFullYear() === viewYear &&
                      selectedDate.getMonth() === viewMonth &&
                      selectedDate.getDate() === day
                    ? "CalendarDay CalendarDaySelected"
                    : "CalendarDay"
                }
                onClick={() => {
                  setSelectedDate(new Date(viewYear, viewMonth, day));
                  setSelectedSlot(null);
                  setConfirmation(null);
                }}
              >
                {day}
              </button>
            )
          )}
        </div>
      </div>

      {selectedDate && (
        <div className="AvailableTimesWrap">
          <p>
            Available times for {selectedDate.getMonth() + 1}/
            {selectedDate.getDate()}/{selectedDate.getFullYear()}
          </p>
          <div className="AvailableTimesGrid">
            {TIME_SLOTS.map((time) => (
              <button
                key={time}
                disabled={slotDisabled(time)}
                className={
                  selectedSlot === time ? "TimeSlotButton TimeSlotSelected" : "TimeSlotButton"
                }
                onClick={() => {
                  setSelectedSlot(time);
                  setConfirmation(null);
                }}
              >
                {time}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="ReserveSummary">
        <div className="ReserveSummaryTop">
          <div className="Rent4">
            <p>Available</p>
          </div>
          <p>$7/hr</p>
        </div>
        <div className="ReserveSummaryBottom">
          <p>{item.name}</p>
          <button
            disabled={!selectedDate || !selectedSlot || submitting}
            onClick={handleReserve}
          >
            {submitting ? "Booking…" : "Reserve"}
          </button>
        </div>
      </div>
    </div>
  );
}