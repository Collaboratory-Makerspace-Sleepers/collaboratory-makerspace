import { Link } from "react-router-dom";
import Makerspacecarousel from "../Makerspacecarousel";
import {
  useDashboard,
  firstName,
  todaySentence,
  reservationSentence,
  upcomingReservations,
  formatTime,
} from "../../hooks/useDashboard";

export default function DashboardHome() {
  const { user, reservations, loading, error } = useDashboard();

  const upcoming = upcomingReservations(reservations);

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Hi {loading ? "..." : firstName(user) || "there"}</p>
        <p>
          Welcome back. {todaySentence()} {!loading && !error && reservationSentence(reservations)}
        </p>
      </div>

      <div className="Dash3">
        <p>Upcoming Reservations</p>
        {loading && <p>Loading…</p>}
        {error && <p>Unable to load reservations.</p>}
        {!loading && !error && upcoming.length === 0 && (
          <p>You don't have any upcoming reservations yet.</p>
        )}
        {!loading && !error &&
          upcoming.map((reservation) => (
            <div className="Dash4" key={reservation.id}>
              <p>{reservation.equipmentName}</p>
              <p className="Dash5-sub">{formatTime(reservation.startTime)}</p>
              <div className="Dash5">
                <Link to={`/dashboard/rentequipment/${reservation.equipmentId}`}>
                  <button>View</button>
                </Link>
              </div>
            </div>
          ))}
      </div>

      <div className="Dash6">
        <div className="Dash7">
          <p>Recommended</p>
          <div className="Dash8">
            <button>Equipment</button>
            <button>Classes</button>
          </div>
        </div>
        <div className="RentGrid">
          <div className="Rent1">
            <div>
              <img src="https://picsum.photos/seed/laser-a/480/360" />
            </div>

            <div className="Rent2">
              <div className="Rent3">
                <div className="Rent4">
                  <p>2/6 Available</p>
                </div>
                <p>LAserMachine</p>
              </div>

              <div className="Rent5">
                <p>$7/hr</p>
                <button>Reserve</button>
              </div>
            </div>
          </div>
          <div className="Rent1">
            <div>
              <img src="https://picsum.photos/seed/laser-a/480/360" />
            </div>

            <div className="Rent2">
              <div className="Rent3">
                <div className="Rent4">
                  <p>2/6 Available</p>
                </div>
                <p>LAserMachine</p>
              </div>

              <div className="Rent5">
                <p>$7/hr</p>
                <button>Reserve</button>
              </div>
            </div>
          </div>
          <div className="Rent1">
            <div>
              <img src="https://picsum.photos/seed/laser-a/480/360" />
            </div>

            <div className="Rent2">
              <div className="Rent3">
                <div className="Rent4">
                  <p>2/6 Available</p>
                </div>
                <p>LAserMachine</p>
              </div>

              <div className="Rent5">
                <p>$7/hr</p>
                <button>Reserve</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="Dash9">
        <div>
          <p>Recommended Tasks</p>
        </div>
        <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
        <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
        <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
      </div>
    </div>
  );
}