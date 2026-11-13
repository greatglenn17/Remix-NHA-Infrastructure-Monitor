// Run: node --test web/drive.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { driveName, driveFiles, copyToDrive } from './drive.js';

const id = n => `${String(n).padStart(8, '0')}-0000-4000-8000-000000000000`;
const PROJECT = id(99);
const jpg = n => `nhafile:${PROJECT}/${id(n)}.jpg`, pdf = n => `nhafile:${PROJECT}/${id(n)}.pdf`;

const records = [
  { uuid: id(50), type: 'weekly_reports', reportingWeek: 'Jan 5 - Jan 11, 2026',
    // The details list the second photo first; the report's own order numbers them.
    photoDetailsJson: JSON.stringify({ [jpg(2)]: { title: 'Roof framing' }, [jpg(1)]: { title: 'Block 3: slab' } }),
    attachedPhotoUrlsJson: JSON.stringify([jpg(1), jpg(2), 'content://not-uploaded-yet']) },
  { uuid: id(51), type: 'project_payments', name: '1st billing', dvNo: '2026-01-0042', fileUrl: pdf(3) },
  { uuid: id(52), type: 'project_payments', name: '1st billing', dvNo: '2026-01-0042', fileUrl: pdf(4) },
  { uuid: id(53), type: 'contractor_letters', subject: 'Notice of slippage', receivedFileUrl: pdf(5), replyFileUrl: pdf(6) },
  { uuid: id(54), type: 'sdp_plans', siteName: 'Site A', planName: 'Approved plan', version: 2, pdfFileUrl: pdf(7) },
  { uuid: id(58), type: 'sdp_plans', siteName: 'Phase 2', planName: 'Phase 2 SDP', version: 1, pdfFileUrl: pdf(10) },
  { uuid: id(55), type: 'pending_documents', documentName: 'Variation Order No. 1', fileUrl: pdf(3) },
  { uuid: id(56), type: 'audit_logs', details: `removed ${pdf(8)}` },
  { uuid: id(57), type: 'pending_documents', documentName: 'Old file', fileUrl: pdf(9), isDeleted: true },
];

test('a name Drive and Windows both take', () => {
  assert.equal(driveName('  Block 3: slab / "east"  ', 'x'), 'Block 3 slab east');
  assert.equal(driveName('???', 'Project'), 'Project');
  assert.equal(driveName(null, 'Project'), 'Project');
  assert.equal(driveName('a'.repeat(200), 'x').length, 120);
});

test('each file goes to its folder under a readable name, once', () => {
  const files = driveFiles(records);
  const week = [['weekly', 'Weekly Reports'], [`weekly/${id(50)}`, '2026-01-05 to 2026-01-11']];
  assert.deepEqual(files, [
    { ref: jpg(1), name: '01 Block 3 slab.jpg', folders: week },
    { ref: jpg(2), name: '02 Roof framing.jpg', folders: week },
    { ref: pdf(3), name: '1st billing (DV 2026-01-0042).pdf', folders: [['billings', 'Billings']] },
    { ref: pdf(4), name: '1st billing (DV 2026-01-0042) (2).pdf', folders: [['billings', 'Billings']] },
    { ref: pdf(5), name: 'Notice of slippage (received copy).pdf', folders: [['letters', 'Letters to Contractor']] },
    { ref: pdf(6), name: 'Notice of slippage (reply).pdf', folders: [['letters', 'Letters to Contractor']] },
    { ref: pdf(7), name: 'Site A Approved plan v2.pdf', folders: [['plans', 'Subdivision Plan']] },
    { ref: pdf(10), name: 'Phase 2 SDP v1.pdf', folders: [['plans', 'Subdivision Plan']] },
  ]);
});

/** A pretend Drive: keeps what is made, answers the calls copyToDrive sends, and counts them. */
function fakeDrive() {
  const items = [];
  let n = 0;
  const reply = (body, headers = {}) => ({ ok: true, status: 200, json: async () => body, headers: { get: k => headers[k] } });
  const api = async (url, init = {}) => {
    assert.equal(init.headers.Authorization, 'Bearer token');
    const u = new URL(url);
    if (u.pathname === '/upload-session') { items.push({ ...JSON.parse(u.searchParams.get('meta')), id: `f${++n}`, bytes: init.body }); return reply({}); }
    if (u.pathname.startsWith('/upload/')) return reply({}, { Location: `https://www.googleapis.com/upload-session?meta=${encodeURIComponent(init.body)}` });
    if (init.method === 'POST') { const made = { ...JSON.parse(init.body), id: `f${++n}` }; items.push(made); return reply({ id: made.id }); }
    const [, key, value] = /key='(\w+)' and value='([^']+)'/.exec(u.searchParams.get('q'));
    return reply({ files: items.filter(f => !f.trashed && f.appProperties?.[key] === value) });
  };
  return { items, api };
}

test('copies into named folders, then only what is new', async () => {
  const drive = fakeDrive();
  const run = (files, bytes = async ref => `bytes of ${ref}`) =>
    copyToDrive({ token: 'token', projectId: PROJECT, projectName: 'Pandi Heights: Phase 2', files, bytes, api: drive.api });
  const files = driveFiles(records);

  const first = await run(files);
  assert.deepEqual([first.copied, first.skipped, first.failed], [8, 0, []]);
  const named = name => drive.items.find(f => f.name === name);
  const path = f => (f.parents ? [...path(drive.items.find(p => p.id === f.parents[0])), f.name] : [f.name]);
  assert.deepEqual(path(named('01 Block 3 slab.jpg')), ['NHA Monitor', 'Pandi Heights Phase 2', 'Weekly Reports', '2026-01-05 to 2026-01-11', '01 Block 3 slab.jpg']);
  assert.deepEqual(path(named('Site A Approved plan v2.pdf')), ['NHA Monitor', 'Pandi Heights Phase 2', 'Subdivision Plan', 'Site A Approved plan v2.pdf']);
  assert.equal(named('01 Block 3 slab.jpg').bytes, `bytes of ${jpg(1)}`);
  assert.equal(first.folderUrl, `https://drive.google.com/drive/folders/${named('Pandi Heights Phase 2').id}`);
  assert.equal(drive.items.length, 15); // 8 files, and 7 folders: the root, the project, four kinds and one week

  // Again: nothing new, even after a file was renamed in Drive.
  named('02 Roof framing.jpg').name = 'renamed by hand.jpg';
  const again = await run(files);
  assert.deepEqual([again.copied, again.skipped], [0, 8]);
  assert.equal(drive.items.length, 15);

  // A new billing file and a file put in the bin are copied; one that cannot be read is reported and the rest go on.
  named('1st billing (DV 2026-01-0042).pdf').trashed = true;
  const more = [...files, { ref: pdf(20), name: 'New.pdf', folders: [['billings', 'Billings']] }, { ref: pdf(21), name: 'Gone.pdf', folders: [['billings', 'Billings']] }];
  const third = await run(more, async ref => { if (ref === pdf(21)) throw new Error('not found'); return 'x'; });
  assert.deepEqual([third.copied, third.skipped, third.failed], [2, 7, ['Billings / Gone.pdf']]);
  assert.equal(drive.items.filter(f => f.name === 'Billings').length, 1);
});

test('an answer Drive refuses stops the run with its status', async () => {
  const api = async () => ({ ok: false, status: 401 });
  await assert.rejects(copyToDrive({ token: 'token', projectId: PROJECT, projectName: 'P', files: [], bytes: async () => '', api }), { status: 401 });
});
