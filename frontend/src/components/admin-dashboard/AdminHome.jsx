import AdminCalendar from "./AdminCalendar";
import RecentActivity from "./RecentActivity";
import { firstName, useDashboard } from "../../hooks/useDashboard";

export default function AdminHome() {
  const { user, loading } = useDashboard();

  return (
    <div className="DashHome1">
      <div className="AdHome">
        <p>Welcome back {loading ? "..." : firstName(user) || "User"}</p>
        <p>Makerspace Admin</p>
        <button>New Payment</button>
      </div>

      <div className="AdRow">
        <div className="AdCon">
          <AdminCalendar />
        </div>
        <div className="AdCon">
          <div className="AdR">
            <div className="AdCol">
              <div>
                <p>Capacity</p>
                <p>42/200</p>
              </div>
              <button>View</button>
            </div>
            <div className="AdCol">
              <div>
                <p>Capacity</p>
                <p>42/200</p>
              </div>
              <button>View</button>
            </div>
            <div className="AdCol">
              <div>
                <p>Capacity</p>
                <p>42/200</p>
              </div>
              <button>View</button>
            </div>
            <div className="AdCol">
              <div>
                <p>Capacity</p>
                <p>42/200</p>
              </div>
              <button>View</button>
            </div>
          </div>
        </div>
      </div>

      <div className="AdAct">
        <RecentActivity />
      </div>
    </div>
  );
}