import { useState, useEffect, useCallback } from 'react';
import api from '../services/api';

const KEY = 'duitrip_pending_expenses';

interface PendingExpense {
  id: string;
  tripId: string;
  payload: Record<string, unknown>;
  description: string;
  createdAt: string;
}

function load(): PendingExpense[] {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch { return []; }
}
function save(items: PendingExpense[]) {
  localStorage.setItem(KEY, JSON.stringify(items));
}

export function usePendingExpenses(tripId: string | undefined) {
  const [pending, setPending] = useState<PendingExpense[]>(() =>
    load().filter((p) => p.tripId === tripId),
  );
  const [syncing, setSyncing] = useState(false);

  const enqueue = useCallback((payload: Record<string, unknown>, description: string) => {
    const item: PendingExpense = {
      id: `pending_${Date.now()}`,
      tripId: tripId!,
      payload,
      description,
      createdAt: new Date().toISOString(),
    };
    const all = [...load(), item];
    save(all);
    setPending(all.filter((p) => p.tripId === tripId));
    return item.id;
  }, [tripId]);

  const flush = useCallback(async () => {
    const forTrip = load().filter((p) => p.tripId === tripId);
    if (!forTrip.length || syncing) return;
    setSyncing(true);
    const remaining: PendingExpense[] = [];
    for (const item of forTrip) {
      try {
        await api.post(`/trips/${item.tripId}/expenses`, item.payload);
      } catch {
        remaining.push(item);
      }
    }
    const other = load().filter((p) => p.tripId !== tripId);
    save([...other, ...remaining]);
    setPending(remaining);
    setSyncing(false);
  }, [tripId, syncing]);

  // Auto-flush when coming back online
  useEffect(() => {
    window.addEventListener('online', flush);
    return () => window.removeEventListener('online', flush);
  }, [flush]);

  // Attempt flush on mount if online
  useEffect(() => {
    if (navigator.onLine && load().filter((p) => p.tripId === tripId).length > 0) {
      flush();
    }
  }, [tripId, flush]);

  return { pending, syncing, enqueue, flush };
}
