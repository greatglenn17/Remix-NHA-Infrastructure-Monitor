// Run: node --test web/logic.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';

test('variation order: a title and lines, not the stored name', () => {
  const vo = { documentName: 'Variation Order No. 2 (Deductive) - PHP 1,500,000.50', remarks: 'Reason: Less fill | Date Approved: 2026-11-05' };
  assert.equal(L.variationTitle(vo.documentName), 'Variation Order No. 2');
  assert.deepEqual(L.variationLines(vo), ['Type: Deductive', 'Amount: ₱1,500,000.50', 'Date approved: Nov 5, 2026', 'Reason: Less fill']);
  // Not approved yet: no "Date approved: -" line; an old name without an amount keeps its title.
  assert.deepEqual(L.variationLines({ documentName: 'Variation Order No. 3', remarks: 'Added culvert' }), ['Reason: Added culvert']);
  assert.equal(L.variationTitle('Variation Order No. 3'), 'Variation Order No. 3');
  assert.equal(L.orderLabel(vo.documentName), 'Variation Order No. 2');
  // keepOrderFields puts a dropped date back first, with no "|".
  assert.deepEqual(L.variationLines({ documentName: vo.documentName, remarks: L.keepOrderFields('Reason: Less fill | Date Approved: 2026-11-05', 'Reason: More fill') }),
    ['Type: Deductive', 'Amount: ₱1,500,000.50', 'Date approved: Nov 5, 2026', 'Reason: More fill']);
  // Older names ContractCost also reads: a sign or brackets, no dash.
  assert.equal(L.variationTitle('Variation Order No. 2 - PHP -20,000'), 'Variation Order No. 2');
  assert.deepEqual(L.variationLines({ documentName: 'Variation Order No. 4 - PHP (10,000)' }), ['Amount: -₱10,000.00']);
  assert.equal(L.variationTitle('Variation Order No. 1 PHP 50,000'), 'Variation Order No. 1');
});

test('nextLot: an arrow key moves to the nearest lot that way, a step sideways counting double', () => {
  // A 3 x 2 grid of lots, a, b, c over d, e, f, 10 apart; g sits low and to the right of c.
  const c = new Map([['a', [0, 0]], ['b', [10, 0]], ['c', [20, 0]], ['d', [0, 10]], ['e', [10, 10]], ['f', [20, 10]], ['g', [26, 4]]]);
  assert.deepEqual([[1, 0], [-1, 0], [0, 1], [0, -1]].map(dir => L.nextLot(c, 'b', dir)), ['c', 'a', 'e', null]);
  assert.equal(L.nextLot(c, 'c', [1, 0]), 'g');
  assert.equal(L.nextLot(c, 'a', [-1, 0]), null);
});

test('roles that see every project', () => {
  assert.equal(L.seesAll({ role: 'SUPER_ADMIN' }), true);
  assert.equal(L.seesAll({ role: 'SUPER_VIEWER' }), true);
  assert.equal(L.seesAll({ role: 'FIELD_ENGINEER', allProjects: true }), true);
  assert.equal(L.seesAll({ role: 'ENGINEER_ADMIN' }), false);
  assert.equal(L.seesAll({}), false);
});

test('record data string is unpacked, bad JSON gives an empty row', () => {
  const r = L.parseRecord({ uuid: 'u', type: 'project_payments', revision: 3, data: '{"name":"1st billing","grossAmount":100}' });
  assert.equal(r.name, '1st billing');
  assert.equal(r.revision, 3);
  assert.equal(L.parseRecord({ uuid: 'u', data: '{oops' }).uuid, 'u');
});

test('contract cost: scope sum, fallback, approved variation orders', () => {
  const scope = JSON.stringify([{ name: 'A', amount: 1000, weightPct: 50 }, { name: 'B', amount: 1000, weightPct: 50 }]);
  assert.equal(L.originalCost({ scopeOfWork: scope, contractCostOriginal: 5 }), 2000);
  assert.equal(L.originalCost({ scopeOfWork: 'Site development', contractCostOriginal: 5 }), 5);
  const docs = [
    { documentName: 'Variation Order No. 1 - PHP 500.00', status: 'Approved' },
    { documentName: 'Variation Order No. 2 (Deductive) - PHP 200', status: 'Approved' },
    { documentName: 'Variation Order No. 3 - PHP 9,999', status: 'Pending' },
  ];
  assert.equal(L.currentCost({ scopeOfWork: scope }, docs), 2300);
  assert.equal(L.currentCost({ scopeOfWork: scope }, []), 2000);
});

test('scope items: JSON list or plain text', () => {
  assert.deepEqual(L.scopeItems('Road works'), [{ name: 'Road works', amount: 0, weightPct: 100 }]);
  assert.equal(L.scopeItems('[{"name":"A","amount":1,"weightPct":100},{"name":" "}]').length, 1);
});

test('status levels, view-only tag and attention flags follow the app', () => {
  assert.equal(L.varianceLevel(0), 'ok');
  assert.equal(L.varianceLevel(-9.99), 'neutral');
  assert.equal(L.varianceLevel(-10), 'watch');
  assert.equal(L.varianceLevel(-15), 'behind');
  assert.equal(L.statusLevel({ status: 'Behind Schedule', actualAccomplishment: 50, targetAccomplishment: 52 }), 'watch');
  assert.equal(L.statusLevel({ status: 'Suspended', actualAccomplishment: 10, targetAccomplishment: 40 }), 'neutral');
  assert.equal(L.statusLevel({ status: 'On-going', actualAccomplishment: 100, targetAccomplishment: 100 }), 'ok');
  assert.equal(L.displayStatus({ status: 'On-going', actualAccomplishment: 100 }), 'Completed');

  const p = { assignedUids: ['me'] };
  assert.equal(L.isViewOnly(p, 'other', { role: 'FIELD_ENGINEER', allProjects: true }), true);
  assert.equal(L.isViewOnly(p, 'other', { role: 'SUPER_VIEWER' }), false);
  assert.equal(L.isViewOnly(p, 'me', { role: 'FIELD_ENGINEER' }), false);
  assert.equal(L.isViewOnly({ assignedUids: [], createdByUid: 'me' }, 'me', {}), false);

  const docs = [{ documentName: 'Time Extension No. 1', status: 'Pending' }, { documentName: 'Variation Order No. 1 - PHP 0', status: 'Approved' }];
  assert.equal(L.waitingApproval(docs), 1);
  const project = { status: 'On-going', actualAccomplishment: 20, targetAccomplishment: 36, contractCostOriginal: 1000, completionDateOriginal: '2026-11-20' };
  const kinds = L.attentionFlags(project, docs, 400, '2026-11-08').map(f => `${f.kind}:${f.level}`);
  assert.deepEqual(kinds, ['behind:critical', 'billingAhead:warning', 'waiting:warning', 'dueSoon:warning']);
  assert.equal(L.attentionFlags({ ...project, completionDateOriginal: '2026-11-01' }, [], 0, '2026-11-08')[1].message, 'Overdue by 7 days');
  assert.deepEqual(L.attentionFlags({ ...project, actualAccomplishment: 100, targetAccomplishment: 100 }, [], 0, '2026-11-08'), []);
  // Developer requests not packaged (oldest received), and no field update in 14 days: since the newest report, else the start.
  const quiet = { status: 'On-going', actualAccomplishment: 30, targetAccomplishment: 30, completionDateOriginal: '2027-06-30', dateStarted: '2026-10-01' };
  const requests = [{ dateReceived: '2026-11-01', datePackaged: '' }, { dateReceived: '2026-10-29', datePackaged: '' }, { dateReceived: '2026-10-01', datePackaged: '2026-10-05' }];
  assert.deepEqual(L.attentionFlags(quiet, [], 0, '2026-11-08', requests, '2026-10-20').map(f => [f.kind, f.level, f.message]), [
    ['requests', 'warning', '2 developer requests not yet packaged (oldest received 10 days ago)'],
    ['stale', 'stale', 'No field update in 19 days']]);
  assert.equal(L.attentionFlags(quiet, [], 0, '2026-11-08', [], '2026-10-26').length, 0, '13 days is still recent');
  assert.equal(L.attentionFlags(quiet, [], 0, '2026-11-08')[0].message, 'No field update yet, 38 days since the start', 'no report yet: from the start date');
  assert.equal(L.attentionFlags({ ...quiet, status: 'Suspended' }, [], 0, '2026-11-08').length, 0, 'a suspended project is not expected to report');
  assert.equal(L.attentionFlags(quiet, [], 0, '2026-11-08', [{ dateReceived: 'soon' }])[0].message, '1 developer request not yet packaged');
  assert.equal(L.latestReportDay([{ createdAtTimestamp: new Date(2026, 9, 20, 9).getTime() }, { createdAtTimestamp: new Date(2026, 9, 3).getTime() }]), '2026-10-20');
  assert.equal(L.latestReportDay([]), null);
  assert.equal(L.billedLabel(0, 100), 'No billings yet');
  assert.equal(L.billedLabel(25, 100), '25.00%');
});

test('days left, schedule words, dates, money, attachment paths', () => {
  const p = { completionDateOriginal: '2026-11-10', completionDateRevised: '-', actualAccomplishment: 40, targetAccomplishment: 45.5 };
  assert.equal(L.daysLeftText(p, '2026-11-07'), '3 days');
  assert.equal(L.daysLeftText({ ...p, completionDateRevised: '2026-11-01' }, '2026-11-07'), 'Overdue by 6 days');
  assert.equal(L.daysLeftText({ ...p, actualAccomplishment: 100 }, '2026-11-07'), 'Completed');
  assert.equal(L.daysLeftText(p, '2026-11-09'), '1 day');
  assert.equal(L.daysLeftText(p, '2026-11-11'), 'Overdue by 1 day');
  assert.equal(L.scheduleText(p), '5.50% slipping');
  assert.equal(L.scheduleText({ ...p, actualAccomplishment: 30 }), '15.50% behind');
  // Overdue: 0% done against a 0% plan used to read "on schedule" in green.
  const late = { status: 'On-going', completionDateOriginal: '2026-11-03', actualAccomplishment: 0, targetAccomplishment: 0 };
  assert.equal(L.daysOverdue(late, '2026-11-08'), 5);
  assert.equal(L.daysOverdue(late, '2026-11-03'), 0);
  assert.equal(L.daysOverdue({ ...late, actualAccomplishment: 100 }, '2026-11-08'), 0);
  assert.equal(L.daysOverdue({ ...late, completionDateRevised: '2026-12-01' }, '2026-11-08'), 0);
  assert.equal(L.scheduleText(late, '2026-11-08'), 'past completion date');
  assert.equal(L.statusLevel(late, '2026-11-08'), 'behind');
  assert.equal(L.statusLevel(late), 'ok');
  assert.equal(L.statusLevel({ ...late, status: 'Suspended' }, '2026-11-08'), 'neutral');
  assert.equal(L.displayDate('2026-10-20'), 'Oct 20, 2026');
  assert.equal(L.displayDate('TBA'), 'TBA');
  assert.equal(L.displayDate('2026-11-12 14:05'), 'Nov 12, 2026, 14:05');
  assert.equal(L.displayDate('2026-11-12 14:05:09'), 'Nov 12, 2026, 14:05:09');
  assert.equal(L.shownWord('M1 BILLED'), 'M1 billed');
  assert.equal(L.shownWord('100% / RFO'), '100% / RFO');
  assert.equal(L.formatPhp(-1234.5), '-₱1,234.50');
  assert.equal(L.netAmount({ grossAmount: 1000, retentionPct: 10 }), 900);
  assert.equal(L.isOpenIssue({ status: 'Pending' }), true);
  assert.equal(L.isOpenIssue({ status: 'Resolved' }), false);
  // Saved before Pending / Resolved: Approved, No Action and Closed read as resolved; anything else stays open.
  assert.deepEqual(['Approved', 'No Action', 'Closed', 'Open', '', 'Critical'].map(status => L.isOpenIssue({ status })), [false, false, false, true, true, true]);
  assert.deepEqual(['No Action', 'Pending', undefined].map(status => L.issueStatus({ status })), ['Resolved', 'Pending', 'Pending']);
  assert.deepEqual(L.ISSUE_STATUSES, ['Pending', 'Resolved']);
  const id = '0123abcd-0123-0123-0123-0123456789ab';
  assert.equal(L.storagePathOf(`nhafile:${id}/${id}.jpg`), `attachments/${id}/${id}.jpg`);
  assert.equal(L.storagePathOf(`nhafile:${id}.pdf`), `attachments/${id}.pdf`);
  assert.equal(L.storagePathOf('https://example.com/x.jpg'), null);
});

// San Ildefonso Heights Phase 1, approved bar chart (same data as BarChartTest).
const sih = { label: 'Original', effective: '2026-01-01', items: [
  ['Land Acquisition', 26235612.20, [[1, 60]]], ['Mobilization/Demobilization', 1650985.67, [[1, 15], [356, 360]]],
  ['Permits/Licenses', 200000, [[16, 45]]], ['Temporary Facilities', 483840, [[16, 45]]], ['Safety and Health', 302400, [[16, 355]]],
  ['Survey Works', 1669468.37, [[46, 90]]], ['Site Preparations', 6823711.50, [[46, 165]]], ['Roadworks', 10464952.20, [[61, 300]]],
  ['Drainage Works', 6557196.92, [[166, 300]]], ['Water Supply System', 3251701.34, [[166, 300]]],
  ['Electrical System', 415744, [[301, 355]]], ['Housing Construction', 228766486, [[76, 355]]],
].map(([name, amount, bars]) => ({ name, amount, bars })) };
const chartProject = (versions) => ({ dateStarted: '2026-01-01', barChartJson: JSON.stringify(versions), actualAccomplishment: 0 });
const approvedDoc = (documentName, remarks) => ({ documentName, remarks, status: 'Approved' });

test('bar chart target follows the approved chart per day', () => {
  const p = chartProject([sih]);
  assert.ok(Math.abs(L.plannedTarget(p, [], '2026-01-30') - 5.129) < 0.001);
  assert.equal(L.plannedTarget(p, [], '2025-12-31'), 0);
  assert.equal(L.plannedTarget(p, [], '2027-06-01'), 100);
  assert.equal(L.plannedTarget({ dateStarted: '2026-01-01' }, [], '2026-01-30'), null);
  assert.equal(L.plannedTarget({ dateStarted: '2026-01-01', barChartJson: 'oops' }, [], '2026-01-30'), null);
  assert.equal(L.withPlannedTarget(p, [], '2026-01-30').targetAccomplishment, 5.13);
  const done = { ...p, actualAccomplishment: 100, targetAccomplishment: 100 };
  assert.equal(L.withPlannedTarget(done, [], '2026-01-30'), done);
});

test('bar chart card: version in force, contract day, cell and S-curve', () => {
  const v1 = { label: 'Original', effective: '2026-01-01', items: [{ name: 'A', amount: 60, bars: [[1, 30]] }, { name: 'B', amount: 40, bars: [[31, 70]] }] };
  const v2 = { ...v1, label: 'Revision 1', effective: '2026-02-15' };
  const p = chartProject([v2, v1]);
  const plan = L.planOn(p, [], '2026-01-30');
  assert.equal(plan.version.label, 'Original');
  assert.deepEqual([plan.day, plan.plannedPct, plan.versions.length], [30, 60, 2]);
  assert.equal(L.planOn(p, [], '2026-03-01').version.label, 'Revision 1');
  assert.equal(L.planOn({ dateStarted: '2026-01-01' }, [], '2026-01-30'), null);
  assert.deepEqual([L.cellOf(1), L.cellOf(5), L.cellOf(6), L.cellOf(30), L.cellOf(31)], [[1, 5], [1, 5], [1, 10], [1, 30], [2, 5]]);
  assert.equal(L.chartLastDay(v1), 70);
  // B's 40 is spread over 40 days: 30 in month 2, 10 in month 3.
  assert.deepEqual(L.chartMonths(v1).map(m => [m.no, m.monthlyPct, m.cumulativePct]), [[1, 60, 60], [2, 30, 90], [3, 10, 100]]);
});

test('bar chart target: a Partial suspension pauses only the items it names', () => {
  const p = chartProject([{ label: 'Original', effective: '2026-01-01', items: [
    { name: 'A', amount: 50, bars: [[1, 100]] }, { name: 'B', amount: 50, bars: [[1, 100]] }] }]);
  const partial = approvedDoc('Partial Work Suspension Order No. 1', 'Duration: 10 Days | Effectivity: 2026-01-11 | End: 2026-01-21 | Items: A');
  // Day 30: A has 20 days done, B 30.
  assert.equal(L.plannedTarget(p, [partial], '2026-01-30'), 25);
  const continuance = approvedDoc('1st Partial Continuance of Work Suspension Order No. 1', 'Duration: 5 Days | Effectivity: 2026-01-21 | End: 2026-01-26');
  assert.equal(L.plannedTarget(p, [partial, continuance], '2026-01-30'), 22.5);
});

test('bar chart target: Full suspensions freeze it, Partial and pending ones do not', () => {
  const p = chartProject([sih]);
  const full = approvedDoc('Work Suspension Order No. 1', 'Duration: 10 Days | Effectivity: 2026-01-21 | End: 2026-01-31');
  assert.ok(Math.abs(L.plannedTarget(p, [full], '2026-02-09') - 5.129) < 0.001);
  const plain = L.plannedTarget(p, [], '2026-02-09');
  assert.equal(L.plannedTarget(p, [{ ...full, documentName: 'Partial Work Suspension Order No. 1' }], '2026-02-09'), plain);
  assert.equal(L.plannedTarget(p, [{ ...full, status: 'Pending' }], '2026-02-09'), plain);
  const open = approvedDoc('Work Suspension Order No. 1', 'Duration: 0 Days | Effectivity: 2026-01-21');
  const resumed = approvedDoc('Work Resumption Order No. 1', 'Effectivity: 2026-01-31');
  assert.ok(Math.abs(L.plannedTarget(p, [open, resumed], '2026-02-09') - 5.129) < 0.001);
});

test('status: Suspended during an approved Full suspension, back when it ends or work resumes', () => {
  const p = { ...chartProject([sih]), status: 'On-going', actualAccomplishment: 50 };
  const full = approvedDoc('Work Suspension Order No. 1', 'Duration: 30 Days | Effectivity: 2026-01-21 | End: 2026-02-20');
  const resumed = approvedDoc('Work Resumption Order No. 1', 'Effectivity: 2026-02-10');
  assert.equal(L.withSchedule(p, [full], '2026-01-20').status, 'On-going');
  assert.equal(L.withSchedule(p, [full], '2026-02-10').status, 'Suspended');
  assert.equal(L.withSchedule(p, [full, resumed], '2026-02-10').status, 'On-going');
  assert.equal(L.plannedTarget(p, [full, resumed], '2026-02-09'), L.plannedTarget(p, [], '2026-01-20'));
  assert.equal(L.withSchedule(p, [{ ...full, documentName: 'Partial Work Suspension Order No. 1' }], '2026-02-01').status, 'On-going');
  // Jan 30 target is 5.13%: any shortfall is Behind Schedule, but only 10% or more is flagged.
  const at = (actual, status) => L.withSchedule({ ...p, actualAccomplishment: actual, status }, [], '2026-01-30');
  assert.equal(at(5.12, 'Suspended').status, 'Behind Schedule');
  assert.equal(at(5.13, 'Behind Schedule').status, 'On-going');
  assert.equal(L.attentionFlags(at(5.12, 'On-going'), [], 0, '2026-01-30').filter(f => f.kind === 'behind').length, 0);
});

test('completion: approved time extensions decide it, even when the stored revised date lags', () => {
  // 100 days from Jan 1 end Apr 10; 30 approved extension days move it to May 10. The stored date still says Apr 10.
  const p = { ...chartProject([sih]), contractDurationDays: 100, completionDateOriginal: '2026-04-10', completionDateRevised: '2026-04-10', actualAccomplishment: 50 };
  const te = approvedDoc('Time Extension No. 1 - 30 Days', '');
  const on = L.withSchedule(p, [te], '2026-04-20');
  assert.equal(on.completionDateRevised, '2026-05-10');
  assert.equal(L.daysOverdue(on, '2026-04-20'), 0);
  assert.deepEqual(L.attentionFlags(on, [te], 0, '2026-04-20').filter(f => f.kind === 'overdue' || f.kind === 'dueSoon').map(f => f.message), ['Due in 20 days']);
  // Pending orders add nothing; a finished project keeps its stored date (the day it was completed).
  assert.equal(L.withSchedule(p, [{ ...te, status: 'Pending' }], '2026-04-20').completionDateRevised, '2026-04-10');
  assert.equal(L.withSchedule({ ...p, actualAccomplishment: 100, completionDateRevised: '2026-04-02' }, [te], '2026-04-20').completionDateRevised, '2026-04-02');
});

test('needs attention: each flag links to where it is dealt with; chips count what waits', () => {
  const docs = [{ documentName: 'Time Extension No. 1 - 30 Days', status: 'Approved' }, { documentName: 'Variation Order No. 2', status: 'Pending' },
    { documentName: 'Variation Order No. 3', status: 'Submitted' }, { documentName: 'Work Suspension Order No. 1', status: 'Pending' }];
  assert.deepEqual(L.flagTarget({ kind: 'waiting' }, docs), { section: 'documents', chip: 'Variation Orders' });
  assert.deepEqual(L.flagTarget({ kind: 'billingAhead' }), { section: 'payments', chip: null });
  assert.deepEqual(L.flagTarget({ kind: 'issue' }), { section: 'progress', chip: 'Issues' });
  assert.equal(L.chipSlug('Suspension / Resumption'), 'suspension-resumption');
  const issues = [{ isCritical: 1, status: 'Pending', targetDate: '2026-11-01' }, { isCritical: 0, status: 'Pending', targetDate: '2026-11-01' },
    { isCritical: 1, status: 'Resolved', targetDate: '2026-11-01' }, { isCritical: 1, status: 'Pending', targetDate: '2026-12-01' }];
  assert.deepEqual(L.chipCounts(docs, [{ datePackaged: '' }, { datePackaged: '2026-11-02' }], issues),
    { 'Variation Orders': 2, 'Suspension / Resumption': 1, 'Developer Requests': 1, Issues: 3 });
  const flags = L.attentionFlags({ actualAccomplishment: 50, targetAccomplishment: 50 }, [], 0, '2026-11-18', [], '2026-11-17', issues);
  assert.deepEqual(flags.map(f => [f.kind, f.level, f.message]), [['issue', 'critical', '1 critical issue past target date']]);
});

test('bar chart target: a revised chart applies from its effective date', () => {
  const revised = { label: 'Revised No. 1', effective: '2026-03-01', items: [{ name: 'All works', amount: 100, bars: [[1, 100]] }] };
  const p = chartProject([revised, sih]);
  const before = approvedDoc('Work Suspension Order No. 1', 'Duration: 10 Days | Effectivity: 2026-01-21 | End: 2026-01-31');
  assert.ok(Math.abs(L.plannedTarget(p, [before], '2026-02-09') - 5.129) < 0.001);
  assert.equal(L.plannedTarget(p, [before], '2026-03-01'), 60);
  const later = approvedDoc('Work Suspension Order No. 2', 'Duration: 5 Days | Effectivity: 2026-03-02 | End: 2026-03-07');
  assert.equal(L.plannedTarget(p, [before, later], '2026-03-07'), 61);
});

test('suspension End: the effectivity date is day 1, old one-day-late Ends read right', () => {
  assert.equal(L.withEnd('Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24'), 'Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-23');
  assert.equal(L.withEnd('Duration: 10 Days | Effectivity: 2026-12-27'), 'Duration: 10 Days | Effectivity: 2026-12-27 | End: 2027-01-05');
  assert.equal(L.withEnd('Duration: 0 Days | Effectivity: 2026-01-21'), 'Duration: 0 Days | Effectivity: 2026-01-21');
  // Suspended through Aug 23 (the End), going again on Aug 24.
  const ws = approvedDoc('Work Suspension Order No. 1', 'Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24');
  assert.equal(L.isSuspendedOn([ws], '2026-08-23'), true);
  assert.equal(L.isSuspendedOn([ws], '2026-08-24'), false);
});

test('website edits: who may edit, matching firestore.rules', () => {
  const p = { assignedUids: ['me'] };
  assert.equal(L.canEdit(p, 'me', { role: 'FIELD_ENGINEER', approved: true }), true);
  assert.equal(L.canEdit(p, 'me', { role: 'ENGINEER_ADMIN' }), true, 'from before approvals: role, no approved claim');
  assert.equal(L.canEdit(p, 'me', { role: 'FIELD_ENGINEER', approved: false }), false, 'waiting for approval');
  assert.equal(L.canEdit(p, 'me', { role: 'VIEWER', approved: true }), false);
  assert.equal(L.canEdit(p, 'other', { role: 'FIELD_ENGINEER', approved: true, allProjects: true }), false, 'sees all, not assigned');
  assert.equal(L.canEdit(p, 'other', { role: 'SUPER_VIEWER', approved: true }), false);
  assert.equal(L.canEdit(p, 'other', { role: 'SUPER_ADMIN' }), true);
  assert.equal(L.canEdit(p, 'me', {}), false, 'no role yet');
});

test('website edits: the record keeps its other fields and moves to the next revision', () => {
  const stored = { revision: 4, data: JSON.stringify({ date: '2026-10-01', description: 'Old', remarks: 'Keep me', timestamp: 7, loggedBy: 'Ana' }) };
  const out = L.editedRecord(stored, { description: 'New', status: 'Approved' }, { uid: 'me', now: 99, assignedUids: ['me'] });
  assert.deepEqual({ ...out, data: JSON.parse(out.data) }, {
    revision: 5, updatedAt: 99, updatedByUid: 'me', assignedUids: ['me'],
    data: { date: '2026-10-01', description: 'New', remarks: 'Keep me', timestamp: 7, loggedBy: 'Ana', status: 'Approved' },
  });
  assert.throws(() => L.editedRecord({ revision: 1, data: 'oops' }, {}, { uid: 'me', now: 1, assignedUids: [] }), /could not be read/);
  const audit = L.auditRecord({ uuid: 'a1', projectUuid: 'p', assignedUids: ['me'], uid: 'me', now: 99, user: 'Ana', actionType: 'Issue Creation', details: 'd' });
  assert.equal(audit.type, 'audit_logs');
  assert.equal(audit.revision, 1);
  assert.deepEqual(JSON.parse(audit.data), { timestamp: 99, user: 'Ana', device: 'Website', actionType: 'Issue Creation', oldValue: '', newValue: '', details: 'd' });
});

test('website edits: the issue form checks what AddIssueDialog checks', () => {
  const ok = { date: '2026-11-10', description: 'Rain', actionTaken: 'Pumped', targetDate: '' };
  assert.deepEqual(L.issueErrors(ok), []);
  assert.equal(L.issueErrors({ ...ok, description: ' ', actionTaken: '' }).length, 2);
  assert.equal(L.issueErrors({ ...ok, date: '' }).length, 1);
  assert.equal(L.issueErrors({ ...ok, targetDate: '11/10/2026' }).length, 1);
});

test('sign up: name, checks and the profile match the app', () => {
  assert.equal(L.personName(' Juan ', 'c.', ' Dela Cruz'), 'Juan C. Dela Cruz');
  assert.equal(L.personName('Ana', '', 'Reyes'), 'Ana Reyes');
  const f = { firstName: 'Ana', middleInitial: 'b', lastName: 'Reyes', email: ' Ana@Example.PH ', password: 'secret1', confirm: 'secret1', position: ' Engineer II ', office: '', role: 'VIEWER' };
  assert.deepEqual(L.signUpErrors(f), []);
  assert.equal(L.signUpErrors({ ...f, lastName: ' ', email: 'ana', password: '12345', confirm: '12345' }).length, 3);
  assert.deepEqual(L.signUpErrors({ ...f, confirm: 'other' }), ['The two passwords do not match.']);
  assert.deepEqual(L.newProfile('u1', f, 5), { uid: 'u1', email: 'ana@example.ph', updatedAt: 5, displayName: 'Ana B. Reyes', role: 'VIEWER', position: 'Engineer II', office: '' });
  assert.equal(L.newProfile('u1', { ...f, role: 'SUPER_ADMIN' }, 5).role, 'FIELD_ENGINEER');
});

