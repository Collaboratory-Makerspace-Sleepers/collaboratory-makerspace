import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const POLL_MS = 5000;

const LABELS = {
  NEEDS_WAIVER: { text: "Waiver needs signing", cls: "pending" },
  WAIVER_SIGNED: { text: "Pending staff approval", cls: "pending" },
  WAIVER_APPROVED: { text: "Approved — tap to continue", cls: "approved" },
  DENIED: { text: "Denied", cls: "denied" },
};

export default function WaiverStatusPanel() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);

  const load = useCallback(async () => {
    try {
      const res = await authFetch("/api/v1/hardware/checkins/me/waivers");
      if (res.ok) setItems(await res.json());
    } catch {
      // the next poll retries
    }
  }, [authFetch]);

  useEffect(() => {
    const first = setTimeout(load, 0);
    const timer = setInterval(() => {
      if (!document.hidden) load();
    }, POLL_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [load]);

  if (items.length === 0) return null;

  function open(item) {
    if (item.requestedStart && item.requestedEnd) {
      const start = new Date(item.requestedStart);
      navigate(`/dashboard/rentequipment/${item.equipmentId}/waiver`, {
        state: {
          equipmentId: item.equipmentId,
          equipmentName: item.equipmentName,
          waiverRequired: true,
          startTime: item.requestedStart,
          endTime: item.requestedEnd,
          displayDate: start.toLocaleDateString(undefined, {
            weekday: "long", month: "long", day: "numeric", year: "numeric",
          }),
          displayTime: start.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" }),
        },
      });
    } else {
      navigate(`/dashboard/rentequipment/${item.equipmentId}`);
    }
  }

  function slotText(item) {
    if (!item.requestedStart) return null;
    const start = new Date(item.requestedStart);
    const day = start.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
    const time = (d) => new Date(d).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
    return `${day}, ${time(item.requestedStart)}${item.requestedEnd ? ` – ${time(item.requestedEnd)}` : ""}`;
  }

  return (
    <div className="Dash3 WaiverStatusPanel">
      <p>Waiver Status</p>
      <div className="WaiverStatusGrid">
      {items.map((item) => {
        const label = LABELS[item.status] ?? { text: item.status, cls: "pending" };
        return (
          <button
            className={`Dash4 WaiverStatusItem ${label.cls}`}
            key={item.id}
            onClick={() => open(item)}
            type="button"
          >
            <span>{item.equipmentName}</span>
            {slotText(item) && <span className="WaiverSlot">{slotText(item)}</span>}
            <span className="Dash5-sub">{label.text}</span>
          </button>
        );
      })}
      </div>
    </div>
  );
}
