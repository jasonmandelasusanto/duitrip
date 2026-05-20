import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import api from '../services/api';
import { TripHeader } from '../components/trip/TripHeader';
import { SettlementCard } from '../components/settlement/SettlementCard';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { formatCurrency } from '../utils/currency';
import { useAppStore } from '../store/useAppStore';
import type { Settlement, SettlementTransaction } from '../types';

const TABS = [
  { label: 'Expenses', path: (id: string) => `/trips/${id}`, icon: '💸' },
  { label: 'Analytics', path: (id: string) => `/trips/${id}/analytics`, icon: '📊' },
  { label: 'Members', path: (id: string) => `/trips/${id}/members`, icon: '👥' },
  { label: 'Settle Up', path: (id: string) => `/trips/${id}/settlement`, icon: '💰' },
];

function desktopTabClass(label: string) {
  if (label === 'Settle Up') return 'bg-amber text-bg-base';
  return 'text-text-secondary hover:bg-bg-elevated';
}

function formatSettledAt(iso: string) {
  try {
    return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  } catch { return iso; }
}

export default function Settlement() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip } = useTrip(tripId);
  const navigate = useNavigate();
  const { user } = useAppStore();
  const [data, setData] = useState<{ transactions: SettlementTransaction[]; ratesNote: string } | null>(null);
  const [history, setHistory] = useState<Settlement[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editNote, setEditNote] = useState('');
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);

  const loadSettlement = useCallback(() => {
    if (!tripId) return;
    api.get(`/trips/${tripId}/settlement`).then((r) => setData(r.data)).catch(console.error);
  }, [tripId]);

  const loadHistory = useCallback(() => {
    if (!tripId) return;
    api.get(`/trips/${tripId}/settlements`).then((r) => setHistory(r.data)).catch(console.error);
  }, [tripId]);

  useEffect(() => {
    loadSettlement();
    loadHistory();
  }, [loadSettlement, loadHistory]);

  async function markSettled(tx: SettlementTransaction, note: string) {
    if (!tripId) return;
    await api.post(`/trips/${tripId}/settlements`, {
      fromUserId: tx.from.userId,
      toUserId: tx.to.userId,
      amountInDestinationCurrency: tx.amountInDestinationCurrency,
      note: note || null,
    });
    loadSettlement();
    loadHistory();
  }

  function startEdit(s: Settlement) {
    setEditingId(s.settlementId);
    setEditNote(s.note || '');
  }

  async function saveEdit(settlementId: string) {
    if (!tripId) return;
    await api.patch(`/trips/${tripId}/settlements/${settlementId}`, { note: editNote.trim() || null });
    setEditingId(null);
    loadHistory();
  }

  async function nudgeMember(tx: SettlementTransaction) {
    if (!tripId) return;
    await api.post(`/trips/${tripId}/nudge`, {
      toUserId: tx.from.userId,
      amount: tx.amountInDestinationCurrency,
      currency: tx.destinationCurrency,
    });
  }

  async function confirmDeleteSettlement() {
    if (!tripId || !deleteTargetId) return;
    setDeleteTargetId(null);
    await api.delete(`/trips/${tripId}/settlements/${deleteTargetId}`);
    loadSettlement();
    loadHistory();
  }

  if (!trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const currency = trip.destinationCurrency;

  const OutstandingSection = () => (
    <>
      <p className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-2">Outstanding</p>
      {data && (
        <div className="bg-bg-border/40 rounded-xl px-3 py-2 mb-3">
          <p className="text-xs text-warning">⏱ {data.ratesNote}</p>
        </div>
      )}
      {!data ? (
        <p className="text-text-muted text-center py-8">Loading…</p>
      ) : data.transactions.length === 0 ? (
        <div className="text-center py-8">
          <p className="text-4xl mb-3">✅</p>
          <p className="text-success font-semibold">All settled!</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3 mb-6">
          {data.transactions.map((tx, i) => (
            <SettlementCard
              key={i}
              transaction={tx}
              onMarkSettled={(note) => markSettled(tx, note)}
              onNudge={!tx.from.isGhost && tx.from.userId !== user?.uid ? () => nudgeMember(tx) : undefined}
            />
          ))}
        </div>
      )}
    </>
  );

  const HistorySection = () => history.length === 0 ? null : (
    <>
      <p className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-2 mt-4">Settlement History</p>
      <div className="flex flex-col gap-2">
        {history.map((s) => (
          <div key={s.settlementId} className="bg-bg-surface border border-bg-border rounded-xl p-4">
            {editingId === s.settlementId ? (
              <div>
                <p className="text-sm font-medium text-text-primary mb-2">
                  <span className="text-danger">{s.fromDisplayName}</span>
                  {' → '}
                  <span className="text-success">{s.toDisplayName}</span>
                  <span className="text-amber font-mono ml-2">{formatCurrency(s.amountInDestinationCurrency, currency)}</span>
                </p>
                <input
                  type="text"
                  value={editNote}
                  onChange={(e) => setEditNote(e.target.value)}
                  placeholder="Edit note…"
                  autoFocus
                  className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none focus:border-teal transition-colors mb-2"
                  onKeyDown={(e) => { if (e.key === 'Enter') saveEdit(s.settlementId); if (e.key === 'Escape') setEditingId(null); }}
                />
                <div className="flex gap-2">
                  <button onClick={() => saveEdit(s.settlementId)} className="text-xs text-teal font-medium hover:underline">Save</button>
                  <button onClick={() => setEditingId(null)} className="text-xs text-text-muted hover:underline">Cancel</button>
                </div>
              </div>
            ) : (
              <div className="flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-text-primary">
                    <span className="text-danger">{s.fromDisplayName}</span>
                    {' → '}
                    <span className="text-success">{s.toDisplayName}</span>
                  </p>
                  <p className="text-base font-bold text-amber font-mono mt-0.5">
                    {formatCurrency(s.amountInDestinationCurrency, currency)}
                  </p>
                  {s.note && <p className="text-xs text-text-secondary mt-1 italic">"{s.note}"</p>}
                  <p className="text-xs text-text-muted mt-1">{formatSettledAt(s.settledAt)}</p>
                </div>
                <div className="flex gap-3 shrink-0">
                  <button onClick={() => startEdit(s)} className="text-xs text-text-secondary hover:text-teal transition-colors">Edit</button>
                  <button onClick={() => setDeleteTargetId(s.settlementId)} className="text-xs text-text-secondary hover:text-danger transition-colors">Delete</button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </>
  );

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Mobile */}
      <div className="lg:hidden max-w-2xl mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate(`/trips/${tripId}`)} className="text-text-secondary text-sm mb-4">← Back</button>
        <TripHeader trip={trip} />
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {TABS.map((tab) => (
            <Link key={tab.label} to={tab.path(tripId!)}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Settle Up' ? 'bg-amber text-bg-base' : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >{tab.label}</Link>
          ))}
        </div>
        <OutstandingSection />
        <HistorySection />
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
            <OutstandingSection />
            <HistorySection />
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={!!deleteTargetId}
        title="Remove settlement?"
        message="This will affect the outstanding balance."
        confirmLabel="Remove"
        variant="danger"
        onConfirm={confirmDeleteSettlement}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  );
}
