import { useState, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import { useExpenses } from '../hooks/useExpenses';
import { useAppStore } from '../store/useAppStore';
import { TripHeader } from '../components/trip/TripHeader';
import { ExpenseCard } from '../components/expense/ExpenseCard';
import { Button } from '../components/ui/Button';
import { formatCurrency } from '../utils/currency';
import api from '../services/api';

export default function TripDetail() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip, loading } = useTrip(tripId);
  const { expenses } = useExpenses(tripId);
  const { user } = useAppStore();
  const navigate = useNavigate();
  const [balance, setBalance] = useState<{ myBalance: { netBalance: number; totalOwedToMe: number; totalIOwe: number; owedToMeBy: Array<{ userId: string; displayName: string; isGhost: boolean; amount: number }> } } | null>(null);

  useEffect(() => {
    if (!tripId) return;
    api.get(`/trips/${tripId}/balance`).then((r) => setBalance(r.data)).catch(console.error);
  }, [tripId, expenses.length]);

  if (loading || !trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const totalSpend = expenses.reduce((s, e) => s + e.amountInDestinationCurrency, 0);
  const myShare = expenses.reduce((s, e) => {
    const sp = e.splits.find((x) => x.userId === user?.uid);
    return s + (sp?.amountInDestinationCurrency || 0);
  }, 0);
  const myNet = balance?.myBalance.netBalance ?? 0;

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate('/dashboard')} className="text-text-secondary text-sm mb-4 hover:text-text-primary">← Dashboard</button>

        <TripHeader trip={trip} totalSpend={totalSpend} myShare={myShare} />

        {/* Tab bar */}
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {[
            { label: 'Expenses', path: `/trips/${tripId}` },
            { label: 'Analytics', path: `/trips/${tripId}/analytics` },
            { label: 'Members', path: `/trips/${tripId}/members` },
            { label: 'Settle Up', path: `/trips/${tripId}/settlement` },
          ].map((tab) => (
            <Link
              key={tab.label}
              to={tab.path}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Expenses'
                  ? 'bg-teal text-white'
                  : tab.label === 'Settle Up'
                  ? 'text-amber hover:bg-bg-elevated'
                  : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >
              {tab.label}
            </Link>
          ))}
        </div>

        {/* Balance card */}
        {balance && (
          <div className="bg-bg-surface border border-bg-border rounded-xl p-4 mb-4">
            <p className="text-sm font-semibold text-text-primary mb-2">Your Balance</p>
            {Math.abs(myNet) < 0.01 ? (
              <p className="text-success text-sm">✓ All settled</p>
            ) : (
              <>
                <p className={`text-lg font-bold ${myNet > 0 ? 'text-success' : 'text-danger'}`}>
                  {myNet > 0 ? 'You are owed ' : 'You owe '}
                  {formatCurrency(Math.abs(myNet), trip.destinationCurrency)}
                </p>
                <div className="flex flex-col gap-1 mt-2">
                  {(balance.myBalance.owedToMeBy || []).map((b) => (
                    <p key={b.userId} className="text-xs text-text-secondary">
                      {b.displayName} {b.isGhost ? '👻 ' : ''}owes you {formatCurrency(b.amount, trip.destinationCurrency)} ⏳
                    </p>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* Expense list */}
        <div className="flex flex-col gap-3">
          {expenses.length === 0 && (
            <p className="text-center text-text-muted py-8">No expenses yet. Add the first one!</p>
          )}
          {expenses.map((e) => (
            <ExpenseCard key={e.expenseId} expense={e} destinationCurrency={trip.destinationCurrency} members={trip.members} />
          ))}
        </div>

        <div className="fixed bottom-6 left-1/2 -translate-x-1/2">
          <Link to={`/trips/${tripId}/expenses/new`}>
            <Button size="lg" className="shadow-lg shadow-teal/20">
              + Add Expense
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
