// Pure rules copied from the Android app, so the web shows the same numbers as the phones.
// Each names the Kotlin it mirrors; change both together.

/** Roles that read every project (AssignmentPolicy.seesAll / firestore.rules seesAll()). */
export function seesAll(claims) {
  return claims.role === 'SUPER_ADMIN' || claims.role === 'SUPER_VIEWER' || claims.allProjects === true;
}

/** A cloud record: `data` is the Room row as a JSON string (RecordSync.buildDocument). */
export function parseRecord(doc) {
  let data = {};
  try { data = JSON.parse(doc.data || '{}'); } catch { /* unreadable row: show it empty */ }
  return { ...data, uuid: doc.uuid, type: doc.type, revision: doc.revision, updatedAt: doc.updatedAt, isDeleted: doc.isDeleted === true };
}

/** Scope of works JSON as {name, amount, weightPct}; plain text is one item at 100% (SummarySection.scopeItems). */
export function scopeItems(json) {
  let items;
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) throw new Error('not a list');
    items = parsed.map(o => ({ name: String(o.name ?? ''), amount: Number(o.amount) || 0, weightPct: Number(o.weightPct) || 0 }));
  } catch {
    items = [{ name: String(json ?? ''), amount: 0, weightPct: 100 }];
  }
  return items.filter(i => i.name.trim() !== '');
}

/** ContractCost.original: sum of scope amounts, else the stored cost. */
export function originalCost(project) {
  let total = 0;
  try {
    const items = JSON.parse(project.scopeOfWork);
    if (Array.isArray(items)) for (const o of items) total += Number(o.amount) || 0;
  } catch { /* fall back below */ }
  return total > 0 ? total : (Number(project.contractCostOriginal) || 0);
}

const PHP_AMOUNT = /PHP\s*([-−(])?\s*([0-9,.]+)/i;

/** ContractCost.current: original plus the PHP amount of each approved Variation Order. */
export function currentCost(project, documents) {
  const vos = documents.filter(d => /variation order/i.test(d.documentName || '') && /^approved$/i.test(d.status || ''));
  const original = originalCost(project);
  if (vos.length === 0) return original;
  return original + vos.reduce((sum, vo) => {
    const m = PHP_AMOUNT.exec(vo.documentName);
    if (!m) return sum;
    const amount = parseFloat(m[2].replace(/,/g, '')) || 0;
    const deductive = /deductive/i.test(vo.documentName) || !!m[1];
    return sum + (deductive ? -amount : amount);
  }, 0);
}

/** ProjectPayment.netAmount: what is paid after retention. */
export const netAmount = p => (Number(p.grossAmount) || 0) * (1 - (Number(p.retentionPct) || 0) / 100);

/** Same rule as the Issues tab's "Resolved" filter. */
export const isOpenIssue = i => !/resolved|closed/i.test(i.status || '');

/** ProjectSchedule.effectiveCompletion: the revised date when set, else the original. */
export function effectiveCompletion(project) {
  const revised = (project.completionDateRevised || '').trim();
  return revised && revised !== '-' ? revised : (project.completionDateOriginal || '');
}

/** Days left as the Summary's Key dates card words it. `today` is yyyy-MM-dd. */
export function daysLeftText(project, today) {
  if ((Number(project.actualAccomplishment) || 0) >= 100) return 'Completed';
  const end = isoDay(effectiveCompletion(project));
  const now = isoDay(today);
  if (end == null || now == null) return '-';
  const days = Math.round((end - now) / 86400000);
  return days < 0 ? `Overdue by ${-days} days` : `${days} days`;
}

/**
 * Days past the completion date (the revised one, if any) of an unfinished project, else 0. `today` is yyyy-MM-dd.
 * Web only: the phone's status chip and schedule words don't look at the date yet, so an overdue project with
 * no slippage read "on schedule" in green.
 */
export function daysOverdue(project, today) {
  if (isCompleted(project)) return 0;
  const end = isoDay(effectiveCompletion(project)), now = isoDay(today);
  return end == null || now == null || end >= now ? 0 : Math.round((now - end) / 86400000);
}

function isoDay(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((s || '').trim());
  return m ? Date.UTC(+m[1], +m[2] - 1, +m[3]) : null;
}

const DAY = 86400000;

/**
 * BarChart.targetOn: the approved bar chart's planned accomplishment (%) on `today` (yyyy-MM-dd), or null without
 * a chart. Each item's amount is spread evenly over its bars' contract days (the start date is day 1); a revised
 * chart applies from its effective date; approved suspensions taking effect on or after it stop the clock, a Partial
 * one only for the items it names.
 */
export function plannedTarget(project, docs, today) {
  let versions;
  try { versions = JSON.parse(project.barChartJson || '[]'); } catch { return null; }
  if (!Array.isArray(versions) || !versions.length) return null;
  const start = isoDay(project.dateStarted), now = isoDay(today);
  if (start == null || now == null) return null;
  versions = [...versions].sort((a, b) => String(a.effective).localeCompare(String(b.effective)));
  const v = [...versions].reverse().find(x => { const e = isoDay(x.effective); return e == null || e <= now; }) || versions[0];
  const from = Math.max(isoDay(v.effective) ?? start, start);
  const dayOf = item => (now - start) / DAY + 1 - suspendedDays(docs, from, now, item);
  const items = v.items || [];
  const total = items.reduce((t, i) => t + (Number(i.amount) || 0), 0);
  if (total <= 0) return 0;
  const len = ([a, b]) => Math.max(0, b - a + 1);
  const planned = items.reduce((t, i) => {
    const bars = i.bars || [], day = dayOf(i.name);
    const days = bars.reduce((n, bar) => n + len(bar), 0);
    const done = bars.reduce((n, [a, b]) => n + Math.min(Math.max(Math.min(day, b) - a + 1, 0), len([a, b])), 0);
    return t + (days ? (Number(i.amount) || 0) * done / days : 0);
  }, 0);
  return planned / total * 100;
}

/** BarChart.withPlannedTarget: the project with today's target from its chart; unchanged without one, or once completed. */
export function withPlannedTarget(project, docs, today) {
  if (isCompleted(project)) return project;
  const t = plannedTarget(project, docs, today);
  return t == null ? project : { ...project, targetAccomplishment: Math.round(t * 100) / 100 };
}

// "2nd Partial Continuance of …", "Partial Work Suspension Order No. 1"; an old "(Full) of …" continuance says so itself.
const isPartialOrder = name => { const m = /\((full|partial)\)\s+of/i.exec(name); return m ? /partial/i.test(m[1]) : /partial/i.test(name); };

const orderNo = name => (/No\. ?(\S+?)(?:\s|$|\))/.exec(name || '') || [])[1];
const itemsOf = remarks => { const m = /Items: ([^|]*)/.exec(remarks || ''); return m ? m[1].split(';').map(x => x.trim()).filter(Boolean) : null; };

