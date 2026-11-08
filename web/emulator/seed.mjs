// Fills the local emulators (project demo-nha, never the live project) with test accounts and
// two projects in the phones' cloud format. Needs `firebase emulators:start --project demo-nha` running.
// Run: node web/emulator/seed.mjs
//
// Test accounts (emulator only), all with password test-pass-123:
//   superviewer@test.local  Super Viewer: sees both projects, B shows "View only"
//   engineer@test.local     Field Engineer assigned to project A only
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
console.log('Seeded demo-nha: 3 accounts, 5 projects, 7 records.');