test('users: rows, order and the update for each change match ManageUsersDialog', () => {
  const old = L.userRow('u0', { email: 'old@x.ph', role: 'FIELD_ENGINEER' });
  assert.equal(old.approved, true, 'a profile from before approvals counts as approved');
  assert.equal(old.name, 'old@x.ph');
  const rows = [old, L.userRow('u1', { displayName: 'Zed', approved: false }), L.userRow('u2', { displayName: 'Ana', approved: false, role: 'VIEWER' }),
    L.userRow('me', { displayName: 'Boss', role: 'SUPER_ADMIN' })];
  assert.deepEqual(L.manageableUsers(rows).map(u => u.uid), ['u2', 'u1', 'u0']);
  assert.deepEqual(L.userChange(rows[1], { approved: true }), { approved: true, role: 'FIELD_ENGINEER' });
  assert.deepEqual(L.userChange(rows[2], { approved: true }), { approved: true, role: 'VIEWER' });
  assert.deepEqual(L.userChange(rows[2], { role: 'ENGINEER_ADMIN' }), { role: 'ENGINEER_ADMIN' });
  assert.deepEqual(L.userChange(rows[1], { seesAllProjects: true }), { seesAllProjects: true, role: 'FIELD_ENGINEER' });
  assert.throws(() => L.userChange(rows[1], { role: 'SUPER_ADMIN' }), /cannot be given/);
});

test('website edits: a new issue is the record and audit entry the app uploads', () => {
  const f = { date: '2026-11-10', description: 'Flooded access road near Block 4 gate', actionTaken: 'Asked for a pump', status: 'Pending', isCritical: 1, targetDate: '', actionBy: 'Contractor/Developer' };
  const r = L.newIssueRecord({ uuid: 'i1', projectUuid: 'p', assignedUids: ['me'], uid: 'me', now: 7, loggedBy: 'Engr. Ana' }, f);
  assert.deepEqual({ ...r, data: undefined }, { uuid: 'i1', type: 'project_issues', projectUuid: 'p', assignedUids: ['me'], revision: 1, updatedAt: 7, updatedByUid: 'me', isDeleted: false, data: undefined });
  assert.deepEqual(JSON.parse(r.data), { date: '2026-11-10', description: f.description, actionTaken: 'Asked for a pump', remarks: '', loggedBy: 'Engr. Ana', timestamp: 7, status: 'Pending', isCritical: 1, targetDate: '', actionBy: 'Contractor/Developer' });
  assert.deepEqual(L.newIssueAudit(f), { actionType: 'Issue Creation', details: 'Logged issue: Flooded access road near Block...', oldValue: '',
    newValue: `Description: ${f.description}, Status: Pending, Critical: true` });
});

test('website edits: a new billing is the record the app uploads, with share and balance on the gross', () => {
  const ctx = { uuid: 'b1', projectUuid: 'p', assignedUids: ['me'], uid: 'me', now: 7 };
  // A 10,000,000 contract with 500,000 already billed (gross); this billing is 1,500,000 gross.
  const earlier = [{ grossAmount: 100000 }, { grossAmount: '400000' }];
  const f = { name: ' 3rd Progress Billing ', dvNo: 'DV-1', date: '2026-11-10', periodCovered: 'November 1 - 30, 2026', billingType: '', grossAmount: '1,500,000', units: '', unitPrice: '', retentionPct: '10' };
  const r = L.newPaymentRecord(ctx, f, earlier, 10_000_000);
  assert.deepEqual({ ...r, data: undefined }, { uuid: 'b1', type: 'project_payments', projectUuid: 'p', assignedUids: ['me'], revision: 1, updatedAt: 7, updatedByUid: 'me', isDeleted: false, data: undefined });
  assert.deepEqual(JSON.parse(r.data), { name: '3rd Progress Billing', dvNo: 'DV-1', date: '2026-11-10', periodCovered: 'November 1 - 30, 2026', grossAmount: 1500000,
    percentage: 15, balanceAmount: 8000000, balancePercentage: 80, fileUrl: '', billingType: '', units: 0, unitPrice: 0, retentionPct: 10 });
  assert.deepEqual(L.newPaymentAudit(r), { actionType: 'Payment Creation', details: 'Created payment billing: 3rd Progress Billing (DV-1)', oldValue: '',
    newValue: 'Billing: 3rd Progress Billing, DV: DV-1, Gross: ₱1500000, Retention: 10%' });
  // A housing billing: units × amount per unit, named after its type when the name is blank.
  const unit = { ...f, name: '', billingType: 'M1 - Housing unit', units: '5', unitPrice: '800,000.00', grossAmount: '' };
  assert.equal(L.billingGross(unit), 4000000);
  assert.equal(L.billingGross({ ...unit, units: '3', unitPrice: '123,456.78' }), 370370.34, 'saved to the centavo');
  assert.deepEqual(L.billingErrors(unit), []);
  const d = JSON.parse(L.newPaymentRecord(ctx, unit, [], 0).data);
  assert.deepEqual([d.name, d.units, d.unitPrice, d.percentage, d.balanceAmount, d.balancePercentage], ['M1 - Housing unit', 5, 800000, 0, -4000000, 0]);
  // Checks, as the phone's form gives them.
  assert.deepEqual(L.billingErrors({ ...f, grossAmount: '' }), ['Type the gross amount.']);
  assert.deepEqual(L.billingErrors({ ...f, grossAmount: '12a', date: '' }), ['Pick the date.', 'The gross amount must be a number.']);
  assert.deepEqual(L.billingErrors({ ...unit, units: '2.5', unitPrice: '' }), ['The number must be a whole number.', 'Type the amount per housing unit.']);
  assert.deepEqual(L.billingErrors({ ...unit, billingType: 'Land acquisition', units: '' }), ['Type the number of lots.']);
  // Land development is billed by the amount accomplished; its lots are worked out from the amount per lot.
  const dev = { ...f, name: '', billingType: 'Land development', grossAmount: '622,031.70', units: '', unitPrice: '86000' };
  assert.equal(L.billingGross(dev), 622031.7);
  assert.deepEqual(L.billingErrors(dev), []);
  const saved = JSON.parse(L.newPaymentRecord(ctx, dev, [], 0).data);
  assert.deepEqual([saved.name, saved.units, saved.unitPrice, saved.grossAmount, L.billedUnits(saved)], ['Land development', 0, 86000, 622031.7, 7.23]);
  assert.deepEqual(L.billingErrors({ ...dev, grossAmount: '', unitPrice: '' }), ['Type the gross amount.', 'Type the amount per lot.']);
  assert.equal(L.billedUnits({ units: 5, unitPrice: 800000, grossAmount: 4000000 }), 5);
  assert.equal(L.parseAmount(' 1,234.5 '), 1234.5);
  assert.equal(L.parseAmount('-5'), null);
  assert.equal(L.monthPeriod('2026-02-14'), 'February 1 - 28, 2026');
  assert.equal(L.monthPeriod('2026-11-11'), 'November 1 - 30, 2026');
});

test('weather edit: the form reads a report\'s week, and only changed days are rewritten', () => {
  const report = { reportingWeek: 'November 01, 2026 - November 07, 2026', _weather: [
    { date: '2026-11-01', dayOfWeek: 'Sunday', hourlyConditionsCsv: Array(24).fill('CLOUDY').join(',') },
    { date: '2026-11-02', dayOfWeek: 'Monday', hourlyConditionsCsv: 'RAIN SHOWERS,oops' },
  ] };
  const days = L.weatherForm(report);
  assert.equal(days.length, 7);
  assert.equal(days[0].date, '2026-11-01');
  assert.deepEqual([days[0].logged, days[0].hours[5]], [true, 'CLOUDY']);
  assert.deepEqual(days[1].hours.slice(0, 3), ['RAIN_SHOWERS', 'FAIR', 'FAIR'], 'old spellings and unknown hours read like the clock');
  assert.deepEqual([days[2].logged, days[2].hours[0]], [false, 'FAIR']);
  assert.equal(L.weatherChanges(report, days, new Set()), null);
  days[2].hours[14] = 'STORMY';
  const out = L.weatherChanges(report, days, new Set(['2026-11-03']))._weather;
  assert.deepEqual(out.map(w => w.date), ['2026-11-01', '2026-11-02', '2026-11-03']);
  assert.equal(out[1], report._weather[1], 'an untouched day stays exactly as stored');
  assert.deepEqual(out[2], { date: '2026-11-03', dayOfWeek: 'Tuesday', hourlyConditionsCsv: [...Array(14).fill('FAIR'), 'STORMY', ...Array(9).fill('FAIR')].join(',') });
});

test('weather presets match the app\'s (Reports.kt WEATHER_PRESETS)', () => {
  assert.deepEqual(L.WEATHER_PRESETS.map(([n]) => n), ['All Fair', 'All Cloudy', 'Cloudy AM / Showers PM', 'All Rainy', 'Rainy AM / Stormy PM', 'All Stormy']);
  for (const [n, hours] of L.WEATHER_PRESETS) {
    assert.equal(hours.length, 24, n);
    assert.ok(hours.every(h => L.WEATHER_KEYS.includes(h)), n);
  }
  const [, rs] = L.WEATHER_PRESETS[4];
  assert.deepEqual([rs[0], rs[11], rs[12], rs[23]], ['RAINY', 'RAINY', 'STORMY', 'STORMY']);
});

test('developer request edits: checks and the app audit entry', () => {
  assert.deepEqual(L.requestErrors({ dateReceived: '' }), ['Pick the date received by NHA.']);
  assert.deepEqual(L.requestErrors({ dateReceived: '2026-11-01', dateRequested: '', datePackaged: '' }), []);
  assert.deepEqual(L.requestErrors({ dateReceived: '2026-11-01', datePackaged: 'soon' }), ['Pick a valid date packaged.']);
  // A request for billing needs its date, what is billed and a whole number of lots or units.
  const bill = { requestType: L.BILLING_REQUEST, dateReceived: '2026-11-01', dateRequested: '2026-10-30', billingType: 'Land acquisition', units: 12 };
  assert.deepEqual(L.requestErrors(bill), []);
  assert.deepEqual(L.requestErrors({ ...bill, dateRequested: '', billingType: '', units: 0 }),
    ['Pick the date of the request.', 'Pick what the billing is for.', 'Type the number of housing units, a whole number.']);
  assert.deepEqual(L.requestErrors({ ...bill, units: 1.5 }), ['Type the number of lots, a whole number.']);
  assert.equal(L.billingRequestText(bill), '12 lots · Land acquisition');
  assert.equal(L.billingRequestText({ units: 1, billingType: 'M2 - Housing unit' }), '1 housing unit · M2 - Housing unit');
  // It waits under Payments until a billing is saved from it; one whose billing was deleted waits again.
  const asked = [{ ...bill, uuid: 'r1', paymentUuid: '' }, { ...bill, uuid: 'r2', paymentUuid: 'p1' }, { ...bill, uuid: 'r3', paymentUuid: 'gone' }, { requestType: 'Time Extension', uuid: 'r4' }];
  assert.deepEqual(L.pendingBillingRequests(asked, [{ uuid: 'p1' }]).map(r => r.uuid), ['r1', 'r3']);
  assert.deepEqual(L.billedRequestErrors({ dvNo: ' ' }, false), ['Type the DV No.', 'Attach the scanned DV form.']);
  assert.deepEqual(L.billedRequestErrors({ dvNo: 'DV-1' }, true), []);
  const f = { requestType: 'Time Extension', details: 'Letter 12', dateReceived: '2026-11-01', datePackaged: '' };
  assert.equal(L.requestAudit(f).details, 'Time Extension request received 2026-11-01');
  assert.deepEqual(L.requestAudit({ ...f, datePackaged: '2026-11-12' }), {
    actionType: 'Developer Request', details: 'Time Extension request received 2026-11-01, packaged 2026-11-12', oldValue: '', newValue: 'Letter 12',
  });
});

test('contractor letter edits: checks and the app audit entry', () => {
  assert.deepEqual(L.letterErrors({ dateSent: '', subject: ' ' }), ['Pick the date of the letter.', 'Type the subject.']);
  assert.deepEqual(L.letterErrors({ dateSent: '2026-10-27', subject: 'Manpower', dateReceived: '', dateReplied: '' }), []);
  assert.deepEqual(L.letterErrors({ dateSent: '2026-10-27', subject: 'Manpower', dateReplied: 'soon' }), ['Pick a valid date of reply.']);
  const f = { dateSent: '2026-10-27', subject: 'Manpower', actionTaken: 'Added 10 workers', dateReplied: '' };
  assert.equal(L.letterAudit(f).details, 'Letter of 2026-10-27: Manpower');
  assert.deepEqual(L.letterAudit({ ...f, dateReplied: '2026-11-03' }), {
    actionType: 'Letter to Contractor', details: 'Letter of 2026-10-27: Manpower, replied 2026-11-03', oldValue: '', newValue: 'Added 10 workers',
  });
});

test('a new contractor letter is the record a phone uploads', () => {
  const f = { dateSent: '2026-11-12', subject: 'Catch-up plan', actionTaken: '', dateReplied: '', dateReceived: '2026-11-13' };
  const r = L.newLetterRecord({ uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'a', now: 9 }, f);
  assert.deepEqual({ ...r, data: undefined }, { uuid: 'u1', type: 'contractor_letters', projectUuid: 'p1', assignedUids: ['a'], revision: 1, updatedAt: 9, updatedByUid: 'a', isDeleted: false, data: undefined });
  assert.deepEqual(JSON.parse(r.data), { dateSent: '2026-11-12', subject: 'Catch-up plan', actionTaken: '', dateReplied: '', replyFileUrl: '', dateReceived: '2026-11-13', receivedFileUrl: '' });
});

test('attachments: a new reference has the phone\'s shape and a scan is never enlarged', () => {
  const refText = L.newFileRef('11111111-1111-4111-8111-111111111111', '9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b', 'pdf');
  assert.equal(refText, 'nhafile:11111111-1111-4111-8111-111111111111/9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b.pdf');
  assert.equal(L.storagePathOf(refText), 'attachments/11111111-1111-4111-8111-111111111111/9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b.pdf');
  assert.deepEqual(L.scaledSize(5000, 2500, 2500), [2500, 1250]);
  assert.deepEqual(L.scaledSize(3000, 4000, 2500), [1875, 2500]);
  assert.deepEqual(L.scaledSize(800, 600, 2500), [800, 600]);
  const ids = { uuid: 'u', projectUuid: 'p', assignedUids: [], uid: 'a', now: 1 };
  const letter = { dateSent: '2026-11-12', subject: 'S', actionTaken: '', dateReplied: '', dateReceived: '', receivedFileUrl: refText };
  assert.equal(JSON.parse(L.newLetterRecord(ids, letter).data).receivedFileUrl, refText);
  assert.equal(JSON.parse(L.newLetterRecord(ids, letter).data).replyFileUrl, '');
  const billing = { name: 'B', dvNo: '', date: '2026-11-12', periodCovered: '', billingType: '', grossAmount: '100', retentionPct: '0' };
  assert.equal(JSON.parse(L.newPaymentRecord(ids, { ...billing, fileUrl: refText }, [], 1000).data).fileUrl, refText);
  assert.equal(JSON.parse(L.newPaymentRecord(ids, billing, [], 1000).data).fileUrl, '');
});

test('deleting a letter or a developer request writes the app audit entry', () => {
  assert.deepEqual(L.deletedLetterAudit({ dateSent: '2026-10-27', subject: 'Manpower', actionTaken: 'Added 10 workers' }),
    { actionType: 'Letter to Contractor', details: 'Deleted letter of 2026-10-27: Manpower', oldValue: 'Added 10 workers', newValue: '' });
  assert.deepEqual(L.deletedRequestAudit({ requestType: 'Time Extension', dateReceived: '2026-11-02', details: '30 days for rain' }),
    { actionType: 'Developer Request', details: 'Deleted Time Extension request received 2026-11-02', oldValue: '30 days for rain', newValue: '' });
  assert.equal(L.deletedRequestAudit({ requestType: 'Variation Order', dateReceived: '2026-07-03' }).oldValue, '');
});

test('weekly report photos: titles, removals and additions, and the project card photo', () => {
  const a = 'nhafile:p/a.jpg', b = 'nhafile:p/b.jpg', n = 'nhafile:p/n.jpg';
  const report = {
    attachedPhotoUrlsJson: JSON.stringify([a, b]),
    photoDetailsJson: JSON.stringify({ [a]: { title: 'Old', takenAt: 'Nov 1, 2026 8:00 AM', lat: 14.8, lng: 120.9 }, [b]: { title: 'Gate', takenAt: '' } }),
  };
  const out = L.editedPhotos(report, { titles: { [a]: 'Slab pour', [b]: 'Gate' }, removed: [b], added: [[n, 'New wall']] });
  assert.deepEqual(JSON.parse(out.attachedPhotoUrlsJson), [a, n]);
  assert.deepEqual(JSON.parse(out.photoDetailsJson), {
    [a]: { title: 'Slab pour', takenAt: 'Nov 1, 2026 8:00 AM', lat: 14.8, lng: 120.9 },
    [n]: { title: 'New wall', takenAt: '' },
  });
  // A report saved by an old version (one bare reference, no details) still edits.
  const old = L.editedPhotos({ attachedPhotoUrlsJson: a, photoDetailsJson: '' }, { titles: { [a]: '' }, added: [[n, '']] });
  assert.deepEqual(JSON.parse(old.attachedPhotoUrlsJson), [a, n]);
  assert.deepEqual(JSON.parse(old.photoDetailsJson), { [n]: { title: '', takenAt: '' } });
  const weekly = [
    { createdAtTimestamp: 1, attachedPhotoUrlsJson: JSON.stringify([a]) },
    { createdAtTimestamp: 3, attachedPhotoUrlsJson: '[]' },
    { createdAtTimestamp: 2, attachedPhotoUrlsJson: JSON.stringify([b, n]) },
  ];
  assert.equal(L.latestSitePhoto(weekly), b);
  assert.equal(L.latestSitePhoto([{ createdAtTimestamp: 1, attachedPhotoUrlsJson: '[]' }]), '');
});

test('a figure is an amount, a percentage or a number on its own', () => {
  for (const v of ['₱30,000,000.00', '-₱500.00', '41.25%', '400', 0, ' 7.50% ']) assert.equal(L.isFigure(v), true, String(v));
  for (const v of ['No billings yet', '540 days', 'Jan 5, 2026', '2026-10-15', '77.52% · Month 11', '', null, '-']) assert.equal(L.isFigure(v), false, String(v));
});

test('missing photos: the reports that list one Storage could not give, with how many', () => {
  const a = 'nhafile:p/a.jpg', b = 'nhafile:p/b.jpg', n = 'nhafile:p/n.jpg';
  const weekly = [
    { uuid: 'w1', attachedPhotoUrlsJson: '["nhafile:p\\/a.jpg","nhafile:p\\/b.jpg"]' }, // as a phone saves the list
    { uuid: 'w2', attachedPhotoUrlsJson: JSON.stringify([n]) },
    { uuid: 'w3', attachedPhotoUrlsJson: '[]' },
    { uuid: 'w4', attachedPhotoUrlsJson: JSON.stringify([b]) },
  ];
  assert.deepEqual(L.missingPhotos(weekly, new Set([a, b])).map(x => [x.report.uuid, x.count]), [['w1', 2], ['w4', 1]]);
  assert.deepEqual(L.missingPhotos(weekly, new Set()), []);
});

test('new developer request: the order it files is named and worded as the phone does', () => {
  const blank = { wsNo: '', wsType: 'Full', wsDays: '', wsEffectivity: '', wsReason: '', wsItems: [], coType: 'Full', coDays: '', coEffectivity: '',
    wrNo: '', wrType: 'Full', wrEffectivity: '', wrRemarks: '', voNo: '', voType: 'Additive', voAmount: '', voReason: '', voDateApproved: '', teNo: '', teDays: '', teReason: '' };
  const [ws, co, wr, vo, te] = L.REQUEST_TYPES;
  const s1 = { uuid: 's1', documentName: 'Work Suspension No. 1 (Partial)', status: 'Approved', remarks: 'Duration: 10 Days | Effectivity: 2026-08-01 | End: 2026-08-11' };
  const docs = [
    { uuid: 's2', documentName: 'Work Suspension Order No. 2', status: 'Pending', remarks: '' }, s1,
    { uuid: 'c1', documentName: '1st Partial Continuance of Work Suspension Order No. 1', status: 'Pending', remarks: 'Duration: 5 Days | Effectivity: 2026-08-11' },
    { uuid: 't1', documentName: 'Time Extension No. 1 - 4 Days', status: 'Pending', remarks: 'Reason: Work suspension' },
  ];
  // Day 1 is the effectivity date, so 10 days from Aug 1 end Aug 10 and the continuance's 5 from Aug 11 end Aug 15.
  assert.deepEqual(L.suspensionsByNo(docs).map(d => d.uuid), ['s1', 's2']);
  assert.deepEqual(L.nextContinuance(s1, docs, 'Partial'), ['2nd Partial Continuance of Work Suspension Order No. 1', '2026-08-16']);
  assert.deepEqual(L.nextContinuance(docs[0], docs, 'Full'), ['1st Continuance of Work Suspension Order No. 2', '']);
  // Continuances list 1st to last whatever order the cloud gives them in, old names included.
  const later = [{ documentName: '3rd Continuance of Work Suspension Order No. 1' }, { documentName: 'Continuance No. 2 of Work Suspension No. 1 (Partial)' }, ...docs];
  assert.deepEqual(L.continuances(s1, later).map(d => parseInt(L.orderLabel(d.documentName), 10)), [1, 2, 3]);
  assert.equal(L.suspensionExtensionDays(docs), 6); // 10 suspended days, 4 already claimed
  assert.deepEqual(L.chartItemNames({ barChartJson: JSON.stringify([{ effective: '2026-01-01', items: [{ name: 'Roads' }, { name: 'Drainage' }] }]) }), ['Roads', 'Drainage']);
  assert.deepEqual(L.chartItemNames({}), []);

  assert.deepEqual(L.orderErrors(ws, blank), ['Type the number of days, in whole days.']);
  assert.deepEqual(L.orderErrors(te, { ...blank, teDays: '2.5' }), ['Type the number of days, in whole days.']);
  assert.deepEqual(L.orderErrors(co, blank, undefined), ['No work suspension order is logged yet. Log the suspension first.']);
  assert.deepEqual(L.orderErrors(co, { ...blank, coDays: '5' }, s1), ['Pick the effectivity date.']);
  assert.deepEqual(L.orderErrors(vo, { ...blank, voAmount: 'abc' }), ['Type a valid amount.']);
  assert.deepEqual([L.orderErrors(vo, blank), L.orderErrors(wr, blank), L.orderErrors(te, { ...blank, teDays: ' 30 ' })], [[], [], []]);

  assert.deepEqual(L.orderDocument(ws, { ...blank, wsNo: ' 3 ', wsType: 'Partial', wsDays: '10', wsEffectivity: '2026-11-01', wsReason: ' Flooding ', wsItems: ['Roads', 'Drainage'] }), {
    documentName: 'Partial Work Suspension Order No. 3', remarks: 'Duration: 10 Days | Effectivity: 2026-11-01 | End: 2026-11-10 | Reason: Flooding | Items: Roads; Drainage',
  });
  // A Full suspension names no items, and no effectivity date leaves no End.
  assert.deepEqual(L.orderDocument(ws, { ...blank, wsNo: '4', wsDays: '7', wsItems: ['Roads'] }), { documentName: 'Work Suspension Order No. 4', remarks: 'Duration: 7 Days | Effectivity: ' });
  assert.deepEqual(L.orderDocument(co, { ...blank, coType: 'Partial', coDays: '5', coEffectivity: '2026-08-16' }, s1, docs),
    { documentName: '2nd Partial Continuance of Work Suspension Order No. 1', remarks: 'Duration: 5 Days | Effectivity: 2026-08-16 | End: 2026-08-20' });
  assert.deepEqual(L.orderDocument(wr, { ...blank, wrNo: '1', wrType: 'Partial', wrEffectivity: '2026-08-21', wrRemarks: 'Cleared' }),
    { documentName: 'Partial Work Resumption Order No. 1', remarks: 'Effectivity: 2026-08-21 | Remarks: Cleared' });
  const filed = L.orderDocument(vo, { ...blank, voNo: '2', voType: 'Deductive', voAmount: '1,500,000 .50', voReason: 'Less fill', voDateApproved: '2026-11-05' });
  assert.deepEqual(filed, { documentName: 'Variation Order No. 2 (Deductive) - PHP 1,500,000.50', remarks: 'Reason: Less fill | Date Approved: 2026-11-05' });
  assert.deepEqual(L.orderDocument(te, { ...blank, teNo: '2', teDays: ' 30 ', teReason: 'Rain' }), { documentName: 'Time Extension No. 2 - 30 Days', remarks: 'Reason: Rain' });

  const ids = { uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'a', now: 5 };
  const stamps = { uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], revision: 1, updatedAt: 5, updatedByUid: 'a', isDeleted: false };
  assert.deepEqual(L.newRequestRecord(ids, { requestType: vo, details: 'Letter 12', dateRequested: '', dateReceived: '2026-11-12', datePackaged: '' }), {
    ...stamps, type: 'developer_requests',
    data: '{"requestType":"Variation Order","details":"Letter 12","dateRequested":"","dateReceived":"2026-11-12","datePackaged":"","requestFileUrl":"","billingType":"","units":0,"paymentUuid":""}',
  });
  assert.equal(L.newRequestRecord(ids, { requestType: L.BILLING_REQUEST, details: '', dateRequested: '2026-11-10', dateReceived: '2026-11-12', datePackaged: '', billingType: 'M1 - Housing unit', units: 12 }).data,
    '{"requestType":"Request for billing","details":"","dateRequested":"2026-11-10","dateReceived":"2026-11-12","datePackaged":"","requestFileUrl":"","billingType":"M1 - Housing unit","units":12,"paymentUuid":""}');
  assert.deepEqual(L.newDocumentRecord(ids, filed), {
    ...stamps, type: 'pending_documents',
    data: '{"documentName":"Variation Order No. 2 (Deductive) - PHP 1,500,000.50","status":"Pending","remarks":"Reason: Less fill | Date Approved: 2026-11-05","isCoreChecklist":0,"fileUrl":""}',
  });
  assert.deepEqual(L.addedDocumentAudit('Time Extension No. 2 - 30 Days'), {
    actionType: 'Document Upload', details: 'Added document checklist item: Time Extension No. 2 - 30 Days', oldValue: '', newValue: 'Document: Time Extension No. 2 - 30 Days, Status: Pending',
  });
});

test('developer request title: a number in the details becomes the order number', () => {
  const t = (requestType, details) => L.requestTitle({ requestType, details });
  assert.equal(t('Work Resumption Order', '1'), 'Work Resumption Order No. 1');
  assert.equal(t('Work Suspension Order', ' 2 '), 'Work Suspension Order No. 2');
  assert.equal(t('Continuance of Work Suspension Order', '1.1'), '1st Continuance of Work Suspension Order No. 1');
  assert.equal(t('Continuance of Work Suspension Order', '2.3'), '3rd Continuance of Work Suspension Order No. 2');
  assert.equal(t('Continuance of Work Suspension Order', '1'), 'Continuance of Work Suspension Order No. 1');
  assert.equal(t('Time Extension', '30 days for rain'), 'Time Extension');
  assert.equal(t('Variation Order', '1.1'), 'Variation Order');
  assert.equal(t('Variation Order', ''), 'Variation Order');
});

