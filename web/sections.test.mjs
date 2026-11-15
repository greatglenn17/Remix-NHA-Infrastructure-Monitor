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
  assert.deepEqual(acc.map(a => a.itemDescription), L.ACCOMPLISHMENT_LINES);
  assert.equal(acc[1].variancePct, -4.5);
  assert.equal(acc[1].weightPct, 60);
  // Items saved one by one fold into the three lines: weighted, as is when one, plain when no weights.
  const lines = L.accomplishmentOf(JSON.stringify([
    { itemDescription: 'Land Acquisition', weightPct: 0, targetPct: 100, actualPct: 90 },
    { itemDescription: 'Roads', weightPct: 30, targetPct: 40, actualPct: 20 }, { itemDescription: 'Drainage', weightPct: 10, targetPct: 80, actualPct: 60 },
    { itemDescription: 'Housing units', weightPct: 0, targetPct: 10, actualPct: 4 }, { itemDescription: 'Row house', weightPct: 0, targetPct: 20, actualPct: 6 },
  ]));
  assert.deepEqual(lines.map(a => [a.itemDescription, a.weightPct, a.targetPct, a.actualPct]), [
    ['Land Acquisition', 0, 100, 90], ['Land Development', 40, 50, 30], ['Housing Construction', 0, 15, 5]]);
  assert.deepEqual(L.issuesOf('[{"description":"Rain","actionTaken":"Pumped","actionBy":"Contractor/Developer"}]')[0].actionBy, 'Contractor/Developer');
});

