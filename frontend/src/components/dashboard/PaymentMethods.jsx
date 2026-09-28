import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";

export default function PaymentMethods() {
  const { authFetch } = useAuth();
  const [methods, setMethods] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(null);

  const loadMethods = useCallback(() => {
    setLoading(true);
    authFetch("/api/v1/billing/payment-methods")
      .then((res) => {
        if (!res.ok) throw new Error();
        return res.json();
      })
      .then((data) => { setMethods(data); setLoading(false); })
      .catch(() => { setError("Could not load payment methods."); setLoading(false); });
  }, [authFetch]);

  useEffect(() => { loadMethods(); }, [loadMethods]);

  async function handleAddCard() {
    setActionError("");
    setBusy("setup");
    try {
      const res = await authFetch("/api/v1/billing/payment-methods/setup", { method: "POST" });
      if (!res.ok) throw new Error("Could not start card setup");
      const { sessionUrl } = await res.json();
      window.location.href = sessionUrl;
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
      setBusy(null);
    }
  }

  async function handleDelete(pmId) {
    setActionError("");
    setBusy(pmId + "-del");
    try {
      const res = await authFetch(`/api/v1/billing/payment-methods/${pmId}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Could not remove card");
      loadMethods();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  async function handleSetDefault(pmId) {
    setActionError("");
    setBusy(pmId + "-default");
    try {
      const res = await authFetch(`/api/v1/billing/payment-methods/${pmId}/set-default`, { method: "PATCH" });
      if (!res.ok) throw new Error("Could not set default");
      loadMethods();
    } catch (err) {
      setActionError(err.message || "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Payment Methods</p>
        <p>Manage your saved cards.</p>
      </div>

      {error && <p>{error}</p>}
      {actionError && <p style={{ color: "red" }}>{actionError}</p>}

      <div className="Dash3">
        {loading && <p>Loading…</p>}
        {!loading && methods.length === 0 && <p>No saved cards.</p>}
        {methods.map((pm) => (
          <div className="Dash4" key={pm.id}>
            <p>
              {pm.brand.toUpperCase()} •••• {pm.last4} — expires {pm.expMonth}/{pm.expYear}
              {pm.isDefault && " (Default)"}
            </p>
            <div className="Dash5">
              {!pm.isDefault && (
                <button
                  onClick={() => handleSetDefault(pm.id)}
                  disabled={busy === pm.id + "-default"}
                >
                  {busy === pm.id + "-default" ? "Setting…" : "Set as default"}
                </button>
              )}
              <button
                onClick={() => handleDelete(pm.id)}
                disabled={busy === pm.id + "-del"}
              >
                {busy === pm.id + "-del" ? "Removing…" : "Remove"}
              </button>
            </div>
          </div>
        ))}
      </div>

      <button onClick={handleAddCard} disabled={busy === "setup"}>
        {busy === "setup" ? "Opening…" : "Add a card"}
      </button>
    </div>
  );
}