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
  myShareHome?: number;
  myHomeCurrency?: string;
  budgetInDestCurrency?: number;
  onEdit?: () => void;
}

export function TripHeader({ trip, totalSpend, myShare, myShareHome, myHomeCurrency, budgetInDestCurrency, onEdit }: TripHeaderProps) {
  const { user } = useAppStore();
  const visibleMembers = trip.members.slice(0, 5);
  const overflow = trip.members.length - 5;
  const flag = currencyFlag(trip.destinationCurrency);

  return (
    <div className="bg-bg-surface border border-bg-border rounded-2xl p-4 mb-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-bold text-text-primary">
              {flag && <span className="mr-1.5">{flag}</span>}
              {trip.name}
            </h1>
            {onEdit && (
              <button onClick={onEdit} className="text-text-muted hover:text-teal transition-colors shrink-0" title="Edit trip">
                <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                </svg>
              </button>
            )}
          </div>
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
              {myShareHome !== undefined && myHomeCurrency && myHomeCurrency !== trip.destinationCurrency && myShareHome > 0 && (
                <p className="text-xs text-text-muted">≈ {formatCurrency(myShareHome, myHomeCurrency)}</p>
              )}
            </div>
          )}
        </div>
      )}

      {trip.budget && myShare !== undefined && (
        <div className="mt-3 pt-3 border-t border-bg-border">
          <div className="flex items-center justify-between text-xs mb-1.5">
            <span className="text-text-muted">Budget (per person)</span>
            <div className="text-right">
              {budgetInDestCurrency !== undefined ? (
                <>
                  <span className={`font-mono font-medium ${myShare > budgetInDestCurrency ? 'text-danger' : 'text-text-secondary'}`}>
                    {formatCurrency(myShare, trip.destinationCurrency)} / {formatCurrency(budgetInDestCurrency, trip.destinationCurrency)}
                  </span>
                  {trip.budgetCurrency && trip.budgetCurrency !== trip.destinationCurrency && (
                    <p className="text-text-muted">Budget: {formatCurrency(trip.budget, trip.budgetCurrency)}</p>
                  )}
                </>
              ) : (
                <span className="text-text-muted font-mono">
                  {formatCurrency(trip.budget, trip.budgetCurrency || trip.destinationCurrency)}
                </span>
              )}
            </div>
          </div>
          {budgetInDestCurrency !== undefined && (
            <div className="h-1.5 bg-bg-border rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${myShare > budgetInDestCurrency ? 'bg-danger' : myShare / budgetInDestCurrency > 0.8 ? 'bg-amber' : 'bg-teal'}`}
                style={{ width: `${Math.min(100, (myShare / budgetInDestCurrency) * 100).toFixed(1)}%` }}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
