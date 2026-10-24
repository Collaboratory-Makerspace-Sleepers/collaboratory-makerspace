import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function AdminDashboard() {
  const { setToken, permissions } = useAuth();
  const navigate = useNavigate();

  const canManageRoles = permissions.includes("MANAGE_ROLES");
  const canManageClasses = permissions.includes("MANAGE_CLASSES");

  function handleSignOut() {
    setToken(null);
    navigate("/signin", { replace: true });
  }

  return (
    <div className="Dash1 AdminDashboard">
      <aside className="Dash">
        <div className="DashNav">
          <p>Menu</p>
          <div className="DashNav1">
            {canManageRoles && <NavLink to="/admin-dashboard/home">Home</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/user">Users</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/rentequipment">Rent Equipment</NavLink>}
            {canManageClasses && <NavLink to="/admin-dashboard/bookclasses">Classes</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/hardware">Hardware Access</NavLink>}
            {canManageRoles && <NavLink to="/admin-dashboard/roles">Roles</NavLink>}
          </div>
        </div>

        <div className="DashNav">
          <p>General</p>
          <div className="DashNav1">
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