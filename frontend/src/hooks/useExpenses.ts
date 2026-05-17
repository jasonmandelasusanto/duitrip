import { useEffect, useState } from 'react';
import { collection, onSnapshot, orderBy, query } from 'firebase/firestore';
import { db } from '../services/firebase';
import type { Expense } from '../types';

export function useExpenses(tripId: string | undefined) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!tripId) return;
    const q = query(
      collection(db, 'trips', tripId, 'expenses'),
      orderBy('createdAt', 'desc'),
    );
    const unsub = onSnapshot(q, (snap) => {
      setExpenses(snap.docs.map((d) => ({ ...d.data(), expenseId: d.id } as Expense)));
      setLoading(false);
    });
    return unsub;
  }, [tripId]);

  return { expenses, loading };
}
