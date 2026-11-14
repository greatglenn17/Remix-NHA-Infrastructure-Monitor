// BarChartExcel for the website: reads the contractor's bar chart with cash flow from its Excel file (.xlsx). Under the
// "ITEM NO. / DESCRIPTION / Amount / Duration" header, each item row with an amount, and its bar as the filled (yellow)
// cells from "Month 1" on, 6 cells of 5 days a month. The cash flow rows are worked out again from the bars, so they
// are not read. Change both when the phone's reader (util/BarChartExcel.kt) changes.

const CELL_DAYS = 5, MAX_BYTES = 20 * 1024 * 1024;

export class NotAnExcelFile extends Error {
  constructor() { super('Not an Excel workbook (.xlsx)'); }
}

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
const decode = s => s.replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (whole, e) =>
  (e[0] !== '#' ? ENTITIES[e] ?? whole : String.fromCodePoint(/^#x/i.test(e) ? parseInt(e.slice(2), 16) : Number(e.slice(1)))));

/**
 * The XML's start tags (`open`, with `attrs`), end tags (`close`) and text, in order, names without their namespace
 * prefix, as the phone's pull parser reads them. Works without a DOM, so the tests run it too.
 * shortcut: an attribute value holding ">" would cut its tag short; Excel does not write one in these parts.
 */
function* xmlEvents(xml) {
  const re = /<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<!\[CDATA\[([\s\S]*?)\]\]>|<(\/?)([^\s>/]+)([^>]*?)(\/?)>|([^<]+)/g;
  for (let m; (m = re.exec(xml));) {
    if (m[1] != null) yield { text: m[1] };
    else if (m[6] != null) yield { text: decode(m[6]) };
    else if (m[3]) {
      const name = m[3].split(':').pop();
      if (m[2]) { yield { close: name }; continue; }
      const attrs = {};
      for (const a of m[4].matchAll(/([^\s=]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)) attrs[a[1].split(':').pop()] = decode(a[2] ?? a[3]);
      yield { open: name, attrs };
      if (m[5]) yield { close: name };
    }
  }
}

/** "H28" as [row 28, column 8]. */
function ref(r) {
  const m = /^([A-Za-z]*)(\d*)/.exec(r);
  return [parseInt(m[2], 10) || 0, [...m[1].toUpperCase()].reduce((n, ch) => n * 26 + ch.charCodeAt(0) - 64, 0)];
}

/** A cell's text as a number, as Kotlin's toDoubleOrNull reads it; null when it is not one. */
export const number = text => (text != null && /^[-+]?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?$/i.test(text) ? Number(text) : null);

/** Consecutive cells (0 = Month 1's first) as bars in contract days. */
function runs(cells) {
  const bars = [];
  let start = -1, prev = -2;
  for (const c of [...cells, -Infinity]) {
    if (c !== prev + 1) {
      if (start >= 0) bars.push([start * CELL_DAYS + 1, (prev + 1) * CELL_DAYS]);
      start = c;
    }
    prev = c;
  }
  return bars;
}

/** A sheet's cells (row * 100000 + column -> { row, col, style, text }) and its merged ranges as [row1, col1, row2, col2]. */
export function sheetCells(xml, strings) {
  const cells = new Map(), merges = [];
  let cur = null, capture = false;
  for (const e of xmlEvents(xml)) {
    if (e.open === 'c') {
      cur = null;
      if (e.attrs.r == null) continue;
      const [row, col] = ref(e.attrs.r);
      cur = { row, col, shared: e.attrs.t === 's', style: parseInt(e.attrs.s, 10) || 0, raw: '' };
    } else if (e.close === 'c') {
      // The text of a <c>: its <v> (a formula's is its last worked-out value), or its inline <is><t>.
      if (cur) cells.set(cur.row * 100000 + cur.col, { row: cur.row, col: cur.col, style: cur.style, text: (cur.shared ? strings[parseInt(cur.raw, 10)] ?? '' : cur.raw).trim() });
      cur = null;
    } else if (e.open === 'v' || e.open === 't') capture = cur != null;
    else if (e.close === 'v' || e.close === 't') capture = false;
    else if (e.text != null) { if (capture) cur.raw += e.text; }
    else if (e.open === 'mergeCell') {
      const ends = (e.attrs.ref || '').split(':');
      if (ends.length === 2) merges.push([...ref(ends[0]), ...ref(ends[1])]);
    }
  }
  return { cells, merges };
}

/** One sheet as a chart: its items with their bars, and the Duration column to check the bars against; null when it is not laid out as one. */
function sheetChart(name, xml, strings, filled) {
  const key = (row, col) => row * 100000 + col;
  const { cells, merges } = sheetCells(xml, strings);
  const all = [...cells.values()].sort((a, b) => key(a.row, a.col) - key(b.row, b.col));
  const same = (text, other) => text.toLowerCase() === other.toLowerCase(), starts = (text, prefix) => text.toLowerCase().startsWith(prefix.toLowerCase());
  const header = all.find(c => same(c.text, 'ITEM NO.'));
  if (!header) return null;
  const column = prefix => all.find(c => c.row === header.row && starts(c.text, prefix))?.col;
  const descCol = column('DESCRIPTION'), amountCol = column('Amount'), durationCol = column('Duration');
  const dayCol = all.find(c => same(c.text, 'Month 1'))?.col;
  if (descCol == null || amountCol == null || dayCol == null) return null;
  const end = all.find(c => c.row > header.row && starts(c.text, 'TOTAL PROJECT COST'))?.row ?? Infinity;
  const lastCol = all.reduce((n, c) => Math.max(n, c.col), dayCol);
  const at = (row, col) => cells.get(key(row, col));
  const isFilled = (row, col) => { const c = at(row, col); return c != null && filled.has(c.style); };

  const items = [], durations = {};
  for (const row of new Set(all.filter(c => c.row > header.row && c.row < end).map(c => c.row))) {
    const desc = at(row, descCol)?.text ?? '', amount = number(at(row, amountCol)?.text);
    if (amount == null || !desc || amount <= 0) continue;
    const days = new Set();
    for (let col = dayCol; col <= lastCol; col++) if (isFilled(row, col)) days.add(col - dayCol);
    // A bar is often merged cells, its colour on the first one only.
    for (const [r1, c1, , c2] of merges) if (r1 === row && c1 >= dayCol && isFilled(row, c1)) for (let col = c1; col <= c2; col++) days.add(col - dayCol);
    items.push({ name: desc, amount: Math.round(amount * 100) / 100, bars: runs([...days].sort((a, b) => a - b)) });
    const said = durationCol == null ? null : number(at(row, durationCol)?.text);
    if (said != null) durations[desc] = Math.trunc(said);
  }
  return { name, items, durations };
}

/** The workbook's sheets as [name, relationship id]. */
function sheetList(xml) {
  const out = [];
  for (const e of xmlEvents(xml)) if (e.open === 'sheet') out.push([e.attrs.name ?? '', e.attrs.id ?? '']);
  return out;
}

function relationships(xml) {
  const out = {};
  for (const e of xmlEvents(xml || '')) if (e.open === 'Relationship') out[e.attrs.Id ?? ''] = e.attrs.Target ?? '';
  return out;
}

function sharedStrings(xml) {
  const out = [];
  let text = null, phonetic = 0, capture = false;
  for (const e of xmlEvents(xml || '')) {
    if (e.open === 'si') text = '';
    else if (e.close === 'si') { if (text != null) out.push(text); text = null; }
    // Phonetic runs (<rPh>) are not part of the text.
    else if (e.open === 'rPh') phonetic++;
    else if (e.close === 'rPh') phonetic--;
    else if (e.open === 't') capture = text != null && !phonetic;
    else if (e.close === 't') capture = false;
    else if (e.text != null && capture) text += e.text;
  }
  return out;
}

/** Cell style indexes (cellXfs) with a solid fill other than white. */
function filledStyles(xml) {
  const coloured = [], xfFills = []; // per fill; per cell style, its fill
  let inFills = false, inCellXfs = false, solid = false;
  for (const e of xmlEvents(xml || '')) {
    if (e.open === 'fills') inFills = true;
    else if (e.close === 'fills') inFills = false;
    else if (e.open === 'cellXfs') inCellXfs = true;
    else if (e.close === 'cellXfs') inCellXfs = false;
    else if (e.open === 'fill' && inFills) { solid = false; coloured.push(false); }
    else if (e.open === 'patternFill' && inFills) solid = e.attrs.patternType === 'solid';
    else if (e.open === 'fgColor' && inFills && solid && coloured.length) coloured[coloured.length - 1] = e.attrs.rgb == null || !e.attrs.rgb.toUpperCase().endsWith('FFFFFF');
    else if (e.open === 'xf' && inCellXfs) xfFills.push(parseInt(e.attrs.fillId, 10) || 0);
  }
  return new Set(xfFills.flatMap((fill, i) => (coloured[fill] ? [i] : [])));
}

/** The unzipped workbook's sheets as [name, xml] in workbook order, and its shared strings. */
export function workbookSheets(files) {
  const workbook = files['xl/workbook.xml'];
  if (workbook == null) throw new NotAnExcelFile();
  const targets = relationships(files['xl/_rels/workbook.xml.rels']);
  const sheets = sheetList(workbook).flatMap(([name, rel]) => {
    const target = targets[rel];
    const xml = target == null ? null : files[target.startsWith('/') ? target.slice(1) : `xl/${target}`];
    return xml == null ? [] : [[name, xml]];
  });
  return { sheets, strings: sharedStrings(files['xl/sharedStrings.xml']) };
}

/** BarChartExcel.read on the unzipped parts (`files`: path -> XML text): the sheets laid out as a bar chart, in workbook order. */
export function chartSheets(files) {
  const { sheets, strings } = workbookSheets(files), filled = filledStyles(files['xl/styles.xml']);
  return sheets.flatMap(([name, xml]) => {
    const chart = sheetChart(name, xml, strings, filled);
    return chart?.items.length ? [chart] : [];
  });
}

/**
 * The workbook's XML parts as text; a file that is not a zip, or larger than MAX_BYTES unpacked, is refused.
 * shortcut: no Zip64 and no CRC check (an .xlsx of a bar chart never needs the first); the size limit trusts the zip's
 * own figures, and it is the user's own file read in their own tab.
 */
export async function unzip(buffer) {
  try {
    const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), utf8 = new TextDecoder();
    let eocd = bytes.length - 22;
    while (eocd >= 0 && view.getUint32(eocd, true) !== 0x06054b50) eocd--;
    if (eocd < 0) throw new NotAnExcelFile();
    const files = {};
    let at = view.getUint32(eocd + 16, true), total = 0;
    for (let n = view.getUint16(eocd + 10, true); n > 0; n--) {
      if (view.getUint32(at, true) !== 0x02014b50) throw new NotAnExcelFile();
      const method = view.getUint16(at + 10, true), packed = view.getUint32(at + 20, true), size = view.getUint32(at + 24, true);
      const nameLength = view.getUint16(at + 28, true), local = view.getUint32(at + 42, true);
      const name = utf8.decode(bytes.subarray(at + 46, at + 46 + nameLength));
      at += 46 + nameLength + view.getUint16(at + 30, true) + view.getUint16(at + 32, true);
      if (!/\.(xml|rels)$/.test(name)) continue;
      if ((total += size) > MAX_BYTES) throw new NotAnExcelFile();
      const start = local + 30 + view.getUint16(local + 26, true) + view.getUint16(local + 28, true), data = bytes.subarray(start, start + packed);
      if (method === 0) files[name] = utf8.decode(data);
      else if (method === 8) files[name] = await new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).text();
      else throw new NotAnExcelFile();
    }
    if (!Object.keys(files).length) throw new NotAnExcelFile();
    return files;
  } catch {
    throw new NotAnExcelFile();
  }
}

/** BarChartExcel.read: the chart sheets of an .xlsx file's bytes. */
export const readChartWorkbook = async buffer => chartSheets(await unzip(buffer));
