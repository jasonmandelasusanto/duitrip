import { useEffect } from 'react';
import { onAuthStateChanged, type User as FirebaseUser } from 'firebase/auth';
import { auth } from '../services/firebase';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';

export function useAuth() {
  const { setUser, setAuthLoading } = useAppStore();

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (fbUser: FirebaseUser | null) => {
      if (fbUser) {
        try {
          const res = await api.get('/users/me');
          setUser(res.data);
        } catch {
          setUser({
            uid: fbUser.uid,
            email: fbUser.email || '',
            displayName: fbUser.displayName || '',
            photoURL: fbUser.photoURL,
            homeCurrency: 'USD',
          });
        }
      } else {
        setUser(null);
      }
      setAuthLoading(false);
    });
    return unsub;
  }, [setUser, setAuthLoading]);
}
