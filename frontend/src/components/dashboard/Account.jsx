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
  const [firstNameVal, setFirstNameVal] = useState("");
  const [lastNameVal, setLastNameVal] = useState("");
  const [saveLoading, setSaveLoading] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState("");

  // Populate form once user loads
  useEffect(() => {
    if (user) {
      setFirstNameVal(user.firstName || "");
      setLastNameVal(user.lastName || "");
    }
  }, [user]);

  useEffect(() => {
    let cancelled = false;
    authFetch("/api/v1/billing/me/subscription")
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (cancelled) return;
        setPlan(data);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [authFetch]);

  async function handleUpdate(e) {
    e.preventDefault();
    setSaveError("");
    setSaveSuccess(false);
    setSaveLoading(true);
    try {
      const res = await authFetch("/api/v1/users/me", {
        method: "PATCH",
        body: JSON.stringify({ firstName: firstNameVal, lastName: lastNameVal }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || "Could not update profile");
      }
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(err.message || "Something went wrong.");
    } finally {
      setSaveLoading(false);
    }
  }

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
            <p>
              Makerspace Member ·{" "}
              {plan ? `${plan.planCode} plan` : "No active membership"}
            </p>
          </div>

          <div className="AccountD5">
            <button>Change Image</button>
            <p>We support PNG, JPEGs, and GIFs under 2MB</p>
          </div>

          <form onSubmit={handleUpdate}>
            <div className="AccountFormRow">
              <div className="AccountFormField">
                <label>First Name</label>
                <input
                  type="text"
                  placeholder="First Name"
                  value={firstNameVal}
                  onChange={(e) => setFirstNameVal(e.target.value)}
                  required
                />
              </div>
              <div className="AccountFormField">
                <label>Last Name</label>
                <input
                  type="text"
                  placeholder="Last Name"
                  value={lastNameVal}
                  onChange={(e) => setLastNameVal(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="AccountFormField">
              <label>Email</label>
              <input
                type="text"
                placeholder="you@example.com"
                value={user?.email || ""}
                disabled
              />
            </div>

            {saveSuccess && <p style={{ color: "green" }}>Profile updated.</p>}
            {saveError && <p style={{ color: "red" }}>{saveError}</p>}

            <button type="submit" disabled={saveLoading}>
              {saveLoading ? "Saving…" : "Update Information"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}