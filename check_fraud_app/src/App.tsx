import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import FraudDashboard from "./pages/FraudDashboard";

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<FraudDashboard />} />
        {/* <Route path="/other" element={<OtherPage />} /> */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;