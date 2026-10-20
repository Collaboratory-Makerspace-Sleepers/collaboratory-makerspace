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
};

const RETRY_MS = 2500;
const MAX_ATTEMPTS = 8;

export default function BillingSuccess() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { authFetch } = useAuth();
  const type = params.get("type");
  const [pendingReservation] = useState(() =>
    ["membership", "daypass"].includes(type) ? readPendingReservation() : null
  );
  const [booking, setBooking] = useState(pendingReservation ? "booking" : "none");

  // Create the reservation as soon as payment lands. Membership activation is
  // webhook-driven, so a 402 is retried for a short while before giving up.
  useEffect(() => {
    if (!pendingReservation) return undefined;
    let cancelled = false;
    let timer;

    async function attempt(n) {
      try {
        const res = await authFetch("/api/v1/reservations", {
          method: "POST",
          body: JSON.stringify({
            equipmentId: pendingReservation.equipmentId,
            startTime: pendingReservation.startTime,
            endTime: pendingReservation.endTime,
          }),
        });
        if (cancelled) return;
        if (res.ok) {
          sessionStorage.removeItem(PENDING_RESERVATION_KEY);
          navigate("/dashboard/reservations", { replace: true });
          return;
        }
        if (res.status === 402 && n < MAX_ATTEMPTS) {
          timer = setTimeout(() => attempt(n + 1), RETRY_MS);
          return;
        }
      } catch {
        // fall through to the manual option
      }
      if (!cancelled) setBooking("failed");
    }

    attempt(1);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, navigate, pendingReservation]);

  const content = pendingReservation
    ? {
        title: "Payment received",
        subtitle: booking === "booking"
          ? `Booking ${pendingReservation.equipmentName ?? "your reservation"}… you'll be taken to My Reservations.`
          : "We couldn't finish booking automatically. Continue to confirm your reservation.",
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
          {booking !== "booking" && (
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