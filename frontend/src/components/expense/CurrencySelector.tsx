interface CurrencySelectorProps {
  value: string;
  onChange: (currency: string) => void;
  options: string[];
}

export function CurrencySelector({ value, onChange, options }: CurrencySelectorProps) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="bg-bg-elevated border border-bg-border rounded-xl px-3 py-2 text-text-primary focus:outline-none focus:border-teal text-sm"
    >
      {[...new Set([value, ...options])].map((c) => (
        <option key={c} value={c}>{c}</option>
      ))}
    </select>
  );
}
