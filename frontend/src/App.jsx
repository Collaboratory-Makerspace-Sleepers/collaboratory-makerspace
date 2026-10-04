import { Routes, Route, Navigate } from "react-router-dom";
import "./App.css";
import Home from "./pages/Home";
import Pricing from "./pages/Pricing";
import SignUp from "./pages/SignUp";
import SignIn from "./pages/SignIn";
import OAuthCallback from "./pages/OAuthCallback";
import Dashboard from "./pages/Dashboard";
import DashboardHome from "./components/dashboard/DashboardHome";
import RentEquipment from "./components/dashboard/RentEquipment";
import ReserveEquipment from "./components/dashboard/ReserveEquipment";
import ReservationCheckout from "./components/dashboard/ReservationCheckout";
import BookClasses from "./components/dashboard/BookClasses";
import Account from "./components/dashboard/Account";
import Membership from "./components/dashboard/Membership";
import MembershipCheckout from "./components/dashboard/MembershipCheckout";
import MyReservations from "./components/dashboard/MyReservations";
import PaymentMethods from "./components/dashboard/PaymentMethods";
import RequireAuth from "./components/RequireAuth";
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
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<DashboardHome />} />
        <Route path="rentequipment" element={<RentEquipment />} />
        <Route path="rentequipment/:id" element={<ReserveEquipment />} />
        <Route path="rentequipment/:id/checkout" element={<ReservationCheckout />} />
        <Route path="bookclasses" element={<BookClasses />} />
        <Route path="account" element={<Account />} />
        <Route path="membership" element={<Membership />} />
        <Route path="membership/checkout" element={<MembershipCheckout />} />
        <Route path="reservations" element={<MyReservations />} />
        <Route path="payment-methods" element={<PaymentMethods />} />
      </Route>
    </Routes>
  );
}

export default App;
