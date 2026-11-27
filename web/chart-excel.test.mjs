// Run: node --test "web/*.test.mjs"
// The workbook of the phone's BarChartExcelTest: header, Month row, item rows with yellow cells, then the total.
import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { readChartWorkbook, chartSheets, NotAnExcelFile } from './chart-excel.js';

/** A zip of `parts` (path -> text): the first stored, the rest deflated, as Excel writes them. */
function zip(parts) {
  const chunks = [], central = [];
  let offset = 0;
  Object.entries(parts).forEach(([name, text], i) => {
    const raw = Buffer.from(text), data = i === 0 ? raw : deflateRawSync(raw), file = Buffer.from(name);
    const sizes = Buffer.alloc(12);
    sizes.writeUInt32LE(0, 0); sizes.writeUInt32LE(data.length, 4); sizes.writeUInt32LE(raw.length, 8); // crc (unchecked), packed, size
    const tail = Buffer.alloc(4);
    tail.writeUInt16LE(file.length, 0); // name length, no extra field
    const method = Buffer.alloc(2);
    method.writeUInt16LE(i === 0 ? 0 : 8);
    const local = Buffer.concat([Buffer.from([0x50, 0x4b, 3, 4, 20, 0, 0, 0]), method, Buffer.alloc(4), sizes, tail, file, data]);
    const where = Buffer.alloc(4);
    where.writeUInt32LE(offset);
    central.push(Buffer.concat([Buffer.from([0x50, 0x4b, 1, 2, 20, 0, 20, 0, 0, 0]), method, Buffer.alloc(4), sizes, tail, Buffer.alloc(10), where, file]));
    chunks.push(local);
    offset += local.length;
  });
  const dir = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(central.length, 8); end.writeUInt16LE(central.length, 10);
  end.writeUInt32LE(dir.length, 12); end.writeUInt32LE(offset, 16);
  return new Uint8Array(Buffer.concat([...chunks, dir, end]));
}

// Columns: A item no, B description, C amount, D duration, E.. the day cells (E = Month 1, days 1-5).
const sheet = `<worksheet><sheetData>
  <row r="3"><c r="A3" t="s"><v>0</v></c><c r="B3" t="s"><v>1</v></c><c r="C3" t="s"><v>2</v></c><c r="D3" t="s"><v>3</v></c></row>
  <row r="4"><c r="E4" t="s"><v>4</v></c></row>
  <row r="6"><c r="A6"><v>1</v></c><c r="B6" t="s"><v>5</v></c><c r="C6"><v>1000</v></c><c r="D6"><v>10</v></c>
      <c r="E6" s="1"/><c r="F6" s="1"/><c r="G6" s="2"/><c r="M6" s="1"/></row>
  <row r="7"><c r="E7"><v>500</v></c></row>
  <row r="8"><c r="A8"><v>2</v></c><c r="B8" t="s"><v>6</v></c><c r="C8"><v>3000.5</v></c><c r="D8"><v>30</v></c><c r="G8" s="1"/></row>
  <row r="10"><c r="B10" t="s"><v>7</v></c><c r="C10"><v>4000.5</v></c></row>
  <row r="11"><c r="B11"><v>99</v></c><c r="C11"><v>99</v></c><c r="E11" s="1"/></row>
  </sheetData><mergeCells><mergeCell ref="G8:L8"/></mergeCells></worksheet>`;

const parts = {
  'xl/workbook.xml': `<?xml version="1.0" encoding="UTF-8"?><workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>
    <sheet name="Notes" sheetId="1" r:id="rId1"/><sheet name="Revised &amp; final" sheetId="2" r:id="rId2"/></sheets></workbook>`,
  'xl/_rels/workbook.xml.rels': `<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>`,
  'xl/sharedStrings.xml': `<sst><si><t>ITEM NO.</t></si><si><t>DESCRIPTION</t></si><si><t>Amount (Php)</t></si>
    <si><t>Duration (C.D)</t></si><si><t>Month 1</t></si><si><r><t>Mobili</t></r><r><t>zation</t></r><rPh><t>x</t></rPh></si>
    <si><t>Earthworks &amp; grading</t></si><si><t>TOTAL PROJECT COST</t></si></sst>`,
  // fill 2 yellow, fill 3 white; xf 1 -> yellow, xf 2 -> white
  'xl/styles.xml': `<styleSheet><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/></patternFill></fill></fills>
    <cellXfs count="3"><xf fillId="0"/><xf fillId="2"/><xf fillId="3"/></cellXfs></styleSheet>`,
  'xl/worksheets/sheet1.xml': '<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>hello</t></is></c></row></sheetData></worksheet>',
  'xl/worksheets/sheet2.xml': sheet,
  'docProps/thumbnail.jpeg': 'not xml, so not read',
};

