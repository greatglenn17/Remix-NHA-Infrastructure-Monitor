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
