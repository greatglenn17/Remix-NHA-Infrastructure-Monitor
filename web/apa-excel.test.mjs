// Run: node --test "web/*.test.mjs"
// The workbook of the phone's AbstractExcelTest: a made-up abstract laid out like the district's (three header rows,
// parts 1 to 3 with their groups and subtotals, the equivalent-units columns, the grand total, then the small target table).
import test from 'node:test';
import assert from 'node:assert/strict';
import { abstractOf, asOfDate } from './apa-excel.js';
import { NotAnExcelFile } from './chart-excel.js';
import { abstractParts, abstractRows } from './logic.js';

/** Rows of [ref, value] as a sheet: numbers as they are, text as a formula's worked-out string. */
const sheet = cells => `<worksheet><sheetData>${cells.map(([r, v]) => (typeof v === 'number' ? `<c r="${r}"><f>X1</f><v>${v}</v></c>` : `<c r="${r}" t="str"><v>${v}</v></c>`)).join('')}</sheetData></worksheet>`;
const workbook = xml => ({
  'xl/workbook.xml': '<workbook xmlns:r="x"><sheets><sheet name="MONITORING" sheetId="1" r:id="rId1"/></sheets></workbook>',
  'xl/_rels/workbook.xml.rels': '<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>',
  'xl/worksheets/sheet1.xml': xml,
});

const HEAD = [
  ['A7', 'ABSTRACT OF PHYSICAL ACCOMPLSIHMENT'], ['A8', 'As of March 14, 2025'],
  ['A20', 'ITEM NO.'], ['B20', 'ITEM DESCRIPTION'], ['E20', 'QTY'], ['F20', 'UNIT'], ['G20', 'UNIT COST'], ['H20', 'AMOUNT'], ['I20', 'ACCOMPLISHMENT'], ['S20', 'BALANCE'], ['V20', 'EQUIVALENT TO 100 UNITS'],
  ['I21', 'PREVIOUS'], ['L21', 'THIS PERIOD'], ['O21', 'TO DATE'],
  ['I22', 'QTY'], ['J22', 'AMOUNT'], ['K22', '% WT'], ['L22', 'QTY'], ['M22', 'AMOUNT'], ['N22', '% WT'], ['O22', 'QTY'], ['P22', 'AMOUNT'], ['Q22', '% WT'],
  ['S22', 'QTY'], ['T22', 'AMOUNT'], ['V22', 'PREVIOUS'], ['W22', 'TO DATE'], ['X22', 'BALANCE'],
];
const ACTUAL = [...HEAD,
  ['A23', 1], ['B23', 'LAND ACQUISITION COST'], ['H23', 1000000], ['P23', 500000], ['Q23', 0.5],
  ['B24', 'Subtotal (Land Acquisition Cost)'], ['H24', 1000000], ['P24', 500000], ['Q24', 0.5],
  ['A26', 2], ['B26', 'LAND DEVELOPMENT'],
  ['A27', 2.1], ['B27', 'GENERAL REQUIREMENTS'],
  ['A28', '2.1.1'], ['B28', 'Mobilization/Demobilization'], ['E28', 1], ['F28', 'l.s.'], ['G28', 400000], ['H28', 400000], ['I28', 0], ['J28', 0], ['K28', 0],
  ['L28', 0.25], ['M28', 100000], ['N28', 0.25], ['O28', 0.25], ['P28', 100000], ['Q28', 0.25], ['S28', 0.75], ['T28', 300000], ['V28', 0], ['W28', 25], ['X28', 75],
  ['B29', 'Subtotal (General Requrements)'], ['H29', 400000], ['P29', 100000],
  // A typed 2.2 and 3.1 as Excel stores them: the item no. loses the tail.
  ['A30', '2.2000000000000002'], ['B30', 'ROADWORKS'],
  ['A31', '2.2.1'], ['B31', 'Road Excavation'], ['H31', 1600000], ['P31', 404000], ['Q31', 0.2525], ['V31', 12.5], ['W31', 25.25], ['X31', 74.75],
  ['B32', 'Subtotal (Roadworks)'], ['H32', 1600000], ['P32', 404000],
  // 504,000 of 2,000,000 is 25.20%; the sheet says 25.21% (two rounded figures added), and that is what is printed.
  ['B33', 'Subtotal (Land Development)'], ['H33', 2000000], ['P33', 504000], ['Q33', 0.25210000000000005],
  ['A35', 3], ['B35', 'HOUSING CONSTRUCTION'],
  ['A36', '3.0999999999999996'], ['B36', 'STRUCTURAL WORKS'],
  ['A37', '3.1.1'], ['B37', 'Column Footing'], ['H37', 7000000], ['P37', 70000], ['Q37', 0.01], ['W37', 3.14159],
  ['B38', 'Subtotal (Structural Works)'], ['H38', 7000000], ['P38', 70000],
  ['B39', 'Subtotal (Housing Construction)'], ['H39', 7000000], ['P39', 70000],
  ['B40', 'GRAND TOTAL'], ['H40', 10000000], ['P40', 1074000], ['Q40', 0.1073],
  ['A43', 'Prepared by:'], ['M48', 45730], ['O48', 'TARGET'], ['M50', 'LAND DEVELOPMENT'], ['O50', 0.4], ['P50', 0.2521],
];

