// Run: node --test "web/*.test.mjs"
// The workbook of the phone's AbstractExcelTest: a made-up abstract laid out like the district's (three header rows,
// parts 1 to 3 with their groups and subtotals, the grand total, then the small target table).
import test from 'node:test';
import assert from 'node:assert/strict';
import { abstractOf, asOfDate } from './apa-excel.js';
import { NotAnExcelFile } from './chart-excel.js';

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
  ['A28', '2.1.1'], ['B28', 'Mobilization/Demobilization'], ['H28', 400000], ['P28', 100000], ['Q28', 0.25],
  ['B29', 'Subtotal (General Requrements)'], ['H29', 400000], ['P29', 100000],
  ['A30', 2.2], ['B30', 'ROADWORKS'],
  ['A31', '2.2.1'], ['B31', 'Road Excavation'], ['H31', 1600000], ['P31', 404000], ['Q31', 0.2525],
  ['B32', 'Subtotal (Roadworks)'], ['H32', 1600000], ['P32', 404000],
  // 504,000 of 2,000,000 is 25.20%; the sheet says 25.21% (two rounded figures added), and that is what is printed.
  ['B33', 'Subtotal (Land Development)'], ['H33', 2000000], ['P33', 504000], ['Q33', 0.25210000000000005],
  ['A35', 3], ['B35', 'HOUSING CONSTRUCTION'],
  ['A36', 3.1], ['B36', 'STRUCTURAL WORKS'],
  ['A37', '3.1.1'], ['B37', 'Column Footing'], ['H37', 7000000], ['P37', 70000], ['Q37', 0.01],
  ['B38', 'Subtotal (Structural Works)'], ['H38', 7000000], ['P38', 70000],
  ['B39', 'Subtotal (Housing Construction)'], ['H39', 7000000], ['P39', 70000],
  ['B40', 'GRAND TOTAL'], ['H40', 10000000], ['P40', 1074000], ['Q40', 0.1073],
  ['A43', 'Prepared by:'], ['M48', 45730], ['O48', 'TARGET'], ['M50', 'LAND DEVELOPMENT'], ['O50', 0.4], ['P50', 0.2521],
];

test('abstract Excel: the as-of date, each part\'s amount and % to date from its own subtotal, and the grand total', () => {
  assert.deepEqual(abstractOf(workbook(sheet(ACTUAL))), {
    asOf: '2025-03-14', overallPct: 10.73, billing: false,
    parts: [
      { name: 'LAND ACQUISITION COST', amount: 1000000, actualPct: 50 },
      { name: 'LAND DEVELOPMENT', amount: 2000000, actualPct: 25.21 },
      { name: 'HOUSING CONSTRUCTION', amount: 7000000, actualPct: 1 }, // no % WT on its subtotal: 70,000 of 7,000,000
    ],
  });
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
