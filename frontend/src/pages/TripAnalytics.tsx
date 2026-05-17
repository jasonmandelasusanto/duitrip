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

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate(`/trips/${tripId}`)} className="text-text-secondary text-sm mb-4">← Back</button>
        <TripHeader trip={trip} />

        {/* Tab bar */}
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {[
            { label: 'Expenses', path: `/trips/${tripId}` },
            { label: 'Analytics', path: `/trips/${tripId}/analytics` },
            { label: 'Members', path: `/trips/${tripId}/members` },
            { label: 'Settle Up', path: `/trips/${tripId}/settlement` },
          ].map((tab) => (
            <Link key={tab.label} to={tab.path}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Analytics' ? 'bg-teal text-white' : tab.label === 'Settle Up' ? 'text-amber hover:bg-bg-elevated' : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >{tab.label}</Link>
          ))}
        </div>

        {/* View toggle */}
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {(['group', 'me'] as const).map((v) => (
            <button key={v} onClick={() => setView(v)}
              className={`flex-1 py-2 text-sm font-medium rounded-lg capitalize transition-colors ${view === v ? 'bg-teal text-white' : 'text-text-secondary'}`}
            >{v === 'me' ? 'Me' : 'Group'}</button>
          ))}
        </div>

        {!analytics ? (
          <p className="text-text-muted text-center py-8">Loading analytics…</p>
        ) : view === 'group' ? (
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
        )}
      </div>
    </div>
  );
}
