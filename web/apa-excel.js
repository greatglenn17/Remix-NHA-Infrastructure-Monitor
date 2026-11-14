// AbstractExcel for the website: reads the Abstract of Physical Accomplishment of the actual work (the "as of" one) from
// its Excel file (.xlsx). Under the "ITEM NO. / ITEM DESCRIPTION / AMOUNT" header, each part (item no. 1, 2, 3) with the
// contract amount and the TO DATE "% WT" of its subtotal row, the GRAND TOTAL's, and the "As of" date. The pay items stay
// in Excel. Change both when the phone's reader (util/AbstractExcel.kt) changes.
import { unzip, workbookSheets, sheetCells, number } from './chart-excel.js';

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
  const desc = all.find(c => c.row === header.row && /description/i.test(c.text)), amount = all.find(c => c.row === header.row && same(c.text, 'AMOUNT'));
  // The first TO DATE under the header is the accomplishment's: QTY, AMOUNT, % WT.
  const toDate = all.find(c => c.row > header.row && same(c.text, 'TO DATE'));
  const at = (row, col) => cells.get(row * 100000 + col)?.text ?? '';
  if (!desc || !amount || !toDate || !at(toDate.row + 1, toDate.col + 2).startsWith('%')) return null;
  // The sheet's own "% WT" (it adds two rounded figures); without one, the amount to date over the contract amount.
  const pct = row => {
    const total = number(at(row, amount.col)), done = number(at(row, toDate.col + 1));
    const v = number(at(row, toDate.col + 2)) ?? (total > 0 && done != null ? done / total : null);
    return v == null ? null : Math.round(v * 10000) / 100;
  };
  const parts = [];
  let overallPct = null, billing = false;
  for (const row of new Set(all.filter(c => c.row > toDate.row + 1).map(c => c.row))) {
    const name = at(row, desc.col);
    if (/^grand total/i.test(name)) { overallPct = pct(row); break; }
    if (/^milestone/i.test(name)) billing = true; // the billing format's lines
    if (/^\d+$/.test(at(row, header.col)) && name) parts.push({ name, amount: 0, actualPct: 0 });
    // A part's own subtotal is the last one before the next part, so it overwrites its groups'.
    else if (/^subtotal/i.test(name) && parts.length) Object.assign(parts[parts.length - 1], { amount: number(at(row, amount.col)) ?? 0, actualPct: pct(row) ?? 0 });
  }
  return parts.length ? { asOf: all.map(c => asOfDate(c.text)).find(Boolean) ?? null, parts, overallPct, billing } : null;
}

/**
 * AbstractExcel.read on the unzipped parts: the first sheet laid out as an abstract, as { asOf (yyyy-MM-dd or null),
 * parts: [{ name, amount, actualPct }], overallPct, billing (true for the billing format) }; null without one.
 */
export function abstractOf(files) {
  const { sheets, strings } = workbookSheets(files);
  // A workbook can hold both formats: the actual one wins wherever its sheet is.
  const all = sheets.map(([, xml]) => sheetAbstract(xml, strings)).filter(Boolean);
  return all.find(a => !a.billing) ?? all[0] ?? null;
}

/** AbstractExcel.read: the abstract in an .xlsx file's bytes. */
export const readAbstractWorkbook = async buffer => abstractOf(await unzip(buffer));
