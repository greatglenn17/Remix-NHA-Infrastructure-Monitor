// Fills the local emulators (project demo-nha, never the live project) with test accounts and
// two projects in the phones' cloud format. Needs `firebase emulators:start --project demo-nha` running.
// Run: node web/emulator/seed.mjs
//
// Test accounts (emulator only), all with password test-pass-123:
//   superviewer@test.local  Super Viewer: sees both projects, B shows "View only"
//   engineer@test.local     Field Engineer assigned to project A only
//   engadmin@test.local     Engineer Admin with no project yet (for New project)
//   admin@test.local        Super Admin
const PROJECT = 'demo-nha';
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const FS = `http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents`;
const OWNER = { Authorization: 'Bearer owner', 'Content-Type': 'application/json' }; // emulator admin, skips rules
const PASSWORD = 'test-pass-123';

async function call(url, method, body, headers = { 'Content-Type': 'application/json' }) {
  const res = await fetch(url, { method, headers, body: JSON.stringify(body) });
  if (!res.ok) throw new Error(`${method} ${url}: ${res.status} ${await res.text()}`);
  return res.json();
}

async function account(email, claims) {
  let uid;
  try {
    uid = (await call(`${AUTH}/accounts:signUp?key=demo`, 'POST', { email, password: PASSWORD })).localId;
  } catch {
    uid = (await call(`${AUTH}/accounts:signInWithPassword?key=demo`, 'POST', { email, password: PASSWORD })).localId;
  }
  await call(`${AUTH}/projects/${PROJECT}/accounts:update`, 'POST', { localId: uid, customAttributes: JSON.stringify(claims) }, OWNER);
  // The profile a phone saves at sign-up: Manage users and Assign people list accounts from here.
  await put(`users/${uid}`, { email, role: claims.role, approved: true });
  return uid;
}

