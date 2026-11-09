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
    '<td>Roads</td><td class=\'n\'>100.00%</td><td class=\'n\'>20.00%</td><td class=\'n\'>18.00%</td><td class=\'n\'>18.00%</td>',
    '1. 1st billing', 'Php 1,000.00', 'Php 9,000.00 (90.00%)', '130 c.d.', 'November 21, 2026',
    "<td class='c'>3</td><td>September 2026</td>", '1. Flooding', 'September 4, 2026</td><td></td><td>Pumped</td>',
    'Add crews<br>Work Sundays', 'Checked by:', 'JUAN', 'on or before end of each month']) {
    assert.ok(html.includes(x), x);
  }
  assert.ok(!html.includes('Next') && !html.includes('Old'), 'October billing and August issue are left out');
  assert.ok(html.includes('<td>None</td>'), 'no variation or suspension orders');
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
  assert.deepEqual(L.lotBlocks(lots).map(b => [b.name, b.lots.map(l => l.lotNumber)]),
    [['Block 2', ['9', '10']], ['Block 10', ['2']], ['Block OS', ['1']], ['Road Lot', ['1']], ['Unassigned lots', ['']]]);
});
