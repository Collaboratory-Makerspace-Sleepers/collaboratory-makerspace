import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import AllEquipment from "./AllEquipment";
import ReservationsTable from "./ReservationsTable";

const EMPTY = {
  name: "",
  description: "",
  category: "",
  imageUrl: "",
  trainingRequired: false,
  trainingVideoUrl: "",
  requiresWaiver: true,
  hourlyRateDollars: "",
  trainingFeeCents: 0,
};

export default function AdminRentEquipment() {
  const { authFetch } = useAuth();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  const close = () => {
    setOpen(false);
    setForm(EMPTY);
    setMessage("");
  };

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const rateCents = Math.round(parseFloat(form.hourlyRateDollars || "0") * 100);
      const payload = {
        ...form,
        name: form.name.trim(),
        category: form.category.trim() || null,
        imageUrl: form.imageUrl.trim() || null,
        description: form.description.trim() || null,
        status: "AVAILABLE",
        hourlyRateCents: rateCents,
        memberRateCents: rateCents,
      };
      delete payload.hourlyRateDollars;
      const response = await authFetch("/api/v1/equipment", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || "Unable to create the equipment.");
      }
      setVersion((v) => v + 1);
      close();
    } catch (err) {
      setMessage(err.message || "Unable to create the equipment.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="AdUser">
      <h1 className="admin-page-title">Equipment</h1>
      <div className="admin-actions-row">
        <button onClick={() => setOpen(true)}>Create New Equipment</button>
      </div>

      <div className="AdAct">
        <AllEquipment refreshKey={version} />
      </div>

      <div className="AdAct">
        <ReservationsTable isClass={false} title="Equipment Reservations" />
      </div>

      {open && (
        <div className="ra-profile-backdrop" onClick={close}>
          <form
            className="ra-profile-dialog nu-dialog"
            onClick={(event) => event.stopPropagation()}
            onSubmit={submit}
          >
            <header className="nu-header">
              <div>
                <h3>Create new equipment</h3>
                <p>It appears in Rent Equipment for members as soon as it is saved.</p>
              </div>
              <button type="button" className="nu-close" aria-label="Close" onClick={close}>×</button>
            </header>
            <label className="nu-field">
              Name
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label className="nu-field">
              Category
              <input placeholder="e.g. Laser, Woodworking" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
            </label>
            <label className="nu-field">
              Description
              <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </label>
            <label className="nu-field">
              Image URL (optional)
              <input value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
            </label>
            <label className="nu-field">
              Hourly rate ($/hr)
              <input
                type="text"
                inputMode="decimal"
                placeholder="0.00"
                value={form.hourlyRateDollars}
                onChange={(e) => setForm({ ...form, hourlyRateDollars: e.target.value })}
                onBlur={() => {
                  const cents = Math.round(parseFloat(form.hourlyRateDollars || "0") * 100);
                  setForm({ ...form, hourlyRateDollars: (cents / 100).toFixed(2) });
                }}
              />
            </label>
            {form.trainingRequired && (
              <label className="nu-field">
                Training fee (cents)
                <input type="number" min="0" value={form.trainingFeeCents}
                  onChange={(e) => setForm({ ...form, trainingFeeCents: Number(e.target.value) })} />
              </label>
            )}
            <label className="nu-check">
              <input type="checkbox" checked={form.requiresWaiver} onChange={(e) => setForm({ ...form, requiresWaiver: e.target.checked })} />
              Requires a signed waiver
            </label>
            <label className="nu-check">
              <input type="checkbox" checked={form.trainingRequired} onChange={(e) => setForm({ ...form, trainingRequired: e.target.checked })} />
              Requires training
            </label>
            {form.trainingRequired && (
              <label className="nu-field">
                Training video URL
                <input
                  required
                  type="url"
                  placeholder="https://youtube.com/..."
                  value={form.trainingVideoUrl}
                  onChange={(e) => setForm({ ...form, trainingVideoUrl: e.target.value })}
                />
              </label>
            )}
            {message && <p className="nu-message">{message}</p>}
            <div className="nu-actions">
              <button type="button" className="nu-cancel" onClick={close}>Cancel</button>
              <button type="submit" disabled={busy}>{busy ? "Creating…" : "Create equipment"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