test('bar chart Excel: the items, their bars from the yellow cells, and the Duration column', async () => {
  const sheets = await readChartWorkbook(zip(parts));
  assert.deepEqual(sheets.map(s => s.name), ['Revised & final']); // the sheet with no chart layout is left out
  assert.deepEqual(sheets[0].items, [
    // E,F = days 1-10; white G skipped; M = Month 2's 3rd cell, days 41-45.
    { name: 'Mobilization', amount: 1000, bars: [[1, 10], [41, 45]] },
    // G8:L8 merged, coloured on G only: cells 3-8 = days 11-40.
    { name: 'Earthworks & grading', amount: 3000.5, bars: [[11, 40]] },
  ]);
  assert.deepEqual(sheets[0].durations, { Mobilization: 10, 'Earthworks & grading': 30 });
});

// The office's sheet: B items, C description, F cost, I no. of days, J.. the day cells (J = 1st Month, days 1-5). A bar's
// stretch holds the month's share of the item in its first cell; the red cells are a suspension.
const office = `<worksheet><sheetData>
  <row r="49"><c r="B49" t="s"><v>0</v></c><c r="C49" t="s"><v>1</v></c><c r="F49" t="s"><v>2</v></c><c r="I49" t="s"><v>3</v></c><c r="J49" t="s"><v>4</v></c></row>
  <row r="50"><c r="J50"><v>5</v></c><c r="K50"><v>10</v></c></row>
  <row r="51"><c r="C51" t="s"><v>5</v></c><c r="F51"><v>2000</v></c><c r="I51"><v>45</v></c>
      <c r="N51" s="1"><v>0.10</v></c><c r="O51" s="1"/><c r="P51" s="3"/><c r="Q51" s="3"/>
      <c r="R51" s="1"><v>0.21</v></c><c r="V51" s="1"><v>0.14</v></c><c r="AZ51"><v>0.45</v></c></row>
  <row r="52"><c r="C52" t="s"><v>6</v></c><c r="P52" s="3"/></row>
  <row r="53"><c r="C53" t="s"><v>7</v></c><c r="F53"><v>500</v></c><c r="I53"><v>5</v></c><c r="Y53" s="1"><v>0.05</v></c><c r="AZ53"><v>0.05</v></c></row>
  <row r="54"><c r="B54" t="s"><v>8</v></c><c r="F54"><v>2500</v></c></row>
  <row r="55"><c r="B55" t="s"><v>9</v></c><c r="F55"><v>1</v></c><c r="J55" s="1"><v>0.1</v></c></row>
  </sheetData><mergeCells><mergeCell ref="N51:O51"/><mergeCell ref="R51:U51"/><mergeCell ref="V51:X51"/></mergeCells></worksheet>`;

test('bar chart Excel: a header of NO. / DESCRIPTION / COST / DAYS / MONTH 1, and a stray "No." above it', async () => {
  const sheets = await readChartWorkbook(zip({ ...parts,
    'xl/sharedStrings.xml': parts['xl/sharedStrings.xml'].replace('ITEM NO.', 'NO.').replace('Amount (Php)', 'COST').replace('Duration (C.D)', 'DAYS').replace('Month 1', 'MONTH 1'),
    'xl/worksheets/sheet2.xml': sheet.replace('<sheetData>', '<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>No.</t></is></c><c r="B1" t="inlineStr"><is><t>12</t></is></c></row>') }));
  assert.deepEqual(sheets[0].items.map(i => [i.name, i.amount, i.bars]), [['Mobilization', 1000, [[1, 10], [41, 45]]], ['Earthworks & grading', 3000.5, [[11, 40]]]]);
  assert.deepEqual(sheets[0].durations, { Mobilization: 10, 'Earthworks & grading': 30 });
});

