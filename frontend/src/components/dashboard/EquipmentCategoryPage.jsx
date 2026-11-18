import { useNavigate, useParams } from "react-router-dom";
import { CATEGORY_LABELS } from "./RentEquipment";
import { hourlyPrice, sortNewest } from "../../utils/catalog";
import { useCatalog } from "../../hooks/useCatalog";

const STATUS_BADGE = {
  AVAILABLE:   { label: "Available",   color: "green"  },
  IN_USE:      { label: "In Use",      color: "orange" },
  MAINTENANCE: { label: "Maintenance", color: "gray"   },
  RETIRED:     { label: "Retired",     color: "gray"   },
};

export default function EquipmentCategoryPage() {
  const { category } = useParams();
  const navigate = useNavigate();
  const { items: units, loading: pageLoading, error } = useCatalog(
    `/api/v1/equipment/category/${encodeURIComponent(category)}`);

  const title = CATEGORY_LABELS[category] ?? category;

  return (
    <div className="DashHome1">
      <div className="ReserveHeader">
        <button onClick={() => navigate("/dashboard/rentequipment")}>← Back</button>
        <p>{title}</p>
      </div>

      {pageLoading && <p>Loading…</p>}
      {error && <p>{error}</p>}

      <div className="RentGrid">
        {sortNewest(units).map((item) => {
          const badge = STATUS_BADGE[item.status] ?? { label: item.status, color: "gray" };
          const available = item.status === "AVAILABLE";
          const rateLabel = hourlyPrice(item);

          return (
            <div className="Rent1" key={item.id}>
              <div>
                <img
                  src={item.imageUrl || `https://picsum.photos/seed/${item.id}/480/360`}
                  alt={item.name}
                />
              </div>

              <div className="Rent2">
                <div className="Rent3">
                  <div className="Rent4" style={{ color: badge.color }}>
                    <p>{badge.label}</p>
                  </div>
                  <p>{item.name}</p>
                </div>

                <div className="Rent5">
                  <p>{rateLabel}</p>
                  <button
                    disabled={!available}
                    onClick={() =>
                      available && navigate(`/dashboard/rentequipment/${item.id}`)
                    }
                  >
                    {available ? "Reserve" : "Unavailable"}
                  </button>
                </div>
              </div>
            </div>
          );
        })}
        {!pageLoading && !error && units.length === 0 && <p>No units found.</p>}
      </div>
    </div>
  );
}
