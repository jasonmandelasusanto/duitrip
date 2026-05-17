import { useNavigate } from 'react-router-dom';
import { formatCurrency } from '../../utils/currency';

interface TimelineEntry {
  date: string;
  expenseId: string;
  description: string;
  myShare: number;
  category: string;
  emoji: string;
}

interface MyTimelineProps {
  entries: TimelineEntry[];
  currency: string;
  tripId: string;
}

export function MyTimeline({ entries, currency, tripId }: MyTimelineProps) {
  const navigate = useNavigate();
  const byDate = entries.reduce<Record<string, TimelineEntry[]>>((acc, e) => {
    if (!acc[e.date]) acc[e.date] = [];
    acc[e.date].push(e);
    return acc;
  }, {});

  if (entries.length === 0) return <p className="text-text-muted text-sm py-4 text-center">No expenses yet</p>;

  return (
    <div className="flex flex-col gap-4">
      {Object.entries(byDate).sort().map(([date, items]) => (
        <div key={date}>
          <p className="text-xs text-text-muted font-medium mb-2">
            {new Date(date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
          </p>
          <div className="flex flex-col gap-2">
            {items.map((item) => (
              <button
                key={item.expenseId}
                onClick={() => navigate(`/trips/${tripId}/expenses/${item.expenseId}`)}
                className="flex items-center gap-3 text-left bg-bg-surface border border-bg-border rounded-xl p-3 hover:border-teal/50 transition-colors"
              >
                <span className="text-lg">{item.emoji}</span>
                <span className="flex-1 text-sm text-text-primary">{item.description}</span>
                <span className="text-sm font-mono text-amber">{formatCurrency(item.myShare, currency)}</span>
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
