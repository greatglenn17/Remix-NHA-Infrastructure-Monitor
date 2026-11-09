// Run: node --test "web/*.test.mjs"
// The Progress, Payments, Documents and print rules copied from the app (logic.js, print.js).
import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';
import * as P from './print.js';

test('weekly report lists read like the phone, with its defaults and older keys', () => {
  assert.deepEqual(L.manpowerOf('[{"designation":"Mason","count":"4"},{}]'), [
    { designation: 'Mason', count: 4, remarks: '' }, { designation: 'Personnel', count: 1, remarks: '' }]);
  assert.equal(L.equipmentOf('[{"description":"Mixer","count":1,"remarks":"Idle"}]')[0].status, 'Idle');
  assert.deepEqual(L.activitiesOf('[{"dayOfWeek":"Monday","activity":"Formworks"}]'), [{ day: 'Monday', description: 'Formworks', remarks: '' }]);
  assert.deepEqual(L.issuesOf('not json'), []);
  assert.deepEqual(L.manpowerOf(''), []);
  // A row without a target stops the list; the rows before it stay.
  const acc = L.accomplishmentOf('[{"itemDescription":"A","weightPct":60,"targetPct":40,"actualPct":35.5},{"itemDescription":"B","actualPct":1},{"itemDescription":"C","targetPct":1,"actualPct":1}]');
  assert.equal(acc.length, 1);
  assert.equal(acc[0].variancePct, -4.5);
});

test('photo lists in every shape older versions saved, and their titles', () => {
  const a = 'nhafile:11111111-1111-4111-8111-111111111111/aaaaaaaa-0000-4000-8000-000000000001.jpg';
  assert.deepEqual(L.attachmentList(JSON.stringify([a, 'x.pdf'])), [a, 'x.pdf']);
  assert.deepEqual(L.attachmentList(JSON.stringify([JSON.stringify([a])])), [a]);
  assert.deepEqual(L.attachmentList('content://photo/1'), ['content://photo/1']);
  assert.deepEqual(L.attachmentList('[oops'), []);
  assert.equal(L.photoDetails(JSON.stringify({ [a]: { title: 'Footings', takenAt: 'Oct 24, 2026 9:00 AM' } }))[a].title, 'Footings');
  assert.deepEqual(L.photoDetails('[]'), {});
  assert.equal(L.isPdf('nhafile:x.pdf'), true);
});

test('reporting weeks: both label styles, week order, previous report', () => {
  assert.equal(L.weekStart('Oct 24, 2026 - Oct 30, 2026'), Date.UTC(2026, 9, 24));
  assert.equal(L.weekStart('Week 30: July 20 - July 26, 2026'), Date.UTC(2026, 6, 20));
  assert.equal(L.weekStart('Dec 26 - Jan 1, 2027'), Date.UTC(2027, 11, 26)); // the phone reads the start in the end's year too
  assert.equal(L.weekStart('This week'), null);
  const r = (week, day, at) => ({ reportingWeek: week, daysElapsed: day, createdAtTimestamp: at });
  const list = [r('Oct 31, 2026 - Nov 6, 2026', 14, 3), r('Oct 24, 2026 - Oct 30, 2026', 7, 2), r('odd', 1, 1)];
  assert.deepEqual([...list].sort(L.byWeek).map(x => x.daysElapsed), [1, 7, 14]);
  assert.equal(L.previousReport(list, list[0]).daysElapsed, 7);
  assert.equal(L.previousReport(list, list[2]), null);
});

