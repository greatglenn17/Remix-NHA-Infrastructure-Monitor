// The Blue Book forms as the phone prints them: BlueBookForms.projectProfile, the weekly and monthly Project Engineer's
// Reports (.weeklyReport, .monthlyReport), the Abstract of Physical Accomplishment in the billing format (.billingAbstract), the pay items of an uploaded abstract for the Actual Accomplishment tab (abstractItemsHtml) and the pieces they use (ContractLogs, weekDocuments, weatherClock, workableDays). Change both
// together. Pure: no Firebase.
import * as L from './logic.js';

const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pct = v => `${(Number(v) || 0).toFixed(2)}%`;
/** BlueBookForms.peso: "Php 1,234.50". */
export const peso = n => `Php ${(Number(n) || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const iso = s => /^\d{4}-\d{2}-\d{2}$/.test((s || '').trim());
/** BlueBookForms.date: "2026-10-24" as "October 24, 2026"; anything else as stored. */
export function longDate(s) {
  if (!iso(s)) return s || '';
  const [y, m, d] = s.trim().split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
}
const dayMs = s => (iso(s) ? Date.parse(`${s.trim()}T00:00:00Z`) : null);
const inWeek = (start, s) => { const t = dayMs(s); return t != null && t >= start && t <= start + 6 * 86400000; };

// Weather tab colours (Color.kt WeatherFairGreen ... WeatherStormyRed), in WeatherCondition order.
export const WEATHER = [['FAIR', 'Fair', '#22C55E'], ['CLOUDY', 'Cloudy', '#F59E0B'], ['RAIN_SHOWERS', 'Rain Showers', '#38BDF8'], ['RAINY', 'Rainy', '#3B82F6'], ['STORMY', 'Stormy', '#EF4444']];
const WEEK_DAYS = ['Saturday', 'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']; // the reporting week runs Saturday to Friday
const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']; // ActivityDays.DAYS

/** BlueBookForms.weatherClock: inner ring 12 AM-11 AM, outer ring 12 PM-11 PM, one slice per hour; an unknown hour reads Fair. */
export function weatherClock(log, day = iso(log?.date) ? Number(log.date.trim().slice(8)) : '') {
  const parts = (log?.hourlyConditionsCsv || '').split(',');
  const colour = h => (log ? (WEATHER.find(w => w[0] === (parts[h] ?? 'FAIR').trim()) || WEATHER[0])[2] : 'none');
  const cx = 50, cy = 50, r = 36, inner = r * 0.6, center = r * 0.25;
  const f = n => n.toFixed(2);
  const x = (rad, deg) => f(cx + rad * Math.cos(deg * Math.PI / 180));
  const y = (rad, deg) => f(cy + rad * Math.sin(deg * Math.PI / 180));
  const wedge = (rad, i, fill) => { const a = -90 + i * 30; return `<path d='M${cx},${cy} L${x(rad, a)},${y(rad, a)} A${rad},${rad} 0 0 1 ${x(rad, a + 30)},${y(rad, a + 30)} Z' fill='${fill}'/>`; };
  let s = "<svg viewBox='0 0 100 100' class='clock' aria-hidden='true'>";
  for (let i = 0; i < 12; i++) s += wedge(r, i, colour(i + 12));
  for (let i = 0; i < 12; i++) s += wedge(inner, i, colour(i));
  s += `<circle cx='${cx}' cy='${cy}' r='${center}' fill='#fff'/>`;
  for (const rad of [r, inner, center]) s += `<circle cx='${cx}' cy='${cy}' r='${rad}' fill='none' stroke='#888' stroke-width='0.5'/>`;
  for (let i = 0; i < 12; i++) {
    const a = -90 + i * 30;
    s += `<line x1='${x(center, a)}' y1='${y(center, a)}' x2='${x(r, a)}' y2='${y(r, a)}' stroke='#888' stroke-width='0.5'/>`;
    s += `<text x='${x(r + 7, a)}' y='${y(r + 7, a)}' font-size='6' text-anchor='middle' dominant-baseline='middle'>${i === 0 ? 12 : i}</text>`;
  }
  if (day !== '') s += `<text x='${cx}' y='${cy}' font-size='8' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>${day}</text>`;
  return `${s}</svg>`;
}

/** ActivityDays.covered: the Monday-first day indexes of "Monday" or "Monday - Wednesday" (a range may wrap past Sunday). */
const dayIndex = d => DAYS.findIndex(x => x.toLowerCase() === (d || '').toLowerCase());
/** ActivityDays.split: [from, to]; to is null for one day. */
const splitDays = label => { const p = label.split('-').map(x => x.trim()); return p.length === 2 ? p : [label.trim(), null]; };
function covered(label) {
  const [from, to] = splitDays(label);
  const a = dayIndex(from);
  if (a < 0) return [];
  const b = to == null ? -1 : dayIndex(to);
  if (b < 0) return [a];
  return Array.from({ length: (b - a + 7) % 7 + 1 }, (_, i) => (a + i) % 7);
}
const startOf = label => { const i = dayIndex(splitDays(label)[0]); return i < 0 ? 7 : i; };

/** Section V rows: one per day or day range in week order (Saturday first); weekdays no activity covers stay as blank rows. */
export function activityRows(activities) {
  const labels = [];
  for (const a of activities) { const d = a.day.trim(); if (!labels.some(l => l.toLowerCase() === d.toLowerCase())) labels.push(d); }
  const done = new Set(labels.flatMap(covered));
  const blank = DAYS.slice(0, 5).filter((_, i) => !done.has(i));
  const order = l => { const i = startOf(l); return i < 7 ? (i + 2) % 7 : i; };
  return [...labels, ...blank].map((day, i) => [day, i]).sort((a, b) => order(a[0]) - order(b[0]) || a[1] - b[1]).map(([day]) => {
    const list = activities.filter(a => a.day.trim().toLowerCase() === day.toLowerCase());
    return { day, descriptions: list.map(a => a.description).filter(x => x.trim()), remarks: list.map(a => a.remarks).filter(x => x.trim()) };
  });
}

/** BlueBookForms.weekDocuments: developer requests received and letters the contractor received in the report's week, by date. */
export function weekDocuments(report, letters, requests) {
  const start = L.weekStart(report.reportingWeek);
  if (start == null) return [];
  const fromDeveloper = requests.filter(r => inWeek(start, r.dateReceived)).map(r => [
    `Developer's request: ${r.requestType || ''}${r.details ? ` – ${r.details}` : ''}`, r.dateReceived,
    r.datePackaged ? `Packaged ${longDate(r.datePackaged)}` : 'Received; not yet packaged',
    r.dateRequested ? `Requested ${longDate(r.dateRequested)}` : '',
  ]);
  const received = letters.filter(l => inWeek(start, l.dateReceived)).map(l => [l.subject || '', l.dateReceived, 'Letter issued', `Received ${longDate(l.dateReceived)}`]);
  return [...fromDeveloper, ...received].sort((a, b) => a[1].localeCompare(b[1])).map(r => [r[0], longDate(r[1]), r[2], r[3]]);
}


