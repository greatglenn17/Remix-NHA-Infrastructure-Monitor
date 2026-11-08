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
  if (!finished && (slip <= -10 || project.status === 'Behind Schedule')) {
    flags.push({ kind: 'behind', level: slip <= -15 ? 'critical' : 'warning',
      message: slip < 0 ? `${(-slip).toFixed(2)}% behind schedule` : 'Marked behind schedule' });
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
