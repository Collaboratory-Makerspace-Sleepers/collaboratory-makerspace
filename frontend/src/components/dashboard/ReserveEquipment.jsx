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

  const [booked, setBooked] = useState([]);

  // Load what other members already booked on the chosen day so those slots are blocked.
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

  const [monthBooked, setMonthBooked] = useState([]);

  // Booked ranges for the visible month, used to colour days on the calendar.
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

  function slotBooked(time) {
    if (!selectedDate) return false;
    const start = new Date(
      selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), SLOT_HOURS[time]
    );
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    return booked.some((b) => new Date(b.startTime) < end && new Date(b.endTime) > start);
  }

  // "full" when every slot that day is taken, "partial" when at least one is.
  function dayBookingState(day) {
    const taken = TIME_SLOTS.filter((time) => {
      const start = new Date(viewYear, viewMonth, day, SLOT_HOURS[time]);
      const end = new Date(start.getTime() + 60 * 60 * 1000);
      return monthBooked.some((b) => new Date(b.startTime) < end && new Date(b.endTime) > start);
    }).length;
    if (taken === 0) return null;
    return taken >= TIME_SLOTS.length ? "full" : "partial";
  }

  function slotDisabled(time) {
    return (isTodaySelected() && SLOT_HOURS[time] <= today.getHours()) || slotBooked(time);
  }

  function handleReserve() {
    if (!selectedDate || !selectedSlot) return;
    const hour = SLOT_HOURS[selectedSlot];
    const start = new Date(
      selectedDate.getFullYear(),
      selectedDate.getMonth(),
      selectedDate.getDate(),
      hour, 0, 0
    );
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    const waiverRequired = true;
    const reservationState = {
      equipmentId: Number(id),
      equipmentName: item.name,
      waiverRequired,
      trainingRequired: item.trainingRequired,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      displayDate: selectedDate.toLocaleDateString(undefined, {
        weekday: "long", month: "long", day: "numeric", year: "numeric",
      }),
      displayTime: selectedSlot,
    };
    navigate(
      `/dashboard/rentequipment/${id}/${waiverRequired ? "waiver" : "checkout"}`,
      {
        state: reservationState,
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
                disabled={isPastDay(day) || dayBookingState(day) === "full"}
                className={
                  isPastDay(day)
                    ? "CalendarDay CalendarDayDisabled"
                    : selectedDate &&
                      selectedDate.getFullYear() === viewYear &&
                      selectedDate.getMonth() === viewMonth &&
                      selectedDate.getDate() === day
                    ? "CalendarDay CalendarDaySelected"
                    : `CalendarDay${dayBookingState(day) ? ` CalendarDay-${dayBookingState(day)}` : ""}`
                }
                title={
                  dayBookingState(day) === "full"
                    ? "Fully booked"
                    : dayBookingState(day) === "partial"
                    ? "Some times are booked"
                    : undefined
                }
                onClick={() => {
                  setSelectedDate(new Date(viewYear, viewMonth, day));
                  setSelectedSlot(null);
                }}
              >
                {day}
              </button>
            )
          )}
        </div>
        <div className="CalendarLegend">
          <span className="legend-partial">Some times booked</span>
          <span className="legend-full">Fully booked</span>
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
                }}
              >
                {time}
                {slotBooked(time) && <span className="SlotBookedLabel"> · Booked</span>}
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
            disabled={!selectedDate || !selectedSlot}
            onClick={handleReserve}
          >
            Reserve
          </button>
        </div>
      </div>
    </div>
  );
}