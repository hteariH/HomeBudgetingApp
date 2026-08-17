import React from 'react';
import { BrowserRouter, Route, Routes, Navigate } from 'react-router-dom';
import './index.css';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import Accounts from './pages/Accounts';
import Categories from './pages/Categories';
import Transactions from './pages/Transactions';
import Transfers from './pages/Transfers';
import Currencies from './pages/Currencies';
import Banks from './pages/Banks';
import VerifyEmail from './pages/VerifyEmail';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Login from './pages/Login';
import Register from './pages/Register';
import Header from './components/Header';

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  return token ? <>{children}</> : <Navigate to="/login" />;
};

const App: React.FC = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route
        path="/*"
        element={
          <PrivateRoute>
            <div className="app-layout">
              <Sidebar />
              <div className="main-content">
                <Header />
                <main style={{ flex: 1, paddingBottom: '2rem' }}>
                  <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/accounts" element={<Accounts />} />
                    <Route path="/transactions" element={<Transactions />} />
                    <Route path="/transfers" element={<Transfers />} />
                    <Route path="/categories" element={<Categories />} />
                    <Route path="/currencies" element={<Currencies />} />
                    <Route path="/banks" element={<Banks />} />
                  </Routes>
                </main>
              </div>
            </div>
          </PrivateRoute>
        }
      />
    </Routes>
  </BrowserRouter>
);

export default App;