test('monthly view: months from saved and weekly reports, and the weeks of a month', () => {
  const weekly = [{ reportingWeek: 'Oct 31, 2026 - Nov 6, 2026' }, { reportingWeek: 'Nov 7, 2026 - Nov 13, 2026' }, { reportingWeek: 'Oct 24, 2026 - Oct 30, 2026' }];
  assert.deepEqual(L.reportMonths(weekly, [{ reportingMonth: 'September 2026' }], '2026-11-09'), ['September 2026', 'October 2026', 'November 2026']);
  assert.deepEqual(L.reportMonths([], [], '2026-11-09'), ['November 2026']);
  // A week spanning two months counts in both, as on the phone.
  assert.deepEqual(L.monthReports(weekly, 'November 2026').map(r => r.reportingWeek), ['Oct 31, 2026 - Nov 6, 2026', 'Nov 7, 2026 - Nov 13, 2026']);
});

test('revised completion from approved time extensions, the start date as day 1', () => {
  const p = { dateStarted: '2026-01-01', contractDurationDays: 100, completionDateOriginal: '2026-04-10', completionDateRevised: '-' };
  const te = (days, status = 'Approved') => ({ documentName: `Time Extension No. 1 - ${days} Days`, status });
  assert.equal(L.lastDay('2026-01-01', 100), '2026-04-10');
  assert.equal(L.revisedCompletion(p, [te(30)]), '2026-05-10');
  assert.equal(L.revisedCompletion(p, [te(30), te(20, 'Pending')]), '2026-05-10');
  assert.equal(L.revisedCompletion(p, [te(20, 'Pending')]), null);
  assert.equal(L.revisedCompletion({ ...p, dateStarted: '' }, [te(30)]), null);
  assert.equal(L.revisedDurationDays(p, 30), 130);
  assert.equal(L.revisedDurationDays(p, 0), null);
  assert.equal(L.revisedDurationDays({ ...p, completionDateRevised: '2026-04-20' }, 0), 110);
  assert.equal(L.revisedDurationDays({ ...p, completionDateRevised: '2026-04-10' }, 0), null);
});

test('order names: older forms read like new ones; continuances follow their suspension', () => {
  assert.equal(L.orderLabel('Work Suspension No. 1 (Partial)'), 'Partial Work Suspension Order No. 1');
  assert.equal(L.orderLabel('work resumption order no. 2'), 'Work Resumption Order No. 2');
  assert.equal(L.orderLabel('Continuance No. 2 of Work Suspension No. 1 (Partial)'), '2nd Partial Continuance of Work Suspension Order No. 1');
  assert.equal(L.orderLabel('1st Continuance of Work Suspension Order No. 1'), '1st Continuance of Work Suspension Order No. 1');
  assert.equal(L.orderLabel('Time Extension No. 1 - 30 Days'), 'Time Extension No. 1 - 30 Days');
  assert.equal(L.ordinal(11), '11th');
  assert.equal(L.ordinal(22), '22nd');
  const ws = { documentName: 'Partial Work Suspension Order No. 1' };
  const docs = [ws, { documentName: '1st Partial Continuance of Work Suspension Order No. 1' }, { documentName: 'Work Suspension Order No. 2' }];
  assert.equal(L.isSuspension(ws), true);
  assert.equal(L.isSuspension(docs[1]), false);
  assert.deepEqual(L.continuances(ws, docs), [docs[1]]);
});

test('letters: old document letters merged in, newest first; related files leave out orders and letters', () => {
  const docs = [
    { uuid: 'd1', documentName: 'Letter to Contractor No. 1', remarks: 'Reason: Delay | Date Received: 2026-09-01', fileUrl: 'nhafile:x.jpg' },
    { uuid: 'd2', documentName: 'Building permit', status: 'Approved' },
    { uuid: 'd3', documentName: 'Variation Order No. 1 - PHP 5', status: 'Approved' },
  ];
  const merged = L.mergedLetters([{ uuid: 'l1', dateSent: '2026-10-01', subject: 'Manpower' }], docs);
  assert.deepEqual(merged.map(l => l.uuid), ['l1', 'd1']);
  assert.equal(merged[1].subject, 'Letter to Contractor No. 1: Delay');
  assert.equal(merged[1].receivedFileUrl, 'nhafile:x.jpg');
  assert.deepEqual(L.relatedFiles(docs).map(d => d.uuid), ['d2']);
  assert.deepEqual(L.variationLines({ documentName: 'Variation Order No. 1 (Additive) - PHP 5', status: 'Approved', remarks: 'Extra drainage | Date Approved: 2026-03-01' }),
    ['Type: Additive', 'Status: Approved', 'Date Approved: 2026-03-01', 'Reason: Extra drainage']);
});

