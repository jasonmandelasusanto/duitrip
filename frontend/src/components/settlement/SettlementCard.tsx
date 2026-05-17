import type { SettlementTransaction } from '../../types';
import { formatCurrency } from '../../utils/currency';
import { Button } from '../ui/Button';

interface SettlementCardProps {
  transaction: SettlementTransaction;
  onMarkSettled?: () => void;
}

export function SettlementCard({ transaction: tx, onMarkSettled }: SettlementCardProps) {
  const isGhostTx = tx.from.isGhost || tx.to.isGhost;

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
        {onMarkSettled && (
          <Button size="sm" variant="primary" onClick={onMarkSettled}>
            Mark settled
          </Button>
        )}
      </div>
    </div>
  );
}