test('editing a billing: fields only, or a changed gross that moves its own and later balances', () => {
  const cost = 1000, pay = (uuid, grossAmount, balanceAmount) => ({ uuid, name: uuid, dvNo: 'DV', grossAmount, percentage: grossAmount / 10, balanceAmount, balancePercentage: balanceAmount / 10, fileUrl: 'scan' });
  const payments = [pay('a', 100, 900), pay('b', 200, 700), pay('c', 300, 400)];
  const form = { name: ' B fixed ', dvNo: ' DV-2 ', date: '2026-11-12', periodCovered: 'Nov', billingType: '', grossAmount: '200', retentionPct: '10' };
  const same = L.editedBillings(payments, payments[1], form, cost);
  assert.equal(same.length, 1);
  assert.deepEqual(same[0][1], { name: 'B fixed', dvNo: 'DV-2', date: '2026-11-12', periodCovered: 'Nov', grossAmount: 200, billingType: '', units: 0, unitPrice: 0, retentionPct: 10 });
  const moved = L.editedBillings(payments, payments[1], { ...form, grossAmount: '250' }, cost);
  assert.deepEqual(moved.map(([p]) => p.uuid), ['b', 'c']);
  assert.deepEqual([moved[0][1].grossAmount, moved[0][1].percentage, moved[0][1].balanceAmount, moved[0][1].balancePercentage], [250, 25, 650, 65]);
  assert.deepEqual(moved[1][1], { balanceAmount: 350, balancePercentage: 35 });
  assert.deepEqual(L.editedPaymentAudit({ name: 'B fixed', dvNo: 'DV-2', grossAmount: 250 }),
    { actionType: 'Payment Creation', details: 'Updated billing: B fixed', oldValue: 'Previous payment info', newValue: 'DV: DV-2, Gross: ₱250' });
});

test('deleting a billing: the tombstone, later balances given back, and the audit entry', () => {
  const pay = (uuid, grossAmount, balanceAmount) => ({ uuid, name: uuid, dvNo: 'DV', grossAmount, balanceAmount, balancePercentage: balanceAmount / 10 });
  const payments = [pay('a', 100, 900), pay('b', 200, 700), pay('c', 300, 400)];
  const edits = L.deletedBillingEdits(payments, payments[1], 1000);
  assert.deepEqual(edits.map(([p]) => p.uuid), ['b', 'c']);
  assert.equal(edits[0][1], null);
  assert.deepEqual(edits[1][1], { balanceAmount: 600, balancePercentage: 60 });
  assert.deepEqual(L.deletedBillingEdits(payments, payments[2], 1000).map(([p]) => p.uuid), ['c']);
  assert.deepEqual(L.deletedRecord({ revision: 3, data: '{}' }, { uid: 'u', now: 5, assignedUids: ['u'] }),
    { isDeleted: true, revision: 4, updatedAt: 5, updatedByUid: 'u', assignedUids: ['u'] });
  assert.deepEqual(L.deletedPaymentAudit(payments[1]),
    { actionType: 'Payment Deletion', details: 'Deleted billing: b (DV)', oldValue: 'DV: DV, Gross: ₱200.00', newValue: '' });
});

test('order status: the remarks keep their schedule fields, the End moves the days, and the audit entry is the phone\'s', () => {
  // The phone's own cases (OrderFixesTest, ProjectScheduleTest).
  const ws = 'Duration: 10 Days | Effectivity: 2026-06-01 | End: 2026-06-11';
  assert.equal(L.keepOrderFields(ws, ws), ws);
  assert.equal(L.keepOrderFields(ws, 'Typhoon'), `${ws} | Typhoon`);
  assert.equal(L.keepOrderFields(ws, 'Duration: 12 Days'), 'Effectivity: 2026-06-01 | End: 2026-06-11 | Duration: 12 Days');
  assert.equal(L.keepOrderFields('Reason: Added culvert | Date Approved: 2026-06-02', 'Reason: Bigger culvert'), 'Date Approved: 2026-06-02 | Reason: Bigger culvert');
  assert.equal(L.keepOrderFields('Reason: Rain', 'Note'), 'Note');
  assert.equal(L.keepOrderFields(`${ws} | Reason: ROW | Items: Land Acquisition; Roadworks`, ''), `${ws} | Items: Land Acquisition; Roadworks`);
  assert.equal(L.keepOrderFields(undefined, ' Note '), 'Note');
  const old = 'Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24 | Reason: ROW';
  assert.equal(L.suspensionEnd(old), '2026-08-23');
  assert.equal(L.suspensionEnd('Reason: Rain'), '');
  assert.equal(L.withEndDate(old, '2026-09-02'), 'Duration: 100 Days | Effectivity: 2026-05-26 | End: 2026-09-02 | Reason: ROW');
  assert.equal(L.withEndDate(old, '2026-05-01'), old);
  assert.equal(L.withEndDate(old, ''), old);
  const order = { documentName: 'Work Suspension Order No. 1', status: 'Pending' };
  assert.deepEqual(L.orderStatusAudit(order, 'Approved'),
    { actionType: 'Document Upload', details: 'Updated document status for Work Suspension Order No. 1', oldValue: 'Status: Pending', newValue: 'Status: Approved' });
  assert.equal(L.orderStatusAudit(order, 'Pending'), null);
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER', undefined].map(role => L.canApprove({ role })), [true, true, false, false, false]);
  assert.equal(L.ORDER_STATUSES.at(-1), 'Approved');
});

test('related file: named, worded and logged as the phone does', () => {
  const other = L.RELATED_TYPES.at(-1);
  assert.deepEqual(L.relatedDocument({ docType: 'Notice to Proceed', title: 'ignored', reference: 'Ref: NHA-2026-001', status: 'Signed', remarks: 'Original' }),
    { documentName: 'Notice to Proceed', status: 'Signed', remarks: 'Ref: NHA-2026-001 | Original' });
  assert.deepEqual(L.relatedDocument({ docType: other, title: ' ECC ', reference: ' ', status: 'Pending', remarks: 'DENR' }), { documentName: 'ECC', status: 'Pending', remarks: 'DENR' });
  assert.equal(L.relatedDocument({ docType: other, title: ' ', reference: '', status: 'Pending', remarks: '' }).documentName, '');
  // The form refuses a title every client would read as an order; the types it offers are never one.
  const titled = title => L.relatedDocument({ docType: other, title, reference: '', status: 'Approved', remarks: '' }).documentName;
  assert.deepEqual(['Request Letter for Time Extension', 'variation order memo', 'WORK SUSPENSION notice', 'Work Resumption letter', 'ECC', 'Extension of time'].map(t => L.isOrderName(titled(t))),
    [true, true, true, true, false, false]);
  assert.equal(L.RELATED_TYPES.some(L.isOrderName), false);
  const ids = { uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'a', now: 7 };
  const record = L.newDocumentRecord(ids, { documentName: 'ECC', status: 'Approved', remarks: 'DENR', fileUrl: 'nhafile:attachments/p1/x.pdf' });
  assert.equal(record.type, 'pending_documents');
  assert.deepEqual(JSON.parse(record.data), { documentName: 'ECC', status: 'Approved', remarks: 'DENR', isCoreChecklist: 0, fileUrl: 'nhafile:attachments/p1/x.pdf' });
  // An order still starts Pending with no file.
  assert.deepEqual(JSON.parse(L.newDocumentRecord(ids, { documentName: 'Time Extension No. 1 - 4 Days', remarks: 'Reason: Rain' }).data),
    { documentName: 'Time Extension No. 1 - 4 Days', status: 'Pending', remarks: 'Reason: Rain', isCoreChecklist: 0, fileUrl: '' });
  assert.deepEqual(L.relatedAddedAudit('ECC', 'nhafile:attachments/p1/x.pdf'),
    { actionType: 'Document Upload', details: 'Uploaded project document: ECC', oldValue: '', newValue: 'Doc: ECC, URL: nhafile:attachments/p1/x.pdf' });
  assert.deepEqual(L.fileAttachedAudit({ documentName: 'ECC' }, 'nhafile:attachments/p1/y.jpg'),
    { actionType: 'Document Upload', details: 'Uploaded file attachment for ECC', oldValue: '', newValue: 'nhafile:attachments/p1/y.jpg' });
  assert.deepEqual(L.RELATED_STATUSES, ['Approved', 'Notarized', 'Signed', 'Submitted', 'Pending']);
});

test('lot form: the progress, photos and billing it saves, and the audit entries', () => {
  assert.deepEqual([0, 1, 20, 21, 80, 81, 99, 100].map(L.statusForProgress), ['Not Started', 'Layout / Excavation Started', 'Layout / Excavation Started',
    'Under Construction', 'Under Construction', 'Substantially Completed', 'Substantially Completed', 'Completed']);
  // The band the lot is already in keeps its exact %; another band saves its top; a road lot is 0, 50 or 100.
  assert.equal(L.pickedProgress(40, 2), 40);
  assert.equal(L.pickedProgress(40, 3), 75);
  assert.equal(L.pickedProgress(0, 5), 100);
  assert.equal(L.pickedProgress(30, 1, true), 30);
  assert.equal(L.pickedProgress(30, 2, true), 100);
  assert.equal(L.pickedProgress(100, 1, true), 50);

  const who = { user: 'Engr. Web', at: '2026-11-12 14:05', contractor: 'ABC Builders' };
  const lot = { uuid: 'lot-1', _ref_sdpPlanId: 'plan-1', blockNumber: '3', lotNumber: '12', progress: null };
  const blank = { progress: 0, drainage: null, constructionStatus: 'Not Started', currentActivity: '', remarks: '', photos: [],
    billing: [{ billed: false, reference: '', remarks: '' }, { billed: false, reference: '', remarks: '' }] };
  assert.equal(L.lotSave(lot, blank, who), null);

  // A lot's first update: the progress part only, with the project's contractor.
  const first = L.lotSave(lot, { ...blank, progress: 50, constructionStatus: 'Under Construction', currentActivity: 'Walls', photos: ['nhafile:p/a.jpg'] }, who);
  assert.deepEqual(first.changes, {
    physicalProgress: 50, constructionStatus: 'Under Construction', currentActivity: 'Walls', remarks: '', photosJson: '["nhafile:p/a.jpg"]',
    contractor: 'ABC Builders', createdBy: 'Engr. Web', createdDate: '2026-11-12 14:05', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-12 14:05',
  });
  assert.deepEqual(first.audits, [{
    actionType: 'Construction Progress Update', details: 'Updated Lot Progress (Block 3 Lot 12) to 50% (Under Construction)',
    oldValue: 'Not Started', newValue: 'Progress: 50%, Status: Under Construction, Activity: Walls',
  }]);
  const record = L.newLotProgressRecord({ uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'a', now: 7 }, lot, first.changes);
  assert.equal(record.type, 'sdp_lot_progress');
  const data = JSON.parse(record.data);
  assert.equal(data._ref_sdpPlanId, 'plan-1');
  assert.equal(data._ref_sdpLotId, 'lot-1');
  assert.equal(data.billingStatus, 'NOT BILLED');
  assert.equal(data.m2BillingStatus, 'NOT BILLED');
  assert.equal(data.physicalProgress, 50);
  assert.equal(data.startDate, '');

  // Billing only, M1 then M2 in one save: the progress columns and the first author are left alone.
  const stored = { physicalProgress: 50, constructionStatus: 'Under Construction', currentActivity: 'Walls', remarks: '', photosJson: '["nhafile:p\/a.jpg"]',
    contractor: 'XYZ', createdBy: 'Engr. Phone', createdDate: '2026-10-01 08:00', billingStatus: 'NOT BILLED', m2BillingStatus: 'NOT BILLED' };
  const same = { ...blank, progress: 50, constructionStatus: 'Under Construction', currentActivity: 'Walls', photos: ['nhafile:p/a.jpg'] };
  assert.equal(L.lotSave({ ...lot, progress: stored }, same, who), null);
  const bill = L.lotSave({ ...lot, progress: stored }, { ...same, billing: [{ billed: true, reference: ' DV-7 ', remarks: ' ok ' }, { billed: true, reference: '', remarks: '' }] }, who);
  assert.deepEqual(bill.changes, {
    billingStatus: 'BILLED', billingDate: '2026-11-12 14:05', billedBy: 'Engr. Web', billingReference: 'DV-7', billingRemarks: 'ok',
    m2BillingStatus: 'BILLED', m2BillingDate: '2026-11-12 14:05', m2BilledBy: 'Engr. Web', m2BillingReference: '', m2BillingRemarks: '',
    createdBy: 'Engr. Phone', createdDate: '2026-10-01 08:00', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-12 14:05',
  });
  assert.deepEqual(bill.audits.map(a => [a.actionType, a.details, a.oldValue, a.newValue]), [
    ['Developer Billing Update', 'Updated M1 billing for Block 3 Lot 12 to BILLED (Ref: DV-7)', 'NOT BILLED', 'M1 BILLED (80%), Billed By: Engr. Web, Date: 2026-11-12 14:05, Ref: DV-7'],
    ['Developer Billing Update', 'Updated M2 billing for Block 3 Lot 12 to BILLED (Ref: )', 'M1 BILLED (80%)', 'M1 + M2 BILLED (100%), Billed By: Engr. Web, Date: 2026-11-12 14:05, Ref: '],
  ]);

  // Unbilling M2 clears its columns; a road lot's drainage is saved; a kept contractor stays.
  const road = { ...lot, lotUse: 'Road Lot', blockNumber: 'RL', lotNumber: '4', progress: { ...stored, m2BillingStatus: 'BILLED', billingStatus: 'BILLED', drainageProgress: 0 } };
  const undo = L.lotSave(road, { ...same, drainage: 100, billing: [{ billed: true, reference: '', remarks: '' }, { billed: false, reference: 'x', remarks: 'y' }] }, who);
  assert.equal(undo.changes.drainageProgress, 100);
  assert.equal(undo.changes.contractor, 'XYZ');
  assert.deepEqual([undo.changes.m2BillingStatus, undo.changes.m2BillingDate, undo.changes.m2BilledBy, undo.changes.m2BillingReference], ['NOT BILLED', '', '', '']);
  assert.equal('billingStatus' in undo.changes, false);
  assert.deepEqual(undo.audits.map(a => a.details), ['Updated Lot Progress (Road Lot 4) to 50% (Under Construction)', 'Updated M2 billing for Road Lot 4 to NOT BILLED (Ref: )']);
  assert.equal(undo.audits[0].oldValue, 'Progress: 50%, Status: Under Construction');
});

test('new weekly report: the week, the lines from the chart or the scope, the record, the project fields and the audit entry', () => {
  // The week: Saturday to Friday, labelled as the phone does, and read back by the site's own week reader.
  assert.deepEqual(['2026-02-14', '2026-02-18', '2026-02-20', '2026-02-21'].map(L.weekSaturday), ['2026-02-14', '2026-02-14', '2026-02-14', '2026-02-21']);
  assert.equal(L.weekLabel('2026-10-31'), 'Oct 31, 2026 - Nov 6, 2026');
  assert.equal(L.weekDays(L.weekLabel('2026-02-14'))[6], '2026-02-20');

  // With a chart: weights by amount, targets its plan on the week's last day (contract day 51), not typed.
  const project = { dateStarted: '2026-01-01', completionDateOriginal: '2026-12-31', completionDateRevised: '', scopeOfWork: 'ignored with a chart',
    barChartJson: JSON.stringify([{ effective: '', items: [{ name: 'Roads', amount: 60, bars: [[1, 100]] }, { name: 'Housing units', amount: 40, bars: [[51, 150]] }] }]) };
  const lines = L.weeklyLines(project, [], '2026-02-20');
  assert.deepEqual(lines, [{ name: 'Land Acquisition', weightPct: 0, targetPct: 0 }, { name: 'Land Development', weightPct: 60, targetPct: 51 }, { name: 'Housing Construction', weightPct: 40, targetPct: 1 }]);

  // Without one: the Scope of Works' weights and typed targets; no scope at all leaves every weight at 0.
  const scope = JSON.stringify([{ name: 'Land development', amount: 1, weightPct: 70 }, { name: 'Housing units', amount: 1, weightPct: 30 }]);
  assert.deepEqual(L.weeklyLines({ dateStarted: '2026-01-01', scopeOfWork: scope }, [], '2026-02-20').map(l => [l.weightPct, l.targetPct]), [[0, null], [70, null], [30, null]]);
  assert.deepEqual(L.weeklyLines({ scopeOfWork: '' }, [], '2026-02-20').map(l => [l.weightPct, l.targetPct]), [[0, null], [0, null], [0, null]]);

  const f = {
    first: '2026-02-14',
    lines: [{ ...lines[0], target: '0.00', actual: '' }, { ...lines[1], target: '51.00', actual: '50' }, { ...lines[2], target: '1.00', actual: '' }],
    manpower: [{ designation: ' Foreman ', count: '2' }, { designation: '', count: '5' }, { designation: 'Labor', count: '' }],
    equipment: [{ description: 'Backhoe', count: '1', status: 'Idle' }],
    activities: [{ from: 'Monday', to: 'Wednesday', description: 'Excavation', remarks: ' ok ' }, { from: 'Friday', to: '', description: 'Rebar', remarks: '' }, { from: 'Monday', to: '', description: ' ', remarks: 'x' }],
    issues: [{ description: 'Rain', actionTaken: 'Moved concreting', actionBy: 'Contractor/Developer' }],
  };
  assert.deepEqual(L.weeklyErrors(f), []);
  assert.deepEqual(L.weeklyErrors({ first: '', lines: [{ name: 'Land Development', target: '101', actual: 'abc' }] }),
    ['Pick the first day of the week.', 'Land Development: target must be 0 to 100%.', 'Land Development: actual must be 0 to 100%.']);

  const report = L.newWeeklyReport(f, project, 'Engr. Web');
  const record = report.build({ uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'me', now: 1794470800000 });
  const data = JSON.parse(record.data);
  assert.deepEqual({ ...record, data: null }, { uuid: 'u1', type: 'weekly_reports', projectUuid: 'p1', assignedUids: ['a'], revision: 1, updatedAt: 1794470800000, updatedByUid: 'me', isDeleted: false, data: null });
  assert.equal(data.reportingWeek, 'Feb 14, 2026 - Feb 20, 2026');
  assert.deepEqual([data.daysElapsed, data.remainingDays], [51, 314]);
  assert.ok(Math.abs(data.targetAccomplishmentPct - 31) < 1e-9);
  assert.equal(data.actualAccomplishmentPct, 30);
  assert.deepEqual(JSON.parse(data.manpowerJson), [{ designation: 'Foreman', count: 2, remarks: '' }, { designation: 'Labor', count: 0, remarks: '' }]);
  assert.deepEqual(JSON.parse(data.equipmentJson), [{ description: 'Backhoe', count: 1, status: 'Idle', remarks: '' }]);
  assert.deepEqual(JSON.parse(data.activitiesJson), [{ day: 'Monday - Wednesday', description: 'Excavation', remarks: 'ok' }, { day: 'Friday', description: 'Rebar', remarks: '' }]);
  assert.deepEqual(JSON.parse(data.issuesJson), [{ description: 'Rain', actionTaken: 'Moved concreting', remarks: '', actionBy: 'Contractor/Developer' }]);
  assert.deepEqual([data.documentsIssuedReceivedJson, data.attachedPhotoUrlsJson, data.photoDetailsJson, data.submittedByStaff, data.createdAtTimestamp, data._weather],
    ['[]', '[]', '{}', 'Engr. Web', 1794470800000, []]);
  // The site reads it back as its three lines, and offers all 7 days for weather.
  assert.deepEqual(L.accomplishmentOf(data.accomplishmentItemsJson).map(a => [a.itemDescription, a.weightPct, a.targetPct, a.actualPct]),
    [['Land Acquisition', 0, 0, 0], ['Land Development', 60, 51, 50], ['Housing Construction', 40, 1, 0]]);
  assert.deepEqual(L.weatherForm(data).map(d => d.logged), Array(7).fill(false));

  assert.deepEqual(report.projectFields, { targetAccomplishment: data.targetAccomplishmentPct, actualAccomplishment: 30 });
  assert.deepEqual(report.audit, { actionType: 'Report Creation', details: 'Submitted Weekly Report for Feb 14, 2026 - Feb 20, 2026',
    newValue: `Week: Feb 14, 2026 - Feb 20, 2026, Target: ${data.targetAccomplishmentPct}%, Actual: 30%` });
  // A report for a later week is already saved: this late entry leaves the project's figures alone.
  assert.equal(L.newWeeklyReport(f, project, 'x', [{ uuid: 'later', reportingWeek: 'Feb 21, 2026 - Feb 27, 2026' }]).projectFields, null);
  assert.notEqual(L.newWeeklyReport(f, project, 'x', [{ uuid: 'older', reportingWeek: 'Week 6: February 7 - February 13, 2026' }]).projectFields, null);
  // The project has an uploaded abstract: its actual is the abstract's, so only the target follows the report.
  assert.deepEqual(L.newWeeklyReport(f, project, 'x', [], [{ asOf: '2026-01-30' }]).projectFields, { targetAccomplishment: data.targetAccomplishmentPct });
  assert.equal(L.newWeeklyReport(f, project, 'x', [{ uuid: 'later', reportingWeek: 'Feb 21, 2026 - Feb 27, 2026' }], [{ asOf: '2026-01-30' }]).projectFields, null);
  // No dates on the project: day counts are 0, as on the phone.
  assert.deepEqual((({ daysElapsed, remainingDays }) => [daysElapsed, remainingDays])(JSON.parse(L.newWeeklyReport(f, {}, 'x').build({}).data)), [0, 0]);
});

test('weekly report edit: the form starts from the saved report, keeps what it does not show, and deletes like the phone', () => {
  const project = { dateStarted: '2026-01-01', completionDateOriginal: '2026-12-31', scopeOfWork: JSON.stringify([{ name: 'Land development', amount: 1, weightPct: 70 }, { name: 'Housing units', amount: 1, weightPct: 30 }]) };
  const fair = Array(24).fill('FAIR').join(',');
  const report = {
    uuid: 'r1', reportingWeek: 'Week 7: February 14 - February 20, 2026', daysElapsed: 51, remainingDays: 314, targetAccomplishmentPct: 20, actualAccomplishmentPct: 15,
    manpowerJson: JSON.stringify([{ designation: 'Foreman', count: 2, remarks: 'night shift' }]),
    equipmentJson: JSON.stringify([{ description: 'Crane', count: 1, status: 'On standby', remarks: 'rented' }]),
    activitiesJson: JSON.stringify([{ day: 'Monday - Wednesday', description: 'Excavation', remarks: 'ok' }, { day: 'Whole week', description: 'Hauling' }]),
    issuesJson: JSON.stringify([{ description: 'Rain', actionTaken: 'Moved', remarks: 'see log', actionBy: 'Contractor/Developer' }]),
    accomplishmentItemsJson: JSON.stringify([{ itemDescription: 'Land development', weightPct: 70, targetPct: 20, actualPct: 15.5 }, { itemDescription: 'Housing units', weightPct: 30, targetPct: 20, actualPct: 10 }]),
    attachedPhotoUrlsJson: '["nhafile:p/a.jpg"]', submittedByStaff: 'Engr. Phone',
    _weather: ['2026-02-14', '2026-02-15', '2026-02-20'].map(date => ({ date, dayOfWeek: 'x', hourlyConditionsCsv: fair })),
  };
  const start = L.weeklyStart(report);
  assert.equal(start.first, '2026-02-14');
  assert.deepEqual(start.lines, [{ target: '0.00', actual: '0.00' }, { target: '20.00', actual: '15.50' }, { target: '20.00', actual: '10.00' }]);
  assert.deepEqual(start.manpower, [{ designation: 'Foreman', count: 2, remarks: 'night shift' }]);
  assert.deepEqual(start.activities, [{ from: 'Monday', to: 'Wednesday', description: 'Excavation', remarks: 'ok' }, { from: 'Whole week', to: '', description: 'Hauling', remarks: '' }]);
  assert.equal(start.equipment[0].status, 'On standby');

  // Saved as opened, with one actual changed: the older label, the hidden remarks and the odd day and status all stay.
  const lines = L.weeklyLines(project, [], '2026-02-20').map((l, k) => ({ name: l.name, weightPct: l.weightPct, ...start.lines[k] }));
  const f = { first: start.first, lines: lines.map((l, k) => (k === 1 ? { ...l, actual: '25' } : l)), manpower: start.manpower, equipment: start.equipment, activities: start.activities, issues: start.issues };
  const edit = L.editedWeeklyReport(report, f, project);
  assert.equal(edit.changes.reportingWeek, 'Week 7: February 14 - February 20, 2026');
  assert.equal('_weather' in edit.changes, false);
  assert.equal('attachedPhotoUrlsJson' in edit.changes || 'submittedByStaff' in edit.changes || 'createdAtTimestamp' in edit.changes, false);
  assert.deepEqual([edit.changes.daysElapsed, edit.changes.remainingDays, edit.changes.targetAccomplishmentPct, edit.changes.actualAccomplishmentPct], [51, 314, 20, 20.5]);
  assert.deepEqual(JSON.parse(edit.changes.manpowerJson), [{ designation: 'Foreman', count: 2, remarks: 'night shift' }]);
  assert.deepEqual(JSON.parse(edit.changes.equipmentJson), [{ description: 'Crane', count: 1, status: 'On standby', remarks: 'rented' }]);
  assert.deepEqual(JSON.parse(edit.changes.activitiesJson).map(a => a.day), ['Monday - Wednesday', 'Whole week']);
  assert.deepEqual(JSON.parse(edit.changes.issuesJson), [{ description: 'Rain', actionTaken: 'Moved', remarks: 'see log', actionBy: 'Contractor/Developer' }]);
  assert.deepEqual(edit.projectFields, { targetAccomplishment: 20, actualAccomplishment: 20.5 });
  assert.deepEqual(edit.audit, { actionType: 'Edited Weekly Report', details: 'Modified report for Week 7: February 14 - February 20, 2026' });

  // A day later: the label is rewritten, the day counts move, and weather outside the new week is dropped.
  const moved = L.editedWeeklyReport(report, { ...f, first: '2026-02-15' }, project);
  assert.equal(moved.changes.reportingWeek, 'Feb 15, 2026 - Feb 21, 2026');
  assert.deepEqual([moved.changes.daysElapsed, moved.changes.remainingDays], [52, 313]);
  assert.deepEqual(moved.changes._weather.map(w => w.date), ['2026-02-15', '2026-02-20']);
  assert.equal(moved.audit.details, 'Modified report for Feb 15, 2026 - Feb 21, 2026');

  // The project follows the edit only while no other report is for a later week; the report itself does not count.
  const others = [report, { uuid: 'r2', reportingWeek: 'Feb 21, 2026 - Feb 27, 2026' }];
  assert.equal(L.editedWeeklyReport(report, f, project, others).projectFields, null);
  assert.deepEqual(L.editedWeeklyReport(report, f, project, [report]).projectFields, { targetAccomplishment: 20, actualAccomplishment: 20.5 });
  assert.deepEqual(L.editedWeeklyReport(report, f, project, [report], [{ asOf: '2026-01-30' }]).projectFields, { targetAccomplishment: 20 });
  assert.notEqual(L.editedWeeklyReport(report, { ...f, first: '2026-02-28' }, project, others).projectFields, null);

  assert.deepEqual(L.deletedWeeklyAudit(report), { actionType: 'Deleted Weekly Report', details: 'Deleted report for Week 7: February 14 - February 20, 2026' });
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER'].map(role => L.canDeleteReport({ role })), [true, true, false, false]);

  // Deleting an issue: the same roles, and the phone's entry with the record's id.
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER'].map(role => L.canDeleteIssue({ role })), [true, true, false, false]);
  assert.deepEqual(L.deletedIssueAudit({ uuid: 'i1', description: 'Delayed delivery of reinforcing steel bars to the site' }),
    { actionType: 'Issue Deletion', details: 'Deleted issue: Delayed delivery of reinforcin...', oldValue: 'Issue ID: i1', newValue: '' });
  assert.equal(L.deletedIssueAudit({ uuid: 'i2' }).details, 'Deleted issue: ...');
});

