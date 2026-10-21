import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyRow } from '../lib/types';
import { fillDates, formatDate, formatDay, num, rupiah } from '../lib/format';

/** Validated categorical pair (light surface): shuttlecock = orange, court = blue. */
export const SERIES = { shuttle: '#ea580c', court: '#3b5bdb' };
const AXIS = { fontSize: 11, fill: '#64748b' };

function shortDate(d: string) {
  return formatDate(d, { day: 'numeric', month: 'short' });
}

function compactRupiah(v: number) {
  if (v >= 1_000_000) return `${(v / 1_000_000).toLocaleString('id-ID', { maximumFractionDigits: 1 })} jt`;
  if (v >= 1000) return `${Math.round(v / 1000)} rb`;
  return String(v);
}

export function withEmptyDays(rows: DailyRow[], from: string, to: string): DailyRow[] {
  return fillDates<DailyRow>(rows, from, to, (date) => ({
    date, games: 0, uniquePlayers: 0, shuttlecocks: 0, shuttlecockCost: 0, courtCost: 0, courtSplitMethod: 'EQUAL_SPLIT', totalExpense: 0,
  }));
}

interface TipProps { active?: boolean; payload?: { name: string; value: number; color: string; dataKey: string }[]; label?: string; money?: boolean }
function ChartTooltip({ active, payload, label, money }: TipProps) {
  if (!active || !payload?.length || !label) return null;
  const total = payload.reduce((s, p) => s + (p.value ?? 0), 0);
  return (
    <div className="rounded-xl bg-white px-3 py-2 text-xs shadow-lg ring-1 ring-slate-200">
      <p className="mb-1 font-semibold text-slate-800">{formatDay(label)}</p>
      {payload.map((p) => (
        <p key={p.dataKey} className="flex items-center justify-between gap-4 text-slate-600">
          <span className="flex items-center gap-1.5"><span className="size-2 rounded-sm" style={{ background: p.color }} />{p.name}</span>
          <span className="tabular font-semibold text-slate-900">{money ? rupiah(p.value) : num(p.value)}</span>
        </p>
      ))}
      {payload.length > 1 && (
        <p className="mt-1 flex justify-between gap-4 border-t border-slate-100 pt-1 font-semibold text-slate-900">
          <span>Total</span><span className="tabular">{money ? rupiah(total) : num(total)}</span>
        </p>
      )}
    </div>
  );
}

/** Single-series count chart (games or shuttlecocks per day). The card title names the series. */
export function CountChart({ data, dataKey, name, height = 200 }: { data: DailyRow[]; dataKey: 'games' | 'shuttlecocks'; name: string; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 4, left: -24, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#eef2f6" />
        <XAxis dataKey="date" tickFormatter={shortDate} tick={AXIS} tickLine={false} axisLine={{ stroke: '#cbd5e1' }} minTickGap={16} />
        <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(15,27,61,0.05)' }} />
        <Bar isAnimationActive={false} dataKey={dataKey} name={name} fill={SERIES.shuttle} radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Stacked expense per day: shuttlecock cost + court cost = total daily expense. */
export function ExpenseChart({ data, height = 220 }: { data: DailyRow[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 4, left: -12, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#eef2f6" />
        <XAxis dataKey="date" tickFormatter={shortDate} tick={AXIS} tickLine={false} axisLine={{ stroke: '#cbd5e1' }} minTickGap={16} />
        <YAxis tickFormatter={compactRupiah} tick={AXIS} tickLine={false} axisLine={false} />
        <Tooltip content={<ChartTooltip money />} cursor={{ fill: 'rgba(15,27,61,0.05)' }} />
        <Legend iconType="square" iconSize={10} wrapperStyle={{ fontSize: 12, color: '#475569' }} />
        <Bar isAnimationActive={false} dataKey="shuttlecockCost" name="Biaya kok" stackId="x" fill={SERIES.shuttle} stroke="#fff" strokeWidth={1} maxBarSize={28} />
        <Bar isAnimationActive={false} dataKey="courtCost" name="Biaya lapangan" stackId="x" fill={SERIES.court} stroke="#fff" strokeWidth={1} radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  );
}
