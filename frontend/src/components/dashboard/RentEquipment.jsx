import { useState } from "react";
import { useNavigate } from "react-router-dom";
import WaiverStatusPanel from "./WaiverStatusPanel";
import { BookingBadge, BookingDetail } from "./BookingStatus";
import useInUseNow from "../../hooks/useInUseNow";
import { useCatalog } from "../../hooks/useCatalog";
import { hourlyPrice, sortNewest } from "../../utils/catalog";
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
  const { reservations, loading, error } = useDashboard();
  const inUseNow = useInUseNow();

  const { items, loading: catalogLoading, error: dataError } = useCatalog("/api/v1/equipment");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");

  const upcoming = upcomingReservations(reservations);
  // Only list what can be booked: not in maintenance/retired and not in use right now.
  const bookable = items.filter(
    (item) => item.category !== "CLASS" && (!item.status || item.status === "AVAILABLE") && !inUseNow.includes(item.id)
  );
  const visible = sortNewest(bookable).filter((item) =>
    `${item.name} ${item.category || ""} ${CATEGORY_LABELS[item.category] || ""}`
      .toLowerCase().includes(search.trim().toLowerCase())
      && (!category || item.category === category)
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

      <WaiverStatusPanel />

      <div className="RentEquipmentSection">
        {/* Search field */}
        <input
          type="search"
          placeholder="Search equipment or machine type"
          aria-label="Search equipment or machine type"
          className="SearchInput"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />

        <label className="catalog-filters">Machine type
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All machine types</option>
            {[...new Set(items.filter((item) => item.category !== "CLASS").map((item) => item.category).filter(Boolean))]
              .sort().map((type) => <option key={type} value={type}>{CATEGORY_LABELS[type] || type}</option>)}
          </select>
        </label>
        {dataError && <p role="alert">{dataError} Displayed equipment may be outdated.</p>}
        {catalogLoading && <p role="status">Loading equipment...</p>}

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
                  {item.category && <p>{CATEGORY_LABELS[item.category] || item.category}</p>}
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
          {!catalogLoading && !dataError && visible.length === 0 && <p>No equipment matches your search.</p>}
        </div>
      </div>
    </div>
  );
}