import React from 'react';
import { BrowserRouter, Routes, Route, NavLink, useLocation, Navigate } from 'react-router-dom';
import {
  LayoutDashboard, ArrowRightLeft, Layers, Bell, Send,
  Activity, ChevronRight, LogOut
} from 'lucide-react';
import { ToastProvider } from './context/ToastContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import Dashboard from './pages/Dashboard';
import TradeList from './pages/TradeList';
import Positions from './pages/Positions';
import Notifications from './pages/Notifications';
import SubmitTrade from './pages/SubmitTrade';
import Login from './pages/Login';
import './index.css';

const NAV_ITEMS = [
  { to: '/',            label: 'Dashboard',     icon: <LayoutDashboard size={16} /> },
  { to: '/submit',      label: 'Submit Trade',   icon: <Send size={16} />             },
  { to: '/trades',      label: 'Trade History',  icon: <ArrowRightLeft size={16} />   },
  { to: '/positions',   label: 'Positions',      icon: <Layers size={16} />           },
  { to: '/notifications', label: 'Notifications', icon: <Bell size={16} />            },
];

const EXTERNAL_LINKS = [
  { href: 'http://localhost:9090', label: 'Prometheus', color: '#e6522c' },
  { href: 'http://localhost:3000', label: 'Grafana',    color: '#f46800' },
  { href: 'http://localhost:5601', label: 'Kibana',     color: '#00bfb3' },
  { href: 'http://localhost:8091', label: 'Kafka UI',   color: '#6366f1' },
];

const Sidebar: React.FC = () => {
  const location = useLocation(); // accessed via NavLink isActive below
  const { logout } = useAuth();

  return (
    <aside className="sidebar">
      {/* Logo */}
      <div className="sidebar__logo">
        <div className="sidebar__logo-icon">PT</div>
        <div>
          <div className="sidebar__logo-text">PostTrade</div>
          <div className="sidebar__logo-sub">Clearing Platform</div>
        </div>
      </div>

      {/* Navigation */}
      <span className="sidebar__section-label">Navigation</span>
      {NAV_ITEMS.map(({ to, label, icon }) => (
        <NavLink
          key={to}
          to={to}
          id={`nav-${label.toLowerCase().replace(/\s+/g, '-')}`}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          end={to === '/'}
        >
          {icon}
          {label}
        </NavLink>
      ))}

      {/* Observability links */}
      <span className="sidebar__section-label" style={{ marginTop: 32 }}>Observability</span>
      {EXTERNAL_LINKS.map(({ href, label, color }) => (
        <a
          key={href}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="nav-item"
        >
          <Activity size={14} color={color} />
          <span style={{ fontSize: '0.82rem' }}>{label}</span>
          <ChevronRight size={12} style={{ marginLeft: 'auto', opacity: 0.4 }} />
        </a>
      ))}

      {/* Status footer */}
      <div style={{ marginTop: 'auto', padding: '12px 8px' }}>
        <div style={{
          background: 'var(--bg-elevated)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          padding: '10px 12px',
        }}>
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: 6 }}>
            API Gateway
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.8rem' }}>
            <div className="status-dot" />
            <span style={{ color: 'var(--success)', fontWeight: 600 }}>Connected</span>
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: 4 }}>
            localhost:8080
          </div>
        </div>
        <button 
          onClick={logout}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--text-muted)',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            cursor: 'pointer',
            padding: '8px 0',
            marginTop: '16px',
            fontSize: '0.8rem',
            width: '100%',
          }}
          onMouseOver={e => e.currentTarget.style.color = 'var(--error)'}
          onMouseOut={e => e.currentTarget.style.color = 'var(--text-muted)'}
        >
          <LogOut size={16} /> Logout
        </button>
      </div>
    </aside>
  );
};

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
};



const Topbar: React.FC = () => {
  const location = useLocation();
  const current = NAV_ITEMS.find((n) =>
    n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)
  );

  return (
    <header className="topbar">
      <span className="topbar__title">{current?.label ?? 'Clearing Platform'}</span>
      <div className="topbar__badge">
        <div className="status-dot" />
        <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>Live</span>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginLeft: 8 }}>
          {new Date().toLocaleDateString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })}
        </span>
      </div>
    </header>
  );
};

const AppShell: React.FC = () => (
  <ProtectedRoute>
    <div className="app-shell">
      <Topbar />
      <Sidebar />
      <main className="main-content">
        <Routes>
          <Route path="/"              element={<Dashboard />} />
          <Route path="/submit"        element={<SubmitTrade />} />
          <Route path="/trades"        element={<TradeList />} />
          <Route path="/positions"     element={<Positions />} />
          <Route path="/notifications" element={<Notifications />} />
        </Routes>
      </main>
    </div>
  </ProtectedRoute>
);

const App: React.FC = () => (
  <BrowserRouter>
    <AuthProvider>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="*" element={<AppShell />} />
        </Routes>
      </ToastProvider>
    </AuthProvider>
  </BrowserRouter>
);

export default App;
