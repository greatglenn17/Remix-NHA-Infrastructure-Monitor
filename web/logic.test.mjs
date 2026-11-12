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
    data: '{"requestType":"Variation Order","details":"Letter 12","dateRequested":"","dateReceived":"2026-11-12","datePackaged":"","requestFileUrl":""}',
  });
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
  // No dates on the project: day counts are 0, as on the phone.
  assert.deepEqual((({ daysElapsed, remainingDays }) => [daysElapsed, remainingDays])(JSON.parse(L.newWeeklyReport(f, {}, 'x').build({}).data)), [0, 0]);
});
