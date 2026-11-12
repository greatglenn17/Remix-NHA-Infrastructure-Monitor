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
  // A report for a later week is already saved: this late entry leaves the project's figures alone.
  assert.equal(L.newWeeklyReport(f, project, 'x', [{ uuid: 'later', reportingWeek: 'Feb 21, 2026 - Feb 27, 2026' }]).projectFields, null);
  assert.notEqual(L.newWeeklyReport(f, project, 'x', [{ uuid: 'older', reportingWeek: 'Week 6: February 7 - February 13, 2026' }]).projectFields, null);
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
    latestUpdatePhotoUrl: '', barChartJson: '', contractCostOriginal: 4000000.5, contractCostRevised: 4000000.5, contractDurationDays: 365,
    dateStarted: '2026-01-01', completionDateOriginal: '2026-12-31', completionDateRevised: '2026-12-31', status: 'On-going', targetAccomplishment: 0, actualAccomplishment: 0,
    assignedStaff: 'Engr. Cruz', revision: 1, createdAt: 1700000000000, updatedAt: 1700000000000, createdByUid: 'u1', updatedByUid: 'u1', isDeleted: false, assignedUids: ['u1'],
  });
  assert.deepEqual(audit, { actionType: 'Project Creation', details: 'Created new project: Pandi Heights',
    newValue: 'Title: Pandi Heights, Location: Pandi, Bulacan, Cost: ₱4000000.5, Duration: 365 days' });
  assert.equal(L.isCompleted(project), false);

  // The same fields a phone uploads for a project (FirestoreSyncManager), plus the two it adds for a new one.
  const kt = (await import('node:fs')).readFileSync(new URL('../app/src/main/java/com/example/data/remote/FirestoreSyncManager.kt', import.meta.url), 'utf8');
  const upload = kt.slice(kt.indexOf('"projectUuid" to project.projectUuid'), kt.indexOf('"isDeleted" to (event.operation'));
  assert.deepEqual(Object.keys(project).sort(), [...[...upload.matchAll(/"(\w+)" to/g)].map(m => m[1]), 'isDeleted', 'assignedUids', 'barChartJson'].sort());

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

