import { create } from 'zustand';
import type { User, Trip } from '../types';

interface AppState {
  user: User | null;
  authLoading: boolean;
  trips: Trip[];
  setUser: (user: User | null) => void;
  setAuthLoading: (loading: boolean) => void;
  setTrips: (trips: Trip[]) => void;
}

export const useAppStore = create<AppState>((set) => ({
  user: null,
  authLoading: true,
  trips: [],
  setUser: (user) => set({ user }),
  setAuthLoading: (authLoading) => set({ authLoading }),
  setTrips: (trips) => set({ trips }),
}));