/** BarChart.pausedItems: the items a Partial suspension names (a continuance without a list takes its suspension's), or null for a Full one. */
function pausedItems(d, docs) {
  const name = d.documentName || '';
  if (!isPartialOrder(name)) return null;
  const own = itemsOf(d.remarks);
  if (own || !/continuance/i.test(name)) return own || [];
  const s = (docs || []).find(x => !/continuance/i.test(x.documentName || '') && /work suspension/i.test(x.documentName || '') && orderNo(x.documentName) === orderNo(name));
  return (s && itemsOf(s.remarks)) || [];
}

/**
 * ProjectSchedule.withEnd: remarks with the End worked out again from Duration and Effectivity, the effectivity
 * date being day 1 (May 26 + 90 days ends Aug 23), so orders saved a day late read right. Unchanged without both.
 */
export function withEnd(remarks) {
  const r = remarks || '';
  const days = Number((/Duration: (\d+)/.exec(r) || [])[1]);
  const eff = /Effectivity: (\d{4}-\d{2}-\d{2})/.exec(r);
  const begin = eff && isoDay(eff[1]);
  if (!(days > 0) || begin == null) return r;
  const end = `End: ${new Date(begin + (days - 1) * DAY).toISOString().slice(0, 10)}`;
  return /End: \d{4}-\d{2}-\d{2}/.test(r) ? r.replace(/End: \d{4}-\d{2}-\d{2}/g, end) : r.replace(eff[0], `${eff[0]} | ${end}`);
}

/**
 * BarChart.periods: each approved suspension (and continuance) with its Effectivity and when work resumes, the earlier
 * of its resumption order's Effectivity and the day after its End, its last suspended day (UTC ms); a null end means
 * it is still suspended.
 */
function periods(docs) {
  const approved = (docs || []).filter(d => /^approved$/i.test(d.status || ''));
  const resumptions = approved.filter(d => /work resumption/i.test(d.documentName || ''));
  const field = (remarks, key) => isoDay((new RegExp(`${key}: (\\d{4}-\\d{2}-\\d{2})`).exec(remarks || '') || [])[1]);
  return approved.filter(d => /work suspension/i.test(d.documentName || '')).flatMap(d => {
    const begin = field(d.remarks, 'Effectivity');
    if (begin == null) return [];
    const r = resumptions.find(x => orderNo(x.documentName) === orderNo(d.documentName));
    const last = field(withEnd(d.remarks), 'End');
    const ends = [last == null ? null : last + DAY, r ? field(r.remarks, 'Effectivity') : null].filter(x => x != null);
    return [{ d, begin, end: ends.length ? Math.min(...ends) : null }];
  });
}

/** BarChart.suspendedDays: days from `from` through `until` (UTC ms) under approved suspensions that pause `item`. */
function suspendedDays(docs, from, until, item) {
  return periods(docs)
    .filter(({ d }) => { const p = pausedItems(d, docs); return p == null || p.includes(item); })
    .reduce((t, { begin, end }) => begin < from ? t : t + Math.max(0, (Math.min(end ?? until + DAY, until + DAY) - begin) / DAY), 0);
}

/** BarChart.isSuspendedOn: an approved Full suspension is in effect on `today` (yyyy-MM-dd); Partial ones leave it going. */
export function isSuspendedOn(docs, today) {
  const now = isoDay(today);
  return now != null && periods(docs).some(({ d, begin, end }) => pausedItems(d, docs) == null && begin <= now && (end == null || now < end));
}

/** BarChart.withSchedule: today's target from the chart, then Suspended during a Full suspension, else Behind Schedule at any negative slippage, else On-going. */
export function withSchedule(project, docs, today) {
  const p = withPlannedTarget(project, docs, today);
  if (isCompleted(p)) return p;
  return { ...p, status: isSuspendedOn(docs, today) ? 'Suspended' : slippage(p) < 0 ? 'Behind Schedule' : 'On-going' };
}

/** ProjectViewModel.isMine (AssignmentPolicy.isVisible without seesAll): assigned, or the creator of a list-less project. */
export function isMine(project, uid, claims) {
  const list = project.assignedUids || [];
  return claims.role === 'SUPER_ADMIN' || list.includes(uid) || (list.length === 0 && project.createdByUid === uid);
}

