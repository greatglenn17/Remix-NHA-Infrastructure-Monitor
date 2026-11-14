// AbstractExcel for the website: reads the Abstract of Physical Accomplishment of the actual work (the "as of" one) from
// its Excel file (.xlsx). Under the "ITEM NO. / ITEM DESCRIPTION / AMOUNT" header, every row with a description down to
// the GRAND TOTAL (parts, groups, pay items, subtotals) and the "As of" date; the parts (item no. 1, 2, 3, with the
// contract amount and the TO DATE "% WT" of their subtotal rows) and the overall % come from those rows.
// Change both when the phone's reader (util/AbstractExcel.kt) changes.
import { unzip, workbookSheets, sheetCells, number } from './chart-excel.js';
import { abstractParts } from './logic.js';

const MONTHS = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];

/** "As of October 30, 2026" as "2026-10-30"; null for anything else. */
export function asOfDate(text) {
  const m = /^as of\s+([a-z]+)\s+(\d{1,2}),?\s+(\d{4})$/i.exec(text.trim()), month = m ? MONTHS.indexOf(m[1].toLowerCase()) : -1;
  // A day the month does not have ("September 31") is no date, as on the phone.
  if (month < 0 || new Date(Date.UTC(Number(m[3]), month, Number(m[2]))).getUTCDate() !== Number(m[2])) return null;
  return `${m[3]}-${String(month + 1).padStart(2, '0')}-${m[2].padStart(2, '0')}`;
}

/** One sheet as an abstract; null when it is not laid out as one. */
function sheetAbstract(xml, strings) {
  const { cells } = sheetCells(xml, strings);
  const all = [...cells.values()].sort((a, b) => a.row - b.row || a.col - b.col);
  const same = (text, other) => text.toLowerCase() === other.toLowerCase();
  const header = all.find(c => same(c.text, 'ITEM NO.'));
  if (!header) return null;
  const inHeader = test => all.find(c => c.row === header.row && test(c.text))?.col;
  const named = name => inHeader(text => same(text, name));
  const desc = inHeader(text => /description/i.test(text)), amountCol = named('AMOUNT');
  // The first PREVIOUS, THIS PERIOD and TO DATE under the header are the accomplishment's: QTY, AMOUNT, % WT.
  const block = name => all.find(c => c.row > header.row && same(c.text, name));
  const toDate = block('TO DATE'), prev = block('PREVIOUS'), period = block('THIS PERIOD');
  const at = (row, col) => (col == null ? '' : cells.get(row * 100000 + col)?.text ?? '');
  if (desc == null || amountCol == null || !toDate || !at(toDate.row + 1, toDate.col + 2).startsWith('%')) return null;
  const qtyCol = named('QTY'), unitCol = named('UNIT'), unitCostCol = named('UNIT COST'), balanceCol = named('BALANCE');
  // A cell's number at `times` decimals' worth: amounts 2, quantities 4, and a % (a fraction on the sheet) as 0 to 100 at 2.
  const num = (row, col, times, over = times) => { const v = number(at(row, col)); return v == null ? null : Math.round(v * times) / over; };
  const money = (row, col) => num(row, col, 100), qty = (row, col) => num(row, col, 10000);
  const figures = (row, b) => (b ? [qty(row, b.col), money(row, b.col + 1), num(row, b.col + 2, 10000, 100)] : [null, null, null]);
  const rows = [];
  let billing = false;
  for (const row of new Set(all.filter(c => c.row > toDate.row + 1).map(c => c.row))) {
    const name = at(row, desc), no = at(row, header.col), amount = money(row, amountCol);
    if (!name) continue;
    if (/^milestone/i.test(name)) billing = true; // the billing format's lines
    const kind = /^grand total/i.test(name) ? 'total' : /^subtotal/i.test(name) ? 'subtotal' : /^\d+$/.test(no) ? 'part' : no && amount == null ? 'group' : 'item';
    const done = figures(row, toDate);
    // The sheet's own "% WT" (it adds two rounded figures); a subtotal without one, the amount to date over the contract amount.
    if ((kind === 'subtotal' || kind === 'total') && done[2] == null && amount > 0 && done[1] != null) done[2] = Math.round(done[1] / amount * 10000) / 100;
    rows.push({ kind, no, name, qty: qty(row, qtyCol), unit: at(row, unitCol), unitCost: money(row, unitCostCol), amount,
      prev: figures(row, prev), period: figures(row, period), toDate: done, balance: [qty(row, balanceCol), balanceCol == null ? null : money(row, balanceCol + 1)] });
    if (kind === 'total') break;
  }
  const parts = abstractParts(rows);
  return parts.length ? { asOf: all.map(c => asOfDate(c.text)).find(Boolean) ?? null, parts, overallPct: rows.find(r => r.kind === 'total')?.toDate[2] ?? null, billing, rows } : null;
}

/**
 * AbstractExcel.read on the unzipped parts: the first sheet laid out as an abstract, as { asOf (yyyy-MM-dd or null),
 * parts: [{ name, amount, actualPct }], overallPct, billing (true for the billing format), rows }; null without one.
 * A row is { kind ("part", "group", "item", "subtotal" or "total"), no, name, qty, unit, unitCost, amount, prev, period,
 * toDate (each [qty, amount, %]), balance ([qty, amount]) }; null is an empty cell. Kept as the record's rowsJson.
 */
export function abstractOf(files) {
  const { sheets, strings } = workbookSheets(files);
  // A workbook can hold both formats: the actual one wins wherever its sheet is.
  const all = sheets.map(([, xml]) => sheetAbstract(xml, strings)).filter(Boolean);
  return all.find(a => !a.billing) ?? all[0] ?? null;
}

/** AbstractExcel.read: the abstract in an .xlsx file's bytes. */
export const readAbstractWorkbook = async buffer => abstractOf(await unzip(buffer));
