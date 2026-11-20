import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function AdminDashboard() {
  const { setToken, permissions } = useAuth();
  const navigate = useNavigate();

  const canManageRoles = permissions.includes("MANAGE_ROLES");
  const canManageClasses = permissions.includes("MANAGE_CLASSES");
  const canManageReservations = permissions.includes("MANAGE_RESERVATIONS");

  function handleSignOut() {
    setToken(null);
    navigate("/signin", { replace: true });
  }

  return (
    <div className="Dash1 AdminDashboard">
      <aside className="Dash">
        <button
          onClick={() => navigate("/admin-dashboard/home")}
          style={{ background: "none", border: "none", padding: 0, cursor: "pointer", display: "block" }}
        >
          <img src="/makerspace_logo.png" alt="The Collaboratory" style={{ width: "100%", maxWidth: "160px" }} />
        </button>

        <div className="DashNav">
          <p>Menu</p>
          <div className="DashNav1">
            {canManageRoles && <NavLink to="/admin-dashboard/home">Home</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/analytics">Analytics</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/user">Users</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/rentequipment">Equipment</NavLink>}
            {canManageClasses && <NavLink to="/admin-dashboard/bookclasses">Classes</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/hardware">Hardware Access</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/roles">Roles</NavLink>}
            {canManageReservations && <NavLink to="/admin-dashboard/cancellations">Cancellations</NavLink>}
          </div>
        </div>

        <div className="DashNav">
          <p>General</p>
          <div className="DashNav1">
            <NavLink to="/dashboard/home">Back to member dashboard</NavLink>
            <NavLink to="/admin-dashboard/account">Account</NavLink>
            <button onClick={handleSignOut}>Sign Out</button>
          </div>
        </div>
      </aside>

      <main className="DashMain">
        <Outlet />
      </main>
    </div>
  );
}