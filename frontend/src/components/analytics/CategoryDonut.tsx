import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../../utils/currency';

interface CategoryDonutProps {
  data: Array<{ category: string; emoji: string; amount: number; percentage: number }>;
  currency: string;
  totalLabel?: string;
  total?: number;
}

const COLORS = ['#4DC3EA', '#F59E0B', '#10B981', '#EF4444', '#8B5CF6', '#EC4899', '#14B8A6', '#F97316'];

export function CategoryDonut({ data, currency, total }: CategoryDonutProps) {
  return (
    <div>
      <ResponsiveContainer width="100%" height={240}>
        <PieChart>
          <Pie data={data} dataKey="amount" nameKey="category" cx="50%" cy="50%" innerRadius={60} outerRadius={100}>
            {data.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{ backgroundColor: '#111827', border: '1px solid #263348', borderRadius: '0.75rem', color: '#F1F5F9' }}
            labelStyle={{ color: '#94A3B8' }}
            itemStyle={{ color: '#F1F5F9' }}
            formatter={(value: number, name: string) => [formatCurrency(value, currency), name || null]}
          />
        </PieChart>
      </ResponsiveContainer>
      {total !== undefined && (
        <p className="text-center text-sm text-text-secondary -mt-2 mb-2">
          Total: <span className="text-amber font-semibold">{formatCurrency(total, currency)}</span>
        </p>
      )}
      <div className="flex flex-col gap-1.5 mt-2">
        {data.map((d, i) => (
          <div key={d.category} className="flex items-center gap-2 text-sm">
            <span className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
            <span className="text-lg leading-none">{d.emoji}</span>
            <span className="flex-1 text-text-secondary">{d.category}</span>
            <span className="font-mono text-amber text-xs">{formatCurrency(d.amount, currency)}</span>
            <span className="text-text-muted text-xs w-10 text-right">{d.percentage.toFixed(1)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}
