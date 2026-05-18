import { BarChart, Bar, XAxis, YAxis, Tooltip, Cell, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../../utils/currency';

interface SpendingByDayProps {
  data: Array<{ date: string; amount: number; expenseCount: number }>;
  currency: string;
}

export function SpendingByDay({ data, currency }: SpendingByDayProps) {
  const maxDay = data.reduce((max, d) => (d.amount > max.amount ? d : max), data[0] || { amount: 0 });

  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={data} margin={{ top: 5, right: 5, left: 5, bottom: 5 }}>
        <XAxis
          dataKey="date"
          tickFormatter={(v) => new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
          tick={{ fill: '#94A3B8', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis hide />
        <Tooltip
          contentStyle={{ backgroundColor: '#111827', border: '1px solid #263348', borderRadius: '0.75rem', color: '#F1F5F9' }}
          labelStyle={{ color: '#94A3B8' }}
          itemStyle={{ color: '#F1F5F9' }}
          formatter={(value: number, _: string, props: { payload?: { expenseCount?: number } }) => [
            `${formatCurrency(value, currency)} (${props.payload?.expenseCount ?? 0} expenses)`,
            null,
          ]}
          labelFormatter={(v) => new Date(v).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
        />
        <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
          {data.map((d, i) => (
            <Cell key={i} fill={d.date === maxDay?.date ? '#F59E0B' : '#0EA5E9'} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
