import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { CATEGORY_LABELS } from "./RentEquipment";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const DURATION_OPTIONS = [1, 2, 3, 4];

// Training-required equipment: 6 AM – 11 PM (close at 23:00)
// General equipment: 24/7 (close at 24:00, i.e. end of day)
const HOURS_TRAINING = { openingHour: 6,  closingHour: 23 };
const HOURS_ALL_DAY  = { openingHour: 0,  closingHour: 24 };

function formatHour(hour) {
  if (hour === 0 || hour === 24) return "12:00 AM";
  if (hour === 12) return "12:00 PM";
  if (hour > 12) return `${hour - 12}:00 PM`;
  return `${hour}:00 AM`;
}

/** Returns { "6:00 AM": 6, "7:00 AM": 7, … } for the given range. */
function buildSlotHours(openingHour, closingHour) {
  const map = {};
  for (let h = openingHour; h < closingHour; h++) {
    map[formatHour(h)] = h;
  }
  return map;
}

export default function ReserveEquipment() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [item, setItem] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [durationHours, setDurationHours] = useState(1);

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
    return () => { cancelled = true; };
  }, [authFetch, id]);

  const [booked, setBooked] = useState([]);
  const [monthBooked, setMonthBooked] = useState([]);

  // Load booked ranges for the selected day so those time slots are blocked.
  useEffect(() => {
    if (!selectedDate) return undefined;
    let cancelled = false;
    const from = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate());
    const to = new Date(from.getTime() + 24 * 60 * 60 * 1000);
    const query = `from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`;
    authFetch(`/api/v1/reservations/equipment/${id}/booked?${query}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => { if (!cancelled) setBooked(data); })
      .catch(() => { if (!cancelled) setBooked([]); });
    return () => { cancelled = true; };
  }, [authFetch, id, selectedDate]);

  // Booked ranges for the visible month, used to colour calendar days.
  useEffect(() => {
    let cancelled = false;
    const from = new Date(viewYear, viewMonth, 1);
    const to = new Date(viewYear, viewMonth + 1, 1);
    const query = `from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`;
    authFetch(`/api/v1/reservations/equipment/${id}/booked?${query}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => { if (!cancelled) setMonthBooked(data); })
      .catch(() => { if (!cancelled) setMonthBooked([]); });
    return () => { cancelled = true; };
  }, [authFetch, id, viewYear, viewMonth]);

  function goPrevMonth() {
    setViewMonth((m) => {
      if (m === 0) { setViewYear((y) => y - 1); return 11; }
      return m - 1;
    });
  }

  function goNextMonth() {
    setViewMonth((m) => {
      if (m === 11) { setViewYear((y) => y + 1); return 0; }
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

  const { openingHour, closingHour } = item?.requiresTraining ? HOURS_TRAINING : HOURS_ALL_DAY;
  const slotHours = item ? buildSlotHours(openingHour, closingHour) : {};
  const timeSlots = Object.keys(slotHours);

  function slotBooked(time) {
    if (!selectedDate) return false;
    const start = new Date(
      selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), slotHours[time]
    );
    const end = new Date(start.getTime() + durationHours * 60 * 60 * 1000);
    return booked.some((b) => new Date(b.startTime) < end && new Date(b.endTime) > start);
  }

  // "full" when every slot is taken that day, "partial" when at least one is.
  function dayBookingState(day) {
    const taken = timeSlots.filter((time) => {
      const start = new Date(viewYear, viewMonth, day, slotHours[time]);
      const end = new Date(start.getTime() + 60 * 60 * 1000);
      return monthBooked.some((b) => new Date(b.startTime) < end && new Date(b.endTime) > start);
    }).length;
    if (taken === 0) return null;
    return taken >= timeSlots.length ? "full" : "partial";
  }

  function slotDisabled(time) {
    return (isTodaySelected() && slotHours[time] <= today.getHours()) || slotBooked(time);
  }

  function durationDisabled(hours) {
    if (!selectedSlot) return true;
    return slotHours[selectedSlot] + hours > closingHour;
  }

  function handleReserve() {
    if (!selectedDate || !selectedSlot) return;
    const startHour = slotHours[selectedSlot];
    const endHour = startHour + durationHours;
    const start = new Date(
      selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(),
      startHour, 0, 0
    );
    const end = new Date(
      selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(),
      endHour, 0, 0
    );
    const displayTime = `${selectedSlot} – ${formatHour(endHour)} (${durationHours} hr${durationHours > 1 ? "s" : ""})`;

    navigate(
      `/dashboard/rentequipment/${id}/waiver`,
      {
        state: {
          equipmentId: Number(id),
          equipmentName: item.name,
          requiresTraining: item.requiresTraining,
          trainingVideoUrl: item.trainingVideoUrl || null,
          currentWaiverVersion: item.currentWaiverVersion ?? 1,
          hourlyRateCents: item.hourlyRateCents,
          durationHours,
          waiverRequired: true,
          startTime: start.toISOString(),
          endTime: end.toISOString(),
          displayDate: selectedDate.toLocaleDateString(undefined, {
            weekday: "long", month: "long", day: "numeric", year: "numeric",
          }),
          displayTime,
        },
      }
    );
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
    return <div className="DashHome1"><p>Loading…</p></div>;
  }

  const rateLabel = item.hourlyRateCents > 0
    ? `$${(item.hourlyRateCents / 100).toFixed(0)}/hr`
    : "Free";

  const estimatedTotal = selectedSlot
    ? ((item.hourlyRateCents / 100) * durationHours).toFixed(2)
    : null;

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button
          onClick={() =>
            navigate(
              item.category
                ? `/dashboard/rentequipment/category/${item.category}`
                : "/dashboard/rentequipment"
            )
          }
        >
          ← Back to {CATEGORY_LABELS[item.category] ?? "Equipment"}
        </button>
        <p>{item.name}</p>
        <p>Select a date and time</p>
      </div>

      <div className="CalendarWrap">
        <div className="CalendarHeader">
          <p>{MONTHS[viewMonth]} {viewYear}</p>
          <div className="CalendarArrows">
            <button onClick={goPrevMonth}>&lt;</button>
            <button onClick={goNextMonth}>&gt;</button>
          </div>
        </div>

        <div className="CalendarWeekdays">
          {WEEKDAYS.map((day) => <p key={day}>{day}</p>)}
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
                  setDurationHours(1);
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
            Start time for {selectedDate.getMonth() + 1}/
            {selectedDate.getDate()}/{selectedDate.getFullYear()}
          </p>
          <div className="AvailableTimesGrid">
            {timeSlots.map((time) => (
              <button
                key={time}
                disabled={slotDisabled(time)}
                className={selectedSlot === time ? "TimeSlotButton TimeSlotSelected" : "TimeSlotButton"}
                onClick={() => {
                  setSelectedSlot(time);
                  setDurationHours(1);
                }}
              >
                {time}
              </button>
            ))}
          </div>
        </div>
      )}

      {selectedSlot && (
        <div className="AvailableTimesWrap">
          <p>Duration</p>
          <div className="AvailableTimesGrid">
            {DURATION_OPTIONS.map((hrs) => (
              <button
                key={hrs}
                disabled={durationDisabled(hrs)}
                className={durationHours === hrs && !durationDisabled(hrs) ? "TimeSlotButton TimeSlotSelected" : "TimeSlotButton"}
                onClick={() => setDurationHours(hrs)}
              >
                {hrs} hr{hrs > 1 ? "s" : ""}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="ReserveSummary">
        <div className="ReserveSummaryTop">
          <div className="Rent4"><p>Available</p></div>
          <p>{rateLabel}</p>
        </div>
        <div className="ReserveSummaryBottom">
          <div>
            <p>{item.name}</p>
            {estimatedTotal && item.hourlyRateCents > 0 && (
              <p className="Dash5-sub">
                Est. total: ${estimatedTotal} ({durationHours} hr{durationHours > 1 ? "s" : ""})
              </p>
            )}
          </div>
          <button disabled={!selectedDate || !selectedSlot} onClick={handleReserve}>
            Reserve
          </button>
        </div>
      </div>
    </div>
  );
}
