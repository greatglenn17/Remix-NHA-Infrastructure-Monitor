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

/**
 * netBillings: each billing's share and balance after it on the net. They are saved from the gross with that day's
 * contract cost, so the net balance is the saved one plus the retention held by then. `payments` in the order added.
 */
export function netBillings(payments) {
  let held = 0;
  return payments.map(p => {
    const gross = Number(p.grossAmount) || 0, share = Number(p.percentage) || 0, balanceShare = Number(p.balancePercentage) || 0;
    held += gross - netAmount(p);
    const cost = share > 0 ? gross * 100 / share : 0;
    return { ...p, percentage: share * (1 - (Number(p.retentionPct) || 0) / 100), balanceAmount: (Number(p.balanceAmount) || 0) + held,
      balancePercentage: cost > 0 ? balanceShare + held / cost * 100 : balanceShare };
  });
}

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

/** BarChart.parse: the chart's versions in effective-date order; blank or unreadable JSON is no chart. */
export function chartVersions(json) {
  let v;
  try { v = JSON.parse(json || '[]'); } catch { return []; }
  return Array.isArray(v) ? [...v].sort((a, b) => String(a.effective).localeCompare(String(b.effective))) : [];
}

const MONTH_DAYS = 30, CELL_DAYS = 5;
const barDays = ([a, b]) => Math.max(0, b - a + 1);
const chartTotal = v => (v.items || []).reduce((t, i) => t + (Number(i.amount) || 0), 0);
/** Item.plannedShare: share of the item planned done by the end of contract `day`, 0 to 1. */
const plannedShare = (item, day) => {
  const bars = item.bars || [], days = bars.reduce((n, b) => n + barDays(b), 0);
  return days ? bars.reduce((n, [a, b]) => n + Math.min(Math.max(Math.min(day, b) - a + 1, 0), barDays([a, b])), 0) / days : 0;
};
/** Version.lastDay: the last contract day any bar covers. */
export const chartLastDay = v => Math.max(0, ...(v.items || []).flatMap(i => (i.bars || []).map(b => b[1])));

/** BarChart.cellOf: [month, cell label 5 … 30] holding contract `day`. */
export function cellOf(day) {
  const d = Math.max(1, day) - 1;
  return [Math.floor(d / MONTH_DAYS) + 1, (Math.floor((d % MONTH_DAYS) / CELL_DAYS) + 1) * CELL_DAYS];
}

/** BarChart.months: the S-curve, Month 1 to the month holding the last bar, as monthly and cumulative %. */
export function chartMonths(v) {
  const total = chartTotal(v), count = Math.ceil(chartLastDay(v) / MONTH_DAYS);
  const pct = amount => total <= 0 ? 0 : amount / total * 100;
  let previous = 0;
  return Array.from({ length: count }, (_, i) => {
    const cash = (v.items || []).reduce((t, it) => t + (Number(it.amount) || 0) * plannedShare(it, (i + 1) * MONTH_DAYS), 0);
    const row = { no: i + 1, monthlyPct: pct(cash - previous), cumulativePct: pct(cash) };
    previous = cash;
    return row;
  });
}

/**
 * BarChart.planOn: on `today` (yyyy-MM-dd), the chart version in force (and all `versions`), its contract day and the planned
 * accomplishment (%); null without a chart or start date. Each item's amount is spread evenly over its bars' contract
 * days (the start date is day 1); a revised chart applies from its effective date; approved suspensions taking effect
 * on or after it stop the clock, a Partial one only for the items it names.
 */
export function planOn(project, docs, today) {
  const versions = chartVersions(project.barChartJson);
  if (!versions.length) return null;
  const start = isoDay(project.dateStarted), now = isoDay(today);
  if (start == null || now == null) return null;
  const v = [...versions].reverse().find(x => { const e = isoDay(x.effective); return e == null || e <= now; }) || versions[0];
  const from = Math.max(isoDay(v.effective) ?? start, start);
  const dayOf = item => (now - start) / DAY + 1 - suspendedDays(docs, from, now, item);
  const total = chartTotal(v);
  const plannedPct = total <= 0 ? 0 : (v.items || []).reduce((t, i) => t + (Number(i.amount) || 0) * plannedShare(i, dayOf(i.name)), 0) / total * 100;
  // Plan.itemTargets: each item's planned accomplishment (% of the item, 2 decimals).
  const itemTargets = Object.fromEntries((v.items || []).map(i => [i.name, Math.round(plannedShare(i, dayOf(i.name)) * 10000) / 100]));
  return { version: v, versions, day: dayOf(undefined), plannedPct, itemTargets };
}

/** BarChart.targetOn: the planned accomplishment (%) on `today`, or null without a chart. */
export const plannedTarget = (project, docs, today) => planOn(project, docs, today)?.plannedPct ?? null;

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

/** ProjectSchedule.suspensionEnd: the last day in a suspension's or continuance's remarks, or '' when it has none. */
export const suspensionEnd = remarks => (/End: (\d{4}-\d{2}-\d{2})/.exec(withEnd(remarks)) || [])[1] || '';