test('new project: who may add one, what stops the form, the document a phone would upload and the audit entry', async () => {
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER'].map(role => L.canCreateProject({ role })), [true, true, false, false]);
  assert.equal(L.canCreateProject({ role: 'ENGINEER_ADMIN', approved: false }), false);

  const f = {
    name: ' Pandi Heights ', location: 'Pandi, Bulacan', implementingOffice: 'Bulacan District Office', contractor: 'ABC Builders', projectType: 'Housing Project',
    landArea: '2.5', landUnit: 'Hectares', numberOfUnits: '120', days: '', dateStarted: '2026-01-01', assignedStaff: '', completed: false, completedOn: '2026-11-12', final: '100',
    scope: [{ name: 'Land Development', amount: '1,000,000' }, { name: ' Housing ', amount: '3000000.50' }, { name: '', amount: '' }],
  };
  const seen = [{ name: 'pandi  HEIGHTS', location: 'Pandi, Bulacan' }];
  assert.deepEqual(L.projectErrors(f, [], '2026-11-12'), []);
  assert.deepEqual(L.projectErrors(f, seen, '2026-11-12'), ['A project with this name and location already exists.']);
  assert.deepEqual(L.projectErrors({ ...f, location: 'Bocaue' }, seen, '2026-11-12'), []);
  assert.deepEqual(L.projectErrors({ ...f, name: ' ', contractor: '', days: '90.5', dateStarted: '', scope: [{ name: 'Roads', amount: '0' }] }, [], '2026-11-12'),
    ['Type the project name.', "Type the contractor's name.", 'Each scope of works item needs a name and an amount above 0.', 'Contract duration: whole days only.', 'Pick the date started.']);
  assert.deepEqual(L.projectErrors({ ...f, scope: [{ name: '', amount: '500' }] }, [], '2026-11-12'), ['Each scope of works item needs a name and an amount above 0.']);
  // Already completed: a date from the start up to today, and 0 to 100.
  const done = { ...f, completed: true };
  assert.deepEqual(L.projectErrors(done, [], '2026-11-12'), []);
  assert.deepEqual(L.projectErrors({ ...done, completedOn: '2026-11-13', final: '' }, [], '2026-11-12'), ["The actual completion date can't be in the future.", 'Type the final accomplishment.']);
  assert.deepEqual(L.projectErrors({ ...done, completedOn: '2025-12-31', final: '101' }, [], '2026-11-12'), ['The actual completion date is before the date started.', 'Final accomplishment: enter 0 to 100.']);
  assert.deepEqual(L.projectErrors({ ...done, completedOn: '' }, [], '2026-11-12'), ['Pick the actual completion date.']);

  // The contract's last day counts the start as day 1; blank is 365 days and 0 days ends on the start.
  assert.equal(L.projectEnd(f), '2026-12-31');
  assert.equal(L.projectEnd({ ...f, days: '30' }), '2026-01-30');
  assert.equal(L.projectEnd({ ...f, days: '0' }), '2026-01-01');
  assert.equal(L.projectEnd({ ...f, days: 'abc' }), '2026-12-31');
  assert.equal(L.projectEnd({ ...f, dateStarted: '' }), null);

  const { project, audit } = L.newProject(f, { uuid: 'p9', uid: 'u1', now: 1700000000000, user: 'Engr. Cruz' });
  assert.deepEqual(L.scopeItems(project.scopeOfWork), [{ name: 'Land Development', amount: 1000000, weightPct: 1000000 / 4000000.5 * 100 }, { name: 'Housing', amount: 3000000.5, weightPct: 3000000.5 / 4000000.5 * 100 }]);
  assert.deepEqual({ ...project, scopeOfWork: '' }, {
    projectUuid: 'p9', name: 'Pandi Heights', location: 'Pandi, Bulacan', implementingOffice: 'Bulacan District Office', contractor: 'ABC Builders', scopeOfWork: '',
    projectType: 'Housing Project', landArea: '2.5 Hectares', numberOfUnits: '120', projectedBeneficiaries: '', boardResolution: '', noticeOfAward: '', noticeToProceed: '',
    preparedByName: '', preparedByPosition: '', preparedByOffice: '', checkedByName: '', checkedByPosition: '', checkedByOffice: '', notedByName: '', notedByPosition: '', notedByOffice: '',
    weeklyCheckedByName: '', weeklyCheckedByPosition: '', weeklyCheckedByOffice: '', weeklyNotedByName: '', weeklyNotedByPosition: '', weeklyNotedByOffice: '',
    billingPreparedByName: '', billingPreparedByPosition: '', billingPreparedByOffice: '', billingCheckedByName: '', billingCheckedByPosition: '', billingCheckedByOffice: '', billingReviewedByName: '', billingReviewedByPosition: '', billingReviewedByOffice: '', billingRecommendedByName: '', billingRecommendedByPosition: '', billingRecommendedByOffice: '',
    latestUpdatePhotoUrl: '', barChartJson: '', contractCostOriginal: 4000000.5, contractCostRevised: 4000000.5, contractDurationDays: 365,
    dateStarted: '2026-01-01', completionDateOriginal: '2026-12-31', completionDateRevised: '2026-12-31', status: 'On-going', targetAccomplishment: 0, actualAccomplishment: 0,
    assignedStaff: 'Engr. Cruz', revision: 1, createdAt: 1700000000000, updatedAt: 1700000000000, createdByUid: 'u1', updatedByUid: 'u1', isDeleted: false, assignedUids: ['u1'],
  });
  assert.deepEqual(audit, { actionType: 'Project Creation', details: 'Created new project: Pandi Heights',
    newValue: 'Title: Pandi Heights, Location: Pandi, Bulacan, Cost: ₱4000000.5, Duration: 365 days' });
  assert.equal(L.isCompleted(project), false);

  // The same fields a phone uploads for a project (ProjectDocument.projectFields and FirestoreSyncManager's sync fields), plus the two it adds for a new one.
  const { readFileSync } = await import('node:fs');
  const read = name => readFileSync(new URL(`../app/src/main/java/com/example/data/remote/${name}`, import.meta.url), 'utf8');
  const doc = read('ProjectDocument.kt'), kt = read('FirestoreSyncManager.kt');
  const upload = doc.slice(doc.indexOf('fun projectFields'), doc.indexOf('/** The fields of')) + kt.slice(kt.indexOf('val full = hashMapOf'), kt.indexOf('// An edit sends'));
  assert.deepEqual(Object.keys(project).sort(), [...upload.matchAll(/"(\w+)" to|put\("(\w+)"/g)].map(m => m[1] || m[2]).concat('assignedUids', 'barChartJson').sort());

  // Finished before the app: saved at the final accomplishment on the actual date; no scope leaves the cost at 0.
  const old = L.newProject({ ...done, completedOn: '2026-06-30', final: '98.5', scope: [], projectType: ' ', landArea: '', assignedStaff: 'Engr. Reyes', days: '180' }, { uuid: 'p8', uid: 'u1', now: 1, user: 'Engr. Cruz' });
  assert.deepEqual([old.project.completionDateOriginal, old.project.completionDateRevised, old.project.targetAccomplishment, old.project.actualAccomplishment], ['2026-06-29', '2026-06-30', 98.5, 98.5]);
  assert.deepEqual([old.project.scopeOfWork, old.project.contractCostOriginal, old.project.projectType, old.project.landArea, old.project.assignedStaff], ['[]', 0, 'Housing Project', '', 'Engr. Reyes']);
  assert.equal(old.audit.newValue, 'Title: Pandi Heights, Location: Pandi, Bulacan, Cost: ₱0, Duration: 180 days, Already completed on 2026-06-30 at 98.5%');
  assert.equal(L.isCompleted(L.newProject(done, { uuid: 'p7', uid: 'u1', now: 1, user: 'x' }).project), true);
});

test('project edit: the form starts from the saved project, writes only what changed, and marks it completed like the phone', () => {
  const project = {
    projectUuid: 'p1', name: 'Pandi Heights', location: 'Pandi, Bulacan', implementingOffice: 'Bulacan District Office', contractor: 'ABC Builders',
    scopeOfWork: '[{"name":"Land Development","amount":1000000.0,"weightPct":25.0},{"name":"Housing","amount":3000000.0,"weightPct":75.0}]',
    projectType: 'Resettlement', landArea: '25000 sq.m.', numberOfUnits: '120 units', projectedBeneficiaries: '', boardResolution: 'BR 12', noticeOfAward: '', noticeToProceed: '',
    contractCostOriginal: 4000000, contractCostRevised: 4500000, contractDurationDays: 180, dateStarted: '2026-07-01', completionDateOriginal: '2026-12-27',
    completionDateRevised: '2027-01-31', status: 'On-going', targetAccomplishment: 40, actualAccomplishment: 35, assignedStaff: 'Engr. Cruz', assignedUids: ['u1'],
  };
  const start = L.projectStart(project, '2026-11-12');
  assert.deepEqual(start, {
    name: 'Pandi Heights', location: 'Pandi, Bulacan', implementingOffice: 'Bulacan District Office', contractor: 'ABC Builders', projectType: 'Resettlement',
    numberOfUnits: '120 units', dateStarted: '2026-07-01', projectedBeneficiaries: '', boardResolution: 'BR 12', noticeOfAward: '', noticeToProceed: '',
    landArea: '25000', landUnit: 'Hectares', days: '180', assignedStaff: 'Engr. Cruz',
    scope: [{ name: 'Land Development', amount: '1000000', weightPct: '25' }, { name: 'Housing', amount: '3000000', weightPct: '75' }],
    completed: false, completedOn: '2026-11-12', final: '100',
  });
  // "Mark as completed" starts at the completion date once it has passed (the revised one when set), else today.
  assert.equal(L.projectStart(project, '2027-03-01').completedOn, '2027-01-31');
  assert.equal(L.projectStart({ ...project, completionDateRevised: '' }, '2027-03-01').completedOn, '2026-12-27');
  assert.equal(L.projectStart({ ...project, landArea: '2.5 Square Meters' }, '2026-11-12').landUnit, 'Square Meters');
  assert.deepEqual(L.projectErrors(start, [project], '2026-11-12').length, 1); // the duplicate check is the caller's to skip on an edit
  assert.deepEqual(L.projectErrors(start, [], '2026-11-12'), []);

  // Untouched: nothing is written, not even the land area and scope the form would spell differently.
  assert.deepEqual(L.editedProject(project, start).fields, {});
  // Only the boxes changed are written; the revised cost and date are left alone.
  const edit = L.editedProject(project, { ...start, name: ' Pandi Heights Phase 2 ', days: '200', noticeToProceed: ' NTP 2026-06-20 ' });
  assert.deepEqual(edit.fields, { name: 'Pandi Heights Phase 2', contractDurationDays: 200, completionDateOriginal: '2027-01-16', noticeToProceed: 'NTP 2026-06-20' });
  assert.deepEqual(edit.audit, { actionType: 'Project Edit', details: 'Updated project master: Pandi Heights Phase 2', oldValue: 'Status: On-going', newValue: 'Status: On-going, Revised Cost: ₱4500000' });
  // A new scope item: the cost is the new total and every weight its share.
  const scoped = L.editedProject(project, { ...start, scope: [...start.scope, { name: 'Roads', amount: '1,000,000' }] }).fields;
  assert.deepEqual(Object.keys(scoped), ['scopeOfWork', 'contractCostOriginal']);
  assert.equal(scoped.contractCostOriginal, 5000000);
  assert.deepEqual(L.scopeItems(scoped.scopeOfWork).map(i => i.weightPct), [20, 60, 20]);
  // The land area is rewritten once its box is touched.
  assert.deepEqual(L.editedProject(project, { ...start, landUnit: 'Square Meters' }).fields, { landArea: '25000 Square Meters' });

  // Marked completed: work done becomes the final accomplishment and the actual date the revised completion.
  const done = L.editedProject(project, { ...start, completed: true, completedOn: '2026-11-10', final: '100' });
  assert.deepEqual(done.fields, { completionDateRevised: '2026-11-10', targetAccomplishment: 100, actualAccomplishment: 100 });
  assert.equal(done.audit.newValue, 'Status: Completed, Revised Cost: ₱4500000');
  assert.equal(L.editedProject({ ...project, contractCostRevised: undefined }, { ...start, location: 'Bocaue' }).audit.newValue, 'Status: On-going, Revised Cost: ₱0');

  // An older project's scope with no amounts: its items may stay, and keep their weights when one is renamed.
  const old = { ...project, scopeOfWork: '[{"name":"Land Development","amount":0,"weightPct":40},{"name":"Housing","amount":0,"weightPct":60}]', contractCostOriginal: 0 };
  const oldStart = L.projectStart(old, '2026-11-12');
  assert.deepEqual(oldStart.scope, [{ name: 'Land Development', amount: '', weightPct: '40' }, { name: 'Housing', amount: '', weightPct: '60' }]);
  assert.deepEqual(L.projectErrors(oldStart, [], '2026-11-12'), []);
  const renamed = L.editedProject(old, { ...oldStart, scope: [{ ...oldStart.scope[0], name: 'Site Development' }, oldStart.scope[1]] }).fields;
  assert.deepEqual(L.scopeItems(renamed.scopeOfWork), [{ name: 'Site Development', amount: 0, weightPct: 40 }, { name: 'Housing', amount: 0, weightPct: 60 }]);
  // A plain-text scope from before the list reads as one item at 100%.
  assert.deepEqual(L.projectStart({ ...project, scopeOfWork: 'Land development and housing' }, '2026-11-12').scope, [{ name: 'Land development and housing', amount: '', weightPct: '100' }]);
});

test('signatories: the project fields a weekly or a monthly report or the billing abstract saves, only where they differ, and the audit entry', () => {
  const project = { name: 'Pandi Heights', status: 'On-going', actualAccomplishment: 35, contractCostRevised: 4500000,
    preparedByName: 'Engr. Cruz', preparedByPosition: 'Senior Engineer A', preparedByOffice: 'Bulacan District Office', weeklyCheckedByName: 'Engr. Reyes' };
  const form = { preparedName: ' Engr. Cruz ', preparedPosition: 'Senior Engineer A', preparedOffice: 'Bulacan District Office',
    checkedName: 'Engr. Reyes', checkedPosition: 'Principal Engineer A', checkedOffice: 'Bulacan District Office',
    notedName: 'Engr. Santos', notedPosition: 'District Manager', notedOffice: ' Bulacan District Office ' };
  // Weekly: its own checker and noter; what is already saved (after trimming) is not written again.
  assert.deepEqual(L.signatoryFields(project, form, 'weekly'), {
    weeklyCheckedByPosition: 'Principal Engineer A', weeklyCheckedByOffice: 'Bulacan District Office',
    weeklyNotedByName: 'Engr. Santos', weeklyNotedByPosition: 'District Manager', weeklyNotedByOffice: 'Bulacan District Office',
  });
  // Monthly: the same form goes to the monthly checker and noter; Prepared by is shared.
  assert.deepEqual(L.signatoryFields(project, { ...form, preparedPosition: 'Principal Engineer C' }, 'monthly'), {
    preparedByPosition: 'Principal Engineer C', checkedByName: 'Engr. Reyes', checkedByPosition: 'Principal Engineer A', checkedByOffice: 'Bulacan District Office',
    notedByName: 'Engr. Santos', notedByPosition: 'District Manager', notedByOffice: 'Bulacan District Office',
  });
  // Billing: its own four, billing* only; the reports' Prepared by is not touched.
  assert.deepEqual(L.signatoryFields({ ...project, billingCheckedByName: 'Engr. Reyes' }, { ...form, preparedName: 'Pedro Santos', preparedPosition: '', preparedOffice: 'PS Builders',
    reviewedName: 'Rosa Diaz', reviewedPosition: 'District Manager', reviewedOffice: '', recommendedName: 'Lina Cruz', recommendedPosition: 'Regional Manager', recommendedOffice: 'Region 3 Office' }, 'billing'), {
    billingPreparedByName: 'Pedro Santos', billingPreparedByOffice: 'PS Builders', billingCheckedByPosition: 'Principal Engineer A', billingCheckedByOffice: 'Bulacan District Office',
    billingReviewedByName: 'Rosa Diaz', billingReviewedByPosition: 'District Manager',
    billingRecommendedByName: 'Lina Cruz', billingRecommendedByPosition: 'Regional Manager', billingRecommendedByOffice: 'Region 3 Office',
  });
  // A name cleared is saved as blank; nothing different is nothing to save.
  assert.deepEqual(L.signatoryFields({ ...project, ...L.signatoryFields(project, form, 'weekly') }, { ...form, checkedName: '' }, 'weekly'), { weeklyCheckedByName: '' });
  assert.deepEqual(L.signatoryFields({ ...project, ...L.signatoryFields(project, form, 'weekly') }, form, 'weekly'), {});
  assert.deepEqual(L.projectEditAudit(project, { weeklyNotedByName: 'Engr. Santos' }),
    { actionType: 'Project Edit', details: 'Updated project master: Pandi Heights', oldValue: 'Status: On-going', newValue: 'Status: On-going, Revised Cost: ₱4500000' });
  assert.deepEqual(Object.keys(L.SIGNATORY_POSITIONS), ['prepared', 'weeklyChecked', 'weeklyNoted', 'checked', 'noted']);
});

test('monthly report: the record a first save of recommendations makes, and the phone\'s two audit entries', () => {
  const ids = { uuid: 'm1', projectUuid: 'p1', assignedUids: ['u1'], uid: 'u1', now: 1794292093231, preparedBy: 'Engr. Cruz' };
  const weeks = [{ targetAccomplishmentPct: 30, actualAccomplishmentPct: 28 }, { targetAccomplishmentPct: 38, actualAccomplishmentPct: '36.5' }];
  const rec = L.newMonthlyRecord(ids, 'September 2026', weeks, 'Fast-track the drainage works.');
  assert.deepEqual({ ...rec, data: JSON.parse(rec.data) }, {
    uuid: 'm1', type: 'monthly_reports', projectUuid: 'p1', assignedUids: ['u1'], revision: 1, updatedAt: 1794292093231, updatedByUid: 'u1', isDeleted: false,
    data: {
      reportingMonth: 'September 2026', scopeWeightPct: 100, scopeTargetPct: 38, scopeActualPct: 36.5, paymentsJson: '[]',
      unworkableDaysCount: 0, workableDaysCount: 0, cpesIssuesJson: '[]', recommendations: 'Fast-track the drainage works.',
      preparedByName: 'Engr. Cruz', preparedByStatus: 'Reviewed', checkedByName: '', checkedByStatus: 'Pending Review',
      notedByName: '', notedByStatus: 'Pending Review', auditTrailJson: '', accomplishmentItemsJson: '[]',
    },
  });
  // A month with no weekly report stands at 0.
  const empty = JSON.parse(L.newMonthlyRecord(ids, 'June 2026', [], 'x').data);
  assert.deepEqual([empty.scopeTargetPct, empty.scopeActualPct], [0, 0]);
  assert.deepEqual(L.monthlyAudit('September 2026', weeks, false),
    { actionType: 'Report Creation', details: 'Submitted Monthly Report for September 2026', oldValue: '', newValue: 'Month: September 2026, Target: 38%, Actual: 36.5%' });
  assert.deepEqual(L.monthlyAudit('September 2026', weeks, true),
    { actionType: 'Report Creation', details: 'Modified report for September 2026', oldValue: 'Previous report data', newValue: 'Updated report data' });
});

test('assign people: the options, the list and names saved, and nothing saved when the list is unchanged', () => {
  const rows = [L.userRow('u2', { displayName: 'engr. Reyes', role: 'FIELD_ENGINEER' }), L.userRow('u1', { displayName: 'Engr. Cruz', role: 'ENGINEER_ADMIN' }),
    L.userRow('u3', { email: 'viewer@nha.test', role: 'VIEWER' })];
  // A to Z whatever the case; an assigned uid with no account stays, shown by its uid.
  const users = L.assignableUsers(rows, ['u2', 'gone', 'gone']);
  assert.deepEqual(users.map(u => [u.uid, u.name]), [['u1', 'Engr. Cruz'], ['u2', 'engr. Reyes'], ['u3', 'viewer@nha.test'], ['gone', 'gone']]);
  const project = { assignedUids: ['u2', 'gone'], assignedStaff: 'old text' };
  // Those already assigned keep their place; new ones follow in the order ticked.
  assert.deepEqual(L.assignmentFields(project, ['u1', 'u2', 'u3', 'gone'], users),
    { assignedUids: ['u2', 'gone', 'u1', 'u3'], assignedStaff: 'engr. Reyes, gone, Engr. Cruz, viewer@nha.test' });
  assert.deepEqual(L.assignmentFields(project, ['u2'], users), { assignedUids: ['u2'], assignedStaff: 'engr. Reyes' });
  assert.deepEqual(L.assignmentFields(project, [], users), { assignedUids: [], assignedStaff: '' });
  assert.deepEqual(L.assignmentFields(project, ['gone', 'u2'], users), {});
  assert.deepEqual(L.assignmentFields({}, ['u1'], users), { assignedUids: ['u1'], assignedStaff: 'Engr. Cruz' });
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'SUPER_VIEWER'].map(role => L.canAssign({ role })), [true, false, false, false]);
});

test('delete project: the Super Admin, or an Engineer Admin assigned to it, and the tombstone a phone uploads', () => {
  const project = { projectUuid: 'p1', name: 'Pandi Heights', assignedUids: ['u1'], revision: 4 };
  const can = (role, uid) => L.canDeleteProject(project, uid, { role });
  assert.deepEqual([can('SUPER_ADMIN', 'x'), can('ENGINEER_ADMIN', 'u1'), can('ENGINEER_ADMIN', 'x'), can('FIELD_ENGINEER', 'u1'), can('SUPER_VIEWER', 'u1')],
    [true, true, false, false, false]);
  assert.deepEqual(L.deletedProject(project, { uid: 'u1', now: 1794292093231 }),
    { projectUuid: 'p1', isDeleted: true, updatedAt: 1794292093231, updatedByUid: 'u1' });
});

test('bar chart sheet: days are painted as work, in a colour or cleared, bars that touch join, and a kept draft comes back only for the chart it was made from', () => {
  const none = [{ sm: '', sd: '', em: '', ed: '' }], bar = (sm, sd, em, ed) => ({ sm: String(sm), sd: String(sd), em: String(em), ed: String(ed) });
  const item = (bars = none, marks = []) => ({ name: 'A', amount: 1, bars, marks });
  // The chart's own colours: one for days with no work (a suspension), one for days of work (a variation order).
  const colors = [{ name: 'Suspension No. 1', color: '#FFD966', work: false }, { name: 'Variation Order No. 1', color: '#FF7C80', work: true }];
  // Work: a day next to a bar lengthens it; one apart starts another; a month is 30 days.
  const first = L.paintDays(item(), 1, 5, 'work', colors);
  assert.deepEqual(first, item([bar(1, 1, 1, 5)]));
  assert.deepEqual(L.paintDays(first, 6, 6, 'work', colors).bars, [bar(1, 1, 1, 6)]);
  assert.deepEqual(L.paintDays(first, 31, 35, 'work', colors).bars, [bar(1, 1, 1, 5), bar(2, 1, 2, 5)]);
  // Erase: a day in the middle splits the bar; the last day cleared leaves the empty bar the form starts with; a bar that does not read is dropped.
  assert.deepEqual(L.paintDays(item([bar(1, 1, 1, 15)]), 7, 7, 'erase', colors).bars, [bar(1, 1, 1, 6), bar(1, 8, 1, 15)]);
  assert.deepEqual(L.paintDays(first, 1, 5, 'erase', colors), item());
  assert.deepEqual(L.paintDays(item([bar(1, 1, 1, 5), bar(9, 9, 1, 1)]), 1, 5, 'erase', colors).bars, none);
  // A colour with no work takes its days off the bar, so they carry no cost; a colour of work puts them on it.
  const drawn = L.paintDays(L.paintDays(item([bar(1, 1, 1, 10)]), 4, 6, 0, colors), 9, 12, 1, colors);
  assert.deepEqual(drawn, item([bar(1, 1, 1, 3), bar(1, 7, 1, 12)], [[4, 6, 0], [9, 12, 1]]));
  const draft = { label: 'Revised No. 1', effective: '2026-06-01', colors, items: [drawn] };
  const saved = L.draftVersion(draft);
  assert.deepEqual(saved, { label: 'Revised No. 1', effective: '2026-06-01', items: [{ name: 'A', amount: 1, bars: [[1, 3], [7, 12]], marks: [[4, 6, 0], [9, 12, 1]] }], colors });
  assert.deepEqual(L.dayFills(saved.items[0], colors, 13), ['work', 'work', 'work', 0, 0, 0, 'work', 'work', 1, 1, 1, 1, null]);
  // Work painted over coloured days takes the colour off; a coloured day shows only where it agrees with the bar typed since.
  assert.deepEqual(L.paintDays(drawn, 4, 12, 'work', colors), item([bar(1, 1, 1, 12)]));
  assert.deepEqual(L.draftVersion({ ...draft, items: [{ ...drawn, bars: [bar(1, 1, 1, 5)] }] }).items[0], { name: 'A', amount: 1, bars: [[1, 5]], marks: [[6, 6, 0]] });
  // Saved and opened again it is the same draft; a chart with no colour is saved without the keys, as the phone writes it.
  const project = { dateStarted: '2026-01-05', scopeOfWork: '', barChartJson: JSON.stringify([{ ...saved, effective: '2026-01-05' }]) };
  assert.deepEqual(L.chartDrafts(project), [{ ...draft, effective: '2026-01-05' }]);
  assert.equal(L.chartJson([{ label: 'Original', effective: '2026-01-05', colors: [], items: [first] }]), '[{"label":"Original","effective":"2026-01-05","items":[{"name":"A","amount":1,"bars":[[1,5]]}]}]');
  // Anything else in the JSON is not a colour or a coloured day: a colour goes into a style, so only #RRGGBB is kept.
  assert.deepEqual(L.chartColors({ colors: [{ name: 7, color: 'red;x:url(//e)', work: 'yes' }, null] }), [{ name: '7', color: '#FFD966', work: false }, { name: '', color: '#FFD966', work: false }]);
  assert.deepEqual(L.dayFills({ bars: [[1, 2]], marks: [[2, 1, 0], [3, 4, 9], 'x', [3, 4, 0, 0], [5, 99999, 0], [3, 3, 0]] }, colors, 4), ['work', 'work', 0, null]);
  // A colour is added unnamed, which keeps Save off; the revised chart copies the colours and the coloured days.
  assert.deepEqual([L.newColor([]), L.newColor(colors)], [{ name: '', color: '#FFD966', work: false }, { name: '', color: '#9DC3E6', work: false }]);
  const two = [{ ...draft, label: 'Original', effective: '2026-01-05' }, L.revisedDraft([draft])];
  assert.deepEqual([two[1].colors, two[1].items[0].marks], [colors, drawn.marks]);
  assert.notEqual(two[1].colors[0], colors[0]);
  two[1].colors.push(L.newColor(two[1].colors));
  assert.ok(L.chartErrors(two, { dateStarted: '2026-01-05', contractCostOriginal: 1 }, []).includes('Revised No. 1: name every colour'));
  // "Work is done on these days" ticked: the suspension's days join the bar; the colour removed, they are plain work and the next colour moves up.
  const worked = L.colorWork(draft, 0, true);
  assert.deepEqual(L.draftVersion(worked).items[0], { name: 'A', amount: 1, bars: [[1, 12]], marks: [[4, 6, 0], [9, 12, 1]] });
  assert.deepEqual(L.draftVersion(L.colorWork(worked, 0, false)), saved);
  assert.deepEqual(L.draftVersion(L.dropColor(worked, 0)), { ...saved, items: [{ name: 'A', amount: 1, bars: [[1, 12]], marks: [[9, 12, 0]] }], colors: [colors[1]] });
  // Months drawn: to the last bar or coloured day, the contract duration, or what Add month asked for.
  const plain = { label: 'Original', effective: '2026-01-05', colors: [], items: [item([bar(1, 1, 3, 2)])] };
  assert.deepEqual([L.sheetMonths(plain, 0), L.sheetMonths(plain, 240), L.sheetMonths(plain, 240, 9), L.sheetMonths({ ...plain, items: [] }, '')], [3, 8, 9, 1]);
  assert.equal(L.sheetMonths({ ...plain, items: [item([bar(1, 1, 9999, 1)])] }, 0), 120);
  assert.equal(L.sheetMonths({ ...plain, colors, items: [item([bar(1, 1, 3, 2)], [[100, 125, 0]])] }, 0), 5);
  // The kept draft: back as the drafts when made from the chart saved now; not for another chart, or anything else in the box.
  const kept = JSON.stringify(L.chartKept('[saved]', [draft]));
  assert.deepEqual(L.keptChart(kept, '[saved]'), [draft]);
  assert.deepEqual(L.keptChart('{"base":"","drafts":[{"label":"x","effective":"","items":[{"name":"A","amount":1,"bars":[]}]}]}', ''), [{ label: 'x', effective: '', colors: [], items: [{ name: 'A', amount: 1, bars: [], marks: [] }] }]); // kept before there were colours
  assert.deepEqual([L.keptChart(kept, '[other]'), L.keptChart(null, ''), L.keptChart('{"base":"","drafts":[{"label":"x"}]}', ''), L.keptChart('{"base":"","drafts":[{"label":"x","effective":"","items":[{"name":"A","amount":1,"bars":[{"sm":1}]}]}]}', '')], [null, null, null, null]);
  // The line under the sheet: the original against the original contract, a revised chart against the revised contract after a Variation Order.
  const priced = { scopeOfWork: '', contractCostOriginal: 5000000 }, vo = [{ documentName: 'Variation Order No. 1 (Additive) - PHP 500,000.00', status: 'Approved' }];
  assert.deepEqual(L.chartTotalNote(0, 5000000, priced, vo), { ok: true, text: 'Chart total ₱5,000,000.00: equals the original contract (₱5,000,000.00).' });
  assert.deepEqual(L.chartTotalNote(1, 5500000, priced, vo), { ok: true, text: 'Chart total ₱5,500,000.00: equals the revised contract (₱5,500,000.00).' });
  assert.deepEqual(L.chartTotalNote(1, 5000000, priced, vo), { ok: false, text: 'Chart total ₱5,000,000.00: ₱500,000.00 less than the revised contract (₱5,500,000.00). It still equals the original contract.' });
  assert.deepEqual(L.chartTotalNote(1, 5600000, priced, []), { ok: false, text: 'Chart total ₱5,600,000.00: ₱600,000.00 more than the contract (₱5,000,000.00).' });
});

