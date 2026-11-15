const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, deleteField, collection, query, where, getDocs,
} = require('firebase/firestore');
const { ref, getBytes, uploadBytes, deleteObject } = require('firebase/storage');

const root = path.join(__dirname, '..');
let testEnv;

const bytes = (n) => new Uint8Array(n);
const jpeg = { contentType: 'image/jpeg' };

const asEng = () => testEnv.authenticatedContext('eng', { role: 'FIELD_ENGINEER' });
const asEngAdmin = () => testEnv.authenticatedContext('eng', { role: 'ENGINEER_ADMIN' });
const asOther = () => testEnv.authenticatedContext('other', { role: 'FIELD_ENGINEER' });
const asViewer = () => testEnv.authenticatedContext('viewer', { role: 'VIEWER' });
const asAdmin = () => testEnv.authenticatedContext('admin', { role: 'SUPER_ADMIN' });
const asNoRole = () => testEnv.authenticatedContext('eng');
// Not assigned to P: a Super Viewer, and a field engineer the Super Admin let see all projects.
const asSuperViewer = () => testEnv.authenticatedContext('boss', { role: 'SUPER_VIEWER' });
const asSeesAll = () => testEnv.authenticatedContext('other', { role: 'FIELD_ENGINEER', allProjects: true });

// A record names its writer (`uid`), as every upload does.
const rec = (uids, uid = 'eng') => ({ uuid: 'R', projectUuid: 'P', revision: 2, assignedUids: uids, updatedByUid: uid });

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-nha',
    firestore: { rules: fs.readFileSync(path.join(root, 'firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8080 },
    storage: { rules: fs.readFileSync(path.join(root, 'storage.rules'), 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});

after(async () => { await testEnv.cleanup(); });

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'projects/P'), { assignedUids: ['eng', 'viewer'], name: 'X' });
    await setDoc(doc(db, 'records/R'), { uuid: 'R', projectUuid: 'P', revision: 1, assignedUids: ['eng', 'viewer'] });
  });
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const st = ctx.storage();
    await uploadBytes(ref(st, 'attachments/P/f.jpg'), bytes(10), jpeg);
    await uploadBytes(ref(st, 'attachments/f.jpg'), bytes(10), jpeg);
  });
});

describe('outsider', () => {
  it('cannot get project', () => assertFails(getDoc(doc(asOther().firestore(), 'projects/P'))));
  it('cannot get record', () => assertFails(getDoc(doc(asOther().firestore(), 'records/R'))));
  it('cannot read file', () => assertFails(getBytes(ref(asOther().storage(), 'attachments/P/f.jpg'))));
  it('cannot upload file', () => assertFails(uploadBytes(ref(asOther().storage(), 'attachments/P/o.jpg'), bytes(10), jpeg)));
  it('cannot update project', () => assertFails(updateDoc(doc(asOther().firestore(), 'projects/P'), { name: 'Z' })));
  it('cannot write record', () => assertFails(setDoc(doc(asOther().firestore(), 'records/R'), rec(['eng', 'viewer'], 'other'))));
  it('own-uid record query is allowed and empty', async () => {
    const snap = await assertSucceeds(getDocs(query(collection(asOther().firestore(), 'records'), where('assignedUids', 'array-contains', 'other'))));
    if (snap.size !== 0) throw new Error('expected empty');
  });
  it('own-uid project query is allowed and empty', async () => {
    const snap = await assertSucceeds(getDocs(query(collection(asOther().firestore(), 'projects'), where('assignedUids', 'array-contains', 'other'))));
    if (snap.size !== 0) throw new Error('expected empty');
  });
  it('unfiltered project query denied', () => assertFails(getDocs(collection(asOther().firestore(), 'projects'))));
  describe('assigned to another project Q', () => {
    beforeEach(() => testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/Q'), { assignedUids: ['other'], name: 'Q' })));
    it('can create a new record in Q (control)', () =>
      assertSucceeds(setDoc(doc(asOther().firestore(), 'records/RQ'), { uuid: 'RQ', projectUuid: 'Q', revision: 1, assignedUids: ['other'], updatedByUid: 'other' })));
    it('cannot take over a stored record of P by moving it into Q', () =>
      assertFails(setDoc(doc(asOther().firestore(), 'records/R'), { uuid: 'R', projectUuid: 'Q', revision: 2, assignedUids: ['other'], updatedByUid: 'other' })));
  });
  it('cannot write record for a missing project', () =>
    assertFails(setDoc(doc(asOther().firestore(), 'records/R2'), { uuid: 'R2', projectUuid: 'NOPE', revision: 1, assignedUids: ['other'], updatedByUid: 'other' })));
  it('cannot read file of a missing project', () => assertFails(getBytes(ref(asOther().storage(), 'attachments/NOPE/f.jpg'))));
});

for (const [name, ctx, uid] of [['super viewer', asSuperViewer, 'boss'], ['see-all engineer', asSeesAll, 'other']]) {
  describe(`${name}, not assigned`, () => {
    it('gets the project', () => assertSucceeds(getDoc(doc(ctx().firestore(), 'projects/P'))));
    it('lists every project', () => assertSucceeds(getDocs(collection(ctx().firestore(), 'projects'))));
    it('lists every record', () => assertSucceeds(getDocs(collection(ctx().firestore(), 'records'))));
    it('reads a file', () => assertSucceeds(getBytes(ref(ctx().storage(), 'attachments/P/f.jpg'))));
    it('cannot update the project', () => assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { name: 'Z' })));
    it('cannot write a record', () => assertFails(setDoc(doc(ctx().firestore(), 'records/R'), rec(['eng', 'viewer'], uid))));
    it('cannot upload a file', () => assertFails(uploadBytes(ref(ctx().storage(), 'attachments/P/o.jpg'), bytes(10), jpeg)));
    it('cannot delete a file', () => assertFails(deleteObject(ref(ctx().storage(), 'attachments/P/f.jpg'))));
  });
}

