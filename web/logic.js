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

function isoDay(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((s || '').trim());
  return m ? Date.UTC(+m[1], +m[2] - 1, +m[3]) : null;
}

/** ProjectAttention.slippage in words (PortfolioRow.scheduleText). */
export function scheduleText(project) {
  const s = Math.round(((Number(project.actualAccomplishment) || 0) - (Number(project.targetAccomplishment) || 0)) * 100) / 100;
  if (s > 0) return `${s.toFixed(2)}% ahead`;
  if (s < 0) return `${(-s).toFixed(2)}% behind`;
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