test('billings on the net: the total, and each share and balance after retention', () => {
  // Contract 1,000; two billings of 100 gross, 10% retention: saved 10%/900 then 10%/800 on the gross.
  const pays = [{ grossAmount: 100, retentionPct: 10, percentage: 10, balanceAmount: 900, balancePercentage: 90 },
    { grossAmount: 100, retentionPct: 10, percentage: 10, balanceAmount: 800, balancePercentage: 80 }];
  assert.equal(L.billedTotal(pays), 180);
  const net = L.netBillings(pays);
  assert.deepEqual(net.map(p => [p.percentage, p.balanceAmount, Math.round(p.balancePercentage * 100) / 100]), [[9, 910, 91], [9, 820, 82]]);
  assert.equal(pays[0].balanceAmount, 900); // the saved ones are left as they are
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
  // Key dates: a suspension, its continuances, then its resumption; the rest keep their place.
  const te = { documentName: 'Time Extension No. 1 - 30 Days' }, wr = { documentName: 'Work Resumption Order No. 1' };
  assert.deepEqual(L.chained([te, ws, wr, docs[1], docs[2]]), [te, ws, docs[1], wr, docs[2]]);
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
  // Land development billed by amount counts as its gross over the amount per lot.
  assert.deepEqual(L.billingStatus([{ billingType: 'Land development', units: 0, unitPrice: 20000, grossAmount: 310000 }, { billingType: 'Land development', units: 2 }], 100)[1],
    { label: 'Land development', units: 17.5, share: '17.50%' });
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

test('print: week documents come from the week only', () => {
  const report = { reportingWeek: 'Oct 24, 2026 - Oct 30, 2026' };
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
    activitiesJson: '[]', issuesJson: '[{"description":"Rain","actionTaken":"Pumped","actionBy":"Contractor/Developer"},{"description":"ROW","actionTaken":"Met owners"}]', accomplishmentItemsJson: '[]', _weather: [{ date: '2026-01-05', dayOfWeek: 'Monday', hourlyConditionsCsv: 'RAINY' }] };
  const html = P.weeklyReportHtml({ project, report, docs, letters: [], requests: [], photos: [['https://x/p.jpg', 'Footings']],
    signatories: P.weeklySignatories(project, { name: 'Juan Dela Cruz' }), banner: 'blue-book-header.png' });
  for (const s of ['I. BASIC INFORMATION', 'II. DAILY WEATHER CHART', 'III. MANPOWER', 'V. ACTIVITIES OF THE WEEK', 'VI. ISSUES', 'VII. WEEKLY ACCOMPLISHMENT STATUS',
    'VIII. DOCUMENTS ISSUED', 'IX. ATTACHED PHOTOS', 'For the Week of Jan 3 - Jan 9, 2026', 'Phase &lt;1&gt;', 'No. 1: 30 c.d.<br>No. 2: 10 c.d. (for approval)',
    'May 10, 2026', 'JUAN DELA CRUZ', 'Principal Engineer A', 'Checked and Reviewed by', "<img src='https://x/p.jpg'", 'Php 100.00', 'fill=\'#3B82F6\'']) {
    assert.ok(html.includes(s), s);
  }
  assert.equal((html.match(/&#10003;/g) || []).length, 1); // the Idle mixer
  // Each issue's action under who took it; a blank "action by" is the Implementing Office's.
  assert.ok(html.includes('<th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/DEVELOPER</th>'));
  assert.ok(html.includes('<td>1. Rain</td><td></td><td>Pumped</td>'));
  assert.ok(html.includes('<td>2. ROW</td><td>Met owners</td><td></td>'));
  assert.ok(!html.includes('<1>'));
  assert.equal(P.peso(1234.5), 'Php 1,234.50');
  assert.equal(P.longDate('2026-10-24'), 'October 24, 2026');
  assert.equal(P.longDate('soon'), 'soon');
});

test('print: monthly form pieces read months, orders, weather and signatories like the phone', () => {
  assert.equal(P.monthKey('September 2026'), '2026-09');
  assert.equal(P.monthKey(' july 2026 '), '2026-07');
  assert.equal(P.monthKey('Sept 2026'), null);
  assert.equal(P.monthName('2026-09'), 'September 2026');

  const hours = bad => Array.from({ length: 24 }, (_, h) => (h >= 8 && h < 8 + bad ? 'RAINY' : 'FAIR')).join(',');
  assert.deepEqual(P.workableDays([
    { date: '2026-09-02', hourlyConditionsCsv: hours(5) }, { date: '2026-09-02', hourlyConditionsCsv: hours(0) }, // the first log of a day counts
    { date: '2026-09-03', hourlyConditionsCsv: hours(4) }, { date: '2026-08-31', hourlyConditionsCsv: 'STORMY' }, { date: 'x', hourlyConditionsCsv: '' },
  ]), [['2026-08', 1, 0], ['2026-09', 1, 1]]);

  const { suspensions, variations } = P.ordersOf([
    { documentName: 'Work Suspension Order No. 1', status: 'Approved', remarks: 'Duration: 90 Days | Effectivity: 2026-08-24 | Reason: Rain' },
    { documentName: 'Variation Order No. 1 - PHP 1,500,000.00', status: 'Pending', remarks: 'Reason: Added road' },
    { documentName: 'Variation Order No. 2 (Deductive)', status: 'Approved' },
  ]);
  assert.deepEqual(suspensions, [{ label: 'Work Suspension Order No. 1', value: '90 calendar days – August 24, 2026 to November 21, 2026', reason: 'Rain', status: '' }]);
  assert.deepEqual(variations, [
    { label: 'Variation Order No. 1', value: 'Php 1,500,000.00', reason: 'Added road', status: '(for approval)' },
    { label: 'Variation Order No. 2', value: '', reason: '', status: '' }]);

  const s = P.monthlySignatories({ preparedByName: 'Ana', implementingOffice: 'NHA Bulacan' }, {});
  assert.equal(s.preparedName, 'Ana');
  assert.equal(s.checkedPosition, 'District Manager');
  assert.equal(s.checkedOffice, 'NHA Bulacan');
  assert.equal(s.notedPosition, 'Regional Manager');
  assert.equal(s.notedOffice, 'Region 3 Office');
});

test('print: the monthly form has every section and the month\'s figures', () => {
  const project = { name: 'Phase 1', contractDurationDays: 100, dateStarted: '2026-07-15', completionDateOriginal: '2026-10-22', scopeOfWork: 'Roads' };
  const docs = [{ documentName: 'Time Extension No. 1 - 30 Days', status: 'Approved', remarks: 'Reason: Rain' }];
  const weeks = [
    { reportingWeek: 'Sep 5, 2026 - Sep 11, 2026', targetAccomplishmentPct: 10, actualAccomplishmentPct: 8, accomplishmentItemsJson: '[]' },
    { reportingWeek: 'Sep 26, 2026 - Oct 2, 2026', targetAccomplishmentPct: 20, actualAccomplishmentPct: 18,
      accomplishmentItemsJson: '[{"itemDescription":"Roads","weightPct":100,"targetPct":20,"actualPct":18}]' },
  ];
  const html = P.monthlyReportHtml({
    project, month: '2026-09', weeks, docs, banner: '',
    payments: [{ name: '1st billing', date: '2026-09-30', grossAmount: 1000, balanceAmount: 9000, percentage: 10, balancePercentage: 90 },
      { name: 'Next', date: '2026-10-01', grossAmount: 1 }],
    issues: [{ description: 'Flooding', date: '2026-09-04', actionTaken: 'Pumped', actionBy: 'Contractor/Developer' }, { description: 'Old', date: '2026-08-01' }],
    weather: [{ date: '2026-09-02', hourlyConditionsCsv: 'FAIR' }], saved: { recommendations: 'Add crews\nWork Sundays' },
    signatories: P.monthlySignatories(project, { name: 'Juan' }),
  });
  for (const x of ['For the Month of September 2026', 'II. PROJECT STATUS', 'III. STATUS OF PAYMENT', 'IV. CONTRACT SCHEDULE STATUS',
    'V. SUMMARY OF UNWORKABLE DAYS', 'VI. STATUS OF SUBMISSION', 'VII. ISSUES / CONCERNS', 'VIII. RECOMMENDATIONS',
    '<td>Land Development</td><td class=\'n\'>100.00%</td><td class=\'n\'>20.00%</td><td class=\'n\'>18.00%</td><td class=\'n\'>18.00%</td>',
    '1. 1st billing', 'Php 1,000.00', 'Php 9,000.00 (90.00%)', '130 c.d.', 'November 21, 2026',
    "<td class='c'>3</td><td>September 2026</td>", '1. Flooding', 'September 4, 2026</td><td></td><td>Pumped</td>',
    'Add crews<br>Work Sundays', 'Checked by:', 'JUAN', 'on or before end of each month']) {
    assert.ok(html.includes(x), x);
  }
  assert.ok(!html.includes('Next') && !html.includes('Old'), 'October billing and August issue are left out');
  assert.ok(html.includes('<td>None</td>'), 'no variation or suspension orders');
  assert.ok(html.includes("<tr class='tot'><td colspan='3'>GRAND TOTAL</td><td class='n'>Php 1,000.00</td><td class='n'>10.00%</td><td class='n'>Php 9,000.00 (90.00%)</td></tr>"), 'payment grand total');
});

test('print: extensions read either name style; one for approval does not move the schedule', () => {
  const docs = [{ documentName: 'Time Extension No. 1 (30 days)', status: 'Pending' }, { documentName: 'Time Extension No. 2 - 30 Days', status: 'Approved' }];
  assert.deepEqual(P.extensionsOf(docs).map(e => [e.no, e.days, e.status]), [['1', 30, '(for approval)'], ['2', 30, '']]);
  const html = P.monthlyReportHtml({ project: { contractDurationDays: 540, dateStarted: '2026-01-05' }, month: '2026-09', weeks: [], payments: [], issues: [],
    docs, weather: [], signatories: P.monthlySignatories({}, {}), banner: '' });
  assert.ok(html.includes("<td class='c'>1</td><td class='n'>30</td><td class='n'></td><td></td><td></td><td></td><td>(for approval)</td>"));
  assert.ok(html.includes("<td class='c'>2</td><td class='n'>30</td><td class='n'>570 c.d.</td>") && html.includes('July 28, 2027'));
  assert.ok(!html.includes('No weather logged'));
  assert.ok(html.includes("<td class='nw'>Php 0.00</td>"), 'contract costs stay on one line');
  assert.ok(html.includes("<td colspan='3'>GRAND TOTAL</td><td class='n'>Php 0.00</td><td class='n'>0.00%</td><td class='n'></td>"), 'no billings: zero totals');
});

test('lots: sites, labels, KPIs, filters, colours and blocks read like the phone', () => {
  const rec = (type, o) => ({ type, uuid: o.uuid || `${type}-${Math.random()}`, ...o });
  const records = [
    rec('sdp_plans', { uuid: 'old', version: 1, isActive: 0, uploadedDate: '2026-01-01', siteName: '' }),
    rec('sdp_plans', { uuid: 'p1', version: 2, isActive: 1, uploadedDate: '2026-02-01', siteName: '' }),
    rec('sdp_plans', { uuid: 'p2', version: 1, isActive: 0, uploadedDate: '2026-03-01', siteName: 'Phase 2' }),
    rec('sdp_lots', { uuid: 'a', _ref_sdpPlanId: 'p1', blockNumber: '10', lotNumber: '2', housingUnitNumber: 'U-7' }),
    rec('sdp_lots', { uuid: 'b', _ref_sdpPlanId: 'p1', blockNumber: '2', lotNumber: '10' }),
    rec('sdp_lots', { uuid: 'c', _ref_sdpPlanId: 'p1', blockNumber: '2', lotNumber: '9' }),
    rec('sdp_lots', { uuid: 'r', _ref_sdpPlanId: 'p1', blockNumber: 'RL', lotNumber: '1', lotUse: 'Road Lot' }),
    rec('sdp_lots', { uuid: 'o', _ref_sdpPlanId: 'p1', blockNumber: 'OS', lotNumber: '1' }),
    rec('sdp_lots', { uuid: 'u', _ref_sdpPlanId: 'p1', blockNumber: '', lotNumber: '' }),
    rec('sdp_lots', { uuid: 'x', _ref_sdpPlanId: 'p1', blockNumber: '2', lotNumber: '1', isActive: 0 }),
    rec('sdp_lots', { uuid: 'q', _ref_sdpPlanId: 'old', blockNumber: '1', lotNumber: '1' }),
    rec('sdp_lots', { uuid: 'z', _ref_sdpPlanId: 'p2', blockNumber: '1', lotNumber: '1' }),
    rec('sdp_lot_progress', { _ref_sdpLotId: 'a', physicalProgress: 10, updatedAt: 1 }),
    rec('sdp_lot_progress', { _ref_sdpLotId: 'a', physicalProgress: 100, billingStatus: 'BILLED', m2BillingStatus: 'BILLED', updatedAt: 2 }),
    rec('sdp_lot_progress', { _ref_sdpLotId: 'b', physicalProgress: 30, billingStatus: 'BILLED' }),
    rec('sdp_lot_progress', { _ref_sdpLotId: 'r', physicalProgress: 50 }),
  ];
  const sites = L.sdpSites(records);
  assert.deepEqual(sites.map(s => [s.site, s.plan.uuid, s.lots.length]), [['', 'p1', 6], ['Phase 2', 'p2', 1]]);
  const lots = sites[0].lots, by = id => lots.find(l => l.uuid === id);
  assert.deepEqual(['a', 'r', 'o', 'u'].map(id => L.lotLabel(by(id))), ['Block 10 Lot 2', 'Road Lot 1', 'Block OS Lot 1', 'Unassigned lot']);
  assert.equal(L.lotProgress(by('a')), 100); // the latest progress row
  assert.equal(L.lotBillingLabel(by('a').progress), 'M1 + M2 BILLED (100%)');
  assert.equal(L.lotBillingLabel(null), 'NOT BILLED');
  assert.deepEqual(L.lotStats(lots), { total: 4, m1: 2, m2: 1, notBilled: 2, completed: 1, avg: 32.5 });
  const all = { query: '', band: 'ALL', billing: 'ALL', completion: 'ALL' };
  const ids = f => lots.filter(l => L.lotMatches(l, { ...all, ...f })).map(l => l.uuid);
  assert.deepEqual(ids({ query: 'b2-l1' }), ['b']);
  assert.deepEqual(ids({ query: 'u-7' }), ['a']);
  assert.deepEqual(ids({ query: 'unassigned' }), ['u']);
  assert.deepEqual(ids({ billing: 'NOT BILLED', band: 'Not yet started' }), ['c', 'o', 'u']);
  assert.deepEqual(ids({ completion: 'COMPLETED' }), ['a']);
  assert.deepEqual(['a', 'b', 'c', 'r', 'o'].map(id => L.lotColour(by(id))), ['lot-5', 'lot-2', 'lot-0', 'lot-3', 'lot-n']);
  assert.deepEqual([0, 30, 100].map(L.roadState), [0, 1, 2]);
});

test('issues: overdue and the search and filter chips read like the phone', () => {
  const today = '2026-11-09';
  const a = { description: 'Right-of-way at Block 3', status: 'Pending', isCritical: 1, targetDate: '2026-11-01', loggedBy: 'Engr. Cruz' };
  const b = { description: 'Late delivery', actionTaken: 'Wrote the supplier', status: 'Resolved', isCritical: 0, targetDate: '2026-10-01' };
  const c = { description: 'Drainage', remarks: 'Await rain', status: 'Open', isCritical: 0, targetDate: '2026-12-01' };
  assert.deepEqual([a, b, c].map(i => L.isOverdueIssue(i, today)), [true, false, false]);
  assert.equal(L.isOverdueIssue({ ...a, targetDate: 'Nov 1' }, today), false); // not a date
  const ids = (q, f) => [a, b, c].filter(i => L.issueMatches(i, q, f, today)).map(i => i.description[0]);
  assert.deepEqual(ids('', 'All'), ['R', 'L', 'D']);
  assert.deepEqual(ids('', 'Critical'), ['R']);
  assert.deepEqual(ids('', 'Pending'), ['R', 'D']);
  assert.deepEqual(ids('', 'Overdue'), ['R']);
  assert.deepEqual(ids('', 'Resolved'), ['L']);
  assert.deepEqual(ids('SUPPLIER', 'All'), ['L']);
  assert.deepEqual(ids('cruz', 'All'), ['R']);
  assert.deepEqual(ids('rain', 'Pending'), ['D']);
});

test('weather: the 7 days of a report, the logged months and month lengths read like the phone', () => {
  assert.deepEqual(L.weekDays('Oct 31, 2026 - Nov 6, 2026'), ['2026-10-31', '2026-11-01', '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06']);
  assert.equal(L.weekDays('Week 30: July 20 - July 26, 2026')[0], '2026-07-20');
  assert.deepEqual(L.weekDays('sometime'), []);
  assert.deepEqual(L.weatherMonths([{ date: '2026-10-31' }, { date: '2026-11-02' }, { date: '2026-10-01' }, { date: '' }]), ['2026-11', '2026-10']);
  assert.deepEqual(['2026-02', '2028-02', '2026-11', '2026-12'].map(L.daysInMonth), [28, 29, 30, 31]);
  assert.match(P.weatherClock(null, 7), />7<\/text><\/svg>/); // a day with no log still shows its number
  assert.doesNotMatch(P.weatherClock(null), /font-weight='bold'/);
  assert.match(P.weatherClock({ date: '2026-11-05', hourlyConditionsCsv: '' }), />5<\/text><\/svg>/);
});

test('audit log: who sees it, the search and filter chips, and the badges read like the phone', () => {
  assert.deepEqual(['SUPER_ADMIN', 'ENGINEER_ADMIN', 'FIELD_ENGINEER', 'VIEWER', 'SUPER_VIEWER', undefined].map(role => L.canViewAuditLogs({ role })),
    [true, true, true, false, false, false]);
  const logs = [
    { actionType: 'Weekly Report Creation', user: 'Engr. Cruz', device: 'samsung SM-A155F', details: 'Week 3' },
    { actionType: 'Project Edit', user: 'Admin', oldValue: '41.25%', newValue: '45%' },
    { actionType: 'Database Backup', user: 'Admin' },
    { actionType: 'User Login', user: 'Engr. Cruz' },
  ];
  const ids = (q, f) => logs.filter(l => L.auditMatches(l, q, f)).map(l => logs.indexOf(l));
  assert.deepEqual(ids('', 'All'), [0, 1, 2, 3]);
  assert.deepEqual(ids('', 'Report'), [0]);
  assert.deepEqual(ids('', 'Project'), [1]);
  assert.deepEqual(ids('', 'User'), [3]);
  assert.deepEqual(ids('', 'Backup/Restore/Migration'), [2]);
  assert.deepEqual(ids('SM-A155', 'All'), [0]);
  assert.deepEqual(ids('45%', 'All'), [1]);
  assert.deepEqual(ids('cruz', 'Report'), [0]);
  assert.deepEqual(logs.map(l => L.auditBadge(l.actionType)), [['ok', 'add_circle'], ['link', 'edit'], ['watch', 'cloud_upload'], ['neutral', 'login']]);
  assert.deepEqual(L.auditBadge('Something else'), ['link', 'info']);
});

test('status of construction: frames, polygons, colours, summary and the form read like the phone', () => {
  assert.deepEqual(L.parseFrame('0.1, 0.2,0.9,0.8'), [0.1, 0.2, 0.9, 0.8]);
  assert.equal(L.parseFrame(''), null);
  assert.equal(L.parseFrame('0.5,0.2,0.4,0.8'), null); // not a box
  assert.equal(L.parseFrame('0,0,1.2,1'), null); // off the page
  assert.deepEqual(L.frameAround([[[0.5, 0.5], [0.6, 0.7]], [[0.01, 0.2]]]).map(x => +x.toFixed(2)), [0, 0.17, 0.63, 0.73]);
  assert.equal(L.frameAround([]), null);
  assert.deepEqual(L.lotPolygon({ polygonNormalizedJson: '[[0.1,0.2],[0.3,0.4]]' }), [[0.1, 0.2], [0.3, 0.4]]);
  assert.deepEqual(L.lotPolygon({ polygonNormalizedJson: 'oops' }), []);
  const lot = (use, p) => ({ lotUse: use, blockNumber: '1', lotNumber: '1', progress: { physicalProgress: p } });
  const lots = [lot('Saleable', 0), lot('Saleable', 30), lot('Saleable', 100), lot('Road Lot', 50), lot('Road Lot', 100), lot('Open Space', 0)];
  assert.deepEqual(lots.map(L.lotPrintHex), ['#B91C1C', '#EA580C', '#15803D', '#CA8A04', '#15803D', '#6B6B6B']);
  const one = L.sdpSummary(lots);
  assert.deepEqual(one, { bands: [1, 0, 1, 0, 0, 1], saleable: 3, roads: [0, 1, 1] });
  const project = { name: 'Sample <Homes>', location: 'Malolos' };
  const single = P.sdpPlanHtml({ project, sheets: [{ site: '', image: 'data:image/png;base64,AA', summary: one }], asOf: '2026-11-09', banner: 'b.png', landscape: false });
  assert.match(single, /STATUS OF CONSTRUCTION/);
  assert.match(single, /Sample &lt;Homes&gt;, Malolos &#183; as of November 9, 2026/);
  assert.match(single, /<th class='sub'>Units<\/th>/);
  assert.match(single, /Total units<\/td><td class='n'>3<\/td>/);
  assert.doesNotMatch(single, /max-height/); // one portrait plan fills the page
  const two = P.sdpPlanHtml({ project, sheets: [{ site: '', image: 'x', summary: one }, { site: 'Phase 2', image: 'y', summary: one }], asOf: '2026-11-09', landscape: true });
  assert.match(two, /class='plan multi row'/);
  assert.match(two, /<th class='sub'>Main site<\/th><th class='sub'>Phase 2<\/th><th class='sub'>Total<\/th>/);
  assert.match(two, /Total units<\/td><td class='n'>3<\/td><td class='n'>3<\/td><td class='n'>6<\/td>/);
  assert.match(two, /max-height:151mm/);
});

test('project profile: orders listed under their headings, revised cost only after an approved variation order', () => {
  const project = { name: 'P', contractCostOriginal: 1000, contractDurationDays: 100, actualAccomplishment: 40, targetAccomplishment: 45, status: 'Behind Schedule' };
  const preparer = { name: 'Juan', title: 'Engineer', office: 'Bulacan District Office' };
  const html = docs => P.projectProfileHtml({ project, docs, preparer, asOf: '2026-11-10' });
  const plain = html([]);
  assert.match(plain, /Contract Suspension\/s<\/td><td class='pc'>:<\/td><td>None/);
  assert.match(plain, /Revised Contract Cost<\/td><td class='pc'>:<\/td><td><\/td>/);
  assert.match(plain, /% Delay\/Advance<\/td><td class='pc'>:<\/td><td>-5.00%/);
  assert.match(plain, /<b>JUAN<\/b>/);
  const vo = html([{ documentName: 'Variation Order No. 1 - PHP 500', status: 'Approved', remarks: 'Reason: More units' }]);
  assert.match(vo, /Variation Order No. 1<\/td><td class='pc'>:<\/td><td>Php 500.00<br>\(More units\)/);
  assert.match(vo, /Revised Contract Cost<\/td><td class='pc'>:<\/td><td>Php 1,500.00/);
  assert.match(html([{ documentName: 'Work Suspension Order No. 1', status: 'Approved', remarks: 'Effectivity: 2026-05-26 | Duration: 90 days | End: 2026-08-23 | Reason: EO 110' }]),
    /Work Suspension Order No. 1<\/td><td class='pc'>:<\/td><td>90 calendar days<br>May 26, 2026 to August 23, 2026<br>\(EO 110\)/);
  assert.match(html([{ documentName: 'Work Resumption Order No. 1', status: 'Pending', remarks: 'Effectivity: 2026-08-16' }]),
    /Work Resumption Order No. 1<\/td><td class='pc'>:<\/td><td>August 16, 2026 \(for approval\)/);
});

test('billing abstract: each line as quantity, amount and share before, in this billing and to date, with subtotals and the grand total', () => {
  const project = { name: 'P', contractDurationDays: 360, dateStarted: '2025-01-06', completionDateOriginal: '2025-12-31', targetAccomplishment: 12 };
  // The uploaded abstract gives the part amounts; the billing of March 15 is after its As-of date, so it is not in "previous".
  const abstract = { asOf: '2025-03-14', equivUnits: 100, rowsJson: JSON.stringify([{ kind: 'part', name: 'LAND ACQUISITION COST' }, { kind: 'subtotal', amount: 1000000, equiv: [1, 2, 3] },
    { kind: 'part', name: 'LAND DEVELOPMENT' }, { kind: 'subtotal', amount: 2000000 }, { kind: 'part', name: 'HOUSING CONSTRUCTION' }, { kind: 'subtotal', amount: 7000000 }]) };
  const payments = [{ uuid: 'a', billingType: 'Land acquisition', grossAmount: 400000, date: '2025-02-01' }, { uuid: 'adv', billingType: '', grossAmount: 1500000, date: '2025-02-02' },
    { uuid: 'late', billingType: 'Land development', grossAmount: 99, date: '2025-03-15' }];
  const signatories = { preparedName: 'Juan', preparedPosition: 'Engineer', checkedName: '', checkedPosition: 'District Manager', notedName: '', notedPosition: 'Regional Manager' };
  const sheet = period => P.billingAbstractHtml({ project, docs: [], payments, abstract, period, units: 100, signatories });
  const html = sheet({ covered: ' February 1 - 28, 2025 ', lots: 0, development: 310000, m1: 0, m2: 0 });
  const row = (...cells) => new RegExp(cells.map(c => `>${c.replace(/[.()%]/g, '\\$&')}</td>`).join('<td[^>]*'));
  assert.match(html, /<div class='period'>Period covered: February 1 - 28, 2025<\/div>/);
  assert.match(sheet({ covered: ' ', lots: 0, development: 0, m1: 0, m2: 0 }), /<div class='period'>As of March 14, 2025<\/div>/); // no period typed
  assert.ok(!/equivalent/i.test(html)); // the billing format has no equivalent-units columns
  // Land acquisition: 40 of 100 lots billed before, nothing in this billing.
  assert.match(html, row('1', 'LAND ACQUISITION COST', '100', 'LOT', '10,000.00', '1,000,000.00', '40', '400,000.00', '40.00%', '0', '0.00', '0.00%', '40', '400,000.00', '40.00%', '60', '600,000.00'));
  // Land development is typed as an amount: 310,000 at 20,000 a lot is 15.5 lots.
  assert.match(html, row('2', 'LAND DEVELOPMENT', '100', 'LOT', '20,000.00', '2,000,000.00', '0', '0.00', '0.00%', '15.5', '310,000.00', '15.50%', '15.5', '310,000.00', '15.50%', '84.5', '1,690,000.00'));
  assert.match(html, row('3.1', 'MILESTONE 1 (80%)', '100', 'UNIT', '56,000.00', '5,600,000.00'));
  // Typed quantities become amounts: 2 units at Milestone 2 are 28,000.
  assert.match(sheet({ covered: '', lots: 0, development: 0, m1: 0, m2: 2 }), row('3.2', 'MILESTONE 2 (20%)', '100', 'UNIT', '14,000.00', '1,400,000.00', '0', '0.00', '0.00%', '2', '28,000.00', '2.00%'));
  assert.match(html, row('Subtotal (Housing Construction)', '', '', '70,000.00', '7,000,000.00'));
  // The advance payment has no type, so it is not in the abstract: 710,000 of 10,000,000 billed to date.
  assert.match(html, row('GRAND TOTAL', '', '', '100,000.00', '10,000,000.00', '', '400,000.00', '4.00%', '', '310,000.00', '3.10%', '', '710,000.00', '7.10%', '', '9,290,000.00'));
  // One blank line between the contract cost and the target.
  assert.match(html, /CONTRACT COST<\/td>.*<\/tr>\s*<tr><td colspan='5'>&nbsp;<\/td><\/tr>\s*<tr><td class='pl'>TARGET ACCOMPLISHMENT/);
  assert.match(html, /TARGET ACCOMPLISHMENT<\/td><td class='pc'>:<\/td><td>12.00%/);
  assert.ok(html.includes("<th colspan='2' rowspan='2'>BALANCE</th>")); // the billing format keeps its BALANCE columns
  assert.match(html, /ACTUAL ACCOMPLISHMENT<\/td><td class='pc'>:<\/td><td>7.10%/);
  assert.match(html, /ADVANCE\/SLIPPAGE<\/td><td class='pc'>:<\/td><td>-4.90%/);
  assert.match(html, /Approved by:/);
});

test('abstract of the actual work: the billing format\'s top with the tab\'s target, every row in the abstract\'s columns, then the signatures', () => {
  const none = [null, null, null], blank = { qty: null, unit: '', unitCost: null, amount: null, prev: none, period: none, toDate: none, balance: [null, null] };
  const project = { name: 'P & Q', location: 'Pandi', contractor: 'C', contractDurationDays: 360, dateStarted: '2025-01-06', completionDateOriginal: '2025-12-31', targetAccomplishment: 12 };
  const abstract = { asOf: '2025-03-14', overallPct: 10.73, equivUnits: 100, rowsJson: JSON.stringify([
    { ...blank, kind: 'part', no: '2', name: 'LAND DEVELOPMENT' },
    { ...blank, kind: 'group', no: '2.1', name: 'GENERAL <REQUIREMENTS>' },
    { kind: 'item', no: '2.1.1', name: 'Mobilization/Demobilization', qty: 1, unit: 'l.s.', unitCost: 400000, amount: 400000, prev: [0, 0, 0], period: [0.25, 100000, 25], toDate: [0.25, 100000, 25], balance: [0.75, 300000], equiv: [0, 342.5, 1027.5] },
    { ...blank, kind: 'item', no: '2.2.1', name: 'Road Excavation', qty: 1234.56789, unit: 'cu.m.', amount: 1600000, toDate: [null, 404000, 25.25] },
    { ...blank, kind: 'subtotal', no: '', name: 'Subtotal (Land Development)', amount: 2000000, toDate: [null, 504000, 25.21] },
    { ...blank, kind: 'total', no: '', name: 'GRAND TOTAL', amount: 10000000, toDate: [null, 1074000, 10.73] },
  ]) };
  const signatories = { preparedName: 'Juan', preparedPosition: 'Engineer', checkedName: '', checkedPosition: 'District Manager', notedName: '', notedPosition: 'Regional Manager' };
  const html = P.actualAbstractHtml({ project, docs: [], abstract, signatories, banner: 'b.png' });
  const row = (...cells) => new RegExp(`${cells.map(c => `>${c.replace(/[.()%/]/g, '\\$&')}</td>`).join('<td[^>]*')}</tr>`);
  assert.match(html, /^<img class='banner' src='b.png'/);
  assert.match(html, /<h1>ABSTRACT OF PHYSICAL ACCOMPLISHMENT<\/h1>\s*<div class='period'>As of March 14, 2025<\/div>/);
  assert.match(html, /PROJECT NAME<\/td><td class='pc'>:<\/td><td>P &amp; Q<\/td><td class='pl'>CONTRACT DURATION<\/td><td>: 360 C.D.<\/td>/);
  // Without a bar chart the target is the project's stored one; the actual is the abstract's, not what was billed.
  // One blank line between the contract cost and the target.
  assert.match(html, /CONTRACT COST<\/td>.*<\/tr>\s*<tr><td colspan='5'>&nbsp;<\/td><\/tr>\s*<tr><td class='pl'>TARGET ACCOMPLISHMENT/);
  assert.match(html, /TARGET ACCOMPLISHMENT<\/td><td class='pc'>:<\/td><td>12.00%/);
  assert.match(html, /ACTUAL ACCOMPLISHMENT<\/td><td class='pc'>:<\/td><td>10.73%/);
  assert.match(html, /ADVANCE\/SLIPPAGE<\/td><td class='pc'>:<\/td><td>-1.27%/);
  // The header rows are a thead, so they repeat on every page of a long abstract.
  assert.match(html, /<thead><tr><th rowspan='3'>ITEM NO\.<\/th>.*<th>QTY<\/th><th>AMOUNT<\/th><th>% WT<\/th><\/tr><\/thead>/s);
  assert.ok(!html.includes('BALANCE')); // the printed abstract of the actual work has no BALANCE columns
  assert.ok(html.includes("<tr><td class='c'>2</td><td><b>LAND DEVELOPMENT</b></td>"));
  assert.ok(html.includes("<td class='c'>2.1</td><td><b>GENERAL &lt;REQUIREMENTS&gt;</b></td>"));
  // Every column of the row, ending at TO DATE: its balance and the equivalent units it carries are not shown.
  assert.match(html, row('2.1.1', 'Mobilization/Demobilization', '1', 'l.s.', '400,000.00', '400,000.00', '0', '0.00', '0.00%', '0.25', '100,000.00', '25.00%', '0.25', '100,000.00', '25.00%'));
  assert.ok(!/equivalent/i.test(html));
  // Empty cells stay blank; a quantity shows 4 decimals at most; subtotals and the grand total are bold rows.
  assert.match(html, row('2.2.1', 'Road Excavation', '1,234.5679', 'cu.m.', '', '1,600,000.00', '', '', '', '', '', '', '', '404,000.00', '25.25%'));
  assert.match(html, new RegExp(`<tr class='tot'><td class='c'></td><td${row('GRAND TOTAL', '', '', '', '10,000,000.00', '', '', '', '', '', '', '', '1,074,000.00', '10.73%').source}`));
  assert.match(html, /<div class='keep'><table class='sig'>.*Prepared by:.*Checked by:.*Approved by:/s);
});

test('actual accomplishment tab: every pay item of an uploaded abstract in the sheet\'s columns, a part to a block, then the grand total', () => {
  const none = [null, null, null];
  const rows = [
    { kind: 'part', no: '2', name: 'LAND DEVELOPMENT', qty: null, unit: '', unitCost: null, amount: null, prev: none, period: none, toDate: none, balance: [null, null] },
    { kind: 'group', no: '2.1', name: 'GENERAL <REQUIREMENTS>', qty: null, unit: '', unitCost: null, amount: null, prev: none, period: none, toDate: none, balance: [null, null] },
    { kind: 'item', no: '2.1.1', name: 'Mobilization/Demobilization', qty: 1, unit: 'l.s.', unitCost: 400000, amount: 400000, prev: [0, 0, 0], period: [0.25, 100000, 25], toDate: [0.25, 100000, 25], balance: [0.75, 300000], equiv: [0, 342.5, 1027.5] },
    { kind: 'item', no: '2.2.1', name: 'Road Excavation', qty: 1234.56789, unit: 'cu.m.', unitCost: null, amount: 1600000, prev: none, period: none, toDate: [null, 404000, 25.25], balance: [null, null] },
    { kind: 'subtotal', no: '', name: 'Subtotal (Land Development)', qty: null, unit: '', unitCost: null, amount: 2000000, prev: none, period: none, toDate: [null, 504000, 25.21], balance: [null, null] },
    { kind: 'part', no: '3', name: 'HOUSING CONSTRUCTION', qty: null, unit: '', unitCost: null, amount: null, prev: none, period: none, toDate: none, balance: [null, null] },
    { kind: 'total', no: '', name: 'GRAND TOTAL', qty: null, unit: '', unitCost: null, amount: 9000000, prev: none, period: none, toDate: [null, 504000, 5.6], balance: [null, null] },
  ];
  const html = P.abstractItemsHtml(rows);
  const cells = (...list) => list.map(c => `<td class="num">${c}</td>`).join('');
  // A part heads its block with its own subtotal's amount and % to date; one without a subtotal shows zeros and no table.
  assert.match(html, /<details class="card apa-part" open><summary>.*<b>2 LAND DEVELOPMENT<\/b>\s*<span class="num">2,000,000.00<\/span><span class="num">25.21%<\/span><\/summary><div class="scroll">/);
  assert.match(html, /<b>3 HOUSING CONSTRUCTION<\/b>\s*<span class="num">0.00<\/span><span class="num">0.00%<\/span><\/summary><\/details>/);
  assert.equal(html.match(/<details /g).length, 2);
  assert.ok(html.includes('<tr class="apa-group"><td>2.1</td><td colspan="16">GENERAL &lt;REQUIREMENTS&gt;</td></tr>'));
  assert.ok(html.includes(`<tr class="apa-item"><td>2.1.1</td><td>Mobilization/Demobilization</td>${cells('1')}<td>l.s.</td>${cells('400,000.00', '400,000.00', '0', '0.00', '0.00%', '0.25', '100,000.00', '25.00%', '0.25', '100,000.00', '25.00%', '0.75', '300,000.00')}</tr>`));
  // Empty cells stay blank; a quantity shows 4 decimals at most.
  assert.ok(html.includes(`<tr class="apa-item"><td>2.2.1</td><td>Road Excavation</td>${cells('1,234.5679')}<td>cu.m.</td>${cells('', '1,600,000.00', '', '', '', '', '', '', '', '404,000.00', '25.25%', '', '')}</tr>`));
  assert.ok(html.includes(`<tr class="apa-subtotal"><td></td><td>Subtotal (Land Development)</td>${cells('')}<td></td>${cells('', '2,000,000.00', '', '', '', '', '', '', '', '504,000.00', '25.21%', '', '')}</tr>`));
  // The grand total comes last, after the parts.
  assert.ok(html.lastIndexOf('<tr class="apa-total"><td></td><td>GRAND TOTAL</td>') > html.lastIndexOf('</details>'));
  assert.equal(html.match(/<th colspan="3">TO DATE<\/th>/g).length, 2);
  // The table ends at BALANCE: the sheet's equivalent-units columns are not shown, though the rows carry them.
  assert.ok(rows.some(r => r.equiv?.some(v => v != null)));
  assert.ok(!html.includes('EQUIVALENT'));
  assert.ok(html.includes('<th>QTY</th><th>AMOUNT</th></tr></thead>'));
  assert.equal(P.abstractItemsHtml([]), '');
});

test('a part that is its own pay item shows its quantity and figures under its heading', () => {
  const none = [null, null, null];
  const html = P.abstractItemsHtml([
    { kind: 'part', no: '1', name: 'LAND ACQUISITION COST', qty: 520, unit: 'LOT', unitCost: 65250.5, amount: 33930260, prev: none, period: none, toDate: [420, 27405210, 80.77], balance: [100, 6525050], equiv: none },
    { kind: 'subtotal', no: '', name: 'Subtotal (Land Acquisition Cost)', qty: null, unit: '', unitCost: null, amount: 33930260, prev: none, period: none, toDate: [null, 27405210, 80.77], balance: [null, null], equiv: none }]);
  assert.match(html, /<tr class="apa-item"><td>1<\/td><td>LAND ACQUISITION COST<\/td><td class="num">520<\/td><td>LOT<\/td>/);
});
