import type { Trip } from '../../types';
import { Avatar } from '../ui/Avatar';
import { formatDateRange, tripDays } from '../../utils/date';
import { formatCurrency } from '../../utils/currency';
import { currencyFlag } from '../../utils/flag';
import { useAppStore } from '../../store/useAppStore';

interface TripHeaderProps {
  trip: Trip;
  totalSpend?: number;
  myShare?: number;
}

export function TripHeader({ trip, totalSpend, myShare }: TripHeaderProps) {
  const { user } = useAppStore();
  const visibleMembers = trip.members.slice(0, 5);
  const overflow = trip.members.length - 5;
  const flag = currencyFlag(trip.destinationCurrency);

  return (
    <div className="bg-bg-surface border border-bg-border rounded-2xl p-4 mb-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h1 className="text-lg font-bold text-text-primary">
            {flag && <span className="mr-1.5">{flag}</span>}
            {trip.name}
          </h1>
          <p className="text-sm text-text-secondary">{trip.destination}</p>
          <p className="text-xs text-text-muted mt-1">
            {formatDateRange(trip.startDate, trip.endDate)} · {tripDays(trip.startDate, trip.endDate)} days
          </p>
        </div>
        <span className="bg-amber/20 text-amber font-mono text-sm font-semibold px-2 py-1 rounded-lg">
          {trip.destinationCurrency}
        </span>
      </div>

      <div className="flex items-center gap-2 mt-3">
        <div className="flex -space-x-2">
          {visibleMembers.map((m) => (
            <Avatar
              key={m.userId || m.ghostId || m.displayName}
              src={m.photoURL}
              name={m.displayName}
              isGhost={m.role === 'ghost'}
              size="sm"
            />
          ))}
          {overflow > 0 && (
            <div className="w-7 h-7 rounded-full bg-bg-border flex items-center justify-center text-xs text-text-muted ring-2 ring-bg-base">
              +{overflow}
            </div>
          )}
        </div>
        <span className="text-xs text-text-muted">{trip.members.length} members</span>
      </div>

      {(totalSpend !== undefined || myShare !== undefined) && (
        <div className="mt-3 pt-3 border-t border-bg-border grid grid-cols-2 gap-3">
          {totalSpend !== undefined && (
            <div>
              <p className="text-xs text-text-muted">Total spent</p>
              <p className="text-sm font-semibold text-amber">
                {formatCurrency(totalSpend, trip.destinationCurrency)}
              </p>
            </div>
          )}
          {myShare !== undefined && user && (
            <div>
              <p className="text-xs text-text-muted">Your share</p>
              <p className="text-sm font-semibold text-text-primary">
                {formatCurrency(myShare, trip.destinationCurrency)}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
