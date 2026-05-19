import { useState } from 'react';
import type { SettlementTransaction } from '../../types';
import { formatCurrency } from '../../utils/currency';
import { Button } from '../ui/Button';

interface SettlementCardProps {
  transaction: SettlementTransaction;
  onMarkSettled?: (note: string) => Promise<void>;
  onNudge?: () => Promise<void>;
}

export function SettlementCard({ transaction: tx, onMarkSettled, onNudge }: SettlementCardProps) {
  const [confirming, setConfirming] = useState(false);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);
  const [nudged, setNudged] = useState(false);
  const [nudging, setNudging] = useState(false);
  const isGhostTx = tx.from.isGhost || tx.to.isGhost;

  async function handleNudge() {
    if (!onNudge) return;
    setNudging(true);
    try {
      await onNudge();
      setNudged(true);
      setTimeout(() => setNudged(false), 3000);
    } catch (err: unknown) {
      const msg = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { detail?: string } } }).response?.data?.detail
        : null;
      alert(msg || 'Could not send nudge');
    } finally {
      setNudging(false);
    }
  }

  async function handleConfirm() {
    if (!onMarkSettled) return;
    setLoading(true);
    try {
      await onMarkSettled(note.trim());
    } finally {
      setLoading(false);
      setConfirming(false);
      setNote('');
    }
  }

  return (
    <div className="bg-bg-surface border border-bg-border rounded-xl p-4">
      <div className="flex items-center gap-3">
        <div className="flex-1">
          <p className="text-sm font-medium text-text-primary">
            <span className="text-danger">{tx.from.displayName}</span>
            {tx.from.isGhost && ' 👻'}
            {' → '}
            <span className="text-success">{tx.to.displayName}</span>
            {tx.to.isGhost && ' 👻'}
          </p>
          <p className="text-lg font-bold text-amber font-mono mt-0.5">
            {formatCurrency(tx.amountInDestinationCurrency, tx.destinationCurrency)}
          </p>
          <p className="text-xs text-text-muted mt-0.5">
            ≈ {formatCurrency(tx.amountInFromHomeCurrency, tx.fromHomeCurrency)} → {formatCurrency(tx.amountInToHomeCurrency, tx.toHomeCurrency)}
          </p>
          {isGhostTx && (
            <p className="text-xs text-warning mt-1">⚠️ Collect offline</p>
          )}
        </div>
        <div className="flex flex-col gap-1.5 shrink-0">
          {onNudge && !isGhostTx && !confirming && (
            <button
              onClick={handleNudge}
              disabled={nudging || nudged}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-bg-border text-xs text-text-secondary hover:border-teal/50 hover:text-teal transition-colors disabled:opacity-60"
            >
              {nudged ? '✓ Sent' : nudging ? '…' : '🔔 Nudge'}
            </button>
          )}
          {onMarkSettled && !confirming && (
            <Button size="sm" onClick={() => setConfirming(true)}>
              Mark settled
            </Button>
          )}
        </div>
      </div>

      {confirming && (
        <div className="mt-3 pt-3 border-t border-bg-border">
          <p className="text-xs text-text-secondary mb-2">Add a note (optional)</p>
          <input
            type="text"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Paid via PayNow, bank transfer…"
            className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none focus:border-teal transition-colors mb-3"
            autoFocus
            onKeyDown={(e) => { if (e.key === 'Enter') handleConfirm(); if (e.key === 'Escape') setConfirming(false); }}
          />
          <div className="flex gap-2">
            <Button size="sm" onClick={handleConfirm} disabled={loading} className="flex-1">
              {loading ? 'Saving…' : 'Confirm settled'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => { setConfirming(false); setNote(''); }}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
