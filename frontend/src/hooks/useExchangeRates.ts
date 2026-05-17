import { useState, useCallback } from 'react';
import api from '../services/api';

const cache: Record<string, { rates: Record<string, number>; ts: number }> = {};
const TTL = 3600_000;

export function useExchangeRates() {
  const [rates, setRates] = useState<Record<string, number>>({});

  const fetchRates = useCallback(async (base: string, symbols: string[]) => {
    const key = `${base}:${symbols.sort().join(',')}`;
    if (cache[key] && Date.now() - cache[key].ts < TTL) {
      setRates(cache[key].rates);
      return cache[key].rates;
    }
    try {
      const res = await api.get('/exchange-rates', { params: { base, symbols: symbols.join(',') } });
      const r = res.data.rates as Record<string, number>;
      cache[key] = { rates: r, ts: Date.now() };
      setRates(r);
      return r;
    } catch {
      return {};
    }
  }, []);

  return { rates, fetchRates };
}
