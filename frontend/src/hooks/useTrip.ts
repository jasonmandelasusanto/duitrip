import { useEffect, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../services/firebase';
import type { Trip } from '../types';

export function useTrip(tripId: string | undefined) {
  const [trip, setTrip] = useState<Trip | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!tripId) return;
    const unsub = onSnapshot(doc(db, 'trips', tripId), (snap) => {
      if (snap.exists()) {
        setTrip({ ...snap.data(), tripId: snap.id } as Trip);
      }
      setLoading(false);
    });
    return unsub;
  }, [tripId]);

  return { trip, loading };
}
