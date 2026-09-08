import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Home from "./Home";
import SignUp from "./SignUp";
import SignIn from "./SignIn";

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/signin" element={<SignIn />} />
      </Routes>
    </Router>
  );
}

export default App;