// A sheet drawn a day to a cell: E = Month 1's day 1, and Month 2's heading 30 cells on (AI).
const daily = `<worksheet><sheetData>
  <row r="3"><c r="A3" t="inlineStr"><is><t>NO.</t></is></c><c r="B3" t="inlineStr"><is><t>DESCRIPTION</t></is></c><c r="C3" t="inlineStr"><is><t>COST</t></is></c><c r="D3" t="inlineStr"><is><t>DAYS</t></is></c>
      <c r="E3" t="inlineStr"><is><t>MONTH 1</t></is></c><c r="AI3" t="inlineStr"><is><t>MONTH 2</t></is></c></row>
  <row r="4"><c r="E4"><v>1</v></c><c r="F4"><v>2</v></c></row>
  <row r="5"><c r="A5"><v>1</v></c><c r="B5" t="inlineStr"><is><t>Fence</t></is></c><c r="C5"><v>1000</v></c><c r="D5"><v>5</v></c>
      <c r="AG5" s="1"/><c r="AH5" s="1"/><c r="AI5" s="1"/><c r="AJ5" s="1"/><c r="AK5" s="1"/></row>
  <row r="6"><c r="A6"><v>2</v></c><c r="B6" t="inlineStr"><is><t>Gate</t></is></c><c r="C6"><v>500</v></c><c r="D6"><v>3</v></c><c r="F6" s="1"/><c r="H6" s="1"/><c r="I6" s="1"/></row>
  <row r="7"><c r="B7" t="inlineStr"><is><t>GRAND TOTAL</t></is></c><c r="C7"><v>1500</v></c></row>
  </sheetData></worksheet>`;

test('bar chart Excel: a sheet drawn a day to a cell, 30 cells from Month 1 to Month 2, gives the days exactly', async () => {
  const sheets = await readChartWorkbook(zip({ ...parts, 'xl/worksheets/sheet2.xml': daily }));
  // AG-AK are days 29-33, across the end of month 1; F is day 2, H-I days 4-5.
  assert.deepEqual(sheets[0].items, [{ name: 'Fence', amount: 1000, bars: [[29, 33]] }, { name: 'Gate', amount: 500, bars: [[2, 2], [4, 5]] }]);
  assert.deepEqual(sheets[0].durations, { Fence: 5, Gate: 3 });
});

test('bar chart Excel: the office layout, a bar\'s days from the shares written in it, the suspension colours left out', async () => {
  const sheets = await readChartWorkbook(zip({ ...parts,
    'xl/sharedStrings.xml': `<sst><si><t>ITEMS</t></si><si><t>DESCRIPTION</t></si><si><t>COST (PHP)</t></si><si><t>NO. OF DAYS</t></si><si><t>1st Month</t></si>
      <si><t>SITE PREPARATIONS</t></si><si><t>LAND DEVELOPMENT</t></si><si><t>TURNOVER</t></si><si><t>GRAND TOTAL</t></si><si><t>MONTHLY ACCOMPLISHMENT</t></si></sst>`,
    // as above, and fill 4 red: xf 3
    'xl/styles.xml': parts['xl/styles.xml'].replace('</fills>', '<fill><patternFill patternType="solid"><fgColor rgb="FFFF0000"/></patternFill></fill></fills>').replace('</cellXfs>', '<xf fillId="4"/></cellXfs>'),
    'xl/worksheets/sheet2.xml': office }));
  assert.deepEqual(sheets[0].items, [
    // 45 days as 10, 21 and 14: N-O is cells 25-30 of month 1, days 21-30; R-U is drawn as 20 days from day 41, so its 21
    // end with month 2, days 40-60; V-X follows from day 61 for 14 days, one bar with the stretch before it.
    { name: 'SITE PREPARATIONS', amount: 2000, bars: [[21, 30], [40, 74]] },
    { name: 'TURNOVER', amount: 500, bars: [[76, 80]] }, // one cell; the heading above it has no cost, the rows from the total on are not items
  ]);
  assert.deepEqual(sheets[0].durations, { 'SITE PREPARATIONS': 45, TURNOVER: 5 });
});

test('bar chart Excel: what is not a workbook is refused', async () => {
  await assert.rejects(readChartWorkbook(new TextEncoder().encode('not a zip')), NotAnExcelFile);
  await assert.rejects(readChartWorkbook(new Uint8Array(64)), NotAnExcelFile);
  await assert.rejects(readChartWorkbook(zip({ 'word/document.xml': '<document/>' })), NotAnExcelFile); // a zip, but no workbook
  assert.deepEqual(chartSheets({ 'xl/workbook.xml': '<workbook><sheets/></workbook>' }), []);
});
