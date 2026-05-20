import { describe, it, expect } from 'vitest';
import { formatCurrency, convertAmount } from './currency';

describe('formatCurrency', () => {
  it('formats USD with 2 decimal places', () => {
    expect(formatCurrency(100, 'USD')).toBe('$100.00');
  });

  it('formats SGD with 2 decimal places', () => {
    // Symbol varies by ICU locale data — test decimal precision, not the symbol
    const result = formatCurrency(50.5, 'SGD');
    expect(result).toContain('50.50');
  });

  it('formats IDR with 0 decimal places', () => {
    const result = formatCurrency(150000, 'IDR');
    expect(result).toContain('150,000');
    expect(result).not.toMatch(/\d+\.\d{2}/); // no decimal digits
  });

  it('formats JPY with 0 decimal places', () => {
    const result = formatCurrency(1000, 'JPY');
    expect(result).toContain('1,000');
    expect(result).not.toMatch(/\d+\.\d{2}/);
  });

  it('formats KRW with 0 decimal places', () => {
    const result = formatCurrency(5000, 'KRW');
    expect(result).toContain('5,000');
    expect(result).not.toMatch(/\d+\.\d{2}/);
  });

  it('falls back gracefully for unknown currency code', () => {
    // Unknown currency may throw in Intl — the catch block returns "${currency} ${amount.toFixed(2)}"
    // but Intl may also succeed with a non-breaking space, so test contains rather than strict equality
    const result = formatCurrency(42, 'XYZ');
    expect(result).toContain('XYZ');
    expect(result).toContain('42');
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
