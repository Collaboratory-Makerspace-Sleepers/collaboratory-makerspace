import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { CATEGORY_LABELS } from "./RentEquipment";

const STATUS_BADGE = {
  AVAILABLE:   { label: "Available",   color: "green"  },
  IN_USE:      { label: "In Use",      color: "orange" },
  MAINTENANCE: { label: "Maintenance", color: "gray"   },
  RETIRED:     { label: "Retired",     color: "gray"   },
};

export default function EquipmentCategoryPage() {
  const { category } = useParams();
  const navigate = useNavigate();
  const { authFetch } = useAuth();

  const [units, setUnits] = useState([]);
  const [pageLoading, setPageLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch(`/api/v1/equipment/category/${category}`)
      .then((res) => {
        if (!res.ok) throw new Error("failed");
        return res.json();
      })
      .then((data) => {
        if (cancelled) return;
        setUnits(data);
        setPageLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setError("Unable to load equipment.");
        setPageLoading(false);
      });
    return () => { cancelled = true; };
  }, [authFetch, category]);

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
        {units.map((item) => {
          const badge = STATUS_BADGE[item.status] ?? { label: item.status, color: "gray" };
          const available = item.status === "AVAILABLE";
          const rateLabel = item.hourlyRateCents > 0
            ? `$${(item.hourlyRateCents / 100).toFixed(0)}/hr`
            : "Free";

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