/** PortfolioItem.viewOnly: a Super Viewer reads everything, so the tag would only mark assignments. */
export const isViewOnly = (project, uid, claims) => !isMine(project, uid, claims) && claims.role !== 'SUPER_VIEWER';

/** ProjectAttention.slippage: variance rounded to 2 decimals so the -10 / -15 boundaries hold. */
export const slippage = p => Math.round(((Number(p.actualAccomplishment) || 0) - (Number(p.targetAccomplishment) || 0)) * 100) / 100;
export const isCompleted = p => (Number(p.actualAccomplishment) || 0) >= 100;
export const displayStatus = p => (isCompleted(p) ? 'Completed' : p.status || '-');

/** PortfolioRow.varianceColor as a level: ok (on or ahead), watch (10-15% behind), behind (15%+), neutral (small slips). */
export function varianceLevel(slip) {
  if (slip >= 0) return 'ok';
  if (slip <= -15) return 'behind';
  if (slip <= -10) return 'watch';
  return 'neutral';
}

/** PortfolioRow.statusColor: the status chip never reads green on a project that is behind, or (web) overdue. */
export function statusLevel(p, today) {
  if (isCompleted(p)) return 'ok';
  if (p.status === 'Suspended') return 'neutral';
  if (today && daysOverdue(p, today) > 0) return 'behind';
  const s = varianceLevel(slippage(p));
  return p.status === 'Behind Schedule' && (s === 'ok' || s === 'neutral') ? 'watch' : s;
}

const ORDER_TYPES = ['variation order', 'time extension', 'work suspension', 'work resumption'];
/** ProjectAttention.waitingApproval: orders not yet Approved. */
export const waitingApproval = docs => docs.filter(d =>
  !/^approved$/i.test((d.status || '').trim()) && ORDER_TYPES.some(t => (d.documentName || '').toLowerCase().includes(t))).length;

export const billedTotal = payments => payments.reduce((t, p) => t + (Number(p.grossAmount) || 0), 0);

/** BillingProgress.label. */
export function billedLabel(billed, cost) {
  if (billed <= 0) return 'No billings yet';
  if (cost <= 0) return 'No contract cost';
  return `${(billed / cost * 100).toFixed(2)}%`;
}

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/**
 * ProjectAttention.flags, critical first. `today` is yyyy-MM-dd.
 * shortcut: leaves out "developer requests not packaged" and "no field update in N days", which need
 * every project's requests and weekly reports; add them when the dashboard reads those records.
 */
export function attentionFlags(project, docs, billed, today) {
  const flags = [];
  const finished = isCompleted(project);
  const slip = slippage(project);
  // Any negative slippage reads "Behind Schedule" (withSchedule); only 10% or more is flagged.
  if (!finished && slip <= -10) {
    flags.push({ kind: 'behind', level: slip <= -15 ? 'critical' : 'warning', message: `${(-slip).toFixed(2)}% behind schedule` });
  }
  const cost = currentCost(project, docs);
  if (cost > 0) {
    const billedPct = billed / cost * 100;
    if (billedPct - (Number(project.actualAccomplishment) || 0) > 10) {
      flags.push({ kind: 'billingAhead', level: 'warning',
        message: `Billed ${billedPct.toFixed(2)}%, work done ${(Number(project.actualAccomplishment) || 0).toFixed(2)}%` });
    }
  }
  const waiting = waitingApproval(docs);
  if (waiting > 0) flags.push({ kind: 'waiting', level: 'warning', message: `${plural(waiting, 'order', 'orders')} waiting approval` });
  const due = isoDay(effectiveCompletion(project));
  const now = isoDay(today);
  if (!finished && due != null && now != null) {
    const days = Math.round((due - now) / 86400000);
    if (days < 0) flags.push({ kind: 'overdue', level: 'critical', message: `Overdue by ${plural(-days, 'day', 'days')}` });
    else if (days <= 30) flags.push({ kind: 'dueSoon', level: 'warning', message: `Due in ${plural(days, 'day', 'days')}` });
  }
  return flags.sort((a, b) => (a.level === b.level ? 0 : a.level === 'critical' ? -1 : 1));
}

/**
 * ProjectAttention.slippage in words (PortfolioRow.scheduleText). Web wording: "behind" only from the
 * Behind schedule threshold (10%), "slipping" below it, and "past completion date" once overdue.
 */
export function scheduleText(project, today) {
  if (today && daysOverdue(project, today) > 0) return 'past completion date';
  const s = slippage(project);
  if (s > 0) return `${s.toFixed(2)}% ahead`;
  if (s <= -10) return `${(-s).toFixed(2)}% behind`;
  if (s < 0) return `${(-s).toFixed(2)}% slipping`;
  return 'on schedule';
}

/** "2026-10-20" as "Oct 20, 2026"; anything else as stored (ProjectSchedule.displayDate). */
export function displayDate(s) {
  const t = isoDay(s);
  if (t == null) return s || '-';
  return new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
}

