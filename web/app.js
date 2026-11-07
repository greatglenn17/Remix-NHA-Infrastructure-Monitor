// Read-only web viewer. It never imports a Firestore or Storage write function, so it cannot
// change what the phones sync.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js';
import {
  getAuth, connectAuthEmulator, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import {
  getFirestore, connectFirestoreEmulator, collection, doc, query, where, onSnapshot,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js';
import { getStorage, connectStorageEmulator, ref, getDownloadURL } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js';
import { firebaseConfig } from './firebase-config.js';
import * as L from './logic.js';

// `?emulator` on localhost talks to `firebase emulators:start --project demo-nha` instead of the live project.
const emulator = new URLSearchParams(location.search).has('emulator') && ['localhost', '127.0.0.1'].includes(location.hostname);
const app = initializeApp(emulator
  ? { apiKey: 'demo', projectId: 'demo-nha', authDomain: 'localhost', storageBucket: 'demo-nha.appspot.com' }
  : firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
if (emulator) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  connectStorageEmulator(storage, '127.0.0.1', 9199);
}

const ROLE_LABELS = {
  SUPER_ADMIN: 'Super Admin', ENGINEER_ADMIN: 'Engineer Admin', FIELD_ENGINEER: 'Field Engineer',
  VIEWER: 'Viewer', SUPER_VIEWER: 'Super Viewer',
};
const SCHEDULE_ORDER = /time extension|work suspension|work resumption/i;

const main = document.getElementById('app');
const who = document.getElementById('who');
const s = { user: null, claims: {}, projects: [], projectsError: null, unsubProjects: null, unsubPage: [] };

/** Escapes text for HTML; every value from Firestore goes through it. */
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const today = () => new Date().toLocaleDateString('en-CA'); // yyyy-MM-dd in the viewer's time zone
const pct = n => `${(Number(n) || 0).toFixed(2)}%`;

/** Live projects of this account. Non-seesAll accounts must filter by assignedUids or the rules reject the query. */
function scoped(name, ...filters) {
  const base = collection(db, name);
  return L.seesAll(s.claims) ? query(base, ...filters) : query(base, ...filters, where('assignedUids', 'array-contains', s.user.uid));
}

onAuthStateChanged(auth, async user => {
  s.unsubProjects?.(); s.unsubProjects = null;
  s.unsubPage.forEach(u => u()); s.unsubPage = [];
  s.user = user;
  if (!user) { who.innerHTML = ''; return renderSignIn(); }
  // Fresh token: the role claim may have changed since this browser last signed in.
  s.claims = (await user.getIdTokenResult(true)).claims;
  who.innerHTML = `<span>${esc(user.email)} · ${esc(ROLE_LABELS[s.claims.role] || 'No role yet')}</span> <button id="out" class="link">Sign out</button>`;
  document.getElementById('out').onclick = () => signOut(auth);
  s.unsubProjects = onSnapshot(scoped('projects'), snap => {
    s.projectsError = null;
    s.projects = snap.docs.map(d => ({ ...d.data(), projectUuid: d.id }))
      .filter(p => p.isDeleted !== true && p.name)
      .sort((a, b) => a.name.localeCompare(b.name));
    if (!location.hash.startsWith('#/p/')) renderDashboard();
  }, e => { s.projectsError = e.message; renderDashboard(); });
  route();
});

window.addEventListener('hashchange', () => s.user && route());

function route() {
  s.unsubPage.forEach(u => u()); s.unsubPage = [];
  const m = /^#\/p\/([\w-]+)$/.exec(location.hash);
  if (m) renderProject(m[1]); else renderDashboard();
}

function renderSignIn(message = '') {
  main.innerHTML = `
    <form id="signin" class="card narrow">
      <h1>Sign in</h1>
      <p class="muted">Use the same account as the NHA Monitor app.${emulator ? ' <b>Local emulator.</b>' : ''}</p>
      <label>Email <input name="email" type="email" autocomplete="username" required></label>
      <label>Password <input name="password" type="password" autocomplete="current-password" required></label>
      <p class="error" role="alert">${esc(message)}</p>
      <button type="submit">Sign in</button>
      <button type="button" id="reset" class="link">Forgot password?</button>
    </form>`;
  const form = document.getElementById('signin');
  form.onsubmit = async e => {
    e.preventDefault();
    try {
      await signInWithEmailAndPassword(auth, form.email.value.trim(), form.password.value);
    } catch (err) {
      renderSignIn(err.code === 'auth/invalid-credential' ? 'Wrong email or password.' : err.message);
    }
  };
  document.getElementById('reset').onclick = async () => {
    const email = form.email.value.trim();
    if (!email) return renderSignIn('Type your email first, then tap Forgot password.');
    try { await sendPasswordResetEmail(auth, email); renderSignIn('A password reset link was sent to your email.'); } catch (err) { renderSignIn(err.message); }
  };
}

function renderDashboard() {
  if (!s.user || location.hash.startsWith('#/p/')) return;
  const filter = (document.getElementById('q')?.value || '').toLowerCase();
  const list = s.projects.filter(p => [p.name, p.location, p.contractor].join(' ').toLowerCase().includes(filter));
  const done = s.projects.filter(p => (Number(p.actualAccomplishment) || 0) >= 100).length;
  const behind = s.projects.filter(p => (Number(p.actualAccomplishment) || 0) < 100 && p.actualAccomplishment < p.targetAccomplishment).length;
  const viewOnlyTag = p => L.seesAll(s.claims) && s.claims.role !== 'SUPER_ADMIN' && !(p.assignedUids || []).includes(s.user.uid);
  main.innerHTML = `
    <section class="counts">
      <div class="count"><b>${s.projects.length}</b><span>Projects</span></div>
      <div class="count"><b>${s.projects.length - done}</b><span>Ongoing</span></div>
      <div class="count behind"><b>${behind}</b><span>Behind schedule</span></div>
      <div class="count ok"><b>${done}</b><span>Completed</span></div>
    </section>
    <input id="q" type="search" placeholder="Search name, location or contractor" value="${esc(filter)}" aria-label="Search projects">
    ${s.projectsError ? `<p class="error">Could not load projects: ${esc(s.projectsError)}</p>` : ''}
    ${list.length === 0 ? `<p class="muted">${s.projects.length ? 'No project matches.' : 'No projects for this account yet.'}</p>` : ''}
    <ul class="projects">${list.map(p => `
      <li><a class="card row" href="#/p/${esc(p.projectUuid)}">
        <div class="grow">
          <h2>${esc(p.name)}</h2>
          <p class="muted">${esc(p.location || '-')} · ${esc(p.contractor || '-')}</p>
          <div class="bar-track" role="img" aria-label="${pct(p.actualAccomplishment)} done"><div class="bar-fill ${slipClass(p)}" style="width:${Math.min(100, Number(p.actualAccomplishment) || 0)}%"></div></div>
          <p><b>${pct(p.actualAccomplishment)}</b> · ${esc(L.scheduleText(p))}</p>
        </div>
        <div class="side">
          <span class="pill">${esc(p.status || '-')}</span>
          ${viewOnlyTag(p) ? '<span class="pill">View only</span>' : ''}
          <span class="money">${esc(L.formatPhp(L.originalCost(p)))}</span>
        </div>
      </a></li>`).join('')}
    </ul>`;
  const q = document.getElementById('q');
  q.oninput = () => { renderDashboard(); const n = document.getElementById('q'); n.focus(); n.setSelectionRange(n.value.length, n.value.length); };
}

function slipClass(p) {
  if ((Number(p.actualAccomplishment) || 0) >= 100) return 'ok';
  return p.actualAccomplishment < p.targetAccomplishment ? 'behind' : 'ok';
}

function renderProject(projectUuid) {
  const page = { project: undefined, records: [], error: null };
  const draw = () => drawProject(page);
  main.innerHTML = '<p class="muted">Loading project…</p>';
  s.unsubPage.push(onSnapshot(doc(db, 'projects', projectUuid), d => {
    page.project = d.exists() && d.data().isDeleted !== true ? { ...d.data(), projectUuid: d.id } : null;
    draw();
  }, e => { page.error = e.code === 'permission-denied' ? 'This account cannot open this project.' : e.message; draw(); }));
  s.unsubPage.push(onSnapshot(scoped('records', where('projectUuid', '==', projectUuid)), snap => {
    page.records = snap.docs.map(d => L.parseRecord({ ...d.data(), uuid: d.id })).filter(r => !r.isDeleted);
    draw();
  }, e => { page.error = e.message; draw(); }));
}

function drawProject({ project, records, error }) {
  if (error) { main.innerHTML = `<a href="#/">← All projects</a><p class="error">${esc(error)}</p>`; return; }
  if (project === undefined) return;
  if (project === null) { main.innerHTML = '<a href="#/">← All projects</a><p class="muted">This project was deleted or does not exist.</p>'; return; }
  const of = type => records.filter(r => r.type === type);
  const documents = of('pending_documents');
  // Room lists billings by local id (creation order); the cloud has no id, so date then last edit.
  const payments = of('project_payments').sort((a, b) => String(a.date).localeCompare(String(b.date)) || a.updatedAt - b.updatedAt);
  const latest = of('weekly_reports').sort((a, b) => b.createdAtTimestamp - a.createdAtTimestamp)[0];
  const openIssues = of('project_issues').filter(L.isOpenIssue).length;
  const orders = documents.filter(d => SCHEDULE_ORDER.test(d.documentName || ''));
  const cost = L.currentCost(project, documents);
  const housing = /housing/i.test(project.projectType || '');
  const field = (k, v) => `<div class="field"><span>${esc(k)}</span><b>${esc(v === '' || v == null ? '-' : v)}</b></div>`;
  const grossTotal = payments.reduce((t, p) => t + (Number(p.grossAmount) || 0), 0);

  main.innerHTML = `
    <a href="#/">← All projects</a>
    <header class="project-head">
      <h1>${esc(project.name)}</h1>
      <p class="muted">${esc(project.location || '-')} · <span class="pill">${esc(project.status || '-')}</span></p>
      <p><b>${pct(project.actualAccomplishment)}</b> done against ${pct(project.targetAccomplishment)} target · ${esc(L.scheduleText(project))}</p>
    </header>
    <div class="grid">
      <section class="card">
        <h2>Project profile</h2>
        <img id="site-photo" alt="Latest site photo" hidden>
        ${field('Implementing office', project.implementingOffice)}
        ${field('Contractor', project.contractor)}
        ${field('Project type', project.projectType)}
        ${housing ? field('Land area', project.landArea) + field('No. of units', project.numberOfUnits) : ''}
        ${L.scopeItems(project.scopeOfWork).map((it, i) => `
          <div class="scope"><b>${i + 1}. ${esc(it.name)}</b><span class="muted">${esc(L.formatPhp(it.amount))} · ${it.weightPct.toFixed(2)}%</span></div>`).join('')}
      </section>
      <section class="card">
        <h2>Key dates</h2>
        ${field('Date started', L.displayDate(project.dateStarted))}
        ${field('Original completion', L.displayDate(project.completionDateOriginal))}
        ${field('Revised completion', L.displayDate((project.completionDateRevised || '').trim() === '-' ? '' : project.completionDateRevised))}
        ${field('Contract duration', `${project.contractDurationDays ?? 0} days`)}
        ${field('Days left', L.daysLeftText(project, today()))}
        <h3>Time extensions &amp; suspensions</h3>
        ${orders.length ? orders.map(d => `<p>${esc(d.documentName)} <span class="muted">· ${esc(d.status)}${d.remarks ? ` · ${esc(d.remarks)}` : ''}</span></p>`).join('') : '<p class="muted">No time extension or suspension orders.</p>'}
      </section>
      <section class="card">
        <h2>Contract cost</h2>
        ${field('Original', L.formatPhp(L.originalCost(project)))}
        ${field('Current', L.formatPhp(cost))}
      </section>
      <section class="card">
        <h2>Latest weekly report</h2>
        ${latest ? `<p><b>${esc(latest.reportingWeek)}</b></p>
          ${field('Target', pct(latest.targetAccomplishmentPct))}
          ${field('Actual', pct(latest.actualAccomplishmentPct))}
          ${field('Submitted by', latest.submittedByStaff)}` : '<p class="muted">No weekly reports yet</p>'}
        ${field('Open issues', String(openIssues))}
      </section>
      <section class="card wide">
        <h2>Status of payment</h2>
        ${payments.length === 0 ? '<p class="muted">No billings yet</p>' : `
        <div class="scroll"><table>
          <thead><tr><th>Billing / period covered</th><th>Date</th><th class="num">Net amount (PHP)</th><th class="num">%</th><th class="num">Balance (PHP)</th></tr></thead>
          <tbody>${payments.map(p => `<tr>
            <td>${esc(p.name || '-')}${p.periodCovered ? `<br><span class="muted">${esc(p.periodCovered)}</span>` : ''}</td>
            <td>${esc(L.displayDate(p.date))}</td>
            <td class="num">${L.formatDecimal(L.netAmount(p))}</td>
            <td class="num">${pct(p.percentage)}</td>
            <td class="num">${L.formatDecimal(Number(p.balanceAmount) || 0)}</td></tr>`).join('')}
          </tbody>
          <tfoot><tr><th>Total</th><th></th>
            <th class="num">${L.formatDecimal(payments.reduce((t, p) => t + L.netAmount(p), 0))}</th>
            <th class="num">${cost > 0 ? pct(grossTotal / cost * 100) : '-'}</th>
            <th class="num">${L.formatDecimal(cost - grossTotal)}</th></tr></tfoot>
        </table></div>`}
      </section>
    </div>`;
  showPhoto(project.latestUpdatePhotoUrl);
}

/** Loads the site photo from Storage; a missing file or no read access just leaves it hidden. */
async function showPhoto(refText) {
  const path = L.storagePathOf(refText);
  if (!path) return;
  try {
    const url = await getDownloadURL(ref(storage, path));
    const img = document.getElementById('site-photo');
    if (img) { img.src = url; img.hidden = false; }
  } catch { /* not uploaded yet, or a legacy file only the Super Admin can read */ }
}