test('bar chart suspension: days of no work are put into an item or the whole project, the later work moves, and taking them out moves it back', () => {
  const made = (name, ...bars) => L.draftItems([{ name, amount: 1, bars }])[0];
  const colors = [{ name: 'Suspension No. 1', color: '#FFD966', work: false }];
  const saved = items => L.draftVersion({ label: 'Revised No. 1', effective: '2026-06-01', colors, items }).items.map(i => [i.name, i.bars, i.marks ?? []]);
  // One item: days 41 to 70 are the suspension, so the 40 days of work left start on day 71; its 61 days of work are still 61.
  const running = L.insertDays(made('Running', [20, 80]), 41, 70, 0);
  assert.deepEqual(saved([running]), [['Running', [[20, 40], [71, 110]], [[41, 70, 0]]]]);
  // A second suspension inside the first's later work moves that again, and the first suspension stays where it is.
  assert.deepEqual(saved([L.insertDays(running, 81, 90, 0)]), [['Running', [[20, 40], [71, 80], [91, 120]], [[41, 70, 0], [81, 90, 0]]]]);
  // Taken out, the work is back as it was; days the item works on are not taken out this way.
  assert.deepEqual(L.removeDays(running, 41, 70), made('Running', [20, 80]));
  assert.equal(L.removeDays(running, 35, 70), null);
  // The whole project: an item finished before the suspension is left alone; one under way is cut; one not started moves whole.
  const items = [made('Done', [1, 30]), made('Running', [20, 80]), made('Later', [90, 120])];
  const suspended = L.suspendDays(items, 41, 70, 0);
  assert.deepEqual(saved(suspended), [['Done', [[1, 30]], []], ['Running', [[20, 40], [71, 110]], [[41, 70, 0]]], ['Later', [[120, 150]], [[41, 70, 0]]]]);
  const version = L.draftVersion({ label: 'Revised No. 1', effective: '2026-06-01', colors, items: suspended });
  assert.deepEqual(L.suspendedFills(version, colors, 72).slice(38), [null, null, ...Array(30).fill(0), null, null]); // what the Whole project row shows
  assert.equal(L.sheetMonths({ label: 'R', effective: '', colors, items: suspended }, 120), 5); // the months follow the work moved later
  assert.deepEqual(L.suspendDays(suspended, 41, 70, 'erase'), items);
  // Erasing on the Whole project row never takes work out: an item that works on those days is left as it is.
  assert.deepEqual(saved(L.suspendDays(suspended, 31, 40, 'erase')), [['Done', [[1, 30]], []], ['Running', [[20, 40], [71, 110]], [[41, 70, 0]]], ['Later', [[110, 140]], [[31, 60, 0]]]]);
  // A chart holds 120 months: work that would pass them is left where it is.
  const far = made('Far', [3500, 3590]);
  assert.equal(L.insertDays(far, 3400, 3420, 0), far);
  // Add column, pressed on month 2 day 16 (day 46): an empty day goes in there with no colour and the later work moves a day; pressed again, another.
  const column = L.suspendDays(items, 46, 46, null);
  assert.deepEqual(saved(column), [['Done', [[1, 30]], []], ['Running', [[20, 45], [47, 81]], []], ['Later', [[91, 121]], []]]);
  assert.deepEqual(saved(L.suspendDays(column, 46, 46, null)), [['Done', [[1, 30]], []], ['Running', [[20, 45], [48, 82]], []], ['Later', [[92, 122]], []]]);
  // Remove column takes it out and the work is back; the page leaves a column an item works on (removeDays is null for it).
  assert.deepEqual(L.suspendDays(column, 46, 46, 'erase'), items);
  assert.equal(L.removeDays(column[1], 45, 45), null);
});

test('bar chart editor: drafts from the scope or the saved chart, bars as month and day, the errors and what is saved', () => {
  const scope = JSON.stringify([{ name: 'Land development', amount: 3000000, weightPct: 60 }, { name: 'Housing units', amount: 2000000, weightPct: 40 }]);
  const project = { projectUuid: 'p1', assignedUids: ['u1'], dateStarted: '2026-01-05', scopeOfWork: scope, contractCostOriginal: 5000000, barChartJson: '' };
  // No chart yet: one "Original" chart of the scope items from the start date, each with an empty bar; nothing to save until the bars read.
  const fresh = L.chartDrafts(project);
  assert.deepEqual(fresh, [{ label: 'Original', effective: '2026-01-05', colors: [], items: [
    { name: 'Land development', amount: 3000000, bars: [{ sm: '', sd: '', em: '', ed: '' }], marks: [] }, { name: 'Housing units', amount: 2000000, bars: [{ sm: '', sd: '', em: '', ed: '' }], marks: [] }] }]);
  assert.deepEqual(L.chartErrors(fresh, project, []), ['Original: check the bars of Land development, Housing units (month 1+, day 1 to 30)']);
  assert.equal(L.chartDrafts({ ...project, dateStarted: '' }), null);
  // A bar: From month 1 day 1 is day 1; To month 8 day 30 is day 240. A revised chart's bars start and end on any day.
  assert.deepEqual(L.draftBar({ sm: '1', sd: '1', em: '8', ed: '30' }), [1, 240]);
  assert.deepEqual(L.draftBar({ sm: ' 5 ', sd: '1', em: '18', ed: '30' }), [121, 540]);
  assert.deepEqual(L.draftBar({ sm: '10', sd: '6', em: '10', ed: '26' }), [276, 296]);
  assert.deepEqual(L.draftBar({ sm: '2', sd: '7', em: '2', ed: '7' }), [37, 37]);
  assert.deepEqual([{ sm: '0', sd: '1', em: '2', ed: '30' }, { sm: '1', sd: '0', em: '2', ed: '30' }, { sm: '3', sd: '1', em: '2', ed: '30' }, { sm: '1', sd: '1', em: 'x', ed: '30' },
    { sm: '1', sd: '31', em: '2', ed: '30' }, { sm: '1.5', sd: '1', em: '2', ed: '30' }, { sm: '2', sd: '8', em: '2', ed: '7' }].map(L.draftBar), [null, null, null, null, null, null, null]);
  fresh[0].items[0].bars[0] = { sm: '1', sd: '1', em: '8', ed: '30' };
  fresh[0].items[1].bars = [{ sm: '5', sd: '1', em: '18', ed: '30' }];
  assert.deepEqual(L.chartErrors(fresh, project, []), []);
  const json = L.chartJson(fresh);
  assert.equal(json, '[{"label":"Original","effective":"2026-01-05","items":[{"name":"Land development","amount":3000000,"bars":[[1,240]]},{"name":"Housing units","amount":2000000,"bars":[[121,540]]}]}]');
  // The saved chart opens as it was entered, and saving it untouched makes the same JSON.
  const saved = { ...project, barChartJson: json };
  assert.deepEqual(L.chartDrafts(saved), fresh);
  assert.equal(L.chartJson(L.chartDrafts(saved)), json);
  // A revised chart copies the last one; it needs a date after the start and the previous chart, and the contract total.
  const two = [...L.chartDrafts(saved), L.revisedDraft(L.chartDrafts(saved))];
  assert.equal(two[1].label, 'Revised No. 1');
  assert.notEqual(two[1].items[0].bars[0], two[0].items[0].bars[0]);
  assert.deepEqual(L.chartErrors(two, saved, []), ['Revised No. 1: pick the date it takes effect']);
  two[1].effective = '2025-12-01';
  assert.deepEqual(L.chartErrors(two, saved, []), ['Revised No. 1: takes effect before the start date', 'Revised No. 1: takes effect on or before the previous chart']);
  two[1].effective = '2026-06-01';
  two[1].label = ' ';
  two[1].items[1].amount = 2500000;
  assert.deepEqual(L.chartErrors(two, saved, []), ['Chart 2: enter a name', 'Chart 2: the chart totals ₱5,500,000.00 but the contract amount is ₱5,000,000.00']);
  // An approved Variation Order raises the amount a revised chart may total; the original chart still totals the original.
  const vo = [{ documentName: 'Variation Order No. 1 (Additive) - PHP 500,000.00', status: 'Approved' }];
  two[1].label = 'Revised No. 1';
  assert.deepEqual(L.chartErrors(two, saved, vo), []);
  // With the Variation Order the latest revised chart must total the revised contract, not the original; one before it may still total the original.
  const stale = [two[0], { ...two[1], items: two[0].items }];
  assert.deepEqual(L.chartErrors(stale, saved, vo), ['Revised No. 1: the chart totals ₱5,000,000.00 but the contract amount is ₱5,500,000.00']);
  assert.deepEqual(L.chartErrors([...stale, { ...two[1], label: 'Revised No. 2', effective: '2026-09-01' }], saved, vo), []);
  // A revised chart's items can be renamed, added and removed: each needs a name of its own, and the chart an item.
  two[1].items.push({ ...L.emptyItem(), bars: [{ sm: '1', sd: '1', em: '1', ed: '30' }] });
  assert.deepEqual(L.chartErrors(two, saved, vo), ['Revised No. 1: name every item']);
  two[1].items[2].name = ' Housing units ';
  assert.deepEqual(L.chartErrors(two, saved, vo), ['Revised No. 1: two items are named Housing units']);
  two[1].items[2].name = ' Drainage ';
  two[1].items[1].amount = 2000000;
  two[1].items[2].amount = 500000;
  assert.deepEqual(L.chartErrors(two, saved, vo), []);
  assert.deepEqual(JSON.parse(L.chartJson(two))[1].items[2], { name: 'Drainage', amount: 500000, bars: [[1, 30]] });
  assert.deepEqual(L.chartErrors([two[0], { ...two[1], items: [] }], saved, vo), ['Revised No. 1: add an item, or import the Excel file']);
  // The original's names are not typed, so two of a name from an Excel file do not keep Save off.
  assert.deepEqual(L.chartErrors([{ ...two[0], items: [two[0].items[0], { ...two[0].items[1], name: 'Land development' }] }], saved, []), []);
  assert.deepEqual(L.chartErrors([{ label: 'Original', effective: '2026-01-05', colors: [], items: [] }], { ...project, scopeOfWork: '' }, []), ['Original: import the Excel file, or add the Scope of Works items to the project first']);
  // An imported sheet (chart-excel.js) replaces a chart's items; its notes say what was read and where a bar is not its Duration.
  const sheet = { name: 'Revised', items: [{ name: 'Mobilization', amount: 1000, bars: [[1, 10], [41, 45]] }, { name: 'Earthworks', amount: 3000.5, bars: [[11, 40]] }, { name: 'Turnover', amount: 999.5, bars: [] }],
    durations: { Mobilization: 10, Earthworks: 30 } };
  assert.deepEqual(L.draftItems(sheet.items), [
    { name: 'Mobilization', amount: 1000, bars: [{ sm: '1', sd: '1', em: '1', ed: '10' }, { sm: '2', sd: '11', em: '2', ed: '15' }], marks: [] },
    { name: 'Earthworks', amount: 3000.5, bars: [{ sm: '1', sd: '11', em: '2', ed: '10' }], marks: [] },
    { name: 'Turnover', amount: 999.5, bars: [{ sm: '', sd: '', em: '', ed: '' }], marks: [] }]);
  assert.deepEqual(L.importNotes(sheet), ['Read 3 items from the sheet "Revised".', 'Mobilization: the bar covers 15 days but its Duration says 10.']);
  assert.deepEqual([[0, 5000000], [0, 5500000], [1, 5500000], [1, 5000000], [1, 5200000]].map(([i, total]) => L.chartTotalMatches(i, total, project, vo)), [true, false, true, true, false]);
  assert.equal(L.chartJson([]), '');
  assert.deepEqual([L.chartAudit(json), L.chartAudit('')], [{ actionType: 'Bar Chart', details: 'Saved the approved bar chart' }, { actionType: 'Bar Chart', details: 'Removed the approved bar chart' }]);
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER'].map(role => L.canEditChart(project, 'u1', { role })), [true, true, false]);
  assert.equal(L.canEditChart(project, 'x', { role: 'ENGINEER_ADMIN' }), false);
});

test('plans and lots: a plan upload, a drawn lot, its name and use, and removing it are written like the phone', () => {
  const ids = { uuid: 'new', projectUuid: 'p1', assignedUids: ['u1'], uid: 'u1', now: 1794292093231 };
  const who = { user: 'Engr. Cruz', at: '2026-11-12 09:30' };
  const plans = [{ uuid: 'a1', siteName: '', version: 1, isActive: 0 }, { uuid: 'a2', siteName: '', version: 2, isActive: 1 }, { uuid: 'b1', siteName: 'Phase 2', version: 1, isActive: 1 }];
  const f = { planName: ' SDP rev 2 ', pdfFileUrl: 'nhafile:p1/x.pdf', description: ' as approved ', newSite: null, currentSiteName: '' };

  // A revision of the unnamed first site: the next version, in use, and the plan that was in use switched off.
  const revised = L.planUpload(plans, '', f, { user: 'Engr. Cruz', date: '2026-11-12' });
  assert.deepEqual([revised.site, revised.edits], ['', [[plans[1], { isActive: 0 }]]]);
  const plan = revised.build(ids);
  assert.deepEqual({ ...plan, data: JSON.parse(plan.data) }, { uuid: 'new', type: 'sdp_plans', projectUuid: 'p1', assignedUids: ['u1'], revision: 1, updatedAt: 1794292093231, updatedByUid: 'u1', isDeleted: false,
    data: { planName: 'SDP rev 2', pdfFileUrl: 'nhafile:p1/x.pdf', version: 3, isActive: 1, uploadedDate: '2026-11-12', uploadedBy: 'Engr. Cruz', description: 'as approved', scaleDenominator: 0, frame: '', siteName: '', rotation: 0 } });
  assert.deepEqual(revised.audit, { actionType: 'Document Upload', details: 'Uploaded Approved SDP Plan Version 3: SDP rev 2', newValue: 'Plan: SDP rev 2, Version: 3, File: nhafile:p1/x.pdf' });
  // Another site: version 1 of it, and the first site's plans take the name given to it.
  const site = L.planUpload(plans, '', { ...f, newSite: ' Phase 3 ', currentSiteName: ' Phase 1 ' }, { user: 'x', date: '2026-11-12' });
  assert.deepEqual([site.site, site.edits, JSON.parse(site.build(ids).data).version, site.audit.details],
    ['Phase 3', [[plans[0], { siteName: 'Phase 1' }], [plans[1], { siteName: 'Phase 1' }]], 1, 'Uploaded Approved SDP Plan (Phase 3) Version 1: SDP rev 2']);
  // From a named site nothing is renamed; the first plan of a project is version 1 of the unnamed site.
  assert.deepEqual(L.planUpload(plans, 'Phase 2', { ...f, newSite: 'Phase 3' }, { user: 'x', date: 'd' }).edits, []);
  assert.deepEqual([L.planUpload(plans, 'Phase 2', f, { user: 'x', date: 'd' }).edits, JSON.parse(L.planUpload([], '', f, { user: 'x', date: 'd' }).build(ids).data).version], [[[plans[2], { isActive: 0 }]], 1]);
  assert.deepEqual([L.siteNameError(' ', false, '', []), L.siteNameError('Phase 3', true, ' ', []), L.siteNameError('phase 1', true, 'Phase 1 ', []), L.siteNameError(' phase 2', false, '', ['', 'Phase 2']), L.siteNameError('Phase 3', true, 'Phase 1', ['', 'Phase 2'])],
    ['Name the new site', 'Name the site already uploaded', 'The two sites need different names', 'phase 2 is already a site of this project', null]);

  // A lot's area from the plan's scale: 10% x 10% of an A1 sheet (2384 x 1684 pt) at 1:1000.
  assert.equal(L.lotAreaSqM([[0.1, 0.1], [0.2, 0.1], [0.2, 0.2], [0.1, 0.2]], 2384, 1684, 1000), 4996.33);
  assert.deepEqual([L.lotAreaSqM([[0, 0], [1, 1]], 100, 100, 1000), L.lotAreaSqM([[0, 0], [1, 0], [1, 1]], 100, 100, 0)], [0, 0]);

  const lots = [{ uuid: 'l1', blockNumber: '3', lotNumber: '2-B', lotUse: 'Saleable', revision: 1, updatedAt: 5 }, { uuid: 'l2', blockNumber: '', lotNumber: '', lotUse: '' }];
  assert.deepEqual([L.lotNameError(null, '3', ' 2-b ', lots), L.lotNameError(null, '', '4', lots), L.lotNameError(null, '3', '4', lots), L.lotNameError(lots[0], '3', '2-B', lots),
    L.lotNameError(lots[1], '', '', lots), L.lotNameError(lots[1], '3', '', lots), L.lotNameError(lots[1], '3', '2-b', lots)],
    ['Block 3 Lot 2-b already exists', "Block and lot can't be blank", null, null, null, "Block and lot can't be blank", 'Block 3 Lot 2-b already exists']);

  // A drawn lot: the sdp_lots row under its plan; a road lot goes by one number under block RL, with no housing unit.
  const polygon = [[0.1, 0.1], [0.2, 0.1], [0.2, 0.2]];
  const made = L.newLot({ uuid: 'a2' }, { use: 'Saleable', block: ' 3 ', lotNo: ' 12 ', unit: ' HU-7 ', area: '62.5', description: ' corner lot ', polygon }, who);
  const lot = made.build(ids);
  assert.deepEqual({ ...lot, data: JSON.parse(lot.data) }, { uuid: 'new', type: 'sdp_lots', projectUuid: 'p1', assignedUids: ['u1'], revision: 1, updatedAt: 1794292093231, updatedByUid: 'u1', isDeleted: false,
    data: { blockNumber: '3', lotNumber: '12', housingUnitNumber: 'HU-7', lotAreaSqM: 62.5, polygonNormalizedJson: '[[0.1,0.1],[0.2,0.1],[0.2,0.2]]', isActive: 1, description: 'corner lot', lotUse: 'Saleable',
      createdBy: 'Engr. Cruz', createdDate: '2026-11-12 09:30', lastModifiedBy: 'Engr. Cruz', lastModifiedDate: '2026-11-12 09:30', _ref_sdpPlanId: 'a2' } });
  assert.deepEqual(made.audit, { actionType: 'Lot Created', details: 'Digitized Lot B3-L12', newValue: 'Saleable: Block 3, Lot 12, Unit: HU-7, Area: 62.5sqm' });
  const road = L.newLot({ uuid: 'a2' }, { use: 'Road Lot', block: 'ignored', lotNo: '4', unit: 'HU-9', area: 'abc', description: '', polygon }, who);
  assert.deepEqual([JSON.parse(road.build(ids).data).blockNumber, JSON.parse(road.build(ids).data).housingUnitNumber, road.audit.newValue], ['RL', '', 'Road Lot: Block RL, Lot 4, Unit: , Area: 50.0sqm']);
  assert.deepEqual([L.numberedBlock('Road Lot'), L.numberedBlock('Easement'), L.numberedBlock('Saleable')], ['RL', 'EA', undefined]);
  // The lot it makes reads back as the website shows lots.
  assert.equal(L.lotLabel(JSON.parse(road.build(ids).data)), 'Road Lot 4');

  // Name and use: only what changed is written, each with the phone's audit entry; nothing changed writes nothing.
  assert.equal(L.lotNameSave(lots[0], { use: 'Saleable', block: '3', lotNo: '2-B' }, who), null);
  assert.deepEqual(L.lotNameSave(lots[0], { use: 'Open Space', block: ' 4 ', lotNo: '1' }, who), {
    changes: { lotUse: 'Open Space', blockNumber: '4', lotNumber: '1', lastModifiedBy: 'Engr. Cruz', lastModifiedDate: '2026-11-12 09:30' },
    audits: [{ actionType: 'Lot Use Changed', details: 'Marked 1 lot(s) as Open Space: B4-L1', oldValue: 'Saleable', newValue: 'Open Space' },
      { actionType: 'Lot Renamed', details: 'Renamed B3-L2-B to B4-L1', oldValue: 'Block 3 Lot 2-B', newValue: 'Block 4 Lot 1' }] });
  // An unnamed lot keeps no name when its boxes are left blank; its use can still change.
  assert.deepEqual(L.lotNameSave(lots[1], { use: 'Facility', block: '', lotNo: '' }, who), {
    changes: { lotUse: 'Facility', lastModifiedBy: 'Engr. Cruz', lastModifiedDate: '2026-11-12 09:30' },
    audits: [{ actionType: 'Lot Use Changed', details: 'Marked 1 lot(s) as Facility: B-L', oldValue: 'Saleable', newValue: 'Facility' }] });
  assert.deepEqual(L.lotRemoved(lots[0], who), { changes: { isActive: 0, lastModifiedBy: 'Engr. Cruz', lastModifiedDate: '2026-11-12 09:30' },
    audit: { actionType: 'Lot Deactivated', details: 'Deactivated Lot B3-L2-B', oldValue: 'Active', newValue: 'Deactivated' } });
  // A removed lot is gone from its site.
  const records = [{ type: 'sdp_plans', uuid: 'a2', siteName: '', version: 2, isActive: 1, uploadedDate: '2026-03-10' },
    { type: 'sdp_lots', _ref_sdpPlanId: 'a2', ...lots[0], ...L.lotRemoved(lots[0], who).changes }, { type: 'sdp_lots', uuid: 'l3', _ref_sdpPlanId: 'a2', blockNumber: '1', lotNumber: '1', isActive: 1 }];
  assert.deepEqual(L.sdpSites(records)[0].lots.map(l => l.uuid), ['l3']);
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER'].map(role => L.canManagePlans({ assignedUids: ['u1'] }, 'u1', { role })), [true, true, false]);
});

test('plan tools: the scale and the areas it works out, a quarter turn, the revision in use, and deleting a plan are written like the phone', () => {
  const who = { user: 'Engr. Cruz', at: '2026-11-13 10:00' };
  assert.deepEqual(['1000', ' 500 ', '9', '100001', '1:1000', '12.5', ''].map(L.planScaleOf), [1000, 500, null, null, null, null, null]);

  // 10% x 10% of an A1 sheet (2384 x 1684 pt): 4996.33 sq.m at 1:1000, 1249.08 at 1:500.
  const square = '[[0.1,0.1],[0.2,0.1],[0.2,0.2],[0.1,0.2]]', page = [2384, 1684];
  const lot = (uuid, lotAreaSqM) => ({ uuid, lotAreaSqM, polygonNormalizedJson: square });
  const lots = [lot('blank', 0), lot('fifty', 50), lot('typed', 62.5), lot('worked', 1249.08), lot('same', 4996.33)];
  const plan = { uuid: 'a2', version: 2, planName: 'SDP rev 1', siteName: '', scaleDenominator: 500, rotation: 270, isActive: 1 };
  const area = { lotAreaSqM: 4996.33, lastModifiedBy: 'Engr. Cruz', lastModifiedDate: '2026-11-13 10:00' };
  // Blank, the add form's 50 and the area the last scale gave are worked out again; a typed area and one already right stay.
  assert.deepEqual(L.planScale(plan, lots, page, 1000, who), [
    [plan, { scaleDenominator: 1000 }, { actionType: 'Project Edit', details: 'Set SDP Plan v2 scale to 1:1000 and worked out 3 lot areas', oldValue: '1:500', newValue: '1:1000' }],
    [lots[0], area, null], [lots[1], area, null], [lots[3], area, null]]);
  // A first scale on a plan whose PDF cannot be read: only the scale.
  assert.deepEqual(L.planScale({ ...plan, scaleDenominator: 0 }, lots, null, 1000, who).map(e => e[2]),
    [{ actionType: 'Project Edit', details: 'Set SDP Plan v2 scale to 1:1000 and worked out 0 lot areas', oldValue: 'No scale', newValue: '1:1000' }]);

  assert.deepEqual([L.planTurned({}), L.planTurned({ rotation: 90 }), L.planTurned(plan)], [{ rotation: 90 }, { rotation: 180 }, { rotation: 0 }]);

  // The revision in use: the one picked goes on, the site's others off, with one audit entry; picking it again writes nothing.
  const v1 = { uuid: 'a1', version: 1, planName: 'SDP rev 0', siteName: '', isActive: 0 }, v3 = { uuid: 'a3', version: 3, planName: 'SDP rev 2', siteName: '', isActive: 0 };
  assert.deepEqual(L.planInUse([v3, plan, v1], v1), [[plan, { isActive: 0 }, { actionType: 'Project Edit', details: 'Set active SDP Plan Version 1: SDP rev 0', newValue: 'Active SDP Plan Version 1' }], [v1, { isActive: 1 }, null]]);
  assert.deepEqual(L.planInUse([v3, plan, v1], plan), []);
  assert.equal(L.planInUse([{ ...v1, siteName: 'Phase 2' }], { ...v1, siteName: 'Phase 2' })[0][2].details, 'Set active SDP Plan (Phase 2) Version 1: SDP rev 0');

  // Deleting a plan takes the records under it, and no other plan's.
  const records = [plan, v1, { type: 'sdp_lots', uuid: 'l1', _ref_sdpPlanId: 'a2', isActive: 0 }, { type: 'sdp_lot_progress', uuid: 'g1', _ref_sdpPlanId: 'a2', _ref_sdpLotId: 'l1' },
    { type: 'sdp_roads', uuid: 'r1', _ref_sdpPlanId: 'a2' }, { type: 'sdp_lot_inspections', uuid: 'i1', _ref_sdpPlanId: 'a2' }, { type: 'sdp_lots', uuid: 'l9', _ref_sdpPlanId: 'a1' }];
  const gone = L.planDeleted(plan, records);
  assert.deepEqual([gone.children.map(r => r.uuid), gone.audit], [['l1', 'g1', 'r1', 'i1'],
    { actionType: 'Document Deletion', details: 'Deleted SDP Plan Version 2: SDP rev 1, with its lots and progress', oldValue: 'Plan: SDP rev 1, Version: 2' }]);
  assert.equal(L.planDeleted({ ...plan, siteName: 'Phase 2' }, []).audit.details, 'Deleted SDP Plan (Phase 2) Version 2: SDP rev 1, with its lots and progress');

  // A site's revisions, latest first, for the revision menu; with the plan deleted the site falls back to the one left.
  const typed = [{ ...v1, type: 'sdp_plans' }, { ...plan, type: 'sdp_plans' }];
  assert.deepEqual([L.sdpSites(typed)[0].plans.map(p => p.uuid), L.sdpSites(typed)[0].plan.uuid, L.sdpSites(typed.slice(0, 1))[0].plan.uuid], [['a2', 'a1'], 'a2', 'a1']);
});

