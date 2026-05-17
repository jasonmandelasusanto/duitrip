import { formatCurrency } from '../../utils/currency';

interface MyVsAverageProps {
  myShare: number;
  groupAverage: number;
  currency: string;
}

export function MyVsAverage({ myShare, groupAverage, currency }: MyVsAverageProps) {
  const diff = myShare - groupAverage;
  const isAbove = diff > 0;
  const maxVal = Math.max(myShare, groupAverage, 1);

  return (
    <div className="flex flex-col gap-3">
      <div>
        <div className="flex justify-between text-sm mb-1">
          <span className="text-text-secondary">My share</span>
          <span className="font-mono text-amber">{formatCurrency(myShare, currency)}</span>
        </div>
        <div className="h-3 bg-bg-border rounded-full overflow-hidden">
          <div
            className="h-full bg-teal rounded-full transition-all"
            style={{ width: `${(myShare / maxVal) * 100}%` }}
          />
        </div>
      </div>
      <div>
        <div className="flex justify-between text-sm mb-1">
          <span className="text-text-secondary">Group average</span>
          <span className="font-mono text-text-secondary">{formatCurrency(groupAverage, currency)}</span>
        </div>
        <div className="h-3 bg-bg-border rounded-full overflow-hidden">
          <div
            className="h-full bg-bg-border rounded-full transition-all border-2 border-amber"
            style={{ width: `${(groupAverage / maxVal) * 100}%` }}
          />
        </div>
      </div>
      <p className={`text-sm font-medium ${isAbove ? 'text-danger' : 'text-success'}`}>
        {isAbove ? '↑' : '↓'} {formatCurrency(Math.abs(diff), currency)} {isAbove ? 'above' : 'below'} average
      </p>
    </div>
  );
}
