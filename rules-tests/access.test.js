const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, getDocs,
} = require('firebase/firestore');
const { ref, getBytes, uploadBytes, deleteObject } = require('firebase/storage');

const root = path.join(__dirname, '..');
let testEnv;

const bytes = (n) => new Uint8Array(n);
const jpeg = { contentType: 'image/jpeg' };

const asEng = () => testEnv.authenticatedContext('eng', { role: 'FIELD_ENGINEER' });
const asOther = () => testEnv.authenticatedContext('other', { role: 'FIELD_ENGINEER' });
const asViewer = () => testEnv.authenticatedContext('viewer', { role: 'VIEWER' });
const asAdmin = () => testEnv.authenticatedContext('admin', { role: 'SUPER_ADMIN' });
const asNoRole = () => testEnv.authenticatedContext('eng');

const rec = (uids) => ({ uuid: 'R', projectUuid: 'P', revision: 2, assignedUids: uids });

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
  it('cannot write record', () => assertFails(setDoc(doc(asOther().firestore(), 'records/R'), rec(['eng', 'viewer']))));
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
      assertSucceeds(setDoc(doc(asOther().firestore(), 'records/RQ'), { uuid: 'RQ', projectUuid: 'Q', revision: 1, assignedUids: ['other'] })));
    it('cannot take over a stored record of P by moving it into Q', () =>
      assertFails(setDoc(doc(asOther().firestore(), 'records/R'), { uuid: 'R', projectUuid: 'Q', revision: 2, assignedUids: ['other'] })));
  });
  it('cannot write record for a missing project', () =>
    assertFails(setDoc(doc(asOther().firestore(), 'records/R2'), { uuid: 'R2', projectUuid: 'NOPE', revision: 1, assignedUids: ['other'] })));
  it('cannot read file of a missing project', () => assertFails(getBytes(ref(asOther().storage(), 'attachments/NOPE/f.jpg'))));
});

describe('assigned field engineer', () => {
  it('gets project', () => assertSucceeds(getDoc(doc(asEng().firestore(), 'projects/P'))));
  it('updates project name', () => assertSucceeds(updateDoc(doc(asEng().firestore(), 'projects/P'), { name: 'Y' })));
  it('cannot change assignedUids', () =>
    assertFails(updateDoc(doc(asEng().firestore(), 'projects/P'), { assignedUids: ['eng', 'x'] })));
  it('gets record', () => assertSucceeds(getDoc(doc(asEng().firestore(), 'records/R'))));
  it('writes record with matching assignedUids', () =>
    assertSucceeds(setDoc(doc(asEng().firestore(), 'records/R'), rec(['eng', 'viewer']))));
  it('cannot write record with extra uid', () =>
    assertFails(setDoc(doc(asEng().firestore(), 'records/R'), rec(['eng', 'x']))));
  it('can create a new record in the project', () =>
    assertSucceeds(setDoc(doc(asEng().firestore(), 'records/R3'), { uuid: 'R3', projectUuid: 'P', revision: 1, assignedUids: ['eng', 'viewer'] })));
  it('cannot move a record into a project they are not assigned to', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'projects/Q'), { assignedUids: ['other'] }));
    return assertFails(setDoc(doc(asEng().firestore(), 'records/R'), { uuid: 'R', projectUuid: 'Q', revision: 2, assignedUids: ['other'] }));
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
  it('account without a role claim is treated as a writer', async () => {
    await assertSucceeds(getDoc(doc(asNoRole().firestore(), 'projects/P')));
    await assertSucceeds(updateDoc(doc(asNoRole().firestore(), 'projects/P'), { name: 'Y' }));
  });
});

describe('creating projects', () => {
  const create = (ctx, uids) => setDoc(doc(ctx.firestore(), 'projects/N'), { name: 'N', assignedUids: uids });
  it('creator with only own uid', () => assertSucceeds(create(asEng(), ['eng'])));
  it('creator with extra uid denied', () => assertFails(create(asEng(), ['eng', 'x'])));
  it('creator with someone else only denied', () => assertFails(create(asEng(), ['x'])));
  it('creator with no list denied', () => assertFails(setDoc(doc(asEng().firestore(), 'projects/N'), { name: 'N' })));
  it('viewer cannot create', () => assertFails(create(asViewer(), ['viewer'])));
  it('admin can create with any list', () => assertSucceeds(create(asAdmin(), ['eng', 'x'])));
});

describe('viewer (assigned)', () => {
  it('gets project', () => assertSucceeds(getDoc(doc(asViewer().firestore(), 'projects/P'))));
  it('gets record', () => assertSucceeds(getDoc(doc(asViewer().firestore(), 'records/R'))));
  it('reads file', () => assertSucceeds(getBytes(ref(asViewer().storage(), 'attachments/P/f.jpg'))));
  it('cannot update project', () => assertFails(updateDoc(doc(asViewer().firestore(), 'projects/P'), { name: 'Y' })));
  it('cannot write record', () => assertFails(setDoc(doc(asViewer().firestore(), 'records/R'), rec(['eng', 'viewer']))));
  it('cannot upload file', () => assertFails(uploadBytes(ref(asViewer().storage(), 'attachments/P/v.jpg'), bytes(10), jpeg)));
  it('cannot delete file', () => assertFails(deleteObject(ref(asViewer().storage(), 'attachments/P/f.jpg'))));
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