const pesoFmt = new Intl.NumberFormat('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** CurrencyFormatter.formatPhp. */
export const formatPhp = n => `${n < 0 ? '-' : ''}₱${pesoFmt.format(Math.abs(n))}`;
export const formatDecimal = n => pesoFmt.format(n);

/** Storage path of an `nhafile:` reference (Attachments.storagePathOf), or null when it isn't one. */
export function storagePathOf(ref) {
  const m = /^nhafile:(?:([0-9a-f-]{36})\/)?([0-9a-f-]{36}\.(?:jpg|pdf))$/.exec(ref || '');
  if (!m) return null;
  return m[1] ? `attachments/${m[1]}/${m[2]}` : `attachments/${m[2]}`;
}

/* ---------- Progress: weekly and monthly reports (WeeklyReportsTab, MonthlyReportsTab, PhotoGalleryTab) ---------- */

const jsonArray = text => { try { const a = JSON.parse(text || '[]'); return Array.isArray(a) ? a : []; } catch { return []; } };
// org.json optString / optInt: the fallback when the key is missing or the value can't be read.
const str = (v, fallback = '') => (v == null ? fallback : String(v));
const int = (v, fallback) => (v != null && v !== '' && Number.isFinite(Number(v)) ? Math.trunc(Number(v)) : fallback);

/** parseManpowerList. */
export const manpowerOf = json => jsonArray(json).map(o => ({ designation: str(o?.designation, 'Personnel'), count: int(o?.count, 1), remarks: str(o?.remarks) }));
/** parseEquipmentList: an older row kept its status in remarks. */
export const equipmentOf = json => jsonArray(json).map(o => ({
  description: str(o?.description, 'Equipment'), count: int(o?.count, 1), status: str(o?.status, str(o?.remarks, 'Operational')), remarks: str(o?.remarks),
}));
/** parseIssuesList. */
export const issuesOf = json => jsonArray(json).map(o => ({ description: str(o?.description), actionTaken: str(o?.actionTaken), remarks: str(o?.remarks) }));
/** parseActivitiesList: older rows say dayOfWeek and activity. */
export const activitiesOf = json => jsonArray(json).map(o => ({
  day: str(o?.day, str(o?.dayOfWeek)), description: str(o?.description, str(o?.activity)), remarks: str(o?.remarks),
}));
/** parseAccomplishmentItemsList: stops at the first row without a description, target or actual, keeping the rows before it. */
export function accomplishmentOf(json) {
  const out = [];
  for (const o of jsonArray(json)) {
    const t = Number(o?.targetPct), a = Number(o?.actualPct);
    if (typeof o?.itemDescription !== 'string' || o.targetPct == null || o.actualPct == null || !Number.isFinite(t) || !Number.isFinite(a)) break;
    out.push({ itemDescription: o.itemDescription, weightPct: Number(o.weightPct) || 0, targetPct: t, actualPct: a, variancePct: a - t });
  }
  return out;
}

/** Attachments.parseList: a weekly report's photo list, which older versions saved as an array, an array inside a string, or one uri. */
export function attachmentList(json) {
  const out = [];
  const walk = v => {
    const t = String(v ?? '').trim();
    if (!t) return;
    if (!t.startsWith('[')) { out.push(t); return; }
    let a;
    try { a = JSON.parse(t); } catch { return; }
    if (Array.isArray(a)) a.forEach(x => walk(typeof x === 'string' ? x : JSON.stringify(x)));
  };
  walk(json);
  return out;
}

/** PhotoDetails.parse: photo ref -> {title, takenAt}. */
export function photoDetails(json) {
  let o;
  try { o = JSON.parse(json || '{}'); } catch { return {}; }
  if (!o || typeof o !== 'object' || Array.isArray(o)) return {};
  return Object.fromEntries(Object.entries(o).map(([ref, d]) => [ref, { title: str(d?.title), takenAt: str(d?.takenAt) }]));
}

export const isPdf = ref => /\.pdf$/i.test(ref || '');

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
/** "Oct 24, 2026" or "October 24, 2026" as UTC ms; null otherwise. */
function longDay(text) {
  const m = /^([A-Za-z]+)\.? (\d{1,2}), (\d{4})$/.exec((text || '').trim());
  if (!m) return null;
  const word = m[1].toLowerCase();
  const month = MONTHS.findIndex(x => x.toLowerCase() === word || x.slice(0, 3).toLowerCase() === word);
  return month < 0 ? null : Date.UTC(+m[3], month, +m[2]);
}

/** BlueBookForms.weekStart: first day (UTC ms) of "Oct 24, 2026 - Oct 30, 2026" or the older "Week 30: July 20 - July 26, 2026". */
export function weekStart(label) {
  const parts = (label || '').replace(/^Week \d+: /, '').split(' - ').map(x => x.trim());
  if (parts.length !== 2) return null;
  const end = longDay(parts[1]);
  if (end == null) return null;
  return longDay(parts[0]) ?? longDay(`${parts[0]}, ${new Date(end).getUTCFullYear()}`);
}

/** WeekRange.byWeek: by first day (labels that don't read first), then as entered. */
export const byWeek = (a, b) => (weekStart(a.reportingWeek) ?? -Infinity) - (weekStart(b.reportingWeek) ?? -Infinity)
  || (a.createdAtTimestamp || 0) - (b.createdAtTimestamp || 0);

/** WeeklyReportsTab.previousReport: the report with the most days elapsed before this one's. */
export const previousReport = (reports, report) => reports
  .filter(r => (r.daysElapsed ?? 0) < (report.daysElapsed ?? 0))
  .reduce((best, r) => (!best || r.daysElapsed > best.daysElapsed ? r : best), null);

/**
 * MonthlyReportsTab.availableMonths: saved months, then the month each weekly report's week names (the first of
 * January..December it mentions, in the first year it names), else this month. `today` is yyyy-MM-dd.
 */
export function reportMonths(weekly, monthly, today) {
  const [y, m] = today.split('-');
  const fromWeekly = weekly.map(r => {
    const clean = (r.reportingWeek || '').replace(/Week \d+: /, '').toLowerCase();
    const year = (/\b(20\d{2})\b/.exec(clean) || [])[1] || y;
    const i = MONTHS.findIndex(x => clean.includes(x.toLowerCase()) || clean.includes(x.slice(0, 3).toLowerCase()));
    return i < 0 ? null : `${MONTHS[i]} ${year}`;
  });
  const all = [...new Set([...monthly.map(r => r.reportingMonth), ...fromWeekly].filter(x => x && x.trim()))];
  return all.length ? all : [`${MONTHS[+m - 1]} ${y}`];
}

/** MonthlyReportsTab.monthWeeklyReports: weeks naming the month (or its first 3 letters) and its year, in week order. */
export function monthReports(weekly, month) {
  const parts = month.split(' ');
  const name = (parts[0] || '').toLowerCase(), year = (parts[parts.length - 1] || '').toLowerCase();
  return weekly.filter(r => {
    const w = (r.reportingWeek || '').toLowerCase();
    return (w.includes(name) || w.includes(name.slice(0, 3))) && w.includes(year);
  }).sort(byWeek);
}

/* ---------- Key dates: the revised completion from approved orders (ProjectSchedule) ---------- */

/** ProjectSchedule.lastDay: day `days` counting `start` (yyyy-MM-dd) as day 1. */
export function lastDay(start, days) {
  const t = isoDay(start);
  return t == null ? null : new Date(t + (days - 1) * DAY).toISOString().slice(0, 10);
}

/** ProjectSchedule.addedDays: days added by approved Time Extensions (" - N Days" in the name). */
export const addedDays = docs => docs
  .filter(d => /^approved$/i.test(d.status || '') && /time extension/i.test(d.documentName || ''))
  .reduce((t, d) => t + (Number((/.* - (\d+) Days/i.exec(d.documentName) || [])[1]) || 0), 0);

/** ProjectSchedule.revisedCompletion: the last day of the contract duration plus approved extension days; null when none adds days. */
export function revisedCompletion(project, docs) {
  const added = addedDays(docs);
  return added > 0 ? lastDay(project.dateStarted, (Number(project.contractDurationDays) || 0) + added) : null;
}

/**
 * ProjectSchedule.revisedDurationDays: contract days plus approved order days, else the days from the start through the stored
 * revised date (both counted); null when neither applies or nothing was revised.
 */
export function revisedDurationDays(project, added) {
  const duration = Number(project.contractDurationDays) || 0;
  if (added > 0) return duration + added;
  if ((project.completionDateRevised || '').trim() === (project.completionDateOriginal || '').trim()) return null;
  const start = isoDay(project.dateStarted), end = isoDay(project.completionDateRevised);
  if (start == null || end == null) return null;
  const days = Math.round((end - start) / DAY) + 1;
  return days > 1 && days !== duration ? days : null;
}

/* ---------- Orders and letters (ProjectSchedule, ContractorLetters, DocumentsSection) ---------- */

const ORDER = /^(Partial )?Work (Suspension|Resumption) (?:Order )?No\. ?(\S+?)(?: \((Full|Partial)\))?$/i;
const CONTINUANCE = /^(?:Continuance No\. ?(\d+)(?: \((Full|Partial)\))?|\d+\w\w( Partial)? Continuance) of (.+)$/i;
const partialOrder = m => !!m[1] || /^partial$/i.test(m[4] || '');
const baseOf = name => { const m = ORDER.exec(name.trim()); return m ? `Work Suspension Order No. ${m[3]}` : name.trim(); };
const continuanceOf = name => { const m = CONTINUANCE.exec((name || '').trim()); return m ? baseOf(m[4]) : null; };

/** ProjectSchedule.ordinal: 1st, 2nd, 3rd, 11th, 21st. */
export const ordinal = n => `${n}${n % 100 >= 11 && n % 100 <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' }[n % 10] || 'th')}`;

/** ProjectSchedule.orderLabel: older names read like new ones ("Work Suspension No. 1 (Partial)" -> "Partial Work Suspension Order No. 1"). */
export function orderLabel(name) {
  const n = (name || '').trim();
  const c = CONTINUANCE.exec(n);
  if (c) {
    const [, no, type, , base] = c;
    if (!no) return n;
    const b = ORDER.exec(base);
    const partial = type ? /^partial$/i.test(type) : !!(b && partialOrder(b));
    return `${ordinal(+no)}${partial ? ' Partial' : ''} Continuance of ${baseOf(base)}`;
  }
  const m = ORDER.exec(n);
  return m ? `${partialOrder(m) ? 'Partial ' : ''}Work ${m[2].charAt(0).toUpperCase()}${m[2].slice(1)} Order No. ${m[3].trim()}` : n;
}

/** ProjectSchedule.isSuspension. */
export const isSuspension = doc => /^suspension$/i.test((ORDER.exec((doc.documentName || '').trim()) || [])[2] || '');
/** ProjectSchedule.continuances: a suspension's continuances, in the order given. */
export const continuances = (suspension, docs) => docs.filter(d => continuanceOf(d.documentName) === baseOf(suspension.documentName || ''));

/** A "Key: value" field of an order's or letter's remarks. */
export const remarkField = (remarks, key) => ((new RegExp(`${key}: ([^|]*)`).exec(remarks || '') || [])[1] || '').trim();

/** ContractorLetters.merged: letter records plus the older "Letter to Contractor No. N" documents, newest first. */
export function mergedLetters(letters, docs) {
  const old = docs.filter(d => /^letter to contractor/i.test(d.documentName || '')).map(d => {
    const reason = remarkField(d.remarks, 'Reason');
    return {
      dateSent: '', subject: d.documentName + (reason ? `: ${reason}` : ''), actionTaken: '', dateReplied: '', replyFileUrl: '',
      dateReceived: remarkField(d.remarks, 'Date Received'), receivedFileUrl: d.fileUrl || '', uuid: d.uuid,
    };
  });
  const key = l => l.dateSent || l.dateReceived || '';
  return [...letters, ...old].sort((a, b) => key(b).localeCompare(key(a)));
}

/** RelatedFilesList: documents that are not orders or letters. */
export const relatedFiles = docs => docs.filter(d =>
  !/time extension|variation order|work suspension|work resumption/i.test(d.documentName || '') && !/^letter to contractor/i.test(d.documentName || ''));

/** VariationOrdersLog: a variation order's type, status, approval date and reason. */
export function variationLines(vo) {
  const approved = ((/\|\s*Date Approved:\s*([^|]+)/.exec(vo.remarks || '') || [])[1] || '').trim();
  const clean = (vo.remarks || '').replace(/^Remarks:\s*/i, '').replace(/\|\s*Date Approved:[^|]*/g, '').trim();
  const type = /additive/i.test(vo.documentName || '') ? 'Additive' : /deductive/i.test(vo.documentName || '') ? 'Deductive' : '';
  return [type ? `Type: ${type}` : '', `Status: ${vo.status || ''}`, `Date Approved: ${approved || '-'}`,
    /^reason:/i.test(clean) ? clean : clean ? `Reason: ${clean}` : ''].filter(Boolean);
}

/* ---------- Lots: Status of construction (SdpSummary, SdpSites, SummarySection.ConstructionCard) ---------- */

/** SdpSummary.BANDS. */
export const LOT_BANDS = ['Not yet started', '1–25%', '26–50%', '51–75%', '76–99%', '100% / RFO'];
/** SdpSummary.band. */
export const lotBand = p => (p >= 100 ? 5 : p >= 76 ? 4 : p >= 51 ? 3 : p >= 26 ? 2 : p > 0 ? 1 : 0);

// Room booleans travel as 1/0; a row without the column keeps its default (true).
const on = v => v !== false && v !== 0 && v !== '0';

/** SdpLot.use: blank on lots saved before uses were kept; block "OS" was the open spaces then. */
export const lotUse = l => l.lotUse || (l.blockNumber === 'OS' ? 'Open Space' : 'Saleable');
/** SdpLot.label: "Block 3 Lot 12", "Road Lot 4" or "Easement 2", or "Unassigned lot" until it is named. */
export function lotLabel(l) {
  const b = (l.blockNumber || '').trim(), n = (l.lotNumber || '').trim();
  if (!b || !n) return 'Unassigned lot';
  return NUMBERED_USES[lotUse(l)] === l.blockNumber ?`${lotUse(l)} ${n}` : `Block ${b} Lot ${n}`;
}
/** A lot's progress, 0 without a progress row. */
export const lotProgress = l => Number(l.progress?.physicalProgress) || 0;
const billed = (p, key) => p?.[key] === 'BILLED';
/** SdpLotProgress.billingLabel. */
export function lotBillingLabel(p) {
  const m1 = billed(p, 'billingStatus'), m2 = billed(p, 'm2BillingStatus');
  return m1 && m2 ? 'M1 + M2 BILLED (100%)' : m1 ? 'M1 BILLED (80%)' : m2 ? 'M2 BILLED (20%)' : 'NOT BILLED';
}

/**
 * SdpSites.current with each plan's active lots: per site (in the order its first plan was uploaded), its active plan
 * with the highest version, else its highest version. Each lot carries its latest progress row as `progress` (or null).
 */
export function sdpSites(records) {
  const sites = new Map();
  for (const p of records.filter(r => r.type === 'sdp_plans').sort((a, b) => String(a.uploadedDate || '').localeCompare(String(b.uploadedDate || '')))) {
    const k = p.siteName || '';
    if (!sites.has(k)) sites.set(k, []);
    sites.get(k).push(p);
  }
  const top = list => list.reduce((best, p) => (!best || (Number(p.version) || 0) > (Number(best.version) || 0) ? p : best), null);
  const progress = new Map(records.filter(r => r.type === 'sdp_lot_progress')
    .sort((a, b) => (a.updatedAt || 0) - (b.updatedAt || 0)).map(p => [p._ref_sdpLotId, p]));
  const lots = records.filter(r => r.type === 'sdp_lots' && on(r.isActive));
  return [...sites].map(([site, list]) => {
    const plan = top(list.filter(p => on(p.isActive))) || top(list);
    return { site, plan, lots: lots.filter(l => l._ref_sdpPlanId === plan.uuid).map(l => ({ ...l, progress: progress.get(l.uuid) || null })) };
  });
}

/** Saleable lots per band over every site's plan in use, from a project's records. A lot without progress is at 0%. */
export function lotSummary(records) {
  const saleable = sdpSites(records).flatMap(s => s.lots).filter(l => lotUse(l) === 'Saleable');
  const bands = LOT_BANDS.map(() => 0);
  for (const l of saleable) bands[lotBand(lotProgress(l))]++;
  return { bands, saleable: saleable.length };
}

/** LotStats: the Lots KPIs, over saleable lots only (open spaces, road lots and the like are not housing units). */
export function lotStats(lots) {
  const s = lots.filter(l => lotUse(l) === 'Saleable');
  return {
    total: s.length,
    m1: s.filter(l => billed(l.progress, 'billingStatus')).length,
    m2: s.filter(l => billed(l.progress, 'm2BillingStatus')).length,
    notBilled: s.filter(l => !billed(l.progress, 'billingStatus') && !billed(l.progress, 'm2BillingStatus')).length,
    completed: s.filter(l => lotProgress(l) >= 100).length,
    avg: s.length ? s.reduce((t, l) => t + lotProgress(l), 0) / s.length : 0,
  };
}

/** The Lots filter's choices (LOT_PROGRESS_FILTERS and the billing and status menus). */
export const LOT_FILTERS = { band: ['ALL', ...LOT_BANDS], billing: ['ALL', 'M1 BILLED', 'M2 BILLED', 'NOT BILLED'], completion: ['ALL', 'COMPLETED', 'INCOMPLETE'] };

/** isLotMatchingFilter: `f` is {query, band, billing, completion}; the query matches "b3-l12", "b3l12", "block 3", "lot 12" or the unit. */
export function lotMatches(l, f) {
  const q = (f.query || '').trim().toLowerCase();
  if (q) {
    const b = l.blockNumber || '', n = l.lotNumber || '', unit = (l.housingUnitNumber || '').toLowerCase();
    const unassigned = !b.trim() || !n.trim();
    const keys = unassigned ? [lotLabel(l).toLowerCase()] : [`b${b}-l${n}`, `b${b}l${n}`, `block ${b}`, `lot ${n}`].map(k => k.toLowerCase());
    if (![...keys, unit].some(k => k.includes(q))) return false;
  }
  const m1 = billed(l.progress, 'billingStatus'), m2 = billed(l.progress, 'm2BillingStatus');
  if ((f.billing === 'M1 BILLED' && !m1) || (f.billing === 'M2 BILLED' && !m2) || (f.billing === 'NOT BILLED' && (m1 || m2))) return false;
  const done = lotProgress(l) >= 100;
  if ((f.completion === 'COMPLETED' && !done) || (f.completion === 'INCOMPLETE' && done)) return false;
  if (f.band && f.band !== 'ALL' && LOT_BANDS.indexOf(f.band) !== lotBand(lotProgress(l))) return false;
  return true;
}

/**
 * lotFill as a colour class: saleable lots by band (lot-0..lot-5); road lots red, yellow while concreting, green when
 * done (SdpSummary.roadState); any other use neutral.
 */
export function lotColour(l) {
  const p = lotProgress(l), use = lotUse(l);
  if (use === 'Road Lot') return `lot-${p >= 100 ? 5 : p > 0 ? 3 : 0}`;
  return use === 'Saleable' ? `lot-${lotBand(p)}` : 'lot-n';
}

/** The lots grouped by block for the grid, blocks and lots in natural order ("2" before "10"); unassigned lots last. */
export function lotBlocks(lots) {
  const cmp = (a, b) => String(a).localeCompare(String(b), 'en', { numeric: true });
  const blocks = new Map();
  for (const l of [...lots].sort((a, b) => cmp(a.lotNumber || '', b.lotNumber || ''))) {
    const k = lotLabel(l) === 'Unassigned lot' ? '' : NUMBERED_USES[lotUse(l)] === l.blockNumber ? lotUse(l) : `Block ${l.blockNumber}`;
    if (!blocks.has(k)) blocks.set(k, []);
    blocks.get(k).push(l);
  }
  return [...blocks].sort(([a], [b]) => (!a) - (!b) || cmp(a, b)).map(([name, list]) => ({ name: name || 'Unassigned lots', lots: list }));
}
const NUMBERED_USES = { 'Road Lot': 'RL', Easement: 'EA' };

/** HOUSING_BILLING_TYPES. */
export const HOUSING_BILLING_TYPES = ['Land acquisition', 'Land development', 'M1 - Housing unit', 'M2 - Housing unit'];

/** SummarySection.BillingStatus: lots or units billed per housing billing type, and their share of `total` saleable units. */
export const billingStatus = (payments, total) => HOUSING_BILLING_TYPES.map(type => {
  const units = payments.filter(p => p.billingType === type).reduce((t, p) => t + (Number(p.units) || 0), 0);
  const label = type + (type.startsWith('M1') ? ' (80%)' : type.startsWith('M2') ? ' (20%)' : '');
  return { label, units, share: total > 0 ? `${(units * 100 / total).toFixed(2)}%` : '-' };
});

/** billingUnitName. */
export const billingUnitName = type => (/^land/i.test(type || '') ? 'lot' : 'housing unit');

/** ProjectIssue.isOverdue: still Pending and the target date (yyyy-MM-dd) is before `today`. */
export function isOverdueIssue(i, today) {
  const t = (i.targetDate || '').trim();
  return /pending/i.test(i.status || '') && /^\d{4}-\d{2}-\d{2}$/.test(t) && t < today;
}

/** ProjectIssue.isCritical (sent as 1/0). */
export const isCriticalIssue = i => on(i.isCritical);

/** IssuesTab's filter chips. */
export const ISSUE_FILTERS = ['All', 'Critical', 'Pending', 'Overdue', 'Resolved'];

/** IssuesTab.filteredIssues: the query looks in the description, action taken, remarks and logger. */
export function issueMatches(i, query, filter, today) {
  const q = (query || '').trim().toLowerCase();
  if (q && ![i.description, i.actionTaken, i.remarks, i.loggedBy].some(t => (t || '').toLowerCase().includes(q))) return false;
  const status = i.status || '';
  if (filter === 'Critical') return isCriticalIssue(i);
  if (filter === 'Overdue') return isOverdueIssue(i, today);
  if (filter === 'Resolved') return /resolved|closed/i.test(status);
  if (filter === 'Pending') return /pending|open/i.test(status);
  return true;
}

/** WeatherChartTab.parseWeeklyReportDates: the report's 7 days (yyyy-MM-dd) from its first day; [] when the label has no dates. */
export function weekDays(label) {
  const start = weekStart(label);
  return start == null ? [] : Array.from({ length: 7 }, (_, i) => new Date(start + i * 86400000).toISOString().slice(0, 10));
}

/** WeatherChartTab.availableMonths: "yyyy-MM" of the logged days, newest first. */
export const weatherMonths = logs => [...new Set(logs.map(w => (w.date || '').trim().slice(0, 7)).filter(m => /^\d{4}-\d{2}$/.test(m)))].sort().reverse();

/** WeatherChartTab.daysInMonth for "yyyy-MM". */
export const daysInMonth = month => new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5)), 0)).getUTCDate();

