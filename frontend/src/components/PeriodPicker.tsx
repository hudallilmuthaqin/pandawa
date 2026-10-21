import { useState } from 'react';
import { Button, Input, Segmented } from './ui';
import type { PeriodPreset, PeriodValue } from '../lib/types';
import { todayLocal } from '../lib/format';

const options: { value: PeriodPreset; label: string }[] = [
  { value: 'TODAY', label: 'Hari Ini' },
  { value: 'WEEK', label: 'Minggu Ini' },
  { value: 'MONTH', label: 'Bulan Ini' },
  { value: 'CUSTOM', label: 'Custom' },
];

/** Period presets + custom range with an explicit "Terapkan" button and validation. */
export function PeriodPicker({ value, onChange }: { value: PeriodValue; onChange: (v: PeriodValue) => void }) {
  const [preset, setPreset] = useState<PeriodPreset>(value.period);
  const [from, setFrom] = useState(value.from ?? todayLocal().slice(0, 8) + '01');
  const [to, setTo] = useState(value.to ?? todayLocal());
  const invalid = !from || !to || to < from;

  return (
    <div className="space-y-2">
      <Segmented value={preset} options={options} onChange={(p) => {
        setPreset(p);
        if (p !== 'CUSTOM') onChange({ period: p });
      }} />
      {preset === 'CUSTOM' && (
        <div className="grid grid-cols-2 gap-2 rounded-2xl bg-white p-3 ring-1 ring-slate-200 sm:flex sm:items-end">
          <label className="block min-w-0">
            <span className="mb-1 block text-xs font-medium text-slate-500">Tanggal mulai</span>
            <Input type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="block min-w-0">
            <span className="mb-1 block text-xs font-medium text-slate-500">Tanggal akhir</span>
            <Input type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </label>
          <div className="col-span-2 sm:col-span-1">
            <Button block disabled={invalid} onClick={() => onChange({ period: 'CUSTOM', from, to })}>Terapkan</Button>
          </div>
          {to && from && to < from && <p className="col-span-2 text-xs text-red-600">Tanggal akhir tidak boleh lebih awal dari tanggal mulai.</p>}
        </div>
      )}
    </div>
  );
}
