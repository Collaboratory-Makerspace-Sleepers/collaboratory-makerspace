import { Link, useSearchParams } from "react-router-dom";

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

export default function BillingSuccess() {
  const [params] = useSearchParams();
  const type = params.get("type");
  const pendingReservation = ["membership", "daypass"].includes(type)
    ? readPendingReservation()
    : null;
  const content = pendingReservation
    ? {
        title: "Payment received",
        subtitle: "Continue to confirm your reservation. Required training will appear in your dashboard after booking.",
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
          <Link
            to={content.link}
            onClick={() => {
              if (pendingReservation) sessionStorage.removeItem(PENDING_RESERVATION_KEY);
            }}
          >
            <button className="signButton">{content.label}</button>
          </Link>
        </div>
      </div>
    </div>
  );
}