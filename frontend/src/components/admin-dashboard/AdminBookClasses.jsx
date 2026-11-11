import { useState } from "react";
import AllClasses from "./AllClasses";
import ReservationsTable from "./ReservationsTable";

export default function AdminBookClasses() {
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="AdUser">
      <h1 className="admin-page-title">Classes</h1>
      <div className="admin-actions-row">
        <button onClick={() => setCreateOpen(true)}>Create New Class</button>
      </div>

      <div className="AdAct">
        <AllClasses createOpen={createOpen} onCreateClose={() => setCreateOpen(false)} />
      </div>

      <div className="AdAct">
        <ReservationsTable isClass title="Class Reservations" />
      </div>
    </div>
  );
}