describe('super viewer, assigned', () => {
  beforeEach(() => testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/Q'), { assignedUids: ['boss'], name: 'Q' })));
  it('still cannot write a record', () =>
    assertFails(setDoc(doc(asSuperViewer().firestore(), 'records/RQ'), { uuid: 'RQ', projectUuid: 'Q', revision: 1, assignedUids: ['boss'], updatedByUid: 'boss' })));
  it('still cannot create a project', () =>
    assertFails(setDoc(doc(asSuperViewer().firestore(), 'projects/N'), { assignedUids: ['boss'], name: 'N', createdByUid: 'boss' })));
});

describe('assigned field engineer', () => {
  it('gets project', () => assertSucceeds(getDoc(doc(asEng().firestore(), 'projects/P'))));
  it('updates project name', () => assertSucceeds(updateDoc(doc(asEng().firestore(), 'projects/P'), { name: 'Y' })));
  it('cannot change assignedUids', () =>
    assertFails(updateDoc(doc(asEng().firestore(), 'projects/P'), { assignedUids: ['eng', 'x'] })));
  it('cannot change the bar chart', () =>
    assertFails(updateDoc(doc(asEng().firestore(), 'projects/P'), { barChartJson: '[]' })));
  it('gets record', () => assertSucceeds(getDoc(doc(asEng().firestore(), 'records/R'))));
  it('writes record with matching assignedUids', () =>
    assertSucceeds(setDoc(doc(asEng().firestore(), 'records/R'), rec(['eng', 'viewer']))));
  it('cannot write record with extra uid', () =>
    assertFails(setDoc(doc(asEng().firestore(), 'records/R'), rec(['eng', 'x']))));
  it('can create a new record in the project', () =>
    assertSucceeds(setDoc(doc(asEng().firestore(), 'records/R3'), { uuid: 'R3', projectUuid: 'P', revision: 1, assignedUids: ['eng', 'viewer'], updatedByUid: 'eng' })));
  it('cannot move a record into a project they are not assigned to', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/Q'), { assignedUids: ['other'] }));
    return assertFails(setDoc(doc(asEng().firestore(), 'records/R'), { uuid: 'R', projectUuid: 'Q', revision: 2, assignedUids: ['other'], updatedByUid: 'eng' }));
  });
  it('uploads image 1 KB', () => assertSucceeds(uploadBytes(ref(asEng().storage(), 'attachments/P/g.jpg'), bytes(1024), jpeg)));
  it('cannot upload non-image', () =>
    assertFails(uploadBytes(ref(asEng().storage(), 'attachments/P/g.txt'), bytes(10), { contentType: 'text/plain' })));
  it('cannot upload 20 MB', () =>
    assertFails(uploadBytes(ref(asEng().storage(), 'attachments/P/big.jpg'), bytes(20 * 1024 * 1024), jpeg)));
  it('reads file', () => assertSucceeds(getBytes(ref(asEng().storage(), 'attachments/P/f.jpg'))));
  it('cannot read legacy file', () => assertFails(getBytes(ref(asEng().storage(), 'attachments/f.jpg'))));
  it('cannot upload legacy path', () => assertFails(uploadBytes(ref(asEng().storage(), 'attachments/n.jpg'), bytes(10), jpeg)));
  it('cannot delete project', () => assertFails(deleteDoc(doc(asEng().firestore(), 'projects/P'))));
  it('cannot delete record', () => assertFails(deleteDoc(doc(asEng().firestore(), 'records/R'))));
  // A brand-new account has no claims until syncUserCustomClaims runs, so it is not approved yet.
  it('account without any claims reads but cannot write', async () => {
    await assertSucceeds(getDoc(doc(asNoRole().firestore(), 'projects/P')));
    await assertFails(updateDoc(doc(asNoRole().firestore(), 'projects/P'), { name: 'Y' }));
  });
});