test('abstract Excel: the as-of date, each part\'s amount and % to date from its own subtotal, and the grand total', () => {
  const { rows, ...found } = abstractOf(workbook(sheet(ACTUAL)));
  assert.deepEqual(found, {
    asOf: '2025-03-14', overallPct: 10.73, billing: false, equivUnits: 100,
    parts: [
      { name: 'LAND ACQUISITION COST', amount: 1000000, actualPct: 50 },
      { name: 'LAND DEVELOPMENT', amount: 2000000, actualPct: 25.21 },
      { name: 'HOUSING CONSTRUCTION', amount: 7000000, actualPct: 1 }, // no % WT on its subtotal: 70,000 of 7,000,000
    ],
  });
  assert.equal(abstractParts(rows).length, 3);
});

test('abstract Excel: every row with a description down to the grand total, as the record keeps them', () => {
  const { rows } = abstractOf(workbook(sheet(ACTUAL)));
  assert.deepEqual(rows.map(r => r.kind), ['part', 'subtotal', 'part', 'group', 'item', 'subtotal', 'group', 'item', 'subtotal', 'subtotal', 'part', 'group', 'item', 'subtotal', 'subtotal', 'total']);
  const row = key => rows.find(r => r.no === key || r.name === key), none = [null, null, null];
  assert.deepEqual(row('2.1.1'), { kind: 'item', no: '2.1.1', name: 'Mobilization/Demobilization', qty: 1, unit: 'l.s.', unitCost: 400000, amount: 400000,
    prev: [0, 0, 0], period: [0.25, 100000, 25], toDate: [0.25, 100000, 25], balance: [0.75, 300000], equiv: [0, 25, 75] });
  assert.deepEqual(row('2.2.1'), { kind: 'item', no: '2.2.1', name: 'Road Excavation', qty: null, unit: '', unitCost: null, amount: 1600000,
    prev: none, period: none, toDate: [null, 404000, 25.25], balance: [null, null], equiv: [12.5, 25.25, 74.75] });
  // The sheet's last three columns (equivalent units) at 2 decimals; every other row has none.
  assert.deepEqual(row('3.1.1').equiv, [null, 3.14, null]);
  assert.ok(rows.filter(r => !['2.1.1', '2.2.1', '3.1.1'].includes(r.no)).every(r => r.equiv.join() === none.join()));
  assert.equal(Object.keys(row('2.1.1')).at(-1), 'equiv'); // last in the record's JSON, after "balance"
  assert.deepEqual(rows.filter(r => r.kind === 'group').map(r => r.no), ['2.1', '2.2', '3.1']);
  assert.deepEqual([row('2.1').kind, row('2.1').name, row('2.1').amount], ['group', 'GENERAL REQUIREMENTS', null]);
  assert.deepEqual([row('Subtotal (Land Development)').amount, row('Subtotal (Land Development)').toDate], [2000000, [null, 504000, 25.21]]);
  assert.deepEqual(row('Subtotal (Housing Construction)').toDate, [null, 70000, 1]); // no % WT: its amount to date over its amount
  assert.deepEqual([rows.at(-1).name, rows.at(-1).amount, rows.at(-1).toDate], ['GRAND TOTAL', 10000000, [null, 1074000, 10.73]]);
  // The rows survive the record's JSON as they are.
  assert.deepEqual(abstractRows(JSON.stringify(rows)), rows);

  // A sheet without the quantity, unit cost, balance, PREVIOUS and THIS PERIOD columns still reads: those cells are null.
  const narrow = abstractOf(workbook(sheet([['A1', 'ITEM NO.'], ['B1', 'ITEM DESCRIPTION'], ['C1', 'AMOUNT'], ['D2', 'TO DATE'], ['F3', '% WT'],
    ['A4', 1], ['B4', 'LAND DEVELOPMENT'], ['B5', 'Subtotal (Land Development)'], ['C5', 500], ['E5', 125], ['B6', 'Signed by']])));
  assert.deepEqual(narrow.parts, [{ name: 'LAND DEVELOPMENT', amount: 500, actualPct: 25 }]);
  assert.equal(narrow.overallPct, null);
  assert.equal(narrow.equivUnits, 0); // no "EQUIVALENT TO ... UNITS" header
  assert.deepEqual(narrow.rows[1], { kind: 'subtotal', no: '', name: 'Subtotal (Land Development)', qty: null, unit: '', unitCost: null, amount: 500,
    prev: none, period: none, toDate: [null, 125, 25], balance: [null, null], equiv: none });
  assert.equal(narrow.rows[2].kind, 'item'); // no grand total: the rows run to the end of the sheet
});