/** Plain JS -> Firestore REST typed fields. */
function value(v) {
  if (v === null) return { nullValue: null };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (Number.isInteger(v)) return { integerValue: String(v) };
  if (typeof v === 'number') return { doubleValue: v };
  return { stringValue: String(v) };
}
const put = (path, obj) =>
  call(`${FS}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, value(v)])) }, OWNER);

const engineer = await account('engineer@test.local', { role: 'FIELD_ENGINEER' });
await account('superviewer@test.local', { role: 'SUPER_VIEWER' });
await account('engadmin@test.local', { role: 'ENGINEER_ADMIN' });
await account('admin@test.local', { role: 'SUPER_ADMIN' });

const now = Date.now();
const A = '11111111-1111-4111-8111-111111111111';
const B = '22222222-2222-4222-8222-222222222222';
const base = { revision: 1, createdAt: now, updatedAt: now, isDeleted: false, implementingOffice: 'NHA Bulacan District Office' };
const scope = JSON.stringify([
  { name: 'Land development', amount: 30000000, weightPct: 60 },
  { name: 'Housing units', amount: 20000000, weightPct: 40 },
]);
await put(`projects/${A}`, {
  ...base, projectUuid: A, name: 'Sample Resettlement Phase 1', location: 'Pandi, Bulacan', contractor: 'ABC Builders',
  projectType: 'Housing', landArea: '5 ha', numberOfUnits: '400', scopeOfWork: scope, contractCostOriginal: 50000000,
  barChartJson: JSON.stringify([{ label: 'Original', effective: '2026-01-05', items: [{ name: 'Land development', amount: 30000000, bars: [[1, 240]] }, { name: 'Housing units', amount: 20000000, bars: [[121, 540]] }] }]),
  contractDurationDays: 540, dateStarted: '2026-01-05', completionDateOriginal: '2027-06-28', completionDateRevised: '-',
  status: 'On-going', targetAccomplishment: 45, actualAccomplishment: 41.25, assignedUids: [engineer], latestUpdatePhotoUrl: '',
});
await put(`projects/${B}`, {
  ...base, projectUuid: B, name: 'Sample Road Concreting', location: 'Norzagaray, Bulacan', contractor: 'XYZ Construction',
  projectType: 'Infrastructure', scopeOfWork: 'Road concreting', contractCostOriginal: 12500000, contractDurationDays: 180,
  dateStarted: '2026-03-01', completionDateOriginal: '2026-08-28', completionDateRevised: '2026-12-15',
  status: 'On-going', targetAccomplishment: 100, actualAccomplishment: 100, assignedUids: ['someone-else'],
});

// Records exactly as RecordSync.buildDocument writes them: the row as a JSON string under `data`.
const record = (uuid, type, projectUuid, assignedUids, data) => put(`records/${uuid}`, {
  uuid, type, projectUuid, assignedUids, revision: 1, updatedAt: now, updatedByUid: engineer, isDeleted: false, data: JSON.stringify(data),
});
const rid = n => `aaaaaaaa-0000-4000-8000-${String(n).padStart(12, '0')}`;
await record(rid(1), 'weekly_reports', A, [engineer], {
  reportingWeek: 'Week 44: October 31 - November 6, 2026', daysElapsed: 307, remainingDays: 233,
  targetAccomplishmentPct: 45, actualAccomplishmentPct: 41.25, submittedByStaff: 'Engr. Sample', createdAtTimestamp: now,
  manpowerJson: '[]', equipmentJson: '[]', activitiesJson: '[]', issuesJson: '[]', accomplishmentItemsJson: '[]',
  documentsIssuedReceivedJson: '[]', attachedPhotoUrlsJson: '[]', photoDetailsJson: '{}', _weather: [],
});
await record(rid(2), 'project_payments', A, [engineer], {
  name: '1st partial billing', dvNo: '', date: '2026-06-30', periodCovered: 'Jan - Jun 2026', grossAmount: 10000000,
  percentage: 20, balanceAmount: 40000000, balancePercentage: 80, fileUrl: '', billingType: '', units: 0, unitPrice: 0, retentionPct: 10,
});
await record(rid(3), 'project_issues', A, [engineer], {
  date: '2026-10-20', description: 'Right-of-way issue at Block 3', actionTaken: '', remarks: '', loggedBy: 'Engr. Sample',
  timestamp: now, status: 'Pending', isCritical: 0, targetDate: '', actionBy: '',
});
await record(rid(300), 'project_issues', A, [engineer], {
  date: '2026-10-28', description: 'Delayed delivery of rebars for Block 5 footings. Supplier cites truck shortage.', actionTaken: 'Wrote the supplier and asked for a revised schedule.',
  remarks: 'Follow up on Monday.', loggedBy: 'Engr. Sample', timestamp: now + 1, status: 'Pending', isCritical: 1, targetDate: '2026-11-03', actionBy: 'Contractor/Developer',
});
await record(rid(301), 'project_issues', A, [engineer], {
  date: '2026-09-15', description: 'Water service connection', actionTaken: 'Coordinated with the water district.', remarks: '', loggedBy: 'Engr. Sample',
  timestamp: now - 1, status: 'Resolved', isCritical: 0, targetDate: '2026-10-01', actionBy: 'Implementing Office',
});
await record(rid(310), 'audit_logs', A, [engineer], { timestamp: now - 3600000, user: 'Engr. Sample', device: 'samsung SM-A155F',
  actionType: 'Weekly Report Creation', details: 'Added the report for Oct 31, 2026 - Nov 6, 2026', oldValue: '', newValue: '' });
await record(rid(311), 'audit_logs', A, [engineer], { timestamp: now - 1800000, user: 'Engr. Sample', device: 'samsung SM-A155F',
  actionType: 'Project Edit', details: 'Actual accomplishment', oldValue: '40.10%', newValue: '41.25%' });
await record(rid(312), 'audit_logs', A, [engineer], { timestamp: now - 600000, user: 'Engr. Sample', device: '',
  actionType: 'Issue Deletion', details: 'Removed a duplicate issue', oldValue: 'Right-of-way issue at Block 3', newValue: '' });
await record(rid(4), 'pending_documents', A, [engineer], {
  documentName: 'Variation Order No. 1 - PHP 1,500,000.00', status: 'Approved', remarks: '', isCoreChecklist: 0, fileUrl: '',
});
await record(rid(5), 'pending_documents', A, [engineer], {
  documentName: 'Time Extension No. 1 (30 days)', status: 'Pending', remarks: 'Rain delays', isCoreChecklist: 0, fileUrl: '',
});
await record(rid(6), 'project_issues', B, ['someone-else'], {
  date: '2026-08-01', description: 'Closed sample', actionTaken: 'Done', remarks: '', loggedBy: 'x', timestamp: now, status: 'Resolved',
  isCritical: 0, targetDate: '', actionBy: '',
});
// C: far behind and billed ahead of work (critical + billing flags). D: due within 30 days.
const C = '33333333-3333-4333-8333-333333333333';
const D = '44444444-4444-4444-8444-444444444444';
await put(`projects/${C}`, {
  ...base, projectUuid: C, name: 'Sample Medium-Rise Building', location: 'Baliwag, Bulacan', contractor: 'DEF Builders',
  projectType: 'Housing', landArea: '1.2 ha', numberOfUnits: '120', scopeOfWork: 'Medium-rise building', contractCostOriginal: 84200000,
  contractDurationDays: 600, dateStarted: '2026-02-01', completionDateOriginal: '2027-09-23', completionDateRevised: '-',
  status: 'Behind Schedule', targetAccomplishment: 38, actualAccomplishment: 21.4, assignedUids: [engineer],
});
await put(`projects/${D}`, {
  ...base, projectUuid: D, name: 'Sample Drainage Improvement', location: 'Bocaue, Bulacan', contractor: 'GHI Construction',
  projectType: 'Infrastructure', scopeOfWork: 'Drainage', contractCostOriginal: 9650000, contractDurationDays: 240,
  dateStarted: '2026-04-01', completionDateOriginal: new Date(now + 20 * 86400000).toLocaleDateString('en-CA'), completionDateRevised: '-',
  status: 'On-going', targetAccomplishment: 88, actualAccomplishment: 86.5, assignedUids: ['someone-else'],
});
await record(rid(7), 'project_payments', C, [engineer], {
  name: '1st partial billing', date: '2026-08-15', periodCovered: 'Feb - Jul 2026', grossAmount: 30000000,
  percentage: 35.63, balanceAmount: 54200000, balancePercentage: 64.37, retentionPct: 10,
});
// E: overdue by 5 days with no work done, no billings, no reports (the empty and alarm states).
const E = '55555555-5555-4555-8555-555555555555';
await put(`projects/${E}`, {
  ...base, projectUuid: E, name: 'Sample Site Development', location: 'Sapang Palay, San Jose del Monte, Bulacan', contractor: 'JKL Realty and Construction',
  projectType: 'Housing', landArea: '', numberOfUnits: '', scopeOfWork: 'Site development', contractCostOriginal: 15000000, contractDurationDays: 120,
  dateStarted: '2026-07-01', completionDateOriginal: new Date(now - 5 * 86400000).toLocaleDateString('en-CA'), completionDateRevised: '-',
  status: 'On-going', targetAccomplishment: 0, actualAccomplishment: 0, assignedUids: [engineer],
});

// Progress, Payments, Documents, print and lots for project A, with files in the Storage emulator.
const BUCKET = `http://127.0.0.1:9199/v0/b/${PROJECT}.appspot.com/o`;
const { readFile } = await import('node:fs/promises');
const photoBytes = await readFile(new URL('../signin-bg.webp', import.meta.url)); // any image the browser can draw
const pdfBytes = Buffer.from('%PDF-1.1\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 300 144]/Parent 2 0 R>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');
// A small drawn subdivision plan (A4 landscape): each lot a rectangle at the spot `lotBox` gives, the road a strip.
// Points are 0..1 from the top-left, as polygonNormalizedJson keeps them.
const lotBox = i => { const x = 0.1 + (i >> 2) * 0.2, y = 0.12 + (i % 4) * 0.16; return [[x, y], [x + 0.16, y], [x + 0.16, y + 0.14], [x, y + 0.14]]; };
const ROAD = [[0.1, 0.78], [0.86, 0.78], [0.86, 0.86], [0.1, 0.86]];
function planPdf(count) {
  const W = 842, H = 595, at = ([x, y]) => `${(x * W).toFixed(1)} ${((1 - y) * H).toFixed(1)}`;
  const shape = pts => `${at(pts[0])} m ${pts.slice(1).map(q => `${at(q)} l`).join(' ')} h S`;
  const ops = ['0.8 w', ...Array.from({ length: count }, (_, i) => shape(lotBox(i))), shape(ROAD),
    'BT /F1 14 Tf 84 560 Td (SUBDIVISION PLAN - SAMPLE) Tj ET', ...Array.from({ length: Math.ceil(count / 4) }, (_, b) => `BT /F1 10 Tf ${at([0.1 + b * 0.2, 0.1])} Td (BLOCK ${b + 1}) Tj ET`)];
  const stream = ops.join('\n');
  const objs = ['<</Type/Catalog/Pages 2 0 R>>', '<</Type/Pages/Kids[3 0 R]/Count 1>>',
    `<</Type/Page/MediaBox[0 0 ${W} ${H}]/Parent 2 0 R/Resources<</Font<</F1 5 0 R>>>>/Contents 4 0 R>>`,
    `<</Length ${stream.length}>>stream\n${stream}\nendstream`, '<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>'];
  let out = '%PDF-1.4\n';
  const offsets = objs.map((o, i) => { const at0 = out.length; out += `${i + 1} 0 obj${o}endobj\n`; return at0; });
  const xref = out.length;
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${out}trailer<</Size ${objs.length + 1}/Root 1 0 R>>\nstartxref\n${xref}\n%%EOF\n`, 'latin1');
}
async function upload(id, ext, bytes) {
  const name = `attachments/${A}/${id}.${ext}`;
  const res = await fetch(`${BUCKET}?name=${encodeURIComponent(name)}`, {
    method: 'POST', headers: { Authorization: 'Bearer owner', 'Content-Type': ext === 'pdf' ? 'application/pdf' : 'image/jpeg' }, body: bytes || (ext === 'pdf' ? pdfBytes : photoBytes),
  });
  if (!res.ok) throw new Error(`upload ${name}: ${res.status} ${await res.text()}`);
  return `nhafile:${A}/${id}.${ext}`;
}
const fid = n => `bbbbbbbb-0000-4000-8000-${String(n).padStart(12, '0')}`;
const [photo1, photo2, photo3, dvScan, letterScan, replyScan, requestScan, orderScan, planScan, phase2Scan] = await Promise.all([
  upload(fid(1), 'jpg'), upload(fid(2), 'jpg'), upload(fid(3), 'jpg'), upload(fid(4), 'pdf'), upload(fid(5), 'jpg'), upload(fid(6), 'pdf'), upload(fid(7), 'pdf'), upload(fid(8), 'jpg'),
  upload(fid(9), 'pdf', planPdf(16)), upload(fid(10), 'pdf', planPdf(4)),
]);
const hours = pattern => Array.from({ length: 24 }, (_, h) => pattern(h)).join(',');
const week = (from, days) => days.map((dayOfWeek, i) => ({
  date: new Date(Date.parse(from) + i * 86400000).toISOString().slice(0, 10), dayOfWeek,
  hourlyConditionsCsv: hours(h => (i === 3 && h >= 9 && h <= 15 ? 'RAINY' : h >= 13 && h <= 15 ? 'CLOUDY' : i === 4 && h > 17 ? 'STORMY' : 'FAIR')),
}));
const DAYS7 = ['Saturday', 'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
const weekly = (n, label, from, day, target, actual, extra) => record(rid(n), 'weekly_reports', A, [engineer], {
  reportingWeek: label, daysElapsed: day, remainingDays: 540 - day, targetAccomplishmentPct: target, actualAccomplishmentPct: actual,
  submittedByStaff: 'Engr. Sample', createdAtTimestamp: Date.parse(from),
  manpowerJson: JSON.stringify([{ designation: 'Foreman', count: 1 }, { designation: 'Mason', count: 6 }, { designation: 'Laborer', count: 12 }]),
  equipmentJson: JSON.stringify([{ description: 'Concrete mixer', count: 2, status: 'Operational' }, { description: 'Backhoe', count: 1, status: 'Idle', remarks: 'Awaiting fuel' }]),
  activitiesJson: JSON.stringify([{ day: 'Saturday', description: 'Survey of Block 4' }, { day: 'Monday - Wednesday', description: 'Excavation for footings, Block 3', remarks: 'On schedule' },
    { day: 'Thursday', description: 'Rebar works', remarks: 'Rain in the afternoon' }]),
  issuesJson: JSON.stringify([{ description: 'Rain stopped concreting on Tuesday', actionTaken: 'Moved concreting to Wednesday', actionBy: 'Contractor/Developer' }]),
  accomplishmentItemsJson: JSON.stringify([{ itemDescription: 'Land development', weightPct: 60, targetPct: target + 4, actualPct: actual + 3 }, { itemDescription: 'Housing units', weightPct: 40, targetPct: target - 6, actualPct: actual - 5 }]),
  documentsIssuedReceivedJson: '[]', _weather: week(from, DAYS7), ...extra,
});
await weekly(1, 'Oct 31, 2026 - Nov 6, 2026', '2026-10-31', 301, 45, 41.25, {
  attachedPhotoUrlsJson: JSON.stringify([photo1, photo2, photo3]),
  photoDetailsJson: JSON.stringify({ [photo1]: { title: 'Footings, Block 3', takenAt: 'Nov 2, 2026 9:14 AM' }, [photo2]: { title: 'Rebar works' } }),
});
// A photo whose file is not in Storage (Photos names the report), in a list as a phone saves it: each / as \/.
const lostPhoto = `nhafile:${A}/${fid(99)}.jpg`;
await weekly(8, 'Oct 24, 2026 - Oct 30, 2026', '2026-10-24', 294, 43, 40.1, {
  attachedPhotoUrlsJson: JSON.stringify([photo2, lostPhoto]).replaceAll('/', '\\/'), photoDetailsJson: '{}',
});
await weekly(9, 'Week 42: October 17 - October 23, 2026', '2026-10-17', 287, 41, 39, { attachedPhotoUrlsJson: '[]', photoDetailsJson: '{}', issuesJson: '[]', _weather: [] });
await record(rid(10), 'monthly_reports', A, [engineer], { reportingMonth: 'September 2026', scopeWeightPct: 100, scopeTargetPct: 38, scopeActualPct: 36,
  paymentsJson: '[]', unworkableDaysCount: 2, workableDaysCount: 28, cpesIssuesJson: '[]', recommendations: '' });
await record(rid(11), 'project_payments', A, [engineer], {
  name: '2nd partial billing', dvNo: '2026-10-0141', date: '2026-10-15', periodCovered: 'Jul - Sep 2026', grossAmount: 4000000, percentage: 8,
  balanceAmount: 37500000, balancePercentage: 75, fileUrl: dvScan, billingType: 'M1 - Housing unit', units: 5, unitPrice: 800000, retentionPct: 0,
});
await record(rid(12), 'pending_documents', A, [engineer], { documentName: 'Time Extension No. 2 - 30 Days', status: 'Approved', remarks: 'Reason: Rain delays', fileUrl: orderScan });
await record(rid(13), 'pending_documents', A, [engineer], { documentName: 'Work Suspension No. 1 (Partial)', status: 'Approved',
  remarks: 'Duration: 10 Days | Effectivity: 2026-08-01 | End: 2026-08-11 | Reason: Right of way | Items: Housing units' });
await record(rid(14), 'pending_documents', A, [engineer], { documentName: '1st Partial Continuance of Work Suspension Order No. 1', status: 'Pending',
  remarks: 'Duration: 5 Days | Effectivity: 2026-08-11' });
await record(rid(15), 'pending_documents', A, [engineer], { documentName: 'Partial Work Resumption Order No. 1', status: 'Submitted', remarks: 'Effectivity: 2026-08-16' });
await record(rid(16), 'pending_documents', A, [engineer], { documentName: 'Building permit', status: 'Submitted', remarks: 'Filed at the municipal hall', fileUrl: orderScan });
await record(rid(17), 'pending_documents', A, [engineer], { documentName: 'Letter to Contractor No. 1', status: 'Pending', remarks: 'Reason: Slow mobilization | Date Received: 2026-10-26', fileUrl: letterScan });
await record(rid(18), 'contractor_letters', A, [engineer], { dateSent: '2026-10-27', subject: 'Additional manpower for Block 3', actionTaken: '',
  dateReceived: '2026-10-28', receivedFileUrl: letterScan, dateReplied: '', replyFileUrl: '' });
await record(rid(19), 'contractor_letters', A, [engineer], { dateSent: '2026-09-02', subject: 'Safety at the site', actionTaken: 'Added safety officer',
  dateReceived: '2026-09-03', receivedFileUrl: '', dateReplied: '2026-09-10', replyFileUrl: replyScan });
await record(rid(20), 'developer_requests', A, [engineer], { requestType: 'Time Extension', details: '30 days for rain', dateRequested: '2026-10-29',
  dateReceived: '2026-11-02', datePackaged: '', requestFileUrl: requestScan });
await record(rid(21), 'developer_requests', A, [engineer], { requestType: 'Variation Order', details: '', dateRequested: '2026-07-01',
  dateReceived: '2026-07-03', datePackaged: '2026-07-20', requestFileUrl: '' });
// Subdivision plan: an old version and the one in use; its lots in each progress band, plus lots that are not counted.
await record(rid(30), 'sdp_plans', A, [engineer], { planName: 'SDP rev 0', pdfFileUrl: '', version: 1, isActive: 0, uploadedDate: '2026-01-10', uploadedBy: 'x', siteName: '' });
await record(rid(31), 'sdp_plans', A, [engineer], { planName: 'SDP rev 1', pdfFileUrl: planScan, version: 2, isActive: 1, uploadedDate: '2026-03-10', uploadedBy: 'x', siteName: '' });
// A second site, so the print can put both plans on one page; its plan is turned a quarter turn, as on the phone.
await record(rid(32), 'sdp_plans', A, [engineer], { planName: 'Phase 2 SDP', pdfFileUrl: phase2Scan, version: 1, isActive: 1, uploadedDate: '2026-05-02', uploadedBy: 'x', siteName: 'Phase 2', rotation: 90 });
for (const [i, p] of [100, 60, 0, 30].entries()) {
  await record(rid(140 + i), 'sdp_lots', A, [engineer], { _ref_sdpPlanId: rid(32), blockNumber: '1', lotNumber: String(i + 1), lotAreaSqM: 40,
    polygonNormalizedJson: JSON.stringify(lotBox(i)), isActive: 1, lotUse: 'Saleable' });
  if (p) await record(rid(240 + i), 'sdp_lot_progress', A, [engineer], { _ref_sdpPlanId: rid(32), _ref_sdpLotId: rid(140 + i), physicalProgress: p });
}
const lotProgress = [0, 0, 0, 10, 20, 25, 30, 45, 60, 70, 80, 95, 100, 100, 100, 100];
for (const [i, p] of lotProgress.entries()) {
  const lotId = rid(100 + i);
  await record(lotId, 'sdp_lots', A, [engineer], { _ref_sdpPlanId: rid(31), blockNumber: String(1 + (i >> 2)), lotNumber: String(1 + (i % 4)), lotAreaSqM: 40,
    polygonNormalizedJson: JSON.stringify(lotBox(i)), isActive: 1, lotUse: 'Saleable', housingUnitNumber: `HU-${i + 1}` });
  // Built lots are billed M1 at 76% and M2 when done, so the Lots KPIs and filters have something to show.
  if (p) await record(rid(200 + i), 'sdp_lot_progress', A, [engineer], { _ref_sdpPlanId: rid(31), _ref_sdpLotId: lotId, physicalProgress: p,
    constructionStatus: p >= 100 ? 'Completed' : 'Under Construction', currentActivity: p >= 100 ? '' : 'Walls and roofing', contractor: 'ABC Builders',
    billingStatus: p >= 76 ? 'BILLED' : 'NOT BILLED', billingDate: p >= 76 ? '2026-10-15' : '', billingReference: p >= 76 ? 'DV 2026-118' : '',
    m2BillingStatus: p >= 100 ? 'BILLED' : 'NOT BILLED', m2BillingDate: p >= 100 ? '2026-11-02' : '' });
}
await record(rid(130), 'sdp_lots', A, [engineer], { _ref_sdpPlanId: rid(31), blockNumber: 'RL', lotNumber: '1', polygonNormalizedJson: JSON.stringify(ROAD), isActive: 1, lotUse: 'Road Lot' });
await record(rid(230), 'sdp_lot_progress', A, [engineer], { _ref_sdpPlanId: rid(31), _ref_sdpLotId: rid(130), physicalProgress: 50 });
await record(rid(131), 'sdp_lots', A, [engineer], { _ref_sdpPlanId: rid(30), blockNumber: '9', lotNumber: '9', polygonNormalizedJson: '[]', isActive: 1, lotUse: 'Saleable' });
console.log('Seeded demo-nha: 4 accounts, 5 projects, records and 10 files.');
