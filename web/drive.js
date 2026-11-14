// Copy to Google Drive (Super Admin): a one-way copy of a project's files into named folders in the signed-in Google
// account's Drive. Firebase stays the working store; nothing in Drive is read back, renamed or deleted.
import * as L from './logic.js';

/** Access to the files this site makes in Drive, and to nothing else there. */
export const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.file';
const FOLDER = 'application/vnd.google-apps.folder';
const API = 'https://www.googleapis.com/drive/v3/files', UPLOAD = 'https://www.googleapis.com/upload/drive/v3/files';

/** A name Drive and Windows both take: none of \ / : * ? " < > | or control characters, at most 120 characters. */
export const driveName = (text, fallback) =>
  String(text ?? '').replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120).trim() || fallback;

// Each record type's folders under the project, as [tag, name] from the top down. The tag finds the folder again.
const FOLDERS = {
  weekly_reports: r => {
    const days = L.weekDays(r.reportingWeek || '');
    return [['weekly', 'Weekly Reports'], [`weekly/${r.uuid}`, days.length ? `${days[0]} to ${days[6]}` : driveName(r.reportingWeek, 'Week')]];
  },
  project_payments: () => [['billings', 'Billings']],
  pending_documents: () => [['documents', 'Documents']],
  developer_requests: () => [['requests', 'Developer Requests']],
  contractor_letters: () => [['letters', 'Letters to Contractor']],
  sdp_plans: () => [['plans', 'Subdivision Plan']],
};

function label(r, ref, n) {
  switch (r.type) {
    case 'weekly_reports': return `${String(n).padStart(2, '0')} ${L.photoDetails(r.photoDetailsJson)[ref]?.title || 'Photo'}`;
    case 'project_payments': return `${r.name || 'Billing'}${r.dvNo ? ` (DV ${r.dvNo})` : ''}`;
    case 'pending_documents': return r.documentName || 'Document';
    case 'developer_requests': return `${r.requestType || 'Request'} ${r.dateReceived || r.dateRequested || ''}`;
    case 'contractor_letters': return `${r.subject || 'Letter'}${ref === r.replyFileUrl ? ' (reply)' : ref === r.receivedFileUrl ? ' (received copy)' : ''}`;
    // The site's name first, unless the plan's own name already has it ("Phase 2 SDP").
    case 'sdp_plans': return `${r.siteName && !String(r.planName).includes(r.siteName) ? `${r.siteName} ` : ''}${r.planName || 'Plan'} v${r.version ?? 1}`;
    default: return String(r.type || 'file').replace(/_/g, ' ');
  }
}

/**
 * Where each file of a project goes in Drive: [{ ref, name, folders: [[tag, name], ...] }], one entry per file, from
 * the project's records (deleted ones and the audit log left out). A file is filed under the first record that holds
 * it; a second file with the same name in a folder gets " (2)".
 */
export function driveFiles(records) {
  const out = [], seen = new Set(), taken = new Set();
  for (const r of records) {
    if (r.isDeleted || r.type === 'audit_logs') continue;
    const folders = (FOLDERS[r.type] || (() => [['other', 'Other files']]))(r);
    const here = folders[folders.length - 1][0];
    // A weekly report's photos in the order the report shows them, so their numbers match it.
    const first = r.type === 'weekly_reports' ? L.attachmentList(r.attachedPhotoUrlsJson).filter(L.storagePathOf) : [];
    [...new Set([...first, ...L.fileRefsIn(r)])].forEach((ref, i) => {
      if (seen.has(ref)) return;
      seen.add(ref);
      const base = driveName(label(r, ref, i + 1), 'File'), ext = ref.slice(ref.lastIndexOf('.'));
      let name = base;
      for (let n = 2; taken.has(`${here}/${name}`.toLowerCase()); n++) name = `${base} (${n})`;
      taken.add(`${here}/${name}`.toLowerCase());
      out.push({ ref, name: name + ext, folders });
    });
  }
  return out;
}

