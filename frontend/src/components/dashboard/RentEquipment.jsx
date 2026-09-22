import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  useDashboard,
  todaySentence,
  reservationSentence,
  upcomingReservations,
  formatTime,
} from "../../hooks/useDashboard";

export default function RentEquipment() {
  const navigate = useNavigate();
  const { authFetch } = useAuth();
  const { reservations, loading, error } = useDashboard();

  const [items, setItems] = useState([]);
  const [search, setSearch] = useState("");
  const [dataError, setDataError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/equipment")
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        if (cancelled) return;
        setItems(data.filter((e) => e.category !== "CLASS"));
      })
      .catch(() => {
        if (cancelled) return;
        setDataError("Unable to load equipment.");
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const upcoming = upcomingReservations(reservations);
  const visible = items.filter((item) =>
    item.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Rent Equipment</p>
        <p>
          Welcome back. {todaySentence()}{" "}
          {!loading && !error && reservationSentence(reservations)}
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
            </div>
          ))}
      </div>

      <div className="RentEquipmentSection">
        {/* Search field */}
        <input
          type="text"
          placeholder="Search"
          className="SearchInput"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />

        {dataError && <p>{dataError}</p>}

        <div className="RentGrid">
          {visible.map((item) => (
            <div className="Rent1" key={item.id}>
              <div>
                <img src={item.imageUrl || "https://picsum.photos/seed/machine/480/360"} alt={item.name} />
              </div>

              <div className="Rent2">
                <div className="Rent3">
                  <div className="Rent4">
                    <p>Available</p>
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
          {visible.length === 0 && <p>No equipment matches your search.</p>}
        </div>
      </div>
    </div>
  );
}