test('lots status: six bands, plans in use only, saleable lots only, latest progress wins', () => {
  assert.deepEqual([0, 1, 25, 26, 50, 51, 75, 76, 99, 100].map(L.lotBand), [0, 1, 1, 2, 2, 3, 3, 4, 4, 5]);
  assert.equal(L.LOT_BANDS.length, 6);
  const plan = (uuid, version, isActive, siteName = '') => ({ type: 'sdp_plans', uuid, version, isActive, siteName });
  const lot = (uuid, planUuid, extra = {}) => ({ type: 'sdp_lots', uuid, _ref_sdpPlanId: planUuid, isActive: 1, blockNumber: '1', lotUse: '', ...extra });
  const prog = (lotUuid, physicalProgress, updatedAt) => ({ type: 'sdp_lot_progress', _ref_sdpLotId: lotUuid, physicalProgress, updatedAt });
  const records = [
    plan('old', 1, 0), plan('new', 2, 1), plan('b', 1, 0, 'Site B'),
    lot('l1', 'new'), lot('l2', 'new'), lot('l3', 'new'), lot('os', 'new', { blockNumber: 'OS' }), lot('road', 'new', { lotUse: 'Road Lot' }),
    lot('gone', 'new', { isActive: 0 }), lot('stale', 'old'), lot('b1', 'b'),
    prog('l1', 10, 1), prog('l1', 100, 2), prog('l2', 30, 1), prog('stale', 50, 1), prog('b1', 80, 1),
  ];
  const s = L.lotSummary(records);
  assert.equal(s.saleable, 4); // l1, l2, l3 and Site B's only (inactive) plan's b1
  assert.deepEqual(s.bands, [1, 0, 1, 0, 1, 1]);
  assert.deepEqual(L.lotSummary([]), { bands: [0, 0, 0, 0, 0, 0], saleable: 0 });
  const billing = L.billingStatus([{ billingType: 'M1 - Housing unit', units: 3 }, { billingType: 'M1 - Housing unit', units: 1 }], 8);
  assert.deepEqual(billing.map(b => [b.label, b.units, b.share]), [
    ['Land acquisition', 0, '0.00%'], ['Land development', 0, '0.00%'], ['M1 - Housing unit (80%)', 4, '50.00%'], ['M2 - Housing unit (20%)', 0, '0.00%']]);
  assert.equal(L.billingStatus([], 0)[0].share, '-');
});

test('print: activities in Saturday-to-Friday order with blank weekdays kept', () => {
  const rows = P.activityRows([
    { day: 'Tuesday', description: 'Rebar', remarks: '' }, { day: 'Saturday', description: 'Survey', remarks: 'Ok' },
    { day: 'Monday - Wednesday', description: 'Excavation', remarks: '' }, { day: 'tuesday', description: 'Forms', remarks: '' },
  ]);
  assert.deepEqual(rows.map(r => r.day), ['Saturday', 'Monday - Wednesday', 'Tuesday', 'Thursday', 'Friday']);
  assert.deepEqual(rows[2].descriptions, ['Rebar', 'Forms']);
  assert.deepEqual(rows[3].descriptions, []);
});