/** ProjectSchedule.withEndDate: remarks ending on `end` instead, the Duration following. Unchanged when `end` is before the Effectivity. */
export function withEndDate(remarks, end) {
  const from = isoDay((/Effectivity: (\d{4}-\d{2}-\d{2})/.exec(remarks) || [])[1]), to = isoDay(end);
  if (from == null || to == null || to < from) return remarks;
  const days = `Duration: ${Math.round((to - from) / DAY) + 1}`;
  return withEnd(/Duration: \d+/.test(remarks) ? remarks.replace(/Duration: \d+/g, days) : `${days} Days | ${remarks}`);
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

/**
 * BarChart.suspensionExtensionDays: the days a time extension for suspension covers: the days approved suspensions
 * that have ended stopped work, less the days of time extensions already logged for a suspension.
 */
export function suspensionExtensionDays(docs) {
  const suspended = periods(docs).reduce((t, { begin, end }) => t + (end == null ? 0 : Math.max(0, (end - begin) / DAY)), 0);
  const claimed = (docs || []).filter(d => /time extension/i.test(d.documentName || '') && /suspen/i.test(d.remarks || ''))
    .reduce((t, d) => t + (Number((/ - (\d+) Days/i.exec(d.documentName) || [])[1]) || 0), 0);
  return Math.max(0, suspended - claimed);
}

/** BarChart.itemNames: the latest chart's work items, for picking what a Partial suspension pauses; none without a chart. */
export const chartItemNames = project => (chartVersions(project.barChartJson).at(-1)?.items || []).map(i => i.name);

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

/** Total billed on the net, after retention, as the phone shows it. */
export const billedTotal = payments => payments.reduce((t, p) => t + netAmount(p), 0);

/** BillingProgress.label. */
export function billedLabel(billed, cost) {
  if (billed <= 0) return 'No billings yet';
  if (cost <= 0) return 'No contract cost';
  return `${(billed / cost * 100).toFixed(2)}%`;
}

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/** The day the project's newest weekly report was added (getLatestWeeklyReportTimes), yyyy-MM-dd in local time; null with none. */
export function latestReportDay(weekly) {
  const at = Math.max(0, ...weekly.map(r => Number(r.createdAtTimestamp) || 0));
  return at ? new Date(at).toLocaleDateString('en-CA') : null;
}

const LEVEL_RANK = { critical: 0, warning: 1, stale: 2 };

/**
 * ProjectAttention.flags: critical, then warning, then stale. `today` and `latestReportAt` are yyyy-MM-dd;
 * `requests` are the project's developer requests.
 */
export function attentionFlags(project, docs, billed, today, requests = [], latestReportAt = null) {
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
  const now = isoDay(today);
  const daysSince = s => { const t = isoDay(s); return t == null || now == null ? null : Math.round((now - t) / DAY); };
  const unpackaged = requests.filter(r => !String(r.datePackaged ?? '').trim());
  if (unpackaged.length) {
    const ages = unpackaged.map(r => daysSince(r.dateReceived)).filter(d => d != null);
    const age = ages.length ? ` (oldest received ${plural(Math.max(0, ...ages), 'day', 'days')} ago)` : '';
    flags.push({ kind: 'requests', level: 'warning', message: `${plural(unpackaged.length, 'developer request', 'developer requests')} not yet packaged${age}` });
  }
  const due = isoDay(effectiveCompletion(project));
  if (!finished && due != null && now != null) {
    const days = Math.round((due - now) / DAY);
    if (days < 0) flags.push({ kind: 'overdue', level: 'critical', message: `Overdue by ${plural(-days, 'day', 'days')}` });
    else if (days <= 30) flags.push({ kind: 'dueSoon', level: 'warning', message: `Due in ${plural(days, 'day', 'days')}` });
  }
  // A suspended project is not expected to report; the start date counts until the first report.
  const stale = daysSince(latestReportAt ?? project.dateStarted);
  if (!finished && project.status !== 'Suspended' && stale != null && stale >= 14) {
    flags.push({ kind: 'stale', level: 'stale', message: `No field update in ${plural(stale, 'day', 'days')}` });
  }
  return flags.sort((a, b) => LEVEL_RANK[a.level] - LEVEL_RANK[b.level]);
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

/**
 * Attachments.refsIn: every `nhafile:` reference anywhere in `value` (a record, a list of them, a project).
 * Backslashes are dropped first: a phone writes a list of references as JSON with each / as \/ (org.json), and a
 * reference read past that would look like a file nothing holds.
 */
export const fileRefsIn = value => JSON.stringify(value ?? '').replace(/\\/g, '').match(/nhafile:(?:[0-9a-f-]{36}\/)?[0-9a-f-]{36}\.(?:jpg|pdf)/g) || [];

/**
 * Attachments' orphan cleanup for one save: the files `before` (records as they were) held that nothing holds after it.
 * `after` are those records as saved (a deleted one left out), `records` the project's records (the saved ones are
 * skipped by uuid) and `project` the project. A mention in the audit log does not keep a file.
 */
export function unusedFiles(before, after, records, project) {
  const saved = new Set(before.map(r => r.uuid));
  const held = new Set(fileRefsIn([after, records.filter(r => !saved.has(r.uuid) && r.type !== 'audit_logs'), project]));
  return [...new Set(fileRefsIn(before))].filter(ref => !held.has(ref));
}

/** Attachments.MAX_BYTES: files must be smaller than this, the same limit as storage.rules. */
export const MAX_FILE_BYTES = 20 * 1024 * 1024;
/** Attachments.Kind.SCAN: a scanned image's longest side, in pixels. */
export const SCAN_MAX_EDGE = 2500;
/** Attachments.Kind.PHOTO: a site photo's longest side, in pixels. */
export const PHOTO_MAX_EDGE = 2048;

/** A new attachment reference as Attachments.store makes one: `nhafile:<projectUuid>/<id>.jpg|.pdf`. */
export const newFileRef = (projectUuid, id, ext) => `nhafile:${projectUuid}/${id}.${ext}`;

/** Width and height no larger than `maxEdge` on the longest side; a smaller image keeps its size. */
export function scaledSize(width, height, maxEdge) {
  const k = Math.min(1, maxEdge / Math.max(width, height));
  return [Math.round(width * k), Math.round(height * k)];
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
export const issuesOf = json => jsonArray(json).map(o => ({ description: str(o?.description), actionTaken: str(o?.actionTaken), remarks: str(o?.remarks), actionBy: str(o?.actionBy) }));/** parseActivitiesList: older rows say dayOfWeek and activity. */
export const activitiesOf = json => jsonArray(json).map(o => ({
  day: str(o?.day, str(o?.dayOfWeek)), description: str(o?.description, str(o?.activity)), remarks: str(o?.remarks),
}));
/** ACCOMPLISHMENT_LINES: the weekly report's only accomplishment lines; items of work fold into them by name. */
export const ACCOMPLISHMENT_LINES = ['Land Acquisition', 'Land Development', 'Housing Construction'];
/** accomplishmentLine: "acquisition" is Land Acquisition, a house, housing or unit is Housing Construction, the rest Land Development. */
export const accomplishmentLine = name => (/acquisition/i.test(name) ? ACCOMPLISHMENT_LINES[0]
  : /\b(house|houses|housing|units?)\b/i.test(name) ? ACCOMPLISHMENT_LINES[2] : ACCOMPLISHMENT_LINES[1]);
/** accomplishmentLines: rows as the three lines, each its items' weighted average (as is when one, plain when no weights). */
export function accomplishmentLines(items) {
  if (!items.length) return items;
  return ACCOMPLISHMENT_LINES.map(line => {
    const rows = items.filter(a => accomplishmentLine(a.itemDescription) === line);
    const w = rows.reduce((t, a) => t + a.weightPct, 0);
    const avg = f => (!rows.length ? 0 : rows.length === 1 ? f(rows[0])
      : w > 0 ? rows.reduce((t, a) => t + f(a) * a.weightPct, 0) / w : rows.reduce((t, a) => t + f(a), 0) / rows.length);
    const targetPct = avg(a => a.targetPct), actualPct = avg(a => a.actualPct);
    return { itemDescription: line, weightPct: w, targetPct, actualPct, variancePct: actualPct - targetPct };
  });
}
/**
 * parseAccomplishmentItemsList: stops at the first row without a description, target or actual, keeping the rows
 * before it; then the three lines, so reports saved item by item read like new ones.
 */
export function accomplishmentOf(json) {
  const out = [];
  for (const o of jsonArray(json)) {
    const t = Number(o?.targetPct), a = Number(o?.actualPct);
    if (typeof o?.itemDescription !== 'string' || o.targetPct == null || o.actualPct == null || !Number.isFinite(t) || !Number.isFinite(a)) break;
    out.push({ itemDescription: o.itemDescription, weightPct: Number(o.weightPct) || 0, targetPct: t, actualPct: a, variancePct: a - t });
  }
  return accomplishmentLines(out);
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

/**
 * A weekly report's photo columns after an edit on the site: `titles` are the captions by reference, `removed` the
 * references taken off, `added` the new photos as [reference, title]. A photo keeps the time and place it was taken.
 * shortcut: a photo added here has neither (the site does not read a picked photo's EXIF, as PhotoDetails.fromExif does).
 */
export function editedPhotos(report, { titles = {}, removed = [], added = [] }) {
  let details;
  try { details = JSON.parse(report.photoDetailsJson || '{}'); } catch { details = {}; }
  if (!details || typeof details !== 'object' || Array.isArray(details)) details = {};
  const detail = (d, title) => ({ title, takenAt: str(d?.takenAt), ...(d?.lat != null && d?.lng != null ? { lat: d.lat, lng: d.lng } : {}) });
  const kept = attachmentList(report.attachedPhotoUrlsJson).filter(ref => !removed.includes(ref));
  const out = {};
  for (const ref of kept) if (details[ref] || titles[ref]) out[ref] = detail(details[ref], titles[ref] ?? str(details[ref]?.title));
  for (const [ref, title] of added) out[ref] = detail(null, title);
  return { attachedPhotoUrlsJson: JSON.stringify([...kept, ...added.map(([ref]) => ref)]), photoDetailsJson: JSON.stringify(out) };
}

/** SitePhotos.latest: the first photo of the newest weekly report that has one, or ''. */
export const latestSitePhoto = weekly => [...weekly].sort((a, b) => (b.createdAtTimestamp || 0) - (a.createdAtTimestamp || 0))
  .map(r => attachmentList(r.attachedPhotoUrlsJson)[0]).find(Boolean) || '';

/** The weekly reports that list a photo in `gone` (the references Storage could not give), in report order, each with how many. */
export const missingPhotos = (weekly, gone) => weekly
  .map(report => ({ report, count: attachmentList(report.attachedPhotoUrlsJson).filter(ref => gone.has(ref)).length }))
  .filter(x => x.count);

export const isPdf = ref => /\.pdf$/i.test(ref || '');

/** Whether a shown value is only a measured figure (an amount, a percentage or a number), which the site sets in its mono face. */
export const isFigure = v => /^-?₱?\d[\d,.]*%?$/.test(String(v ?? '').trim());

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
/** ProjectSchedule.continuances: a suspension's continuances, 1st to last (the phone lists them as added, which the cloud does not record). */
export const continuances = (suspension, docs) => docs.filter(d => continuanceOf(d.documentName) === baseOf(suspension.documentName || ''))
  .sort((a, b) => (parseInt(orderLabel(a.documentName), 10) || 0) - (parseInt(orderLabel(b.documentName), 10) || 0));
/** ProjectSchedule.chained: each suspension, then its continuances, then its resumption; the other orders keep their place. */
export function chained(orders) {
  const resumes = (r, s) => /^resumption$/i.test((ORDER.exec((r.documentName || '').trim()) || [])[2] || '') && baseOf(r.documentName) === baseOf(s.documentName);
  const chains = new Map(orders.filter(isSuspension).map(s => [s, [s, ...continuances(s, orders), ...orders.filter(r => resumes(r, s))]]));
  const inChain = new Set([...chains.values()].flat());
  return orders.flatMap(d => chains.get(d) || (inChain.has(d) ? [] : [d]));
}

/** A "Key: value" field of an order's or letter's remarks. */
export const remarkField = (remarks, key) => ((new RegExp(`${key}: ([^|]*)`).exec(remarks || '') || [])[1] || '').trim();

/**
 * The project's suspensions, lowest order number first. A new continuance starts on the last one; the phone takes the
 * one logged last, which the cloud does not record, so the form lets another be picked.
 */
export const suspensionsByNo = docs => docs.filter(isSuspension)
  .sort((a, b) => String(orderNo(a.documentName)).localeCompare(String(orderNo(b.documentName)), undefined, { numeric: true }));

/**
 * ProjectSchedule.nextContinuance: [name, first day] of the next continuance of `suspension`, e.g. "2nd Partial
 * Continuance of Work Suspension Order No. 1", starting the day after the latest End of the suspension and its
 * continuances so far ('' when none has an End).
 */
export function nextContinuance(suspension, docs, type) {
  const chain = continuances(suspension, docs);
  const lastEnd = [...chain, suspension].map(d => remarkField(withEnd(d.remarks), 'End')).filter(x => isoDay(x) != null).sort().at(-1);
  return [`${ordinal(chain.length + 1)}${type === 'Partial' ? ' Partial' : ''} Continuance of ${baseOf(suspension.documentName || '')}`, lastEnd ? lastDay(lastEnd, 2) : ''];
}

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

/** SdpSummary.BAND_VALUES and ROAD_CONCRETING: the % a band or a road state saves (the top of the band; concreting is 50%). */
export const LOT_BAND_VALUES = [0, 25, 50, 75, 99, 100];
export const ROAD_VALUES = [0, 50, 100];

/** statusForProgress: the construction status that goes with a progress %. */
export const statusForProgress = p => (p <= 0 ? 'Not Started' : p <= 20 ? 'Layout / Excavation Started' : p <= 80 ? 'Under Construction'
  : p <= 99 ? 'Substantially Completed' : 'Completed');

/** The % a pick on the lot form saves: what the lot has while the pick is still its band (or road state), else that pick's value. */
export const pickedProgress = (pct, index, road = false) =>
  ((road ? roadState(pct) : lotBand(pct)) === index ? pct : (road ? ROAD_VALUES : LOT_BAND_VALUES)[index]);

// A lot is billed twice: M1 (80%) in the first columns, M2 (20%) in the m2 ones.
const BILLING = [['billingStatus', 'billingDate', 'billedBy', 'billingReference', 'billingRemarks'],
  ['m2BillingStatus', 'm2BillingDate', 'm2BilledBy', 'm2BillingReference', 'm2BillingRemarks']];

/**
 * ProjectViewModel.updateLotProgress and updateLotBillingStatus for one save of the lot form: the sdp_lot_progress
 * columns to write and their audit entries, or null when the form changed nothing. Progress, status, activity,
 * remarks and photos are written and logged only when one of them changed; M1 and M2 each only when toggled.
 * `f`: { progress, drainage (road lots, else null), constructionStatus, currentActivity, remarks, photos,
 * billing: [{ billed, reference, remarks } for M1, then M2], contractor (an inspection's; without it the lot keeps
 * its own, else the project's) }; `at` is "yyyy-MM-dd HH:mm".
 * shortcut: the phone's audit text names the lot by its local id, which the website does not have, so this uses the lot's label.
 */
export function lotSave(lot, f, { user, at, contractor }) {
  const p = lot.progress || {}, label = lotLabel(lot), pct = n => Math.min(100, Math.max(0, Math.round(Number(n) || 0)));
  const kept = (v, fallback) => ((v || '').trim() ? v : fallback); // Kotlin's ifBlank
  const changes = {}, audits = [];
  const next = {
    physicalProgress: pct(f.progress), ...(f.drainage == null ? {} : { drainageProgress: pct(f.drainage) }),
    constructionStatus: f.constructionStatus, currentActivity: f.currentActivity, remarks: f.remarks, photosJson: JSON.stringify(f.photos),
    ...(f.contractor == null ? {} : { contractor: f.contractor }),
  };
  const stored = {
    physicalProgress: lotProgress(lot), drainageProgress: Number(p.drainageProgress) || 0, constructionStatus: p.constructionStatus ?? 'Not Started',
    currentActivity: p.currentActivity || '', remarks: p.remarks || '', photosJson: JSON.stringify(attachmentList(p.photosJson)),
    contractor: p.contractor || '',
  };
  if (Object.keys(next).some(k => next[k] !== stored[k])) {
    Object.assign(changes, next, { contractor: f.contractor ?? kept(p.contractor, contractor) });
    audits.push({
      actionType: 'Construction Progress Update', details: `Updated Lot Progress (${label}) to ${next.physicalProgress}% (${next.constructionStatus})`,
      oldValue: lot.progress ? `Progress: ${stored.physicalProgress}%, Status: ${stored.constructionStatus}` : 'Not Started',
      newValue: `Progress: ${next.physicalProgress}%, Status: ${next.constructionStatus}, Activity: ${next.currentActivity}`,
    });
  }
  let now = { ...p };
  f.billing.forEach((m, i) => {
    const [status, date, by, ref, remarks] = BILLING[i];
    if (m.billed === billed(now, status)) return;
    const reference = m.billed ? m.reference.trim() : '';
    const set = m.billed ? { [status]: 'BILLED', [date]: at, [by]: user, [ref]: reference, [remarks]: m.remarks.trim() }
      : { [status]: 'NOT BILLED', [date]: '', [by]: '', [ref]: '', [remarks]: '' };
    const before = lotBillingLabel(now);
    now = { ...now, ...set };
    Object.assign(changes, set);
    audits.push({
      actionType: 'Developer Billing Update', details: `Updated M${i + 1} billing for ${label} to ${set[status]} (Ref: ${reference})`,
      oldValue: before, newValue: `${lotBillingLabel(now)}, Billed By: ${user}, Date: ${at}, Ref: ${reference}`,
    });
  });
  if (!audits.length) return null;
  return { changes: { ...changes, createdBy: kept(p.createdBy, user), createdDate: kept(p.createdDate, at), lastModifiedBy: user, lastModifiedDate: at }, audits };
}

/**
 * SdpSites.current with each plan's active lots: per site (in the order its first plan was uploaded), its active plan
 * with the highest version, else its highest version. Each lot carries its latest progress row as `progress` (or null).
 * `plans` are the site's revisions, latest first.
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
    return { site, plan, plans: [...list].sort((a, b) => (Number(b.version) || 0) - (Number(a.version) || 0)), lots: lots.filter(l => l._ref_sdpPlanId === plan.uuid).map(l => ({ ...l, progress: progress.get(l.uuid) || null })) };
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

const NUMBERED_USES = { 'Road Lot': 'RL', Easement: 'EA' };

/** HOUSING_BILLING_TYPES. */
export const HOUSING_BILLING_TYPES = ['Land acquisition', 'Land development', 'M1 - Housing unit', 'M2 - Housing unit'];

/** SummarySection.BillingStatus: lots or units billed per housing billing type, and their share of `total` saleable units. */
export const billingStatus = (payments, total) => HOUSING_BILLING_TYPES.map(type => {
  const units = Math.round(payments.filter(p => p.billingType === type).reduce((t, p) => t + billedUnits(p), 0) * 100) / 100;
  const label = type + (type.startsWith('M1') ? ' (80%)' : type.startsWith('M2') ? ' (20%)' : '');
  return { label, units, share: total > 0 ? `${(units * 100 / total).toFixed(2)}%` : '-' };
});

/** billedByAmount: land development is billed by the amount accomplished; the other housing types by whole lots or units. */
export const billedByAmount = type => type === 'Land development';
/** billedUnits: a billing's lots or units as typed; for one billed by amount, its gross over the amount per lot (2 decimals). */
export const billedUnits = p => Number(p.units) || (Number(p.unitPrice) > 0 ? Math.round((Number(p.grossAmount) || 0) / Number(p.unitPrice) * 100) / 100 : 0);

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
/** SdpSummary.roadState: the ROAD_STATES index of a road lot's progress. */
export const roadState = p => (p >= 100 ? 2 : p > 0 ? 1 : 0);

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
  return {
    bands: LOT_BANDS.map((_, b) => saleable.filter(l => lotBand(lotProgress(l)) === b).length),
    saleable: saleable.length,
    roads: ROAD_STATES.map((_, r) => lots.filter(l => lotUse(l) === 'Road Lot' && roadState(lotProgress(l)) === r).length),
  };
}

/* ---------- Website edits (records the phones sync, written the way RecordSync writes them) ---------- */

/** firestore.rules isWriter: approved (accounts from before approvals have a role and no approved claim) and not a Viewer or Super Viewer. */
export const isWriter = claims => (claims.approved ?? Boolean(claims.role)) === true && !['VIEWER', 'SUPER_VIEWER'].includes(claims.role);

/** The project rule on top: the Super Admin, or a writer assigned to the project. */
export const canEdit = (project, uid, claims) => isWriter(claims) && (claims.role === 'SUPER_ADMIN' || (project.assignedUids || []).includes(uid));

/** AddIssueDialog's choices. */
export const ISSUE_STATUSES = ['Pending', 'Approved', 'No Action'];
export const ISSUE_ACTION_BY = ['Implementing Office', 'Contractor/Developer'];

/**
 * The fields an edit writes to an existing cloud record. The revision goes up by one and the edit time changes, so
 * every phone takes it (RemoteVersion.isNewer); every other field in `data` is kept as stored.
 */
export function editedRecord(stored, changes, { uid, now, assignedUids }) {
  let data;
  try { data = JSON.parse(stored.data || '{}'); } catch { throw new Error('This record could not be read, so it cannot be edited here.'); }
  return { revision: (Number(stored.revision) || 1) + 1, updatedAt: now, updatedByUid: uid, assignedUids, data: JSON.stringify({ ...data, ...changes }) };
}

/** A new audit_logs record (AuditLog synced by RecordSync), with "Website" as the device. */
export function auditRecord({ uuid, projectUuid, assignedUids, uid, now, user, actionType, details, oldValue = '', newValue = '' }) {
  return {
    uuid, type: 'audit_logs', projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false,
    data: JSON.stringify({ timestamp: now, user, device: 'Website', actionType, oldValue, newValue, details }),
  };
}

/**
 * A new project_issues record as RecordSync.buildDocument uploads one: the ProjectIssue row without its local ids
 * (MainActivity's addProjectIssue: no remarks, logged by the signed-in name), revision 1, booleans as 1/0.
 */
export function newIssueRecord({ uuid, projectUuid, assignedUids, uid, now, loggedBy }, f) {
  return {
    uuid, type: 'project_issues', projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false,
    data: JSON.stringify({
      date: f.date, description: f.description, actionTaken: f.actionTaken, remarks: '', loggedBy, timestamp: now,
      status: f.status, isCritical: f.isCritical ? 1 : 0, targetDate: f.targetDate || '', actionBy: f.actionBy || '',
    }),
  };
}

/** ProjectViewModel.addProjectIssue's audit entry, word for word. */
export const newIssueAudit = f => ({
  actionType: 'Issue Creation', details: `Logged issue: ${f.description.slice(0, 30)}...`, oldValue: '',
  newValue: `Description: ${f.description}, Status: ${f.status}, Critical: ${Boolean(f.isCritical)}`,
});

/** AddIssueDialog's checks: a date, the issue and the action taken are required; dates are yyyy-MM-dd. */
export function issueErrors(f) {
  const iso = /^\d{4}-\d{2}-\d{2}$/;
  return [
    !iso.test(f.date || '') && 'Pick the date.',
    !f.description?.trim() && 'Type the issue or concern.',
    !f.actionTaken?.trim() && 'Type the action taken.',
    f.targetDate && !iso.test(f.targetDate) && 'Pick a valid target date.',
  ].filter(Boolean);
}

/** ProjectAttention.REQUEST_TYPES. */
export const REQUEST_TYPES = ['Work Suspension Order', 'Continuance of Work Suspension Order', 'Work Resumption Order', 'Variation Order', 'Time Extension'];

/**
 * A request card's title. Details that are only an order number join the type ("Work Suspension Order No. 1");
 * "1.2" on a continuance is suspension 1's 2nd. Any other details stay on their own line.
 */
export function requestTitle(r) {
  const type = r.requestType || '-', d = (r.details || '').trim();
  const cont = type === REQUEST_TYPES[1] && /^(\d+)\.(\d+)$/.exec(d);
  if (cont) return `${ordinal(+cont[2])} ${type} No. ${cont[1]}`;
  return /^\d+$/.test(d) ? `${type} No. ${d}` : type;
}

/** DeveloperRequestDialog's checks: the date received is required; any date is yyyy-MM-dd. */
export function requestErrors(f) {
  const iso = /^\d{4}-\d{2}-\d{2}$/;
  return [
    !iso.test(f.dateReceived || '') && 'Pick the date received by NHA.',
    f.dateRequested && !iso.test(f.dateRequested) && "Pick a valid date of the developer's request.",
    f.datePackaged && !iso.test(f.datePackaged) && 'Pick a valid date packaged.',
  ].filter(Boolean);
}

/** ContractorLetterDialog's checks: the date of the letter and the subject are required; any date is yyyy-MM-dd. */
export function letterErrors(f) {
  const iso = /^\d{4}-\d{2}-\d{2}$/;
  return [
    !iso.test(f.dateSent || '') && 'Pick the date of the letter.',
    !f.subject?.trim() && 'Type the subject.',
    f.dateReceived && !iso.test(f.dateReceived) && 'Pick a valid date received.',
    f.dateReplied && !iso.test(f.dateReplied) && 'Pick a valid date of reply.',
  ].filter(Boolean);
}

/**
 * OrderFields.valid, as messages: the checks on the order a new request asks for. `o` holds the order form's fields;
 * `suspension` is the one a continuance continues.
 */
export function orderErrors(type, o, suspension) {
  const days = v => !/^\d+$/.test((v || '').trim()) && 'Type the number of days, in whole days.';
  return ({
    'Work Suspension Order': [days(o.wsDays)],
    'Continuance of Work Suspension Order': suspension ? [days(o.coDays), !o.coEffectivity && 'Pick the effectivity date.']
      : ['No work suspension order is logged yet. Log the suspension first.'],
    'Variation Order': [(o.voAmount || '').trim() && parseAmount(o.voAmount) == null && 'Type a valid amount.'],
    'Time Extension': [days(o.teDays)],
  }[type] || []).filter(Boolean);
}

/**
 * OrderFields.name and remarks: the document a new request files in Documents, named and worded as the phone does, so
 * the schedule and the contract cost read it. Only after orderErrors.
 */
export function orderDocument(type, o, suspension, docs = []) {
  const partial = t => (t === 'Partial' ? 'Partial ' : '');
  // ProjectSchedule.suspensionRemarks: withEnd adds the End when there are days and an effectivity date.
  const span = (days, effectivity) => withEnd(`Duration: ${Number(days.trim())} Days | Effectivity: ${effectivity}`);
  switch (type) {
    case 'Work Suspension Order': return {
      documentName: `${partial(o.wsType)}Work Suspension Order No. ${o.wsNo.trim()}`,
      remarks: span(o.wsDays, o.wsEffectivity) + (o.wsReason.trim() ? ` | Reason: ${o.wsReason.trim()}` : '')
        + (o.wsType === 'Partial' && o.wsItems.length ? ` | Items: ${o.wsItems.join('; ')}` : ''),
    };
    case 'Continuance of Work Suspension Order': return { documentName: nextContinuance(suspension, docs, o.coType)[0], remarks: span(o.coDays, o.coEffectivity) };
    case 'Work Resumption Order': return {
      documentName: `${partial(o.wrType)}Work Resumption Order No. ${o.wrNo.trim()}`, remarks: `Effectivity: ${o.wrEffectivity} | Remarks: ${o.wrRemarks}`,
    };
    case 'Variation Order': return {
      documentName: `Variation Order No. ${o.voNo} (${o.voType}) - PHP ${o.voAmount.replace(/\s/g, '')}`,
      remarks: `Reason: ${o.voReason}${o.voDateApproved ? ` | Date Approved: ${o.voDateApproved}` : ''}`,
    };
    default: return { documentName: `Time Extension No. ${o.teNo.trim()} - ${o.teDays.trim()} Days`, remarks: `Reason: ${o.teReason}` };
  }
}

/** A new record as RecordSync uploads one: a row's columns, without its local ids, as `data`. */
const newRecord = ({ uuid, projectUuid, assignedUids, uid, now }, type, data) =>
  ({ uuid, type, projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false, data: JSON.stringify(data) });

/**
 * A lot's first sdp_lot_progress record: the SdpLotProgress row with lotSave's changes over its defaults. Its plan and
 * lot travel as their uuids under `_ref_`, as RecordSync sends them. A phone that already started a row for the lot
 * takes this one onto it (RecordSync.sameLotProgress).
 */
export const newLotProgressRecord = (ids, lot, changes) => newRecord(ids, 'sdp_lot_progress', {
  physicalProgress: 0, drainageProgress: 0, constructionStatus: 'Not Started', currentActivity: '', startDate: '', targetCompletionDate: '',
  contractor: '', remarks: '', billingStatus: 'NOT BILLED', billingDate: '', billedBy: '', billingReference: '', billingRemarks: '',
  m2BillingStatus: 'NOT BILLED', m2BillingDate: '', m2BilledBy: '', m2BillingReference: '', m2BillingRemarks: '', photosJson: '[]',
  createdBy: '', createdDate: '', lastModifiedBy: '', lastModifiedDate: '',
  ...changes, _ref_sdpPlanId: lot._ref_sdpPlanId, _ref_sdpLotId: lot.uuid,
});

/** A new contractor_letters record: the ContractorLetter row. */
export const newLetterRecord = (ids, f) => newRecord(ids, 'contractor_letters', {
  dateSent: f.dateSent, subject: f.subject, actionTaken: f.actionTaken, dateReplied: f.dateReplied, replyFileUrl: f.replyFileUrl || '',
  dateReceived: f.dateReceived, receivedFileUrl: f.receivedFileUrl || '',
});

/** A new developer_requests record: the DeveloperRequest row. */
export const newRequestRecord = (ids, f) => newRecord(ids, 'developer_requests', {
  requestType: f.requestType, details: f.details, dateRequested: f.dateRequested, dateReceived: f.dateReceived, datePackaged: f.datePackaged,
  requestFileUrl: f.requestFileUrl || '',
});

/**
 * A new pending_documents record, off the core checklist: an order as ProjectViewModel.addPendingDocument saves one
 * (Pending, no file), or a related file as addRelatedDocument does, with its status and scanned copy.
 */
export const newDocumentRecord = (ids, { documentName, remarks, status = 'Pending', fileUrl = '' }) =>
  newRecord(ids, 'pending_documents', { documentName, status, remarks, isCoreChecklist: 0, fileUrl });

/** AddRelatedDocumentDialog's document types (the last takes a title typed in) and statuses. */
export const RELATED_TYPES = ['Notice of Award', 'Notice to Proceed', 'Contract Agreement', 'Memorandum of Agreement', 'RFA / CAF', 'NHA Board Resolution',
  'Other / Specific Custom Document...'];
export const RELATED_STATUSES = ['Approved', 'Notarized', 'Signed', 'Submitted', 'Pending'];

/**
 * AddRelatedDocumentDialog's document: named by the type picked, or by the title typed for the last type ('' when
 * that is blank, which the form refuses); the remarks are the reference, then the description.
 */
export const relatedDocument = f => ({
  documentName: f.docType === RELATED_TYPES.at(-1) ? (f.title || '').trim() : f.docType,
  status: f.status, remarks: [f.reference, f.remarks].filter(x => (x || '').trim()).join(' | '),
});

/** ProjectViewModel.addRelatedDocument's audit entry, word for word. */
export const relatedAddedAudit = (name, fileUrl) => ({
  actionType: 'Document Upload', details: `Uploaded project document: ${name}`, oldValue: '', newValue: `Doc: ${name}, URL: ${fileUrl}`,
});

/** ProjectViewModel.updatePendingDocumentFileUrl's audit entry, word for word. */
export const fileAttachedAudit = (d, fileUrl) => ({
  actionType: 'Document Upload', details: `Uploaded file attachment for ${d.documentName}`, oldValue: d.fileUrl || '', newValue: fileUrl,
});

/** ProjectViewModel.addPendingDocument's audit entry, word for word. */
export const addedDocumentAudit = name => ({
  actionType: 'Document Upload', details: `Added document checklist item: ${name}`, oldValue: '', newValue: `Document: ${name}, Status: Pending`,
});

/** OrderStatusDialog's statuses; only an Approved order changes the dates and the cost. */
export const ORDER_STATUSES = ['Pending', 'Submitted', 'Signed', 'Notarized', 'Approved'];

/** Permission.APPROVE_ORDER: the Super Admin and Engineer Admins set or undo Approved and delete an order. The phone checks this on its form, not in firestore.rules. */
export const canApprove = claims => ['SUPER_ADMIN', 'ENGINEER_ADMIN'].includes(claims.role);

/**
 * DocumentsSection.keepOrderFields: the edited remarks with any Duration, Effectivity, End, Date Approved or Items
 * field of the original put back when the edit dropped it, so the schedule and the cost keep reading them. A field
 * whose value was changed (its key still there) stays as edited.
 */
export function keepOrderFields(original, edited) {
  const dropped = [...(original || '').matchAll(/\b(Duration|Effectivity|End|Date Approved|Items):[^|]*/gi)]
    .filter(m => !new RegExp(`\\b${m[1]}:`, 'i').test(edited)).map(m => m[0].trim());
  return [...dropped, edited.trim()].filter(Boolean).join(' | ');
}

/** ProjectViewModel.updatePendingDocumentStatus's audit entry, word for word; null for the same status (logAuditAction skips an unchanged value). */
export const orderStatusAudit = (d, status) => (status === d.status ? null : {
  actionType: 'Document Upload', details: `Updated document status for ${d.documentName}`, oldValue: `Status: ${d.status}`, newValue: `Status: ${status}`,
});

/** ProjectViewModel.deleteContractorLetter's audit entry, word for word. */
export const deletedLetterAudit = l => ({
  actionType: 'Letter to Contractor', details: `Deleted letter of ${l.dateSent}: ${l.subject}`, oldValue: l.actionTaken || '', newValue: '',
});

/** ProjectViewModel.deleteDeveloperRequest's audit entry, word for word. */
export const deletedRequestAudit = r => ({
  actionType: 'Developer Request', details: `Deleted ${r.requestType} request received ${r.dateReceived}`, oldValue: r.details || '', newValue: '',
});

/** ProjectViewModel.saveContractorLetter's audit entry, word for word. */
export const letterAudit = f => ({
  actionType: 'Letter to Contractor', details: `Letter of ${f.dateSent}: ${f.subject}${f.dateReplied ? `, replied ${f.dateReplied}` : ''}`,
  oldValue: '', newValue: f.actionTaken,
});

/** ProjectViewModel.saveDeveloperRequest's audit entry, word for word. */
export const requestAudit = f => ({
  actionType: 'Developer Request', details: `${f.requestType} request received ${f.dateReceived}${f.datePackaged ? `, packaged ${f.datePackaged}` : ''}`,
  oldValue: '', newValue: f.details,
});

/** FormValidation.parseAmount: digits with an optional decimal part; commas and spaces are ignored. */
export function parseAmount(text) {
  const t = String(text ?? '').replace(/[, ]/g, '');
  return /^(\d+(\.\d*)?|\.\d+)$/.test(t) ? Number(t) : null;
}

/** AddPaymentDialog's retention choices. */
export const RETENTIONS = [0, 5, 10];

/** PaymentDialogs.currentMonthPeriod: "November 1 - 30, 2026" for a yyyy-MM-dd day. */
export function monthPeriod(day) {
  const [y, m] = day.split('-').map(Number);
  const month = new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString('en-US', { month: 'long', timeZone: 'UTC' });
  return `${month} 1 - ${new Date(Date.UTC(y, m, 0)).getUTCDate()}, ${y}`;
}

const billedByUnit = f => Boolean(f.billingType) && !billedByAmount(f.billingType);
/** AddPaymentDialog's gross: units × amount per lot or unit for a housing billing type counted in whole ones, else the gross typed. */
export const billingGross = f => (billedByUnit(f) ? (Number.parseInt(f.units, 10) || 0) * (parseAmount(f.unitPrice) ?? 0) : parseAmount(f.grossAmount) ?? 0);

/** AddPaymentDialog's checks (FormValidation.amountError), plus a valid date. */
export function billingErrors(f) {
  const byUnit = billedByUnit(f);
  const units = String(f.units ?? '').trim();
  return [
    !/^\d{4}-\d{2}-\d{2}$/.test(f.date || '') && 'Pick the date.',
    !byUnit && (!String(f.grossAmount ?? '').trim() ? 'Type the gross amount.' : parseAmount(f.grossAmount) == null && 'The gross amount must be a number.'),
    byUnit && (!units ? `Type the number of ${billingUnitName(f.billingType)}s.` : !/^\d+$/.test(units) || Number(units) < 1 ? 'The number must be a whole number.' : false),
    f.billingType && (!String(f.unitPrice ?? '').trim() ? `Type the amount per ${billingUnitName(f.billingType)}.` : parseAmount(f.unitPrice) == null && 'The amount must be a number.'),
  ].filter(Boolean);
}

/** What AddPaymentDialog's form sets on a billing; share, balance and the scanned DV are not among them. */
function billingFields(f) {
  const byUnit = billedByUnit(f);
  return {
    name: f.name.trim() || f.billingType, dvNo: f.dvNo.trim(), date: f.date, periodCovered: f.periodCovered.trim(), grossAmount: billingGross(f),
    billingType: f.billingType, units: byUnit ? Number(f.units) : 0, unitPrice: f.billingType ? parseAmount(f.unitPrice) : 0,
    retentionPct: Number(f.retentionPct) || 0,
  };
}

/** BillingAbstract.latestWeekly: the report of the latest week, the one the project's target and actual follow; undefined with none. */
export const latestWeekly = weekly => weekly.reduce((best, r) =>
  (best == null || (weekStart(r.reportingWeek) ?? -Infinity) > (weekStart(best.reportingWeek) ?? -Infinity) ? r : best), undefined);

/**
 * BillingAbstract.partAmounts: the contract amount of each accomplishment line on `asOf`, from the bar chart in force;
 * without one from the Scope of Works (an item without an amount takes its weight of the current cost).
 */
export function partAmounts(project, docs, asOf) {
  const plan = planOn(project, docs, asOf), cost = currentCost(project, docs);
  const items = plan && chartTotal(plan.version) > 0 ? plan.version.items
    : String(project.scopeOfWork ?? '').trim() ? scopeItems(project.scopeOfWork).map(i => ({ name: i.name, amount: i.amount > 0 ? i.amount : i.weightPct * cost / 100 })) : [];
  return ACCOMPLISHMENT_LINES.map(line => Math.round(items.filter(i => accomplishmentLine(i.name) === line).reduce((t, i) => t + (Number(i.amount) || 0), 0) * 100) / 100);
}

/**
 * BillingAbstract.aheadErrors: what is billed never passes the work done. With the form `f` saved (in place of `old`
 * when editing), the billed share of its part's contract amount, and of the contract cost for all parts, against the
 * actual, at 2 decimals: the latest uploaded abstract's (`abstracts`, the project_abstracts records) when the project
 * has one, else the latest weekly report's. Only billings with a housing type count, and only a new or raised one is
 * checked, so a billing already saved can still be corrected.
 */
export function billingAheadErrors(project, docs, payments, weekly, f, old = null, abstracts = []) {
  const gross = billingGross(f);
  if (!f.billingType || (old && old.billingType === f.billingType && Math.round(gross * 100) <= Math.round((Number(old.grossAmount) || 0) * 100))) return [];
  const abstract = latestAbstract(abstracts), last = abstract ? null : latestWeekly(weekly);
  const actuals = abstract ? abstractLineActuals(abstract).map(Number) : last ? accomplishmentOf(last.accomplishmentItemsJson).map(a => a.actualPct) : [];
  const from = abstract ? ` of the abstract as of ${displayDate(abstract.asOf)}` : last ? ` of ${last.reportingWeek}` : '';
  const billed = [...payments.filter(p => p.billingType && p.uuid !== old?.uuid), { billingType: f.billingType, grossAmount: gross }];
  const sum = list => list.reduce((t, p) => t + (Number(p.grossAmount) || 0), 0), round = v => Math.round(v * 100) / 100;
  const line = accomplishmentLine(f.billingType), k = ACCOMPLISHMENT_LINES.indexOf(line);
  return [
    [line, sum(billed.filter(p => accomplishmentLine(p.billingType) === line)), partAmounts(project, docs, f.date)[k], actuals[k] ?? 0],
    ['Overall', sum(billed), currentCost(project, docs), Number(abstract ? abstract.overallPct : last ? last.actualAccomplishmentPct : project.actualAccomplishment) || 0],
  ].flatMap(([name, amount, of, actual]) => (of > 0 && round(amount / of * 100) > round(actual)
    ? [`${name}: billed to date ${round(amount / of * 100).toFixed(2)}% would pass the actual ${round(actual).toFixed(2)}%${from}.`] : []));
}

/** BillingAbstract.units: the project's number of units as typed ("370 units"), else its saleable lots, else 1 (a lump sum). */
export const abstractUnits = (project, saleable = 0) => Number.parseInt(String(project.numberOfUnits ?? '').replace(/,/g, '').match(/\d+/)?.[0] ?? '', 10) || saleable || 1;

/**
 * BillingAbstract.of: the Abstract of Physical Accomplishment the contractor attaches before a billing (the district's
 * billing format), printed from the uploaded `abstract` on the Actual Accomplishment tab. Each part with a contract
 * amount has its lines { no, name, unit, type, amount, previous, thisPeriod }. The amounts are the abstract's own
 * (abstractLineAmounts), housing as Milestone 1 (80%) and Milestone 2 (20%). `previous` is the gross of the saved
 * billings of that type dated on or before the abstract's "As of" date. `thisPeriod` is typed and never saved:
 * `period` is { covered, lots, development, m1, m2 }, this billing's land acquisition lots, land development amount
 * and Milestone 1 and 2 housing units; a quantity is an amount as quantity x (line amount / `units`), and anything
 * that is not a number above 0 counts as 0. `targetPct` is the plan on the "As of" date.
 */
export function billingAbstract(project, docs, payments, abstract, period, units) {
  const [acquisition, development, housing] = abstractLineAmounts(abstract), asOf = isoDay(abstract.asOf);
  // A billing's date is yyyy-MM-dd text: compared as days, so one without a date is not counted.
  const before = payments.filter(p => { const day = isoDay(p.date); return day != null && asOf != null && day <= asOf; });
  const round = v => Math.round(v * 100) / 100, typed = v => Math.max(0, Number(v) || 0);
  const line = (no, name, unit, type, amount, thisPeriod) => ({ no, name, unit, type, amount,
    previous: round(before.filter(p => p.billingType === type).reduce((t, p) => t + (Number(p.grossAmount) || 0), 0)), thisPeriod: round(thisPeriod) });
  const counted = (no, name, unit, type, amount, quantity) => line(no, name, unit, type, amount, typed(quantity) * (amount / units));
  const m1 = round(housing * 0.8);
  return {
    targetPct: plannedTarget(project, docs, abstract.asOf) ?? (Number(project.targetAccomplishment) || 0),
    parts: [
      { name: 'Land Acquisition Cost', lines: [counted('1', 'LAND ACQUISITION COST', 'LOT', HOUSING_BILLING_TYPES[0], acquisition, period.lots)] },
      { name: 'Land Development', lines: [line('2', 'LAND DEVELOPMENT', 'LOT', HOUSING_BILLING_TYPES[1], development, typed(period.development))] },
      { name: 'Housing Construction', heading: ['3', 'HOUSING CONSTRUCTION'], lines: [
        counted('3.1', 'MILESTONE 1 (80%)', 'UNIT', HOUSING_BILLING_TYPES[2], m1, period.m1), counted('3.2', 'MILESTONE 2 (20%)', 'UNIT', HOUSING_BILLING_TYPES[3], round(housing - m1), period.m2)] },
    ].filter(part => part.lines.some(l => l.amount > 0)),
  };
}

/**
 * MoneySection.editedBillings: the [billing, changes] pairs to save when `old` is edited with the form `f`. A changed
 * gross recomputes its share and moves its own and every later balance by the difference; earlier billings stay.
 * shortcut: "later" is the order shown here (by date), not the phone's order added; the same unless one was back-dated.
 */
export function editedBillings(payments, old, f, cost) {
  const fields = billingFields(f), delta = fields.grossAmount - (Number(old.grossAmount) || 0);
  if (delta === 0) return [[old, fields]];
  const shift = balanceShift(delta, cost);
  return [[old, { ...fields, ...shift(old), percentage: cost > 0 ? fields.grossAmount / cost * 100 : 0 }], ...laterBillings(payments, old).map(p => [p, shift(p)])];
}

const laterBillings = (payments, old) => payments.slice(payments.findIndex(p => p.uuid === old.uuid) + 1);
/** A billing's balance once the gross billed up to it has grown by `delta`. */
const balanceShift = (delta, cost) => p => ({
  balanceAmount: (Number(p.balanceAmount) || 0) - delta, balancePercentage: (Number(p.balancePercentage) || 0) - (cost > 0 ? delta / cost * 100 : 0),
});

/**
 * The pairs to save when `old` is deleted: itself (null changes) and every later billing with its balance given back
 * the deleted gross (MoneySection.billingsAfterDelete).
 */
export function deletedBillingEdits(payments, old, cost) {
  const shift = balanceShift(-(Number(old.grossAmount) || 0), cost);
  return [[old, null], ...laterBillings(payments, old).map(p => [p, shift(p)])];
}

/** A record marked deleted the way FirestoreSyncManager uploads a delete: the tombstone, one revision up. */
export const deletedRecord = (stored, { uid, now, assignedUids }) => ({ isDeleted: true, revision: (Number(stored.revision) || 1) + 1, updatedAt: now, updatedByUid: uid, assignedUids });

/** ProjectViewModel.deleteProjectPayment's audit entry, word for word. */
export const deletedPaymentAudit = p => ({
  actionType: 'Payment Deletion', details: `Deleted billing: ${p.name}${p.dvNo ? ` (${p.dvNo})` : ''}`, oldValue: `DV: ${p.dvNo}, Gross: ${formatPhp(Number(p.grossAmount) || 0)}`, newValue: '',
});

/** ProjectViewModel.updateProjectPayment's audit entry, for a billing as it will be saved. */
export const editedPaymentAudit = p => ({
  actionType: 'Payment Creation', details: `Updated billing: ${p.name}`, oldValue: 'Previous payment info', newValue: `DV: ${p.dvNo}, Gross: ₱${p.grossAmount}`,
});

/**
 * A new project_payments record as RecordSync uploads one: the ProjectPayment row without its local ids. Share and
 * balance are on the gross, with the contract cost of today and every billing before it (MoneySection's onSubmit).
 */
export function newPaymentRecord({ uuid, projectUuid, assignedUids, uid, now }, f, payments, cost) {
  const grossAmount = billingGross(f);
  const balanceAmount = cost - (payments.reduce((t, p) => t + (Number(p.grossAmount) || 0), 0) + grossAmount);
  return {
    uuid, type: 'project_payments', projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false,
    data: JSON.stringify({
      ...billingFields(f), percentage: cost > 0 ? grossAmount / cost * 100 : 0, balanceAmount,
      balancePercentage: cost > 0 ? balanceAmount / cost * 100 : 0, fileUrl: f.fileUrl || '',
    }),
  };
}

/** ProjectViewModel.addProjectPayment's audit entry (amounts written plainly, not in Kotlin's 1.5E7 form). */
export function newPaymentAudit(record) {
  const p = JSON.parse(record.data);
  return { actionType: 'Payment Creation', details: `Created payment billing: ${p.name} (${p.dvNo})`, oldValue: '',
    newValue: `Billing: ${p.name}, DV: ${p.dvNo}, Gross: ₱${p.grossAmount}, Retention: ${p.retentionPct}%` };
}

/* ---------- Sign up (LoginScreen in sign-up mode, ProjectViewModel.authenticateWithFirebase) ---------- */

/** AuthManager.personName: "Juan C. Dela Cruz" from its parts; the middle initial is optional and gets its dot. */
export const personName = (first, mi, last) =>
  [first.trim(), mi.trim().replace(/\.+$/, '').toUpperCase(), last.trim()].map((p, i) => (i === 1 && p ? `${p}.` : p)).filter(Boolean).join(' ');

/** New accounts start as Field Engineer or Viewer; the Super Admin promotes and approves them. */
export const SIGN_UP_ROLES = ['FIELD_ENGINEER', 'VIEWER'];

/** The sign-up form's checks: the app's (first and last name, an email, 6+ characters) plus a matching retype. */
export function signUpErrors(f) {
  const email = f.email.trim();
  return [
    (!f.firstName.trim() || !f.lastName.trim()) && 'Type your first and last name.',
    (!email.includes('@') || !email.includes('.')) && 'Please enter a valid email address.',
    f.password.trim().length < 6 && 'Password must be at least 6 characters long.',
    f.password !== f.confirm && 'The two passwords do not match.',
  ].filter(Boolean);
}

/** users/{uid} as FirestoreSyncManager.syncUserProfile writes it at sign-up; firestore.rules allow exactly this. */
export function newProfile(uid, f, now) {
  const email = f.email.trim().toLowerCase();
  return {
    uid, email, updatedAt: now,
    displayName: personName(f.firstName, f.middleInitial, f.lastName) || email.split('@')[0],
    role: SIGN_UP_ROLES.includes(f.role) ? f.role : 'FIELD_ENGINEER',
    position: f.position.trim(), office: f.office.trim(),
  };
}

/* ---------- Users (ManageUsersDialog, ProjectViewModel.approveUser / setUserRole / setUserSeesAll) ---------- */

/** The roles the Super Admin can give; Super Admin itself is only the designated account's. */
export const ASSIGNABLE_ROLES = ['ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER', 'SUPER_VIEWER'];

/** One users/{uid} doc as refreshRegisteredUsers reads it: a profile from before approvals counts as approved. */
export const userRow = (uid, d) => ({
  uid, name: d.displayName?.trim() || d.email?.trim() || uid, email: d.email || '', role: d.role || '',
  position: d.position || '', office: d.office || '', seesAllProjects: d.seesAllProjects === true, approved: d.approved !== false,
});

/** Everyone but the Super Admin, accounts waiting for approval first, then by name. */
export const manageableUsers = rows => rows.filter(u => u.role !== 'SUPER_ADMIN')
  .sort((a, b) => a.approved - b.approved || a.name.localeCompare(b.name));

/**
 * The users/{uid} update for one change. The rules want a role on every Super Admin edit, so a profile without one
 * gets Field Engineer, as the claims function reads it.
 */
export function userChange(user, change) {
  const role = user.role || 'FIELD_ENGINEER';
  if ('approved' in change) return { approved: true, role };
  if ('role' in change) {
    if (!ASSIGNABLE_ROLES.includes(change.role)) throw new Error('That role cannot be given here.');
    return { role: change.role };
  }
  return { seesAllProjects: change.seesAllProjects === true, role };
}

/* ---------- New weekly report (AddWeeklyReportDialog, ProjectViewModel.submitWeeklyReportWithWeather) ---------- */

export const MANPOWER_DESIGNATIONS = ['Project Manager', 'Project Engineer', 'Project Architect', 'Safety Officer', 'Foreman', 'Operator', 'Labor',
  'Security Guard', 'Driver', 'Warehouseman'];
/** The form's first manpower rows: the usual designations at zero, so no sample headcount is saved as real. */
export const MANPOWER_START = ['Project Engineer', 'Foreman', 'Masons / Carpenters', 'Laborers'];
export const EQUIPMENT_NAMES = ['Backhoe', 'Bulldozer', 'Road Roller', 'Forward Truck', 'Mini Dump Truck', 'Generator'];
export const EQUIPMENT_STATUSES = ['Operational', 'Idle', 'For Repair'];
/** ActivityDays.DAYS. */
export const ACTIVITY_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

/** WeekRange.containing's first day: the Saturday on or before `day` (yyyy-MM-dd). */
export const weekSaturday = day => new Date(isoDay(day) - ((new Date(isoDay(day)).getUTCDay() + 1) % 7) * DAY).toISOString().slice(0, 10);

/** WeekRange's label for the 7 days from `first` (yyyy-MM-dd): "Oct 31, 2026 - Nov 6, 2026". */
export function weekLabel(first) {
  const day = ms => new Date(ms).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
  return `${day(isoDay(first))} - ${day(isoDay(first) + 6 * DAY)}`;
}

/**
 * The form's three accomplishment lines for a week ending `asOf`: a line's weight is its items' (the bar chart in force
 * that day, by amount; without a chart the Scope of Works'). With a chart the target is its plan and is not typed;
 * `targetPct` is null when it is typed.
 */
export function weeklyLines(project, docs, asOf) {
  const plan = planOn(project, docs, asOf), total = plan ? chartTotal(plan.version) : 0;
  const items = total > 0 ? plan.version.items.map(i => ({ name: i.name, weightPct: (Number(i.amount) || 0) / total * 100 }))
    : String(project.scopeOfWork ?? '').trim() ? scopeItems(project.scopeOfWork) : [];
  const lines = accomplishmentLines(items.map(i => ({ itemDescription: i.name, weightPct: i.weightPct, targetPct: total > 0 ? plan.itemTargets[i.name] ?? 0 : 0, actualPct: 0 })));
  return ACCOMPLISHMENT_LINES.map((name, k) => ({ name, weightPct: lines[k]?.weightPct ?? 0, targetPct: total > 0 ? Math.round((lines[k]?.targetPct ?? 0) * 100) / 100 : null }));
}

/** BillingAbstract.carriedActuals: a new report's Actual % boxes start from the latest report's, so a week without a new abstract keeps the last actual. */
export const carriedActuals = weekly => { const last = latestWeekly(weekly); return last ? accomplishmentOf(last.accomplishmentItemsJson).map(a => ({ actual: a.actualPct.toFixed(2) })) : []; };

/**
 * AbstractExcel.actuals: the abstract's parts ([{ name, amount, actualPct }]) as the three lines' Actual % boxes;
 * null for a line the abstract has no part for. Parts of one line are averaged by their contract amounts.
 */
export const abstractActuals = parts => (parts.length ? accomplishmentLines(parts.map(p => ({ itemDescription: p.name, weightPct: p.amount, targetPct: 0, actualPct: p.actualPct })))
  .map(l => (l.weightPct > 0 ? l.actualPct.toFixed(2) : null)) : ACCOMPLISHMENT_LINES.map(() => null));

/* ---------- The uploaded abstracts: project_abstracts records (ProjectAbstract, util/ProjectAbstracts.kt) ---------- */

/** ProjectAbstracts.rows: an abstract's rowsJson as its rows, every pay item as apa-excel.js reads them; [] when it cannot be read. */
export const abstractRows = rowsJson => jsonArray(rowsJson).filter(r => r && typeof r === 'object');

/** AbstractExcel.parts: each part row as { name, amount, actualPct }, from its own subtotal: the last one before the next part. */
export function abstractParts(rows) {
  const parts = [];
  for (const r of rows) {
    if (r.kind === 'part') parts.push({ name: r.name, amount: 0, actualPct: 0 });
    else if (r.kind === 'subtotal' && parts.length) Object.assign(parts.at(-1), { amount: r.amount ?? 0, actualPct: r.toDate?.[2] ?? 0 });
  }
  return parts;
}

/** ProjectAbstracts.latest: the abstract of the latest "As of" date, the last saved when two devices filed the same date; undefined with none. */
export const latestAbstract = abstracts => abstracts.reduce((best, a) =>
  (best == null || a.asOf > best.asOf || (a.asOf === best.asOf && (a.updatedAt || 0) > (best.updatedAt || 0)) ? a : best), undefined);

/** ProjectAbstracts.inWeek: the latest abstract dated on one of the report's 7 days; null when the week has none. */
export function abstractInWeek(abstracts, reportingWeek) {
  const days = weekDays(reportingWeek);
  return latestAbstract(abstracts.filter(a => days.includes(a.asOf))) ?? null;
}

/** An abstract's three Actual % as the weekly form shows them (abstractActuals of its parts); 0.00 for a line it has no part for. */
export const abstractLineActuals = abstract => abstractActuals(abstractParts(abstractRows(abstract.rowsJson))).map(v => Math.min(100, Number(v ?? 0)).toFixed(2)); // an overrun part reads 100: the weekly form takes 0 to 100

/** Each accomplishment line's sum, at 2 decimals, of `of(row)` over the own subtotal rows (the last before the next part) of the abstract's parts of that line. */
function abstractLineSums(abstract, of) {
  const parts = [];
  for (const r of abstractRows(abstract.rowsJson)) {
    if (r.kind === 'part') parts.push([accomplishmentLine(r.name), 0]);
    else if (r.kind === 'subtotal' && parts.length) parts.at(-1)[1] = Number(of(r)) || 0;
  }
  return ACCOMPLISHMENT_LINES.map(line => Math.round(parts.filter(p => p[0] === line).reduce((t, p) => t + p[1], 0) * 100) / 100);
}

/** ProjectAbstracts.lineAmounts: the contract amount of each accomplishment line as the abstract has it (its parts' amounts). */
export const abstractLineAmounts = abstract => abstractLineSums(abstract, r => r.amount);

/** ProjectAbstracts.periodAmounts: each accomplishment line's THIS PERIOD amount on the abstract; 0 where the sheet has none. */
export const abstractPeriodAmounts = abstract => abstractLineSums(abstract, r => r.period?.[1]);

/**
 * ProjectAbstracts.standing: the tab's Target / Actual / +/- table for an abstract, as four rows { name, target,
 * actual, diff }: the three accomplishment lines, then "Overall". A line's target is the weekly form's (the approved
 * bar chart's plan for that line on the "As of" date; null without a chart), the overall the chart's plan that day,
 * else the project's stored target. The actuals are the abstract's. `diff` is the actual less the target, each at 2
 * decimals; null without a target.
 */
export function abstractStanding(project, docs, abstract) {
  const round = v => Math.round(v * 100) / 100;
  const targets = weeklyLines(project, docs, abstract.asOf), actuals = abstractLineActuals(abstract);
  const row = (name, target, actual) => ({ name, target, actual, diff: target == null ? null : round(round(actual) - round(target)) });
  return [...ACCOMPLISHMENT_LINES.map((name, k) => row(name, targets[k].targetPct, Number(actuals[k]))),
    row('Overall', plannedTarget(project, docs, abstract.asOf) ?? (Number(project.targetAccomplishment) || 0), Number(abstract.overallPct) || 0)];
}

/** A standing row's +/- as shown: "+4.66%", "-7.58%", "0.00%"; "-" without a target. */
export const standingDiffText = diff => (diff == null ? '-' : `${diff > 0 ? '+' : ''}${diff.toFixed(2)}%`);

/**
 * ProjectAbstracts.fields: a read workbook (apa-excel.js) as the six columns of its record. The overall is the grand
 * total's; without one the parts' by their amounts, else 0. `equivUnits` is the N of the sheet's "EQUIVALENT TO N
 * UNITS" columns, 0 without them (a record from before the column reads as 0 too).
 */
export function abstractFields(found, fileName, uploadedBy) {
  const total = found.parts.reduce((t, p) => t + p.amount, 0);
  const overallPct = found.overallPct ?? (total > 0 ? Math.round(found.parts.reduce((t, p) => t + p.actualPct * p.amount, 0) / total * 100) / 100 : 0);
  return { asOf: found.asOf, overallPct, rowsJson: JSON.stringify(found.rows), fileName: fileName || '', uploadedBy: uploadedBy || '', equivUnits: found.equivUnits || 0 };
}

/** A new project_abstracts record: the ProjectAbstract row, every column (all NOT NULL on the phone). */
export const newAbstractRecord = (ids, f) => newRecord(ids, 'project_abstracts', { asOf: f.asOf, overallPct: f.overallPct, rowsJson: f.rowsJson, fileName: f.fileName, uploadedBy: f.uploadedBy, equivUnits: f.equivUnits });

/**
 * ProjectAbstracts.projectActualAfter: the project's actual once its abstracts are `abstracts`: the latest one's
 * overall; without any the latest weekly report's actual; without that it stays `current`.
 */
export const projectActualAfter = (abstracts, weekly, current) => latestAbstract(abstracts)?.overallPct ?? latestWeekly(weekly)?.actualAccomplishmentPct ?? current;

/**
 * Why a picked file cannot be filed as the project's abstract, '' when it can. `found` is what readAbstractWorkbook
 * gave: undefined when the file is not a workbook, null when it holds no abstract.
 */
export const abstractProblem = found => (found === undefined ? 'That file could not be read. Pick the abstract saved as an Excel workbook (.xlsx).'
  : !found ? 'No Abstract of Physical Accomplishment was found in that file.'
  : found.billing ? 'That is the billing abstract. Pick the abstract of the actual accomplishment.'
  : !found.asOf ? 'That abstract has no "As of" date, so it cannot be filed. Check the line under its title.' : '');

const overallText = a => `${(Number(a.overallPct) || 0).toFixed(2)}%`;
const abstractLabel = a => `as of ${displayDate(a.asOf)}: overall ${overallText(a)}`;
/** The tab's line after an upload is saved. */
export const abstractSavedNote = (f, replaced) => `${replaced ? 'Replaced' : 'Saved'} the abstract ${abstractLabel(f)}.`;
/** The weekly form's line when its week has an abstract. */
export const abstractWeekNote = abstract => `Actual % is from the abstract as of ${displayDate(abstract.asOf)} (Actual Accomplishment tab).`;
/** ProjectViewModel.saveAbstract's audit entry, word for word; `old` is the abstract of the same date it replaced. */
export const abstractAudit = (f, old = null) => ({
  actionType: old ? 'Updated abstract' : f.fileName ? 'Abstract Upload' : 'New abstract', details: `Abstract ${abstractLabel(f)}`, oldValue: old && overallText(old) !== overallText(f) ? overallText(old) : '', newValue: overallText(f), // the phone's log drops an entry whose old and new are equal
});
/* ---------- The next abstract, made in the app from the one before it (ProjectAbstracts.next*, the sheet's own formulas) ---------- */

const round2 = v => Math.round(v * 100) / 100, round4 = v => Math.round(v * 10000) / 10000;

/** ProjectAbstracts.isPayLine: a row the engineer types a quantity for: a pay item, or a part that is its own pay item (Land Acquisition), with a quantity and an amount. */
export const isPayLine = r => (r.kind === 'item' || r.kind === 'part') && r.qty > 0 && r.amount != null;

/**
 * ProjectAbstracts.cover: for each subtotal and the grand total (by row index), the indexes of the pay lines it adds
 * up. A subtotal right after another one is its part's (every line since the part row); any other, the lines since
 * the subtotal before it or the part row; the grand total, every line.
 */
export function abstractCover(rows) {
  const cover = new Map();
  let part = 0, group = 0;
  rows.forEach((r, i) => {
    if (r.kind === 'part') part = group = i;
    if (r.kind !== 'subtotal' && r.kind !== 'total') return;
    const from = r.kind === 'total' ? 0 : rows[i - 1]?.kind === 'subtotal' ? part : group;
    cover.set(i, rows.slice(from, i).flatMap((x, k) => (isPayLine(x) ? [from + k] : [])));
    group = i + 1;
  });
  return cover;
}

/** ProjectAbstracts.nextProblem: why the next abstract cannot be made from `abstract`, '' when it can: its subtotals must add up from its pay items. */
export function nextAbstractProblem(abstract) {
  const rows = abstractRows(abstract.rowsJson), cover = abstractCover(rows), from = `the abstract as of ${displayDate(abstract.asOf)}`;
  const off = [...cover].find(([i, lines]) => Math.abs(lines.reduce((t, k) => t + rows[k].amount, 0) - (rows[i].amount ?? 0)) > 1);
  return !rows.some(r => r.kind === 'total') || !rows.some(isPayLine) ? `There are no pay items with a grand total in ${from}, so the next one cannot be made from it. Upload the next abstract instead.`
    : off ? `"${rows[off[0]].name}" does not add up from its items in ${from}, so the next one cannot be made from it. Upload the next abstract instead.` : '';
}

/**
 * ProjectAbstracts.nextSource: what an abstract of `asOf` (yyyy-MM-dd) made in the app continues, as { source, same,
 * error }. `source` is the latest abstract dated before it: its TO DATE is the new PREVIOUS. `same` is the abstract
 * already filed for that date (saving replaces it), null with none. `error` says why the date cannot be used.
 */
export function nextAbstractSource(abstracts, asOf) {
  const latest = latestAbstract(abstracts), source = latestAbstract(abstracts.filter(a => a.asOf < asOf));
  const error = !asOf ? 'Pick the "As of" date.' : asOf < latest.asOf ? `The date must not be before the latest abstract's, ${displayDate(latest.asOf)}.`
    : !source ? `The date must be after ${displayDate(latest.asOf)}, the first abstract's.` : nextAbstractProblem(source);
  return { source, same: latestAbstract(abstracts.filter(a => a.asOf === asOf)) ?? null, error };
}

/** ProjectAbstracts.nextTyped: this period's quantities of `same` (the abstract of the date being made again) by row index; {} when its rows are not the source's. */
export function nextAbstractTyped(sourceRows, same) {
  const rows = same ? abstractRows(same.rowsJson) : [];
  if (rows.length !== sourceRows.length || rows.some((r, i) => r.kind !== sourceRows[i].kind || r.no !== sourceRows[i].no || r.name !== sourceRows[i].name)) return {};
  return Object.fromEntries(rows.flatMap((r, i) => (isPayLine(r) && r.period?.[0] != null ? [[i, r.period[0]]] : [])));
}

/**
 * ProjectAbstracts.nextRows: the rows of the next abstract. `rows` are the source's, `typed` this period's quantity
 * by row index (none typed is no work this period). As on the sheet: PREVIOUS is the source's TO DATE; a pay line's
 * THIS PERIOD amount is the quantity at its unit cost and its % that amount over the line's amount; TO DATE is
 * previous plus this period; BALANCE the rest; the equivalent units the quantity's share of `equivUnits`. Subtotals
 * and the grand total add up their pay lines' THIS PERIOD amounts. Cells the sheet leaves blank stay blank.
 */
export function nextAbstractRows(rows, typed, equivUnits = 0) {
  const out = rows.map(r => ({ ...r }));
  rows.forEach((r, i) => {
    if (!isPayLine(r)) return;
    const q = typed[i] ?? null, n = Number(q) || 0, prev = [0, 1, 2].map(k => Number(r.toDate?.[k]) || 0);
    const amount = round2(n * (r.unitCost ?? r.amount / r.qty)), share = r.amount ? round2(amount / r.amount * 100) : 0;
    const toDate = [round4(prev[0] + n), round2(prev[1] + amount), round2(prev[2] + share)], done = round2(toDate[0] / r.qty * equivUnits);
    Object.assign(out[i], { prev, period: [q, amount, share], toDate, balance: [round4(r.qty - toDate[0]), round2(r.amount - toDate[1])],
      equiv: equivUnits > 0 ? [round2(prev[0] / r.qty * equivUnits), done, round2(equivUnits - done)] : [null, null, null] });
  });
  for (const [i, lines] of abstractCover(rows)) {
    const r = rows[i], amount = round2(lines.reduce((t, k) => t + out[k].period[1], 0)), share = r.amount ? round2(amount / r.amount * 100) : 0;
    const before = [Number(r.toDate?.[1]) || 0, Number(r.toDate?.[2]) || 0], done = round2(before[0] + amount);
    Object.assign(out[i], { prev: [null, before[0], r.prev?.[2] == null ? null : before[1]], period: [null, amount, r.period?.[2] == null ? null : share],
      toDate: [null, done, round2(before[1] + share)], balance: [null, r.balance?.[1] == null ? null : round2((r.amount ?? 0) - done)], equiv: [null, null, null] });
  }
  return out;
}

/** A quantity as the abstract shows it: up to 4 decimals, with commas. */
export const quantityText = v => Number(v).toLocaleString('en-US', { maximumFractionDigits: 4 });

/** ProjectAbstracts.nextErrors: what stops the typed quantities from being saved, one line per pay line; [] when they can. */
export function nextAbstractErrors(rows, typed) {
  const next = nextAbstractRows(rows, typed);
  return rows.flatMap((r, i) => {
    const label = `${r.no} ${r.name}`.trim(), done = next[i].toDate?.[0];
    return !isPayLine(r) ? [] : !Number.isFinite(Number(typed[i] ?? 0)) ? [`${label}: this period must be a number.`]
      : done < 0 ? [`${label}: to date would go below zero.`] : done > r.qty ? [`${label}: to date ${quantityText(done)} would pass the quantity ${quantityText(r.qty)}.`] : [];
  });
}

/** ProjectAbstracts.nextFields: the record's columns for the abstract of `asOf` made from `source` with `rows` (nextAbstractRows); it has no file. */
export const nextAbstractFields = (source, rows, asOf, uploadedBy) => ({
  asOf, overallPct: rows.find(r => r.kind === 'total')?.toDate[2] ?? 0, rowsJson: JSON.stringify(rows), fileName: '', uploadedBy: uploadedBy || '', equivUnits: Number(source.equivUnits) || 0,
});

/** The New abstract form's line: the overall its quantities give and what it continues, word for word on the phone. */
export const nextAbstractNote = (source, same, rows) => `Overall to date ${overallText({ overallPct: rows.find(r => r.kind === 'total')?.toDate[2] })}, from ${overallText(source)} as of ${displayDate(source.asOf)}.${same ? ' Saving replaces the abstract already filed for this date.' : ''}`;

/** ProjectViewModel.deleteAbstract's audit entry, word for word. */
export const deletedAbstractAudit = a => ({ actionType: 'Deleted abstract', details: `Abstract ${abstractLabel(a)}`, oldValue: overallText(a), newValue: '' });

/** FormValidation.percentError's reading: blank is 0, else 0 to 100; NaN for anything else. */
const percent = text => {
  const t = String(text ?? '').trim(), v = !t ? 0 : /^(\d+(\.\d*)?|\.\d+)$/.test(t) ? Number(t) : NaN;
  return v >= 0 && v <= 100 ? v : NaN;
};

/** weeklyErrors: every line's target and actual is 0 to 100; the week needs its first day. */
export function weeklyErrors(f) {
  return [isoDay(f.first) == null && 'Pick the first day of the week.', ...f.lines.flatMap(l => [
    Number.isNaN(percent(l.target)) && `${l.name}: target must be 0 to 100%.`, Number.isNaN(percent(l.actual)) && `${l.name}: actual must be 0 to 100%.`,
  ])].filter(Boolean);
}

/** The overall target and actual of the form's lines: each line's percent by its weight. */
export const weeklyTotals = lines => Object.fromEntries(['target', 'actual'].map(k => [k, lines.reduce((t, l) => t + (percent(l[k]) || 0) * l.weightPct / 100, 0)]));

/**
 * The columns the weekly report form `f` writes ({ first, lines: [{ name, weightPct, target, actual }], manpower,
 * equipment, activities, issues }). Rows without a name or description are left out; a row's remarks the form does not
 * show are kept.
 */
function weeklyFields(f, project) {
  const reportingWeek = weekLabel(f.first), last = isoDay(f.first) + 6 * DAY;
  const { target, actual } = weeklyTotals(f.lines);
  // ProjectSchedule.elapsedAndRemaining as of the week's last day: 0 and 0 without a start and completion date.
  const start = isoDay(project.dateStarted), end = isoDay(effectiveCompletion(project)), dated = start != null && end != null;
  const rows = (list, key, row) => JSON.stringify(list.filter(r => (r[key] || '').trim()).map(row));
  const count = r => Math.max(0, int(r.count, 0));
  return {
    reportingWeek, daysElapsed: dated ? Math.max(0, (last - start) / DAY + 1) : 0, remainingDays: dated ? Math.max(0, (end - last) / DAY) : 0,
    targetAccomplishmentPct: target, actualAccomplishmentPct: actual,
    manpowerJson: rows(f.manpower, 'designation', r => ({ designation: r.designation.trim(), count: count(r), remarks: r.remarks || '' })),
    equipmentJson: rows(f.equipment, 'description', r => ({ description: r.description.trim(), count: count(r), status: r.status || 'Operational', remarks: r.remarks || '' })),
    // ActivityDays.label: one day, or a range such as "Monday - Wednesday".
    activitiesJson: rows(f.activities, 'description', r => ({ day: r.to && r.to !== r.from ? `${r.from} - ${r.to}` : r.from, description: r.description.trim(), remarks: (r.remarks || '').trim() })),
    issuesJson: rows(f.issues, 'description', r => ({ description: r.description.trim(), actionTaken: (r.actionTaken || '').trim(), remarks: r.remarks || '', actionBy: r.actionBy || '' })),
    accomplishmentItemsJson: JSON.stringify(f.lines.map(l => ({ itemDescription: l.name, targetPct: percent(l.target), actualPct: percent(l.actual), weightPct: l.weightPct }))),
  };
}

/**
 * WeekRange.isLatest: the project's target and actual follow a saved report only when none of the `weekly` reports is
 * for a later week, so a late entry or a fix to an older week does not set the project back.
 */
function projectFollows(weekly, uuid, reportingWeek, target, actual, abstracts = []) {
  const at = label => weekStart(label) ?? -Infinity;
  if (weekly.some(r => r.uuid !== uuid && at(r.reportingWeek) > at(reportingWeek))) return null;
  // With an uploaded abstract the project's actual is the abstract's (projectActualAfter): only the target follows the report.
  return abstracts.length ? { targetAccomplishment: target } : { targetAccomplishment: target, actualAccomplishment: actual };
}

/**
 * A new weekly report from the form: `build` makes the weekly_reports record as RecordSync uploads one,
 * `projectFields` are the project's target and actual saved with it (projectFollows; the target alone while the
 * project has `abstracts`), `audit` its entry. Photos and weather start empty: the report's Add photos and Edit weather fill them.
 */
export function newWeeklyReport(f, project, user, weekly = [], abstracts = []) {
  const data = weeklyFields(f, project), { reportingWeek, targetAccomplishmentPct: target, actualAccomplishmentPct: actual } = data;
  return {
    build: ({ uuid, projectUuid, assignedUids, uid, now }) => ({
      uuid, type: 'weekly_reports', projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false,
      data: JSON.stringify({ ...data, documentsIssuedReceivedJson: '[]', attachedPhotoUrlsJson: '[]', photoDetailsJson: '{}', submittedByStaff: user,
        createdAtTimestamp: now, _weather: [] }),
    }),
    projectFields: projectFollows(weekly, null, reportingWeek, target, actual, abstracts),
    audit: { actionType: 'Report Creation', details: `Submitted Weekly Report for ${reportingWeek}`, newValue: `Week: ${reportingWeek}, Target: ${target}%, Actual: ${actual}%` },
  };
}

/** A saved report as the form's starting values: its first day, each line's target and actual, and its rows. */
export function weeklyStart(report) {
  // ActivityDays.split: "Monday - Wednesday" is a range, anything else one day.
  const span = day => { const parts = day.split('-').map(x => x.trim()); return parts.length === 2 ? { from: parts[0], to: parts[1] } : { from: day.trim(), to: '' }; };
  return {
    first: weekDays(report.reportingWeek)[0] || '',
    lines: accomplishmentOf(report.accomplishmentItemsJson).map(a => ({ target: a.targetPct.toFixed(2), actual: a.actualPct.toFixed(2) })),
    manpower: manpowerOf(report.manpowerJson), equipment: equipmentOf(report.equipmentJson),
    activities: activitiesOf(report.activitiesJson).map(a => ({ ...span(a.day), description: a.description, remarks: a.remarks })),
    issues: issuesOf(report.issuesJson),
  };
}

/**
 * The form's edit of a saved report (MainActivity's reportToEdit.copy, updateWeeklyReportWithWeather): the columns it
 * writes, the project's target and actual (projectFollows), and the phone's audit entry. The week's label is kept unless its first day
 * was changed (an older "Week 42: ..." label stays); then weather logged for days outside the new week is dropped.
 * Photos and the rest of the weather are left alone.
 */
export function editedWeeklyReport(report, f, project, weekly = [], abstracts = []) {
  const fields = weeklyFields(f, project), moved = weekDays(report.reportingWeek)[0] !== f.first;
  const days = weekDays(fields.reportingWeek);
  const changes = { ...fields, reportingWeek: moved ? fields.reportingWeek : report.reportingWeek,
    ...(moved && Array.isArray(report._weather) ? { _weather: report._weather.filter(w => days.includes((w.date || '').trim())) } : {}) };
  return {
    changes, projectFields: projectFollows(weekly, report.uuid, changes.reportingWeek, fields.targetAccomplishmentPct, fields.actualAccomplishmentPct, abstracts),
    audit: { actionType: 'Edited Weekly Report', details: `Modified report for ${changes.reportingWeek}` },
  };
}

/** ProjectViewModel.deleteWeeklyReport's audit entry. */
export const deletedWeeklyAudit = report => ({ actionType: 'Deleted Weekly Report', details: `Deleted report for ${report.reportingWeek}` });

/** Permission.DELETE_REPORT: the Super Admin and Engineer Admins; a Field Engineer can add and edit reports, not delete them. */
export const canDeleteReport = claims => ['SUPER_ADMIN', 'ENGINEER_ADMIN'].includes(claims.role);

/** Permission.DELETE_ISSUE: the same roles. A form rule only, like canDeleteReport. */
export const canDeleteIssue = canDeleteReport;

/** ProjectViewModel.deleteProjectIssue's audit entry; the phone's "Issue ID" is its own row number, here it is the record's id. */
export const deletedIssueAudit = issue => ({
  actionType: 'Issue Deletion', details: `Deleted issue: ${String(issue.description ?? '').slice(0, 30)}...`, oldValue: `Issue ID: ${issue.uuid}`, newValue: '',
});

/* ---------- Weather edit (AddWeeklyReportDialog's 24-hour weather, saved with its weekly report) ---------- */

export const WEATHER_KEYS = ['FAIR', 'CLOUDY', 'RAIN_SHOWERS', 'RAINY', 'STORMY'];
/** AddWeeklyReportDialog's one-tap day weather (Reports.kt WEATHER_PRESETS): half-and-half is 12 AM-11 AM, then 12 PM-11 PM. */
const halves = (am, pm) => Array.from({ length: 24 }, (_, h) => (h < 12 ? am : pm));
export const WEATHER_PRESETS = [
  ['All Fair', halves('FAIR', 'FAIR')], ['All Cloudy', halves('CLOUDY', 'CLOUDY')],
  ['Cloudy AM / Showers PM', halves('CLOUDY', 'RAIN_SHOWERS')], ['All Rainy', halves('RAINY', 'RAINY')],
  ['Rainy AM / Stormy PM', halves('RAINY', 'STORMY')], ['All Stormy', halves('STORMY', 'STORMY')],
];
const weatherKey = v => { const k = String(v ?? '').trim().toUpperCase().replace(/ /g, '_'); return WEATHER_KEYS.includes(k) ? k : 'FAIR'; };

/** The 7 days of a weekly report for the weather form: its stored hours, or Fair all day for a day with no log. */
export function weatherForm(report) {
  const rows = Array.isArray(report?._weather) ? report._weather : [];
  return weekDays(report?.reportingWeek || '').map(date => {
    const row = rows.find(w => (w.date || '').trim() === date);
    const parts = (row?.hourlyConditionsCsv || '').split(',');
    return { date, logged: Boolean(row), hours: Array.from({ length: 24 }, (_, h) => weatherKey(row ? parts[h] : 'FAIR')) };
  });
}

/**
 * The weekly report's new `_weather` (RecordSync.buildDocument's rows, by date): days changed in the form are rewritten
 * as the app saves them, every other stored row stays exactly as it was. Null when nothing changed.
 */
export function weatherChanges(report, days, changed) {
  const rows = Array.isArray(report?._weather) ? report._weather : [];
  if (!changed.size) return null;
  const byDate = new Map(rows.map(w => [(w.date || '').trim(), w]));
  for (const d of days.filter(d => changed.has(d.date))) {
    const dayOfWeek = new Date(`${d.date}T00:00:00Z`).toLocaleDateString('en-US', { weekday: 'long', timeZone: 'UTC' });
    byDate.set(d.date, { ...byDate.get(d.date), date: d.date, dayOfWeek, hourlyConditionsCsv: d.hours.join(',') });
  }
  return { _weather: [...byDate.values()].sort((a, b) => String(a.date).localeCompare(String(b.date))) };
}

/* ---------- New project and Edit project (CreateProjectDialog, EditProjectDialog, ProjectViewModel) ---------- */

/** Permission.CREATE_PROJECT: the Super Admin and Engineer Admins. A form rule only: firestore.rules lets any writer create one. */
export const canCreateProject = claims => isWriter(claims) && ['SUPER_ADMIN', 'ENGINEER_ADMIN'].includes(claims.role);

/** CreateProjectDialog's choices; the type also takes any other text ("Custom..."). */
export const PROJECT_TYPES = ['Housing Project', 'Community Facility'];
export const LAND_UNITS = ['Hectares', 'Square Meters'];

/** normalizeProjectKey: how a name or a location is compared for the duplicate check. */
const projectKey = text => String(text ?? '').trim().toLowerCase().replace(/\s+/g, ' ');

/** The form's scope of works rows with anything typed. */
const scopeRows = f => f.scope.filter(r => r.name.trim() || r.amount.trim());

/** The form's contract duration in days: blank (or not yet a whole number) is 365, as the phone saves it. */
const projectDays = f => (/^\d{1,5}$/.test(f.days.trim()) ? Number(f.days) : 365);

/** The contract's last day (ProjectSchedule.lastDay); a duration of 0 ends on the start date. Null without a start date. */
export const projectEnd = f => lastDay(f.dateStarted, Math.max(projectDays(f), 1));

/** What keeps the new project form from saving; `projects` are the ones this account can see, `today` is yyyy-MM-dd. */
export function projectErrors(f, projects, today) {
  const start = isoDay(f.dateStarted), done = isoDay(f.completedOn);
  return [
    !f.name.trim() && 'Type the project name.',
    !f.contractor.trim() && 'Type the contractor\'s name.',
    // A saved item with no amount (an older project's) may stay as it is.
    scopeRows(f).some(r => !r.name.trim() || !(parseAmount(r.amount) > 0 || (!r.amount.trim() && r.weightPct)))
      && 'Each scope of works item needs a name and an amount above 0.',
    f.days.trim() && !/^\d{1,5}$/.test(f.days.trim()) && 'Contract duration: whole days only.',
    start == null && 'Pick the date started.',
    ...(f.completed ? [
      done == null ? 'Pick the actual completion date.' : done > isoDay(today) ? 'The actual completion date can\'t be in the future.'
        : start != null && done < start && 'The actual completion date is before the date started.',
      !f.final.trim() ? 'Type the final accomplishment.' : Number.isNaN(percent(f.final)) && 'Final accomplishment: enter 0 to 100.',
    ] : []),
    projects.some(p => projectKey(p.name) === projectKey(f.name) && projectKey(p.location) === projectKey(f.location))
      && 'A project with this name and location already exists.',
  ].filter(Boolean);
}

/** The Project fields a phone uploads that the new project form leaves blank. */
const BLANK_PROJECT_FIELDS = ['projectedBeneficiaries', 'boardResolution', 'noticeOfAward', 'noticeToProceed',
  ...['prepared', 'checked', 'noted', 'weeklyChecked', 'weeklyNoted'].flatMap(k => [`${k}ByName`, `${k}ByPosition`, `${k}ByOffice`]),
  'latestUpdatePhotoUrl', 'barChartJson'];

/**
 * The `projects` document for the form, as a phone uploads a new project (FirestoreSyncManager): the cost is the scope
 * of works' total, each item's weight its share of it (with no amounts at all, saved items keep the weights they
 * had: withAutoWeights), and the creator the only assigned account. With "already
 * completed" it is saved at the final accomplishment on the actual completion date (Project.markedCompleted).
 * `audit` is its "Project Creation" entry.
 */
export function newProject(f, { uuid, uid, now, user }) {
  const rows = scopeRows(f).map(r => ({ name: r.name.trim(), amount: parseAmount(r.amount) ?? 0, weightPct: Number(r.weightPct) || 0 }));
  const cost = rows.reduce((t, r) => t + r.amount, 0), days = projectDays(f), end = projectEnd(f);
  const final = f.completed ? Number(f.final) : 0, name = f.name.trim(), location = f.location.trim();
  return {
    project: {
      projectUuid: uuid, name, location, implementingOffice: f.implementingOffice.trim(), contractor: f.contractor.trim(),
      scopeOfWork: JSON.stringify(rows.map(r => ({ ...r, weightPct: cost > 0 ? r.amount / cost * 100 : r.weightPct }))),
      projectType: f.projectType.trim() || PROJECT_TYPES[0],
      landArea: f.landArea.trim() ? `${f.landArea.trim()} ${f.landUnit}` : '', numberOfUnits: f.numberOfUnits.trim(),
      ...Object.fromEntries(BLANK_PROJECT_FIELDS.map(k => [k, ''])),
      contractCostOriginal: cost, contractCostRevised: cost, contractDurationDays: days,
      dateStarted: f.dateStarted, completionDateOriginal: end, completionDateRevised: f.completed ? f.completedOn : end,
      status: 'On-going', targetAccomplishment: final, actualAccomplishment: final, assignedStaff: f.assignedStaff.trim() || user,
      revision: 1, createdAt: now, updatedAt: now, createdByUid: uid, updatedByUid: uid, isDeleted: false, assignedUids: [uid],
    },
    audit: {
      actionType: 'Project Creation', details: `Created new project: ${name}`,
      newValue: `Title: ${name}, Location: ${location}, Cost: ₱${cost}, Duration: ${days} days${f.completed ? `, Already completed on ${f.completedOn} at ${final}%` : ''}`,
    },
  };
}

/** The Project Profile fields only the edit form has. */
const PROFILE_FIELDS = ['projectedBeneficiaries', 'boardResolution', 'noticeOfAward', 'noticeToProceed'];

/**
 * What the edit form starts with for a saved project (EditProjectDialog): the land area split into its number and
 * unit, a type outside the list as the custom one, and "Mark as completed" off with its date at the completion date
 * when that has passed, else today (defaultCompletedOn).
 */
export function projectStart(project, today) {
  const text = key => String(project[key] ?? ''), land = text('landArea').trim(), cut = land.indexOf(' ');
  const due = effectiveCompletion(project);
  return {
    ...Object.fromEntries(['name', 'location', 'implementingOffice', 'contractor', 'projectType', 'numberOfUnits', 'dateStarted', ...PROFILE_FIELDS].map(k => [k, text(k)])),
    landArea: cut < 0 ? land : land.slice(0, cut), landUnit: cut >= 0 && /square/i.test(land.slice(cut)) ? LAND_UNITS[1] : LAND_UNITS[0],
    days: text('contractDurationDays'), assignedStaff: text('assignedStaff'),
    scope: (text('scopeOfWork').trim() ? scopeItems(project.scopeOfWork) : []).map(i => ({ name: i.name, amount: i.amount > 0 ? String(i.amount) : '', weightPct: String(i.weightPct) })),
    completed: false, completedOn: isoDay(due) != null && due <= today ? due.trim() : today, final: '100',
  };
}

/**
 * The project fields an edit changes (the edit dialog's onEdit and Project.markedCompleted) and its "Project Edit"
 * audit entry. Only what the form changed from its starting values is returned, so a field it did not touch (an
 * older land area or scope the form would spell differently) is not rewritten; the revised cost, the revised
 * completion date (unless marked completed) and the assignment are never the form's to change. `fields` is empty
 * when nothing changed.
 */
export function editedProject(project, f) {
  const fieldsOf = g => {
    const made = newProject(g, { uuid: project.projectUuid }).project;
    return {
      ...Object.fromEntries(['name', 'location', 'implementingOffice', 'contractor', 'scopeOfWork', 'projectType', 'landArea', 'numberOfUnits', 'contractCostOriginal',
        'contractDurationDays', 'dateStarted', 'completionDateOriginal'].map(k => [k, made[k]])),
      ...Object.fromEntries(PROFILE_FIELDS.map(k => [k, g[k].trim()])),
      ...(g.completed ? { completionDateRevised: g.completedOn, targetAccomplishment: made.actualAccomplishment, actualAccomplishment: made.actualAccomplishment } : {}),
    };
  };
  const was = fieldsOf(projectStart(project, ''));
  const fields = Object.fromEntries(Object.entries(fieldsOf(f)).filter(([k, v]) => was[k] !== v));
  return { fields, audit: projectEditAudit(project, fields) };
}

/** updateProjectMaster's audit entry for `fields` saved on `project`. */
export function projectEditAudit(project, fields) {
  const after = { ...project, ...fields };
  return {
    actionType: 'Project Edit', details: `Updated project master: ${after.name}`,
    oldValue: `Status: ${displayStatus(project)}`, newValue: `Status: ${displayStatus(after)}, Revised Cost: ₱${Number(after.contractCostRevised) || 0}`,
  };
}

/** SignatoriesDialog's suggestions for each signer's position and office; any other text can be typed. */
export const SIGNATORY_POSITIONS = {
  prepared: ['Senior Engineer A', 'Senior Architect A', 'Supervising Engineer A', 'Principal Engineer C', 'Principal Engineer A', 'Principal Architect A'],
  weeklyChecked: ['Principal Engineer A', 'Principal Architect A'], weeklyNoted: ['District Manager', 'Officer-in-Charge'],
  checked: ['District Manager', 'Officer-in-Charge'], noted: ['Regional Manager', 'Officer-in-Charge'],
};
export const SIGNATORY_OFFICES = ['Bulacan District Office', 'Region 3 Office'];

/**
 * saveSignatories: the project fields that differ from `s`, a weekly or a monthly report's signatories as the form has
 * them (trimmed). Prepared by is shared; the weekly report has its own checker and noter. Empty when nothing differs.
 */
export function signatoryFields(project, s, weekly) {
  const [checked, noted] = weekly ? ['weeklyChecked', 'weeklyNoted'] : ['checked', 'noted'];
  const all = [['prepared', 'prepared'], [checked, 'checked'], [noted, 'noted']].flatMap(([field, key]) =>
    ['Name', 'Position', 'Office'].map(part => [`${field}By${part}`, String(s[`${key}${part}`] ?? '').trim()]));
  return Object.fromEntries(all.filter(([k, v]) => (project[k] ?? '') !== v));
}

/** The month's standing: its last weekly report's target and actual % (`weeks` = monthReports), 0 with none. */
const monthEnd = weeks => { const last = weeks[weeks.length - 1]; return [Number(last?.targetAccomplishmentPct) || 0, Number(last?.actualAccomplishmentPct) || 0]; };

/**
 * A new monthly_reports record as RecordSync.buildDocument uploads one (submitMonthlyReport: prepared by the signed-in
 * name, "Reviewed"), made when the month's recommendations are first saved. The form prints its days from the weather
 * logs, so the stored counts stay 0.
 */
export function newMonthlyRecord({ uuid, projectUuid, assignedUids, uid, now, preparedBy }, month, weeks, recommendations) {
  const [target, actual] = monthEnd(weeks);
  return {
    uuid, type: 'monthly_reports', projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: uid, isDeleted: false,
    data: JSON.stringify({
      reportingMonth: month, scopeWeightPct: 100, scopeTargetPct: target, scopeActualPct: actual, paymentsJson: '[]',
      unworkableDaysCount: 0, workableDaysCount: 0, cpesIssuesJson: '[]', recommendations,
      preparedByName: preparedBy, preparedByStatus: 'Reviewed', checkedByName: '', checkedByStatus: 'Pending Review',
      notedByName: '', notedByStatus: 'Pending Review', auditTrailJson: '', accomplishmentItemsJson: '[]',
    }),
  };
}

/** submitMonthlyReport's audit entry, or updateMonthlyReportFull's for an `edit`, word for word. */
export function monthlyAudit(month, weeks, edit) {
  const [target, actual] = monthEnd(weeks);
  return edit ? { actionType: 'Report Creation', details: `Modified report for ${month}`, oldValue: 'Previous report data', newValue: 'Updated report data' }
    : { actionType: 'Report Creation', details: `Submitted Monthly Report for ${month}`, oldValue: '', newValue: `Month: ${month}, Target: ${target}%, Actual: ${actual}%` };
}

/** Only the Super Admin changes who a project is assigned to (EditProjectDialog, firestore.rules). */
export const canAssign = claims => claims.role === 'SUPER_ADMIN';

/**
 * AssignStaffField's options: every account (userRow) A to Z by name, then any assigned uid that has no account,
 * shown by its uid so saving cannot drop it unnoticed.
 */
export function assignableUsers(rows, assigned) {
  const known = new Set(rows.map(u => u.uid));
  return [...rows].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
    .concat([...new Set(assigned)].filter(uid => !known.has(uid)).map(uid => ({ uid, name: uid, role: '' })));
}

/**
 * AssignmentPolicy.mergeAssignment: the project's `assignedUids` for the ticked `uids` (those already assigned keep
 * their place, new ones follow) and `assignedStaff`, their names. Empty when the list is unchanged.
 */
export function assignmentFields(project, uids, users) {
  const was = project.assignedUids || [];
  const list = [...new Set([...was.filter(uid => uids.includes(uid)), ...uids])];
  if (list.length === was.length && list.every((uid, i) => uid === was[i])) return {};
  return { assignedUids: list, assignedStaff: list.map(uid => users.find(u => u.uid === uid)?.name ?? uid).join(', ') };
}

/** DELETE_PROJECT on a project the account can edit: the phone's dashboard has no Delete on a view-only project. */
export const canDeleteProject = (project, uid, claims) => canCreateProject(claims) && canEdit(project, uid, claims);

/**
 * The tombstone a phone uploads for a deleted project (FirestoreSyncManager's DELETE): the document and its records
 * stay, marked deleted, and every phone and this website drop the project.
 */
export const deletedProject = (project, { uid, now }) => ({ projectUuid: project.projectUuid, isDeleted: true, updatedAt: now, updatedByUid: uid });

/** MANAGE_BAR_CHART (the Super Admin and Engineer Admins) on a project the account can edit, as firestore.rules allows. */
export const canEditChart = canDeleteProject;

const EMPTY_BAR = () => ({ sm: '', sc: '', em: '', ec: '' });

/**
 * BarChartDialog's drafts: the saved charts as the form edits them, each bar as its From and To month and cell, or
 * one "Original" chart of the Scope of Works items with an empty bar each. The first chart always runs from the
 * start date. Null without a start date: the chart counts its days from it.
 */
export function chartDrafts(project) {
  if (isoDay(project.dateStarted) == null) return null;
  const saved = chartVersions(project.barChartJson).map(v => ({ label: String(v.label ?? ''), effective: String(v.effective ?? ''), items: draftItems(v.items || []) }));
  const drafts = saved.length ? saved : [{ label: 'Original', effective: '', items: draftItems(scopeItems(project.scopeOfWork)) }];
  drafts[0].effective = project.dateStarted;
  return drafts;
}

/** itemDrafts: a chart's items (saved, from the scope, or read from an Excel sheet) as the form edits them, an item with no bar getting an empty one. */
export function draftItems(list) {
  const bar = ([a, b]) => { const [sm, sc] = cellOf(a), [em, ec] = cellOf(b); return { sm: String(sm), sc: String(sc), em: String(em), ec: String(ec) }; };
  return list.map(i => ({ name: i.name, amount: Number(i.amount) || 0, bars: (i.bars || []).length ? i.bars.map(bar) : [EMPTY_BAR()] }));
}

/** The "+ Revised chart" tab: the next chart starts as a copy of the last one, with no date yet. */
export const revisedDraft = drafts => ({ label: `Revised No. ${drafts.length}`, effective: '', items: drafts.at(-1).items.map(i => ({ ...i, bars: i.bars.map(b => ({ ...b })) })) });
export const emptyBar = EMPTY_BAR;

/** BarDraft.bar: the bar in contract days, or null when a box is not a month (1+) and a cell (5, 10 … 30), or it ends before it starts. */
export function draftBar(b) {
  const whole = t => (/^\d+$/.test(String(t).trim()) && Number(t) >= 1 ? Number(t) : null);
  const cell = t => { const n = whole(t); return n != null && n <= MONTH_DAYS && n % CELL_DAYS === 0 ? n : null; };
  const sm = whole(b.sm), sc = cell(b.sc), em = whole(b.em), ec = cell(b.ec);
  if ([sm, sc, em, ec].includes(null)) return null;
  const bar = [(sm - 1) * MONTH_DAYS + sc - CELL_DAYS + 1, (em - 1) * MONTH_DAYS + ec];
  return barDays(bar) > 0 ? bar : null;
}

/** VersionDraft.version: the chart as saved; a bar that does not read is left out (chartErrors names its item). */
export const draftVersion = d => ({ label: d.label.trim(), effective: d.effective, items: d.items.map(i => ({ name: i.name, amount: i.amount, bars: i.bars.map(draftBar).filter(Boolean) })) });

/** BarChart.toJson for the drafts, keys in the phone's order; no chart at all is blank, which removes it. */
export const chartJson = drafts => (drafts.length ? JSON.stringify(drafts.map(draftVersion)) : '');

/**
 * BarChartDialog's errors, which keep Save off. The original chart must total the original contract amount, a revised
 * one the original or the current (after Variation Orders); a revised chart takes effect after the start date and
 * after the chart before it. A chart with no items (no Scope of Works, and nothing imported) is refused here.
 */
export function chartErrors(drafts, project, docs) {
  const start = isoDay(project.dateStarted), original = originalCost(project), current = currentCost(project, docs);
  return drafts.flatMap((d, i) => {
    const name = d.label.trim() || `Chart ${i + 1}`, date = isoDay(d.effective), total = d.items.reduce((t, x) => t + x.amount, 0);
    const matches = chartTotalMatches(i, total, project, docs);
    const bad = d.items.filter(x => !x.bars.every(draftBar));
    return [
      !d.label.trim() && `${name}: enter a name`,
      !d.items.length && `${name}: import the Excel file, or add the Scope of Works items to the project first`,
      d.items.length && !matches && `${name}: the chart totals ${formatPhp(total)} but the contract amount is ${formatPhp(i > 0 ? current : original)}`,
      i > 0 && date == null && `${name}: pick the date it takes effect`,
      i > 0 && date != null && start != null && date < start && `${name}: takes effect before the start date`,
      i > 0 && date != null && drafts[i - 1].effective >= d.effective && `${name}: takes effect on or before the previous chart`,
      bad.length && `${name}: check the bars of ${bad.map(x => x.name).join(', ')} (month 1+, cell 5, 10 … 30)`,
    ].filter(Boolean);
  });
}

/** totalMatches: chart `i` totals the original contract amount, or for a revised one the current (after Variation Orders). */
export const chartTotalMatches = (i, total, project, docs) =>
  Math.abs(total - originalCost(project)) < 0.015 || (i > 0 && Math.abs(total - currentCost(project, docs)) < 0.015);

/** useSheet's notes on an imported sheet (chart-excel.js): what was read, then each item whose bars do not cover the days its Duration column says. */
export const importNotes = sheet => [`Read ${sheet.items.length} items from the sheet "${sheet.name}".`, ...sheet.items.flatMap(item => {
  const days = item.bars.reduce((n, b) => n + barDays(b), 0), said = Object.hasOwn(sheet.durations, item.name) ? sheet.durations[item.name] : days;
  return said === days ? [] : [`${item.name}: the bar covers ${days} days but its Duration says ${said}.`];
})];

/** saveBarChart's audit entry. */
export const chartAudit = json => ({ actionType: 'Bar Chart', details: json ? 'Saved the approved bar chart' : 'Removed the approved bar chart' });

/* ---------- Plans and lots: upload a plan, draw a lot, name it, remove it (SubdivisionPlanTab, ProjectViewModel) ---------- */

/** MANAGE_SDP_PLANS (the Super Admin and Engineer Admins) on a project the account can edit. */
export const canManagePlans = canDeleteProject;

/** LOT_USES: what a lot of the plan is for. Only saleable lots are housing units. */
export const LOT_USES = ['Saleable', 'Open Space', 'Road Lot', 'Breaker / Buffer', 'Easement', 'Facility'];
/** NUMBERED_LOT_BLOCKS: road lots and easements go by one number, kept as the lot number under this block ("RL", "EA"). */
export const numberedBlock = use => NUMBERED_USES[use];

/** AddSdpPlanDialog's siteNameError: why another site cannot be saved yet, or null. `naming`: the site on screen has no name yet. */
export function siteNameError(newSite, naming, currentName, taken) {
  const site = newSite.trim(), same = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase();
  if (!site) return 'Name the new site';
  if (naming && !currentName.trim()) return 'Name the site already uploaded';
  if (naming && same(site, currentName)) return 'The two sites need different names';
  return taken.some(t => same(t, site)) ? `${site} is already a site of this project` : null;
}

/**
 * ProjectViewModel.addSdpPlan: a plan uploaded as the next revision of `site` (the site on screen; '' is the first site,
 * unnamed), or with `f.newSite` as the first plan of another site under the contract, which can name the unnamed first
 * site (`f.currentSiteName`). `plans` are the project's sdp_plans records; `f`: { planName, pdfFileUrl, description,
 * newSite (null for a revision), currentSiteName }. Returns { site, build(ids) → the new sdp_plans record, edits:
 * [[plan, changes]] (the site's plans in use switched off, or the first site named), audit }.
 */
export function planUpload(plans, site, f, { user, date }) {
  const of = p => p.siteName || '', fresh = f.newSite != null, name = fresh ? f.newSite.trim() : site, called = (f.currentSiteName || '').trim();
  const edits = !fresh ? plans.filter(p => of(p) === name && on(p.isActive)).map(p => [p, { isActive: 0 }])
    : !site && called ? plans.filter(p => !of(p)).map(p => [p, { siteName: called }]) : [];
  const version = Math.max(0, ...plans.filter(p => of(p) === name).map(p => Number(p.version) || 0)) + 1, planName = f.planName.trim();
  return {
    site: name, edits,
    build: ids => newRecord(ids, 'sdp_plans', { planName, pdfFileUrl: f.pdfFileUrl, version, isActive: 1, uploadedDate: date, uploadedBy: user,
      description: f.description.trim(), scaleDenominator: 0, frame: '', siteName: name, rotation: 0 }),
    audit: { actionType: 'Document Upload', details: `Uploaded Approved SDP Plan${name ? ` (${name})` : ''} Version ${version}: ${planName}`,
      newValue: `Plan: ${planName}, Version: ${version}, File: ${f.pdfFileUrl}` },
  };
}

/** LotArea.squareMetres: the ground area of an outline in page fractions, on a page of `pageW` x `pageH` PDF points drawn at 1:`scale`; two decimals. */
export function lotAreaSqM(polygon, pageW, pageH, scale) {
  if (polygon.length < 3 || !(scale > 0)) return 0;
  const twice = polygon.reduce((t, [x1, y1], i) => { const [x2, y2] = polygon[(i + 1) % polygon.length]; return t + x1 * pageW * y2 * pageH - x2 * pageW * y1 * pageH; }, 0);
  const metres = 0.0254 / 72 * scale; // a PDF point is 1/72 inch on paper
  return Math.round(Math.abs(twice) / 2 * metres * metres * 100) / 100;
}

/** SdpLot.isUnassigned: a lot found on the plan that nobody has given a block and lot yet. */
export const unassigned = l => !(l.blockNumber || '').trim() || !(l.lotNumber || '').trim();
const lotTag = l => `B${l.blockNumber || ''}-L${l.lotNumber || ''}`;

/**
 * UpdateLotProgressDialog's nameError, also asked of a lot being added (`lot` null): why the block and lot cannot be
 * saved, or null. A lot found without a name can stay unnamed; "2-b" and "2-B" are the same lot on the plan.
 */
export function lotNameError(lot, block, lotNo, lots) {
  const b = block.trim(), n = lotNo.trim(), same = (x, y) => String(x ?? '').trim().toLowerCase() === y.toLowerCase();
  if (lot && unassigned(lot) && !b && !n) return null;
  if (!b || !n) return "Block and lot can't be blank";
  return lots.some(l => l.uuid !== lot?.uuid && !unassigned(l) && same(l.blockNumber, b) && same(l.lotNumber, n)) ? `Block ${b} Lot ${n} already exists` : null;
}

/**
 * ProjectViewModel.addSdpLot: a lot drawn on `plan`. `f`: { use, block, lotNo, unit, area (text), description, polygon
 * (page fractions) }; a road lot or easement takes its fixed block, and an area that is not a number is the form's 50.
 * Returns { build(ids) → the sdp_lots record, audit }.
 * shortcut: the phone's audit text also names the lot by its local id, which the website does not have.
 */
export function newLot(plan, f, { user, at }) {
  const block = (NUMBERED_USES[f.use] || f.block).trim(), lotNo = f.lotNo.trim(), unit = f.use === 'Saleable' ? f.unit.trim() : '';
  const typed = String(f.area ?? '').trim(), area = typed !== '' && Number.isFinite(Number(typed)) ? Number(typed) : 50;
  return {
    build: ids => newRecord(ids, 'sdp_lots', {
      blockNumber: block, lotNumber: lotNo, housingUnitNumber: unit, lotAreaSqM: area, polygonNormalizedJson: JSON.stringify(f.polygon), isActive: 1,
      description: f.description.trim(), lotUse: f.use, createdBy: user, createdDate: at, lastModifiedBy: user, lastModifiedDate: at, _ref_sdpPlanId: plan.uuid,
    }),
    audit: { actionType: 'Lot Created', details: `Digitized Lot B${block}-L${lotNo}`,
      newValue: `${f.use}: Block ${block}, Lot ${lotNo}, Unit: ${unit}, Area: ${Number.isInteger(area) ? area.toFixed(1) : area}sqm` },
  };
}

/**
 * setSdpLotUse and renameSdpLot for one save of a lot's name form: the sdp_lots columns to write and their audit
 * entries, or null when nothing changed. `f`: { use, block, lotNo }; a blank block or lot keeps the name.
 */
export function lotNameSave(lot, f, { user, at }) {
  const block = f.block.trim(), lotNo = f.lotNo.trim(), was = lotUse(lot);
  const renamed = Boolean(block && lotNo) && (block !== (lot.blockNumber || '') || lotNo !== (lot.lotNumber || ''));
  const named = renamed ? { blockNumber: block, lotNumber: lotNo } : {}, used = f.use !== was ? { lotUse: f.use } : {};
  const audits = [
    f.use !== was && { actionType: 'Lot Use Changed', details: `Marked 1 lot(s) as ${f.use}: ${lotTag({ ...lot, ...named })}`, oldValue: was, newValue: f.use },
    renamed && { actionType: 'Lot Renamed', details: `Renamed ${lotTag(lot)} to ${lotTag(named)}`,
      oldValue: `Block ${lot.blockNumber || ''} Lot ${lot.lotNumber || ''}`, newValue: `Block ${block} Lot ${lotNo}` },
  ].filter(Boolean);
  return audits.length ? { changes: { ...used, ...named, lastModifiedBy: user, lastModifiedDate: at }, audits } : null;
}

/** ProjectViewModel.deactivateSdpLot: the lot taken off the plan (its record stays, switched off) and its audit entry. */
export const lotRemoved = (lot, { user, at }) => ({
  changes: { isActive: 0, lastModifiedBy: user, lastModifiedDate: at },
  audit: { actionType: 'Lot Deactivated', details: `Deactivated Lot ${lotTag(lot)}`, oldValue: 'Active', newValue: 'Deactivated' },
});

/* ---------- Several lots at once, and formal inspections (GroupProgressDialog, RecordInspectionSnapshotDialog, LotInfoDialog) ---------- */

// The lot form's values as the lot has them now, for a save that changes only some of them.
function lotKept(lot) {
  const p = lot.progress || {};
  return {
    progress: lotProgress(lot), drainage: null, constructionStatus: p.constructionStatus ?? 'Not Started', currentActivity: p.currentActivity || '',
    remarks: p.remarks || '', photos: attachmentList(p.photosJson), billing: BILLING.map(([status]) => ({ billed: billed(p, status), reference: '', remarks: '' })),
  };
}

/**
 * GroupProgressDialog's save: `f` { progress, constructionStatus, currentActivity, remarks } for every lot picked; each
 * keeps its own dates, contractor, photos and billing. Returns [[lot, lotSave's result]] for the lots it changes.
 */
export const lotsProgressSave = (lots, f, who) => lots.map(lot => [lot, lotSave(lot, { ...lotKept(lot), ...f }, who)]).filter(([, saved]) => saved);

/**
 * setSdpLotUse for the lots picked: saveEdits' edits giving `use` to each lot that does not have it yet, with the app's
 * one "Lot Use Changed" entry on the first; empty when every lot already has it.
 */
export function lotsUseSave(lots, use, { user, at }) {
  const changed = lots.filter(l => lotUse(l) !== use);
  const audit = { actionType: 'Lot Use Changed', details: `Marked ${changed.length} lot(s) as ${use}: ${changed.slice(0, 20).map(lotTag).join(', ')}`,
    oldValue: [...new Set(changed.map(lotUse))].join(', '), newValue: use };
  return changed.map((l, i) => [l, { lotUse: use, lastModifiedBy: user, lastModifiedDate: at }, i ? null : audit]);
}

/**
 * recordLotInspection: a formal inspection of a lot. `f`: { progress, constructionStatus, currentActivity, contractor,
 * remarks }. Returns { build(ids) → the sdp_lot_inspections record (the snapshot, with the lot's billing as it
 * stands), audit, progress: lotSave's result bringing the lot's own progress to the snapshot, or null when it already
 * matches }.
 * shortcut: the phone also blanks the lot's start and target dates here; the website keeps them.
 */
export function lotInspection(lot, f, who) {
  const p = lot.progress || {}, pct = Math.min(100, Math.max(0, Math.round(Number(f.progress) || 0)));
  return {
    build: ids => newRecord(ids, 'sdp_lot_inspections', {
      inspectionTimestamp: ids.now, inspectionDate: who.at, inspectedBy: who.user, physicalProgress: pct, constructionStatus: f.constructionStatus,
      currentActivity: f.currentActivity, contractor: f.contractor, remarks: f.remarks, billingStatus: lotBillingLabel(lot.progress),
      billingReference: p.billingReference || '', createdDate: who.at, _ref_sdpPlanId: lot._ref_sdpPlanId, _ref_sdpLotId: lot.uuid,
    }),
    audit: { actionType: 'Inspection Snapshot Recorded', details: `Recorded formal inspection for ${lotLabel(lot)}: ${pct}% (${f.constructionStatus})`,
      oldValue: 'N/A', newValue: `Progress: ${pct}%, Inspector: ${who.user}, Date: ${who.at}` },
    progress: lotSave(lot, { ...lotKept(lot), ...f, remarks: `Snapshot recorded: ${f.remarks}` }, who),
  };
}

/**
 * LotInfoDialog's inspection history: the lot's inspections, latest first, each with its `change` since the one
 * before it ("+25 percentage points / 12 days"; "First inspection recorded" when it is the only one).
 */
export function lotInspections(records, lot) {
  const list = records.filter(r => r.type === 'sdp_lot_inspections' && r._ref_sdpLotId === lot.uuid)
    .sort((a, b) => (Number(b.inspectionTimestamp) || 0) - (Number(a.inspectionTimestamp) || 0));
  const time = x => Date.parse(String(x.inspectionDate || '').replace(' ', 'T'));
  return list.map((x, i) => {
    const before = list[i + 1];
    if (!before) return { ...x, change: list.length === 1 ? 'First inspection recorded' : '' };
    const diff = (Number(x.physicalProgress) || 0) - (Number(before.physicalProgress) || 0), days = Math.floor((time(x) - time(before)) / 86400000);
    return { ...x, change: `${diff >= 0 ? '+' : ''}${diff} percentage points / ${days > 0 ? days : 0} days` };
  });
}

/* ---------- Plan tools: its scale, a quarter turn, the revision in use, deleting it (SubdivisionPlanTab, ProjectViewModel) ---------- */

/** The plan scale dialog's rule: the whole number printed after "SCALE 1:", 10 to 100000, or null. */
export const planScaleOf = text => { const n = /^\d+$/.test(text.trim()) ? Number(text.trim()) : 0; return n >= 10 && n <= 100000 ? n : null; };

/**
 * ProjectViewModel.setSdpPlanScale: the plan's printed scale, and the areas worked out from it for its lots nobody typed
 * an area for (blank, the add form's 50, or the area the last scale gave). `page` is the PDF page's [width, height] in
 * points, or null when the PDF cannot be read: then only the scale is saved. Returns saveEdits' [[record, changes, audit]].
 */
export function planScale(plan, lots, page, scale, { user, at }) {
  const was = Number(plan.scaleDenominator) || 0;
  const worked = page ? lots.flatMap(l => {
    const area = lotAreaSqM(lotPolygon(l), ...page, scale);
    return untypedArea(l, page, was) && area !== (Number(l.lotAreaSqM) || 0) ? [[l, { lotAreaSqM: area, lastModifiedBy: user, lastModifiedDate: at }, null]] : [];
  }) : [];
  return [[plan, { scaleDenominator: scale }, { actionType: 'Project Edit', details: `Set SDP Plan v${plan.version} scale to 1:${scale} and worked out ${worked.length} lot areas`,
    oldValue: was > 0 ? `1:${was}` : 'No scale', newValue: `1:${scale}` }], ...worked];
}

// An area nobody typed for a lot: blank, the add form's 50, or the area `scale` gives its outline.
const untypedArea = (l, page, scale) => {
  const has = Number(l.lotAreaSqM) || 0;
  return has === 0 || has === 50 || (scale > 0 && Math.abs(has - lotAreaSqM(lotPolygon(l), ...page, scale)) < 0.01);
};

/**
 * ProjectViewModel.updateSdpLot for a lot's outline with corners moved or added: its `polygon` (page fractions), and
 * its area from the plan's scale when nobody typed one (`page`: the PDF page's [width, height] in points, or null when
 * the PDF cannot be read; `scale` is 0 without one). Its name, progress, billing and inspections stay. Returns { changes, audit }.
 * shortcut: the phone's audit text names the lot by its local id, which the website does not have; it names the lot.
 */
export function lotOutline(lot, polygon, page, scale, { user, at }) {
  const area = page ? lotAreaSqM(polygon, ...page, scale) : 0;
  return {
    changes: { polygonNormalizedJson: JSON.stringify(polygon), ...(area > 0 && untypedArea(lot, page, scale) ? { lotAreaSqM: area } : {}), lastModifiedBy: user, lastModifiedDate: at },
    audit: { actionType: 'Lot Geometry Changed', details: `Updated Lot ${lotTag(lot)}`, newValue: `Updated boundary/info for Lot ${lotTag(lot)}` },
  };
}

/** PlanRotation.next: the plan a quarter turn clockwise on screen; its lots keep their page coordinates. */
export const planTurned = plan => ({ rotation: ((Number(plan.rotation) || 0) + 90) % 360 });

/**
 * ProjectViewModel.setActiveSdpPlan: `plan` becomes its site's plan in use and the site's other plans are switched off.
 * `plans` are that site's sdp_plans records. Returns saveEdits' [[record, changes, audit]]; empty when nothing changes.
 * shortcut: the phone's audit text names the plan by its local id, which the website does not have; it names the version.
 */
export function planInUse(plans, plan) {
  const audit = { actionType: 'Project Edit', details: `Set active SDP Plan${siteNote(plan)} Version ${plan.version}: ${plan.planName}`, newValue: `Active SDP Plan Version ${plan.version}` };
  return plans.filter(p => on(p.isActive) !== (p.uuid === plan.uuid)).map((p, i) => [p, { isActive: p.uuid === plan.uuid ? 1 : 0 }, i ? null : audit]);
}

const siteNote = plan => ((plan.siteName || '').trim() ? ` (${plan.siteName})` : '');

/**
 * ProjectViewModel.moveSdpPlanToNewSite: `plan`, uploaded as a revision, becomes version 1, in use, of a site of its
 * own; its lots, progress and inspections follow it. `currentSiteName` names the site it leaves when that site has no
 * name yet, on that site's other plans too. `plans`: the project's sdp_plans records. Returns saveEdits' [[record, changes, audit]].
 */
export function planOwnSite(plans, plan, newSite, currentSiteName = '') {
  const blank = p => !(p.siteName || '').trim(), called = blank(plan) ? currentSiteName.trim() : '';
  const old = called || (plan.siteName || '').trim() || 'Main site', site = newSite.trim();
  return [[plan, { siteName: site, version: 1, isActive: 1 }, { actionType: 'Project Edit', details: `Made SDP Plan v${plan.version} of ${old} its own site: ${site}`,
    oldValue: `Site: ${old}, Version: ${plan.version}`, newValue: `Site: ${site}, Version: 1` }],
    ...(called ? plans.filter(p => p.uuid !== plan.uuid && blank(p)).map(p => [p, { siteName: called }, null]) : [])];
}

/** setSdpPlanFrame's text for the box between two opposite corners (page fractions): "minX,minY,maxX,maxY", four decimals. */
export const planFrame = (a, b) => [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])].map(n => n.toFixed(4)).join(',');

/**
 * ProjectViewModel.deleteSdpPlan: the plan's audit entry, and `children`: the project's records under the plan (its
 * lots, roads, lot progress and inspections), which the phone's delete removes with it.
 */
export const planDeleted = (plan, records) => ({
  children: records.filter(r => r._ref_sdpPlanId === plan.uuid),
  audit: { actionType: 'Document Deletion', details: `Deleted SDP Plan${siteNote(plan)} Version ${plan.version}: ${plan.planName}, with its lots and progress`,
    oldValue: `Plan: ${plan.planName}, Version: ${plan.version}` },
});

/* ---------- Renumber a block (BlockNumbering, the RENUMBER BLOCK PANEL, ProjectViewModel.renumberSdpLots) ---------- */

/**
 * BlockNumbering.number: lot numbers for the lots of one block, from where the lots sit. `centres` are the lots'
 * centres as [x, y] in one unit, on the plan as it is turned on screen. The lots are split into rows along the block's
 * long direction and numbered odd along one row and even along the other (1 across from 2), or, `consecutive`, around
 * the block (one row out, the next back). Without the flips the first row is the top (or left) one and numbering starts
 * at its left (or top) end. Returns each lot's number, in the order of `centres`.
 */
export function blockNumbers(centres, first, consecutive = false, otherEnd = false, otherRow = false) {
  const n = centres.length;
  if (n < 2) return centres.map(() => first);
  const mx = centres.reduce((t, p) => t + p[0], 0) / n, my = centres.reduce((t, p) => t + p[1], 0) / n;
  let sxx = 0, syy = 0, sxy = 0;
  for (const [x, y] of centres) { const dx = x - mx, dy = y - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
  const angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
  let ux = Math.cos(angle), uy = Math.sin(angle);
  // Point the long axis rightwards (downwards when the block stands upright).
  if (Math.abs(ux) > 0.3 ? ux < 0 : uy < 0) { ux = -ux; uy = -uy; }
  const along = centres.map(([x, y]) => (x - mx) * ux + (y - my) * uy), across = centres.map(([x, y]) => (x - mx) * -uy + (y - my) * ux);
  // Neighbours in a row sit one lot width apart; the next row is a lot depth away.
  const width = centres.map(([x, y], i) => Math.min(...centres.filter((_, j) => j !== i).map(([a, b]) => Math.hypot(x - a, y - b)))).sort((a, b) => a - b)[Math.floor(n / 2)];
  const rows = [[]], byAcross = centres.map((_, i) => i).sort((a, b) => across[a] - across[b]);
  byAcross.forEach((i, k) => { if (k > 0 && across[i] - across[byAcross[k - 1]] > width * 0.5) rows.push([]); rows.at(-1).push(i); });
  if (otherRow) rows.reverse();
  for (const row of rows) row.sort((a, b) => (otherEnd ? along[b] - along[a] : along[a] - along[b]));
  const numbers = centres.map(() => first), used = [0, 0];
  let next = first;
  rows.forEach((row, k) => {
    if (consecutive) for (const i of k % 2 ? [...row].reverse() : row) numbers[i] = next++;
    else { row.forEach((i, m) => { numbers[i] = first + k % 2 + 2 * (used[k % 2] + m); }); used[k % 2] += row.length; }
  });
  return numbers;
}

/**
 * ProjectViewModel.renumberSdpLots: `lots` get `block` and their `numbers` (blockNumbers, in the lots' order); progress
 * and billing follow the lots. `oldBlock` is the block they were picked from ('' for lots without a name). A found lot
 * is marked as checked against the plan. Returns saveEdits' [[lot, changes, audit]], one audit entry on the first.
 */
export function lotsRenumbered(lots, numbers, block, oldBlock, { user, at }) {
  const audit = { actionType: 'Lot Geometry Changed', details: oldBlock ? `Renumbered ${lots.length} lots of Block ${oldBlock}` : `Numbered ${lots.length} unassigned lots`,
    oldValue: oldBlock ? `Block ${oldBlock}` : 'Unassigned', newValue: `Block ${block}, Lots ${Math.min(...numbers)}-${Math.max(...numbers)}` };
  return lots.map((l, i) => [l, { blockNumber: block, lotNumber: String(numbers[i]), ...((l.description || '').startsWith('Auto-detected') ? { description: 'Renumbered to the plan' } : {}),
    lastModifiedBy: user, lastModifiedDate: at }, i ? null : audit]);
}

/** The renumber panel's warnings about a plan's lots: saleable lots still without a name, and names two lots share. */
export function renumberNotes(lots) {
  const tags = lots.filter(l => !unassigned(l)).map(lotTag), twice = [...new Set(tags.filter((t, i) => tags.indexOf(t) !== i))];
  const unnamed = lots.filter(l => unassigned(l) && lotUse(l) === 'Saleable').length;
  return [unnamed && `${unnamed} ${unnamed === 1 ? 'lot has' : 'lots have'} no block and lot yet.`,
    twice.length && `Used twice: ${twice.slice(0, 4).join(', ')}${twice.length > 4 ? ` and ${twice.length - 4} more` : ''}`].filter(Boolean);
}

/* ---------- Find lots (LotDetector, PlanLabels, ProjectViewModel.addDetectedSdpLots and removeDetectedSdpLots) ---------- */

// LotDetector, ported line for line. The plan page is a picture of its lines: `dark` is a Uint8Array, row by row,
// 1 where the page is dark. Every closed white area is a candidate; the most common size among the near-rectangular
// ones is a lot. Lots far from the main group (title block, legend, vicinity map) are dropped, and larger clean
// rectangles inside the site are kept as open space or facility areas. Roads are the long open strips and are left out.
const GROW = 2;
let fillStack = new Int32Array(4096);

/** Thin anti-aliased lines can leave one-pixel gaps; growing walls by a pixel closes them. */
function thicken(dark, w) {
  const out = dark.slice();
  for (let p = 0; p < dark.length; p++) {
    if (!dark[p]) continue;
    if (p % w + 1 < w) out[p + 1] = 1;
    if (p + w < dark.length) out[p + w] = 1;
  }
  return out;
}

// The closed white area around `start`, flood-filled into `labels` as `id`: its size, its box, and how much of a
// rectangle it is, measured along its own axes (second moments), so tilted lots count as rectangles too.
function fillRegion(start, id, wall, labels, w, h) {
  const r = { id, area: 0, minX: w, minY: h, maxX: -1, maxY: -1, touchesEdge: false };
  let sp = 0, sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
  fillStack[sp++] = start;
  labels[start] = id;
  while (sp > 0) {
    const p = fillStack[--sp], x = p % w, y = (p - x) / w;
    r.area++;
    sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
    if (x < r.minX) r.minX = x;
    if (x > r.maxX) r.maxX = x;
    if (y < r.minY) r.minY = y;
    if (y > r.maxY) r.maxY = y;
    if (x === 0 || y === 0 || x === w - 1 || y === h - 1) r.touchesEdge = true;
    if (sp + 4 > fillStack.length) { const grown = new Int32Array(fillStack.length * 2); grown.set(fillStack); fillStack = grown; }
    if (x > 0 && !wall[p - 1] && !labels[p - 1]) { labels[p - 1] = id; fillStack[sp++] = p - 1; }
    if (x < w - 1 && !wall[p + 1] && !labels[p + 1]) { labels[p + 1] = id; fillStack[sp++] = p + 1; }
    if (y > 0 && !wall[p - w] && !labels[p - w]) { labels[p - w] = id; fillStack[sp++] = p - w; }
    if (y < h - 1 && !wall[p + w] && !labels[p + w]) { labels[p + w] = id; fillStack[sp++] = p + w; }
  }
  const mx = sx / r.area, my = sy / r.area, a = sxx / r.area - mx * mx, b = syy / r.area - my * my, c = sxy / r.area - mx * my;
  const d = Math.sqrt((a - b) * (a - b) / 4 + c * c), l1 = (a + b) / 2 + d, l2 = Math.max((a + b) / 2 - d, 1 / 12);
  return Object.assign(r, { fill: r.area / (12 * Math.sqrt(l1 * l2)), aspect: Math.sqrt(l1 / l2),
    cx: (r.minX + r.maxX) / 2, cy: (r.minY + r.maxY) / 2, w: r.maxX - r.minX + 1, h: r.maxY - r.minY + 1 });
}

/** Lots are drawn to the middle of their lines: let each area take the line pixels next to it, GROW deep. */
function growIntoWalls(labels, wall, w) {
  for (let n = 0; n < GROW; n++) {
    const before = labels.slice();
    for (let p = 0; p < labels.length; p++) {
      if (!wall[p] || before[p]) continue;
      const x = p % w;
      labels[p] = (x > 0 && before[p - 1]) || (x < w - 1 && before[p + 1]) || (p >= w && before[p - w]) || (p + w < labels.length && before[p + w]) || 0;
    }
  }
}

/** The most common size, counted on a log scale so lots of slightly different sizes fall together. */
function typicalArea(regions) {
  const bins = new Map();
  for (const r of regions) { const k = Math.round(Math.log(r.area) * 8); bins.set(k, (bins.get(k) || 0) + 1); }
  let best = null;
  for (const bin of bins) if (!best || bin[1] > best[1]) best = bin;
  return Math.exp(best[0] / 8);
}

// Regions whose boxes are less than `gap` apart, joined into groups.
function groupsOf(regions, gap) {
  const parent = regions.map((_, i) => i);
  const find = i => { while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; } return i; };
  const order = regions.map((_, i) => i).sort((a, b) => regions[a].minX - regions[b].minX);
  for (let a = 0; a < order.length; a++) {
    const ra = regions[order[a]];
    for (let b = a + 1; b < order.length; b++) {
      const rb = regions[order[b]];
      if (rb.minX - ra.maxX > gap) break;
      const gx = Math.max(0, rb.minX - ra.maxX, ra.minX - rb.maxX), gy = Math.max(0, rb.minY - ra.maxY, ra.minY - rb.maxY);
      if (gx < gap && gy < gap) parent[find(order[a])] = find(order[b]);
    }
  }
  const groups = new Map();
  regions.forEach((r, i) => { const k = find(i); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(r); });
  return [...groups.values()];
}

/** [region, block, lot]: blocks top to bottom, lots in rows along each block's long direction. */
function numberLots(lots, touchGap) {
  const least = (list, key) => list.reduce((m, r) => Math.min(m, r[key]), Infinity);
  const blocks = groupsOf(lots, touchGap).sort((a, b) => least(a, 'minY') - least(b, 'minY') || least(a, 'minX') - least(b, 'minX'));
  return blocks.flatMap((block, bi) => {
    const mx = block.reduce((t, r) => t + r.cx, 0) / block.length, my = block.reduce((t, r) => t + r.cy, 0) / block.length;
    let sxx = 0, syy = 0, sxy = 0;
    for (const r of block) { const dx = r.cx - mx, dy = r.cy - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
    // Long axis of the block (principal direction of the lot centres).
    const angle = 0.5 * Math.atan2(2 * sxy, sxx - syy), ux = Math.cos(angle), uy = Math.sin(angle);
    const rowBand = block.map(r => Math.min(r.w, r.h)).sort((a, b) => a - b)[Math.floor(block.length / 2)] * 0.8;
    const row = r => Math.round(((r.cx - mx) * -uy + (r.cy - my) * ux) / rowBand), along = r => (r.cx - mx) * ux + (r.cy - my) * uy;
    return [...block].sort((a, b) => row(a) - row(b) || along(a) - along(b)).map((r, li) => [r, bi + 1, li + 1]);
  });
}

/** Left edge top to bottom, then right edge bottom to top: exact for convex lots, close for the rest. */
function outlineOf(r, labels, w) {
  const left = [], right = [], h = labels.length / w;
  for (let y = Math.max(r.minY - GROW, 0); y <= Math.min(r.maxY + GROW, h - 1); y++) {
    let lo = -1, hi = -1;
    for (let x = Math.max(r.minX - GROW, 0); x <= Math.min(r.maxX + GROW, w - 1); x++) if (labels[y * w + x] === r.id) { if (lo < 0) lo = x; hi = x; }
    if (lo < 0) continue;
    left.push([lo, y]);
    right.push([hi + 1, y + 1]);
  }
  return [...left, ...right.reverse()];
}

/** Douglas-Peucker on the closed outline, keeping corners further than `eps` pixels from a straight run. */
function simplify(points, eps) {
  if (points.length < 4) return points;
  const keep = new Uint8Array(points.length), stack = [[0, points.length - 1]];
  keep[0] = keep[points.length - 1] = 1;
  const distance = (p, a, b) => {
    const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
    return len === 0 ? Math.hypot(p[0] - a[0], p[1] - a[1]) : Math.abs(dy * p[0] - dx * p[1] + b[0] * a[1] - b[1] * a[0]) / len;
  };
  while (stack.length) {
    const [a, b] = stack.pop();
    let best = -1, bestD = eps;
    for (let i = a + 1; i < b; i++) { const far = distance(points[i], points[a], points[b]); if (far > bestD) { bestD = far; best = i; } }
    if (best >= 0) { keep[best] = 1; stack.push([a, best], [best, b]); }
  }
  return points.filter((_, i) => keep[i]);
}

// An outline in pixels as page fractions, six decimals as a drawn lot's corners are.
const outlineOnPage = (r, labels, w, h) => simplify(outlineOf(r, labels, w), 1.5).map(([x, y]) => [Math.round(x / w * 1e6) / 1e6, Math.round(y / h * 1e6) / 1e6]);

/**
 * LotDetector.detect: the lots of a plan page (`dark`: its line pixels, `width` x `height`). Returns the lots first,
 * numbered block by block (lots that touch form a block; blocks top to bottom, lots row by row along the block), then
 * the larger areas: [{ block, lot, polygon (page fractions), isLot }]. Empty when the picture has no clear lot pattern
 * (a scanned plan, say).
 */
export function findLots(dark, width, height) {
  const wall = thicken(dark, width), labels = new Int32Array(width * height), regions = [];
  for (let p = 0; p < wall.length; p++) if (!wall[p] && !labels[p]) regions.push(fillRegion(p, regions.length + 1, wall, labels, width, height));
  const candidates = regions.filter(r => !r.touchesEdge && r.area > 50);
  const rectish = candidates.filter(r => r.fill > 0.55 && r.aspect < 6);
  if (rectish.length < 5) return [];
  const typical = typicalArea(rectish);
  // Lots near each other, in groups at least a tenth the size of the biggest group.
  const groups = groupsOf(rectish.filter(r => r.area > typical * 0.35 && r.area < typical * 3), Math.sqrt(typical) * 2.5);
  const biggest = Math.max(0, ...groups.map(g => g.length));
  const lots = groups.filter(g => g.length >= Math.max(3, Math.floor(biggest / 10))).flat();
  if (!lots.length) return [];
  const edge = (pick, key) => lots.reduce((m, r) => pick(m, r[key]), pick === Math.min ? Infinity : -Infinity);
  const site = [edge(Math.min, 'minX'), edge(Math.min, 'minY'), edge(Math.max, 'maxX'), edge(Math.max, 'maxY')];
  const large = candidates.filter(r => r.area >= typical * 3 && r.area < typical * 40 && r.fill > 0.6 && r.aspect < 4
    && r.cx >= site[0] && r.cx <= site[2] && r.cy >= site[1] && r.cy <= site[3]).sort((a, b) => a.cy - b.cy || a.cx - b.cx);
  growIntoWalls(labels, wall, width);
  return [...numberLots(lots, Math.trunc(Math.sqrt(typical) * 0.15 + 4)).map(([r, block, lot]) => ({ block, lot, polygon: outlineOnPage(r, labels, width, height), isLot: true })),
    ...large.map((r, i) => ({ block: 0, lot: i + 1, polygon: outlineOnPage(r, labels, width, height), isLot: false }))];
}

/**
 * LotDetector.traceAt: the outline of the closed area around (`x`, `y`), both page fractions, for a lot the user
 * points at; null when the point is on a line, or the area is open or too big to be one lot.
 */
export function traceLot(dark, width, height, x, y) {
  const wall = thicken(dark, width);
  const px = Math.min(width - 1, Math.max(0, Math.trunc(x * width))), py = Math.min(height - 1, Math.max(0, Math.trunc(y * height)));
  // A click can land on the lot line itself; take the nearest open pixel within a few pixels.
  let start = -1;
  for (let d = 0; d <= 6 && start < 0; d++) for (let dy = -d; dy <= d && start < 0; dy++) for (let dx = -d; dx <= d && start < 0; dx++) {
    const cx = px + dx, cy = py + dy;
    if (cx >= 0 && cx < width && cy >= 0 && cy < height && !wall[cy * width + cx]) start = cy * width + cx;
  }
  if (start < 0) return null;
  const labels = new Int32Array(width * height), r = fillRegion(start, 1, wall, labels, width, height);
  if (r.touchesEdge || r.area < 50 || r.area > Math.floor(width * height / 50)) return null;
  growIntoWalls(labels, wall, width);
  return outlineOnPage(r, labels, width, height);
}

const NUM = '(\\d{1,4}[A-Z]?)';
const LOT_WORD = new RegExp(`^(?:LOT|L)?[\\s.#-]*${NUM}$`, 'i'), BLOCK_LABEL = new RegExp(`^(?:BLOCK|BLK|B)[\\s.#-]*${NUM}$`, 'i');
const BLOCK_AND_LOT = new RegExp(`^(?:BLOCK|BLK|B)[\\s.#-]*${NUM}[\\s.,/#-]*(?:LOT|L)[\\s.#-]*${NUM}$`, 'i');
const BLOCK_WORD = /^(?:BLOCK|BLK\.?)$/i, NUMBER_WORD = new RegExp(`^${NUM}$`, 'i');

/**
 * PlanLabels.name: [block, lot] for each of `lots` (outlines in page fractions), in order, from the text printed on
 * the plan; both '' where the plan does not name the lot, or names two lots the same. A lot's number is printed inside
 * it ("12", "L-12", "LOT 12", or "B3 L12" with its block); a block's name ("BLOCK 3", "BLK. 3", "B-3") sits inside or
 * beside its lots. `blocks` are findLots' block numbers (lots that touch), so a label beside a block names each of its
 * lots. `page`: { words: [{ text, x, y (its centre), size (letter height) }], width, height }, in page points, y down.
 */
export function lotNames(lots, blocks, page) {
  const words = page.words.map(w => ({ ...w, text: w.text.trim().toUpperCase() }));
  const outlines = lots.map(lot => lot.map(([x, y]) => [x * page.width, y * page.height]));
  const span = (o, i) => { const v = o.map(p => p[i]); return [Math.min(...v), Math.max(...v)]; };
  const boxes = outlines.map(o => [...span(o, 0), ...span(o, 1)]), centres = boxes.map(b => [(b[0] + b[1]) / 2, (b[2] + b[3]) / 2]);
  const to = (w, c) => Math.hypot(w.x - c[0], w.y - c[1]);
  const nearest = (list, far) => list.reduce((best, x) => (best == null || far(x) < far(best) ? x : best), null);
  const used = words.map(() => false), labels = [];
  // Block labels: "BLOCK 3" in one word, or "BLOCK" with its number beside or under it.
  words.forEach((w, i) => { const m = !BLOCK_AND_LOT.test(w.text) && BLOCK_LABEL.exec(w.text); if (m) { labels.push({ ...w, text: m[1] }); used[i] = true; } });
  words.forEach((w, i) => {
    if (!BLOCK_WORD.test(w.text)) return;
    const n = nearest(words.map((_, k) => k).filter(k => !used[k] && NUMBER_WORD.test(words[k].text)), k => to(words[k], [w.x, w.y]));
    if (n == null || to(words[n], [w.x, w.y]) > w.size * (w.text.length * 0.5 + 2)) return;
    used[i] = used[n] = true;
    labels.push({ text: words[n].text, x: (w.x + words[n].x) / 2, y: (w.y + words[n].y) / 2, size: w.size });
  });
  // Each label names the found block whose nearest lot is within two lot widths of it.
  const shoelace = o => o.reduce((t, [x, y], i) => { const [x2, y2] = o[(i + 1) % o.length]; return t + x * y2 - x2 * y; }, 0) / 2;
  const typical = outlines.map(o => Math.sqrt(Math.abs(shoelace(o)))).sort((a, b) => a - b)[Math.floor(outlines.length / 2)] ?? 0;
  const labelsOf = new Map();
  for (const l of labels) {
    const i = nearest(centres.map((_, k) => k), k => to(l, centres[k]));
    if (i != null && to(l, centres[i]) < typical * 2) labelsOf.set(blocks[i], [...(labelsOf.get(blocks[i]) || []), l]);
  }
  const contains = (o, x, y) => {
    let inside = false;
    for (let i = 0, j = o.length - 1; i < o.length; j = i++) {
      const [xi, yi] = o[i], [xj, yj] = o[j];
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  };
  const names = outlines.map((o, i) => {
    const b = boxes[i], inside = words.filter((w, k) => !used[k] && w.x >= b[0] && w.x <= b[1] && w.y >= b[2] && w.y <= b[3] && contains(o, w.x, w.y));
    for (const w of inside) { const m = BLOCK_AND_LOT.exec(w.text); if (m) return [m[1], m[2]]; }
    // The lot number is the biggest number in the lot (an undecorated area is smaller), else the one nearest its middle.
    const big = w => Math.round(w.size * 2);
    const lot = inside.filter(w => LOT_WORD.test(w.text)).reduce((best, w) => (!best || big(w) > big(best) || (big(w) === big(best) && to(w, centres[i]) < to(best, centres[i])) ? w : best), null);
    const block = nearest(labelsOf.get(blocks[i]) || [], l => to(l, centres[i]));
    return lot && block ? [block.text, LOT_WORD.exec(lot.text)[1]] : ['', ''];
  });
  const count = new Map();
  for (const [b, l] of names) if (l) count.set(`${b}|${l}`, (count.get(`${b}|${l}`) || 0) + 1);
  return names.map(([b, l]) => (count.get(`${b}|${l}`) > 1 ? ['', ''] : [b, l]));
}

/** A lot Find lots added that nobody has renumbered ("Auto-detected…"); one without progress is replaced by the next Find lots. */
export const foundLot = l => (l.description || '').startsWith('Auto-detected');

/**
 * ProjectViewModel.addDetectedSdpLots: the `areas` Find lots found (findLots', each with the blockName and lotName the
 * plan prints on it) as lots of `plan`, replacing the earlier found lots that have no progress yet. `lots`: the plan's
 * active lots, each with its `progress`. A found area whose middle is inside a lot that stays (drawn by hand,
 * renumbered, or with progress) is left out, and a name a lot that stays already has is not given again. `page`: the
 * PDF page's [width, height] in points, for the areas (0 without a scale). Returns { stale: saveEdits' edits that take
 * the earlier found lots off, builds: [build(ids) → an sdp_lots record], audit }.
 */
export function foundLots(plan, lots, areas, page, { user, at }) {
  const stays = l => !foundLot(l) || Boolean(l.progress);
  const kept = lots.filter(stays).map(lotPolygon).filter(p => p.length).map(p => [Math.min(...p.map(q => q[0])), Math.min(...p.map(q => q[1])), Math.max(...p.map(q => q[0])), Math.max(...p.map(q => q[1]))]);
  const keptNames = new Set(lots.filter(l => stays(l) && !unassigned(l)).map(l => `${l.blockNumber}|${l.lotNumber}`));
  const added = areas.filter(a => {
    const cx = a.polygon.reduce((t, p) => t + p[0], 0) / a.polygon.length, cy = a.polygon.reduce((t, p) => t + p[1], 0) / a.polygon.length;
    return !kept.some(b => cx >= b[0] && cx <= b[2] && cy >= b[1] && cy <= b[3]);
  });
  return {
    stale: lots.filter(l => !stays(l)).map(l => [l, { isActive: 0, lastModifiedBy: user, lastModifiedDate: at }, null]),
    builds: added.map(a => {
      // The block and lot printed on the plan, else none and the engineer names the lot.
      const named = Boolean(a.lotName) && !keptNames.has(`${a.blockName}|${a.lotName}`);
      return ids => newRecord(ids, 'sdp_lots', {
        blockNumber: named ? a.blockName : '', lotNumber: named ? a.lotName : '', housingUnitNumber: '', lotAreaSqM: page ? lotAreaSqM(a.polygon, ...page, Number(plan.scaleDenominator) || 0) : 0,
        polygonNormalizedJson: JSON.stringify(a.polygon), isActive: 1, description: a.isLot ? 'Auto-detected' : 'Auto-detected open space or facility', lotUse: a.isLot ? '' : 'Open Space',
        createdBy: user, createdDate: at, lastModifiedBy: user, lastModifiedDate: at, _ref_sdpPlanId: plan.uuid,
      });
    }),
    audit: { actionType: 'Lot Created', details: `Added ${added.length} lots found in SDP Plan v${plan.version}`,
      newValue: `${added.filter(a => a.isLot).length} lots, ${added.filter(a => !a.isLot).length} larger areas` },
  };
}

/** ProjectViewModel.removeDetectedSdpLots: the plan's found lots without progress taken off, with one audit entry. Returns saveEdits' edits. */
export function foundLotsRemoved(plan, lots, { user, at }) {
  const stale = lots.filter(l => foundLot(l) && !l.progress);
  return stale.map((l, i) => [l, { isActive: 0, lastModifiedBy: user, lastModifiedDate: at },
    i ? null : { actionType: 'Lot Deactivated', details: `Removed ${stale.length} auto-detected lots from SDP Plan v${plan.version}`, oldValue: 'Active', newValue: 'Deactivated' }]);
}
