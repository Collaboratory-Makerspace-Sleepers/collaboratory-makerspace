import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function Dashboard() {
  const { setToken } = useAuth();
  const navigate = useNavigate();

  function handleSignOut() {
    setToken(null);
    navigate("/signin", { replace: true });
  }

  return (
    <div className="Dash1">
      {/* nav bar */}
      <div className="Dash">
        <div className="DashNav">
          <p>Menu</p>
          <div className="DashNav1">
            <NavLink to="/dashboard/home">Home</NavLink>
            <NavLink to="/dashboard/rentequipment">Rent Equipment</NavLink>
            <NavLink to="/dashboard/bookclasses">Book Classes</NavLink>
          </div>
        </div>

        <div className="DashNav">
          <p>General</p>
          <div className="DashNav1">
            <NavLink to="/dashboard/account">Account</NavLink>
            <NavLink to="/dashboard/membership">Membership</NavLink>
            <button
              onClick={handleSignOut}
              className="text-sm text-gray-500 hover:text-red-500 transition-colors"
            >
              Sign Out
            </button>
          </div>
        </div>
      </div>

      {/* main screen */}
      <div className="DashMain">
        <Outlet />
      </div>
    </div>
  );
}