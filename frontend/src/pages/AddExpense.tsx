import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import api from '../services/api';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { CurrencySelector } from '../components/expense/CurrencySelector';
import { DEFAULT_CATEGORIES } from '../types';
import { formatCurrency } from '../utils/currency';
import { useExchangeRates } from '../hooks/useExchangeRates';
import { useAppStore } from '../store/useAppStore';

type SplitMode = 'equal' | 'percentage' | 'exact';

export default function AddExpense() {
  const { tripId, expenseId } = useParams<{ tripId: string; expenseId?: string }>();
  const isEdit = !!expenseId;
  const { trip } = useTrip(tripId);
  const { user } = useAppStore();
  const navigate = useNavigate();
  const { rates, fetchRates } = useExchangeRates();

  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('Food & Drink');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('');
  const [paidBy, setPaidBy] = useState('');
  const [splitMode, setSplitMode] = useState<SplitMode>('equal');
  const [percentages, setPercentages] = useState<Record<string, string>>({});
  const [exactAmounts, setExactAmounts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<number | null>(null);

  // Seed form when editing
  useEffect(() => {
    if (!isEdit || !trip || !expenseId) return;
    api.get(`/trips/${tripId}/expenses/${expenseId}`).then((r) => {
      const e = r.data;
      setDescription(e.description);
      setCategory(e.category);
      setAmount(String(e.originalAmount));
      setCurrency(e.originalCurrency);
      setPaidBy(e.paidBy);
      setSplitMode(e.splitMode);
      if (e.splitMode === 'percentage') {
        const pct: Record<string, string> = {};
        e.splits.forEach((s: { userId: string; percentage?: number }) => {
          pct[s.userId] = String(s.percentage ?? 0);
        });
        setPercentages(pct);
      } else if (e.splitMode === 'exact') {
        const ex: Record<string, string> = {};
        e.splits.forEach((s: { userId: string; amountInDestinationCurrency?: number }) => {
          ex[s.userId] = String(s.amountInDestinationCurrency ?? 0);
        });
        setExactAmounts(ex);
      }
    }).catch(console.error);
  }, [isEdit, expenseId, tripId, trip]);

  useEffect(() => {
    if (trip && !isEdit) {
      setCurrency(trip.destinationCurrency);
      setPaidBy(user?.uid || '');
    }
  }, [trip, user, isEdit]);

  useEffect(() => {
    if (trip && currency && currency !== trip.destinationCurrency) {
      const home_currencies = trip.members.map((m) => m.homeCurrency).filter(Boolean) as string[];
      fetchRates(trip.destinationCurrency, [currency, ...home_currencies]);
    }
  }, [currency, trip, fetchRates]);

  useEffect(() => {
    if (!amount || !trip) { setPreview(null); return; }
    const n = parseFloat(amount);
    if (isNaN(n)) { setPreview(null); return; }
    if (currency === trip.destinationCurrency) { setPreview(n); return; }
    const rate = rates[currency];
    if (rate && rate > 0) setPreview(n / rate);
    else setPreview(null);
  }, [amount, currency, rates, trip]);

  const members = trip?.members || [];
  const currencyOptions = [
    trip?.destinationCurrency || 'USD',
    ...members.map((m) => m.homeCurrency).filter(Boolean) as string[],
  ];
  const allCategories = [
    ...DEFAULT_CATEGORIES,
    ...(trip?.customCategories || []).map((c) => ({ name: c.name, emoji: c.emoji })),
  ];

  const totalPct = Object.values(percentages).reduce((s, v) => s + (parseFloat(v) || 0), 0);

  async function handleSubmit() {
    if (!tripId || !trip) return;
    setLoading(true);
    try {
      let splits: Array<{ userId: string; percentage?: number; exactAmount?: number; exactCurrency?: string }> = [];

      if (splitMode === 'equal') {
        splits = members.map((m) => ({ userId: (m.userId || m.ghostId)! }));
      } else if (splitMode === 'percentage') {
        splits = members.map((m) => ({
          userId: (m.userId || m.ghostId)!,
          percentage: parseFloat(percentages[(m.userId || m.ghostId)!] || '0'),
        }));
      } else {
        splits = members.map((m) => ({
          userId: (m.userId || m.ghostId)!,
          exactAmount: parseFloat(exactAmounts[(m.userId || m.ghostId)!] || '0'),
          exactCurrency: trip.destinationCurrency,
        }));
      }

      const payload = {
        description,
        category,
        originalAmount: parseFloat(amount),
        originalCurrency: currency,
        paidBy,
        splitMode,
        splits,
      };

      if (isEdit && expenseId) {
        await api.patch(`/trips/${tripId}/expenses/${expenseId}`, payload);
      } else {
        await api.post(`/trips/${tripId}/expenses`, payload);
      }
      navigate(`/trips/${tripId}`);
    } finally {
      setLoading(false);
    }
  }

  if (!trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const valid = description && amount && parseFloat(amount) > 0 && paidBy &&
    (splitMode !== 'percentage' || Math.abs(totalPct - 100) < 0.01);

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-6 pb-10">
        <button onClick={() => navigate(-1)} className="text-text-secondary text-sm mb-4 hover:text-text-primary">← Back</button>
        <h1 className="text-2xl font-bold text-text-primary mb-6">{isEdit ? 'Edit Expense' : 'Add Expense'}</h1>

        <div className="flex flex-col gap-4">
          <Input label="Description" placeholder="Dinner at Jimbaran" value={description} onChange={(e) => setDescription(e.target.value)} />

          <div>
            <label className="text-sm text-text-secondary block mb-2">Category</label>
            <div className="flex flex-wrap gap-2">
              {allCategories.map((c) => (
                <button
                  key={c.name}
                  onClick={() => setCategory(c.name)}
                  className={`px-3 py-1.5 rounded-full text-sm transition-colors ${
                    category === c.name ? 'bg-amber/20 text-amber border border-amber/50' : 'bg-bg-surface border border-bg-border text-text-muted hover:border-teal/50'
                  }`}
                >
                  {c.emoji} {c.name}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-1">Amount</label>
            <div className="flex gap-2">
              <CurrencySelector value={currency} onChange={setCurrency} options={currencyOptions} />
              <input
                type="number"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="flex-1 bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-text-primary focus:outline-none focus:border-teal text-lg font-mono"
              />
            </div>
            {preview !== null && currency !== trip.destinationCurrency && (
              <p className="text-xs text-text-muted mt-1">
                ≈ {formatCurrency(preview, trip.destinationCurrency)} {trip.destinationCurrency}
              </p>
            )}
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-1">Paid by</label>
            <select
              value={paidBy}
              onChange={(e) => setPaidBy(e.target.value)}
              className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-text-primary focus:outline-none focus:border-teal text-sm"
            >
              {members.map((m) => {
                const uid = (m.userId || m.ghostId)!;
                return <option key={uid} value={uid}>{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</option>;
              })}
            </select>
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-2">Split</label>
            <div className="flex gap-2 mb-3">
              {(['equal', 'percentage', 'exact'] as SplitMode[]).map((mode) => (
                <button
                  key={mode}
                  onClick={() => setSplitMode(mode)}
                  className={`flex-1 py-2 rounded-xl text-sm font-medium capitalize transition-colors ${
                    splitMode === mode ? 'bg-teal text-white' : 'bg-bg-surface border border-bg-border text-text-secondary'
                  }`}
                >
                  {mode}
                </button>
              ))}
            </div>

            {splitMode === 'equal' && (
              <div className="flex flex-col gap-1">
                {members.map((m) => {
                  const uid = (m.userId || m.ghostId)!;
                  const share = amount ? parseFloat(amount) / members.length : 0;
                  return (
                    <div key={uid} className="flex items-center justify-between text-sm bg-bg-surface border border-bg-border rounded-lg px-3 py-2">
                      <span className="text-text-secondary">{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</span>
                      <span className="text-amber font-mono">{isNaN(share) ? '—' : formatCurrency(share, currency)}</span>
                    </div>
                  );
                })}
              </div>
            )}

            {splitMode === 'percentage' && (
              <div className="flex flex-col gap-2">
                {members.map((m) => {
                  const uid = (m.userId || m.ghostId)!;
                  return (
                    <div key={uid} className="flex items-center gap-2">
                      <span className="text-sm text-text-secondary flex-1">{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</span>
                      <input
                        type="number"
                        value={percentages[uid] || ''}
                        onChange={(e) => setPercentages({ ...percentages, [uid]: e.target.value })}
                        placeholder="0"
                        min={0} max={100}
                        className="w-20 bg-bg-elevated border border-bg-border rounded-lg px-2 py-1 text-sm text-amber font-mono text-right focus:outline-none focus:border-teal"
                      />
                      <span className="text-text-muted text-sm">%</span>
                    </div>
                  );
                })}
                <p className={`text-xs font-medium ${Math.abs(totalPct - 100) < 0.01 ? 'text-success' : 'text-warning'}`}>
                  Total: {totalPct.toFixed(1)}% {Math.abs(totalPct - 100) < 0.01 ? '✓' : `(${(100 - totalPct).toFixed(1)} remaining)`}
                </p>
              </div>
            )}

            {splitMode === 'exact' && (
              <div className="flex flex-col gap-2">
                {members.map((m) => {
                  const uid = (m.userId || m.ghostId)!;
                  return (
                    <div key={uid} className="flex items-center gap-2">
                      <span className="text-sm text-text-secondary flex-1">{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</span>
                      <input
                        type="number"
                        value={exactAmounts[uid] || ''}
                        onChange={(e) => setExactAmounts({ ...exactAmounts, [uid]: e.target.value })}
                        placeholder="0.00"
                        className="w-24 bg-bg-elevated border border-bg-border rounded-lg px-2 py-1 text-sm text-amber font-mono text-right focus:outline-none focus:border-teal"
                      />
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <Button className="w-full mt-2" size="lg" onClick={handleSubmit} disabled={!valid || loading}>
            {loading ? 'Saving…' : isEdit ? 'Save Changes' : 'Add Expense'}
          </Button>
        </div>
      </div>
    </div>
  );
}
