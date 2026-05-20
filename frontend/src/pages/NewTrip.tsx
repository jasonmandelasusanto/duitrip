import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

interface NominatimResult {
  display_name: string;
  address: { country_code: string; country: string };
}

const COUNTRY_CURRENCY: Record<string, string> = {
  id: 'IDR', sg: 'SGD', th: 'THB', my: 'MYR', jp: 'JPY', kr: 'KRW', vn: 'VND',
  ph: 'PHP', au: 'AUD', us: 'USD', gb: 'GBP', de: 'EUR', fr: 'EUR', in: 'INR',
  cn: 'CNY', hk: 'HKD', tw: 'TWD', nz: 'NZD', ca: 'CAD',
};

export default function NewTrip() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [destination, setDestination] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [budget, setBudget] = useState('');
  const [budgetCurrency, setBudgetCurrency] = useState('');
  const [suggestions, setSuggestions] = useState<NominatimResult[]>([]);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<number | null>(null);

  useEffect(() => {
    if (destination.length < 2) { setSuggestions([]); return; }
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = window.setTimeout(async () => {
      try {
        const res = await fetch(
          `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(destination)}&format=json&addressdetails=1&limit=5&featuretype=city,state,country`,
          { headers: { 'User-Agent': 'Duitrip/1.0 (contact@duitrip.com)' } },
        );
        const data: NominatimResult[] = await res.json();
        setSuggestions(data.filter((d) => d.address?.country_code));
      } catch { /* ignore */ }
    }, 400);
  }, [destination]);

  function selectPlace(place: NominatimResult) {
    setDestination(place.display_name.split(',').slice(0, 2).join(',').trim());
    const cc = place.address.country_code?.toLowerCase();
    const resolved = COUNTRY_CURRENCY[cc] || 'USD';
    setCurrency(resolved);
    if (!budgetCurrency) setBudgetCurrency(resolved);
    setSuggestions([]);
  }

  async function handleCreate() {
    setLoading(true);
    try {
      const res = await api.post('/trips', { name, destination, startDate, endDate, destinationCurrency: currency, budget: budget ? parseFloat(budget) : null, budgetCurrency: budget ? (budgetCurrency || currency) : null });
      navigate(`/trips/${res.data.tripId}`);
    } finally {
      setLoading(false);
    }
  }

  const valid = name && destination && startDate && endDate;

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-2xl mx-auto px-4 pt-8 pb-10">
        <button onClick={() => navigate(-1)} className="text-text-secondary text-sm mb-6 hover:text-text-primary">← Back</button>
        <h1 className="text-2xl font-bold text-text-primary mb-6">New Trip</h1>

        <div className="flex flex-col gap-4">
          <Input label="Trip name" placeholder="Bali June 2026" value={name} onChange={(e) => setName(e.target.value)} />

          <div className="relative">
            <Input label="Destination" placeholder="Bali, Indonesia" value={destination} onChange={(e) => setDestination(e.target.value)} />
            {suggestions.length > 0 && (
              <div className="absolute top-full left-0 right-0 z-10 bg-bg-elevated border border-bg-border rounded-xl mt-1 overflow-hidden">
                {suggestions.map((s, i) => {
                  const cc = s.address.country_code?.toLowerCase();
                  const curr = COUNTRY_CURRENCY[cc] || 'USD';
                  return (
                    <button key={i} onClick={() => selectPlace(s)} className="w-full text-left px-4 py-3 hover:bg-bg-surface text-sm text-text-secondary border-b border-bg-border last:border-0">
                      📍 {s.display_name.split(',').slice(0, 2).join(',')} · {curr}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-sm text-text-secondary">Currency:</span>
            <input
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              maxLength={3}
              className="w-20 bg-bg-elevated border border-bg-border rounded-lg px-2 py-1 text-amber font-mono font-semibold text-sm focus:outline-none focus:border-teal"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          </div>

          <div>
            <label className="text-sm text-text-secondary block mb-1">
              Budget <span className="text-text-muted">(optional)</span>
            </label>
            <div className="flex gap-2">
              <input
                value={budgetCurrency || currency}
                onChange={(e) => setBudgetCurrency(e.target.value.toUpperCase())}
                maxLength={3}
                className="w-20 bg-bg-elevated border border-bg-border rounded-lg px-2 py-1 text-amber font-mono font-semibold text-sm focus:outline-none focus:border-teal"
              />
              <input
                type="number"
                placeholder="e.g. 5000"
                value={budget}
                onChange={(e) => setBudget(e.target.value)}
                className="flex-1 bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-text-primary focus:outline-none focus:border-teal font-mono text-sm"
              />
            </div>
          </div>

          <Button className="w-full mt-2" size="lg" onClick={handleCreate} disabled={!valid || loading}>
            {loading ? 'Creating…' : 'Create Trip'}
          </Button>
        </div>
      </div>
    </div>
  );
}