test('signatories: the project fields a weekly or a monthly report saves, only where they differ, and the audit entry', () => {
  const project = { name: 'Pandi Heights', status: 'On-going', actualAccomplishment: 35, contractCostRevised: 4500000,
    preparedByName: 'Engr. Cruz', preparedByPosition: 'Senior Engineer A', preparedByOffice: 'Bulacan District Office', weeklyCheckedByName: 'Engr. Reyes' };
  const form = { preparedName: ' Engr. Cruz ', preparedPosition: 'Senior Engineer A', preparedOffice: 'Bulacan District Office',
    checkedName: 'Engr. Reyes', checkedPosition: 'Principal Engineer A', checkedOffice: 'Bulacan District Office',
    notedName: 'Engr. Santos', notedPosition: 'District Manager', notedOffice: ' Bulacan District Office ' };
  // Weekly: its own checker and noter; what is already saved (after trimming) is not written again.
  assert.deepEqual(L.signatoryFields(project, form, true), {
    weeklyCheckedByPosition: 'Principal Engineer A', weeklyCheckedByOffice: 'Bulacan District Office',
    weeklyNotedByName: 'Engr. Santos', weeklyNotedByPosition: 'District Manager', weeklyNotedByOffice: 'Bulacan District Office',
  });
  // Monthly: the same form goes to the monthly checker and noter; Prepared by is shared.
  assert.deepEqual(L.signatoryFields(project, { ...form, preparedPosition: 'Principal Engineer C' }, false), {
    preparedByPosition: 'Principal Engineer C', checkedByName: 'Engr. Reyes', checkedByPosition: 'Principal Engineer A', checkedByOffice: 'Bulacan District Office',
    notedByName: 'Engr. Santos', notedByPosition: 'District Manager', notedByOffice: 'Bulacan District Office',
  });
  // A name cleared is saved as blank; nothing different is nothing to save.
  assert.deepEqual(L.signatoryFields({ ...project, ...L.signatoryFields(project, form, true) }, { ...form, checkedName: '' }, true), { weeklyCheckedByName: '' });
  assert.deepEqual(L.signatoryFields({ ...project, ...L.signatoryFields(project, form, true) }, form, true), {});
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

test('bar chart editor: drafts from the scope or the saved chart, bars as month and cell, the errors and what is saved', () => {
  const scope = JSON.stringify([{ name: 'Land development', amount: 3000000, weightPct: 60 }, { name: 'Housing units', amount: 2000000, weightPct: 40 }]);
  const project = { projectUuid: 'p1', assignedUids: ['u1'], dateStarted: '2026-01-05', scopeOfWork: scope, contractCostOriginal: 5000000, barChartJson: '' };
  // No chart yet: one "Original" chart of the scope items from the start date, each with an empty bar; nothing to save until the bars read.
  const fresh = L.chartDrafts(project);
  assert.deepEqual(fresh, [{ label: 'Original', effective: '2026-01-05', items: [
    { name: 'Land development', amount: 3000000, bars: [{ sm: '', sc: '', em: '', ec: '' }] }, { name: 'Housing units', amount: 2000000, bars: [{ sm: '', sc: '', em: '', ec: '' }] }] }]);
  assert.deepEqual(L.chartErrors(fresh, project, []), ['Original: check the bars of Land development, Housing units (month 1+, cell 5, 10 … 30)']);
  assert.equal(L.chartDrafts({ ...project, dateStarted: '' }), null);
  // A bar: From month 1 cell 5 is day 1; To month 8 cell 30 is day 240.
  assert.deepEqual(L.draftBar({ sm: '1', sc: '5', em: '8', ec: '30' }), [1, 240]);
  assert.deepEqual(L.draftBar({ sm: ' 5 ', sc: '5', em: '18', ec: '30' }), [121, 540]);
  assert.deepEqual([{ sm: '0', sc: '5', em: '2', ec: '30' }, { sm: '1', sc: '7', em: '2', ec: '30' }, { sm: '3', sc: '5', em: '2', ec: '30' }, { sm: '1', sc: '5', em: 'x', ec: '30' },
    { sm: '1', sc: '35', em: '2', ec: '30' }, { sm: '1.5', sc: '5', em: '2', ec: '30' }].map(L.draftBar), [null, null, null, null, null, null]);
  fresh[0].items[0].bars[0] = { sm: '1', sc: '5', em: '8', ec: '30' };
  fresh[0].items[1].bars = [{ sm: '5', sc: '5', em: '18', ec: '30' }];
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
  assert.deepEqual(L.chartErrors([{ label: 'Original', effective: '2026-01-05', items: [] }], { ...project, scopeOfWork: '' }, []), ['Original: import the Excel file, or add the Scope of Works items to the project first']);
  // An imported sheet (chart-excel.js) replaces a chart's items; its notes say what was read and where a bar is not its Duration.
  const sheet = { name: 'Revised', items: [{ name: 'Mobilization', amount: 1000, bars: [[1, 10], [41, 45]] }, { name: 'Earthworks', amount: 3000.5, bars: [[11, 40]] }, { name: 'Turnover', amount: 999.5, bars: [] }],
    durations: { Mobilization: 10, Earthworks: 30 } };
  assert.deepEqual(L.draftItems(sheet.items), [
    { name: 'Mobilization', amount: 1000, bars: [{ sm: '1', sc: '5', em: '1', ec: '10' }, { sm: '2', sc: '15', em: '2', ec: '15' }] },
    { name: 'Earthworks', amount: 3000.5, bars: [{ sm: '1', sc: '15', em: '2', ec: '10' }] },
    { name: 'Turnover', amount: 999.5, bars: [{ sm: '', sc: '', em: '', ec: '' }] }]);
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

test('files: a delete or an edit names the files nothing holds any more, and no others', () => {
  const P = '11111111-1111-4111-8111-111111111111', id = n => `${String(n).repeat(8)}-0000-4000-8000-000000000000`;
  const jpg = n => `nhafile:${P}/${id(n)}.jpg`, pdf = n => `nhafile:${P}/${id(n)}.pdf`;
  // Every reference, wherever it sits: a column, a JSON list inside a column, the old form without a project.
  assert.deepEqual(L.fileRefsIn({ fileUrl: pdf(1), photosJson: JSON.stringify([jpg(2), { url: jpg(3) }]), old: `nhafile:${id(4)}.jpg`, web: 'https://example.com/a.jpg', bad: 'nhafile:x.jpg' }),
    [pdf(1), jpg(2), jpg(3), `nhafile:${id(4)}.jpg`]);
  assert.deepEqual([L.fileRefsIn(null), L.fileRefsIn([])], [[], []]);

  const report = { uuid: 'w1', type: 'weekly_reports', attachedPhotoUrlsJson: JSON.stringify([jpg(1), jpg(2), jpg(3)]) };
  const letter = { uuid: 'c1', type: 'contractor_letters', replyFileUrl: pdf(4), receivedFileUrl: pdf(5) };
  const audit = { uuid: 'a1', type: 'audit_logs', newValue: `File: ${pdf(4)}` };
  const records = [report, letter, audit, { uuid: 'd1', type: 'pending_documents', fileUrl: pdf(5) }];
  const project = { projectUuid: P, latestUpdatePhotoUrl: jpg(1) };
  // A deleted report: its photos, but not the one the project still shows.
  assert.deepEqual(L.unusedFiles([report], [], records, project), [jpg(2), jpg(3)]);
  // A deleted letter: the file only the audit log mentions goes; the one another record holds stays.
  assert.deepEqual(L.unusedFiles([letter], [], records, project), [pdf(4)]);
  // An edit: a photo taken off the list and a scan replaced; what the record keeps stays.
  assert.deepEqual(L.unusedFiles([report], [{ ...report, attachedPhotoUrlsJson: JSON.stringify([jpg(1), jpg(3)]) }], records, project), [jpg(2)]);
  assert.deepEqual(L.unusedFiles([letter], [{ ...letter, replyFileUrl: pdf(6) }], records, project), [pdf(4)]);
  assert.deepEqual(L.unusedFiles([letter], [{ ...letter, replyFileUrl: '' }], records, project), [pdf(4)]);
  // An edit that touches no file, and a record without files.
  assert.deepEqual([L.unusedFiles([letter], [{ ...letter, subject: 'x' }], records, project), L.unusedFiles([audit], [], records, project), L.unusedFiles([{ uuid: 'n' }], [], records, null)], [[], [], []]);
  // A deleted project: every file of it, once each.
  assert.deepEqual(L.unusedFiles([...records, project], [], [], null), [jpg(1), jpg(2), jpg(3), pdf(4), pdf(5)]);
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
