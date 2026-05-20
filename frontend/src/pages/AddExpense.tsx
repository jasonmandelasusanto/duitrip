import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import { usePendingExpenses } from '../hooks/usePendingExpenses';
import api from '../services/api';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { CurrencySelector } from '../components/expense/CurrencySelector';
import { DEFAULT_CATEGORIES } from '../types';
import { formatCurrency } from '../utils/currency';
import { useExchangeRates } from '../hooks/useExchangeRates';
import { useAppStore } from '../store/useAppStore';
import { compressImage } from '../utils/imageCompress';

type SplitMode = 'equal' | 'percentage' | 'exact';

export default function AddExpense() {
  const { tripId, expenseId } = useParams<{ tripId: string; expenseId?: string }>();
  const location = useLocation();
  const isEdit = !!expenseId;
  const { trip } = useTrip(tripId);
  const { user } = useAppStore();
  const navigate = useNavigate();
  const { rates, fetchRates } = useExchangeRates();
  const { enqueue } = usePendingExpenses(tripId);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Pre-fill state (from duplicate via location.state)
  const prefill = (location.state as Record<string, unknown> | null) ?? {};

  const [description, setDescription] = useState((prefill.description as string) || '');
  const [category, setCategory] = useState((prefill.category as string) || 'Food & Drink');
  const [amount, setAmount] = useState((prefill.amount as string) || '');
  const [currency, setCurrency] = useState((prefill.currency as string) || '');
  const [paidBy, setPaidBy] = useState(user?.uid || '');
  const [notes, setNotes] = useState((prefill.notes as string) || '');
  const [splitMode, setSplitMode] = useState<SplitMode>((prefill.splitMode as SplitMode) || 'equal');
  const [percentages, setPercentages] = useState<Record<string, string>>({});
  const [exactAmounts, setExactAmounts] = useState<Record<string, string>>({});
  const [expenseDate, setExpenseDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<number | null>(null);
  const [receiptDataUrl, setReceiptDataUrl] = useState<string | null>(null);
  const [receiptLoading, setReceiptLoading] = useState(false);
  const [selectedMembers, setSelectedMembers] = useState<Set<string>>(new Set());
  const [customRateEnabled, setCustomRateEnabled] = useState(false);
  const [customRate, setCustomRate] = useState('');
  const [offline, setOffline] = useState(!navigator.onLine);

  useEffect(() => {
    const up = () => setOffline(false);
    const down = () => setOffline(true);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => { window.removeEventListener('online', up); window.removeEventListener('offline', down); };
  }, []);

  async function handleReceiptChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setReceiptLoading(true);
    try { setReceiptDataUrl(await compressImage(file)); } finally { setReceiptLoading(false); }
    e.target.value = '';
  }

  // Seed form when editing — deps exclude `trip` to avoid re-running on every Firestore snapshot
  useEffect(() => {
    if (!isEdit || !tripId || !expenseId) return;
    api.get(`/trips/${tripId}/expenses/${expenseId}`).then((r) => {
      const e = r.data;
      setDescription(e.description);
      setCategory(e.category);
      setAmount(String(e.originalAmount));
      setCurrency(e.originalCurrency);
      setPaidBy(e.paidBy);
      setNotes(e.notes || '');
      setSplitMode(e.splitMode);
      if (e.receiptUrl) setReceiptDataUrl(e.receiptUrl);
      setSelectedMembers(new Set((e.splits as { userId: string }[]).map((s) => s.userId)));
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
  }, [isEdit, expenseId, tripId]);

  useEffect(() => {
    if (trip && !isEdit) {
      setCurrency(trip.destinationCurrency);
      setSelectedMembers(new Set(trip.members.map((m) => (m.userId || m.ghostId)!)));
    }
  }, [trip, isEdit]);

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
  const currencyOptions = [...new Set([
    trip?.destinationCurrency || 'USD',
    // Use current profile homeCurrency for self; trip member record may be stale
    ...members.map((m) => {
      const uid = m.userId || m.ghostId;
      return uid === user?.uid ? (user?.homeCurrency || m.homeCurrency) : m.homeCurrency;
    }).filter(Boolean) as string[],
  ])];
  const allCategories = [
    ...DEFAULT_CATEGORIES,
    ...(trip?.customCategories || []).map((c) => ({ name: c.name, emoji: c.emoji })),
  ];

  const involvedMembers = members.filter((m) => selectedMembers.has((m.userId || m.ghostId)!));
  const totalPct = involvedMembers.reduce((s, m) => s + (parseFloat(percentages[(m.userId || m.ghostId)!] || '0') || 0), 0);

  async function handleSubmit() {
    if (!tripId || !trip) return;
    setLoading(true);
    try {
      let splits: Array<{ userId: string; percentage?: number; exactAmount?: number; exactCurrency?: string }> = [];
      const involvedMembers = members.filter((m) => selectedMembers.has((m.userId || m.ghostId)!));

      if (splitMode === 'equal') {
        splits = involvedMembers.map((m) => ({ userId: (m.userId || m.ghostId)! }));
      } else if (splitMode === 'percentage') {
        splits = involvedMembers.map((m) => ({
          userId: (m.userId || m.ghostId)!,
          percentage: parseFloat(percentages[(m.userId || m.ghostId)!] || '0'),
        }));
      } else {
        splits = involvedMembers.map((m) => ({
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
        notes: notes.trim() || null,
        receiptUrl: receiptDataUrl || null,
        customRate: customRateEnabled && customRate ? parseFloat(customRate) : null,
        expenseDate,
      };

      if (offline && !isEdit) {
        enqueue(payload as Record<string, unknown>, description);
        navigate(`/trips/${tripId}`);
        return;
      }

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

  const valid = description && amount && parseFloat(amount) > 0 && paidBy && selectedMembers.size > 0 &&
    (splitMode !== 'percentage' || Math.abs(totalPct - 100) < 0.01);

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-2xl mx-auto px-4 pt-6 pb-10">
        <button onClick={() => navigate(-1)} className="text-text-secondary text-sm mb-4 hover:text-text-primary">← Back</button>
        <h1 className="text-2xl font-bold text-text-primary mb-6">{isEdit ? 'Edit Expense' : 'Add Expense'}</h1>

        <div className="flex flex-col gap-4">
          <Input label="Description" placeholder="Dinner at Jimbaran" value={description} onChange={(e) => setDescription(e.target.value)} />

          <Input label="Date" type="date" value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} />

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

          {/* Custom rate */}
          {currency !== (trip?.destinationCurrency || '') && (
            <div>
              <label className="flex items-center gap-2 text-sm text-text-secondary mb-2 cursor-pointer select-none">
                <input type="checkbox" checked={customRateEnabled} onChange={(e) => setCustomRateEnabled(e.target.checked)}
                  className="accent-teal" />
                Use custom exchange rate
              </label>
              {customRateEnabled && (
                <div className="flex items-center gap-2">
                  <span className="text-xs text-text-muted">1 {currency} =</span>
                  <input
                    type="number"
                    placeholder="e.g. 15800"
                    value={customRate}
                    onChange={(e) => setCustomRate(e.target.value)}
                    className="flex-1 bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-amber font-mono focus:outline-none focus:border-teal"
                  />
                  <span className="text-xs text-text-muted">{trip?.destinationCurrency}</span>
                </div>
              )}
            </div>
          )}

          {/* Receipt */}
          <div>
            <label className="text-sm text-text-secondary block mb-1">Receipt <span className="text-text-muted">(optional)</span></label>
            {receiptDataUrl ? (
              <div className="relative inline-block">
                <img src={receiptDataUrl} alt="Receipt" className="h-28 rounded-xl object-cover border border-bg-border" />
                <button onClick={() => setReceiptDataUrl(null)}
                  className="absolute top-1 right-1 bg-bg-base/80 text-danger rounded-full w-5 h-5 flex items-center justify-center text-xs leading-none">✕</button>
              </div>
            ) : (
              <button onClick={() => fileInputRef.current?.click()} disabled={receiptLoading}
                className="flex items-center gap-2 px-4 py-2 border border-dashed border-bg-border rounded-xl text-sm text-text-muted hover:border-teal/50 transition-colors disabled:opacity-50">
                {receiptLoading ? 'Compressing…' : '📷 Attach receipt'}
              </button>
            )}
            <input ref={fileInputRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={handleReceiptChange} />
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-1">Notes <span className="text-text-muted">(optional)</span></label>
            <textarea
              placeholder="Any extra details…"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
              className="w-full bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none focus:border-teal transition-colors resize-none"
            />
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-2">Split between</label>
            <div className="flex flex-col gap-1">
              {members.map((m) => {
                const uid = (m.userId || m.ghostId)!;
                const checked = selectedMembers.has(uid);
                return (
                  <label key={uid} className={`flex items-center gap-3 border rounded-lg px-3 py-2 cursor-pointer transition-colors ${checked ? 'bg-teal/10 border-teal/40' : 'bg-bg-surface border-bg-border'}`}>
                    <input type="checkbox" checked={checked}
                      onChange={() => setSelectedMembers((prev) => { const next = new Set(prev); if (next.has(uid)) next.delete(uid); else next.add(uid); return next; })}
                      className="accent-teal" />
                    <span className="text-sm text-text-secondary">{m.displayName}{m.role === 'ghost' ? ' 👻' : ''}</span>
                  </label>
                );
              })}
            </div>
            {selectedMembers.size === 0 && <p className="text-xs text-warning mt-1">Select at least one member.</p>}
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
                {involvedMembers.map((m) => {
                  const uid = (m.userId || m.ghostId)!;
                  const share = amount && involvedMembers.length > 0 ? parseFloat(amount) / involvedMembers.length : 0;
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
                {involvedMembers.map((m) => {
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
                {involvedMembers.map((m) => {
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