test('abstract Excel: the billing format is told apart, and other sheets are not abstracts', () => {
  const billing = [...HEAD, ['A23', 1], ['B23', 'LAND ACQUISITION COST'], ['B24', 'Subtotal (Land Acquisition Cost)'], ['H24', 1000000], ['Q24', 0.4],
    ['A27', 3], ['B27', 'HOUSING CONSTRUCTION'], ['A28', 3.1], ['B28', 'MILESTONE 1 (80%)'], ['B30', 'Subtotal (Housing Construction)'], ['H30', 7000000], ['Q30', 0]];
  const found = abstractOf(workbook(sheet(billing)));
  assert.equal(found.billing, true);
  assert.deepEqual(found.parts.map(p => p.actualPct), [40, 0]);
  assert.equal(abstractOf(workbook(sheet([['A1', 'ITEM NO.'], ['B1', 'DESCRIPTION'], ['C1', 'Amount (Php)']]))), null); // a bar chart
  assert.equal(abstractOf(workbook(sheet(HEAD))), null); // the header alone
  assert.throws(() => abstractOf({ 'word/document.xml': '<document/>' }), NotAnExcelFile);
});

test('abstract Excel: a workbook with both formats gives the actual one, wherever its sheet is', () => {
  const billing = [...HEAD, ['A23', 3], ['B23', 'HOUSING CONSTRUCTION'], ['B24', 'MILESTONE 1 (80%)'], ['B25', 'Subtotal (Housing Construction)'], ['H25', 7000000], ['Q25', 0]];
  const files = { ...workbook(sheet(billing)), 'xl/worksheets/sheet2.xml': sheet(ACTUAL),
    'xl/workbook.xml': '<workbook xmlns:r="x"><sheets><sheet name="APA" sheetId="1" r:id="rId1"/><sheet name="MONITORING" sheetId="2" r:id="rId2"/></sheets></workbook>',
    'xl/_rels/workbook.xml.rels': '<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>' };
  assert.equal(abstractOf(workbook(sheet(billing))).billing, true);
  assert.equal(abstractOf(files).billing, false);
  assert.equal(abstractOf(files).asOf, '2025-03-14');
});

test('abstract Excel: the as-of line', () => {
  assert.equal(asOfDate('As of October 30, 2026'), '2026-10-30');
  assert.equal(asOfDate(' as of  May 5 2026 '), '2026-05-05');
  assert.equal(asOfDate('As of last week'), null);
  assert.equal(asOfDate('As of September 31, 2026'), null); // no such day
});

