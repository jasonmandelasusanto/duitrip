import { useEffect, useState } from 'react';
import { collection, onSnapshot, orderBy, query } from 'firebase/firestore';
import { db } from '../services/firebase';
import type { Settlement } from '../types';

export function useSettlements(tripId: string | undefined) {
  const [settlements, setSettlements] = useState<Settlement[]>([]);

  useEffect(() => {
    if (!tripId) return;
    const q = query(
      collection(db, 'trips', tripId, 'settlements'),
      orderBy('settledAt', 'desc'),
    );
    return onSnapshot(q, (snap) => {
      setSettlements(snap.docs.map((d) => ({ ...d.data(), settlementId: d.id } as Settlement)));
    });
  }, [tripId]);

  return { settlements };
}
