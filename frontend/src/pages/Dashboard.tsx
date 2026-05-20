import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../services/api';
import { TripCard } from '../components/trip/TripCard';
import { Button } from '../components/ui/Button';
import { NotificationBell } from '../components/ui/NotificationBell';
import { signOut } from '../services/auth';
import { useAppStore } from '../store/useAppStore';
import KofiWidget from '../components/ui/KofiWidget';

export default function Dashboard() {
  const { user, trips, setTrips } = useAppStore();
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    api.get('/trips').then((r) => setTrips(r.data)).catch(console.error).finally(() => setLoading(false));
  }, [setTrips]);

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-5xl mx-auto px-4 pt-8 pb-24">
        {/* Header — profile/signout hidden on lg since sidebar handles it */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-text-primary">My Trips</h1>
            <p className="text-sm text-text-secondary">{user?.displayName} · {user?.homeCurrency}</p>
          </div>
          <div className="flex items-center gap-1 lg:hidden">
            <NotificationBell />
            <Link to="/profile">
              <Button variant="ghost" size="sm">Profile</Button>
            </Link>
            <Button variant="ghost" size="sm" onClick={() => { signOut(); navigate('/'); }}>Sign out</Button>
          </div>
          <div className="hidden lg:flex items-center gap-1">
            <NotificationBell />
          </div>
        </div>

        {loading ? (
          <div className="text-text-muted text-center py-12">Loading…</div>
        ) : trips.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-6xl mb-4">✈️</p>
            <p className="text-text-secondary mb-6">No trips yet. Start your first one!</p>
          </div>
        ) : (
          <>
            {/* Multi-trip stats */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
              <div className="bg-bg-surface border border-bg-border rounded-xl p-3">
                <p className="text-xs text-text-muted">Trips planned</p>
                <p className="text-2xl font-bold text-text-primary mt-0.5">{trips.length}</p>
              </div>
              <div className="bg-bg-surface border border-bg-border rounded-xl p-3">
                <p className="text-xs text-text-muted">Destinations</p>
                <p className="text-2xl font-bold text-text-primary mt-0.5">
                  {new Set(trips.map((t) => t.destinationCurrency)).size}
                </p>
              </div>
              <div className="bg-bg-surface border border-bg-border rounded-xl p-3">
                <p className="text-xs text-text-muted">Total members</p>
                <p className="text-2xl font-bold text-text-primary mt-0.5">
                  {new Set(trips.flatMap((t) => t.members.map((m) => m.userId || m.ghostId))).size}
                </p>
              </div>
              <div className="bg-bg-surface border border-bg-border rounded-xl p-3">
                <p className="text-xs text-text-muted">Upcoming trips</p>
                <p className="text-2xl font-bold text-teal mt-0.5">
                  {trips.filter((t) => new Date(t.startDate) > new Date()).length}
                </p>
              </div>
            </div>

            {/* Trips grid — 1 col on mobile, 2 on lg */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
              {trips.map((t) => <TripCard key={t.tripId} trip={t} />)}
            </div>
          </>
        )}

        <div className="mt-10 border-t border-bg-border pt-6">
          <div className="flex items-start gap-3 mb-3">
            <span className="text-lg">☕</span>
            <p className="text-sm text-text-secondary leading-snug">I intend to run this for free forever — but if you'd like to leave a tip, any amount is hugely appreciated to keep the app running.</p>
          </div>
          <KofiWidget />
        </div>

        {/* FAB — hidden on lg since sidebar has + New Trip */}
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 lg:hidden">
          <Link to="/trips/new">
            <Button size="lg" className="shadow-lg shadow-teal/20">
              + New Trip
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