/** The file's own id, the tag that marks its copy in Drive: "<uuid>.jpg". */
const fileTag = ref => ref.replace(/^nhafile:(?:[^/]*\/)?/, '');

/**
 * Copies `files` (driveFiles) into Drive as NHA Monitor / <project name> / <folders>. Everything it makes carries a
 * private tag (appProperties), so a later run finds its folders again and skips the files already copied, wherever they
 * were moved and whatever they were renamed to; a folder put in the bin is made again. `bytes(ref)` gives a file's
 * content, `api` is fetch. A file that cannot be read is listed in `failed` and the rest go on; an answer Drive refuses
 * (sign-in expired, no space left) stops the run with an error that has its `status`.
 * Returns { copied, skipped, failed: ["folder / name"], folderUrl }.
 */
export async function copyToDrive({ token, projectId, projectName, files, bytes, onProgress = () => {}, api = fetch }) {
  const call = async (url, init = {}) => {
    const res = await api(url, { ...init, headers: { Authorization: `Bearer ${token}`, ...init.headers } });
    if (!res.ok) throw Object.assign(new Error(`Google Drive answered ${res.status}`), { status: res.status });
    return res;
  };
  const list = async q => {
    const found = [];
    let pageToken = '';
    do {
      const query = new URLSearchParams({ q: `${q} and trashed = false`, fields: 'nextPageToken, files(id, mimeType, appProperties)', pageSize: '1000', ...(pageToken && { pageToken }) });
      const page = await (await call(`${API}?${query}`)).json();
      found.push(...(page.files || []));
      pageToken = page.nextPageToken || '';
    } while (pageToken);
    return found;
  };
  const tagged = (key, value) => `appProperties has { key='${key}' and value='${value}' }`;
  const makeFolder = async (name, parent, appProperties) => (await call(`${API}?fields=id`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, mimeType: FOLDER, appProperties, ...(parent && { parents: [parent] }) }),
  })).json();

  const root = (await list(tagged('nhaRoot', '1')))[0] || await makeFolder('NHA Monitor', null, { nhaRoot: '1' });
  const mine = await list(tagged('nhaProject', projectId));
  const folders = new Map(mine.filter(f => f.mimeType === FOLDER).map(f => [f.appProperties.nhaFolder, f.id]));
  const there = new Set(mine.filter(f => f.mimeType !== FOLDER).map(f => f.appProperties.nhaFile));
  const folder = async (tag, name, parent) => {
    if (!folders.has(tag)) folders.set(tag, (await makeFolder(name, parent, { nhaProject: projectId, nhaFolder: tag })).id);
    return folders.get(tag);
  };
  const home = await folder('/', driveName(projectName, 'Project'), root.id);

  const todo = files.filter(f => !there.has(fileTag(f.ref)));
  const failed = [];
  let copied = 0;
  for (const [i, f] of todo.entries()) {
    onProgress(i + 1, todo.length, f.name);
    let data;
    try { data = await bytes(f.ref); } catch (e) { console.warn('Copy to Drive: could not read', f.ref, e); failed.push(`${f.folders.at(-1)[1]} / ${f.name}`); continue; }
    let parent = home;
    for (const [tag, name] of f.folders) parent = await folder(tag, name, parent);
    const type = L.isPdf(f.ref) ? 'application/pdf' : 'image/jpeg';
    // Two steps (a resumable upload), so a large plan PDF goes up as surely as a photo.
    const start = await call(`${UPLOAD}?uploadType=resumable`, {
      method: 'POST', headers: { 'Content-Type': 'application/json; charset=UTF-8', 'X-Upload-Content-Type': type },
      body: JSON.stringify({ name: f.name, parents: [parent], appProperties: { nhaProject: projectId, nhaFile: fileTag(f.ref) } }),
    });
    await call(start.headers.get('Location'), { method: 'PUT', headers: { 'Content-Type': type }, body: data });
    copied++;
  }
  return { copied, skipped: files.length - todo.length, failed, folderUrl: `https://drive.google.com/drive/folders/${home}` };
}
