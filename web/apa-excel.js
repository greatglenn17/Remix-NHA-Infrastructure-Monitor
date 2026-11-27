// AbstractExcel for the website: reads the Abstract of Physical Accomplishment of the actual work (the "as of" one) from
// its Excel file (.xlsx). Under the "ITEM NO. / ITEM DESCRIPTION / AMOUNT" header, every row with a description down to
// the GRAND TOTAL (parts, groups, pay items, subtotals) and the "As of" date; the parts (item no. 1, 2, 3, with the
// contract amount and the TO DATE "% WT" of their subtotal rows) and the overall % come from those rows. The sheet's
// last three columns, under "EQUIVALENT TO 370 UNITS" (PREVIOUS, TO DATE, BALANCE), are each row's `equiv`.
// The office-building layout (a community facility) is read too: its parts are the works numbered I, II, III.
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
  const desc = inHeader(text => /description/i.test(text)), amountHead = named('AMOUNT');
  // The first PREVIOUS, THIS PERIOD and TO DATE under the header are the accomplishment's: QTY, AMOUNT, % WT.
  const block = name => all.find(c => c.row > header.row && same(c.text, name));
  const toDate = block('TO DATE'), prev = block('PREVIOUS'), period = block('THIS PERIOD');
  const at = (row, col) => (col == null ? '' : cells.get(row * 100000 + col)?.text ?? '');
  if (desc == null || amountHead == null || !toDate || !at(toDate.row + 1, toDate.col + 2).startsWith('%')) return null;
  // A heading over several columns (AMOUNT over "Original" and "V.O.#1") is read at its last one, the figures in force.
  const blocks = Math.min(...[prev, period, toDate].filter(Boolean).map(b => b.col));
  const inForce = col => { while (col != null && col + 1 < blocks && !at(header.row, col + 1) && at(toDate.row + 1, col + 1)) col++; return col; };
  const qtyHead = named('QTY') ?? named('QUANTITY'), unitCol = named('UNIT'), balanceCol = named('BALANCE');
  const amountCol = inForce(amountHead), qtyCol = inForce(qtyHead), unitCostCol = inForce(named('UNIT COST'));
  // The office-building layout: works numbered I, II, III, "Sub-Total (...)" and "GRAND TOTAL" written in the item no.
  // column, and a sub-item's number ("6.1.") where its description starts, the description one column further in.
  const TOTALS = /^(sub-?\s*total|grand total)/i, LABEL = /^\d+[a-z]?(\.\d+)*\.?$/i, wide = qtyHead ?? unitCol ?? amountHead;
  const romanNo = n => ['', 'X', 'XX', 'XXX'][Math.floor(n / 10)] + ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX'][n % 10];
  let roman = null, works = 0;
  // A cell's number at `times` decimals' worth: amounts 2, quantities 4, and a % (a fraction on the sheet) as 0 to 100 at 2.
  const num = (row, col, times, over = times) => { const v = number(at(row, col)); return v == null ? null : Math.round(v * times) / over; };
  const money = (row, col) => num(row, col, 100), qty = (row, col) => num(row, col, 10000);
  const figures = (row, b) => (b ? [qty(row, b.col), money(row, b.col + 1), num(row, b.col + 2, 10000, 100)] : [null, null, null]);
  // "EQUIVALENT TO 370 UNITS" in the header row: the number of units, and the first of its three columns.
  const EQUIV = /^equivalent to\s+([\d,.]+)\s+units?$/i, equivCol = inHeader(text => EQUIV.test(text.trim()));
  const equivUnits = equivCol == null ? 0 : Number(EQUIV.exec(at(header.row, equivCol).trim())[1].replace(/,/g, '')) || 0;
  const rows = [];
  let billing = false;
  for (const row of new Set(all.filter(c => c.row > toDate.row + 1).map(c => c.row))) {
    let name = at(row, desc), cellNo = at(row, header.col);
    const amount = money(row, amountCol);
    if (roman == null && name && cellNo) roman = cellNo.toUpperCase() === 'I';
    if (roman && !name && TOTALS.test(cellNo)) [name, cellNo] = [cellNo, ''];
    else if (roman && !cellNo) {
      const texts = Array.from({ length: wide - desc }, (_, i) => at(row, desc + i)).filter(Boolean);
      if (texts.length > 1 && LABEL.test(texts[0])) [cellNo, name] = texts;
    }
    if (!name) continue;
    // Excel keeps a typed 2.2 as 2.2000000000000002: such an item no. is the number at 6 decimals, written plainly.
    const no = /^\d+\.\d{7,}$/.test(cellNo) ? String(Math.round(Number(cellNo) * 1e6) / 1e6) : cellNo;
    if (/^milestone/i.test(name)) billing = true; // the billing format's lines
    // A work is the next Roman numeral in turn, so the letters I, V, X, C, D and L of a work's own items are not taken for one.
    const part = roman ? amount == null && no.toUpperCase() === romanNo(works + 1) : /^\d+$/.test(no);
    const kind = /^grand total/i.test(name) ? 'total' : TOTALS.test(name) ? 'subtotal' : part ? 'part' : no && amount == null ? 'group' : 'item';
    if (kind === 'part') works++;
    const done = figures(row, toDate);
    // The sheet's own "% WT" (it adds two rounded figures); a subtotal without one, the amount to date over the contract amount.
    if ((kind === 'subtotal' || kind === 'total') && done[2] == null && amount > 0 && done[1] != null) done[2] = Math.round(done[1] / amount * 10000) / 100;
    rows.push({ kind, no, name, qty: qty(row, qtyCol), unit: at(row, unitCol), unitCost: money(row, unitCostCol), amount,
      prev: figures(row, prev), period: figures(row, period), toDate: done, balance: [qty(row, balanceCol), balanceCol == null ? null : money(row, balanceCol + 1)],
      equiv: [0, 1, 2].map(i => (equivCol == null ? null : money(row, equivCol + i))) });
    if (kind === 'total') break;
  }
  const parts = abstractParts(rows);
  return parts.length ? { asOf: all.map(c => asOfDate(c.text)).find(Boolean) ?? null, parts, overallPct: rows.find(r => r.kind === 'total')?.toDate[2] ?? null, billing, equivUnits, rows } : null;
}

/**
 * AbstractExcel.read on the unzipped parts: the first sheet laid out as an abstract, as { asOf (yyyy-MM-dd or null),
 * parts: [{ name, amount, actualPct }], overallPct, billing (true for the billing format), equivUnits (the N of the
 * sheet's "EQUIVALENT TO N UNITS" columns, 0 without them), rows }; null without one.
 * A row is { kind ("part", "group", "item", "subtotal" or "total"), no, name, qty, unit, unitCost, amount, prev, period,
 * toDate (each [qty, amount, %]), balance ([qty, amount]), equiv ([previous, to date, balance] in equivalent units, at
 * 2 decimals) }; null is an empty cell. Kept as the record's rowsJson.
 */
export function abstractOf(files) {
  const { sheets, strings } = workbookSheets(files);
  // A workbook can hold both formats: the actual one wins wherever its sheet is.
  const all = sheets.map(([, xml]) => sheetAbstract(xml, strings)).filter(Boolean);
  return all.find(a => !a.billing) ?? all[0] ?? null;
}

/** AbstractExcel.read: the abstract in an .xlsx file's bytes. */
export const readAbstractWorkbook = async buffer => abstractOf(await unzip(buffer));
