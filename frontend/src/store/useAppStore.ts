import { create } from 'zustand';
import type { User, Trip } from '../types';

interface AppState {
  user: User | null;
  trips: Trip[];
  setUser: (user: User | null) => void;
  setTrips: (trips: Trip[]) => void;
}

export const useAppStore = create<AppState>((set) => ({
  user: null,
  trips: [],
  setUser: (user) => set({ user }),
  setTrips: (trips) => set({ trips }),
}));