describe('approvals', () => {
  const asPending = () => testEnv.authenticatedContext('eng', { role: 'ENGINEER_ADMIN', approved: false });
  const asApproved = () => testEnv.authenticatedContext('eng', { role: 'ENGINEER_ADMIN', approved: true });
  const create = (ctx) => setDoc(doc(ctx.firestore(), 'projects/N'), { name: 'N', assignedUids: ['eng'], createdByUid: 'eng' });
  beforeEach(() => testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users/eng'), { name: 'E', role: 'FIELD_ENGINEER' })));
  it('pending account cannot create a project', () => assertFails(create(asPending())));
  it('pending account cannot edit its project', () => assertFails(updateDoc(doc(asPending().firestore(), 'projects/P'), { name: 'Y' })));
  it('pending account cannot write a record', () => assertFails(setDoc(doc(asPending().firestore(), 'records/R'), rec(['eng', 'viewer']))));
  it('pending account cannot upload', () => assertFails(uploadBytes(ref(asPending().storage(), 'attachments/P/g.jpg'), bytes(10), jpeg)));
  it('pending account still reads its project', () => assertSucceeds(getDoc(doc(asPending().firestore(), 'projects/P'))));
  it('approved account creates a project', () => assertSucceeds(create(asApproved())));
  it('account from before approvals (role, no approved claim) still writes', () => assertSucceeds(create(asEngAdmin())));
  it('cannot sign up approved', () =>
    assertFails(setDoc(doc(testEnv.authenticatedContext('new', {}).firestore(), 'users/new'), { role: 'FIELD_ENGINEER', approved: true })));
  it('cannot approve itself', () => assertFails(updateDoc(doc(asPending().firestore(), 'users/eng'), { approved: true })));
  it('super admin approves', () => assertSucceeds(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { approved: true })));
  it('approval must be true or false', () => assertFails(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { approved: 'yes' })));
});

