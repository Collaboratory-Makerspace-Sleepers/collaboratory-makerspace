import { Routes, Route } from "react-router-dom";
import Home from "./Home";
import SignUp from "./SignUp";
import SignIn from "./SignIn";

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signup" element={<SignUp />} />
      <Route path="/signin" element={<SignIn />} />
    </Routes>
  );
}

export default App;