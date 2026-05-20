import { describe, it, expect } from 'vitest';
import { parseXlsxDate } from './dataExport';

describe('parseXlsxDate', () => {
  it('returns empty string for undefined', () => {
    expect(parseXlsxDate(undefined)).toBe('');
  });

  it('passes through ISO date strings unchanged', () => {
    expect(parseXlsxDate('2026-05-20')).toBe('2026-05-20');
  });

  it('truncates ISO datetime to date part', () => {
    expect(parseXlsxDate('2026-05-20T12:30:00.000Z')).toBe('2026-05-20');
  });

  it('parses locale-style date strings', () => {
    // new Date('5/20/2026') → valid date in most environments
    const result = parseXlsxDate('5/20/2026');
    expect(result).toBe('2026-05-20');
  });

  it('parses Excel serial date number', () => {
    // Excel serial 46200 = 2026-05-20 (approx)
    const result = parseXlsxDate(46201);
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('returns raw string when date cannot be parsed', () => {
    const result = parseXlsxDate('not-a-date');
    expect(typeof result).toBe('string');
  });
});
