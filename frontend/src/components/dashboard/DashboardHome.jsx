import { useState } from "react";
import Makerspacecarousel from "../Makerspacecarousel";

const DAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTH_LABELS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

function Calendar() {
  const today = new Date();
  const [viewDate, setViewDate] = useState(
    new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDate, setSelectedDate] = useState(today);

  const year = viewDate.getFullYear();
  const month = viewDate.getMonth();

  const firstWeekday = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const goToPrevMonth = () => setViewDate(new Date(year, month - 1, 1));
  const goToNextMonth = () => setViewDate(new Date(year, month + 1, 1));

  const isSelected = (day) =>
    selectedDate &&
    selectedDate.getFullYear() === year &&
    selectedDate.getMonth() === month &&
    selectedDate.getDate() === day;

  const cells = [];
  for (let i = 0; i < firstWeekday; i++) {
    cells.push(
      <div key={`empty-${i}`} className="CalendarDay CalendarDayEmpty" />,
    );
  }
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push(
      <button
        key={day}
        type="button"
        className={`CalendarDay${isSelected(day) ? " CalendarDaySelected" : ""}`}
        onClick={() => setSelectedDate(new Date(year, month, day))}
      >
        {day}
      </button>,
    );
  }

  return (
    <div className="CalendarWrapper">
      <div className="CalendarHeader">
        <span className="CalendarMonthLabel">
          {MONTH_LABELS[month]} {year}
        </span>
        <div className="CalendarNav">
          <button
            type="button"
            className="CalendarNavBtn"
            onClick={goToPrevMonth}
            aria-label="Previous month"
          >
            &#8249;
          </button>
          <button
            type="button"
            className="CalendarNavBtn"
            onClick={goToNextMonth}
            aria-label="Next month"
          >
            &#8250;
          </button>
        </div>
      </div>

      <div className="CalendarGrid CalendarWeekdays">
        {DAY_LABELS.map((label) => (
          <div key={label} className="CalendarWeekday">
            {label}
          </div>
        ))}
      </div>

      <div className="CalendarGrid">{cells}</div>
    </div>
  );
}

export default function DashboardHome() {
  return (
    <div className="DashboardHome">
      <div className="DashHomeD">
        <p>Welcome back Kolbe</p>
        <p>Makerspace member</p>
      </div>

      <div className="DashHomeD1">
        <div className="DashHomeCalendarBox">
          <Calendar />
        </div>

        <div className="DashHomeGrid">
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
          <div className="DashHomeG1">
            <div className="DashHomeG2">
              <p>Upcoming Class</p>
              <p>Intro the laser cutting</p>
            </div>

            <div className="DashHomeG3">
              <button>Book</button>
            </div>
          </div>
        </div>
      </div>
      
      <div className="DashHomeD2">
        <div className="DashH1">
          <p>Most Popular Equipment</p>
          <button>See all Equipment</button>
        </div>

        <Makerspacecarousel/>
      </div>
    </div>
  );
}
