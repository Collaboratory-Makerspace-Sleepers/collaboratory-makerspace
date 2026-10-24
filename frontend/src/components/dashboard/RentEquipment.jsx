import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import WaiverStatusPanel from "./WaiverStatusPanel";
import { BookingBadge, BookingDetail } from "./BookingStatus";
import useInUseNow from "../../hooks/useInUseNow";
import { useAuth } from "../../context/AuthContext";
import {
  useDashboard,
  todaySentence,
  reservationSentence,
  upcomingReservations,
  formatTime,
} from "../../hooks/useDashboard";

export const CATEGORY_LABELS = {
  LASER:         "Laser Cutters",
  "3D_PRINTER":  "3D Printers",
  CNC:           "CNC Routers",
  CNC_LARGE:     "CNC Routers (Large)",
  CNC_SMALL:     "CNC Routers (Small)",
  ELECTRONICS:   "Electronics",
  WOODSHOP:      "Woodshop",
  WOOD_LATHE:    "Wood Lathes",
  METAL_LATHE:   "Metal Lathes",
  SEWING:        "Sewing",
  STUDIO_SMALL:  "Small Studios",
  STUDIO_MEDIUM: "Medium Studios",
  STUDIO_LARGE:  "Large Studios",
};

export default function RentEquipment() {
  const navigate = useNavigate();
  const { authFetch } = useAuth();
  const { reservations, loading, error } = useDashboard();
  const inUseNow = useInUseNow();

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
  // Only list what can be booked: not in maintenance/retired and not in use right now.
  const bookable = items.filter(
    (item) => (!item.status || item.status === "AVAILABLE") && !inUseNow.includes(item.id)
  );
  const visible = bookable.filter((item) =>
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

      <WaiverStatusPanel />

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
                  <BookingBadge reservations={reservations} equipmentId={item.id} fallback="Available" />
                  <p>{item.name}</p>
                  <BookingDetail reservations={reservations} equipmentId={item.id} />
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