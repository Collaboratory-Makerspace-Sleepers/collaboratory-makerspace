import { Routes, Route } from "react-router-dom";
import Home from "./Home";
import SignUp from "./SignUp";
import SignIn from "./SignIn";
import OAuthCallback from "./pages/OAuthCallback";
import Dashboard from "./pages/Dashboard";
import RequireAuth from "./components/RequireAuth";

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signup" element={<SignUp />} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/oauth-callback" element={<OAuthCallback />} />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <Dashboard />
          </RequireAuth>
        }
      />
    </Routes>
  );
}

export default App;