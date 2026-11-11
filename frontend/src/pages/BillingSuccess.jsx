import { useEffect, useRef, useState } from "react";
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

function ClassEnrollmentSuccess({ enrollmentId, stripeSessionId }) {
  const { authFetch } = useAuth();
  const [status, setStatus] = useState(enrollmentId ? "confirming" : "error");

  useEffect(() => {
    if (!enrollmentId) return;
    authFetch(`/api/v1/class-sessions/enrollment/${enrollmentId}/confirm`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ stripeSessionId: stripeSessionId ?? "" }),
    })
      .then(async (res) => {
        if (!res.ok) { setStatus("error"); return; }
        setStatus("confirmed");
      })
      .catch(() => setStatus("error"));
  }, [authFetch, enrollmentId, stripeSessionId]);

  if (status === "confirming") {
    return (
      <div className="bigdiv2">
        <div className="SignIn">
          <div className="SignInText">
            <p className="signin-title">Confirming your enrollment…</p>
            <p className="signin-subtitle">Please wait a moment.</p>
          </div>
        </div>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="bigdiv2">
        <div className="SignIn">
          <div className="SignInText">
            <p className="signin-title">Enrollment confirmation failed</p>
            <p className="signin-subtitle">
              Your payment was received but we could not confirm your enrollment automatically.
              Please contact support with enrollment #{enrollmentId}.
            </p>
          </div>
          <div className="sign-in">
            <Link to="/dashboard/bookclasses">
              <button className="signButton">Back to Classes</button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bigdiv2">
      <div className="SignIn">
        <div className="SignInText">
          <p className="signin-title">You&apos;re enrolled!</p>
          <p className="signin-subtitle">
            Your enrollment is confirmed. See you in class!
          </p>
        </div>
        <div className="sign-in">
          <Link to="/dashboard/bookclasses">
            <button className="signButton">View Classes</button>
          </Link>
        </div>
      </div>
    </div>
  );
}

function CompletePaidBooking({ reservation, type, stripeSessionId }) {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const operation = useRef(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function complete() {
      if (type === "daypass") {
        const payment = await authFetch("/api/v1/billing/me/payments/sync", {
          method: "POST",
          signal: AbortSignal.timeout(30000),
        });
        if (!payment.ok) {
          const body = await payment.json().catch(() => ({}));
          throw new Error(body.message || "Could not verify your day pass. Do not pay again; retry confirmation.");
        }
      }
      if (type === "membership") {
        const membership = await authFetch("/api/v1/billing/checkout-session/confirm-membership", {
          method: "POST",
          body: JSON.stringify({ sessionId: stripeSessionId }),
          signal: AbortSignal.timeout(30000),
        });
        if (!membership.ok) {
          const body = await membership.json().catch(() => ({}));
          throw new Error(body.message || "Membership activation could not be verified. Do not pay again.");
        }
      }
      const response = await authFetch("/api/v1/reservations", {
        method: "POST",
        signal: AbortSignal.timeout(30000),
        body: JSON.stringify({
          equipmentId: reservation.equipmentId,
          startTime: reservation.startTime,
          endTime: reservation.endTime,
          waiverSignatureId: reservation.waiverSignatureId,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.detail || `Reservation could not be confirmed (HTTP ${response.status}). Your payment does not need to be repeated.`);
      }
      sessionStorage.removeItem(PENDING_RESERVATION_KEY);
      if (reservation.waiverSignatureStorageKey) {
        sessionStorage.removeItem(reservation.waiverSignatureStorageKey);
      }
    }
    if (!operation.current) operation.current = complete();
    operation.current.then(() => {
      if (!cancelled) navigate("/dashboard/reservations", { replace: true });
    }).catch((err) => {
      if (!cancelled) setError(err.name === "TimeoutError"
        ? "Confirmation timed out. Retry confirmation without paying again."
        : err.message);
    });
    return () => { cancelled = true; };
  }, [authFetch, navigate, reservation, type, stripeSessionId, attempt]);

  return (
    <div className="DashHome1">
      <h1>{error ? "Reservation needs attention" : "Finalizing your reservation..."}</h1>
      {error ? (
        <>
          <p role="alert">{error}</p>
          <button onClick={() => {
            operation.current = null;
            setError("");
            setAttempt((value) => value + 1);
          }}>Retry confirmation</button>
          <Link to="/dashboard/billing-history">View payment and receipt</Link>
          <Link to={`/dashboard/rentequipment/${reservation.equipmentId}/waiver`} state={reservation}>
            Review waiver
          </Link>
        </>
      ) : <p role="status">Verifying payment and booking {reservation.equipmentName}. Please wait.</p>}
    </div>
  );
}

export default function BillingSuccess() {
  const [params] = useSearchParams();
  const type = params.get("type");
  const enrollmentId = params.get("enrollmentId");
  const stripeSessionId = params.get("session_id");
  const [pendingReservation] = useState(() =>
    ["membership", "daypass"].includes(type) ? readPendingReservation() : null
  );

  if (type === "class") {
    return (
      <ClassEnrollmentSuccess
        enrollmentId={enrollmentId ? parseInt(enrollmentId, 10) : null}
        stripeSessionId={stripeSessionId}
      />
    );
  }

  if (pendingReservation) {
    return <CompletePaidBooking reservation={pendingReservation} type={type} stripeSessionId={stripeSessionId} />;
  }
  const content = CONTENT[type] ?? {
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
          >
            <button className="signButton">{content.label}</button>
          </Link>
        </div>
      </div>
    </div>
  );
}