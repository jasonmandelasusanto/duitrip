import { useState, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import api from '../services/api';
import { TripHeader } from '../components/trip/TripHeader';
import { SettlementCard } from '../components/settlement/SettlementCard';
import type { SettlementTransaction } from '../types';

export default function Settlement() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip } = useTrip(tripId);
  const navigate = useNavigate();
  const [data, setData] = useState<{ transactions: SettlementTransaction[]; calculatedAt: string; ratesNote: string } | null>(null);

  function load() {
    if (!tripId) return;
    api.get(`/trips/${tripId}/settlement`).then((r) => setData(r.data)).catch(console.error);
  }

  useEffect(() => { load(); }, [tripId]);

  async function markSettled(tx: SettlementTransaction) {
    if (!tripId) return;
    await api.post(`/trips/${tripId}/settlements`, {
      fromUserId: tx.from.userId,
      toUserId: tx.to.userId,
      amountInDestinationCurrency: tx.amountInDestinationCurrency,
    });
    load();
  }

  if (!trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate(`/trips/${tripId}`)} className="text-text-secondary text-sm mb-4">← Back</button>
        <TripHeader trip={trip} />

        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {[
            { label: 'Expenses', path: `/trips/${tripId}` },
            { label: 'Analytics', path: `/trips/${tripId}/analytics` },
            { label: 'Members', path: `/trips/${tripId}/members` },
            { label: 'Settle Up', path: `/trips/${tripId}/settlement` },
          ].map((tab) => (
            <Link key={tab.label} to={tab.path}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Settle Up' ? 'bg-amber text-bg-base' : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >{tab.label}</Link>
          ))}
        </div>

        {data && (
          <div className="bg-bg-border/40 rounded-xl px-3 py-2 mb-4">
            <p className="text-xs text-warning">⏱ {data.ratesNote}</p>
          </div>
        )}

        {!data ? (
          <p className="text-text-muted text-center py-8">Loading…</p>
        ) : data.transactions.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-4xl mb-4">✅</p>
            <p className="text-success font-semibold">All settled!</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {data.transactions.map((tx, i) => (
              <SettlementCard key={i} transaction={tx} onMarkSettled={() => markSettled(tx)} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