describe('creating projects', () => {
  const create = (ctx, uids, by = 'eng') => setDoc(doc(ctx.firestore(), 'projects/N'), { name: 'N', assignedUids: uids, createdByUid: by });
  it('engineer admin with only own uid', () => assertSucceeds(create(asEngAdmin(), ['eng'])));
  it('creator with extra uid denied', () => assertFails(create(asEngAdmin(), ['eng', 'x'])));
  it('creator with someone else only denied', () => assertFails(create(asEngAdmin(), ['x'])));
  it('creator with no list denied', () => assertFails(setDoc(doc(asEngAdmin().firestore(), 'projects/N'), { name: 'N', createdByUid: 'eng' })));
  it('creator naming someone else as the creator denied', () => assertFails(create(asEngAdmin(), ['eng'], 'x')));
  it('creator naming no creator denied', () => assertFails(setDoc(doc(asEngAdmin().firestore(), 'projects/N'), { name: 'N', assignedUids: ['eng'] })));
  it('field engineer cannot create', () => assertFails(create(asEng(), ['eng'])));
  // The server's creation time decides which of two same-name projects is the older one.
  it('creator cannot set the server\'s creation time', () =>
    assertFails(setDoc(doc(asEngAdmin().firestore(), 'projects/N'), { name: 'N', assignedUids: ['eng'], createdByUid: 'eng', serverCreatedAt: 0 })));
  it('nobody assigned can set, change or remove the server\'s creation time', async () => {
    for (const ctx of [asEng, asEngAdmin]) {
      await assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { serverCreatedAt: 0 }));
    }
    await testEnv.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), 'projects/P'), { serverCreatedAt: 5 }));
    for (const ctx of [asEng, asEngAdmin]) {
      await assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { serverCreatedAt: 0 }));
      await assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { serverCreatedAt: deleteField() }));
      await assertSucceeds(updateDoc(doc(ctx().firestore(), 'projects/P'), { name: 'Y', serverCreatedAt: 5 }));
    }
  });
  // The duplicate guard skips its own writes, which it knows by this updatedByUid.
  it('nobody assigned can sign a project write as the duplicate guard', async () => {
    const guard = 'duplicate-guard';
    await assertFails(setDoc(doc(asEngAdmin().firestore(), 'projects/N'), { name: 'N', assignedUids: ['eng'], createdByUid: 'eng', updatedByUid: guard }));
    for (const ctx of [asEng, asEngAdmin]) {
      await assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { name: 'Y', updatedByUid: guard }));
    }
    // After a write of the guard's, the next client write must carry the writer's own uid, as the app and the site do.
    for (const ctx of [asEng, asEngAdmin]) {
      await testEnv.withSecurityRulesDisabled((off) => updateDoc(doc(off.firestore(), 'projects/P'), { updatedByUid: guard }));
      await assertFails(updateDoc(doc(ctx().firestore(), 'projects/P'), { name: 'Y' }));
      await assertSucceeds(updateDoc(doc(ctx().firestore(), 'projects/P'), { name: 'Y', updatedByUid: 'eng' }));
    }
  });
  it('viewer cannot create', () => assertFails(create(asViewer(), ['viewer'], 'viewer')));
  it('admin can create with any list', () => assertSucceeds(create(asAdmin(), ['eng', 'x'])));
});

describe('viewer (assigned)', () => {
  it('gets project', () => assertSucceeds(getDoc(doc(asViewer().firestore(), 'projects/P'))));
  it('gets record', () => assertSucceeds(getDoc(doc(asViewer().firestore(), 'records/R'))));
  it('reads file', () => assertSucceeds(getBytes(ref(asViewer().storage(), 'attachments/P/f.jpg'))));
  it('cannot update project', () => assertFails(updateDoc(doc(asViewer().firestore(), 'projects/P'), { name: 'Y' })));
  it('cannot write record', () => assertFails(setDoc(doc(asViewer().firestore(), 'records/R'), rec(['eng', 'viewer'], 'viewer'))));
  it('cannot upload file', () => assertFails(uploadBytes(ref(asViewer().storage(), 'attachments/P/v.jpg'), bytes(10), jpeg)));
  it('cannot delete file', () => assertFails(deleteObject(ref(asViewer().storage(), 'attachments/P/f.jpg'))));
});