test('files: every reference a record holds, wherever it sits', () => {
  const P = '11111111-1111-4111-8111-111111111111', id = n => `${String(n).repeat(8)}-0000-4000-8000-000000000000`;
  const jpg = n => `nhafile:${P}/${id(n)}.jpg`, pdf = n => `nhafile:${P}/${id(n)}.pdf`;
  // Every reference, wherever it sits: a column, a JSON list inside a column, the old form without a project.
  assert.deepEqual(L.fileRefsIn({ fileUrl: pdf(1), photosJson: JSON.stringify([jpg(2), { url: jpg(3) }]), old: `nhafile:${id(4)}.jpg`, web: 'https://example.com/a.jpg', bad: 'nhafile:x.jpg' }),
    [pdf(1), jpg(2), jpg(3), `nhafile:${id(4)}.jpg`]);
  assert.deepEqual([L.fileRefsIn(null), L.fileRefsIn([])], [[], []]);
  // A list a phone saved writes each / as \/ (org.json): its files are found all the same.
  const phone = { uuid: 'w9', type: 'weekly_reports', attachedPhotoUrlsJson: JSON.stringify([jpg(7), jpg(8)]).replaceAll('/', '\\/') };
  assert.equal(phone.attachedPhotoUrlsJson.includes('\\/'), true);
  assert.deepEqual(L.fileRefsIn(phone), [jpg(7), jpg(8)]);
});