/** ContractLogs.extensions: each Time Extension's number, days and "(for approval)" until approved. */
export const extensionsOf = docs => L.byOrderNumber(docs.filter(d => /^time extension/i.test(d.documentName || ''))).map(d => {
  // "Time Extension No. 1 - 30 Days" as the phone names it; an older "Time Extension No. 1 (30 days)" reads too.
  const no = /\bNo\.\s*([^\s(-]+)/i.exec(d.documentName), days = /(\d+)\s*Days/i.exec(d.documentName);
  return { no: no?.[1] || '', days: Number(days?.[1]) || 0, reason: L.remarkField(d.remarks, 'Reason'), status: /^approved$/i.test(d.status || '') ? '' : '(for approval)' };
});

/** ProjectViewModel.signatoriesFor(weekly = true): saved names, else the signed-in user and the form's usual positions. */
export function weeklySignatories(project, user) {
  const district = project.implementingOffice || 'Bulacan District Office';
  return {
    preparedName: project.preparedByName || user.name || '', preparedPosition: project.preparedByPosition || user.title || 'Site Engineer',
    preparedOffice: project.preparedByOffice || district,
    checkedName: project.weeklyCheckedByName || '', checkedPosition: project.weeklyCheckedByPosition || 'Principal Engineer A',
    checkedOffice: project.weeklyCheckedByOffice || district,
    notedName: project.weeklyNotedByName || '', notedPosition: project.weeklyNotedByPosition || 'District Manager',
    notedOffice: project.weeklyNotedByOffice || district,
  };
}

const numbered = (count, cells) => Array.from({ length: count }, (_, i) => {
  const [first, ...rest] = cells(i);
  return `<tr><td>${i + 1}. ${esc(first ?? '')}</td>${rest.map(c => `<td>${esc(c ?? '')}</td>`).join('')}</tr>`;
}).join('');

function scopeText(json) {
  try { const a = JSON.parse(json); if (Array.isArray(a)) return a.map(o => esc(o?.name ?? '')).join('<br>'); } catch { /* plain text below */ }
  return esc(json);
}

/**
 * The weekly form's HTML, for a `.bb` sheet. `photos` are [src, title] pairs (images only); `docs`, `letters`
 * and `requests` are the project's records (letters already merged); `banner` is the NHA header image URL.
 */
export function weeklyReportHtml({ project, report, docs, letters, requests, photos, signatories: s, banner }) {
  const manpower = L.manpowerOf(report.manpowerJson), equipment = L.equipmentOf(report.equipmentJson);
  const accomplishment = L.accomplishmentOf(report.accomplishmentItemsJson, L.isCommunityFacility(project.projectType));
  const weather = Array.isArray(report._weather) ? report._weather : [];
  const b = [header(banner, `For the Week of ${esc((report.reportingWeek || '').replace(/^Week \d+: /, ''))}`),
    basicInfo(project, docs, 'DAYS ELAPSED:', `${esc(report.daysElapsed ?? 0)} calendar days`, 'REMAINING DAYS:', `${esc(report.remainingDays ?? 0)} calendar days`)];

  b.push(`<table><tr><th class='sec'>II. DAILY WEATHER CHART</th></tr><tr><td><div class='legend'>LEGEND: ${WEATHER.map(([, label, hex]) => `<span><i style='background:${hex}'></i>${label}</span>`).join('')}</div>
    <table class='clocks'><tr>${WEEK_DAYS.map(d => `<th>${d.toUpperCase()}</th>`).join('')}</tr><tr>${WEEK_DAYS.map(day => {
      const log = weather.find(w => (w.dayOfWeek || '').toLowerCase() === day.toLowerCase());
      return `<td>${weatherClock(log)}<div class='small'>${esc(log?.date ? longDate(log.date) : '')}</div></td>`;
    }).join('')}</tr></table></td></tr></table>`);

  const mark = (e, letter) => (e && e.status.toUpperCase().startsWith(letter) ? '&#10003;' : '');
  b.push(`<table><tr><th colspan='2' class='sec'>III. MANPOWER</th><th colspan='6' class='sec'>IV. TOOLS, PLANT AND EQUIPMENT (O-Operational, I-Idle, R-Reserve)</th></tr>
    <tr><th rowspan='2'>DESIGNATION</th><th rowspan='2'>NO.</th><th rowspan='2'>DESCRIPTION</th><th rowspan='2'>NO.</th><th colspan='3'>STATUS</th><th rowspan='2'>REMARKS</th></tr><tr><th>O</th><th>I</th><th>R</th></tr>
    ${Array.from({ length: Math.max(7, manpower.length, equipment.length) }, (_, i) => {
      const m = manpower[i], e = equipment[i];
      return `<tr><td>${i + 1}. ${esc(m?.designation ?? '')}</td><td class='n'>${m ? m.count : ''}</td><td>${i + 1}. ${esc(e?.description ?? '')}</td><td class='n'>${e ? e.count : ''}</td>`
        + `<td class='c'>${mark(e, 'O')}</td><td class='c'>${mark(e, 'I')}</td><td class='c'>${mark(e, 'R')}</td><td>${esc(e?.remarks ?? '')}</td></tr>`;
    }).join('')}
    <tr class='tot'><td>TOTAL (Weekly)</td><td class='n'>${manpower.reduce((t, m) => t + m.count, 0)}</td><td>TOTAL (Weekly)</td><td class='n'>${equipment.reduce((t, e) => t + e.count, 0)}</td><td colspan='4'></td></tr></table>`);

  b.push(`<table><tr><th colspan='3' class='sec'>V. ACTIVITIES OF THE WEEK</th></tr><tr><th>DAY OF THE WEEK</th><th>DESCRIPTION</th><th>REMARKS</th></tr>
    ${activityRows(L.activitiesOf(report.activitiesJson)).map(r => `<tr><td class='day'>${esc(r.day.toUpperCase())}</td><td>${r.descriptions.map(esc).join('<br>')}</td><td>${r.remarks.map(esc).join('<br>')}</td></tr>`).join('')}</table>`);

  // Only the report's own (construction) issues; the Issues log is the project's as a whole. A blank "action by" is the Implementing Office's.
  const issueRows = L.issuesOf(report.issuesJson);
  b.push(`<table><tr><th colspan='4' class='sec'>VI. ISSUES / PROBLEMS ENCOUNTERED FOR THIS WEEK</th></tr><tr><th rowspan='2'>DESCRIPTION</th><th colspan='2'>ACTION TAKEN</th><th rowspan='2'>REMARKS</th></tr><tr><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/DEVELOPER</th></tr>
    ${numbered(Math.max(5, issueRows.length), i => {
      const x = issueRows[i], byContractor = x?.actionBy === L.ISSUE_ACTION_BY[1];
      return [x?.description, byContractor ? '' : x?.actionTaken, byContractor ? x?.actionTaken : '', x?.remarks];
    })}</table>`);

  b.push(`<table><tr><th colspan='4' class='sec'>VII. WEEKLY ACCOMPLISHMENT STATUS</th></tr><tr><th>ITEM DESCRIPTION</th><th>TARGET ACCOMPLISHMENT</th><th>ACTUAL ACCOMPLISHMENT</th><th>VARIANCE</th></tr>
    ${Array.from({ length: Math.max(5, accomplishment.length) }, (_, i) => {
      const a = accomplishment[i];
      return `<tr><td>${esc(a?.itemDescription ?? '')}</td><td class='n'>${a ? pct(a.targetPct) : ''}</td><td class='n'>${a ? pct(a.actualPct) : ''}</td><td class='n'>${a ? pct(a.variancePct) : ''}</td></tr>`;
    }).join('')}
    <tr class='tot'><td>GRAND TOTAL</td><td class='n'>${pct(report.targetAccomplishmentPct)}</td><td class='n'>${pct(report.actualAccomplishmentPct)}</td><td class='n'>${pct((Number(report.actualAccomplishmentPct) || 0) - (Number(report.targetAccomplishmentPct) || 0))}</td></tr></table>`);

  const issued = weekDocuments(report, letters, requests);
  b.push(`<table><tr><th colspan='4' class='sec'>VIII. DOCUMENTS ISSUED (If any) – SITE INSTRUCTION / SITE MEMORANDUM / NON-CONFORMANCE REPORT, ETC.</th></tr>
    <tr><th rowspan='2'>DOCUMENTS ISSUED / RECEIVED BY THE CONTRACTOR/DEVELOPER</th><th colspan='3'>ACTION TAKEN</th></tr><tr><th>DATE</th><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/ DEVELOPER</th></tr>
    ${numbered(Math.max(5, issued.length), i => issued[i] || [null, null, null, null])}</table>`);

  const pairs = [];
  for (let i = 0; i < photos.length; i += 2) pairs.push(photos.slice(i, i + 2));
  b.push(`<table class='photos'><tr><th colspan='2' class='sec'>IX. ATTACHED PHOTOS FOR THE UPDATES IN THE SITE</th></tr>
    ${photos.length ? '' : "<tr><td colspan='2' class='empty'>No photos attached.</td></tr>"}
    ${pairs.map(pair => `<tr>${pair.map(([src, title]) => `<td><img src='${esc(src)}' alt='${esc(title || 'Site photo')}'>${title ? `<div>${esc(title)}</div>` : ''}</td>`).join('')}${pair.length === 1 ? '<td></td>' : ''}</tr>`).join('')}</table>`);

  b.push(`<div class='keep'><p class='note'>This form must be submitted to NHA Regional Office, duly accomplished and signed by the concerned personnel on a weekly basis.</p>
    ${signatures(s, 'Checked and Reviewed by')}</div>`);
  return b.join('\n');
}

/** BlueBookForms.header, with the banner on top. */
const header = (banner, period) => `${banner ? `<img class='banner' src='${esc(banner)}' alt='National Housing Authority'>` : ''}
  <h1>PROJECT ENGINEER&#8217;S REPORT</h1><div class='period'>${period}</div>`;

/** BlueBookForms.basicInfo: section I of both reports; the right-hand cells of the cost rows differ (values are HTML). */
function basicInfo(project, docs, label1, value1, label2, value2) {
  const te = extensionsOf(docs).map(e => esc(`No. ${e.no}: ${e.days} c.d.${e.status ? ` ${e.status}` : ''}`)).join('<br>') || 'None';
  const revised = L.revisedCompletion(project, docs) ?? L.effectiveCompletion(project);
  return `<table class='info'>
    <tr><th colspan='4' class='sec'>I. BASIC INFORMATION</th></tr>
    <tr><td class='lab'>PROJECT NAME</td><td colspan='3'>${esc(project.name)}</td></tr>
    <tr><td class='lab'>LOCATION</td><td colspan='3'>${esc(project.location)}</td></tr>
    <tr><td class='lab'>IMPLEMENTING OFFICE</td><td colspan='3'>${esc(project.implementingOffice)}</td></tr>
    <tr><td class='lab'>CONTRACTOR</td><td colspan='3'>${esc(project.contractor)}</td></tr>
    <tr><td class='lab'>SCOPE OF WORK</td><td colspan='3'>${scopeText(project.scopeOfWork ?? '')}</td></tr>
    <tr><td class='lab'>CONTRACT COST (ORIGINAL)</td><td class='nw'>${esc(peso(L.originalCost(project)))}</td><td class='lab'>${label1}</td><td>${value1}</td></tr>
    <tr><td class='lab'>CONTRACT COST (REVISED)</td><td class='nw'>${esc(peso(L.currentCost(project, docs)))}</td><td class='lab'>${label2}</td><td>${value2}</td></tr>
    <tr><td class='lab'>CONTRACT DURATION</td><td colspan='3'>${esc(project.contractDurationDays ?? 0)} calendar days</td></tr>
    <tr><td class='lab'>DATE STARTED</td><td>${esc(longDate(project.dateStarted))}</td><td class='lab'>TIME EXTENSION/S</td><td>${te}</td></tr>
    <tr><td class='lab'>COMPLETION DATE (ORIGINAL)</td><td>${esc(longDate(project.completionDateOriginal))}</td><td class='lab'>REVISED COMPLETION DATE</td><td>${esc(longDate(revised))}</td></tr>
  </table>`;
}

/** BlueBookForms.signatures. */
function signatures(s, checkedTitle, notedTitle = 'Noted by') {
  return signRow(s, [['Prepared by:', 'prepared'], [`${checkedTitle}:`, 'checked'], [`${notedTitle}:`, 'noted']]);
}

/** One row of signers, each [its title as printed, its key in `s`]: the title, the name in capitals over the line, the position and office. */
function signRow(s, signers) {
  const office = name => ((name || '').trim() ? `<br>${esc(name.trim())}` : '');
  return `<table class='sig'><tr>${signers.map(([title, k]) => `<td>${title}<br><br><br><b>${esc((s[`${k}Name`] || '').toUpperCase())}</b><div class='line'></div>${esc(s[`${k}Position`])}${office(s[`${k}Office`])}</td>`).join('')}</tr></table>`;
}

/* ---------- The Abstract of Physical Accomplishment in the billing format (BlueBookForms.billingAbstract) ---------- */

const money = n => (Number(n) || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** A quantity: 2 decimals at most, none when whole. */
const qty = n => (Number(n) || 0).toLocaleString('en-US', { maximumFractionDigits: 2 });

/**
 * The abstract the contractor attaches before a billing, on a landscape sheet laid out as the district's billing
 * format, printed from the uploaded `abstract` (Actual Accomplishment tab): each line's contract quantity, unit cost
 * and amount, what was billed before, in this billing (`period`, the typed figures of L.billingAbstract) and to date
 * (quantity, amount, share of the line). `units` is the project's lots or housing units. Its target and actual are
 * the Print abstract's (actualAbstractHtml): the plan on the "As of" date and the abstract's own overall actual, not
 * what is billed. It has neither BALANCE nor equivalent-units columns. Its signers are its own four (billingSignatories).
 */
export function billingAbstractHtml({ project, docs, payments, abstract, period, units, signatories, banner }) {
  const { parts, targetPct } = L.billingAbstract(project, docs, payments, abstract, period, units), covered = String(period.covered ?? '').trim();
  const sumOf = lines => Object.fromEntries(['amount', 'previous', 'thisPeriod'].map(k => [k, lines.reduce((t, l) => t + l[k], 0)]));
  // Previous, this period, to date: quantity (a line's only), amount, share of the row's contract amount.
  const billed = (r, unitCost) => [r.previous, r.thisPeriod, r.previous + r.thisPeriod].map(x =>
    `<td class='n'>${unitCost > 0 ? qty(x / unitCost) : ''}</td><td class='n'>${money(x)}</td><td class='n'>${r.amount > 0 ? pct(x / r.amount * 100) : ''}</td>`).join('');
  const line = l => {
    const unitCost = l.amount / units;
    return `<tr><td class='c'>${l.no}</td><td>${l.name}</td><td class='n'>${qty(units)}</td><td class='c'>${l.unit}</td><td class='n'>${money(unitCost)}</td><td class='n'>${money(l.amount)}</td>${billed(l, unitCost)}</tr>`;
  };
  const total = (label, r) => `<tr class='tot'><td></td><td>${esc(label)}</td><td></td><td></td><td class='n'>${money(r.amount / units)}</td><td class='n'>${money(r.amount)}</td>${billed(r, 0)}</tr>`;
  const all = sumOf(parts.flatMap(p => p.lines));
  return `${apaTop(banner, covered ? `Period covered: ${covered}` : `As of ${longDate(abstract.asOf)}`, project, docs, targetPct, Number(abstract.overallPct) || 0)}
    <table class='apa'>
      ${apaHead()}
      ${parts.map(p => `${p.heading ? `<tr><td class='c'>${p.heading[0]}</td><td colspan='14'>${p.heading[1]}</td></tr>` : ''}${p.lines.map(line).join('')}${total(`Subtotal (${p.name})`, sumOf(p.lines))}`).join('')}
      ${total('GRAND TOTAL', all)}
    </table>
    <div class='keep'>${signRow(signatories, [['Prepared by:', 'prepared'], ['Checked by:', 'checked'], ['Reviewed by:', 'reviewed'], ['Recommending Approval', 'recommended']])}</div>`;
}

/**
 * The district's column layout of an Abstract of Physical Accomplishment under three header rows (BlueBookForms.apaHead):
 * 15 columns, up to TO DATE. Neither printed abstract has the sheet's BALANCE columns. An abstract in terms of a
 * variation order has `earlier` more columns under each of QTY, UNIT COST and AMOUNT: "Original", then "V.O.#1" on.
 */
function apaHead(earlier = 0) {
  // shortcut: the columns are named in order, not as the sheet names them; keep the sheet's names when one differs.
  const names = ['Original', ...Array.from({ length: earlier }, (_, i) => `V.O.#${i + 1}`)].map(name => `<th>${name}</th>`).join('');
  const over = name => (earlier ? `<th rowspan='2' colspan='${earlier + 1}'>${name}</th>` : `<th rowspan='3'>${name}</th>`);
  return `<tr><th rowspan='3'>ITEM NO.</th><th rowspan='3'>ITEM DESCRIPTION</th>${over('QTY')}<th rowspan='3'>UNIT</th>${over('UNIT COST')}${over('AMOUNT')}<th colspan='9'>ACCOMPLISHMENT</th></tr>
      <tr><th colspan='3'>PREVIOUS</th><th colspan='3'>THIS PERIOD</th><th colspan='3'>TO DATE</th></tr>
      <tr>${earlier ? names.repeat(3) : ''}${'<th>QTY</th><th>AMOUNT</th><th>% WT</th>'.repeat(3)}</tr>`;
}

/**
 * The top of both abstracts (BlueBookForms.apaTop): the banner, the title, the `period` line, then the project, its
 * contract (its duration and dates at the sheet's right edge) and, after a blank line, its target, actual and
 * advance/slippage, the last as the two at 2 decimals each.
 */
function apaTop(banner, period, project, docs, targetPct, actual) {
  const info = (label, value, label2 = '', value2 = '') => `<tr><td class='pl'>${label}</td><td class='pc'>:</td><td>${esc(value)}</td><td class='pl'>${label2}</td><td>${label2 ? `: ${esc(value2)}` : ''}</td></tr>`;
  return `${banner ? `<img class='banner' src='${esc(banner)}' alt='National Housing Authority'>` : ''}
    <h1>ABSTRACT OF PHYSICAL ACCOMPLISHMENT</h1>
    <div class='period'>${esc(period)}</div>
    <table class='apa-info'>
      ${info('PROJECT NAME', project.name, 'CONTRACT DURATION', `${project.contractDurationDays ?? 0} C.D.`)}
      ${info('LOCATION', project.location, 'DATE STARTED', longDate(project.dateStarted))}
      ${info('CONTRACTOR', project.contractor, 'COMPLETION DATE', longDate(L.effectiveCompletion(project)))}
      ${info('CONTRACT COST', peso(L.currentCost(project, docs)))}
      <tr><td colspan='5'>&nbsp;</td></tr>
      ${info('TARGET ACCOMPLISHMENT', pct(targetPct))}
      ${info('ACTUAL ACCOMPLISHMENT', pct(actual))}
      ${info('ADVANCE/SLIPPAGE', pct(Math.round(actual * 100) / 100 - Math.round(targetPct * 100) / 100))}
    </table>`;
}

/* ---------- The Abstract of Physical Accomplishment of the actual work, the detailed one (BlueBookForms.actualAbstract) ---------- */

/**
 * The abstract of the actual work on a landscape sheet (Print abstract on the Actual Accomplishment tab): the same
 * top as the billing format, with the target the tab shows for its "As of" date (L.abstractStanding's Overall) and
 * the abstract's own actual, then every row of `abstract` in the abstract's columns up to TO DATE, an empty cell
 * left blank, and the signatures. The header rows repeat on each page. Neither BALANCE nor the sheet's "EQUIVALENT
 * TO <N> UNITS" columns are shown. An abstract in terms of a variation order (its rows have `was`) prints as its
 * sheet: the Original quantity, unit cost and amount beside the V.O.'s.
 */
export function actualAbstractHtml({ project, docs, abstract, signatories, banner }) {
  const overall = L.abstractStanding(project, docs, abstract).at(-1);
  const n = (v, show) => `<td class='n'>${v == null ? '' : show(v)}</td>`;
  const quantity = v => Number(v).toLocaleString('en-US', { maximumFractionDigits: 4 });
  const figures = t => n(t?.[0], quantity) + n(t?.[1], money) + n(t?.[2], pct);
  const rows = L.abstractRows(abstract.rowsJson), earlier = Math.max(0, ...rows.map(r => r.was?.length ?? 0));
  // A figure after the same one in each earlier column (`was`: 0 quantity, 1 unit cost, 2 amount).
  const cells = (r, i, v, show) => Array.from({ length: earlier }, (_, j) => n(r.was?.[j]?.[i], show)).join('') + n(v, show);
  const row = r => `<tr${r.kind === 'subtotal' || r.kind === 'total' ? " class='tot'" : ''}><td class='c'>${esc(r.no)}</td><td>${r.kind === 'part' || r.kind === 'group' ? `<b>${esc(r.name)}</b>` : esc(r.name)}</td>${cells(r, 0, r.qty, quantity)}<td class='c'>${esc(r.unit)}</td>`
    + `${cells(r, 1, r.unitCost, money)}${cells(r, 2, r.amount, money)}${figures(r.prev)}${figures(r.period)}${figures(r.toDate)}</tr>`;
  return `${apaTop(banner, `As of ${longDate(abstract.asOf)}`, project, docs, overall.target, overall.actual)}
    <table class='apa'>
      <thead>${apaHead(earlier)}</thead>
      <tbody>${rows.map(row).join('')}</tbody>
    </table>
    <div class='keep'>${signatures(signatories, 'Checked by', 'Approved by')}</div>`;
}

/* ---------- The Actual Accomplishment tab's pay items (an uploaded abstract's rows, every column as in Excel) ---------- */

/**
 * An uploaded abstract's rows (L.abstractRows) for the site's Actual Accomplishment tab: each part a folding block
 * headed by its item no., name, amount and % to date, holding its groups, pay items and subtotals in the sheet's
 * columns up to TO DATE, then the grand total. The sheet's BALANCE and "EQUIVALENT TO <N> UNITS" columns are not
 * shown, as on the printed abstracts, so the table fits the page's width. Empty cells stay blank; the workbook's text
 * is escaped.
 */
export function abstractItemsHtml(rows) {
  const cell = (v, show) => `<td class="num">${v == null ? '' : show(v)}</td>`;
  const quantity = v => Number(v).toLocaleString('en-US', { maximumFractionDigits: 4 });
  const figures = t => cell(t?.[0], quantity) + cell(t?.[1], money) + cell(t?.[2], pct);
  const line = r => (r.kind === 'group' ? `<tr class="apa-group"><td>${esc(r.no)}</td><td colspan="14">${esc(r.name)}</td></tr>`
    : `<tr class="apa-${esc(r.kind)}"><td>${esc(r.no)}</td><td>${esc(r.name)}</td>${cell(r.qty, quantity)}<td>${esc(r.unit)}</td>${cell(r.unitCost, money)}${cell(r.amount, money)}`
      + `${figures(r.prev)}${figures(r.period)}${figures(r.toDate)}</tr>`);
  const table = body => `<div class="scroll"><table class="grid-table apa-items"><thead>
    <tr><th rowspan="2">ITEM NO.</th><th rowspan="2">ITEM DESCRIPTION</th><th rowspan="2">QTY</th><th rowspan="2">UNIT</th><th rowspan="2">UNIT COST</th><th rowspan="2">AMOUNT</th><th colspan="3">PREVIOUS</th><th colspan="3">THIS PERIOD</th><th colspan="3">TO DATE</th></tr>
    <tr>${'<th>QTY</th><th>AMOUNT</th><th>% WT</th>'.repeat(3)}</tr></thead><tbody>${body.map(line).join('')}</tbody></table></div>`;
  // Rows under each part; rows before the first part, if any, get a block without a heading.
  const parts = L.abstractParts(rows), total = rows.filter(r => r.kind === 'total'), blocks = [];
  for (const r of rows.filter(x => x.kind !== 'total')) {
    // A part that is its own pay item (Land Acquisition) shows its quantity and figures as the first line under it.
    if (r.kind === 'part') blocks.push({ part: r, sum: parts[blocks.filter(b => b.part).length], rows: L.isPayLine(r) ? [{ ...r, kind: 'item' }] : [] });
    else { if (!blocks.length) blocks.push({ rows: [] }); blocks.at(-1).rows.push(r); }
  }
  return blocks.map(b => (b.part ? `<details class="card apa-part" open><summary><span class="icon sm" aria-hidden="true">expand_more</span><b>${esc(b.part.no)} ${esc(b.part.name)}</b>
      <span class="num">${money(b.sum.amount)}</span><span class="num">${pct(b.sum.actualPct)}</span></summary>${b.rows.length ? table(b.rows) : ''}</details>`
    : `<div class="card apa-part">${table(b.rows)}</div>`)).join('') + (total.length ? `<div class="card apa-part">${table(total)}</div>` : '');
}

/* ---------- The Project Profile sheet (BlueBookForms.projectProfile) ---------- */

/** ContractLogs.resumptions: each Work Resumption Order with its effectivity, "(for approval)" until approved. */
export const resumptionsOf = docs => L.byEffectivity(docs.filter(d => /work resumption/i.test(d.documentName || '')))
  .map(d => [L.orderLabel(d.documentName), longDate(L.remarkField(d.remarks, 'Effectivity')) + (/^approved$/i.test(d.status || '') ? '' : ' (for approval)')]);

/** The Project Profile sheet; `preparer` is {name, title, office} and `asOf` an ISO date. */
export function projectProfileHtml({ project, docs, preparer, asOf, banner }) {
  const rows = [];
  const row = (label, value, cls = 'pl') => rows.push(`<tr><td class='${cls}'>${esc(label)}</td><td class='pc'>:</td><td>${value}</td></tr>`);
  const sub = (label, value) => row(label, value, 'pl ps');
  // The reason goes on its own line, under the days or amount.
  const withReason = (value, status, reason) => esc(value + (status ? ` ${status}` : '')) + (reason ? `<br>(${esc(reason)})` : '');
  const { suspensions, variations } = ordersOf(docs), extensions = extensionsOf(docs), resumptions = resumptionsOf(docs);
  // ContractCost.revised: blank until a variation order is approved.
  const revised = docs.some(d => /variation order/i.test(d.documentName || '') && /^approved$/i.test(d.status || '')) ? peso(L.currentCost(project, docs)) : '';
  const when = longDate(asOf), actual = Number(project.actualAccomplishment) || 0, target = Number(project.targetAccomplishment) || 0;
  row('Project Title', esc(project.name));
  row('Project Location', esc(project.location));
  const facility = L.isCommunityFacility(project.projectType);
  if (!facility) row('Projected Beneficiaries', esc(project.projectedBeneficiaries));
  row('Contractor', esc(project.contractor));
  row('Board Resolution/Date', esc(project.boardResolution));
  row('Notice of Award', esc(longDate(project.noticeOfAward)));
  row('Notice to Proceed', esc(longDate(project.noticeToProceed)));
  row('Date Commenced', esc(longDate(project.dateStarted)));
  if (!facility) { row('Land Area', esc(project.landArea)); row('Number of Units', esc(project.numberOfUnits)); }
  row('Original Contract Amount', esc(peso(L.originalCost(project))));
  row('Original Contract Duration', esc(`${project.contractDurationDays ?? 0} calendar days (c.d.)`));
  row('Contract Suspension/s', suspensions.length ? '' : 'None');
  // A suspension reads as three lines: its days, its effectivity dates, its reason.
  suspensions.forEach(o => sub(o.label, withReason(o.value.replace(' – ', '\n'), o.status, o.reason).replace('\n', '<br>')));
  row('Contract Resumption', resumptions.length ? '' : 'None');
  resumptions.forEach(([label, value]) => sub(label, esc(value)));
  row('Contract Time Extensions', extensions.length ? '' : 'None');
  extensions.forEach(e => sub(`Time Extension No. ${e.no}`, withReason(`${e.days} calendar days`, e.status, e.reason)));
  row('Variation Order/s', variations.length ? '' : 'None');
  variations.forEach(o => sub(o.label, withReason(o.value, o.status, o.reason)));
  row('Revised Contract Cost', esc(revised));
  row('Revised Completion Date', esc(longDate(L.revisedCompletion(project, docs) ?? L.effectiveCompletion(project))));
  row(`% Actual Accomplishment as of ${when}`, esc(pct(actual)));
  row(`% Target Accomplishment as of ${when}`, esc(pct(target)));
  row('% Delay/Advance', esc(pct(actual - target)));
  row('Status of the Project', esc(L.displayStatus(project)));
  return `${banner ? `<img class='banner' src='${esc(banner)}' alt='National Housing Authority'>` : ''}
    <h1 class='title'>PROJECT PROFILE</h1>
    <div class='profile'><table class='profile'>${rows.join('')}</table>
    <div class='sig1'>Prepared By:<br><br><br><b>${esc((preparer.name || '').toUpperCase())}</b><br>${esc(preparer.title)}<br>${esc(preparer.office)}</div></div>`;
}

/* ---------- The monthly form (BlueBookForms.monthlyReport) ---------- */

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
/** BlueBookForms.parseMonth: "September 2026" as "2026-09"; null when it is not a full month name and year. */
export function monthKey(month) {
  const m = /^([A-Za-z]+) (\d{4})$/.exec((month || '').trim());
  const i = m ? MONTH_NAMES.findIndex(n => n.toLowerCase() === m[1].toLowerCase()) : -1;
  return i < 0 ? null : `${m[2]}-${String(i + 1).padStart(2, '0')}`;
}
/** "2026-09" as "September 2026". */
export const monthName = key => `${MONTH_NAMES[Number(key.slice(5)) - 1]} ${key.slice(0, 4)}`;
const monthOf = s => (iso(s) ? s.trim().slice(0, 7) : null);

/** ProjectViewModel.signatoriesFor(weekly = false): Prepared by as on the weekly form; District and Regional Manager after it. */
export function monthlySignatories(project, user) {
  const w = weeklySignatories(project, user);
  return {
    ...w,
    checkedName: project.checkedByName || '', checkedPosition: project.checkedByPosition || 'District Manager',
    checkedOffice: project.checkedByOffice || project.implementingOffice || 'Bulacan District Office',
    notedName: project.notedByName || '', notedPosition: project.notedByPosition || 'Regional Manager',
    notedOffice: project.notedByOffice || 'Region 3 Office',
  };
}

/**
 * The billing abstract's own four signers (`billing*` on the project): Prepared by the contractor, Checked by the
 * signed-in engineer, then the monthly report's Checked by as Reviewed by and its Noted by as Recommending Approval.
 */
export function billingSignatories(project, user) {
  const m = monthlySignatories(project, user);
  return {
    preparedName: project.billingPreparedByName || '', preparedPosition: project.billingPreparedByPosition || '',
    preparedOffice: project.billingPreparedByOffice || project.contractor || '',
    checkedName: project.billingCheckedByName || user.name || '', checkedPosition: project.billingCheckedByPosition || user.title || 'Principal Engineer A',
    checkedOffice: project.billingCheckedByOffice || project.implementingOffice || 'Bulacan District Office',
    reviewedName: project.billingReviewedByName || m.checkedName, reviewedPosition: project.billingReviewedByPosition || m.checkedPosition,
    reviewedOffice: project.billingReviewedByOffice || m.checkedOffice,
    recommendedName: project.billingRecommendedByName || m.notedName, recommendedPosition: project.billingRecommendedByPosition || m.notedPosition,
    recommendedOffice: project.billingRecommendedByOffice || m.notedOffice,
  };
}

/** ContractLogs.suspensions and .variations: each order as the monthly form lists it, "(for approval)" until approved. */
export function ordersOf(docs) {
  const mark = d => (/^approved$/i.test(d.status || '') ? '' : '(for approval)');
  const suspensions = L.byEffectivity(docs.filter(d => /work suspension/i.test(d.documentName || ''))).map(d => {
    const ran = L.inEffect(d, docs); // the days it ran: cut short by its resumption order
    const days = L.remarkField(ran, 'Duration').split(' ')[0];
    const from = longDate(L.remarkField(d.remarks, 'Effectivity'));
    const to = L.remarkField(L.withEnd(ran), 'End');
    return {
      label: L.orderLabel(d.documentName), value: (days ? `${days} calendar days – ` : '') + (to ? `${from} to ${longDate(to)}` : from),
      reason: L.isSuspension(d) ? L.remarkField(d.remarks, 'Reason') : '', status: mark(d),
    };
  });
  const variations = L.byOrderNumber(docs.filter(d => /^variation order/i.test(d.documentName || ''))).map(d => {
    const name = d.documentName, cut = name.indexOf(' - ');
    const amount = cut < 0 ? '' : name.slice(cut + 3);
    const n = Number(amount.replace(/^PHP/, '').replace(/,/g, '').trim());
    return {
      label: (cut < 0 ? name : name.slice(0, cut)).split(' (')[0],
      value: amount.trim() && Number.isFinite(n) ? peso(n) : amount.replace(/PHP/g, 'Php'),
      reason: L.remarkField(d.remarks, 'Reason'), status: mark(d),
    };
  });
  return { suspensions, variations };
}

/**
 * BlueBookForms.workableDays: [month, workable, unworkable] per month of logged days, oldest first. A day is
 * unworkable when more than 4 of its 8 AM–4 PM hours were Rainy or Stormy.
 */
export function workableDays(weather) {
  const byDay = new Map();
  for (const w of weather) if (iso(w.date) && !byDay.has(w.date.trim())) byDay.set(w.date.trim(), w);
  const months = new Map();
  for (const [day, w] of byDay) {
    const hours = (w.hourlyConditionsCsv || '').split(',');
    const bad = [8, 9, 10, 11, 12, 13, 14, 15, 16].filter(h => ['RAINY', 'STORMY'].includes((hours[h] || '').trim())).length > 4;
    const m = months.get(day.slice(0, 7)) || [0, 0];
    m[bad ? 1 : 0]++;
    months.set(day.slice(0, 7), m);
  }
  return [...months].sort((a, b) => a[0].localeCompare(b[0])).map(([m, [ok, bad]]) => [m, ok, bad]);
}

/**
 * The monthly form's HTML for `month` ("2026-09"), for a `.bb` sheet. `weeks` are the month's weekly reports in week
 * order (the last one gives the progress); `weather` is every logged day; `saved` is the month's monthly report, if any.
 */
export function monthlyReportHtml({ project, month, weeks, payments, issues, docs, weather, saved, signatories, banner }) {
  const last = weeks[weeks.length - 1];
  const { suspensions, variations } = ordersOf(docs);
  const list = items => items.map(o => esc(`${o.label}: ${o.value}${o.reason ? ` (${o.reason})` : ''}${o.status ? ` ${o.status}` : ''}`)).join('<br>') || 'None';
  const b = [header(banner, `For the Month of ${esc(monthName(month))}`),
    basicInfo(project, docs, 'VARIATION ORDER/S', list(variations), 'WORK SUSPENSION ORDER/S', list(suspensions))];

  // A community facility's rows are its bar chart's items as the last report saved them; it has no housing lines to fall back on.
  const facility = L.isCommunityFacility(project.projectType), acc = last ? L.accomplishmentOf(last.accomplishmentItemsJson, facility) : [];
  const items = acc.length || facility ? acc : L.ACCOMPLISHMENT_LINES.map(itemDescription => ({ itemDescription, weightPct: 0, actualPct: 0, targetPct: 0 }));
  const sum = f => items.reduce((t, a) => t + f(a), 0);
  b.push(`<table><tr><th colspan='5' class='sec'>II. PROJECT STATUS</th></tr><tr><th colspan='5' class='sub'>STATUS OF ACCOMPLISHMENT</th></tr>
    <tr><th>ITEM</th><th>% WEIGHT</th><th>TARGET ACCOMPLISHMENT</th><th>ACTUAL ACCOMPLISHMENT</th><th>TOTAL</th></tr>
    ${items.map(a => `<tr><td>${esc(a.itemDescription)}</td><td class='n'>${pct(a.weightPct)}</td><td class='n'>${pct(a.targetPct)}</td><td class='n'>${pct(a.actualPct)}</td><td class='n'>${pct(a.weightPct * a.actualPct / 100)}</td></tr>`).join('')}
    <tr class='tot'><td>TOTAL</td><td class='n'>${pct(sum(a => a.weightPct))}</td><td class='n'>${last ? pct(last.targetAccomplishmentPct) : ''}</td><td class='n'>${last ? pct(last.actualAccomplishmentPct) : ''}</td><td class='n'>${pct(sum(a => a.weightPct * a.actualPct / 100))}</td></tr></table>`);

  // Billings up to month end; one with an unreadable date is kept, as on the phone. The balances are worked out from the
  // contract cost in date order, as the Payments tab shows them: the saved ones count every billing entered before, of any date.
  const paid = L.netBillings(payments, L.currentCost(project, docs)).filter(p => { const m = monthOf(p.date); return m == null || m <= month; });
  b.push(`<table><tr><th colspan='6' class='sec'>III. STATUS OF PAYMENT</th></tr><tr><th rowspan='2'></th><th rowspan='2'>DATE</th><th rowspan='2'>PERIOD COVERED</th><th colspan='2'>PAYMENTS MADE</th><th rowspan='2'>BALANCE</th></tr><tr><th>NET AMOUNT</th><th>%</th></tr>
    ${Array.from({ length: Math.max(3, paid.length) }, (_, i) => {
      const p = paid[i];
      return `<tr><td>${i + 1}. ${esc(p?.name ?? '')}</td><td>${esc(p ? longDate(p.date) : '')}</td><td>${esc(p?.periodCovered ?? '')}</td><td class='n'>${p ? esc(peso(L.netAmount(p))) : ''}</td><td class='n'>${p ? pct(p.percentage) : ''}</td><td class='n'>${p ? esc(`${peso(p.balanceAmount)} (${pct(p.balancePercentage)})`) : ''}</td></tr>`;
    }).join('')}
    <tr class='tot'><td colspan='3'>GRAND TOTAL</td><td class='n'>${esc(peso(paid.reduce((t, p) => t + L.netAmount(p), 0)))}</td><td class='n'>${pct(paid.reduce((t, p) => t + p.percentage, 0))}</td><td class='n'>${paid.length ? esc(`${peso(paid.at(-1).balanceAmount)} (${pct(paid.at(-1).balancePercentage)})`) : ''}</td></tr></table>`);

  // Each approved extension adds its days to the running duration, as the Key Dates card does; one still for approval
  // shows its days but no revised duration or date, so the last date matches section I.
  const extensions = extensionsOf(docs);
  let duration = Number(project.contractDurationDays) || 0;
  b.push(`<table><tr><th colspan='7' class='sec'>IV. CONTRACT SCHEDULE STATUS</th></tr><tr><th>TIME EXTENSION NO.</th><th>NO. OF DAYS</th><th>REVISED CONTRACT DURATION</th><th>PERIOD CONSIDERED (AS APPLICABLE)</th><th>REASON FOR TIME EXTENSION</th><th>REVISED COMPLETION DATE</th><th>REMARKS</th></tr>
    ${Array.from({ length: Math.max(4, extensions.length) }, (_, i) => {
      const t = extensions[i], counts = t && !t.status;
      if (counts) duration += t.days;
      const end = counts ? longDate(L.lastDay(project.dateStarted, duration) || '') : '';
      return `<tr><td class='c'>${esc(t ? t.no : i + 1)}</td><td class='n'>${t ? t.days : ''}</td><td class='n'>${counts ? `${duration} c.d.` : ''}</td><td></td><td>${esc(t?.reason ?? '')}</td><td>${esc(end)}</td><td>${esc(t?.status ?? '')}</td></tr>`;
    }).join('')}</table>`);

  // Only months with weather logged, numbered by contract month when the start date is known.
  const logged = workableDays(weather).filter(([m]) => m <= month);
  const first = monthOf(project.dateStarted);
  const monthNo = (m, i) => (first ? (Number(m.slice(0, 4)) - Number(first.slice(0, 4))) * 12 + Number(m.slice(5)) - Number(first.slice(5)) + 1 : i + 1);
  b.push(`<table><tr><th colspan='4' class='sec'>V. SUMMARY OF UNWORKABLE DAYS DUE TO WEATHER CONDITION</th></tr><tr><th>MONTH NO.</th><th>PERIOD COVERED</th><th>NO. OF WORKABLE DAYS</th><th>NO. OF UNWORKABLE DAYS</th></tr>
    ${logged.map(([m, ok, bad], i) => `<tr><td class='c'>${monthNo(m, i)}</td><td>${esc(monthName(m))}</td><td class='n'>${ok}</td><td class='n'>${bad}</td></tr>`).join('')}
    <tr class='tot'><td colspan='2'>GRAND TOTAL</td><td class='n'>${logged.reduce((t, x) => t + x[1], 0)}</td><td class='n'>${logged.reduce((t, x) => t + x[2], 0)}</td></tr></table>`);

  b.push(`<table><tr><th colspan='3' class='sec'>VI. STATUS OF SUBMISSION OF DOCUMENTARY REQUIREMENTS</th></tr><tr><th>DOCUMENT</th><th>STATUS</th><th>REMARKS</th></tr>
    ${numbered(Math.max(5, docs.length), i => [docs[i]?.documentName, docs[i]?.status, docs[i]?.remarks])}</table>`);

  const monthIssues = issues.filter(x => monthOf(x.date) === month);
  b.push(`<table><tr><th colspan='4' class='sec'>VII. ISSUES / CONCERNS / SITE INSTRUCTIONS</th></tr>
    <tr><th rowspan='2'>ISSUES/CONCERNS/OBSERVATIONS (INCLUDING COMPLIANCE WITH CPES REQUIREMENTS, ETC.)</th><th colspan='3'>ACTION TAKEN</th></tr><tr><th>DATE</th><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/DEVELOPER</th></tr>
    ${numbered(Math.max(5, monthIssues.length), i => {
      const x = monthIssues[i];
      if (!x) return [null, null, null, null];
      const action = [x.actionTaken, x.remarks].filter(v => (v || '').trim()).join('; ');
      // An issue logged before "action taken by" existed keeps its action under the Implementing Office.
      const byContractor = x.actionBy === 'Contractor/Developer';
      return [x.description, longDate(x.date), byContractor ? null : action, byContractor ? action : null];
    })}</table>`);

  b.push(`<div class='keep'><table><tr><th class='sec'>VIII. RECOMMENDATIONS (If any)</th></tr><tr><td class='rec'>${esc(saved?.recommendations ?? '').replace(/\n/g, '<br>')}</td></tr></table>
    <p class='note'>This form must be submitted to NHA Regional Office, duly accomplished and signed by the concerned personnel on or before end of each month.</p>
    ${signatures(signatories, 'Checked by')}</div>`);
  return b.join('\n');
}


/**
 * BlueBookForms.sdpPlan: the subdivision plan coloured by lot progress on one A4 page, with the legend of saleable units
 * per band. `sheets` are [{site, image, summary}] (image a data URI, summary from L.sdpSummary). One plan has the legend
 * over its bottom-left corner; several sit side by side (landscape) or one above the other (portrait) with the legend apart.
 */
export function sdpPlanHtml({ project, sheets, asOf, banner, landscape }) {
  const multi = sheets.length > 1;
  const span = multi ? sheets.length + 2 : 2;
  const site = s => esc(s || 'Main site');
  const counts = of => (multi ? sheets.map(x => `<td class='n'>${of(x.summary)}</td>`).join('') : '') + `<td class='n'>${sheets.reduce((t, x) => t + of(x.summary), 0)}</td>`;
  const legend = `<table class='lg'><tr><th class='sec' colspan='${span}'>LEGEND</th></tr><tr><th class='sub'>Progress</th>${multi ? sheets.map(x => `<th class='sub'>${site(x.site)}</th>`).join('') : ''}<th class='sub'>${multi ? 'Total' : 'Units'}</th></tr>
    ${L.LOT_BANDS.map((band, i) => `<tr><td><i class='sw' style='background:${L.LOT_PRINT_HEX[i]}'></i>${esc(band)}</td>${counts(s => s.bands[i])}</tr>`).join('')}
    <tr class='tot'><td>Total units</td>${counts(s => s.saleable)}</tr>
    <tr><th class='sub' colspan='${span}'>Road lot</th></tr>
    ${L.ROAD_STATES.map((state, i) => `<tr><td colspan='${span}'><i class='sw' style='background:${L.LOT_PRINT_HEX[[0, 3, 5][i]]}'></i>${esc(state)}</td></tr>`).join('')}</table>`;
  // Height left for plans under the banner and title: 244mm portrait, 157mm landscape; a site name takes 6mm.
  const room = landscape ? 157 : 244;
  const maxHeight = !multi ? room : landscape ? room - 6 : Math.trunc((room - 12 * 6 - sheets.length * 9) / sheets.length);
  const style = multi || landscape ? ` style='max-height:${maxHeight}mm'` : '';
  const plan = !multi ? `<div class='plan'><img src='${esc(sheets[0].image)}' alt='Subdivision plan'${style}>${legend}</div>`
    : `<div class='plan multi${landscape ? ' row' : ''}'>${sheets.map(x => `<figure><figcaption>${site(x.site)}</figcaption><img src='${esc(x.image)}' alt='${site(x.site)} plan'${style}></figure>`).join('')}${legend}</div>`;
  return `${banner ? `<img class='banner' src='${esc(banner)}' alt='National Housing Authority'>` : ''}<h1>STATUS OF CONSTRUCTION</h1>
    <div class='period'>${esc([project.name, project.location].filter(x => (x || '').trim()).join(', '))} &#183; as of ${esc(longDate(asOf))}</div>${plan}`;
}

/* ---------- The bar chart / S-curve with cash flow (the office's sheet, less its PERT/CPM network; website only) ---------- */

const ordinal = n => `${n}${n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] || 'th'}`;
const CHART_ROWS = ['MONTHLY ACCOMPLISHMENT', 'CUMULATIVE ACCOMPLISHMENT', 'MONTHLY CASHFLOW (PHP)', 'CUMULATIVE CASHFLOW (PHP)'];

/**
 * One chart `version` drawn as the office's sheet, in millimetres, `width` across (a landscape A4 unless given): each item's cost, share of the
 * total and days, its days of work in green and its coloured days in the chart's own colours (L.dayFills) over a grid
 * of 30-day months in 5-day cells, with each month's share of the project written on them, the S-curve over the bars,
 * and under the total the monthly and cumulative accomplishment and cash flow, then what the colours stand for. An
 * SVG, so the bars and the curve line up with the grid and print without the browser's background setting.
 * `duration` is the total's days, the contract's (the bars' days overlap, so they do not add up to it); blank without one.
 * The item columns keep their width on a wider sheet, so the months take the rest.
 */
export function barChartSvg(version, duration, width = 285) {
  const items = version.items || [], total = items.reduce((t, i) => t + (Number(i.amount) || 0), 0), colors = L.chartColors(version);
  const curved = L.chartMonths(version), all = Math.max(curved.length, Math.ceil(L.chartSpan(version) / 30));
  // A month after the last bar (coloured days only) has no work in it.
  const months = Array.from({ length: all }, (_, k) => curved[k] ?? { no: k + 1, monthlyPct: 0, cumulativePct: curved.at(-1)?.cumulativePct ?? 0 });
  const count = Math.max(months.length, 1), first = 1, last = months.length * 30;
  const W = width, COLS = [0, 10, 62, 86, 99, 109], left = COLS[5], mw = (W - left) / count, dw = mw / 30;
  const HEAD = 9, ROW = 6, FOOT = 5, bottom = HEAD + items.length * ROW, foot = bottom + ROW, H = foot + CHART_ROWS.length * FOOT;
  const f = n => Number(n.toFixed(2)), dayX = day => left + (day - first) * dw;
  // Text is not measured here: one wider than its `room` is squeezed into it.
  const text = (x, y, value, { anchor = 'middle', size = 2, bold = false, room = 0 } = {}) => {
    const fits = !room || String(value).length * size * (bold ? 0.65 : 0.55) <= room;
    return `<text x='${f(x)}' y='${f(y)}' font-size='${size}' text-anchor='${anchor}'${bold ? " font-weight='bold'" : ''}${fits ? '' : ` textLength='${f(room)}' lengthAdjust='spacingAndGlyphs'`}>${esc(value)}</text>`;
  };
  const line = (x1, y1, x2, y2, width = 0.15) => `<line x1='${f(x1)}' y1='${f(y1)}' x2='${f(x2)}' y2='${f(y2)}' stroke='#000' stroke-width='${width}'/>`;
  const mid = k => (COLS[k] + COLS[k + 1]) / 2;
  const itemDays = i => (i.bars || []).reduce((n, [a, b]) => n + Math.max(0, b - a + 1), 0);
  const fill = k => (k === 'work' ? '#92D050' : colors[k].color);

  const head = ['ITEMS', 'DESCRIPTION', 'COST (PHP)', 'PERCENT (%)', 'NO. OF DAYS'].map((h, k) => text(mid(k), 5.3, h, { size: 1.9, bold: true, room: COLS[k + 1] - COLS[k] - 1 })).join('')
    + months.map((m, k) => text(left + (k + 0.5) * mw, 3.3, mw >= 14 ? `${ordinal(m.no)} Month` : m.no, { size: 1.9, bold: true })
      + (mw >= 15 ? [5, 10, 15, 20, 25, 30].map((d, j) => text(left + k * mw + (j + 0.5) * mw / 6, 7.7, d, { size: 1.5 })).join('') : '')).join('');
  const rows = items.map((item, i) => {
    const y = HEAD + i * ROW, days = itemDays(item), amount = Number(item.amount) || 0, base = y + 4;
    // A rectangle for each run of days that show the same thing.
    let bars = '';
    const fills = L.dayFills(item, colors, last);
    for (let d = first; d <= last; d++) {
      const k = fills[d - 1];
      if (k == null) continue;
      let e = d;
      while (e < last && fills[e] === k) e++;
      bars += `<rect x='${f(dayX(d))}' y='${f(y + 1)}' width='${f((e - d + 1) * dw)}' height='${ROW - 2}' fill='${fill(k)}'/>`;
      d = e;
    }
    // Each month's share of the project, over the item's days in that month.
    const shares = months.map(m => {
      const a0 = (m.no - 1) * 30 + 1, b0 = a0 + 29, parts = (item.bars || []).map(([a, b]) => [Math.max(a, a0), Math.min(b, b0)]).filter(([a, b]) => b >= a);
      const inMonth = parts.reduce((n, [a, b]) => n + b - a + 1, 0);
      if (!inMonth || !days || total <= 0) return '';
      return text((dayX(parts[0][0]) + dayX(parts.at(-1)[1] + 1)) / 2, y + 3.6, pct(amount / total * inMonth / days * 100), { size: 1.5 });
    }).join('');
    return text(mid(0), base, i + 1) + text(COLS[1] + 1, base, item.name, { anchor: 'start', room: COLS[2] - COLS[1] - 2 }) + text(COLS[3] - 1, base, money(amount), { anchor: 'end', room: COLS[3] - COLS[2] - 2 })
      + text(COLS[4] - 1, base, pct(total > 0 ? amount / total * 100 : 0), { anchor: 'end' }) + text(mid(4), base, days) + bars + shares;
  }).join('');
  const totals = text(COLS[1] + 1, bottom + 4, 'TOTAL COST', { anchor: 'start', bold: true }) + text(COLS[3] - 1, bottom + 4, money(total), { anchor: 'end', bold: true, room: COLS[3] - COLS[2] - 2 })
    + text(COLS[4] - 1, bottom + 4, pct(total > 0 ? 100 : 0), { anchor: 'end', bold: true }) + text(mid(4), bottom + 4, duration ?? '', { bold: true });
  const figures = [m => pct(m.monthlyPct), m => pct(m.cumulativePct), m => money(m.monthlyPct / 100 * total), m => money(m.cumulativePct / 100 * total)];
  const footer = CHART_ROWS.map((label, r) => text(left - 1.5, foot + r * FOOT + 3.4, label, { anchor: 'end', size: 1.9, bold: true })
    + months.map((m, k) => text(left + (k + 0.5) * mw, foot + r * FOOT + 3.4, figures[r](m), { size: 1.7, room: mw - 1 })).join('')).join('');
  const curve = `<polyline fill='none' stroke='#1D6FB8' stroke-width='0.5' points='${[[0, 0], ...months.map((m, k) => [k + 1, m.cumulativePct])]
    .map(([k, p]) => `${f(left + k * mw)},${f(bottom - p / 100 * (bottom - HEAD))}`).join(' ')}'/>`;
  // What the colours stand for, under the sheet: a square and the name its maker gave it.
  let lx = 0;
  const key = (color, name) => { const x = lx; lx += 5 + name.length * 1.15 + 4; return `<rect x='${f(x)}' y='${f(H + 1.5)}' width='4' height='3' fill='${color}' stroke='#000' stroke-width='0.1'/>${text(x + 5, H + 3.9, name, { anchor: 'start' })}`; };
  const legend = colors.length ? key('#92D050', 'Work') + colors.map(c => key(c.color, `${c.name}${c.work ? '' : ' (no work)'}`)).join('') : '';

  const across = [HEAD, ...items.map((_, i) => HEAD + (i + 1) * ROW), foot, ...CHART_ROWS.map((_, r) => foot + (r + 1) * FOOT)].map(y => line(0, y, W, y)).join('') + line(left, 5, W, 5);
  const down = COLS.slice(1, 5).map(x => line(x, 0, x, foot)).join('') + Array.from({ length: count }, (_, k) => line(left + k * mw, 0, left + k * mw, H, 0.3)
    + [1, 2, 3, 4, 5].map(j => line(left + k * mw + j * mw / 6, 5, left + k * mw + j * mw / 6, bottom, 0.08)).join('')).join('');
  return `<svg class='chart' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${f(W)} ${f(H + (legend ? 6 : 0))}' font-family='Arial, Helvetica, sans-serif' role='img' aria-label='Bar chart, S-curve and cash flow'>
    ${rows}${across}${down}<rect x='0' y='0' width='${W}' height='${f(H)}' fill='none' stroke='#000' stroke-width='0.3'/>${head}${totals}${footer}${curve}${legend}</svg>`;
}

/**
 * One chart (`index` 0 is the original) as one long sheet, `pages` landscape pages wide (a page for every eight
 * months without it), cut into those pages to tape side by side: each is a `wide` mm slice of the same sheet (285, a
 * landscape A4; wider for a sheet that is then shrunk onto the page, drawChartPrint). Across the
 * whole sheet: the banner, the title with the chart's name, the project and its contract, the chart (barChartSvg; the
 * total's days are the contract duration on the original, and left blank on a revised chart, whose working days the
 * orders may have changed) and five signers: the billing abstract's four (billingSignatories) and Approved by, who is
 * not kept on the project, so only the line is printed.
 */
export function barChartPages({ project, docs, version, index, signatories, banner, pages, wide = 285 }) {
  const info = (label, value, label2, value2) => `<tr><td class='pl'>${label}</td><td class='pc'>:</td><td>${esc(value)}</td><td class='pl'>${label2}</td><td>: ${esc(value2)}</td></tr>`;
  const cost = (version.items || []).reduce((t, i) => t + (Number(i.amount) || 0), 0);
  const end = index > 0 ? L.revisedCompletion(project, docs) ?? L.effectiveCompletion(project) : project.completionDateOriginal;
  const n = pages > 0 ? pages : Math.ceil(Math.max(1, Math.ceil(L.chartSpan(version) / 30)) / 8), mm = x => Number(x.toFixed(2));
  const name = `${esc(version.label || `Chart ${index + 1}`)}${index > 0 ? ` &#183; from ${esc(longDate(version.effective))}` : ''}`;
  const sheet = `${banner ? `<img class='banner' src='${esc(banner)}' alt='National Housing Authority'>` : ''}
    <h1>BAR CHART/S-CURVE WITH CASH FLOW</h1>
    <div class='period'>${name}</div>
    <table class='apa-info'>
      ${info('PROJECT TITLE', project.name, 'CONTRACT COST', peso(cost))}
      ${info('LOCATION', project.location, 'CONTRACT DURATION', `${project.contractDurationDays ?? 0} calendar days`)}
      ${info('CONTRACTOR', project.contractor, 'CONTRACT PERIOD', `${longDate(project.dateStarted)} - ${longDate(end)}`)}
    </table>
    ${barChartSvg(version, index > 0 ? null : project.contractDurationDays, n * wide)}
    <div class='keep'>${signRow({ ...signatories, approvedName: '', approvedPosition: '', approvedOffice: '' },
    [['Prepared by:', 'prepared'], ['Checked by:', 'checked'], ['Reviewed by:', 'reviewed'], ['Recommending Approval:', 'recommended'], ['Approved by:', 'approved']])}</div>`;
  // The same sheet in every page, moved left by the pages before it: the page shows its own slice of it.
  return Array.from({ length: n }, (_, p) => `<div class='bc-slice' style='width:${mm(wide)}mm'><div style='width:${mm(n * wide)}mm;margin-left:${mm(-p * wide) || 0}mm'>${sheet}</div></div>`);
}
