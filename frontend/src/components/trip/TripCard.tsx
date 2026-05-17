import { Link } from 'react-router-dom';
import type { Trip } from '../../types';
import { formatDateRange, tripDays } from '../../utils/date';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';

interface TripCardProps {
  trip: Trip;
}

export function TripCard({ trip }: TripCardProps) {
  const realMembers = trip.members.filter((m) => m.role !== 'ghost');

  return (
    <Link to={`/trips/${trip.tripId}`} className="block bg-bg-surface border border-bg-border rounded-2xl p-4 hover:border-teal/50 transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-text-primary truncate">{trip.name}</h3>
          <p className="text-sm text-text-secondary mt-0.5">{trip.destination}</p>
          <p className="text-xs text-text-muted mt-1">
            {formatDateRange(trip.startDate, trip.endDate)} · {tripDays(trip.startDate, trip.endDate)} days
          </p>
        </div>
        <Badge variant="amber">{trip.destinationCurrency}</Badge>
      </div>
      <div className="flex items-center gap-2 mt-3">
        <div className="flex -space-x-2">
          {realMembers.slice(0, 4).map((m) => (
            <Avatar key={m.userId || m.ghostId || m.displayName} src={m.photoURL} name={m.displayName} size="sm" />
          ))}
          {realMembers.length > 4 && (
            <div className="w-7 h-7 rounded-full bg-bg-border flex items-center justify-center text-xs text-text-muted ring-2 ring-bg-base">
              +{realMembers.length - 4}
            </div>
          )}
        </div>
        <span className="text-xs text-text-muted">{trip.members.length} members</span>
      </div>
    </Link>
  );
}