// What the app and the website hide from a Field Engineer (UserRole.kt) is refused here too, for someone who skips them.
describe('role limits on an assigned project', () => {
  const kept = (uuid, type, more = {}) =>
    ({ uuid, type, projectUuid: 'P', revision: 1, assignedUids: ['eng', 'viewer'], updatedByUid: 'x', isDeleted: false, data: '{"a":1}', ...more });
  // The record as `uid` uploads it again, with `changes`.
  const write = (ctx, uuid, type, changes = {}, uid = 'eng') =>
    setDoc(doc(ctx.firestore(), `records/${uuid}`), { ...kept(uuid, type), revision: 2, updatedByUid: uid, ...changes });
  const adminOnlyDeletes = ['weekly_reports', 'monthly_reports', 'project_issues', 'sdp_plans'];
  beforeEach(() => testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const type of [...adminOnlyDeletes, 'project_payments', 'audit_logs']) await setDoc(doc(db, `records/${type}`), kept(type, type));
  }));

  it('field engineer cannot delete the project', () => assertFails(updateDoc(doc(asEng().firestore(), 'projects/P'), { isDeleted: true })));
  it('field engineer still uploads the project as not deleted', () =>
    assertSucceeds(updateDoc(doc(asEng().firestore(), 'projects/P'), { name: 'Y', isDeleted: false })));
  it('engineer admin deletes the project', () => assertSucceeds(updateDoc(doc(asEngAdmin().firestore(), 'projects/P'), { isDeleted: true })));
  // The phones and the website drop a project that has no name.
  for (const [how, name] of [['removing its name', deleteField()], ['blanking its name', ''], ['a name that is no text', 5]]) {
    it(`field engineer cannot delete the project by ${how}`, () => assertFails(updateDoc(doc(asEng().firestore(), 'projects/P'), { name })));
  }
  it('field engineer cannot bring a deleted project back', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/D'), { assignedUids: ['eng'], name: 'D', isDeleted: true }));
    await assertFails(updateDoc(doc(asEng().firestore(), 'projects/D'), { isDeleted: false, name: 'New' }));
    await assertFails(updateDoc(doc(asEng().firestore(), 'projects/D'), { name: 'New' }));
  });
  it('engineer admin cannot create a project without a name', () =>
    assertFails(setDoc(doc(asEngAdmin().firestore(), 'projects/N'), { assignedUids: ['eng'], createdByUid: 'eng' })));
  it('nobody but the super admin moves a record to another project, even one of their own', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/Q'), { assignedUids: ['eng'], name: 'Q' }));
    for (const ctx of [asEng, asEngAdmin]) {
      for (const type of ['audit_logs', 'weekly_reports', 'project_payments']) {
        await assertFails(write(ctx(), type, type, { projectUuid: 'Q', assignedUids: ['eng'] }));
      }
    }
  });

  for (const type of adminOnlyDeletes) {
    it(`field engineer edits a ${type} record`, () => assertSucceeds(write(asEng(), type, type, { data: '{"a":2}' })));
    it(`field engineer cannot delete a ${type} record`, () => assertFails(write(asEng(), type, type, { isDeleted: true })));
    it(`field engineer cannot delete a ${type} record the way a phone does (merged)`, () =>
      assertFails(setDoc(doc(asEng().firestore(), `records/${type}`), { uuid: type, type, projectUuid: 'P', revision: 2, isDeleted: true, updatedByUid: 'eng' }, { merge: true })));
    it(`engineer admin deletes a ${type} record`, () => assertSucceeds(write(asEngAdmin(), type, type, { isDeleted: true })));
  }
  it('field engineer deletes a payment', () => assertSucceeds(write(asEng(), 'project_payments', 'project_payments', { isDeleted: true })));
  it('field engineer cannot add a plan', () => assertFails(write(asEng(), 'NEW', 'sdp_plans')));
  it('engineer admin adds a plan', () => assertSucceeds(write(asEngAdmin(), 'NEW', 'sdp_plans')));
  it('field engineer adds a weekly report', () => assertSucceeds(write(asEng(), 'NEW', 'weekly_reports')));
  it('cannot turn a record into another type', () => assertFails(write(asEng(), 'project_payments', 'weekly_reports')));
  it('cannot write a record in someone else\'s name', () => assertFails(write(asEng(), 'project_payments', 'project_payments', {}, 'other')));
  it('cannot write a record in no one\'s name', () =>
    assertFails(setDoc(doc(asEng().firestore(), 'records/NEW'), { uuid: 'NEW', type: 'project_payments', projectUuid: 'P', revision: 1, assignedUids: ['eng', 'viewer'] })));

  for (const [name, ctx] of [['field engineer', asEng], ['engineer admin', asEngAdmin]]) {
    it(`${name} adds an audit entry`, () => assertSucceeds(write(ctx(), 'NEW', 'audit_logs')));
    it(`${name} sends the same audit entry again`, () => assertSucceeds(write(ctx(), 'audit_logs', 'audit_logs')));
    it(`${name} cannot change an audit entry`, () => assertFails(write(ctx(), 'audit_logs', 'audit_logs', { data: '{"a":2}' })));
    it(`${name} cannot delete an audit entry`, () => assertFails(write(ctx(), 'audit_logs', 'audit_logs', { isDeleted: true })));
    it(`${name} cannot add an audit entry already deleted`, () => assertFails(write(ctx(), 'NEW', 'audit_logs', { isDeleted: true })));
    it(`${name} cannot turn an audit entry into another type`, () => assertFails(write(ctx(), 'audit_logs', 'project_payments')));
  }
  it('super admin changes and deletes an audit entry', () =>
    assertSucceeds(write(asAdmin(), 'audit_logs', 'audit_logs', { data: '{"a":2}', isDeleted: true }, 'admin')));
});

