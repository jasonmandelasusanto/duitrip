import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import { useExpenses } from '../hooks/useExpenses';
import { useSettlements } from '../hooks/useSettlements';
import { useAppStore } from '../store/useAppStore';
import { useExchangeRates } from '../hooks/useExchangeRates';
import { TripHeader } from '../components/trip/TripHeader';
import { ExpenseCard } from '../components/expense/ExpenseCard';
import { Button } from '../components/ui/Button';
import { formatCurrency } from '../utils/currency';
import { exportExpensesCSV, exportSummaryCSV } from '../utils/export';
import { DEFAULT_CATEGORIES } from '../types';
import type { Expense, Settlement, TripMember } from '../types';

function computeMyBalance(
  expenses: Expense[],
  settlements: Settlement[],
  myUid: string,
  members: TripMember[],
) {
  // Net balance per uid (destination currency): positive = owed money, negative = owes
  const net: Record<string, number> = {};
  for (const exp of expenses) {
    net[exp.paidBy] = (net[exp.paidBy] || 0) + exp.amountInDestinationCurrency;
    for (const sp of exp.splits) {
      net[sp.userId] = (net[sp.userId] || 0) - sp.amountInDestinationCurrency;
    }
  }
  for (const s of settlements) {
    net[s.fromUserId] = (net[s.fromUserId] || 0) + s.amountInDestinationCurrency;
    net[s.toUserId] = (net[s.toUserId] || 0) - s.amountInDestinationCurrency;
  }

  // Bilateral pair-wise flows between me and each other member
  const bilateral: Record<string, number> = {};
  for (const exp of expenses) {
    for (const sp of exp.splits) {
      if (sp.userId === exp.paidBy) continue;
      if (exp.paidBy === myUid) {
        // sp.userId owes me their share
        bilateral[sp.userId] = (bilateral[sp.userId] || 0) + sp.amountInDestinationCurrency;
      } else if (sp.userId === myUid) {
        // I owe exp.paidBy my share
        bilateral[exp.paidBy] = (bilateral[exp.paidBy] || 0) - sp.amountInDestinationCurrency;
      }
    }
  }
  for (const s of settlements) {
    if (s.toUserId === myUid) {
      bilateral[s.fromUserId] = (bilateral[s.fromUserId] || 0) - s.amountInDestinationCurrency;
    } else if (s.fromUserId === myUid) {
      bilateral[s.toUserId] = (bilateral[s.toUserId] || 0) + s.amountInDestinationCurrency;
    }
  }

  const memberMap = Object.fromEntries(members.map((m) => [(m.userId || m.ghostId)!, m]));
  const owedToMeBy = Object.entries(bilateral)
    .filter(([, v]) => v > 0.01)
    .map(([uid, amount]) => ({
      userId: uid,
      displayName: memberMap[uid]?.displayName || uid,
      isGhost: memberMap[uid]?.role === 'ghost',
      amount: Math.round(amount * 100) / 100,
    }));

  return {
    myBalance: {
      netBalance: Math.round((net[myUid] || 0) * 100) / 100,
      totalOwedToMe: owedToMeBy.reduce((s, e) => s + e.amount, 0),
      totalIOwe: Object.values(bilateral).filter((v) => v < -0.01).reduce((s, v) => s - v, 0),
      owedToMeBy,
    },
  };
}

