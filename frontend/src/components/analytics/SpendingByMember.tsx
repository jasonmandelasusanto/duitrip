import { BarChart, Bar, XAxis, YAxis, Tooltip, ReferenceLine, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../../utils/currency';

interface SpendingByMemberProps {
  data: Array<{ userId: string; displayName: string; isGhost: boolean; totalPaid: number; percentage: number }>;
  currency: string;
  average?: number;
}

export function SpendingByMember({ data, currency, average }: SpendingByMemberProps) {
  const chartData = data.map((d) => ({
    ...d,
    name: d.displayName + (d.isGhost ? ' 👻' : ''),
  }));

  return (
    <ResponsiveContainer width="100%" height={Math.max(160, data.length * 48)}>
      <BarChart data={chartData} layout="vertical" margin={{ left: 60, right: 20 }}>
        <XAxis type="number" hide />
        <YAxis dataKey="name" type="category" tick={{ fill: '#94A3B8', fontSize: 12 }} axisLine={false} tickLine={false} width={60} />
        <Tooltip
          contentStyle={{ backgroundColor: '#111827', border: '1px solid #263348', borderRadius: '0.75rem' }}
          formatter={(value: number) => [formatCurrency(value, currency), 'Paid']}
        />
        {average && <ReferenceLine x={average} stroke="#F59E0B" strokeDasharray="4 2" />}
        <Bar dataKey="totalPaid" fill="#4DC3EA" radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