/** UserRole.hasPermission(VIEW_AUDIT_LOGS): every role except the two viewer roles. */
export const canViewAuditLogs = claims => ['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER'].includes(claims.role);

/** AuditLogTab's filter chips. */
export const AUDIT_FILTERS = ['All', 'Project', 'Report', 'Issue', 'Payment', 'Document', 'User', 'Sync', 'Backup/Restore/Migration'];

/** AuditLogTab.filteredLogs: the query looks in the action, user, details, device and both values. */
export function auditMatches(log, query, filter) {
  const has = (text, word) => (text || '').toLowerCase().includes(word.toLowerCase());
  const q = (query || '').trim();
  if (q && ![log.actionType, log.user, log.details, log.device, log.oldValue, log.newValue].some(t => has(t, q))) return false;
  const a = log.actionType;
  if (filter === 'User') return ['User', 'Login', 'Logout'].some(w => has(a, w));
  if (filter === 'Backup/Restore/Migration') return ['Backup', 'Restore', 'Migration'].some(w => has(a, w));
  return !filter || filter === 'All' || has(a, filter);
}

/** AuditLogCard's badge: [colour level, Material Symbol] by action type, first match wins. */
export function auditBadge(actionType) {
  const a = (actionType || '').toLowerCase();
  return [
    ['creation', 'ok', 'add_circle'], ['edit', 'link', 'edit'], ['update', 'link', 'edit'], ['deletion', 'neutral', 'delete'],
    ['login', 'neutral', 'login'], ['logout', 'neutral', 'logout'], ['sync', 'link', 'sync'], ['backup', 'watch', 'cloud_upload'],
    ['restore', 'ok', 'cloud_download'], ['migration', 'neutral', 'storage'], ['upload', 'link', 'upload_file'], ['document', 'link', 'upload_file'],
  ].find(([w]) => a.includes(w))?.slice(1) || ['link', 'info'];
}

