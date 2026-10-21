import { describe, expect, it } from 'vitest';
import { duration, fillDates, rupiah, resolvePeriod } from '../format';

describe('format helpers', () => {
  it('formats rupiah without fractions', () => {
    expect(rupiah(16000)).toBe('Rp16.000');
    expect(rupiah(0)).toBe('Rp0');
  });

  it('formats durations as mm:ss or h:mm:ss', () => {
    expect(duration(65)).toBe('01:05');
    expect(duration(3725)).toBe('1:02:05');
    expect(duration(null)).toBe('-');
  });

  it('fills missing days with empty rows (inclusive range)', () => {
    const rows = fillDates([{ date: '2026-10-02', v: 5 }], '2026-10-01', '2026-10-03', (date) => ({ date, v: 0 }));
    expect(rows.map((r) => r.date)).toEqual(['2026-10-01', '2026-10-02', '2026-10-03']);
    expect(rows[1].v).toBe(5);
  });

  it('resolves a custom period as-is', () => {
    expect(resolvePeriod({ period: 'CUSTOM', from: '2026-10-01', to: '2026-10-12' })).toEqual({ from: '2026-10-01', to: '2026-10-12' });
  });

  it('week preset spans Monday to Sunday', () => {
    const { from, to } = resolvePeriod({ period: 'WEEK' });
    expect(new Date(from + 'T00:00:00Z').getUTCDay()).toBe(1);
    expect(new Date(to + 'T00:00:00Z').getUTCDay()).toBe(0);
  });
});
