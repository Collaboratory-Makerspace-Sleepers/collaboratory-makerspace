import { useState } from "react";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export default function AdminCalendar() {
  const [viewMonth, setViewMonth] = useState(() => {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  });

  const today = new Date();
  const firstWeekday = new Date(
    viewMonth.getFullYear(),
    viewMonth.getMonth(),
    1
  ).getDay();
  const daysInMonth = new Date(
    viewMonth.getFullYear(),
    viewMonth.getMonth() + 1,
    0
  ).getDate();
  const calendarCells = [
    ...Array(firstWeekday).fill(null),
    ...Array.from({ length: daysInMonth }, (_, index) => index + 1),
  ];

  function changeMonth(offset) {
    setViewMonth((current) =>
      new Date(current.getFullYear(), current.getMonth() + offset, 1)
    );
  }

  return (
    <div className="AdminCalendar" aria-label="Calendar">
      <div className="AdminCalendarHeader">
        <p>
          {viewMonth.toLocaleDateString(undefined, {
            month: "long",
            year: "numeric",
          })}
        </p>
        <div className="AdminCalendarControls">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => changeMonth(-1)}
          >
            ‹
          </button>
          <button
            type="button"
            aria-label="Next month"
            onClick={() => changeMonth(1)}
          >
            ›
          </button>
        </div>
      </div>

      <div className="AdminCalendarWeekdays" aria-hidden="true">
        {WEEKDAYS.map((day) => <span key={day}>{day}</span>)}
      </div>

      <div className="AdminCalendarDays">
        {calendarCells.map((day, index) => {
          const isToday = day !== null
            && viewMonth.getFullYear() === today.getFullYear()
            && viewMonth.getMonth() === today.getMonth()
            && day === today.getDate();

          return day === null ? (
            <span className="AdminCalendarEmpty" key={`empty-${index}`} />
          ) : (
            <span
              aria-current={isToday ? "date" : undefined}
              className={isToday ? "AdminCalendarDay AdminCalendarToday" : "AdminCalendarDay"}
              key={day}
            >
              {day}
            </span>
          );
        })}
      </div>
    </div>
  );
}