// Run: node --test web/logic.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';

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
  assert.equal(L.attentionFlags(quiet, [], 0, '2026-11-08')[0].message, 'No field update in 38 days', 'no report yet: from the start date');
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
  assert.equal(L.formatPhp(-1234.5), '-₱1,234.50');
  assert.equal(L.netAmount({ grossAmount: 1000, retentionPct: 10 }), 900);
  assert.equal(L.isOpenIssue({ status: 'Pending' }), true);
  assert.equal(L.isOpenIssue({ status: 'Resolved' }), false);
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
  assert.deepEqual(L.billingErrors(unit), []);
  const d = JSON.parse(L.newPaymentRecord(ctx, unit, [], 0).data);
  assert.deepEqual([d.name, d.units, d.unitPrice, d.percentage, d.balanceAmount, d.balancePercentage], ['M1 - Housing unit', 5, 800000, 0, -4000000, 0]);
  // Checks, as the phone's form gives them.
  assert.deepEqual(L.billingErrors({ ...f, grossAmount: '' }), ['Type the gross amount.']);
  assert.deepEqual(L.billingErrors({ ...f, grossAmount: '12a', date: '' }), ['Pick the date.', 'The gross amount must be a number.']);
  assert.deepEqual(L.billingErrors({ ...unit, units: '2.5', unitPrice: '' }), ['The number must be a whole number.', 'Type the amount per housing unit.']);
  assert.deepEqual(L.billingErrors({ ...unit, billingType: 'Land development', units: '' }), ['Type the number of lots.']);
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
