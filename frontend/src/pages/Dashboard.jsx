import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import useHardwareRequest from "../components/dashboard/useHardwareRequest";
import "../components/dashboard/HardwareAccess.css";

export function DashboardLanding() {
  const { loadingProfile, hasPermission } = useAuth();

  if (loadingProfile) return <p className="AdminLoading">Loading dashboard…</p>;

  return hasPermission("MANAGE_ROLES")
    ? <Navigate to="/admin-dashboard" replace />
    : <Navigate to="/dashboard/home" replace />;
}

export default function Dashboard() {
  const { setToken, hasPermission, authFetch } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const hardware = useHardwareRequest(authFetch, true);
  const onAccessPage = location.pathname.startsWith("/dashboard/hardware-access");
  const hwStatus = hardware.current?.status;

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
            <NavLink to="/dashboard/reservations">My Reservations</NavLink>
            <NavLink to="/dashboard/hardware-access">Hardware Access</NavLink>
            {hasPermission("MANAGE_EQUIPMENT") && !hasPermission("MANAGE_ROLES") && (
              <NavLink to="/dashboard/hardware-approvals">Approve Hardware</NavLink>
            )}
          </div>
        </div>

        <div className="DashNav">
          <p>General</p>
          <div className="DashNav1">
            <NavLink to="/dashboard/account">Account</NavLink>
            <NavLink to="/dashboard/membership">Membership</NavLink>
            <NavLink to="/dashboard/payment-methods">Payment Methods</NavLink>
            <NavLink to="/dashboard/billing-history">Billing History</NavLink>
            {hasPermission("MANAGE_ROLES") && (
              <NavLink to="/admin-dashboard">Admin dashboard</NavLink>
            )}
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
        {!onAccessPage && (hwStatus === "NEEDS_WAIVER" || hwStatus === "WAIVER_SIGNED") && (
          <div className="HWBanner" role="status">
            <span>
              {hwStatus === "NEEDS_WAIVER"
                ? `A waiver is required to use ${hardware.current.equipmentName}.`
                : `Waiting for staff to approve your waiver for ${hardware.current.equipmentName}.`}
            </span>
            <NavLink to="/dashboard/hardware-access">
              {hwStatus === "NEEDS_WAIVER" ? "Sign waiver" : "View status"}
            </NavLink>
          </div>
        )}
        {!onAccessPage && hardware.justApproved && (
          <div className="HWBanner approved" role="status">
            <span>Your waiver for {hardware.current?.equipmentName} was approved.</span>
            <NavLink to="/dashboard/hardware-access" onClick={hardware.dismissApproved}>
              Continue
            </NavLink>
          </div>
        )}
        <Outlet context={{ hardware }} />
      </div>
    </div>
  );
}
