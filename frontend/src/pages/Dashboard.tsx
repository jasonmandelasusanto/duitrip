import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../services/api';
import type { Trip } from '../types';
import { TripCard } from '../components/trip/TripCard';
import { Button } from '../components/ui/Button';
import { signOut } from '../services/auth';
import { useAppStore } from '../store/useAppStore';
import KofiWidget from '../components/ui/KofiWidget';

export default function Dashboard() {
  const { user } = useAppStore();
  const [trips, setTrips] = useState<Trip[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    api.get('/trips').then((r) => setTrips(r.data)).catch(console.error).finally(() => setLoading(false));
  }, []);

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-8 pb-24">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-text-primary">My Trips</h1>
            <p className="text-sm text-text-secondary">{user?.displayName} · {user?.homeCurrency}</p>
          </div>
          <div className="flex gap-2">
            <Link to="/profile">
              <Button variant="ghost" size="sm">Profile</Button>
            </Link>
            <Button variant="ghost" size="sm" onClick={() => { signOut(); navigate('/'); }}>Sign out</Button>
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
          <div className="flex flex-col gap-3">
            {trips.map((t) => <TripCard key={t.tripId} trip={t} />)}
          </div>
        )}

        <div className="mt-10 border-t border-bg-border pt-6">
          <div className="flex items-start gap-3 mb-3">
            <span className="text-lg">☕</span>
            <p className="text-sm text-text-secondary leading-snug">I intend to run this for free forever — but if you'd like to leave a tip, any amount is hugely appreciated to keep the app running.</p>
          </div>
          <KofiWidget />
        </div>

        <div className="fixed bottom-6 left-1/2 -translate-x-1/2">
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