describe('assigned engineer admin', () => {
  it('changes the bar chart', () => assertSucceeds(updateDoc(doc(asEngAdmin().firestore(), 'projects/P'), { barChartJson: '[]' })));
  it('cannot change it on a project not assigned to them', () =>
    assertFails(updateDoc(doc(testEnv.authenticatedContext('other', { role: 'ENGINEER_ADMIN' }).firestore(), 'projects/P'), { barChartJson: '[]' })));
});

describe('super admin', () => {
  it('gets project and record', async () => {
    await assertSucceeds(getDoc(doc(asAdmin().firestore(), 'projects/P')));
    await assertSucceeds(getDoc(doc(asAdmin().firestore(), 'records/R')));
  });
  it('changes assignedUids', () =>
    assertSucceeds(updateDoc(doc(asAdmin().firestore(), 'projects/P'), { assignedUids: ['eng', 'x'] })));
  it('writes record with any list', () =>
    assertSucceeds(setDoc(doc(asAdmin().firestore(), 'records/R'), rec(['eng', 'viewer']))));
  it('reads and uploads project files', async () => {
    await assertSucceeds(getBytes(ref(asAdmin().storage(), 'attachments/P/f.jpg')));
    await assertSucceeds(uploadBytes(ref(asAdmin().storage(), 'attachments/P/a.jpg'), bytes(10), jpeg));
  });
  it('reads legacy file (only admin)', () => assertSucceeds(getBytes(ref(asAdmin().storage(), 'attachments/f.jpg'))));
  it('lists all projects', () => assertSucceeds(getDocs(collection(asAdmin().firestore(), 'projects'))));
});

describe('deletion', () => {
  for (const [name, ctx] of [['eng', asEng], ['viewer', asViewer], ['admin', asAdmin]]) {
    it(`${name} cannot delete project`, () => assertFails(deleteDoc(doc(ctx().firestore(), 'projects/P'))));
  }
});

