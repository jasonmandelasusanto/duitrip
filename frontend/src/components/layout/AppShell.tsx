import { useEffect, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAppStore } from '../../store/useAppStore';
import { signOut } from '../../services/auth';
import { currencyFlag } from '../../utils/flag';
import api from '../../services/api';

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const { user, trips, setTrips } = useAppStore();
  const navigate = useNavigate();
  const { tripId } = useParams<{ tripId?: string }>();

  useEffect(() => {
    api.get('/trips').then((r) => setTrips(r.data)).catch(() => {});
  }, [setTrips]);

  return (
    <div className="flex flex-col min-h-screen lg:flex-row">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:flex-col lg:fixed lg:inset-y-0 lg:w-60 bg-bg-surface border-r border-bg-border z-20">
        {/* Brand */}
        <div className="px-5 py-5 border-b border-bg-border">
          <Link to="/dashboard" className="text-lg font-bold text-text-primary tracking-tight">
            duitrip
          </Link>
          <p className="text-xs text-text-muted mt-0.5">{user?.displayName} · {user?.homeCurrency}</p>
        </div>

        {/* Trips list */}
        <nav className="flex-1 overflow-y-auto px-3 py-4">
          <p className="text-xs font-semibold text-text-muted uppercase tracking-wider px-2 mb-2">My Trips</p>
          <div className="flex flex-col gap-0.5">
            {trips.map((t) => {
              const flag = currencyFlag(t.destinationCurrency);
              const isActive = t.tripId === tripId;
              return (
                <Link
                  key={t.tripId}
                  to={`/trips/${t.tripId}`}
                  className={`flex items-center gap-2.5 px-2 py-2 rounded-lg text-sm transition-colors ${
                    isActive
                      ? 'bg-teal/15 text-teal font-medium'
                      : 'text-text-secondary hover:bg-bg-elevated hover:text-text-primary'
                  }`}
                >
                  <span className="text-base leading-none shrink-0">{flag || '✈️'}</span>
                  <span className="truncate">{t.name}</span>
                </Link>
              );
            })}
            {trips.length === 0 && (
              <p className="text-xs text-text-muted px-2 py-1">No trips yet</p>
            )}
          </div>

          <Link
            to="/trips/new"
            className="flex items-center gap-2 mt-3 px-2 py-2 rounded-lg text-sm text-text-muted hover:text-teal hover:bg-teal/10 transition-colors"
          >
            <span className="text-base">+</span>
            <span>New Trip</span>
          </Link>
        </nav>

        {/* Profile / sign out */}
        <div className="px-3 py-4 border-t border-bg-border flex flex-col gap-1">
          <Link
            to="/profile"
            className="flex items-center gap-2 px-2 py-2 rounded-lg text-sm text-text-secondary hover:bg-bg-elevated transition-colors"
          >
            <span>👤</span> Profile
          </Link>
          <button
            onClick={() => { signOut(); navigate('/'); }}
            className="flex items-center gap-2 px-2 py-2 rounded-lg text-sm text-text-secondary hover:bg-bg-elevated transition-colors text-left"
          >
            <span>↩</span> Sign out
          </button>
        </div>
      </aside>

      {/* Main content — offset by sidebar on desktop */}
      <main className="flex-1 min-w-0 lg:ml-60">
        {children}
      </main>
    </div>
  );
}
