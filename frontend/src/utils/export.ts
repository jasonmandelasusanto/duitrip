import type { Expense, TripMember } from '../types';
import { formatCurrency } from './currency';

function escapeCSV(v: unknown): string {
  const s = String(v ?? '');
  return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s.replace(/"/g, '""')}"` : s;
}

export function exportExpensesCSV(
  expenses: Expense[],
  members: TripMember[],
  tripName: string,
  currency: string,
) {
  const memberMap = Object.fromEntries(members.map((m) => [(m.userId || m.ghostId)!, m.displayName]));

  const headers = ['Date', 'Description', 'Category', 'Paid By', 'Original', 'Currency', `Amount (${currency})`, 'Split Mode', 'Notes'];
  const rows = expenses.map((e) => [
    e.createdAt ? new Date(e.createdAt).toLocaleDateString() : '',
    e.description,
    e.category,
    memberMap[e.paidBy] || e.paidBy,
    e.originalAmount,
    e.originalCurrency,
    e.amountInDestinationCurrency.toFixed(2),
    e.splitMode,
    e.notes || '',
  ]);

  const csv = [headers, ...rows].map((r) => r.map(escapeCSV).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${tripName.replace(/[^a-z0-9]/gi, '_')}_expenses.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

export function exportSummaryCSV(
  expenses: Expense[],
  members: TripMember[],
  tripName: string,
  currency: string,
) {
  const memberMap = Object.fromEntries(members.map((m) => [(m.userId || m.ghostId)!, m.displayName]));
  const totalsByMember: Record<string, { paid: number; owed: number }> = {};

  for (const e of expenses) {
    const payer = e.paidBy;
    if (!totalsByMember[payer]) totalsByMember[payer] = { paid: 0, owed: 0 };
    totalsByMember[payer].paid += e.amountInDestinationCurrency;
    for (const sp of e.splits) {
      if (!totalsByMember[sp.userId]) totalsByMember[sp.userId] = { paid: 0, owed: 0 };
      totalsByMember[sp.userId].owed += sp.amountInDestinationCurrency;
    }
  }

  const headers = ['Member', `Total Paid (${currency})`, `Total Owed (${currency})`, `Net (${currency})`];
  const rows = Object.entries(totalsByMember).map(([uid, { paid, owed }]) => [
    memberMap[uid] || uid,
    paid.toFixed(2),
    owed.toFixed(2),
    (paid - owed).toFixed(2),
  ]);

  const total = expenses.reduce((s, e) => s + e.amountInDestinationCurrency, 0);
  rows.push(['TOTAL', formatCurrency(total, currency), formatCurrency(total, currency), '0.00']);

  const csv = [headers, ...rows].map((r) => r.map(escapeCSV).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${tripName.replace(/[^a-z0-9]/gi, '_')}_summary.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}
