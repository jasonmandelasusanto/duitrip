import { useState, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import api from '../services/api';
import { TripHeader } from '../components/trip/TripHeader';
import { CategoryDonut } from '../components/analytics/CategoryDonut';
import { SpendingByDay } from '../components/analytics/SpendingByDay';
import { SpendingByMember } from '../components/analytics/SpendingByMember';
import { MyTimeline } from '../components/analytics/MyTimeline';
import { MyVsAverage } from '../components/analytics/MyVsAverage';

const TABS = [
  { label: 'Expenses', path: (id: string) => `/trips/${id}`, icon: '💸' },
  { label: 'Analytics', path: (id: string) => `/trips/${id}/analytics`, icon: '📊' },
  { label: 'Members', path: (id: string) => `/trips/${id}/members`, icon: '👥' },
  { label: 'Settle Up', path: (id: string) => `/trips/${id}/settlement`, icon: '💰' },
];

function desktopTabClass(label: string) {
  if (label === 'Analytics') return 'bg-teal text-white';
  if (label === 'Settle Up') return 'text-amber hover:bg-amber/10';
  return 'text-text-secondary hover:bg-bg-elevated';
}

export default function TripAnalytics() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip } = useTrip(tripId);
  const navigate = useNavigate();
  const [analytics, setAnalytics] = useState<Record<string, unknown> | null>(null);
  const [view, setView] = useState<'group' | 'me'>('group');

  useEffect(() => {
    if (!tripId) return;
    api.get(`/trips/${tripId}/analytics`).then((r) => setAnalytics(r.data)).catch(console.error);
  }, [tripId]);

  if (!trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const currency = trip.destinationCurrency;
  const g = analytics?.group as Record<string, unknown> | undefined;
  const ind = analytics?.individual as Record<string, unknown> | undefined;

  const ViewToggle = ({ className }: { className?: string }) => (
    <div className={`flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 ${className ?? ''}`}>
      {(['group', 'me'] as const).map((v) => (
        <button key={v} onClick={() => setView(v)}
          className={`flex-1 py-2 text-sm font-medium rounded-lg capitalize transition-colors ${view === v ? 'bg-teal text-white' : 'text-text-secondary'}`}
        >{v === 'me' ? 'Me' : 'Group'}</button>
      ))}
    </div>
  );

  const Charts = () => view === 'group' ? (
    <div className="flex flex-col gap-6">
      {!!g?.byCategory && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">Spending by Category</h3>
          <CategoryDonut data={g.byCategory as never} currency={currency} total={g.totalSpend as number} />
        </div>
      )}
      {!!g?.byDay && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">Spending by Day</h3>
          <SpendingByDay data={g.byDay as never} currency={currency} />
        </div>
      )}
      {!!g?.byMember && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">Who Paid Most</h3>
          <SpendingByMember data={g.byMember as never} currency={currency} average={(g.totalSpendPerMember as number) || 0} />
        </div>
      )}
    </div>
  ) : (
    <div className="flex flex-col gap-6">
      {!!ind?.byCategory && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">My Spending by Category</h3>
          <CategoryDonut data={ind.byCategory as never} currency={currency} total={ind.totalShare as number} />
        </div>
      )}
      {!!ind?.vsGroupAverage && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">My Share vs Group Average</h3>
          <MyVsAverage {...(ind.vsGroupAverage as { myShare: number; groupAverage: number })} currency={currency} />
        </div>
      )}
      {!!ind?.timeline && (
        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
          <h3 className="font-semibold text-text-primary mb-4">My Expense Timeline</h3>
          <MyTimeline entries={ind.timeline as never} currency={currency} tripId={tripId!} />
        </div>
      )}
    </div>
  );

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Mobile */}
      <div className="lg:hidden max-w-lg mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate(`/trips/${tripId}`)} className="text-text-secondary text-sm mb-4">← Back</button>
        <TripHeader trip={trip} />
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {TABS.map((tab) => (
            <Link key={tab.label} to={tab.path(tripId!)}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Analytics' ? 'bg-teal text-white' : tab.label === 'Settle Up' ? 'text-amber hover:bg-bg-elevated' : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >{tab.label}</Link>
          ))}
        </div>
        <ViewToggle className="mb-4" />
        {!analytics ? <p className="text-text-muted text-center py-8">Loading analytics…</p> : <Charts />}
      </div>

      {/* Desktop */}
      <div className="hidden lg:flex min-h-screen">
        <div className="w-80 shrink-0 border-r border-bg-border bg-bg-surface flex flex-col sticky top-0 h-screen overflow-y-auto">
          <div className="p-5">
            <button onClick={() => navigate('/dashboard')} className="text-text-secondary text-sm mb-4 hover:text-text-primary block">← All Trips</button>
            <TripHeader trip={trip} />
          </div>
          <nav className="px-3 pb-3 flex flex-col gap-1">
            {TABS.map((tab) => (
              <Link key={tab.label} to={tab.path(tripId!)}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${desktopTabClass(tab.label)}`}
              >
                <span>{tab.icon}</span>
                {tab.label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex-1 min-w-0 px-6 pt-6 pb-10 overflow-y-auto">
          <div className="max-w-2xl">
            <ViewToggle className="mb-6" />
            {!analytics ? <p className="text-text-muted text-center py-8">Loading analytics…</p> : <Charts />}
          </div>
        </div>
      </div>
    </div>
  );
}