test('print: week issues and documents come from the week only', () => {
  const report = { reportingWeek: 'Oct 24, 2026 - Oct 30, 2026', issuesJson: '[{"description":"Rain","actionTaken":"Waited"}]' };
  const issues = P.weekIssues(report, [
    { date: '2026-10-25', description: 'Late delivery', actionTaken: 'Called', remarks: '', targetDate: '2026-11-02' },
    { date: '2026-10-31', description: 'Next week' }, { date: '2026-10-26', description: 'rain' },
  ]);
  assert.deepEqual(issues.map(i => i.description), ['Rain', 'Late delivery']);
  assert.equal(issues[1].remarks, 'Target: November 2, 2026');
  const docs = P.weekDocuments(report, [{ subject: 'Letter No. 3', dateReceived: '2026-10-27' }, { subject: 'Old', dateReceived: '2026-10-01' }],
    [{ requestType: 'Time Extension', details: '30 days', dateReceived: '2026-10-24', dateRequested: '2026-10-20', datePackaged: '' }]);
  assert.deepEqual(docs, [
    ["Developer's request: Time Extension – 30 days", 'October 24, 2026', 'Received; not yet packaged', 'Requested October 20, 2026'],
    ['Letter No. 3', 'October 27, 2026', 'Letter issued', 'Received October 27, 2026'],
  ]);
  assert.deepEqual(P.weekDocuments({ reportingWeek: 'odd' }, [], []), []);
});

test('print: the weekly form has every section, escapes text, and marks orders for approval', () => {
  const project = { name: 'Phase <1>', location: 'Pandi', contractor: 'ABC', implementingOffice: 'NHA Bulacan', scopeOfWork: '[{"name":"Land development","amount":100}]',
    contractCostOriginal: 0, contractDurationDays: 100, dateStarted: '2026-01-01', completionDateOriginal: '2026-04-10', completionDateRevised: '-' };
  const docs = [{ documentName: 'Time Extension No. 1 - 30 Days', status: 'Approved' }, { documentName: 'Time Extension No. 2 - 10 Days', status: 'Pending' }];
  const report = { reportingWeek: 'Week 2: Jan 3 - Jan 9, 2026', daysElapsed: 9, remainingDays: 121, targetAccomplishmentPct: 5, actualAccomplishmentPct: 4.5,
    manpowerJson: '[{"designation":"Mason","count":4}]', equipmentJson: '[{"description":"Mixer","count":1,"status":"Idle"}]',
    activitiesJson: '[]', issuesJson: '[]', accomplishmentItemsJson: '[]', _weather: [{ date: '2026-01-05', dayOfWeek: 'Monday', hourlyConditionsCsv: 'RAINY' }] };
  const html = P.weeklyReportHtml({ project, report, docs, issues: [], letters: [], requests: [], photos: [['https://x/p.jpg', 'Footings']],
    signatories: P.weeklySignatories(project, { name: 'Juan Dela Cruz' }), banner: 'blue-book-header.png' });
  for (const s of ['I. BASIC INFORMATION', 'II. DAILY WEATHER CHART', 'III. MANPOWER', 'V. ACTIVITIES OF THE WEEK', 'VI. ISSUES', 'VII. WEEKLY ACCOMPLISHMENT STATUS',
    'VIII. DOCUMENTS ISSUED', 'IX. ATTACHED PHOTOS', 'For the Week of Jan 3 - Jan 9, 2026', 'Phase &lt;1&gt;', 'No. 1: 30 c.d.<br>No. 2: 10 c.d. (for approval)',
    'May 10, 2026', 'JUAN DELA CRUZ', 'Principal Engineer A', 'Checked and Reviewed by', "<img src='https://x/p.jpg'", 'Php 100.00', 'fill=\'#3B82F6\'']) {
    assert.ok(html.includes(s), s);
  }
  assert.equal((html.match(/&#10003;/g) || []).length, 1); // the Idle mixer
  assert.ok(!html.includes('<1>'));
  assert.equal(P.peso(1234.5), 'Php 1,234.50');
  assert.equal(P.longDate('2026-10-24'), 'October 24, 2026');
  assert.equal(P.longDate('soon'), 'soon');
});
