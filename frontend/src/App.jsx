import { Routes, Route, Navigate } from "react-router-dom";
import "./App.css";
import Home from "./pages/Home";
import Pricing from "./pages/Pricing";
import SignUp from "./pages/SignUp";
import SignIn from "./pages/SignIn";
import OAuthCallback from "./pages/OAuthCallback";
import Dashboard, { DashboardLanding } from "./pages/Dashboard";
import AdminDashboard from "./pages/AdminDashboard";
import AdminHome from "./components/admin-dashboard/AdminHome";
import AdminUsers from "./components/admin-dashboard/AdminUsers";
import AdminRentEquipment from "./components/admin-dashboard/AdminRentEquipment";
import AdminBookClasses from "./components/admin-dashboard/AdminBookClasses";
import AdminAccount from "./components/admin-dashboard/AdminAccount";
import HardwareCheckIns from "./components/admin-dashboard/HardwareCheckIns";
import DashboardHome from "./components/dashboard/DashboardHome";
import RentEquipment from "./components/dashboard/RentEquipment";
import ReserveEquipment from "./components/dashboard/ReserveEquipment";
import ReservationCheckout from "./components/dashboard/ReservationCheckout";
import LaserWaiver from "./components/dashboard/LaserWaiver";
import BookClasses from "./components/dashboard/BookClasses";
import Account from "./components/dashboard/Account";
import Membership from "./components/dashboard/Membership";
import MembershipCheckout from "./components/dashboard/MembershipCheckout";
import MyReservations from "./components/dashboard/MyReservations";
import HardwareAccess from "./components/dashboard/HardwareAccess";
import PaymentMethods from "./components/dashboard/PaymentMethods";
import BillingHistory from "./components/dashboard/BillingHistory";
import RequireAuth from "./components/RequireAuth";
import RequirePermission from "./components/RequirePermission";
import BillingSuccess from "./pages/BillingSuccess";
import BillingCancel from "./pages/BillingCancel";
import TermsOfService from "./pages/TermsOfService"; //added this import for Terms of Service page

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/pricing" element={<Pricing />} />
      <Route path="/signup" element={<SignUp />} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/login" element={<SignIn />} />
      <Route path="/terms" element={<TermsOfService />} /> //added this route for Terms of Service page
      <Route path="/oauth-callback" element={<OAuthCallback />} />
      <Route path="/billing/success" element={<BillingSuccess />} />
      <Route path="/billing/cancel" element={<BillingCancel />} />
      <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>}>
        <Route index element={<DashboardLanding />} />
        <Route path="home" element={<DashboardHome />} />
        <Route path="rentequipment" element={<RentEquipment />} />
        <Route path="rentequipment/:id" element={<ReserveEquipment />} />
        <Route path="rentequipment/:id/waiver" element={<LaserWaiver />} />
        <Route path="rentequipment/:id/checkout" element={<ReservationCheckout />} />
        <Route path="bookclasses" element={<BookClasses />} />
        <Route path="account" element={<Account />} />
        <Route path="membership" element={<Membership />} />
        <Route path="membership/checkout" element={<MembershipCheckout />} />
        <Route path="reservations" element={<MyReservations />} />
        <Route path="hardware-access" element={<HardwareAccess />} />
        <Route
          path="hardware-approvals"
          element={<RequirePermission anyOf={["MANAGE_EQUIPMENT"]}><HardwareCheckIns /></RequirePermission>}
        />
        <Route path="payment-methods" element={<PaymentMethods />} />
        <Route path="billing-history" element={<BillingHistory />} />
      </Route>
      <Route
        path="/admin-dashboard"
        element={
          <RequireAuth>
            <RequirePermission anyOf={["MANAGE_ROLES"]}>
              <AdminDashboard />
            </RequirePermission>
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<AdminHome />} />
        <Route path="user" element={<AdminUsers />} />
        <Route path="rentequipment" element={<AdminRentEquipment />} />
        <Route path="bookclasses" element={<AdminBookClasses />} />
        <Route path="account" element={<AdminAccount />} />
        <Route path="hardware" element={<HardwareCheckIns />} />
      </Route>
    </Routes>
  );
}

export default App;