test('several lots at once and inspections: one progress or use for the lots picked, and a snapshot with its history, are written like the phone', () => {
  const who = { user: 'Engr. Web', at: '2026-11-13 09:30', contractor: 'ABC Builders' };
  const P = 'nhafile:11111111-1111-4111-8111-111111111111/22222222-0000-4000-8000-000000000000.jpg';
  const fresh = { uuid: 'lot-1', _ref_sdpPlanId: 'plan-1', blockNumber: '3', lotNumber: '1', progress: null };
  const started = { uuid: 'lot-2', _ref_sdpPlanId: 'plan-1', blockNumber: '3', lotNumber: '2', progress: { uuid: 'pr-2', physicalProgress: 40, constructionStatus: 'Under Construction',
    currentActivity: 'CHB laying', remarks: 'old', contractor: 'XYZ', startDate: '2026-01-05', photosJson: JSON.stringify([P]), billingStatus: 'BILLED', billingReference: 'DV-7', createdBy: 'A', createdDate: '2026-01-05 08:00' } };
  const same = { uuid: 'lot-3', _ref_sdpPlanId: 'plan-1', blockNumber: '3', lotNumber: '3', progress: { uuid: 'pr-3', physicalProgress: 75, constructionStatus: 'Under Construction', currentActivity: 'Roofing', remarks: '' } };
  const f = { progress: 75, constructionStatus: 'Under Construction', currentActivity: 'Roofing', remarks: '' };

  // Group progress: every lot gets the four values; photos, billing and the contractor stay; a lot already there is left out.
  const saves = L.lotsProgressSave([fresh, started, same], f, who);
  assert.deepEqual(saves.map(([lot]) => lot.uuid), ['lot-1', 'lot-2']);
  assert.deepEqual(saves[0][1].changes, { physicalProgress: 75, constructionStatus: 'Under Construction', currentActivity: 'Roofing', remarks: '', photosJson: '[]',
    contractor: 'ABC Builders', createdBy: 'Engr. Web', createdDate: '2026-11-13 09:30', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 09:30' });
  assert.deepEqual(saves[1][1].changes, { physicalProgress: 75, constructionStatus: 'Under Construction', currentActivity: 'Roofing', remarks: '', photosJson: JSON.stringify([P]),
    contractor: 'XYZ', createdBy: 'A', createdDate: '2026-01-05 08:00', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 09:30' });
  assert.deepEqual(saves[1][1].audits, [{ actionType: 'Construction Progress Update', details: 'Updated Lot Progress (Block 3 Lot 2) to 75% (Under Construction)',
    oldValue: 'Progress: 40%, Status: Under Construction', newValue: 'Progress: 75%, Status: Under Construction, Activity: Roofing' }]);

  // Group use: only the lots that change, one audit entry on the first.
  const road = { uuid: 'lot-4', blockNumber: 'RL', lotNumber: '1', lotUse: 'Road Lot' }, open = { uuid: 'lot-5', blockNumber: 'OS', lotNumber: '1' };
  const uses = L.lotsUseSave([fresh, road, open], 'Open Space', who);
  assert.deepEqual(uses.map(([lot, changes]) => [lot.uuid, changes]), [['lot-1', { lotUse: 'Open Space', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 09:30' }],
    ['lot-4', { lotUse: 'Open Space', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 09:30' }]]);
  assert.deepEqual(uses.map(([, , audit]) => audit), [{ actionType: 'Lot Use Changed', details: 'Marked 2 lot(s) as Open Space: B3-L1, BRL-L1', oldValue: 'Saleable, Road Lot', newValue: 'Open Space' }, null]);
  assert.deepEqual(L.lotsUseSave([open], 'Open Space', who), []);

  // An inspection: the snapshot with the lot's billing as it stands, its audit entry, and the lot's progress brought to it.
  const x = L.lotInspection(started, { progress: 99, constructionStatus: 'Substantially Completed', currentActivity: 'Painting', contractor: 'New Co', remarks: 'punch list' }, who);
  const ids = { uuid: 'i-1', projectUuid: 'P', assignedUids: ['u'], uid: 'me', now: 1794562200000 };
  const rec = x.build(ids);
  assert.deepEqual({ ...rec, data: JSON.parse(rec.data) }, { uuid: 'i-1', type: 'sdp_lot_inspections', projectUuid: 'P', assignedUids: ['u'], revision: 1, updatedAt: 1794562200000, updatedByUid: 'me', isDeleted: false,
    data: { inspectionTimestamp: 1794562200000, inspectionDate: '2026-11-13 09:30', inspectedBy: 'Engr. Web', physicalProgress: 99, constructionStatus: 'Substantially Completed', currentActivity: 'Painting',
      contractor: 'New Co', remarks: 'punch list', billingStatus: 'M1 BILLED (80%)', billingReference: 'DV-7', createdDate: '2026-11-13 09:30', _ref_sdpPlanId: 'plan-1', _ref_sdpLotId: 'lot-2' } });
  assert.deepEqual(x.audit, { actionType: 'Inspection Snapshot Recorded', details: 'Recorded formal inspection for Block 3 Lot 2: 99% (Substantially Completed)', oldValue: 'N/A',
    newValue: 'Progress: 99%, Inspector: Engr. Web, Date: 2026-11-13 09:30' });
  assert.deepEqual([x.progress.changes.physicalProgress, x.progress.changes.remarks, x.progress.changes.contractor, x.progress.changes.photosJson, 'billingStatus' in x.progress.changes],
    [99, 'Snapshot recorded: punch list', 'New Co', JSON.stringify([P]), false]);
  // The same values on a lot whose progress already says so change only its record of the inspection.
  const again = { ...started, progress: { ...started.progress, ...x.progress.changes } };
  assert.equal(L.lotInspection(again, { progress: 99, constructionStatus: 'Substantially Completed', currentActivity: 'Painting', contractor: 'New Co', remarks: 'punch list' }, who).progress, null);
  // The lot form still keeps the lot's own contractor.
  assert.equal(L.lotSave(started, { progress: 50, drainage: null, constructionStatus: 'Under Construction', currentActivity: 'CHB laying', remarks: 'old', photos: [P],
    billing: [{ billed: true, reference: '', remarks: '' }, { billed: false, reference: '', remarks: '' }] }, who).changes.contractor, 'XYZ');

  // The history: latest first, each with its change since the one before; another lot's inspections are left out.
  const insp = (uuid, lot, at, pct, date) => ({ uuid, type: 'sdp_lot_inspections', _ref_sdpLotId: lot, inspectionTimestamp: at, physicalProgress: pct, inspectionDate: date });
  const records = [insp('a', 'lot-2', 1, 10, '2026-10-01 08:00'), insp('c', 'lot-2', 3, 30, '2026-10-20 17:00'), insp('b', 'lot-2', 2, 35, '2026-10-13 09:00'), insp('z', 'lot-9', 9, 100, '2026-11-01 08:00')];
  assert.deepEqual(L.lotInspections(records, started).map(i => [i.uuid, i.change]), [['c', '-5 percentage points / 7 days'], ['b', '+25 percentage points / 12 days'], ['a', '']]);
  assert.deepEqual(L.lotInspections(records, { uuid: 'lot-9' }).map(i => i.change), ['First inspection recorded']);
  assert.deepEqual(L.lotInspections(records, fresh), []);
});

test('plan tools: a revision as its own site, the print area, and a block renumbered from where its lots sit, are written like the phone', () => {
  const plan = (uuid, siteName, version, isActive) => ({ uuid, type: 'sdp_plans', siteName, version, isActive, planName: `Plan ${uuid}` });
  // From a first site without a name: the plan leaves as version 1, in use, and the site it leaves is named on its other plans.
  const plans = [plan('a', '', 1, 0), plan('b', '', 2, 1), plan('c', 'Phase 9', 1, 1)];
  assert.deepEqual(L.planOwnSite(plans, plans[1], ' Phase 2 ', ' Phase 1 '), [
    [plans[1], { siteName: 'Phase 2', version: 1, isActive: 1 }, { actionType: 'Project Edit', details: 'Made SDP Plan v2 of Phase 1 its own site: Phase 2',
      oldValue: 'Site: Phase 1, Version: 2', newValue: 'Site: Phase 2, Version: 1' }],
    [plans[0], { siteName: 'Phase 1' }, null]]);
  assert.deepEqual(L.planOwnSite(plans, plans[1], 'Phase 2').map(([p, , audit]) => [p.uuid, audit.oldValue]), [['b', 'Site: Main site, Version: 2']]);
  // From a named site only the plan changes.
  const named = [plan('d', 'East', 1, 0), plan('e', 'East', 2, 1)];
  assert.deepEqual(L.planOwnSite(named, named[1], 'West', 'ignored').map(([p, changes, audit]) => [p.uuid, changes, audit.details]),
    [['e', { siteName: 'West', version: 1, isActive: 1 }, 'Made SDP Plan v2 of East its own site: West']]);

  // The print area: the box between two opposite corners, whichever two were clicked; a box with no width is not one.
  assert.equal(L.planFrame([0.8, 0.25], [0.1, 0.75]), '0.1000,0.2500,0.8000,0.7500');
  assert.deepEqual(L.parseFrame(L.planFrame([0, 0], [1, 1])), [0, 0, 1, 1]);
  assert.equal(L.parseFrame(L.planFrame([0.5, 0.5], [0.5, 0.9])), null);

  // A block of two rows of four lots (10 apart in a row, the rows 20 apart), listed top, bottom, top, bottom...
  const block = [0, 1, 2, 3].flatMap(i => [[i * 10, 0], [i * 10, 20]]);
  assert.deepEqual(L.blockNumbers(block, 1), [1, 2, 3, 4, 5, 6, 7, 8]); // odd along the top row, even along the bottom
  assert.deepEqual(L.blockNumbers(block, 1, true), [1, 8, 2, 7, 3, 6, 4, 5]); // out along the top row, back along the bottom
  assert.deepEqual(L.blockNumbers(block, 1, false, true), [7, 8, 5, 6, 3, 4, 1, 2]);
  assert.deepEqual(L.blockNumbers(block, 1, false, false, true), [2, 1, 4, 3, 6, 5, 8, 7]);
  assert.deepEqual(L.blockNumbers(block, 11, true, true, true), [15, 14, 16, 13, 17, 12, 18, 11]);
  // The same block standing upright: the rows run down the page.
  assert.deepEqual(L.blockNumbers(block.map(([x, y]) => [y, x]), 1), [2, 1, 4, 3, 6, 5, 8, 7]);
  assert.deepEqual(L.blockNumbers([[5, 5]], 7), [7]);
  assert.deepEqual(L.blockNumbers([], 1), []);

  const who = { user: 'Engr. Web', at: '2026-11-13 10:00' };
  const lot = (uuid, blockNumber, lotNumber, extra = {}) => ({ uuid, type: 'sdp_lots', blockNumber, lotNumber, ...extra });
  const found = [lot('l1', '7', '1', { description: 'Auto-detected' }), lot('l2', '7', '2', { description: 'Corner lot' })];
  assert.deepEqual(L.lotsRenumbered(found, [12, 11], '3', '7', who), [
    [found[0], { blockNumber: '3', lotNumber: '12', description: 'Renumbered to the plan', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 10:00' },
      { actionType: 'Lot Geometry Changed', details: 'Renumbered 2 lots of Block 7', oldValue: 'Block 7', newValue: 'Block 3, Lots 11-12' }],
    [found[1], { blockNumber: '3', lotNumber: '11', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 10:00' }, null]]);
  assert.deepEqual(L.lotsRenumbered([lot('l3', '', '')], [5], '4', '', who)[0][2],
    { actionType: 'Lot Geometry Changed', details: 'Numbered 1 unassigned lots', oldValue: 'Unassigned', newValue: 'Block 4, Lots 5-5' });

  assert.deepEqual(L.renumberNotes([lot('a', '1', '1'), lot('b', '1', '1'), lot('c', '', ''), lot('d', '', '', { lotUse: 'Open Space' }), lot('e', '2', '1')]),
    ['1 lot has no block and lot yet.', 'Used twice: B1-L1']);
  assert.deepEqual(L.renumberNotes([lot('a', '1', '1')]), []);

  // A lot's outline edited: its corners, and its area from the scale unless someone typed one. A 100 x 100 point square at 1:1000 is 1244.52 sq.m.
  const square = [[0.1, 0.1], [0.2, 0.1], [0.2, 0.2], [0.1, 0.2]], wide = [[0.1, 0.1], [0.3, 0.1], [0.3, 0.2], [0.1, 0.2]], page = [1000, 1000];
  const drawn = lot('l5', '2', '9', { polygonNormalizedJson: JSON.stringify(square), lotAreaSqM: 1244.52 });
  assert.deepEqual(L.lotOutline(drawn, wide, page, 1000, who), {
    changes: { polygonNormalizedJson: JSON.stringify(wide), lotAreaSqM: 2489.04, lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 10:00' },
    audit: { actionType: 'Lot Geometry Changed', details: 'Updated Lot B2-L9', newValue: 'Updated boundary/info for Lot B2-L9' } });
  assert.equal('lotAreaSqM' in L.lotOutline({ ...drawn, lotAreaSqM: 120 }, wide, page, 1000, who).changes, false); // a typed area stays
  assert.equal('lotAreaSqM' in L.lotOutline(drawn, wide, page, 0, who).changes, false); // no scale, no area
  assert.equal('lotAreaSqM' in L.lotOutline(drawn, wide, null, 1000, who).changes, false); // the PDF could not be read
});

test('find lots: the closed outlines of a plan page become lots, numbered and named like the phone, and replace earlier found lots', () => {
  // A 300 x 300 page: two blocks of 2 rows x 4 lots (30 x 40 a lot, one-pixel lines), an open space between them, and a road strip around.
  const W = 300, H = 300, dark = new Uint8Array(W * H);
  const line = (x1, y1, x2, y2) => { for (let x = x1; x <= x2; x++) for (let y = y1; y <= y2; y++) dark[y * W + x] = 1; };
  const grid = (x, y) => { for (let i = 0; i <= 4; i++) line(x + 30 * i, y, x + 30 * i, y + 80); for (let j = 0; j <= 2; j++) line(x, y + 40 * j, x + 120, y + 40 * j); };
  grid(20, 20); grid(20, 200);
  line(30, 120, 130, 120); line(30, 180, 130, 180); line(30, 120, 30, 180); line(130, 120, 130, 180);
  const centre = (bx, by, col, row) => [bx + 30 * col + 15.5, by + 40 * row + 20.5];

  const areas = L.findLots(dark, W, H);
  assert.deepEqual(areas.map(a => [a.block, a.lot, a.isLot]), [...[1, 2].flatMap(b => [1, 2, 3, 4, 5, 6, 7, 8].map(l => [b, l, true])), [0, 1, false]]);
  // Each lot is its rectangle, to the middle of its lines: block 1's lots run along the top row, then the bottom row.
  const middle = p => [p.reduce((t, q) => t + q[0], 0) / p.length * W, p.reduce((t, q) => t + q[1], 0) / p.length * H];
  const near = (p, [x, y]) => { const [mx, my] = middle(p); return Math.abs(mx - x) < 2 && Math.abs(my - y) < 2; };
  assert.equal(areas.slice(0, 16).every(a => a.polygon.length === 4), true);
  assert.equal(areas.slice(0, 8).every((a, i) => near(a.polygon, centre(20, 20, i % 4, Math.floor(i / 4)))), true);
  assert.equal(areas.slice(8, 16).every((a, i) => near(a.polygon, centre(20, 200, i % 4, Math.floor(i / 4)))), true);
  assert.equal(near(areas[16].polygon, [80.5, 150.5]), true);
  const [x0, x1] = [Math.min(...areas[0].polygon.map(p => p[0])) * W, Math.max(...areas[0].polygon.map(p => p[0])) * W];
  assert.equal(x0 >= 19 && x0 <= 22 && x1 >= 49 && x1 <= 52, true, `lot 1 spans ${x0} to ${x1}`);
  // A page with no lot pattern finds nothing.
  assert.deepEqual(L.findLots(new Uint8Array(W * H), W, H), []);

  // A click inside a lot traces it; on the road (open to the page's edge) it traces nothing.
  // Alone on the page, a traced lot takes its lines' corners too, so each corner comes out cut by a pixel or two, as on the phone.
  const traced = L.traceLot(dark, W, H, 95 / W, 225 / H);
  assert.equal(traced.length >= 4 && traced.length <= 8 && near(traced, centre(20, 200, 2, 0)), true);
  assert.equal(near(L.traceLot(dark, W, H, 95 / W, 240 / H), centre(20, 200, 2, 0)), true); // on a lot line: the nearest open pixel is taken
  assert.equal(L.traceLot(dark, W, H, 200 / W, 150 / H), null);

  // Names: block 1 is labelled "BLOCK 7" in one run; block 2 by "BLK." with "9" beside it. A lot's number is the biggest
  // number in it; "B9 L20" names a lot by itself; a number two lots share names neither.
  const word = (text, [x, y], size = 8) => ({ text, x, y, size });
  const words = [
    word('BLOCK 7', [80, 108]), word('BLK.', [60, 192]), word('9', [80, 192]),
    ...['1', '2', '3', 'L-4', '5', '5', 'LOT 7', '8'].map((t, i) => word(t, centre(20, 20, i % 4, Math.floor(i / 4)))),
    ...['1', '2', '3', '4', '5', '6', '7'].map((t, i) => word(t, centre(20, 200, i % 4, Math.floor(i / 4)))),
    word('150', [centre(20, 200, 0, 0)[0], centre(20, 200, 0, 0)[1] + 9], 4), word('b9 l20', centre(20, 200, 3, 1)), word('TITLE 12', [250, 280]),
  ];
  const lots = areas.filter(a => a.isLot);
  assert.deepEqual(L.lotNames(lots.map(a => a.polygon), lots.map(a => a.block), { words, width: W, height: H }), [
    ['7', '1'], ['7', '2'], ['7', '3'], ['7', '4'], ['', ''], ['', ''], ['7', '7'], ['7', '8'],
    ['9', '1'], ['9', '2'], ['9', '3'], ['9', '4'], ['9', '5'], ['9', '6'], ['9', '7'], ['9', '20']]);
  // A plan with no text names nothing.
  assert.deepEqual(L.lotNames(lots.slice(0, 2).map(a => a.polygon), [1, 1], { words: [], width: W, height: H }), [['', ''], ['', '']]);

  // Adding them: an earlier found lot without progress is replaced; a found area under a lot that stays is left out,
  // and a name a lot that stays already has is not given again.
  const who = { user: 'Engr. Web', at: '2026-11-13 11:00' };
  const plan = { uuid: 'plan-1', version: 2, scaleDenominator: 1000 };
  const box = (a, b, c, d) => JSON.stringify([[a, b], [c, b], [c, d], [a, d]]);
  const drawn = { uuid: 'k1', blockNumber: '7', lotNumber: '2', description: '', polygonNormalizedJson: box(20 / W, 20 / H, 50 / W, 60 / H), progress: null };
  const old = { uuid: 'k2', blockNumber: '', lotNumber: '', description: 'Auto-detected', polygonNormalizedJson: box(0.8, 0.8, 0.9, 0.9), progress: null };
  const started = { uuid: 'k3', blockNumber: '', lotNumber: '', description: 'Auto-detected', polygonNormalizedJson: box(0.8, 0.1, 0.9, 0.2), progress: { uuid: 'pr' } };
  const named = lots.slice(0, 3).map((a, i) => ({ ...a, blockName: '7', lotName: String(i + 1) }));
  const saved = L.foundLots(plan, [drawn, old, started], [...named, areas[16]], [W, H], who);
  assert.deepEqual(saved.stale, [[old, { isActive: 0, lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 11:00' }, null]]);
  assert.deepEqual(saved.audit, { actionType: 'Lot Created', details: 'Added 3 lots found in SDP Plan v2', newValue: '2 lots, 1 larger areas' });
  const ids = { uuid: 'n', projectUuid: 'P', assignedUids: ['u'], uid: 'me', now: 5 };
  const made = saved.builds.map(build => { const r = build(ids); return { type: r.type, ...JSON.parse(r.data) }; });
  assert.deepEqual(made.map(r => [r.blockNumber, r.lotNumber, r.lotUse, r.description]), [['', '', '', 'Auto-detected'], ['7', '3', '', 'Auto-detected'], ['', '', 'Open Space', 'Auto-detected open space or facility']]);
  assert.deepEqual({ ...made[1], polygonNormalizedJson: '', lotAreaSqM: 0 }, { type: 'sdp_lots', blockNumber: '7', lotNumber: '3', housingUnitNumber: '', lotAreaSqM: 0, polygonNormalizedJson: '', isActive: 1,
    description: 'Auto-detected', lotUse: '', createdBy: 'Engr. Web', createdDate: '2026-11-13 11:00', lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 11:00', _ref_sdpPlanId: 'plan-1' });
  assert.deepEqual(JSON.parse(made[1].polygonNormalizedJson), named[2].polygon);
  assert.equal(made[1].lotAreaSqM, L.lotAreaSqM(named[2].polygon, W, H, 1000));
  assert.equal(made[1].lotAreaSqM > 100, true);

  // Remove found lots: only the found lots without progress, with one audit entry.
  assert.deepEqual(L.foundLotsRemoved(plan, [drawn, old, started], who), [[old, { isActive: 0, lastModifiedBy: 'Engr. Web', lastModifiedDate: '2026-11-13 11:00' },
    { actionType: 'Lot Deactivated', details: 'Removed 1 auto-detected lots from SDP Plan v2', oldValue: 'Active', newValue: 'Deactivated' }]]);
  assert.deepEqual([drawn, old, started].map(L.foundLot), [false, true, true]);
});

// A made-up abstract's rows as the record keeps them: three parts at 50, 25.21 and 1, overall 10.73.
const ABSTRACT_ROWS = [
  { kind: 'part', no: '1', name: 'LAND ACQUISITION COST', amount: 1000000, toDate: [null, 500000, 50] },
  { kind: 'subtotal', no: '', name: 'Subtotal (Land Acquisition Cost)', amount: 1000000, toDate: [null, 500000, 50] },
  { kind: 'part', no: '2', name: 'LAND DEVELOPMENT', amount: null, toDate: [null, null, null] },
  { kind: 'group', no: '2.2', name: 'ROADWORKS', amount: null, toDate: [null, null, null] },
  { kind: 'item', no: '2.2.1', name: 'Road Excavation', amount: 1600000, toDate: [null, 404000, 25.25] },
  { kind: 'subtotal', no: '', name: 'Subtotal (Roadworks)', amount: 1600000, period: [null, 80000, 5], toDate: [null, 404000, 25.25] },
  { kind: 'subtotal', no: '', name: 'Subtotal (Land Development)', amount: 2000000, period: [null, 104000, 5.2], toDate: [null, 504000, 25.21] },
  { kind: 'part', no: '3', name: 'HOUSING CONSTRUCTION', amount: null, toDate: [null, null, null] },
  { kind: 'subtotal', no: '', name: 'Subtotal (Housing Construction)', amount: 7000000, toDate: [null, 70000, 1] },
  { kind: 'total', no: '', name: 'GRAND TOTAL', amount: 10000000, toDate: [null, 1074000, 10.73] },
];

test('uploaded abstracts: the record, the latest and a week\'s, the project\'s actual, and the words shown', () => {
  const parts = [{ name: 'LAND ACQUISITION COST', amount: 1000000, actualPct: 50 }, { name: 'LAND DEVELOPMENT', amount: 2000000, actualPct: 25.21 }, { name: 'HOUSING CONSTRUCTION', amount: 7000000, actualPct: 1 }];
  assert.deepEqual(L.abstractRows(JSON.stringify(ABSTRACT_ROWS)), ABSTRACT_ROWS);
  assert.deepEqual([L.abstractRows('not json'), L.abstractRows(''), L.abstractRows('{"a":1}'), L.abstractRows('[null,1]')], [[], [], [], []]);
  assert.deepEqual(L.abstractParts(ABSTRACT_ROWS), parts);
  assert.deepEqual(L.abstractParts([{ kind: 'subtotal', amount: 5 }, { kind: 'part', name: 'A' }]), [{ name: 'A', amount: 0, actualPct: 0 }]);

  const found = { asOf: '2025-03-14', parts, overallPct: 10.73, billing: false, equivUnits: 100, rows: ABSTRACT_ROWS };
  const fields = L.abstractFields(found, 'APA March.xlsx', 'Engr. Web');
  assert.deepEqual(fields, { asOf: '2025-03-14', overallPct: 10.73, rowsJson: JSON.stringify(ABSTRACT_ROWS), fileName: 'APA March.xlsx', uploadedBy: 'Engr. Web', equivUnits: 100 });
  assert.equal(L.abstractFields({ ...found, equivUnits: undefined }, '', '').equivUnits, 0); // a sheet without the equivalent-units columns
  // No grand total on the sheet: the parts by their amounts; no file name or uploader is '' (the columns are NOT NULL on the phone).
  assert.deepEqual((({ overallPct, fileName, uploadedBy }) => [overallPct, fileName, uploadedBy])(L.abstractFields({ ...found, overallPct: null }, undefined, null)), [10.74, '', '']);
  assert.equal(L.abstractFields({ ...found, overallPct: null, parts: [] }, '', '').overallPct, 0);
  const record = L.newAbstractRecord({ uuid: 'u1', projectUuid: 'p1', assignedUids: ['a'], uid: 'me', now: 5 }, fields);
  assert.deepEqual(record, { uuid: 'u1', type: 'project_abstracts', projectUuid: 'p1', assignedUids: ['a'], revision: 1, updatedAt: 5, updatedByUid: 'me', isDeleted: false, data: JSON.stringify(fields) });
  assert.deepEqual(Object.keys(JSON.parse(record.data)), ['asOf', 'overallPct', 'rowsJson', 'fileName', 'uploadedBy', 'equivUnits']);

  const feb = { uuid: 'feb', asOf: '2025-02-28', overallPct: 8, updatedAt: 9 }, mar = { uuid: 'mar', asOf: '2025-03-14', overallPct: 10.73, updatedAt: 2 };
  const again = { uuid: 'again', asOf: '2025-03-14', overallPct: 11, updatedAt: 3 };
  assert.equal(L.latestAbstract([mar, feb]).uuid, 'mar');
  assert.equal(L.latestAbstract([again, mar, feb]).uuid, 'again'); // two devices filed the same date: the last saved
  assert.equal(L.latestAbstract([mar, again]).uuid, 'again');
  assert.equal(L.latestAbstract([]), undefined);
  assert.equal(L.abstractForWeek([feb, mar], 'Mar 8, 2025 - Mar 14, 2025').uuid, 'mar');
  assert.equal(L.abstractForWeek([feb, mar, again], 'Week 11: March 8 - March 14, 2025').uuid, 'again');
  assert.equal(L.abstractForWeek([feb, mar], 'Mar 15, 2025 - Mar 21, 2025').uuid, 'mar'); // a week with none of its own takes the latest before it
  assert.equal(L.abstractForWeek([feb, mar], 'Mar 1, 2025 - Mar 7, 2025').uuid, 'feb'); // never one dated after the week's last day
  assert.equal(L.abstractForWeek([mar], 'Mar 1, 2025 - Mar 7, 2025'), null);
  assert.equal(L.abstractForWeek([feb, mar], 'no dates'), null);
  assert.deepEqual(L.abstractLineActuals(fields), ['50.00', '25.21', '1.00']);
  assert.deepEqual(L.abstractLineActuals({ rowsJson: JSON.stringify(ABSTRACT_ROWS.slice(2, 7)) }), ['0.00', '25.21', '0.00']);
  // Two parts of one line averaged to 25.215 (stored just under it) read 25.21 on both sides; an overrun part reads 100.
  const sub = (amount, pct) => ({ kind: 'subtotal', no: '', name: 'Subtotal', amount, toDate: [null, null, pct] });
  const tie = [{ kind: 'part', no: '2', name: 'LAND DEVELOPMENT' }, sub(300000, 25.21), { kind: 'part', no: '4', name: 'SITE GRADING' }, sub(100000, 25.23)];
  assert.deepEqual(L.abstractLineActuals({ rowsJson: JSON.stringify(tie) }), ['0.00', '25.21', '0.00']);
  assert.deepEqual(L.abstractLineActuals({ rowsJson: JSON.stringify([tie[0], sub(1000, 100.5)]) }), ['0.00', '100.00', '0.00']);
  assert.equal(L.abstractAudit(fields, mar).oldValue, ''); // replaced by a file of the same overall

  // The project's actual: the latest abstract's overall, else the latest weekly report's, else as it is.
  const weekly = [{ reportingWeek: 'Mar 1, 2025 - Mar 7, 2025', actualAccomplishmentPct: 9 }, { reportingWeek: 'Mar 8, 2025 - Mar 14, 2025', actualAccomplishmentPct: 10.5 }];
  assert.equal(L.projectActualAfter([feb, mar], weekly, 3), 10.73);
  assert.equal(L.projectActualAfter([], weekly, 3), 10.5);
  assert.equal(L.projectActualAfter([], [], 3), 3);

  assert.equal(L.abstractProblem(undefined), 'That file could not be read. Pick the abstract saved as an Excel workbook (.xlsx).');
  assert.equal(L.abstractProblem(null), 'No Abstract of Physical Accomplishment was found in that file.');
  assert.equal(L.abstractProblem({ ...found, billing: true }), 'That is the billing abstract. Pick the abstract of the actual accomplishment.');
  assert.equal(L.abstractProblem({ ...found, asOf: null }), 'That abstract has no "As of" date, so it cannot be filed. Check the line under its title.');
  assert.equal(L.abstractProblem(found), '');
  assert.equal(L.abstractSavedNote(fields, false), 'Saved the abstract as of Mar 14, 2025: overall 10.73%.');
  assert.equal(L.abstractSavedNote({ ...fields, overallPct: 11 }, true), 'Replaced the abstract as of Mar 14, 2025: overall 11.00%.');
  assert.equal(L.abstractWeekNote(mar), 'Actual % is from the abstract as of Mar 14, 2025 (Actual Accomplishment tab).');
  assert.deepEqual(L.abstractAudit(fields), { actionType: 'Abstract Upload', details: 'Abstract as of Mar 14, 2025: overall 10.73%', oldValue: '', newValue: '10.73%' });
  assert.deepEqual(L.abstractAudit({ ...fields, overallPct: 11 }, mar), { actionType: 'Updated abstract', details: 'Abstract as of Mar 14, 2025: overall 11.00%', oldValue: '10.73%', newValue: '11.00%' });
  assert.deepEqual(L.deletedAbstractAudit(mar), { actionType: 'Deleted abstract', details: 'Abstract as of Mar 14, 2025: overall 10.73%', oldValue: '10.73%', newValue: '' });
});

test('uploaded abstracts: target, actual and advance/slippage of the three lines and overall', () => {
  const abstract = { asOf: '2025-03-14', overallPct: 10.73, rowsJson: JSON.stringify(ABSTRACT_ROWS) };
  // The approved bar chart on March 14, 2025 (contract day 73): roads 73% planned, housing 23%, overall 53%; it has no land acquisition.
  const project = { dateStarted: '2025-01-01', targetAccomplishment: 12,
    barChartJson: JSON.stringify([{ effective: '', items: [{ name: 'Roads', amount: 60, bars: [[1, 100]] }, { name: 'Housing units', amount: 40, bars: [[51, 150]] }] }]) };
  assert.deepEqual(L.abstractStanding(project, [], abstract), [
    { name: 'Land Acquisition', target: 0, actual: 50, diff: 50 },
    { name: 'Land Development', target: 73, actual: 25.21, diff: -47.79 },
    { name: 'Housing Construction', target: 23, actual: 1, diff: -22 },
    { name: 'Overall', target: 53, actual: 10.73, diff: -42.27 },
  ]);
  // Without a chart a line has no target; the overall target is the project's stored one.
  assert.deepEqual(L.abstractStanding({ targetAccomplishment: 12 }, [], abstract), [
    { name: 'Land Acquisition', target: null, actual: 50, diff: null },
    { name: 'Land Development', target: null, actual: 25.21, diff: null },
    { name: 'Housing Construction', target: null, actual: 1, diff: null },
    { name: 'Overall', target: 12, actual: 10.73, diff: -1.27 },
  ]);
  assert.deepEqual([4.66, -7.58, 0, -0, null].map(L.standingDiffText), ['+4.66%', '-7.58%', '0.00%', '0.00%', '-']);
});

test('monthly abstract: to date from the latest abstract, previous from the one at the end of the month before', () => {
  const part = { kind: 'part', no: '1', name: 'LAND DEVELOPMENT', amount: null, toDate: [null, null, null] };
  const road = { kind: 'item', no: '1.1', name: 'Road Excavation', qty: 1000, unit: 'cu.m.', unitCost: 100, amount: 100000 };
  const subbase = { kind: 'item', no: '1.2', name: 'Subbase', qty: 500, unit: 'cu.m.', unitCost: 200, amount: 100000 };
  const curb = { kind: 'item', no: '1.3', name: 'Curb and Gutter', qty: 200, unit: 'l.m.', unitCost: 50, amount: 10000 };
  const sub = (amount, toDate) => ({ kind: 'subtotal', no: '', name: 'Subtotal', amount, toDate });
  const total = (amount, toDate) => ({ kind: 'total', no: '', name: 'GRAND TOTAL', amount, toDate });
  const filed = (uuid, asOf, overallPct, rows) => ({ uuid, asOf, overallPct, equivUnits: 100, rowsJson: JSON.stringify(rows), fileName: `${uuid}.xlsx`, uploadedBy: 'Engr. Web', updatedAt: 1 });
  const sep = filed('sep', '2026-09-30', 5, [part, { ...road, toDate: [100, 10000, 10] }, total(200000, [null, 10000, 5])]);
  // October leaves Subbase's cells empty; its two subtotals share a name, so they are matched in order.
  const oct = filed('oct', '2026-10-30', 20, [part, { ...road, toDate: [400, 40000, 40] }, { ...subbase, toDate: [null, null, null] },
    sub(200000, [null, 40000, 20]), sub(200000, [null, 40000, 20.5]), total(200000, [null, 40000, 20])]);
  // November's own PREVIOUS and THIS PERIOD (since an abstract of Nov 6 that is not filed here) are not the month's.
  const nov = filed('nov', '2026-11-13', 41.72, [part,
    { ...road, prev: [600, 60000, 60], period: [50.1234, 5012.34, 5.01], toDate: [650.1234, 65012.34, 65.01], balance: [349.8766, 34987.66], equiv: [60, 65.01, 34.99] },
    { ...subbase, toDate: [100.5, 20100, 20.1] }, { ...curb, toDate: [50, 2500, 25] },
    sub(210000, [null, 87612.34, 41.72]), sub(210000, [null, 87612.34, 41.72]), total(210000, [null, 87612.34, 41.72])]);
  const all = [nov, sep, oct];

  const none = error => ({ abstract: null, to: null, from: null, error });
  assert.deepEqual(L.monthlyAbstract(all, ''), none('Pick the date of the monthly abstract.'));
  assert.deepEqual(L.monthlyAbstract(all, 'Nov 15, 2026'), none('Pick the date of the monthly abstract.'));
  assert.deepEqual(L.monthlyAbstract(all, '2026-02-30'), none('Pick the date of the monthly abstract.')); // not on the calendar
  assert.deepEqual(L.monthlyAbstract(all, '2026-09-29'), none('No abstract is filed on or before Sep 29, 2026.'));
  assert.deepEqual(L.monthlyAbstract([], '2026-11-15'), none('No abstract is filed on or before Nov 15, 2026.'));

  const made = L.monthlyAbstract(all, '2026-11-15');
  assert.deepEqual([made.to.uuid, made.from.uuid, made.error], ['nov', 'oct', '']);
  assert.deepEqual((({ rowsJson, ...rest }) => rest)(made.abstract), { asOf: '2026-11-15', overallPct: 41.72, equivUnits: 100, fileName: '', uploadedBy: '' });
  assert.deepEqual(L.abstractRows(made.abstract.rowsJson), [
    { ...part, prev: [null, null, null], period: [null, null, null] },
    // The row keeps its own to date, balance and equivalent units; previous and this period are the month's.
    { ...road, prev: [400, 40000, 40], period: [250.1234, 25012.34, 25.01], toDate: [650.1234, 65012.34, 65.01], balance: [349.8766, 34987.66], equiv: [60, 65.01, 34.99] },
    { ...subbase, toDate: [100.5, 20100, 20.1], prev: [0, 0, 0], period: [100.5, 20100, 20.1] }, // October's cells were empty: previous is 0
    { ...curb, toDate: [50, 2500, 25], prev: [0, 0, 0], period: [50, 2500, 25] }, // a row new since October
    { ...sub(210000, [null, 87612.34, 41.72]), prev: [null, 40000, 20], period: [null, 47612.34, 21.72] },
    { ...sub(210000, [null, 87612.34, 41.72]), prev: [null, 40000, 20.5], period: [null, 47612.34, 21.22] },
    { ...total(210000, [null, 87612.34, 41.72]), prev: [null, 40000, 20], period: [null, 47612.34, 21.72] },
  ]);
  assert.equal(L.monthlyAbstractNote(made.to, made.from), 'To date is the abstract as of Nov 13, 2026. Previous is the abstract as of Oct 30, 2026.');

  // The month before is the one before the picked date's, whatever was filed last: Oct 31 reads October's against September's.
  assert.deepEqual((({ to, from }) => [to.uuid, from.uuid])(L.monthlyAbstract(all, '2026-10-31')), ['oct', 'sep']);
  assert.deepEqual((({ to, from }) => [to.uuid, from.uuid])(L.monthlyAbstract(all, '2027-01-05')), ['nov', 'nov']); // January: previous is as of Dec 31
  // Nothing filed in the picked month yet: both are the same abstract, so this period is zero.
  const idle = L.monthlyAbstract(all, '2026-10-15');
  assert.deepEqual([idle.to.uuid, idle.from.uuid], ['sep', 'sep']);
  assert.deepEqual(L.abstractRows(idle.abstract.rowsJson).map(r => r.period), [[null, null, null], [0, 0, 0], [null, 0, 0]]);

  // No abstract by the end of the month before: previous is zero.
  const first = L.monthlyAbstract(all, '2026-09-30');
  assert.deepEqual([first.to.uuid, first.from, first.error], ['sep', null, '']);
  assert.deepEqual(L.abstractRows(first.abstract.rowsJson).map(r => [r.prev, r.period]),
    [[[null, null, null], [null, null, null]], [[0, 0, 0], [100, 10000, 10]], [[null, 0, 0], [null, 10000, 5]]]);
  assert.equal(L.monthlyAbstractNote(first.to, first.from), 'To date is the abstract as of Sep 30, 2026. Previous is zero: no abstract was filed by the end of the month before.');
});

test('abstracts: what is billed never passes the actual, and the billing abstract is made from the uploaded abstract', () => {
  const project = { scopeOfWork: JSON.stringify([{ name: 'Land Acquisition', amount: 1000000, weightPct: 10 }, { name: 'Land Development', amount: 2000000, weightPct: 20 },
    { name: 'Construction of Housing Units', amount: 7000000, weightPct: 70 }]), targetAccomplishment: 12, actualAccomplishment: 10.74, numberOfUnits: '100 units' };
  const lines = (a, b, c) => JSON.stringify([['Land Acquisition', a, 10], ['Land Development', b, 20], ['Housing Construction', c, 70]].map(([itemDescription, actualPct, weightPct]) => ({ itemDescription, targetPct: 0, actualPct, weightPct })));
  // Newest first, as the site lists them; the latest is the one of the latest week, whenever it was typed.
  const weekly = [{ uuid: 'late', reportingWeek: 'Mar 1, 2025 - Mar 7, 2025', actualAccomplishmentPct: 9, accomplishmentItemsJson: lines(50, 20, 0) },
    { uuid: 'w2', reportingWeek: 'Mar 8, 2025 - Mar 14, 2025', actualAccomplishmentPct: 10.74, accomplishmentItemsJson: lines(50, 25.21, 1) }];
  assert.equal(L.latestWeekly(weekly).uuid, 'w2');
  assert.equal(L.latestWeekly([]), undefined);
  assert.deepEqual(L.carriedActuals(weekly), [{ actual: '50.00' }, { actual: '25.21' }, { actual: '1.00' }]);
  assert.deepEqual(L.carriedActuals([]), []);
  assert.deepEqual(L.partAmounts(project, [], '2025-03-14'), [1000000, 2000000, 7000000]);
  assert.deepEqual(L.partAmounts({ scopeOfWork: '' }, [], '2025-03-14'), [0, 0, 0]);
  // The abstract's parts fill the three Actual % boxes; a part it does not have leaves its box alone.
  assert.deepEqual(L.abstractActuals([{ name: 'LAND ACQUISITION COST', amount: 1000000, actualPct: 50 }, { name: 'LAND DEVELOPMENT', amount: 2000000, actualPct: 25.21 },
    { name: 'HOUSING CONSTRUCTION', amount: 7000000, actualPct: 1 }]), ['50.00', '25.21', '1.00']);
  assert.deepEqual(L.abstractActuals([{ name: 'LAND DEVELOPMENT', amount: 2000000, actualPct: 25.21 }]), [null, '25.21', null]);
  assert.deepEqual(L.abstractActuals([]), [null, null, null]);

  const payments = [{ uuid: 'a', billingType: 'Land acquisition', grossAmount: 400000, date: '2025-02-01' }, { uuid: 'adv', billingType: '', grossAmount: 1500000, date: '2025-02-02' },
    { uuid: 'b', billingType: 'Land development', grossAmount: 300000, unitPrice: 20000, date: '2025-03-01', periodCovered: 'February 1 - 28, 2025' }];
  const form = { date: '2025-03-15', billingType: 'Land development', grossAmount: '204,200', units: '', unitPrice: '20000' };
  const ahead = (f, old, w = weekly) => L.billingAheadErrors(project, [], payments, w, f, old);
  assert.deepEqual(ahead(form), []); // 504,200 of 2,000,000 is 25.21%, the actual: equal is allowed
  assert.deepEqual(ahead({ ...form, grossAmount: '204400' }), ['Land Development: billed to date 25.22% would pass the actual 25.21% of Mar 8, 2025 - Mar 14, 2025.']);
  assert.deepEqual(ahead({ ...form, billingType: 'M1 - Housing unit', units: '1', unitPrice: '80000' }), ['Housing Construction: billed to date 1.14% would pass the actual 1.00% of Mar 8, 2025 - Mar 14, 2025.']);
  // The advance payment has no type, so it is not in the abstract and not counted.
  assert.deepEqual(ahead({ ...form, billingType: '', grossAmount: '9000000' }), []);
  assert.deepEqual(ahead({ ...form, billingType: 'Land acquisition', units: '1', unitPrice: '100000' }, null, [{ ...weekly[1], actualAccomplishmentPct: 7.5 }]),
    ['Overall: billed to date 8.00% would pass the actual 7.50% of Mar 8, 2025 - Mar 14, 2025.']);
  assert.deepEqual(ahead(form, null, []), ['Land Development: billed to date 25.21% would pass the actual 0.00%.']);
  // An edit is checked only when it raises the billing, so one already saved can still be corrected.
  assert.deepEqual(ahead({ ...form, grossAmount: '300000' }, payments[2], []), []);
  assert.deepEqual(ahead({ ...form, grossAmount: '504,400' }, payments[2]), ['Land Development: billed to date 25.22% would pass the actual 25.21% of Mar 8, 2025 - Mar 14, 2025.']);

  // An old billing saved as lots x price: its gross at 2 decimals parses a hair above the saved one, which is not a raise.
  assert.deepEqual(ahead({ ...form, grossAmount: '35219729.88' }, { ...payments[2], grossAmount: 81 * 434811.48 }, []), []);

  // With an uploaded abstract the actual is the latest abstract's, whatever the weekly reports say.
  const uploaded = { asOf: '2025-03-14', overallPct: 10.73, rowsJson: JSON.stringify(ABSTRACT_ROWS) };
  const byAbstract = (f, a = uploaded, w = []) => L.billingAheadErrors(project, [], payments, w, f, null, [a]);
  const acquisition = { ...form, billingType: 'Land acquisition', units: '1', unitPrice: '100000' };
  assert.deepEqual(byAbstract(form), []);
  assert.deepEqual(byAbstract({ ...form, grossAmount: '204,400' }), ['Land Development: billed to date 25.22% would pass the actual 25.21% of the abstract as of Mar 14, 2025.']);
  assert.deepEqual(byAbstract(acquisition, { ...uploaded, overallPct: 7.5 }), ['Overall: billed to date 8.00% would pass the actual 7.50% of the abstract as of Mar 14, 2025.']);
  // The weekly reports say 10.74% overall and would let this one through: the abstract wins.
  assert.deepEqual(ahead(acquisition), []);
  assert.deepEqual(byAbstract(acquisition, { ...uploaded, overallPct: 7.5 }, weekly), ['Overall: billed to date 8.00% would pass the actual 7.50% of the abstract as of Mar 14, 2025.']);
  // A line the abstract has no part for has no work done.
  assert.deepEqual(byAbstract({ ...form, grossAmount: '1' }, { ...uploaded, rowsJson: JSON.stringify(ABSTRACT_ROWS.slice(0, 2)) }),
    ['Land Development: billed to date 15.00% would pass the actual 0.00% of the abstract as of Mar 14, 2025.']);

  assert.equal(L.abstractUnits(project), 100);
  assert.equal(L.abstractUnits({ numberOfUnits: '' }, 42), 42);
  assert.equal(L.abstractUnits({}), 1);

  // The billing abstract is printed from the uploaded abstract: its own part amounts, and its this-period amounts to start from.
  assert.deepEqual(L.abstractLineAmounts(uploaded), [1000000, 2000000, 7000000]);
  assert.deepEqual(L.abstractPeriodAmounts(uploaded), [0, 104000, 0]); // a part's own subtotal, not its groups'
  assert.deepEqual([L.abstractLineAmounts({ rowsJson: '' }), L.abstractPeriodAmounts({ rowsJson: '' })], [[0, 0, 0], [0, 0, 0]]);
  // Previous: the saved billings dated on or before the As-of date (March 14); the one of April 1 is not in it, the one of the day is.
  const saved = [...payments, { uuid: 'c', billingType: 'Land development', grossAmount: 1, date: '2025-04-01' }, { uuid: 'd', billingType: 'M1 - Housing unit', grossAmount: 56000, date: ' 2025-03-14 ' },
    { uuid: 'e', billingType: 'Land development', grossAmount: 9, date: '' }];
  // Typed for this billing: 5 lots, 5.2 lots of land development (104,000), 2 units at Milestone 1 and 1 at Milestone 2, of 100 units.
  const period = { covered: 'March 1 - 14, 2025', lots: 5, development: 5.2, m1: 2, m2: 1 };
  const abstract = L.billingAbstract(project, [], saved, uploaded, period, 100);
  assert.equal(abstract.targetPct, 12); // no bar chart: the project's stored target
  assert.deepEqual(abstract.parts.map(p => [p.name, p.heading]), [['Land Acquisition Cost', undefined], ['Land Development', undefined], ['Housing Construction', ['3', 'HOUSING CONSTRUCTION']]]);
  assert.deepEqual(abstract.parts.flatMap(p => p.lines), [
    { no: '1', name: 'LAND ACQUISITION COST', unit: 'LOT', type: 'Land acquisition', amount: 1000000, previous: 400000, thisPeriod: 50000 },
    { no: '2', name: 'LAND DEVELOPMENT', unit: 'LOT', type: 'Land development', amount: 2000000, previous: 300000, thisPeriod: 104000 },
    { no: '3.1', name: 'MILESTONE 1 (80%)', unit: 'UNIT', type: 'M1 - Housing unit', amount: 5600000, previous: 56000, thisPeriod: 112000 },
    { no: '3.2', name: 'MILESTONE 2 (20%)', unit: 'UNIT', type: 'M2 - Housing unit', amount: 1400000, previous: 0, thisPeriod: 14000 },
  ]);
  // Printing warns of a part billed above its line's actual (housing: 182,000 of 7,000,000 against 1%), and of the grand total above the overall.
  assert.deepEqual(L.billingAbstractWarnings(uploaded, abstract), ['Housing Construction: billed to date 2.60% is above the actual 1.00%.']);
  assert.deepEqual(L.billingAbstractWarnings(uploaded, L.billingAbstract(project, [], saved, uploaded, { ...period, development: 11 }, 100)), [
    'Land Development: billed to date 26.00% is above the actual 25.21%.', 'Housing Construction: billed to date 2.60% is above the actual 1.00%.',
    'Grand total: billed to date 11.52% is above the actual 10.73%.']);
  assert.deepEqual(L.billingAbstractWarnings(uploaded, L.billingAbstract(project, [], [], uploaded, {}, 100)), []);
  // A blank, unreadable or negative figure is 0; a quantity's amount is at 2 decimals (1 of 3 units of 1,000,000 is 333,333.33).
  assert.deepEqual(L.billingAbstract(project, [], [], uploaded, { covered: '', lots: 1, development: -5, m1: 'x', m2: '' }, 3).parts.flatMap(p => p.lines.map(l => l.thisPeriod)), [333333.33, 0, 0, 0]);
  // Land development is typed in lots too: 40 of 370 lots of 31,820,000 (86,000 a lot) are 3,440,000.
  const dev = { ...uploaded, rowsJson: JSON.stringify([{ kind: 'part', name: 'LAND DEVELOPMENT' }, { kind: 'subtotal', amount: 31820000 }]) };
  assert.deepEqual(L.billingAbstract(project, [], [], dev, { development: 40 }, 370).parts.flatMap(p => p.lines.map(l => l.thisPeriod)), [3440000]);
  // The target is the bar chart's plan on the As-of date when the project has one.
  assert.equal(L.billingAbstract({ dateStarted: '2025-01-01', targetAccomplishment: 12, barChartJson: JSON.stringify([{ effective: '', items: [{ name: 'Roads', amount: 60, bars: [[1, 100]] }, { name: 'Housing units', amount: 40, bars: [[51, 150]] }] }]) },
    [], [], uploaded, period, 100).targetPct, 53);
  // An abstract without housing has no housing lines.
  assert.deepEqual(L.billingAbstract(project, [], payments, { ...uploaded, rowsJson: JSON.stringify(ABSTRACT_ROWS.slice(2, 7)) }, period, 100).parts.map(p => p.name), ['Land Development']);
});

test('unitsStanding: the abstract to date in lots and units at the billing abstract unit costs', () => {
  // 370 units: 70,907.06 a lot of land acquisition, 86,000 of land development, 618,287.80 a house (494,630.24 + 123,657.56).
  const sheet = (asOf, housingDone, parts = 3) => ({ asOf, rowsJson: JSON.stringify([
    { kind: 'part', name: 'LAND ACQUISITION COST' }, { kind: 'subtotal', amount: 26235612.2, toDate: [null, 26235612.2, 100] },
    { kind: 'part', name: 'LAND DEVELOPMENT' }, { kind: 'subtotal', amount: 31820000, toDate: [null, 15910000, 50] },
    { kind: 'part', name: 'HOUSING CONSTRUCTION' }, { kind: 'subtotal', amount: 228766486, toDate: [null, housingDone, 0] },
  ].slice(0, parts * 2)) });
  const abstract = sheet('2026-11-14', 100000000);
  assert.deepEqual(L.abstractToDateAmounts(abstract), [26235612.2, 15910000, 100000000]);
  const billings = [{ billingType: 'Land acquisition', grossAmount: 26235612.2, date: '2026-01-10' }, { billingType: 'Land development', grossAmount: 12900000, date: '2026-11-20' },
    { billingType: 'M1 - Housing unit', grossAmount: 74194536, date: '' }, { billingType: '', grossAmount: 5000000, date: '2026-01-01' }];
  const rows = (a, all = [a]) => L.unitsStanding(a, all, billings, 370).map(r => [r.name, r.unit, r.contract, r.done, r.billed, r.canBill]);
  // The latest abstract counts every billing, whatever its date.
  assert.deepEqual(rows(abstract), [
    ['Land acquisition', 'lots', 370, 370, 370, 0],
    ['Land development', 'lots', 370, 185, 150, 35],
    ['Milestone 1 (80%)', 'units', 370, 202.17, 150, 52],
    ['Milestone 2 (20%)', 'units', 370, 0, 0, 0],
  ]);
  // Milestone 1 fills first; the rest of the housing to date is Milestone 2.
  assert.deepEqual(rows(sheet('2026-11-14', 200000000)).slice(2).map(r => r.slice(3)), [[370, 150, 220], [137.37, 0, 137]]);
  // An older abstract counts only the billings dated up to its As of: not the one of Nov 20 or the undated one.
  assert.deepEqual(rows(abstract, [abstract, sheet('2026-11-21', 0)]).slice(1).map(r => r.slice(4)), [[0, 185], [0, 202], [0, 0]]);
  // Without a housing part there are only the land rows.
  assert.deepEqual(rows(sheet('2026-11-14', 0, 2)).map(r => r[0]), ['Land acquisition', 'Land development']);
});

// A made-up project: Land Acquisition is a part that is its own pay line; group subtotals carry no % on the sheet.
const NEXT_ROWS = (() => {
  const none = [null, null, null];
  const line = (kind, no, name, qty, unit, unitCost, toDate) => ({ kind, no, name, qty, unit, unitCost, amount: Math.round(qty * unitCost * 100) / 100,
    prev: [0, 0, 0], period: [null, 0, 0], toDate, balance: [0, 0], equiv: [0, 0, 0] });
  const head = (kind, no, name) => ({ kind, no, name, qty: null, unit: '', unitCost: null, amount: null, prev: none, period: none, toDate: none, balance: [null, null], equiv: none });
  const sum = (kind, name, amount, done, pct, shown) => ({ ...head(kind, '', name), amount, prev: [null, 0, shown ? 0 : null], period: [null, 0, shown ? 0 : null], toDate: [null, done, pct] });
  return [
    line('part', '1', 'LAND ACQUISITION COST', 520, 'LOT', 65250.5, [420, 27405210, 80.77]),
    sum('subtotal', 'Subtotal (Land Acquisition Cost)', 33930260, 27405210, 80.77, true),
    head('part', '2', 'LAND DEVELOPMENT'),
    head('group', '2.1', 'GENERAL REQUIREMENTS'),
    line('item', '2.1.1', 'Mobilization/Demobilization', 1, 'LOT', 2150000.75, [0.6, 1290000.45, 60]),
    line('item', '2.1.2', 'Permits & Licences', 1, 'P.S.', 350000, [1, 350000, 100]),
    sum('subtotal', 'Subtotal (General Requirements)', 2500000.75, 1640000.45, 65.6, false),
    head('group', '2.2', 'DRAINAGE WORKS'),
    line('item', '2.2.1', 'Catch basin', 96, 'unit', 18500, [36, 666000, 37.5]),
    sum('subtotal', 'Subtotal (Drainage Works)', 1776000, 666000, 37.5, false),
    sum('subtotal', 'Subtotal (Land Development)', 4276000.75, 2306000.45, 53.93, true),
    sum('total', 'GRAND TOTAL', 38206260.75, 29711210.45, 77.77, true),
  ];
})();

test('the next abstract is made from the one before it: previous is its to date, the typed quantity gives the rest', () => {
  assert.deepEqual([...L.abstractCover(NEXT_ROWS)], [[1, [0]], [6, [4, 5]], [9, [8]], [10, [4, 5, 8]], [11, [0, 4, 5, 8]]]);
  const next = L.nextAbstractRows(NEXT_ROWS, { 0: 20, 4: 0.25, 8: 10.5 }, 520);
  const of = i => (({ prev, period, toDate, balance, equiv }) => ({ prev, period, toDate, balance, equiv }))(next[i]);
  assert.deepEqual(of(0), { prev: [420, 27405210, 80.77], period: [20, 1305010, 3.85], toDate: [440, 28710220, 84.62], balance: [80, 5220040], equiv: [420, 440, 80] });
  assert.deepEqual(of(1), { prev: [null, 27405210, 80.77], period: [null, 1305010, 3.85], toDate: [null, 28710220, 84.62], balance: [null, null], equiv: [null, null, null] });
  assert.deepEqual(of(4), { prev: [0.6, 1290000.45, 60], period: [0.25, 537500.19, 25], toDate: [0.85, 1827500.64, 85], balance: [0.15, 322500.11], equiv: [312, 442, 78] });
  assert.deepEqual(of(5), { prev: [1, 350000, 100], period: [null, 0, 0], toDate: [1, 350000, 100], balance: [0, 0], equiv: [520, 520, 0] }); // nothing typed: no work this period
  assert.deepEqual(of(6), { prev: [null, 1640000.45, null], period: [null, 537500.19, null], toDate: [null, 2177500.64, 87.1], balance: [null, null], equiv: [null, null, null] });
  assert.deepEqual(of(8), { prev: [36, 666000, 37.5], period: [10.5, 194250, 10.94], toDate: [46.5, 860250, 48.44], balance: [49.5, 915750], equiv: [195, 251.88, 268.12] });
  assert.deepEqual(of(10).period, [null, 731750.19, 17.11]);
  assert.deepEqual(of(10).toDate, [null, 3037750.64, 71.04]);
  assert.deepEqual(of(11), { prev: [null, 29711210.45, 77.77], period: [null, 2036760.19, 5.33], toDate: [null, 31747970.64, 83.1], balance: [null, null], equiv: [null, null, null] });
  // What is copied from the project's abstract stays as it is: the works, quantities, units, unit costs and amounts.
  assert.deepEqual(next.map(r => [r.kind, r.no, r.name, r.qty, r.unit, r.unitCost, r.amount]), NEXT_ROWS.map(r => [r.kind, r.no, r.name, r.qty, r.unit, r.unitCost, r.amount]));
  assert.deepEqual(L.nextAbstractRows(NEXT_ROWS, {})[0].equiv, [null, null, null]); // a sheet without the equivalent-units columns
  // The one after it continues from these rows.
  assert.deepEqual(L.nextAbstractRows(next, { 0: 80 }, 520)[0].toDate, [520, 33930260, 100]);

  const source = { asOf: '2026-11-13', rowsJson: JSON.stringify(NEXT_ROWS), equivUnits: 520, updatedAt: 1 };
  assert.deepEqual(L.nextAbstractFields(source, next, '2026-11-20', 'Engr. Web'),
    { asOf: '2026-11-20', overallPct: 83.1, rowsJson: JSON.stringify(next), fileName: '', uploadedBy: 'Engr. Web', equivUnits: 520 });
  assert.equal(L.abstractAudit(L.nextAbstractFields(source, next, '2026-11-20', '')).actionType, 'New abstract');

  assert.deepEqual(L.nextAbstractErrors(NEXT_ROWS, { 0: 20, 4: 0.25 }), []);
  assert.deepEqual(L.nextAbstractErrors(NEXT_ROWS, { 0: 101, 4: -0.7, 5: NaN, 8: 60 }), [
    '1 LAND ACQUISITION COST: to date 521 would pass the quantity 520.', '2.1.1 Mobilization/Demobilization: to date would go below zero.',
    '2.1.2 Permits & Licences: this period must be a number.']);
  assert.deepEqual(L.nextAbstractErrors(NEXT_ROWS, { 8: 60.5 }), ['2.2.1 Catch basin: to date 96.5 would pass the quantity 96.']);
  // A line already over its quantity is carried; only more work on it is refused.
  const over = NEXT_ROWS.map((r, i) => (i === 8 ? { ...r, toDate: [97, 1794500, 101.04] } : r));
  assert.deepEqual(L.nextAbstractErrors(over, {}), []);
  assert.deepEqual(L.nextAbstractErrors(over, { 8: -1 }), []);
  assert.deepEqual(L.nextAbstractErrors(over, { 8: 1 }), ['2.2.1 Catch basin: to date 98 would pass the quantity 96.']);
  // A line done a third at a time ends at 100.00: its to date % is its to date amount over its amount.
  const third = [{ ...NEXT_ROWS[8], qty: 3, unitCost: 1000, amount: 3000, toDate: [0, 0, 0] }, { ...NEXT_ROWS[11], amount: 3000, toDate: [null, 0, 0] }];
  const thrice = [1, 2, 3].reduce(rows => L.nextAbstractRows(rows, { 0: 1 }), third);
  assert.deepEqual([thrice[0].toDate, thrice[0].balance, thrice[1].toDate], [[3, 3000, 100], [0, 0], [null, 3000, 100]]);
});

test('the next abstract takes housing construction items in houses: each house is QTY / the housing units', () => {
  const head = (kind, no, name) => ({ kind, no, name, qty: null, amount: null });
  const item = (no, name, qty, unit, unitCost) => ({ kind: 'item', no, name, qty, unit, unitCost, amount: qty * unitCost, toDate: [0, 0, 0] });
  const rows = [head('part', '2', 'LAND DEVELOPMENT'), item('2.1', 'Catch basin', 96, 'unit', 100),
    head('part', '3', 'HOUSING CONSTRUCTION'), item('3.1', 'Soil poisoning', 10508, 'sq.m', 50), head('group', '3.2', 'ROOFING'), item('3.2.1', 'Roofing', 18500, 'sq.m', 10),
    { kind: 'subtotal', name: 'Subtotal (Housing Construction)', amount: 710400, toDate: [null, 0, 0] }];
  assert.deepEqual(L.abstractHouseLines(rows, 370), [3, 5]);
  // With no housing units (the fallback 1) nothing is in houses.
  assert.deepEqual(L.abstractHouseLines(rows, 1), []);
  assert.deepEqual(L.abstractHouses(rows, { 3: 120 }, 1), { 3: 120 });
  // 120 houses of soil poisoning is 120 x 10,508 / 370 = 3,408 sq.m; land development stays as typed; a NaN stays one, for nextAbstractErrors to name.
  const typed = L.abstractHouses(rows, { 1: 12, 3: 120, 5: 12.5 }, 370);
  assert.deepEqual(typed, { 1: 12, 3: 3408, 5: 625 });
  assert.equal(L.nextAbstractRows(rows, typed)[3].period[1], 170400);
  assert.deepEqual(L.abstractHouses(rows, typed, 370, true), { 1: 12, 3: 120, 5: 12.5 }); // a saved abstract's quantities back as houses
  assert.ok(Number.isNaN(L.abstractHouses(rows, { 3: NaN }, 370)[3]));
  // A quantity that does not split evenly comes back to the houses typed.
  assert.deepEqual(L.abstractHouses(rows, L.abstractHouses(rows, { 3: 7 }, 9), 9, true), { 3: 7 });
  // The last house gives the QTY exactly, however the abstracts before it rounded, and no more.
  // `houses` abstract after abstract: the last one's line and the errors saving it gives.
  const line = (qty, houses) => {
    const one = [head('part', '3', 'HOUSING CONSTRUCTION'), item('3.1', 'Soil poisoning', qty, 'sq.m', 1)];
    const before = houses.slice(0, -1).reduce((done, h) => L.nextAbstractRows(done, L.abstractHouses(done, { 1: h }, 370)), one), last = L.abstractHouses(before, { 1: houses.at(-1) }, 370);
    return [L.nextAbstractRows(before, last)[1], L.nextAbstractErrors(before, last)];
  };
  for (const [qty, houses] of [[1000, [19, 19, 332]], [1000, [1, 18, 351]], [12345.67, [100, 100, 100, 70]], [10508, [35.5, 0, 334.5]]]) {
    const [done, errors] = line(qty, houses);
    assert.deepEqual([done.toDate[0], done.balance[0], errors], [qty, 0, []], `${qty}: ${houses}`);
  }
  assert.match(line(1000, [19, 19, 333])[1].join(), /would pass the quantity 1,000/); // past the units is still refused
  // A box takes at most what is left: 1000 - 2 x 51.3514 sq.m is 332 houses, which gives the QTY; a land line in its own unit.
  const two = [19, 19].reduce(done => L.nextAbstractRows(done, L.abstractHouses(done, { 1: 19 }, 370)), [head('part', '3', 'HOUSING CONSTRUCTION'), item('3.1', 'Soil poisoning', 1000, 'sq.m', 1)]);
  assert.deepEqual(L.nextAbstractMost(two, 370), { 1: 332 });
  assert.deepEqual(L.nextAbstractMost([{ ...rows[1], toDate: [90.5, 0, 0] }, { ...rows[1], toDate: [100, 0, 0] }], 370), { 0: 5.5, 1: 0 }); // already past its QTY: 0 more
  assert.deepEqual(['400', '1,000', '332', '12.', '-5', 'abc', ''].map(t => L.cappedBox(t, 332)), ['332', '332', '332', '12.', '-5', 'abc', '']);
  assert.equal(L.cappedBox('400', undefined), '400'); // not a pay line
  // Past 4 decimals: a quantity is cut, so it saves; a sliver of a house is still typed, and the snap ends it at the QTY.
  const fine = [{ ...rows[1], qty: 1.23456 }], sliver = [head('part', '3', 'HOUSING CONSTRUCTION'), { ...item('3.1', 'Soil poisoning', 3408, 'sq.m', 1), toDate: [3407.999, 0, 0] }];
  assert.deepEqual([L.nextAbstractMost(fine, 370), L.nextAbstractErrors(fine, { 0: 1.2345 })], [{ 0: 1.2345 }, []]);
  assert.deepEqual(L.nextAbstractMost(sliver, 120), { 1: 0.0001 });
  assert.equal(L.nextAbstractRows(sliver, L.abstractHouses(sliver, { 1: 0.0001 }, 120))[1].toDate[0], 3408);
  // A box left as it opened keeps its saved quantity: 1000 sq.m opens as 35.2113 houses, which would be 1,000.0009.
  const opened = { 3: ['35.2113', 1000] };
  assert.deepEqual({ ...L.abstractHouses(rows, { 3: 35.2113 }, 370), ...L.untouchedQuantities({ 3: '35.2113' }, opened) }, { 3: 1000 });
  assert.deepEqual(L.untouchedQuantities({ 3: '35.2114' }, opened), {});
  assert.deepEqual(L.untouchedQuantities({ 3: '1' }, { 3: '1' }), {}); // not a kept pair
  // The draft keeps what the boxes opened with.
  const draft = L.nextAbstractDraft({ asOf: '2026-11-20', from: 'a|b|370', text: { 3: '35.2113' }, opened, kept: true });
  assert.deepEqual(L.keptNextAbstract(JSON.stringify(draft)).opened, opened);
  assert.deepEqual(L.keptNextAbstract(JSON.stringify({ ...draft, opened: undefined })).opened, {});
});

test('the next abstract continues the latest one dated before it', () => {
  const first = { uuid: 'a', asOf: '2026-11-13', rowsJson: JSON.stringify(NEXT_ROWS), equivUnits: 520, updatedAt: 1 };
  const second = { uuid: 'b', asOf: '2026-11-20', rowsJson: JSON.stringify(L.nextAbstractRows(NEXT_ROWS, { 0: 20, 8: 10.5 }, 520)), equivUnits: 520, updatedAt: 2 };
  assert.deepEqual(L.nextAbstractSource([first], '2026-11-20'), { source: first, same: null, error: '' });
  assert.equal(L.nextAbstractSource([first], '').error, 'Pick the "As of" date.');
  assert.equal(L.nextAbstractSource([first], '2026-11-13').error, "The date must be after Nov 13, 2026, the first abstract's.");
  assert.equal(L.nextAbstractSource([first, second], '2026-11-19').error, "The date must not be before the latest abstract's, Nov 20, 2026.");
  // The latest date again: it is made again from the one before it, starting from what was typed for it.
  assert.deepEqual(L.nextAbstractSource([first, second], '2026-11-20'), { source: first, same: second, error: '' });
  assert.deepEqual(L.nextAbstractTyped(NEXT_ROWS, second), { 0: 20, 8: 10.5 });
  assert.deepEqual(L.nextAbstractTyped(NEXT_ROWS, null), {});
  assert.deepEqual(L.nextAbstractTyped(NEXT_ROWS.slice(1), second), {}); // other rows: nothing is carried
  assert.equal(L.nextAbstractSource([first, second], '2026-11-27').source, second);
  // Subtotals that do not add up from the items cannot be continued.
  const off = { ...first, rowsJson: JSON.stringify(NEXT_ROWS.map((r, i) => (i === 9 ? { ...r, amount: 1800000 } : r))) };
  assert.equal(L.nextAbstractSource([off], '2026-11-20').error,
    '"Subtotal (Drainage Works)" does not add up from its items in the abstract as of Nov 13, 2026, so the next one cannot be made from it. Upload the next abstract instead.');
  assert.equal(L.nextAbstractProblem({ ...first, rowsJson: JSON.stringify(NEXT_ROWS.slice(0, 10)) }),
    'There are no pay items with a grand total in the abstract as of Nov 13, 2026, so the next one cannot be made from it. Upload the next abstract instead.');
});

test('undo of a saved abstract: back to the one it replaced, or removed when the date had none; not once it was saved again', () => {
  const fields = asOf => ({ asOf, overallPct: 12.5, rowsJson: '[{"kind":"total"}]', fileName: 'new.xlsx', uploadedBy: 'Engr. Web', equivUnits: 370 });
  const old = { uuid: 'old', type: 'project_abstracts', revision: 2, updatedAt: 5, asOf: '2026-11-13', overallPct: 10.73, rowsJson: '[]', fileName: 'old.xlsx', uploadedBy: 'Engr. Phone', equivUnits: 370 };
  const before = { asOf: '2026-11-13', overallPct: 10.73, rowsJson: '[]', fileName: 'old.xlsx', uploadedBy: 'Engr. Phone', equivUnits: 370 };
  // A save that replaced the abstract of its date keeps that one's six columns.
  const replaced = L.abstractUndo(fields('2026-11-13'), old);
  assert.deepEqual(replaced, { asOf: '2026-11-13', rowsJson: '[{"kind":"total"}]', before });
  const now = { ...old, ...fields('2026-11-13'), revision: 3, updatedAt: 9 }, kept = JSON.stringify(replaced);
  assert.deepEqual(L.abstractUndoEdit(kept, [now]), { rec: now, changes: before,
    audit: { actionType: 'Updated abstract', details: 'Abstract as of Nov 13, 2026: overall 10.73%', oldValue: '12.50%', newValue: '10.73%' } });
  assert.equal(L.abstractUndoAsk(L.abstractUndoEdit(kept, [now])), 'Undo the last save? The abstract as of Nov 13, 2026 goes back to the one before it (overall 10.73%).');
  assert.equal(L.abstractUndoneNote(L.abstractUndoEdit(kept, [now])), 'Undid the last save. The abstract as of Nov 13, 2026: overall 10.73% is back.');
  // A save of a new date is taken back by removing it.
  const added = JSON.stringify(L.abstractUndo(fields('2026-11-20'), null)), made = { ...fields('2026-11-20'), uuid: 'made', revision: 1, updatedAt: 9 };
  assert.deepEqual(L.abstractUndoEdit(added, [old, made]), { rec: made, changes: null,
    audit: { actionType: 'Deleted abstract', details: 'Abstract as of Nov 20, 2026: overall 12.50%', oldValue: '12.50%', newValue: '' } });
  assert.equal(L.abstractUndoAsk(L.abstractUndoEdit(added, [made])), 'Undo the last save? The abstract as of Nov 20, 2026 will be removed.');
  assert.equal(L.abstractUndoneNote(L.abstractUndoEdit(added, [made])), 'Undid the last save. The abstract as of Nov 20, 2026 was removed.');
  // Nothing to undo: nothing kept, text that is not one, the abstract deleted, or saved again since (other rows).
  for (const [text, abstracts] of [[null, [made]], ['{', [made]], ['[]', [made]], [added, [old]], [added, [{ ...made, rowsJson: '[]' }]]]) assert.equal(L.abstractUndoEdit(text, abstracts), null);
});

test('an unfinished New abstract is kept as a draft: its date and what was typed, only while something is typed', () => {
  const next = { asOf: '2026-11-20', from: 'a|b', text: { 0: '20', 8: ' ' }, kept: true };
  assert.deepEqual(L.nextAbstractDraft(next), { asOf: '2026-11-20', from: 'a|b', text: { 0: '20', 8: ' ' }, opened: {} });
  assert.equal(L.nextAbstractDraft({ ...next, text: { 8: ' ' } }), null); // nothing typed: nothing to keep
  assert.equal(L.nextAbstractDraft({ ...next, kept: false }), null); // quantities the date already had, not typed here
  // Read back from the browser: the form as it was left; anything else is no draft.
  assert.deepEqual(L.keptNextAbstract(JSON.stringify(L.nextAbstractDraft(next))), { asOf: '2026-11-20', from: 'a|b', text: { 0: '20', 8: ' ' }, opened: {}, kept: true });
  for (const text of [null, '', '{', '[]', '{"asOf":5,"text":{}}', '{"asOf":"2026-11-20","text":[1]}', '{"asOf":"2026-11-20"}']) assert.equal(L.keptNextAbstract(text), null);
});

test('web status: the badge says Behind Schedule only from 10% behind, matching the note and the filter', () => {
  const p = { status: 'Behind Schedule', actualAccomplishment: 40, targetAccomplishment: 41.5 };
  assert.deepEqual(L.badgeStatus(p), { text: 'On-going', level: 'neutral' }, 'a 1.50% slip reads slipping, not behind');
  assert.equal(L.scheduleText(p), '1.50% slipping');
  assert.deepEqual(L.badgeStatus({ ...p, actualAccomplishment: 31.5 }), { text: 'Behind Schedule', level: 'watch' }, '10% behind');
  assert.deepEqual(L.badgeStatus({ ...p, actualAccomplishment: 20 }), { text: 'Behind Schedule', level: 'behind' });
  assert.deepEqual(L.badgeStatus({ ...p, status: 'Suspended' }), { text: 'Suspended', level: 'neutral' });
  assert.deepEqual(L.badgeStatus({ ...p, actualAccomplishment: 100 }), { text: 'Completed', level: 'ok' });
  assert.equal(L.displayStatus(p), 'Behind Schedule', 'the stored word, used on prints, is unchanged');
});

test('web dashboard order: worst first, not by name', () => {
  const today = '2026-11-17';
  const it = (name, actual, target, completion, flags, lastUpdate = null, dateStarted = '2026-01-05') =>
    ({ p: { name, actualAccomplishment: actual, targetAccomplishment: target, completionDateOriginal: completion, dateStarted }, flags, lastUpdate });
  const crit = [{ kind: 'behind', level: 'critical' }];
  const medium = it('Medium-Rise', 50, 66.6, '2027-06-30', crit);
  const resettle = it('Resettlement', 41.25, 77.81, '2027-03-31', crit);
  const site = it('Site Development', 0, 30, '2026-11-12', [{ kind: 'behind', level: 'critical' }, { kind: 'overdue', level: 'critical' }]);
  const drainage = it('Drainage', 40, 41.5, '2027-01-31', [{ kind: 'waiting', level: 'warning' }]);
  const calm = it('A calm one', 60, 50, '2027-12-31', []);
  const order = [calm, drainage, medium, resettle, site].sort((a, b) => L.attentionOrder(a, b, today)).map(x => x.p.name);
  assert.deepEqual(order, ['Site Development', 'Resettlement', 'Medium-Rise', 'Drainage', 'A calm one']);
  assert.equal(L.leadingFlag(site.flags).kind, 'overdue', 'the overdue flag is the one named');
  const stale = it('B', 50, 50, '2027-12-31', [{ kind: 'stale', level: 'stale' }], '2026-10-01');
  const staler = it('C', 50, 50, '2027-12-31', [{ kind: 'stale', level: 'stale' }], '2026-09-01');
  assert.deepEqual([stale, staler].sort((a, b) => L.attentionOrder(a, b, today)).map(x => x.p.name), ['C', 'B'], 'longest without an update first');
});

test('order remarks read with display dates and dots', () => {
  assert.equal(L.remarksText('Duration: 10 Days | Effectivity: 2026-08-01'), 'Duration: 10 Days · Effectivity: Aug 1, 2026 · End: Aug 10, 2026');
  assert.equal(L.remarksText(''), '');
  assert.equal(L.remarksText('Approved by the RD'), 'Approved by the RD');
  // A reference number that looks like a date stays as typed.
  assert.equal(L.remarksText('Effectivity: 2026-08-01 | Remarks: Memo No. 2026-11-001'), 'Effectivity: Aug 1, 2026 · Remarks: Memo No. 2026-11-001');
});

test('web dashboard order: a finished or suspended project is not ranked up for going quiet', () => {
  const today = '2026-11-17';
  const it = (name, p, lastUpdate) => ({ p: { name, actualAccomplishment: 40, targetAccomplishment: 40, completionDateOriginal: '2027-06-01', ...p }, lastUpdate, flags: [] });
  const items = [it('Active', {}, '2026-11-15'), it('Done', { actualAccomplishment: 100, targetAccomplishment: 100, status: 'Completed', completionDateOriginal: '2025-01-01' }, '2025-01-01'), it('Paused', { status: 'Suspended' }, '2026-03-01'), it('Quiet', {}, '2026-10-01')];
  assert.deepEqual(items.sort((a, b) => L.attentionOrder(a, b, today)).map(x => x.p.name), ['Quiet', 'Active', 'Done', 'Paused']);
});

test('web errors: plain sentences for Firebase codes, the app\'s own messages kept, raw text never', () => {
  const fb = (code, message = 'Failed to get document because the client is offline.') => Object.assign(new Error(message), { code });
  const denied = 'This account cannot edit this project.', fallback = 'Could not save. Try again.';
  assert.equal(L.errorText(fb('permission-denied'), denied, fallback), denied);
  assert.equal(L.errorText(fb('storage/unauthorized'), denied, fallback), denied);
  assert.equal(L.errorText(fb('unavailable'), denied, fallback), 'The connection dropped. Check the internet, then try again.');
  assert.equal(L.errorText(fb('unauthenticated'), denied, fallback), 'Your sign-in has run out. Sign out, sign in again, then try again.');
  assert.equal(L.errorText(fb('internal', 'INTERNAL ASSERTION FAILED'), denied, fallback), fallback);
  assert.equal(L.errorText(new Error('The plan must be a PDF file.'), denied, fallback), 'The plan must be a PDF file.');
  assert.equal(L.errorText(new TypeError("Cannot read properties of undefined (reading 'x')"), denied, fallback), fallback);
  assert.equal(L.errorText(undefined, denied, fallback), fallback);
  assert.equal(L.errorText('oops', denied, fallback), fallback);
});

test('web dashboard: a flag built on records that failed to load is left out', () => {
  const flags = [{ kind: 'overdue' }, { kind: 'behind' }, { kind: 'stale' }, { kind: 'billingAhead' }, { kind: 'waiting' }, { kind: 'requests' }, { kind: 'dueSoon' }];
  const kinds = failed => L.flagsWithout(flags, failed).map(f => f.kind);
  assert.deepEqual(kinds([]), ['overdue', 'behind', 'stale', 'billingAhead', 'waiting', 'requests', 'dueSoon']);
  assert.deepEqual(kinds(['weekly_reports']), ['overdue', 'behind', 'billingAhead', 'waiting', 'requests', 'dueSoon']);
  assert.deepEqual(kinds(['project_payments']), ['overdue', 'behind', 'stale', 'waiting', 'requests', 'dueSoon']);
  assert.deepEqual(kinds(['pending_documents']), ['overdue', 'behind', 'requests', 'dueSoon']);
  assert.deepEqual(kinds(['developer_requests']), ['overdue', 'behind', 'stale', 'billingAhead', 'waiting', 'dueSoon']);
});

test('billingText: the billing labels in sentence case, as the lot card and plan say them', () => {
  assert.deepEqual(['M1 BILLED (80%)', 'M2 BILLED (20%)', 'M1 + M2 BILLED (100%)', 'NOT BILLED', '', undefined, 'BILLED'].map(L.billingText),
    ['M1 billed · 80%', 'M2 billed · 20%', 'M1 + M2 billed · 100%', 'Not billed', 'Not billed', 'Not billed', 'M1 billed · 80%']);
});

test('dashboard CSV: BOM, plain numbers, iso dates, quoted commas, quotes and line breaks, formulas kept as text', () => {
  const item = { p: { name: 'Road "A", phase 2', location: 'Bulacan\nMalolos', contractor: '=HYPERLINK("x")', assignedStaff: 'Ana', status: 'On-going',
    actualAccomplishment: '41.255', completionDateOriginal: '2027-03-01' }, billed: 1500000.5, cost: 3000001, lastUpdate: '2026-11-20',
    flags: [{ message: 'Due in 3 days' }, { message: 'No field update in 14 days' }] };
  const csv = L.dashboardCsv([item], '2026-11-21');
  assert.ok(csv.startsWith('﻿Project,Location,Contractor,Assigned staff,Status,Work done (%),Billed (₱),'));
  assert.equal(csv.split('\r\n')[1], '"Road ""A"", phase 2","Bulacan\nMalolos","\'=HYPERLINK(""x"")",Ana,On-going,41.26,1500000.5,50,3000001,2027-03-01,2026-11-20,Due in 3 days; No field update in 14 days');
  assert.equal(L.toCsv([[-5, 'a,b']]), '﻿-5,"a,b"\r\n');
  // Billings not loaded: the money cells are empty, not 0.
  assert.match(L.dashboardCsv([item], '2026-11-21', false), /,41\.26,,,,2027-03-01,/);
});

test('dashboard sort: days left puts overdue first and finished or undated last; an unknown sort is the attention order', () => {
  const it = (name, due, done = 10, cost = 0) => ({ p: { name, completionDateOriginal: due, actualAccomplishment: done, status: done >= 100 ? 'Completed' : 'On-going' }, cost, flags: [] });
  const items = [it('Done', '2026-01-01', 100), it('Late', '2026-11-01'), it('None', ''), it('Soon', '2026-12-01', 50, 9)];
  const names = sort => [...items].sort(L.dashboardOrder(sort, '2026-11-21')).map(x => x.p.name);
  assert.deepEqual(names('due'), ['Late', 'Soon', 'Done', 'None']);
  assert.equal(names('cost')[0], 'Soon');
  assert.deepEqual(names('done'), ['Late', 'None', 'Soon', 'Done']);
  assert.deepEqual(names('nonsense'), [...items].sort((a, b) => L.attentionOrder(a, b, '2026-11-21')).map(x => x.p.name));
});

test('a new weekly report starts with the latest report\'s manpower and equipment', () => {
  const r = (week, manpower, equipment) => ({ reportingWeek: week, manpowerJson: JSON.stringify(manpower), equipmentJson: JSON.stringify(equipment) });
  const weekly = [
    r('Oct 19, 2026 - Oct 25, 2026', [{ designation: 'Foreman', count: 2 }], [{ description: 'Backhoe', count: 1, status: 'Idle' }]),
    r('Oct 5, 2026 - Oct 11, 2026', [{ designation: 'Laborers', count: 9 }], []),
  ];
  assert.deepEqual(L.carriedCrew(weekly), {
    manpower: [{ designation: 'Foreman', count: 2, remarks: '' }],
    equipment: [{ description: 'Backhoe', count: 1, status: 'Idle', remarks: '' }],
  });
  // No report yet, or one without manpower: the usual designations at zero.
  const start = L.MANPOWER_START.map(designation => ({ designation, count: 0 }));
  assert.deepEqual(L.carriedCrew([]), { manpower: start, equipment: [] });
  assert.deepEqual(L.carriedCrew([r('Oct 19, 2026 - Oct 25, 2026', [], [])]).manpower, start);
});
