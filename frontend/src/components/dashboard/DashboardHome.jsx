import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Makerspacecarousel from "../Makerspacecarousel";
import TrainingTasks from "./TrainingTasks";
import { useAuth } from "../../context/AuthContext";
import {
  useDashboard,
  firstName,
  todaySentence,
  reservationSentence,
  upcomingReservations,
  formatTime,
} from "../../hooks/useDashboard";

export default function DashboardHome() {
  const { authFetch } = useAuth();
  const { user, reservations, loading, error } = useDashboard();
  const navigate = useNavigate();

  const [equipment, setEquipment] = useState([]);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/equipment")
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        if (cancelled) return;
        setEquipment(data.filter((e) => e.category !== "CLASS").slice(0, 3));
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [authFetch]);

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
                    <div className="Rent4">
                      <p>{item.status === "AVAILABLE" ? "Available" : item.status}</p>
                    </div>
                    <p>{item.name}</p>
                  </div>
                  <div className="Rent5">
                    <p>$7/hr</p>
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
    </div>
  );
}