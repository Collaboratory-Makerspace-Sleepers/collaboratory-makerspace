import { Link, useNavigate } from "react-router-dom";
import TrainingTasks from "./TrainingTasks";
import WaiverStatusPanel from "./WaiverStatusPanel";
import { BookingBadge, BookingDetail } from "./BookingStatus";
import { useCatalog } from "../../hooks/useCatalog";
import { formatDollars, hourlyPrice, sortNewest } from "../../utils/catalog";
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
  const navigate = useNavigate();

  const { items, error: equipmentError } = useCatalog("/api/v1/equipment");
  const { items: classes, error: classError } = useCatalog("/api/v1/class-sessions");
  const equipment = sortNewest(items.filter((item) => item.category !== "CLASS"
    && item.status === "AVAILABLE")).slice(0, 3);

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
              <div className="Dash5 checkout-actions">
                <Link to={`/dashboard/rentequipment/${reservation.equipmentId}`}>
                  <button>View</button>
                </Link>
              </div>
            </div>
          ))}
      </div>

      <WaiverStatusPanel />

        <TrainingTasks />

      {equipment.length > 0 && (
        <div className="Dash6">
          <div className="Dash7">
            <p>Recommended Equipment</p>
          </div>
          <div className="RentGrid">
            {equipment.map((item) => (
              <div className="Rent1" key={item.id}>
                <div>
                  <img
                    src={item.imageUrl || `https://picsum.photos/seed/${item.id}/480/360`}
                    alt={item.name}
                  />
                </div>
                <div className="Rent2">
                  <div className="Rent3">
                    <BookingBadge
                      reservations={reservations}
                      equipmentId={item.id}
                      fallback={item.status === "AVAILABLE" ? "Available" : item.status}
                    />
                    <p>{item.name}</p>
                    <BookingDetail reservations={reservations} equipmentId={item.id} />
                  </div>
                  <div className="Rent5">
                    <p>{hourlyPrice(item)}</p>
                    <button onClick={() => navigate(`/dashboard/rentequipment/${item.id}`)}>
                      Reserve
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
      {equipmentError && <p role="alert">{equipmentError}</p>}
      {classError && <p role="alert">{classError}</p>}
      {classes.length > 0 && (
        <section className="Dash6">
          <div className="Dash7"><p>Latest Classes</p></div>
          <div className="RentGrid">
            {sortNewest(classes).slice(0, 3).map((session) => (
              <div className="Rent1" key={session.id}>
                <div><img src={session.imageUrl || "https://picsum.photos/seed/class/480/360"} alt={session.title} /></div>
                <div className="Rent2">
                  <p>{session.title}</p>
                  <p>{new Date(session.startTime).toLocaleString()}</p>
                  <div className="Rent5">
                    <p>{session.resolvedPriceCents === 0 ? "Free" : formatDollars(session.resolvedPriceCents)}</p>
                    <button onClick={() => navigate(`/dashboard/bookclasses/session/${session.id}`)}>View class</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}