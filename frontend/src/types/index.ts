export interface User {
  uid: string;
  email: string;
  displayName: string;
  photoURL?: string | null;
  homeCurrency: string;
}

export interface TripMember {
  userId: string | null;
  email: string | null;
  displayName: string;
  photoURL?: string | null;
  homeCurrency: string;
  role: 'owner' | 'member' | 'ghost';
  joinedAt?: string | null;
  ghostId?: string | null;
}

export interface TripInvite {
  email: string;
  invitedBy: string;
  status: 'pending' | 'accepted' | 'declined';
}

export interface CustomCategory {
  id: string;
  name: string;
  emoji: string;
  createdBy: string;
}

export interface Trip {
  tripId: string;
  name: string;
  destination: string;
  destinationCurrency: string;
  startDate: string;
  endDate: string;
  createdBy: string;
  members: TripMember[];
  invites: TripInvite[];
  customCategories: CustomCategory[];
  status: 'active' | 'settled' | 'archived';
  createdAt: string;
  updatedAt: string;
}

export interface SplitEntry {
  userId: string;
  percentage: number;
  amountInDestinationCurrency: number;
  amountInHomeCurrency: number;
  homeCurrency: string;
}

export interface MemberStatus {
  userId: string;
  displayName: string;
  isGhost: boolean;
  isPayer: boolean;
  amountInDestinationCurrency: number;
  amountInHomeCurrency: number;
  homeCurrency: string;
  status: 'paid' | 'settled' | 'outstanding';
}

export interface Expense {
  expenseId: string;
  description: string;
  category: string;
  originalAmount: number;
  originalCurrency: string;
  destinationCurrency: string;
  amountInDestinationCurrency: number;
  exchangeRateUsed: number;
  exchangeRateTimestamp: string;
  exchangeRates: Record<string, number>;
  splitMode: 'equal' | 'percentage' | 'exact';
  paidBy: string;
  splits: SplitEntry[];
  receiptUrl?: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  memberStatuses?: MemberStatus[];
}

export interface Settlement {
  settlementId: string;
  fromUserId: string;
  toUserId: string;
  amountInDestinationCurrency: number;
  destinationCurrency: string;
  note?: string | null;
  settledAt: string;
  createdBy: string;
}

export interface SettlementTransaction {
  from: { userId: string; displayName: string; isGhost: boolean };
  to: { userId: string; displayName: string; isGhost: boolean };
  amountInDestinationCurrency: number;
  destinationCurrency: string;
  amountInFromHomeCurrency: number;
  fromHomeCurrency: string;
  amountInToHomeCurrency: number;
  toHomeCurrency: string;
}

export interface BalanceMember {
  userId: string;
  displayName: string;
  isGhost: boolean;
  amount: number;
  amountInTheirCurrency: number;
  theirCurrency: string;
  status: 'outstanding' | 'settled';
}

export const DEFAULT_CATEGORIES = [
  { name: 'Flight', emoji: '✈️' },
  { name: 'Accommodation', emoji: '🏨' },
  { name: 'Food & Drink', emoji: '🍽️' },
  { name: 'Transport', emoji: '🚗' },
  { name: 'Tour & Activities', emoji: '🎟️' },
  { name: 'Entertainment', emoji: '🎉' },
  { name: 'Shopping', emoji: '🛍️' },
  { name: 'Gift', emoji: '🎁' },
  { name: 'Health & Medical', emoji: '💊' },
  { name: 'Communication', emoji: '📱' },
  { name: 'Other', emoji: '📌' },
];
