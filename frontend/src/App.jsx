import { Routes, Route, Navigate } from "react-router-dom";
import Home from "./pages/Home";
import SignUp from "./pages/SignUp";
import SignIn from "./pages/SignIn";
import OAuthCallback from "./pages/OAuthCallback";
import Dashboard from "./pages/Dashboard";
import DashboardHome from "./components/dashboard/DashboardHome";
import RentEquipment from "./components/dashboard/RentEquipment";
import BookClasses from "./components/dashboard/BookClasses";
import Account from "./components/dashboard/Account";
import Membership from "./components/dashboard/Membership";

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signup" element={<SignUp />} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/login" element={<SignIn />} />
      <Route path="/oauth-callback" element={<OAuthCallback />} />
      <Route path="/dashboard" element={<Dashboard />}>
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<DashboardHome />} />
        <Route path="rentequipment" element={<RentEquipment />} />
        <Route path="bookclasses" element={<BookClasses />} />
        <Route path="account" element={<Account />} />
        <Route path="membership" element={<Membership />} />
      </Route>
    </Routes>
  );
}

export default App;
