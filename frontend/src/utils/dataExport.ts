import * as XLSX from 'xlsx';
import type { Trip } from '../types';
import type { AxiosInstance } from 'axios';

export async function exportAllData(trips: Trip[], api: AxiosInstance): Promise<void> {
  if (trips.length === 0) throw new Error('No trips to export');

  const tripData = await Promise.all(
    trips.map(async (trip) => {
      const [expensesRes, settlementsRes] = await Promise.all([
        api.get(`/trips/${trip.tripId}/expenses`, { params: { limit: 9999 } }),
        api.get(`/trips/${trip.tripId}/settlements`),
      ]);
      return {
        trip,
        expenses: expensesRes.data as Record<string, unknown>[],
        settlements: settlementsRes.data as Record<string, unknown>[],
      };
    }),
  );

  // ── Expenses sheet ─────────────────────────────────────────────
  const expenseRows: unknown[][] = [
    ['Trip', 'Trip Currency', 'Date', 'Description', 'Category', 'Paid By',
     'Original Amount', 'Original Currency', 'Amount (trip currency)', 'Split Mode', 'Notes'],
  ];

  for (const { trip, expenses } of tripData) {
    const memberMap = Object.fromEntries(
      trip.members.map((m) => [(m.userId || m.ghostId)!, m.displayName]),
    );
    for (const e of expenses) {
      const rawDate =
        (e.expenseDate as string) ||
        (e.createdAt ? (e.createdAt as string).slice(0, 10) : '');
      expenseRows.push([
        trip.name,
        trip.destinationCurrency,
        rawDate,
        e.description,
        e.category,
        memberMap[e.paidBy as string] || e.paidBy,
        e.originalAmount,
        e.originalCurrency,
        (e.amountInDestinationCurrency as number).toFixed(2),
        e.splitMode,
        e.notes || '',
      ]);
    }
  }

  // ── Settlements sheet ──────────────────────────────────────────
  const settlementRows: unknown[][] = [
    ['Trip', 'Date', 'From', 'To', 'Amount', 'Currency', 'Note'],
  ];
  for (const { trip, settlements } of tripData) {
    for (const s of settlements) {
      const rawDate = s.settledAt
        ? (s.settledAt as string).slice(0, 10)
        : '';
      settlementRows.push([
        trip.name,
        rawDate,
        s.fromDisplayName || s.fromUserId,
        s.toDisplayName || s.toUserId,
        (s.amountInDestinationCurrency as number).toFixed(2),
        trip.destinationCurrency,
        s.note || '',
      ]);
    }
  }

  // ── Members sheet ──────────────────────────────────────────────
  const memberRows: unknown[][] = [['Trip', 'Name', 'Role', 'Home Currency']];
  for (const { trip } of tripData) {
    for (const m of trip.members) {
      memberRows.push([trip.name, m.displayName, m.role, m.homeCurrency || '']);
    }
  }

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(expenseRows), 'Expenses');
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(settlementRows), 'Settlements');
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(memberRows), 'Members');

  XLSX.writeFile(wb, `duitrip_${new Date().toISOString().slice(0, 10)}.xlsx`);
}

// ── Import ─────────────────────────────────────────────────────────────────────

export interface ImportRow {
  tripName: string;
  tripId?: string;
  date: string;
  description: string;
  category: string;
  paidByName: string;
  paidById?: string;
  originalAmount: number;
  originalCurrency: string;
  splitMode: string;
  notes: string;
  valid: boolean;
  errors: string[];
}

export function parseXlsxDate(val: string | number | undefined): string {
  if (!val) return '';
  if (typeof val === 'number') {
    // Excel serial date
    const d = XLSX.SSF.parse_date_code(val);
    if (d) {
      const month = String(d.m).padStart(2, '0');
      const day = String(d.d).padStart(2, '0');
      return `${d.y}-${month}-${day}`;
    }
  }
  const s = String(val).trim();
  // Already ISO
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  // Try parsing locale strings like "5/20/2026"
  const d = new Date(s);
  if (!isNaN(d.getTime())) return d.toISOString().slice(0, 10);
  return s;
}

export function parseImportXLSX(file: File, trips: Trip[]): Promise<ImportRow[]> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (evt) => {
      try {
        const wb = XLSX.read(evt.target!.result, { type: 'array', cellDates: false });
        const ws = wb.Sheets['Expenses'];
        if (!ws) {
          reject(new Error('No "Expenses" sheet found. Please export from Duitrip first.'));
          return;
        }

        const rows = XLSX.utils.sheet_to_json<Record<string, string | number>>(ws);
        if (rows.length === 0) {
          reject(new Error('Expenses sheet is empty'));
          return;
        }

        const tripMap = new Map(trips.map((t) => [t.name.trim().toLowerCase(), t]));

        const parsed: ImportRow[] = rows.map((row) => {
          const tripName = String(row['Trip'] || '').trim();
          const matchedTrip = tripMap.get(tripName.toLowerCase());
          const paidByName = String(row['Paid By'] || '').trim();
          const errors: string[] = [];

          if (!tripName) errors.push('Trip name missing');
          else if (!matchedTrip) errors.push(`Trip "${tripName}" not found`);

          let paidById: string | undefined;
          if (matchedTrip) {
            const member = matchedTrip.members.find(
              (m) => m.displayName.trim().toLowerCase() === paidByName.toLowerCase(),
            );
            paidById = member?.userId || member?.ghostId || undefined;
            if (!paidById) errors.push(`Member "${paidByName}" not found in trip`);
          }

          const originalAmount = parseFloat(String(row['Original Amount'] || '0'));
          if (!originalAmount || isNaN(originalAmount)) errors.push('Invalid amount');

          const description = String(row['Description'] || '').trim();
          if (!description) errors.push('Description missing');

          return {
            tripName,
            tripId: matchedTrip?.tripId,
            date: parseXlsxDate(row['Date'] as string | number),
            description,
            category: String(row['Category'] || 'Other').trim(),
            paidByName,
            paidById,
            originalAmount: isNaN(originalAmount) ? 0 : originalAmount,
            originalCurrency: String(row['Original Currency'] || '').trim(),
            splitMode: String(row['Split Mode'] || 'equal').trim(),
            notes: String(row['Notes'] || '').trim(),
            valid: errors.length === 0,
            errors,
          };
        });

        resolve(parsed);
      } catch (err) {
        reject(err);
      }
    };
    reader.readAsArrayBuffer(file);
  });
}