describe('user roles', () => {
  const asOwner = () => testEnv.authenticatedContext('owner', { role: 'SUPER_ADMIN', email: 'great.glenn17@gmail.com', email_verified: true });
  // Someone who registered the Super Admin's address without proving they own it.
  const asUnverifiedOwner = () => testEnv.authenticatedContext('fake', { email: 'great.glenn17@gmail.com', email_verified: false });
  const asNew = () => testEnv.authenticatedContext('new', { email: 'new@x.com' });
  beforeEach(() => testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/eng'), { name: 'E', role: 'FIELD_ENGINEER' });
    await setDoc(doc(db, 'users/admin'), { name: 'A', role: 'SUPER_ADMIN' });
  }));
  it('signs up as field engineer', () => assertSucceeds(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'FIELD_ENGINEER' })));
  it('signs up as viewer', () => assertSucceeds(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'VIEWER' })));
  it('signs up without a role', () => assertSucceeds(setDoc(doc(asNew().firestore(), 'users/new'), { name: 'N' })));
  it('cannot sign up as engineer admin', () => assertFails(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'ENGINEER_ADMIN' })));
  it('cannot sign up as super admin', () => assertFails(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'SUPER_ADMIN' })));
  it('super admin email creates its profile', () => assertSucceeds(setDoc(doc(asOwner().firestore(), 'users/owner'), { role: 'SUPER_ADMIN' })));
  it('unverified super admin email cannot', () => assertFails(setDoc(doc(asUnverifiedOwner().firestore(), 'users/fake'), { role: 'SUPER_ADMIN' })));
  it('nobody writes audit_logs', () => assertFails(setDoc(doc(asOwner().firestore(), 'audit_logs/x'), { action: 'fake' })));
  it('super admin reads audit_logs', () => assertSucceeds(getDocs(collection(asAdmin().firestore(), 'audit_logs'))));
  it('edits own profile without the role', () => assertSucceeds(setDoc(doc(asEng().firestore(), 'users/eng'), { office: 'O' }, { merge: true })));
  it('cannot change own role', () => assertFails(updateDoc(doc(asEng().firestore(), 'users/eng'), { role: 'ENGINEER_ADMIN' })));
  it('cannot change another user', () => assertFails(updateDoc(doc(asOther().firestore(), 'users/eng'), { role: 'VIEWER' })));
  it('super admin promotes a user', () => assertSucceeds(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { role: 'ENGINEER_ADMIN' })));
  it('super admin cannot make another super admin', () => assertFails(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { role: 'SUPER_ADMIN' })));
  it('cannot sign up as super viewer', () => assertFails(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'SUPER_VIEWER' })));
  it('cannot sign up seeing all projects', () =>
    assertFails(setDoc(doc(asNew().firestore(), 'users/new'), { role: 'FIELD_ENGINEER', seesAllProjects: true })));
  it('cannot turn on own see-all switch', () => assertFails(updateDoc(doc(asEng().firestore(), 'users/eng'), { seesAllProjects: true })));
  it('super admin makes a super viewer', () => assertSucceeds(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { role: 'SUPER_VIEWER' })));
  it('super admin turns on see-all', () =>
    assertSucceeds(updateDoc(doc(asAdmin().firestore(), 'users/eng'), { seesAllProjects: true, role: 'FIELD_ENGINEER' })));
  it('super admin lists users', () => assertSucceeds(getDocs(collection(asAdmin().firestore(), 'users'))));
  it('engineer cannot list users', () => assertFails(getDocs(collection(asEng().firestore(), 'users'))));
});

// web/logic.js builds what the website writes; these are the writes a Field Engineer and an Engineer Admin send from it.
describe('the website\'s own writes', () => {
  let L;
  const ids = (uuid) => ({ uuid, projectUuid: 'P', assignedUids: ['eng', 'viewer'], uid: 'eng', now: 5 });
  const report = { uuid: 'W', type: 'weekly_reports', projectUuid: 'P', revision: 1, updatedAt: 1, updatedByUid: 'x', isDeleted: false, assignedUids: ['eng', 'viewer'], data: '{"a":1}' };
  const update = (ctx, path, fields) => updateDoc(doc(ctx.firestore(), path), fields);
  before(async () => { L = await import('../web/logic.js'); });
  beforeEach(() => testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'records/W'), report)));
  it('field engineer adds an audit entry', () =>
    assertSucceeds(setDoc(doc(asEng().firestore(), 'records/A'), L.auditRecord({ ...ids('A'), user: 'E', actionType: 'T', details: 'd' }))));
  it('field engineer edits a report', () => assertSucceeds(update(asEng(), 'records/W', L.editedRecord(report, { a: 2 }, ids('W')))));
  it('field engineer cannot delete a report', () => assertFails(update(asEng(), 'records/W', L.deletedRecord(report, ids('W')))));
  it('engineer admin deletes a report', () => assertSucceeds(update(asEngAdmin(), 'records/W', L.deletedRecord(report, ids('W')))));
  it('engineer admin deletes the project', () =>
    assertSucceeds(update(asEngAdmin(), 'projects/P', L.deletedProject({ projectUuid: 'P' }, { uid: 'eng', now: 5 }))));
});
