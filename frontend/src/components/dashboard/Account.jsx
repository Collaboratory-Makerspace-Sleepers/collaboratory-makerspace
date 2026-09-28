import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import {
  useDashboard,
  displayName,
  firstName,
  todaySentence,
  reservationSentence,
} from "../../hooks/useDashboard";

export default function Account() {
  const { authFetch } = useAuth();
  const { user, reservations, loading, error } = useDashboard();
  const [plan, setPlan] = useState(null);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/users/me/membership")
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (cancelled) return;
        setPlan(data);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <div className="AccountDiv">
      <div className="AccountD1">
        <p>Account Information</p>
        <p>
          Welcome back, {loading ? "..." : firstName(user) || "there"}. {todaySentence()}{" "}
          {!loading && !error && reservationSentence(reservations)}
        </p>
      </div>

      <div className="AccountD2">
        <div className="AccountD3">
          <p>Profile Information</p>

          <div className="AccountD4">
            <p>{(user?.firstName || user?.email || "?").slice(0, 1).toUpperCase()}</p>
            <p>{loading ? "…" : displayName(user) || "Your name"}</p>
            <p>Makerspace Member · {plan ? `${plan.planName} plan` : "…"}</p>
          </div>

          <div className="AccountD5">
            <button>Change Image</button>
            <p>We support PNG, JPEGs, and GIFs under 2MB</p>
          </div>

          {/* form input */}
          <div className="AccountFormRow">
            <div className="AccountFormField">
              <label>First Name</label>
              <input type="text" placeholder="First Name" defaultValue={user?.firstName || ""} />
            </div>
            <div className="AccountFormField">
              <label>Last Name</label>
              <input type="text" placeholder="Last Name" defaultValue={user?.lastName || ""} />
            </div>
          </div>

          <div className="AccountFormField">
            <label>Email</label>
            <input type="text" placeholder="you@example.com" defaultValue={user?.email || ""} />
          </div>

          <button>Update Information</button>
        </div>
      </div>
    </div>
  );
}