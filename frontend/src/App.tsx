import { Routes, Route, Navigate } from 'react-router-dom';
import { useAppStore } from './store/useAppStore';

import Landing from './pages/Landing';
import Onboarding from './pages/Onboarding';
import Dashboard from './pages/Dashboard';
import NewTrip from './pages/NewTrip';
import TripDetail from './pages/TripDetail';
import TripAnalytics from './pages/TripAnalytics';
import AddExpense from './pages/AddExpense';
import Settlement from './pages/Settlement';
import Members from './pages/Members';
import InviteAccept from './pages/InviteAccept';
import Profile from './pages/Profile';

function RequireAuth({ children }: { children: JSX.Element }) {
  const { user, authLoading } = useAppStore();
  if (authLoading) return <div className="min-h-screen bg-bg-base flex items-center justify-center"><div className="w-8 h-8 border-2 border-teal border-t-transparent rounded-full animate-spin" /></div>;
  if (!user) return <Navigate to="/" replace />;
  if (!user.homeCurrency || user.homeCurrency === '') return <Navigate to="/onboarding" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/onboarding" element={<Onboarding />} />
      <Route path="/invite/:tripId" element={<InviteAccept />} />
      <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>} />
      <Route path="/trips/new" element={<RequireAuth><NewTrip /></RequireAuth>} />
      <Route path="/trips/:tripId" element={<RequireAuth><TripDetail /></RequireAuth>} />
      <Route path="/trips/:tripId/analytics" element={<RequireAuth><TripAnalytics /></RequireAuth>} />
      <Route path="/trips/:tripId/expenses/new" element={<RequireAuth><AddExpense /></RequireAuth>} />
      <Route path="/trips/:tripId/expenses/:expenseId/edit" element={<RequireAuth><AddExpense /></RequireAuth>} />
      <Route path="/trips/:tripId/settlement" element={<RequireAuth><Settlement /></RequireAuth>} />
      <Route path="/trips/:tripId/members" element={<RequireAuth><Members /></RequireAuth>} />
      <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
