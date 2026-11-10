// The Blue Book forms as the phone prints them: BlueBookForms.projectProfile, the weekly and monthly Project Engineer's
// Reports (.weeklyReport, .monthlyReport) and the pieces they use (ContractLogs, weekIssues, weekDocuments, weatherClock, workableDays). Change both
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

/** BlueBookForms.weekIssues: the report's own issues, then the logged issues dated in its week that it doesn't already list. */
export function weekIssues(report, logged) {
  const own = L.issuesOf(report.issuesJson);
  const start = L.weekStart(report.reportingWeek);
  if (start == null) return own;
  const extra = logged.filter(i => inWeek(start, i.date)).sort((a, b) => String(a.date).localeCompare(String(b.date))).map(i => ({
    description: i.description || '', actionTaken: i.actionTaken || '',
    remarks: [i.remarks || null, i.targetDate ? `Target: ${longDate(i.targetDate)}` : null].filter(Boolean).join('; '),
  }));
  const seen = d => own.some(o => o.description.trim().toLowerCase() === d.description.trim().toLowerCase());
  return [...own, ...extra.filter(e => !seen(e))];
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
export const extensionsOf = docs => docs.filter(d => /^time extension/i.test(d.documentName || '')).map(d => {
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
 * The weekly form's HTML, for a `.bb` sheet. `photos` are [src, title] pairs (images only); `docs`, `issues`, `letters`
 * and `requests` are the project's records (letters already merged); `banner` is the NHA header image URL.
 */
export function weeklyReportHtml({ project, report, docs, issues, letters, requests, photos, signatories: s, banner }) {
  const manpower = L.manpowerOf(report.manpowerJson), equipment = L.equipmentOf(report.equipmentJson);
  const accomplishment = L.accomplishmentOf(report.accomplishmentItemsJson);
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

  const issueRows = weekIssues(report, issues);
  b.push(`<table><tr><th colspan='3' class='sec'>VI. ISSUES / PROBLEMS ENCOUNTERED FOR THIS WEEK</th></tr><tr><th>DESCRIPTION</th><th>ACTION TAKEN</th><th>REMARKS</th></tr>
    ${numbered(Math.max(5, issueRows.length), i => { const x = issueRows[i]; return [x?.description, x?.actionTaken, x?.remarks]; })}</table>`);

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
function signatures(s, checkedTitle) {
  const office = name => ((name || '').trim() ? `<br>${esc(name.trim())}` : '');
  const sign = (title, name, position, off) => `<td>${title}:<br><br><br><b>${esc((name || '').toUpperCase())}</b><div class='line'></div>${esc(position)}${office(off)}</td>`;
  return `<table class='sig'><tr>${sign('Prepared by', s.preparedName, s.preparedPosition, s.preparedOffice)}${sign(checkedTitle, s.checkedName, s.checkedPosition, s.checkedOffice)}${sign('Noted by', s.notedName, s.notedPosition, s.notedOffice)}</tr></table>`;
}

/* ---------- The Project Profile sheet (BlueBookForms.projectProfile) ---------- */

/** ContractLogs.resumptions: each Work Resumption Order with its effectivity, "(for approval)" until approved. */
export const resumptionsOf = docs => docs.filter(d => /work resumption/i.test(d.documentName || ''))
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
  row('Projected Beneficiaries', esc(project.projectedBeneficiaries));
  row('Contractor', esc(project.contractor));
  row('Board Resolution/Date', esc(project.boardResolution));
  row('Notice of Award', esc(longDate(project.noticeOfAward)));
  row('Notice to Proceed', esc(longDate(project.noticeToProceed)));
  row('Date Commenced', esc(longDate(project.dateStarted)));
  row('Land Area', esc(project.landArea));
  row('Number of Units', esc(project.numberOfUnits));
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

/** ContractLogs.suspensions and .variations: each order as the monthly form lists it, "(for approval)" until approved. */
export function ordersOf(docs) {
  const mark = d => (/^approved$/i.test(d.status || '') ? '' : '(for approval)');
  const suspensions = docs.filter(d => /work suspension/i.test(d.documentName || '')).map(d => {
    const days = L.remarkField(d.remarks, 'Duration').split(' ')[0];
    const from = longDate(L.remarkField(d.remarks, 'Effectivity'));
    const to = L.remarkField(L.withEnd(d.remarks || ''), 'End');
    return {
      label: L.orderLabel(d.documentName), value: (days ? `${days} calendar days – ` : '') + (to ? `${from} to ${longDate(to)}` : from),
      reason: L.isSuspension(d) ? L.remarkField(d.remarks, 'Reason') : '', status: mark(d),
    };
  });
  const variations = docs.filter(d => /^variation order/i.test(d.documentName || '')).map(d => {
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

  const acc = last ? L.accomplishmentOf(last.accomplishmentItemsJson) : [];
  const items = acc.length ? acc : [{ itemDescription: 'Land Development', weightPct: 0, actualPct: 0, targetPct: 0 }, { itemDescription: 'Housing Construction', weightPct: 0, actualPct: 0, targetPct: 0 }];
  const sum = f => items.reduce((t, a) => t + f(a), 0);
  b.push(`<table><tr><th colspan='5' class='sec'>II. PROJECT STATUS</th></tr><tr><th colspan='5' class='sub'>STATUS OF ACCOMPLISHMENT</th></tr>
    <tr><th>ITEM</th><th>% WEIGHT</th><th>TARGET ACCOMPLISHMENT</th><th>ACTUAL ACCOMPLISHMENT</th><th>TOTAL</th></tr>
    ${items.map(a => `<tr><td>${esc(a.itemDescription)}</td><td class='n'>${pct(a.weightPct)}</td><td class='n'>${pct(a.targetPct)}</td><td class='n'>${pct(a.actualPct)}</td><td class='n'>${pct(a.weightPct * a.actualPct / 100)}</td></tr>`).join('')}
    <tr class='tot'><td>TOTAL</td><td class='n'>${pct(sum(a => a.weightPct))}</td><td class='n'>${last ? pct(last.targetAccomplishmentPct) : ''}</td><td class='n'>${last ? pct(last.actualAccomplishmentPct) : ''}</td><td class='n'>${pct(sum(a => a.weightPct * a.actualPct / 100))}</td></tr></table>`);

  // Billings up to month end; one with an unreadable date is kept, as on the phone.
  const paid = payments.filter(p => { const m = monthOf(p.date); return m == null || m <= month; });
  b.push(`<table><tr><th colspan='6' class='sec'>III. STATUS OF PAYMENT</th></tr><tr><th rowspan='2'></th><th rowspan='2'>DATE</th><th rowspan='2'>PERIOD COVERED</th><th colspan='2'>PAYMENTS MADE</th><th rowspan='2'>BALANCE</th></tr><tr><th>NET AMOUNT</th><th>%</th></tr>
    ${Array.from({ length: Math.max(3, paid.length) }, (_, i) => {
      const p = paid[i];
      return `<tr><td>${i + 1}. ${esc(p?.name ?? '')}</td><td>${esc(p ? longDate(p.date) : '')}</td><td>${esc(p?.periodCovered ?? '')}</td><td class='n'>${p ? esc(peso(L.netAmount(p))) : ''}</td><td class='n'>${p ? pct(p.percentage) : ''}</td><td class='n'>${p ? esc(`${peso(p.balanceAmount)} (${pct(p.balancePercentage)})`) : ''}</td></tr>`;
    }).join('')}</table>`);

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
