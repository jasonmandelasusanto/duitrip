import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Expense, ExpenseComment } from '../../types';
import { formatCurrency } from '../../utils/currency';
import { formatTimestamp } from '../../utils/date';
import { useAppStore } from '../../store/useAppStore';
import { DEFAULT_CATEGORIES } from '../../types';
import { ConfirmDialog } from '../ui/ConfirmDialog';

const categoryEmoji = Object.fromEntries(DEFAULT_CATEGORIES.map((c) => [c.name, c.emoji]));

interface ExpenseCardProps {
  expense: Expense;
  tripId: string;
  destinationCurrency: string;
  members: Array<{ userId: string | null; ghostId?: string | null; displayName: string }>;
  onDeleted?: () => void;
}

export function ExpenseCard({ expense, tripId, destinationCurrency, members, onDeleted }: ExpenseCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [comments, setComments] = useState<ExpenseComment[]>(expense.comments || []);
  const [commentText, setCommentText] = useState('');
  const [commentLoading, setCommentLoading] = useState(false);
  const [receiptExpanded, setReceiptExpanded] = useState(false);
  const { user } = useAppStore();
  const navigate = useNavigate();

  const payer = members.find((m) => (m.userId || m.ghostId) === expense.paidBy);
  const myMemberId = user?.uid;
  const mySplit = expense.splits.find((s) => s.userId === myMemberId);
  const emoji = categoryEmoji[expense.category] || '🏷️';
  const canEdit = expense.createdBy === user?.uid;

  async function handleDelete() {
    setShowDeleteConfirm(false);
    const { default: api } = await import('../../services/api');
    await api.delete(`/trips/${tripId}/expenses/${expense.expenseId}`);
    onDeleted?.();
  }

  function handleEdit(e: React.MouseEvent) {
    e.stopPropagation();
    navigate(`/trips/${tripId}/expenses/${expense.expenseId}/edit`);
  }

  function handleDuplicate(e: React.MouseEvent) {
    e.stopPropagation();
    navigate(`/trips/${tripId}/expenses/new`, {
      state: {
        description: expense.description,
        category: expense.category,
        amount: String(expense.originalAmount),
        currency: expense.originalCurrency,
        splitMode: expense.splitMode,
        notes: expense.notes || '',
      },
    });
  }

  async function handleAddComment(e: React.FormEvent) {
    e.preventDefault();
    if (!commentText.trim() || !user) return;
    setCommentLoading(true);
    try {
      const { default: api } = await import('../../services/api');
      const res = await api.post(`/trips/${tripId}/expenses/${expense.expenseId}/comments`, {
        text: commentText.trim(),
      });
      setComments((prev) => [...prev, res.data]);
      setCommentText('');
    } finally {
      setCommentLoading(false);
    }
  }

  async function handleDeleteComment(commentId: string) {
    const { default: api } = await import('../../services/api');
    await api.delete(`/trips/${tripId}/expenses/${expense.expenseId}/comments/${commentId}`);
    setComments((prev) => prev.filter((c) => c.id !== commentId));
  }

  return (
    <>
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
                {mySplit && mySplit.homeCurrency !== destinationCurrency && mySplit.amountInHomeCurrency > 0 && (
                  <p className="text-xs text-text-muted">
                    ≈ {formatCurrency(mySplit.amountInHomeCurrency, mySplit.homeCurrency)}
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>

        {expanded && (
          <div className="mt-3 pt-3 border-t border-bg-border" onClick={(e) => e.stopPropagation()}>
            <p className="text-xs text-text-muted mb-2">
              {expense.originalCurrency !== destinationCurrency
                ? `@ ${expense.exchangeRateUsed.toFixed(4)} ${expense.originalCurrency}/${destinationCurrency} · `
                : ''}
              {formatTimestamp(expense.exchangeRateTimestamp)}
            </p>
            {expense.notes && (
              <p className="text-xs text-text-secondary italic mb-3">"{expense.notes}"</p>
            )}

            {/* Receipt */}
            {expense.receiptUrl && (
              <div className="mb-3">
                {receiptExpanded ? (
                  <div>
                    <img
                      src={expense.receiptUrl}
                      alt="Receipt"
                      className="w-full rounded-xl border border-bg-border object-contain max-h-72"
                    />
                    <button
                      onClick={() => setReceiptExpanded(false)}
                      className="text-xs text-text-muted mt-1 hover:text-text-secondary"
                    >
                      Hide receipt
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => setReceiptExpanded(true)}
                    className="flex items-center gap-1.5 text-xs text-teal hover:underline"
                  >
                    📷 View receipt
                  </button>
                )}
              </div>
            )}

            <div className="flex flex-col gap-1 mb-3">
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
              {(!expense.memberStatuses || expense.memberStatuses.length === 0) && expense.splits.map((sp) => {
                const member = members.find((m) => (m.userId || m.ghostId) === sp.userId);
                return (
                  <div key={sp.userId} className="flex items-center gap-2 text-sm">
                    <span className="text-text-secondary flex-1">{member?.displayName || sp.userId}</span>
                    <span className="font-mono text-xs text-amber">
                      {formatCurrency(sp.amountInDestinationCurrency, destinationCurrency)}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Comments */}
            <div className="border-t border-bg-border pt-3 mb-3">
              <p className="text-xs text-text-muted font-medium mb-2">Comments</p>
              {comments.length === 0 && (
                <p className="text-xs text-text-muted italic mb-2">No comments yet.</p>
              )}
              <div className="flex flex-col gap-2 mb-2">
                {comments.map((c) => (
                  <div key={c.id} className="flex items-start gap-2">
                    <div className="flex-1 min-w-0">
                      <span className="text-xs font-medium text-text-secondary">{c.displayName} </span>
                      <span className="text-xs text-text-primary">{c.text}</span>
                    </div>
                    {c.userId === user?.uid && (
                      <button
                        onClick={() => handleDeleteComment(c.id)}
                        className="text-xs text-text-muted hover:text-danger shrink-0"
                      >
                        ✕
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {user && (
                <form onSubmit={handleAddComment} className="flex gap-2">
                  <input
                    type="text"
                    value={commentText}
                    onChange={(e) => setCommentText(e.target.value)}
                    placeholder="Add a comment…"
                    className="flex-1 bg-bg-base border border-bg-border rounded-lg px-2 py-1 text-xs text-text-primary placeholder-text-muted focus:outline-none focus:border-teal"
                  />
                  <button
                    type="submit"
                    disabled={!commentText.trim() || commentLoading}
                    className="text-xs text-teal font-medium disabled:opacity-50 hover:underline"
                  >
                    Post
                  </button>
                </form>
              )}
            </div>

            {/* Actions */}
            <div className="flex gap-3 pt-2 border-t border-bg-border">
              <button onClick={handleDuplicate} className="text-xs text-text-secondary hover:underline font-medium">Duplicate</button>
              {canEdit && (
                <>
                  <button onClick={handleEdit} className="text-xs text-teal hover:underline font-medium">Edit</button>
                  <button
                    onClick={(e) => { e.stopPropagation(); setShowDeleteConfirm(true); }}
                    className="text-xs text-danger hover:underline font-medium"
                  >Delete</button>
                </>
              )}
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={showDeleteConfirm}
        title="Delete expense?"
        message="This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteConfirm(false)}
      />
    </>
  );
}
