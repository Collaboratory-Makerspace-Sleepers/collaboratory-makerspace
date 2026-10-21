import AllEquipment from "./AllEquipment";
import ReservationsTable from "./ReservationsTable";
export default function AdminRentEquipment() {
  return (
    <div className="AdUser">
      <p>Equipment</p>

      <div className="AdUser1">
        <div className="AdUser2">
          <div className="AdUser3">
            <p>Create a new equipment</p>
            <p>Create a new staff employee or instructor for classes</p>
          </div>
          <button>Create New Equipment</button>
        </div>
      </div>

      <div className="AdAct">
        <AllEquipment />
      </div>

      <div className="AdAct">
        <ReservationsTable isClass={false} title="Equipment Reservations" />
      </div>
    </div>
  );
}
