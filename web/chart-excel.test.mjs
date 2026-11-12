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

test('bar chart Excel: what is not a workbook is refused', async () => {
  await assert.rejects(readChartWorkbook(new TextEncoder().encode('not a zip')), NotAnExcelFile);
  await assert.rejects(readChartWorkbook(new Uint8Array(64)), NotAnExcelFile);
  await assert.rejects(readChartWorkbook(zip({ 'word/document.xml': '<document/>' })), NotAnExcelFile); // a zip, but no workbook
  assert.deepEqual(chartSheets({ 'xl/workbook.xml': '<workbook><sheets/></workbook>' }), []);
});