// The office-building layout (a community facility): works numbered I, II, "Sub-Total (...)" and "GRAND TOTAL" in the
// item no. column, Original and V.O.#1 columns under QUANTITY, UNIT COST and AMOUNT, and a sub-item's number where its
// description starts. The phone's AbstractExcelTest reads the same sheet.
const OFFICE = [
  ['A1', 'As of May 25, 2026'],
  ['A3', 'Item No.'], ['B3', 'ITEM DESCRIPTION'], ['J3', 'QUANTITY'], ['L3', 'UNIT'], ['M3', 'UNIT COST'], ['O3', 'AMOUNT'], ['Q3', 'Accomplishment'],
  ['Q4', 'Previous'], ['T4', 'This Period'], ['W4', 'To Date'],
  ['J5', 'Original'], ['K5', 'V.O.#1'], ['M5', 'Original'], ['N5', 'V.O.#1'], ['O5', 'Original'], ['P5', 'V.O#1'],
  ['Q5', 'Qty'], ['R5', 'Amount'], ['S5', '%WT'], ['T5', 'Qty'], ['U5', 'Amount'], ['V5', '%WT'], ['W5', 'Qty'], ['X5', 'Amount'], ['Y5', '%WT'],
  ['A6', 'I'], ['B6', 'GENERAL REQUIREMENTS'],
  ['A7', 'A'], ['B7', 'Mobilization'], ['J7', 1], ['K7', 1], ['L7', 'lot'], ['M7', 300], ['N7', 400], ['O7', 300], ['P7', 400], ['W7', 0.5], ['X7', 200], ['Y7', 0.5],
  ['A8', 'Sub-Total (General Requirements)'], ['O8', 300], ['P8', 400], ['X8', 200], ['Y8', 0.5],
  ['A9', 'CONSTRUCTION OF OFFICE BUILDING'], // a title between works: not a row
  ['A10', 'II'], ['B10', 'ARCHITECTURAL WORKS'],
  ['A11', 'A'], ['B11', 'Doors and Jambs'],
  ['B12', '1a'], ['C12', 'D1 Door'], ['K12', 2], ['L12', 'sets'], ['N12', 50], ['P12', 100], ['X12', 0], ['Y12', 0],
  ['A13', 'I'], ['B13', 'Tempered Glass'], ['P13', 500], ['X13', 150], ['Y13', 0.3], // the letter I of an item, not work I
  ['A14', 'Sub-Total (Architectural Works 1)'], ['P14', 600], ['X14', 150], ['Y14', 0.25],
  ['A15', 'J'], ['B15', 'Construction of Guard House'], ['P15', 200], ['X15', 50], ['Y15', 0.25],
  ['A16', 'Sub-Total (Architectural Works)'], ['O16', 800], ['P16', 800], ['X16', 200], // no % WT: 200 of 800
  ['A17', 'GRAND TOTAL'], ['O17', 1100], ['P17', 1200], ['X17', 400], ['Y17', 0.3333],
  ['A20', 'Prepared By:'],
];

test('abstract Excel, office-building layout: Roman-numbered works with their Sub-Totals, the V.O. columns in force', () => {
  const { rows, ...found } = abstractOf(workbook(sheet(OFFICE)));
  assert.deepEqual(found, { asOf: '2026-05-25', overallPct: 33.33, billing: false, equivUnits: 0,
    parts: [{ name: 'GENERAL REQUIREMENTS', amount: 400, actualPct: 50 }, { name: 'ARCHITECTURAL WORKS', amount: 800, actualPct: 25 }] });
  assert.deepEqual(rows.map(r => [r.kind, r.no, r.name, r.amount]), [
    ['part', 'I', 'GENERAL REQUIREMENTS', null], ['item', 'A', 'Mobilization', 400], ['subtotal', '', 'Sub-Total (General Requirements)', 400],
    ['part', 'II', 'ARCHITECTURAL WORKS', null], ['group', 'A', 'Doors and Jambs', null], ['item', '1a', 'D1 Door', 100], ['item', 'I', 'Tempered Glass', 500],
    ['subtotal', '', 'Sub-Total (Architectural Works 1)', 600], ['item', 'J', 'Construction of Guard House', 200],
    ['subtotal', '', 'Sub-Total (Architectural Works)', 800], ['total', '', 'GRAND TOTAL', 1200]]);
  // The V.O.#1 quantity and unit cost, and the work's own subtotal over its groups'.
  assert.deepEqual([rows[1].qty, rows[1].unit, rows[1].unitCost, rows[1].toDate], [1, 'lot', 400, [0.5, 200, 50]]);
  assert.deepEqual([rows[5].qty, rows[5].unit, rows[5].unitCost], [2, 'sets', 50]);
  // The Original quantity, unit cost and amount beside them, for the printed abstract; an extra work has none.
  assert.deepEqual([rows[1].was, rows[2].was, rows[5].was, rows.at(-1).was], [[[1, 300, 300]], [[null, null, 300]], [[null, null, null]], [[null, null, 1100]]]);
});