export default function TripDetail() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip, loading } = useTrip(tripId);
  const { expenses } = useExpenses(tripId);
  const { settlements } = useSettlements(tripId);
  const { user } = useAppStore();
  const navigate = useNavigate();
  const { rates, fetchRates } = useExchangeRates();

  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [payerFilter, setPayerFilter] = useState('');
  const [showExportMenu, setShowExportMenu] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteInput, setDeleteInput] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editName, setEditName] = useState('');
  const [editStartDate, setEditStartDate] = useState('');
  const [editEndDate, setEditEndDate] = useState('');
  const [editBudget, setEditBudget] = useState('');
  const [editBudgetCurrency, setEditBudgetCurrency] = useState('');
  const [saving, setSaving] = useState(false);
  const [sortOrder, setSortOrder] = useState<'desc' | 'asc'>('desc');

  const isOwner = trip?.members.some((m) => m.userId === user?.uid && m.role === 'owner');

  function openEditModal() {
    if (!trip) return;
    setEditName(trip.name);
    setEditStartDate(trip.startDate);
    setEditEndDate(trip.endDate);
    setEditBudget(trip.budget != null ? String(trip.budget) : '');
    setEditBudgetCurrency(trip.budgetCurrency || trip.destinationCurrency);
    setShowEditModal(true);
  }

  async function handleSaveTrip() {
    if (!tripId || !editName.trim() || !editStartDate || !editEndDate) return;
    setSaving(true);
    try {
      const budgetVal = editBudget.trim() === '' ? null : parseFloat(editBudget);
      await import('../services/api').then(({ default: api }) =>
        api.patch(`/trips/${tripId}`, {
          name: editName.trim(),
          startDate: editStartDate,
          endDate: editEndDate,
          budget: budgetVal,
          ...(budgetVal != null && { budgetCurrency: editBudgetCurrency.toUpperCase() }),
          ...(budgetVal == null && { budgetCurrency: null }),
        }),
      );
      setShowEditModal(false);
    } catch {
      alert('Failed to save changes. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  async function handlePermanentDelete() {
    if (!tripId || deleteInput !== 'delete') return;
    setDeleting(true);
    try {
      await import('../services/api').then(({ default: api }) =>
        api.delete(`/trips/${tripId}/permanent`),
      );
      navigate('/dashboard');
    } catch {
      alert('Failed to delete trip. Please try again.');
      setDeleting(false);
    }
  }

  const totalSpend = useMemo(
    () => expenses.reduce((s, e) => s + e.amountInDestinationCurrency, 0),
    [expenses],
  );
  const myShare = useMemo(() => {
    if (!user) return 0;
    return expenses.reduce((s, e) => {
      const sp = e.splits.find((x) => x.userId === user.uid);
      return s + (sp?.amountInDestinationCurrency || 0);
    }, 0);
  }, [expenses, user]);
  const myShareHome = useMemo(() => {
    if (!user) return 0;
    return expenses.reduce((s, e) => {
      const sp = e.splits.find((x) => x.userId === user.uid);
      return s + (sp?.amountInHomeCurrency || 0);
    }, 0);
  }, [expenses, user]);

  const balance = useMemo(() => {
    if (!user || !trip) return null;
    return computeMyBalance(expenses, settlements, user.uid, trip.members);
  }, [expenses, settlements, user, trip]);

  // Fetch exchange rate when budget is in a different currency than the destination
  useEffect(() => {
    if (trip?.budget && trip.budgetCurrency && trip.budgetCurrency !== trip.destinationCurrency) {
      fetchRates(trip.destinationCurrency, [trip.budgetCurrency]);
    }
  }, [trip?.budget, trip?.budgetCurrency, trip?.destinationCurrency, fetchRates]);

  // Budget converted to destination currency for progress bar
  const budgetInDestCurrency = useMemo(() => {
    if (!trip?.budget) return undefined;
    const bc = trip.budgetCurrency || trip.destinationCurrency;
    if (bc === trip.destinationCurrency) return trip.budget;
    // rates[budgetCurrency] = how many budgetCurrency units per 1 destinationCurrency
    const rate = rates[bc];
    if (!rate) return undefined;
    return trip.budget / rate;
  }, [trip?.budget, trip?.budgetCurrency, trip?.destinationCurrency, rates]);

  const filteredExpenses = useMemo(() => {
    const filtered = expenses.filter((e) => {
      if (search && !e.description.toLowerCase().includes(search.toLowerCase())) return false;
      if (categoryFilter && e.category !== categoryFilter) return false;
      if (payerFilter && e.paidBy !== payerFilter) return false;
      return true;
    });
    return [...filtered].sort((a, b) => {
      const da = new Date(a.createdAt).getTime();
      const db = new Date(b.createdAt).getTime();
      if (da !== db) return sortOrder === 'desc' ? db - da : da - db;
      // Same logical date: use updatedAt (wall-clock entry time) as tiebreaker
      const ua = new Date(a.updatedAt).getTime();
      const ub = new Date(b.updatedAt).getTime();
      return sortOrder === 'desc' ? ub - ua : ua - ub;
    });
  }, [expenses, search, categoryFilter, payerFilter, sortOrder]);

  if (loading || !trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const myNet = balance?.myBalance.netBalance ?? 0;

  const tabs = [
    { label: 'Expenses', path: `/trips/${tripId}` },
    { label: 'Analytics', path: `/trips/${tripId}/analytics` },
    { label: 'Members', path: `/trips/${tripId}/members` },
    { label: 'Settle Up', path: `/trips/${tripId}/settlement` },
  ];

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Mobile layout */}
      <div className="lg:hidden max-w-lg mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate('/dashboard')} className="text-text-secondary text-sm mb-4 hover:text-text-primary">← Dashboard</button>
        <TripHeader trip={trip} totalSpend={totalSpend} myShare={myShare} myShareHome={myShareHome} myHomeCurrency={user?.homeCurrency} budgetInDestCurrency={budgetInDestCurrency} onEdit={isOwner ? openEditModal : undefined} />
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {tabs.map((tab) => (
            <Link key={tab.label} to={tab.path}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${tab.label === 'Expenses' ? 'bg-teal text-white' : tab.label === 'Settle Up' ? 'text-amber hover:bg-bg-elevated' : 'text-text-secondary hover:bg-bg-elevated'}`}
            >{tab.label}</Link>
          ))}
        </div>
        <BalanceCard balance={balance} myNet={myNet} trip={trip} formatCurrency={formatCurrency} />
        <FiltersAndList
          search={search} setSearch={setSearch}
          categoryFilter={categoryFilter} setCategoryFilter={setCategoryFilter}
          payerFilter={payerFilter} setPayerFilter={setPayerFilter}
          showExportMenu={showExportMenu} setShowExportMenu={setShowExportMenu}
          sortOrder={sortOrder} setSortOrder={setSortOrder}
          expenses={expenses} filteredExpenses={filteredExpenses}
          trip={trip} tripId={tripId!}
          exportExpensesCSV={exportExpensesCSV} exportSummaryCSV={exportSummaryCSV}
        />
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2">
          <Link to={`/trips/${tripId}/expenses/new`}><Button size="lg" className="shadow-lg shadow-teal/20">+ Add Expense</Button></Link>
        </div>
        {isOwner && (
          <div className="mt-6 pt-6 border-t border-bg-border">
            <p className="text-xs text-text-muted font-semibold uppercase tracking-wider mb-3">Danger Zone</p>
            <button onClick={() => { setDeleteInput(''); setShowDeleteModal(true); }}
              className="w-full py-2.5 rounded-xl border border-danger/40 text-danger text-sm font-medium hover:bg-danger/10 transition-colors">
              Delete Trip
            </button>
          </div>
        )}
      </div>

      {/* Edit trip modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="bg-bg-surface border border-bg-border rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <h2 className="text-lg font-bold text-text-primary mb-4">Edit Trip</h2>
            <div className="flex flex-col gap-3">
              <div>
                <label className="block text-xs text-text-muted mb-1">Trip name</label>
                <input type="text" value={editName} onChange={(e) => setEditName(e.target.value)}
                  className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-teal" />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-xs text-text-muted mb-1">Start date</label>
                  <input type="date" value={editStartDate} onChange={(e) => setEditStartDate(e.target.value)}
                    className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-teal" />
                </div>
                <div>
                  <label className="block text-xs text-text-muted mb-1">End date</label>
                  <input type="date" value={editEndDate} onChange={(e) => setEditEndDate(e.target.value)}
                    className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-teal" />
                </div>
              </div>
              <div>
                <label className="block text-xs text-text-muted mb-1">Budget per person <span className="text-text-muted">(leave empty to remove)</span></label>
                <div className="flex gap-2">
                  <input type="number" min="0" value={editBudget} onChange={(e) => setEditBudget(e.target.value)}
                    placeholder="0"
                    className="flex-1 bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-teal" />
                  <input type="text" value={editBudgetCurrency} onChange={(e) => setEditBudgetCurrency(e.target.value.toUpperCase().slice(0, 3))}
                    placeholder="SGD"
                    className="w-20 bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary font-mono text-center focus:outline-none focus:border-teal" />
                </div>
              </div>
            </div>
            <div className="flex gap-2 mt-5">
              <button onClick={() => setShowEditModal(false)} disabled={saving}
                className="flex-1 py-2.5 rounded-xl border border-bg-border text-text-secondary text-sm hover:bg-bg-elevated transition-colors">
                Cancel
              </button>
              <button onClick={handleSaveTrip} disabled={!editName.trim() || !editStartDate || !editEndDate || saving}
                className="flex-1 py-2.5 rounded-xl bg-teal text-white text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-teal/90 transition-colors">
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete confirmation modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="bg-bg-surface border border-bg-border rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <h2 className="text-lg font-bold text-text-primary mb-1">Delete Trip</h2>
            <p className="text-sm text-text-secondary mb-4">
              This will permanently delete <span className="font-semibold text-text-primary">{trip.name}</span> and all its expenses and settlements. This cannot be undone.
            </p>
            <p className="text-sm text-text-muted mb-2">Type <span className="font-mono font-bold text-danger">delete</span> to confirm:</p>
            <input
              type="text"
              value={deleteInput}
              onChange={(e) => setDeleteInput(e.target.value)}
              placeholder="delete"
              className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none focus:border-danger mb-4"
              autoFocus
            />
            <div className="flex gap-2">
              <button onClick={() => setShowDeleteModal(false)} disabled={deleting}
                className="flex-1 py-2.5 rounded-xl border border-bg-border text-text-secondary text-sm hover:bg-bg-elevated transition-colors">
                Cancel
              </button>
              <button onClick={handlePermanentDelete} disabled={deleteInput !== 'delete' || deleting}
                className="flex-1 py-2.5 rounded-xl bg-danger text-white text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-danger/90 transition-colors">
                {deleting ? 'Deleting…' : 'Delete Forever'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Desktop layout — left panel + right content */}
      <div className="hidden lg:flex min-h-screen">
        {/* Left panel: trip info + tabs + balance */}
        <div className="w-80 shrink-0 border-r border-bg-border bg-bg-surface flex flex-col sticky top-0 h-screen overflow-y-auto">
          <div className="p-5">
            <button onClick={() => navigate('/dashboard')} className="text-text-secondary text-sm mb-4 hover:text-text-primary block">← All Trips</button>
            <TripHeader trip={trip} totalSpend={totalSpend} myShare={myShare} myShareHome={myShareHome} myHomeCurrency={user?.homeCurrency} budgetInDestCurrency={budgetInDestCurrency} onEdit={isOwner ? openEditModal : undefined} />
          </div>
          {/* Vertical tabs */}
          <nav className="px-3 pb-3 flex flex-col gap-1">
            {tabs.map((tab) => (
              <Link key={tab.label} to={tab.path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${tab.label === 'Expenses' ? 'bg-teal text-white' : tab.label === 'Settle Up' ? 'text-amber hover:bg-amber/10' : 'text-text-secondary hover:bg-bg-elevated'}`}
              >
                <span>{tab.label === 'Expenses' ? '💸' : tab.label === 'Analytics' ? '📊' : tab.label === 'Members' ? '👥' : '💰'}</span>
                {tab.label}
              </Link>
            ))}
          </nav>
          {balance && (
            <div className="mx-3 mb-3">
              <BalanceCard balance={balance} myNet={myNet} trip={trip} formatCurrency={formatCurrency} />
            </div>
          )}
          <div className="mt-auto p-3 space-y-2 border-t border-bg-border">
            <Link to={`/trips/${tripId}/expenses/new`}><Button size="sm" className="w-full">+ Add Expense</Button></Link>
            {isOwner && (
              <button onClick={() => { setDeleteInput(''); setShowDeleteModal(true); }}
                className="w-full py-2 rounded-xl border border-danger/40 text-danger text-xs font-medium hover:bg-danger/10 transition-colors">
                Delete Trip
              </button>
            )}
          </div>
        </div>

        {/* Right panel: expense list */}
        <div className="flex-1 px-6 pt-6 pb-10 overflow-y-auto">
          <FiltersAndList
            search={search} setSearch={setSearch}
            categoryFilter={categoryFilter} setCategoryFilter={setCategoryFilter}
            payerFilter={payerFilter} setPayerFilter={setPayerFilter}
            showExportMenu={showExportMenu} setShowExportMenu={setShowExportMenu}
            sortOrder={sortOrder} setSortOrder={setSortOrder}
            expenses={expenses} filteredExpenses={filteredExpenses}
            trip={trip} tripId={tripId!}
            exportExpensesCSV={exportExpensesCSV} exportSummaryCSV={exportSummaryCSV}
          />
        </div>
      </div>
    </div>
  );
}