/* ---------- Status of Construction print (BlueBookForms.sdpPlan, ProjectViewModel.sdpPlanImage) ---------- */

/** SdpSummary.ROAD_STATES. */
export const ROAD_STATES = ['Not completed', 'Concreting', 'Completed'];

/** lotFill on paper (LightNHAColors): the six bands, then neutral for lots that are not built on. */
export const LOT_PRINT_HEX = ['#B91C1C', '#7C3AED', '#EA580C', '#CA8A04', '#2563EB', '#15803D'];
export const lotPrintHex = l => { const c = lotColour(l).slice(4); return c === 'n' ? '#6B6B6B' : LOT_PRINT_HEX[c]; };

/** ProjectViewModel.polygonOf: the lot's [[x, y], ...] (0..1 of the page); [] when unreadable. */
export function lotPolygon(l) {
  try {
    const pts = JSON.parse(l.polygonNormalizedJson || '[]');
    return Array.isArray(pts) ? pts.map(p => [Number(p[0]), Number(p[1])]).filter(([x, y]) => Number.isFinite(x) && Number.isFinite(y)) : [];
  } catch { return []; }
}

/** SdpSummary.parseFrame: a saved "minX,minY,maxX,maxY" box on the page, or null. */
export function parseFrame(text) {
  const v = String(text || '').split(',').map(x => x.trim()).filter(Boolean).map(Number).filter(Number.isFinite);
  if (v.length !== 4 || v.some(x => x < 0 || x > 1) || v[2] <= v[0] || v[3] <= v[1]) return null;
  return v;
}

/** SdpSummary.frameAround: the box around all polygons plus a margin, kept on the page; null with no points. */
export function frameAround(polygons, margin = 0.03) {
  const pts = polygons.flat();
  if (!pts.length) return null;
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  return [Math.max(0, Math.min(...xs) - margin), Math.max(0, Math.min(...ys) - margin), Math.min(1, Math.max(...xs) + margin), Math.min(1, Math.max(...ys) + margin)];
}

/** SdpSummary.of for one site's lots: saleable lots per band and in all, and road lots per state. */
export function sdpSummary(lots) {
  const saleable = lots.filter(l => lotUse(l) === 'Saleable');
  const roadState = p => (p >= 100 ? 2 : p > 0 ? 1 : 0);
  return {
    bands: LOT_BANDS.map((_, b) => saleable.filter(l => lotBand(lotProgress(l)) === b).length),
    saleable: saleable.length,
    roads: ROAD_STATES.map((_, r) => lots.filter(l => lotUse(l) === 'Road Lot' && roadState(lotProgress(l)) === r).length),
  };
}
