import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import equipment from "../../data/equipment";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const TIME_SLOTS = [
  "9:00 AM", "10:00 AM", "11:00 AM",
  "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM",
];

export default function ReserveEquipment() {
  const { id } = useParams();
  const navigate = useNavigate();
  const item = equipment.find((e) => String(e.id) === id);

  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());
  const [selectedDate, setSelectedDate] = useState(null);

  if (!item) {
    return (
      <div className="DashHome1">
        <p>Equipment not found.</p>
        <button onClick={() => navigate("/dashboard/rentequipment")}>
          Back
        </button>
      </div>
    );
  }

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

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button onClick={() => navigate("/dashboard/rentequipment")}>
          Back
        </button>
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
                className={
                  selectedDate &&
                  selectedDate.getFullYear() === viewYear &&
                  selectedDate.getMonth() === viewMonth &&
                  selectedDate.getDate() === day
                    ? "CalendarDay CalendarDaySelected"
                    : "CalendarDay"
                }
                onClick={() => setSelectedDate(new Date(viewYear, viewMonth, day))}
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
              <button key={time} className="TimeSlotButton">
                {time}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="ReserveSummary">
        <div className="ReserveSummaryTop">
          <div className="Rent4">
            <p>
              {item.available}/{item.total} Available
            </p>
          </div>
          <p>{item.price}</p>
        </div>
        <div className="ReserveSummaryBottom">
          <p>{item.name}</p>
          <button>Reserve</button>
        </div>
      </div>
    </div>
  );
}