// ─── Extracted sub-components ────────────────────────────────────────────────

function BalanceCard({ balance, myNet, trip, formatCurrency }: {
  balance: ReturnType<typeof computeMyBalance> | null;
  myNet: number;
  trip: import('../types').Trip;
  formatCurrency: typeof import('../utils/currency').formatCurrency;
}) {
  if (!balance) return null;
  return (
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
  );
}

function FiltersAndList({ search, setSearch, categoryFilter, setCategoryFilter, payerFilter, setPayerFilter,
  showExportMenu, setShowExportMenu, sortOrder, setSortOrder, expenses, filteredExpenses, trip, tripId,
  exportExpensesCSV, exportSummaryCSV }: {
  search: string; setSearch: (v: string) => void;
  categoryFilter: string; setCategoryFilter: (v: string) => void;
  payerFilter: string; setPayerFilter: (v: string) => void;
  showExportMenu: boolean; setShowExportMenu: (v: boolean) => void;
  sortOrder: 'desc' | 'asc'; setSortOrder: (v: 'desc' | 'asc') => void;
  expenses: import('../types').Expense[];
  filteredExpenses: import('../types').Expense[];
  trip: import('../types').Trip;
  tripId: string;
  exportExpensesCSV: typeof import('../utils/export').exportExpensesCSV;
  exportSummaryCSV: typeof import('../utils/export').exportSummaryCSV;
}) {
  return (
    <>
      <div className="flex flex-col gap-2 mb-3">
        <div className="flex gap-2 items-center">
          <input type="search" placeholder="Search expenses…" value={search} onChange={(e) => setSearch(e.target.value)}
            className="flex-1 bg-bg-surface border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none focus:border-teal"
          />
          <button onClick={() => setSortOrder(sortOrder === 'desc' ? 'asc' : 'desc')}
            title={sortOrder === 'desc' ? 'Oldest first' : 'Newest first'}
            className="px-3 py-2 bg-bg-surface border border-bg-border rounded-xl text-sm text-text-secondary hover:border-teal/50 transition-colors">
            {sortOrder === 'desc' ? '↓ Date' : '↑ Date'}
          </button>
          <div className="relative">
            <button onClick={() => setShowExportMenu(!showExportMenu)}
              className="px-3 py-2 bg-bg-surface border border-bg-border rounded-xl text-sm text-text-secondary hover:border-teal/50 transition-colors">
              Export
            </button>
            {showExportMenu && (
              <div className="absolute right-0 top-full mt-1 bg-bg-elevated border border-bg-border rounded-xl overflow-hidden z-10 min-w-[160px]">
                <button onClick={() => { exportExpensesCSV(expenses, trip.members, trip.name, trip.destinationCurrency); setShowExportMenu(false); }}
                  className="w-full text-left px-4 py-3 text-sm text-text-secondary hover:bg-bg-surface border-b border-bg-border">Expenses CSV</button>
                <button onClick={() => { exportSummaryCSV(expenses, trip.members, trip.name, trip.destinationCurrency); setShowExportMenu(false); }}
                  className="w-full text-left px-4 py-3 text-sm text-text-secondary hover:bg-bg-surface">Summary CSV</button>
              </div>
            )}
          </div>
        </div>
        <div className="flex gap-1.5 overflow-x-auto pb-0.5 scrollbar-hide">
          <button onClick={() => setCategoryFilter('')}
            className={`shrink-0 px-2.5 py-1 rounded-full text-xs transition-colors ${!categoryFilter ? 'bg-teal text-white' : 'bg-bg-surface border border-bg-border text-text-muted'}`}>All</button>
          {DEFAULT_CATEGORIES.map((c) => (
            <button key={c.name} onClick={() => setCategoryFilter(categoryFilter === c.name ? '' : c.name)}
              className={`shrink-0 px-2.5 py-1 rounded-full text-xs transition-colors ${categoryFilter === c.name ? 'bg-amber/20 text-amber border border-amber/50' : 'bg-bg-surface border border-bg-border text-text-muted'}`}>
              {c.emoji} {c.name}
            </button>
          ))}
        </div>
        <select value={payerFilter} onChange={(e) => setPayerFilter(e.target.value)}
          className="bg-bg-surface border border-bg-border rounded-xl px-3 py-2 text-xs text-text-secondary focus:outline-none focus:border-teal">
          <option value="">All payers</option>
          {trip.members.map((m) => {
            const uid = (m.userId || m.ghostId)!;
            return <option key={uid} value={uid}>{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</option>;
          })}
        </select>
      </div>
      <div className="flex flex-col gap-3">
        {expenses.length === 0 && <p className="text-center text-text-muted py-8">No expenses yet. Add the first one!</p>}
        {expenses.length > 0 && filteredExpenses.length === 0 && <p className="text-center text-text-muted py-8">No expenses match your filters.</p>}
        {filteredExpenses.map((e) => (
          <ExpenseCard key={e.expenseId} expense={e} tripId={tripId} destinationCurrency={trip.destinationCurrency} members={trip.members} />
        ))}
      </div>
    </>
  );
}
