import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const PENDING_RESERVATION_KEY = "pendingReservationAfterPayment";
const PENDING_RESERVATION_TTL_MS = 2 * 60 * 60 * 1000;

function readPendingReservation() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(PENDING_RESERVATION_KEY));
    if (Date.now() - saved.savedAt > PENDING_RESERVATION_TTL_MS) return null;
    if (!saved.reservation?.equipmentId || !saved.reservation?.startTime) return null;
    return saved.reservation;
  } catch {
    return null;
  }
}

const CONTENT = {
  membership: {
    title: "Membership activated",
    subtitle:
      "Your membership is being activated. It may take a moment to reflect — check your membership page in a few seconds.",
    link: "/dashboard/membership",
    label: "View membership",
  },
  daypass: {
    title: "Day pass purchased",
    subtitle: "Your day pass is confirmed. Enjoy your visit!",
    link: "/dashboard/home",
    label: "Go to dashboard",
  },
  setup: {
    title: "Card saved",
    subtitle: "Your card has been saved and set as your default payment method.",
    link: "/dashboard/payment-methods",
    label: "View payment methods",
  },
  equipment: {
    title: "Payment received",
    subtitle: "Creating your reservation...",
    link: "/dashboard/reservations",
    label: "View my reservations",
  },
};

export default function BillingSuccess() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { authFetch } = useAuth();
  const type = params.get("type");
  const equipmentId = params.get("equipmentId");
  const startTime = params.get("startTime");
  const endTime = params.get("endTime");
  
  const [pendingReservation] = useState(() =>
    ["membership", "daypass"].includes(type) ? readPendingReservation() : null
  );

  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  // For equipment payments, create the reservation
  useEffect(() => {
    if (type === "equipment" && equipmentId && startTime && endTime && !creating) {
      setCreating(true);
      const createReservation = async () => {
        try {
          const res = await authFetch("/api/v1/reservations", {
            method: "POST",
            body: JSON.stringify({
              equipmentId: parseInt(equipmentId),
              startTime: decodeURIComponent(startTime),
              endTime: decodeURIComponent(endTime),
            }),
          });

          if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || "Failed to create reservation");
          }

          // Reservation created successfully, navigate to reservations page
          setTimeout(() => navigate("/dashboard/reservations", { replace: true }), 1500);
        } catch (err) {
          setError(err.message || "Something went wrong");
          setCreating(false);
        }
      };

      createReservation();
    }

    // Go straight to My Reservations; it finishes the booking once the membership is active.
    if (pendingReservation && !creating) {
      navigate("/dashboard/reservations", { replace: true });
    }
  }, [navigate, authFetch, pendingReservation, type, equipmentId, startTime, endTime, creating]);

  const content = 
    error ? {
      title: "Error creating reservation",
      subtitle: error,
      link: "/dashboard/rentequipment",
      label: "Try again",
    } : creating ? {
      title: "Payment received",
      subtitle: "Creating your reservation...",
      link: "/dashboard/reservations",
      label: "View my reservations",
    } : pendingReservation
    ? {
        title: "Payment received",
        subtitle: "Taking you to My Reservations…",
        link: `/dashboard/rentequipment/${pendingReservation.equipmentId}/checkout`,
        label: "Continue reservation",
      }
    : CONTENT[type] ?? {
    title: "Payment received",
    subtitle: "Your payment was successful.",
    link: "/dashboard/home",
    label: "Go to dashboard",
  };

  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p className="signin-title">{content.title}</p>
          <p className="signin-subtitle">{content.subtitle}</p>
        </div>
        <div className="sign-in">
          {!pendingReservation && !creating && (
          <Link
            to={content.link}
            onClick={() => {
              if (pendingReservation) sessionStorage.removeItem(PENDING_RESERVATION_KEY);
            }}
          >
            <button className="signButton">{content.label}</button>
          </Link>
          )}
        </div>
      </div>
    </div>
  );
}