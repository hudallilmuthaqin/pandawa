import { Navigate, Route, Routes } from 'react-router-dom';
import { lazy, type ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import { AppLayout } from './layout/AppLayout';
import { LoadingBlock } from './components/ui';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
// Court pages are loaded eagerly (used constantly on phones); report pages are split into chunks.
import QueuePage from './pages/QueuePage';
import LivePage from './pages/LivePage';

const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const PlayersPage = lazy(() => import('./pages/PlayersPage'));
const PlayerDetailPage = lazy(() => import('./pages/PlayerDetailPage'));
const MyProfilePage = lazy(() => import('./pages/PlayerDetailPage').then((m) => ({ default: m.MyProfilePage })));
const HistoryPage = lazy(() => import('./pages/HistoryPage'));
const ExpensesPage = lazy(() => import('./pages/ExpensesPage'));
const DailyReportPage = lazy(() => import('./pages/DailyReportPage'));
const MonthlyReportPage = lazy(() => import('./pages/MonthlyReportPage'));
const RangeReportPage = lazy(() => import('./pages/RangeReportPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

function AdminOnly({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  return isAdmin ? <>{children}</> : <Navigate to="/" replace />;
}

export default function App() {
  const { user, loading } = useAuth();
  if (loading) return <div className="min-h-dvh bg-navy-900"><LoadingBlock /></div>;
  if (!user) {
    return (
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  }
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="queue" element={<QueuePage />} />
        <Route path="live" element={<LivePage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="me" element={<MyProfilePage />} />
        <Route path="players" element={<AdminOnly><PlayersPage /></AdminOnly>} />
        <Route path="players/:id" element={<PlayerDetailPage />} />
        <Route path="expenses" element={<AdminOnly><ExpensesPage /></AdminOnly>} />
        <Route path="reports/daily" element={<AdminOnly><DailyReportPage /></AdminOnly>} />
        <Route path="reports/monthly" element={<AdminOnly><MonthlyReportPage /></AdminOnly>} />
        <Route path="reports/range" element={<AdminOnly><RangeReportPage /></AdminOnly>} />
        <Route path="settings" element={<AdminOnly><SettingsPage /></AdminOnly>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
