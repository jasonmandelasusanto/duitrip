import { describe, it, expect } from 'vitest';
import { formatCurrency, convertAmount } from './currency';

describe('formatCurrency', () => {
  it('formats USD with 2 decimal places', () => {
    expect(formatCurrency(100, 'USD')).toBe('$100.00');
  });

  it('formats SGD with 2 decimal places', () => {
    expect(formatCurrency(50.5, 'SGD')).toBe('S$50.50');
  });

  it('formats IDR with 0 decimal places', () => {
    expect(formatCurrency(150000, 'IDR')).toBe('Rp150,000');
  });

  it('formats JPY with 0 decimal places', () => {
    expect(formatCurrency(1000, 'JPY')).toBe('¥1,000');
  });

  it('formats KRW with 0 decimal places', () => {
    expect(formatCurrency(5000, 'KRW')).toBe('₩5,000');
  });

  it('falls back gracefully for unknown currency code', () => {
    expect(formatCurrency(42, 'XYZ')).toBe('XYZ 42.00');
  });

  it('handles zero', () => {
    expect(formatCurrency(0, 'USD')).toBe('$0.00');
  });

  it('handles negative amounts', () => {
    expect(formatCurrency(-25, 'USD')).toBe('-$25.00');
  });
});

describe('convertAmount', () => {
  it('returns same amount for identical currencies', () => {
    expect(convertAmount(100, 'SGD', 'SGD', { SGD: 1 })).toBe(100);
  });

  it('converts SGD to IDR using rates', () => {
    // rates relative to some base: SGD=1, IDR=11000 means 1 base = 11000 IDR, 1 base = 1 SGD
    const rates = { SGD: 1, IDR: 11000 };
    const result = convertAmount(1, 'SGD', 'IDR', rates);
    expect(result).toBeCloseTo(11000, 0);
  });

  it('converts IDR to SGD', () => {
    const rates = { SGD: 1, IDR: 11000 };
    const result = convertAmount(11000, 'IDR', 'SGD', rates);
    expect(result).toBeCloseTo(1, 4);
  });

  it('defaults rate to 1 when currency missing from rates map', () => {
    expect(convertAmount(100, 'USD', 'MYR', {})).toBe(100);
  });
});
