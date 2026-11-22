import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import AllEquipment from "./AllEquipment";
import ReservationsTable from "./ReservationsTable";
import DollarInput from "../DollarInput";
import { dollarsToCents } from "../../utils/catalog";
import { useCatalog } from "../../hooks/useCatalog";

const EMPTY = {
  name: "",
  description: "",
  category: "",
  imageUrl: "",
  requiresTraining: false,
  trainingVideoUrl: "",
  hourlyRateDollars: "0.00",
  trainingFeeCents: 0,
  requiresWaiver: true,
};

export default function AdminRentEquipment() {
  const { authFetch } = useAuth();
  const { items: equipment, error: catalogError } = useCatalog("/api/v1/equipment");
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
      const response = await authFetch("/api/v1/equipment", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          hourlyRateCents: dollarsToCents(form.hourlyRateDollars),
          trainingVideoUrl: form.trainingVideoUrl.trim() || null,
          name: form.name.trim(),
          category: form.category.trim() || null,
          imageUrl: form.imageUrl.trim() || null,
          description: form.description.trim() || null,
          status: "AVAILABLE",
        }),
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
    <div className="AdUser admin-classes-page">
      <title>Equipment | The Collaboratory</title>
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
              <input required list="equipment-types" placeholder="Machine type" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
              <datalist id="equipment-types">
                {[...new Set(equipment.map((item) => item.category).filter(Boolean))].sort().map((type) =>
                  <option key={type} value={type} />)}
              </datalist>
              {catalogError && <span role="alert">{catalogError} You can still enter a machine type.</span>}
            </label>
            <label className="nu-field">
              Description
              <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </label>
            <label className="nu-field">
              Image URL (optional)
              <input value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
            </label>
            <label className="nu-check">
              <input type="checkbox" checked={form.requiresWaiver} onChange={(e) => setForm({ ...form, requiresWaiver: e.target.checked })} />
              Requires a signed waiver
            </label>
            <label className="nu-check">
              <input type="checkbox" checked={form.requiresTraining} onChange={(e) => setForm({ ...form, requiresTraining: e.target.checked })} />
              Requires training
            </label>
            <label className="nu-field">
              Training video URL {form.requiresTraining ? "(required)" : "(optional)"}
              <input type="url" required={form.requiresTraining} value={form.trainingVideoUrl}
                onChange={(e) => setForm({ ...form, trainingVideoUrl: e.target.value })} placeholder="https://..." />
            </label>
            {form.requiresTraining && <label className="nu-field">
              Training fee (cents)
              <input type="number" min="0" value={form.trainingFeeCents}
                onChange={(e) => setForm({ ...form, trainingFeeCents: Number(e.target.value) })} />
            </label>}
            <label className="nu-field">
              Hourly rate ($)
              <DollarInput value={form.hourlyRateDollars}
                onChange={(value) => setForm({ ...form, hourlyRateDollars: value })} />
              <span>One rate for paying users. Existing free-access exemptions still apply.</span>
            </label>
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
