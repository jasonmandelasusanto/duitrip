import { useState } from 'react';
import type { Expense } from '../../types';
import { formatCurrency } from '../../utils/currency';
import { formatTimestamp } from '../../utils/date';
import { useAppStore } from '../../store/useAppStore';
import { DEFAULT_CATEGORIES } from '../../types';

const categoryEmoji = Object.fromEntries(DEFAULT_CATEGORIES.map((c) => [c.name, c.emoji]));

interface ExpenseCardProps {
  expense: Expense;
  destinationCurrency: string;
  members: Array<{ userId: string | null; ghostId?: string | null; displayName: string }>;
}

export function ExpenseCard({ expense, destinationCurrency, members }: ExpenseCardProps) {
  const [expanded, setExpanded] = useState(false);
  const { user } = useAppStore();

  const payer = members.find((m) => (m.userId || m.ghostId) === expense.paidBy);
  const myMemberId = user?.uid;
  const mySplit = expense.splits.find((s) => s.userId === myMemberId);
  const emoji = categoryEmoji[expense.category] || '🏷️';

  return (
    <div
      className="bg-bg-surface border border-bg-border rounded-xl p-4 cursor-pointer hover:border-bg-elevated transition-colors"
      onClick={() => setExpanded(!expanded)}
    >
      <div className="flex items-start gap-3">
        <span className="text-2xl leading-none mt-0.5">{emoji}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="font-medium text-text-primary truncate">{expense.description}</p>
              <p className="text-xs text-text-muted mt-0.5">
                Paid by {payer?.displayName || expense.paidBy}
                {expense.createdBy !== expense.paidBy && ' · logged by someone else'}
              </p>
            </div>
            <div className="text-right shrink-0">
              <p className="font-semibold text-amber font-mono">
                {expense.originalCurrency !== destinationCurrency
                  ? `${expense.originalCurrency} ${expense.originalAmount.toLocaleString()}`
                  : formatCurrency(expense.amountInDestinationCurrency, destinationCurrency)}
              </p>
              {expense.originalCurrency !== destinationCurrency && (
                <p className="text-xs text-text-muted">
                  ≈ {formatCurrency(expense.amountInDestinationCurrency, destinationCurrency)}
                </p>
              )}
              {mySplit && (
                <p className="text-xs text-text-secondary mt-0.5">
                  Your share: {formatCurrency(mySplit.amountInDestinationCurrency, destinationCurrency)}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {expanded && (
        <div className="mt-3 pt-3 border-t border-bg-border">
          <p className="text-xs text-text-muted mb-2">
            @ {expense.exchangeRateUsed.toFixed(6)} {expense.originalCurrency}/{destinationCurrency} · {formatTimestamp(expense.exchangeRateTimestamp)}
          </p>
          <div className="flex flex-col gap-1">
            {(expense.memberStatuses || []).map((ms) => (
              <div key={ms.userId} className="flex items-center gap-2 text-sm">
                <span className={ms.status === 'outstanding' ? 'text-amber' : 'text-success'}>
                  {ms.status === 'paid' || ms.status === 'settled' ? '✓' : '⏳'}
                </span>
                <span className="text-text-primary flex-1">
                  {ms.displayName} {ms.isGhost ? '👻' : ''}
                </span>
                <span className="text-text-secondary font-mono text-xs">
                  {formatCurrency(ms.amountInDestinationCurrency, destinationCurrency)}
                </span>
                <span className="text-xs text-text-muted capitalize">{ms.status}</span>
              </div>
            ))}
            {(!expense.memberStatuses || expense.memberStatuses.length === 0) && expense.splits.map((sp) => (
              <div key={sp.userId} className="flex items-center gap-2 text-sm">
                <span className="text-text-secondary flex-1">{sp.userId}</span>
                <span className="font-mono text-xs text-amber">
                  {formatCurrency(sp.amountInDestinationCurrency, destinationCurrency)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
