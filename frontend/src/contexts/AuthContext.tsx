import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { auth } from '../services/firebase';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';

const AuthContext = createContext<null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const { setUser, setAuthLoading } = useAppStore();

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (fbUser) => {
      if (fbUser) {
        try {
          const res = await api.get('/users/me');
          setUser({ ...res.data, uid: res.data.uid || fbUser.uid });
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
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return <AuthContext.Provider value={null}>{children}</AuthContext.Provider>;
}

export function useAuthContext() {
  return useContext(AuthContext);
}
