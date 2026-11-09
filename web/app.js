// Read-only web viewer. It never imports a Firestore or Storage write function, so it cannot
// change what the phones sync. Screens mirror the Android app (ui/dashboard, ui/detail).
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js';
import {
  initializeAuth, indexedDBLocalPersistence, browserLocalPersistence, connectAuthEmulator, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import {
  getFirestore, connectFirestoreEmulator, collection, doc, getDoc, query, where, onSnapshot,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js';
import { getStorage, connectStorageEmulator, ref, getDownloadURL, getBytes } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js';
import { firebaseConfig } from './firebase-config.js';
import * as L from './logic.js';
import * as P from './print.js';

// `?emulator` on localhost talks to `firebase emulators:start --project demo-nha` instead of the live project.
const emulator = new URLSearchParams(location.search).has('emulator') && ['localhost', '127.0.0.1'].includes(location.hostname);
const app = initializeApp(emulator
  ? { apiKey: 'demo', projectId: 'demo-nha', authDomain: 'localhost', storageBucket: 'demo-nha.appspot.com' }
  : firebaseConfig);
// getAuth() without its popup/redirect helper: email and password only, so no Google sign-in iframe or
// script loads and the Content-Security-Policy in firebase.json can block frames and other script hosts.
const auth = initializeAuth(app, { persistence: [indexedDBLocalPersistence, browserLocalPersistence] });
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
// PortfolioCounts: the banner's one row of filters, each a count of projects. Red for the critical two, quiet for the rest.
const COUNTS = [
  { kind: 'overdue', label: 'Overdue', icon: 'warning', tone: 'behind' },
  { kind: 'behind', label: 'Behind schedule', icon: 'trending_down', tone: 'behind' },
  { kind: 'billingAhead', label: 'Billing ahead of work', icon: 'payments', tone: 'watch' },
  { kind: 'waiting', label: 'Waiting approval', icon: 'hourglass_top', tone: 'watch' },
  { kind: 'dueSoon', label: 'Due in 30 days', icon: 'event', tone: 'watch' },
];
// PortfolioCounts.TERM_HELP, from the same thresholds as ProjectAttention (the phone's sync chip terms are left out).
const TERM_HELP = [
  ['Overdue', 'Unfinished projects past their completion date (the revised one, if any).'],
  ['Behind schedule', 'Work done is 10% or more behind the plan, or the project is marked Behind Schedule. A smaller gap reads "slipping" on the card.'],
  ['Billing ahead of work', 'Billed is more than 10% ahead of work done, for example billed 65%, work done 38.50%.'],
  ['Waiting approval', 'Projects with a variation order, time extension, suspension or resumption not yet approved.'],
  ['Due in 30 days', 'Unfinished projects whose completion date (the revised one, if any) is 30 days away or less.'],
  ['Bar colours', 'Work done is blue on plan or slipping, amber from 10% behind, red from 15% behind or once overdue, green when finished. Billed is violet.'],
];

const main = document.getElementById('app');
const who = document.getElementById('who');
const s = {
  user: null, claims: {}, profile: {}, projects: null, projectsError: null,
  payments: new Map(), docs: new Map(), unsub: [], unsubPage: [], page: null,
  filter: null, mineOnly: false, q: '',
};

/** Escapes text for HTML; every value from Firestore goes through it. */
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, cls = '') => `<span class="icon ${cls}" aria-hidden="true">${name}</span>`;
const today = () => new Date().toLocaleDateString('en-CA'); // yyyy-MM-dd in the viewer's time zone
const pct = n => `${(Number(n) || 0).toFixed(2)}%`;
const pesoWhole = n => new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', maximumFractionDigits: 0 }).format(n);
const clamp = f => Math.max(0, Math.min(1, f || 0));
const MAC = /Mac|iPhone|iPad/.test(navigator.platform);

// Cmd+K (Ctrl+K elsewhere) jumps to the dashboard search.
document.addEventListener('keydown', e => {
  const q = document.getElementById('q');
  if (q && (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); q.focus(); q.select(); }
});

/** Every bar on a freshly opened screen fills in once; later redraws (typing in search) stay still. */
function freshScreen() {
  main.dataset.fresh = '';
  clearTimeout(freshScreen.t);
  freshScreen.t = setTimeout(() => delete main.dataset.fresh, 900);
}

/** A Firestore query this account may run: non-seesAll accounts must filter by assignedUids or the rules reject it. */
function scoped(name, ...filters) {
  const base = collection(db, name);
  return L.seesAll(s.claims) ? query(base, ...filters) : query(base, ...filters, where('assignedUids', 'array-contains', s.user.uid));
}

/** Live records of one type grouped by project, for the dashboard. */
function listenRecords(type, into) {
  return onSnapshot(scoped('records', where('type', '==', type)), snap => {
    into.clear();
    for (const d of snap.docs) {
      const r = L.parseRecord({ ...d.data(), uuid: d.id });
      if (r.isDeleted) continue;
      const pid = d.data().projectUuid;
      if (!into.has(pid)) into.set(pid, []);
      into.get(pid).push(r);
    }
    redrawDashboard();
  }, e => console.warn(`records ${type}:`, e.message));
}

onAuthStateChanged(auth, async user => {
  s.unsub.forEach(u => u()); s.unsub = [];
  s.unsubPage.forEach(u => u()); s.unsubPage = [];
  s.user = user; s.projects = null; s.payments.clear(); s.docs.clear(); s.page = null; s.profile = {};
  document.body.classList.toggle('signed-out', !user);
  if (!user) { who.innerHTML = ''; return renderSignIn(); }
  // Fresh token: the role claim may have changed since this browser last signed in.
  s.claims = (await user.getIdTokenResult(true)).claims;
  // Name and position for the weekly form's "Prepared by" when the project has none saved (signatoriesFor).
  getDoc(doc(db, 'users', user.uid)).then(d => { s.profile = { name: d.get('displayName') || '', title: d.get('position') || '' }; }).catch(() => {});
  who.innerHTML = `
    <span class="email">${esc(user.email)}</span>
    <span class="badge view">${icon('visibility', 'sm')}${esc(ROLE_LABELS[s.claims.role] || 'No role yet')} · read only</span>
    <button id="out" class="text-btn">${icon('logout', 'sm')}Sign out</button>`;
  document.getElementById('out').onclick = () => signOut(auth);
  s.unsub.push(onSnapshot(scoped('projects'), snap => {
    s.projectsError = null;
    s.projects = snap.docs.map(d => ({ ...d.data(), projectUuid: d.id }))
      .filter(p => p.isDeleted !== true && p.name);
    redrawDashboard();
  }, e => { s.projectsError = e.message; redrawDashboard(); }));
  s.unsub.push(listenRecords('project_payments', s.payments), listenRecords('pending_documents', s.docs));
  route();
});

window.addEventListener('hashchange', () => s.user && route());

// #/p/<project>[/progress|/payments|/documents|/print/<weekly report>]
const PAGE = /^#\/p\/([\w-]+)(?:\/(progress|payments|documents|print)(?:\/([\w-]+))?)?$/;

function route() {
  const m = PAGE.exec(location.hash);
  window.scrollTo(0, 0);
  document.title = 'NHA Monitor';
  // Another section of the open project: keep its listeners and redraw.
  if (m && s.page?.id === m[1]) {
    Object.assign(s.page, { section: m[2] || 'summary', sub: m[3] || null });
    freshScreen();
    return drawProject(s.page);
  }
  s.unsubPage.forEach(u => u()); s.unsubPage = []; s.page = null;
  if (m) renderProject(m[1], m[2] || 'summary', m[3] || null); else renderDashboard();
}

/* ---------- Sign in (LoginScreen: brand gradient hero beside the form) ---------- */

/** Messages for errors that say nothing about whether an account exists; null for the rest. */
const authTrouble = err => (err.code === 'auth/too-many-requests' ? 'Too many tries. Wait a few minutes, then try again.'
  : err.code === 'auth/network-request-failed' ? 'No internet connection. Check it and try again.' : null);

function renderSignIn(message = '', note = '') {
  main.innerHTML = `
    <div class="signin">
      <section class="hero">
        <img class="hero-logo" src="logo.svg" alt="" width="72" height="72">
        <h1>National Housing Authority</h1>
        <p>Infrastructure Monitoring System</p>
        <small>REGION III • BULACAN DISTRICT OFFICE</small>
        <p class="unofficial">Unofficial app. Not issued or endorsed by the National Housing Authority. Made for the Bulacan District Office; other district offices are welcome to try it.</p>
      </section>
      <form id="signin" class="signin-form" novalidate>
        <h2>Sign in</h2>
        <p class="muted">Use the same email and password as the NHA Monitor app.${emulator ? ' <b>Local emulator.</b>' : ''}</p>
        <label class="field">Email address <input name="email" type="email" autocomplete="username" required></label>
        <label class="field">Password <input name="password" type="password" autocomplete="current-password" required></label>
        <p class="error" role="alert">${esc(message)}</p>
        ${note ? `<p class="note" role="status">${esc(note)}</p>` : ''}
        <button type="submit" class="btn">Sign in</button>
        <div class="row"><button type="button" id="reset" class="text-btn">Forgot password?</button><span class="muted">View only. Edits stay in the app.</span></div>
      </form>
    </div>`;
  const form = document.getElementById('signin');
  form.onsubmit = async e => {
    e.preventDefault();
    const email = form.email.value.trim();
    if (!email || !form.password.value) return renderSignIn('Enter your email and password.');
    form.querySelector('[type=submit]').disabled = true;
    try {
      await signInWithEmailAndPassword(auth, email, form.password.value);
    } catch (err) {
      // One message for unknown email and wrong password, so the page never says which emails have accounts.
      renderSignIn(['auth/invalid-credential', 'auth/user-not-found', 'auth/wrong-password', 'auth/invalid-email', 'auth/user-disabled'].includes(err.code)
        ? 'Wrong email or password. Try again, or reset your password.'
        : authTrouble(err) || 'Could not sign in. Try again in a moment.');
      document.querySelector('#signin [name=email]').value = email;
    }
  };
  document.getElementById('reset').onclick = async () => {
    const email = form.email.value.trim();
    if (!email) return renderSignIn('Type your email first, then choose Forgot password.');
    // The same note whether or not the email has an account.
    const sent = 'If this email has an NHA Monitor account, a password reset link is on its way. Check your inbox and spam folder.';
    try { await sendPasswordResetEmail(auth, email); renderSignIn('', sent); } catch (err) {
      const trouble = authTrouble(err);
      if (trouble) renderSignIn(trouble); else renderSignIn('', sent);
    }
  };
}

/* ---------- Dashboard (DashboardScreen + PortfolioCounts + PortfolioRow) ---------- */

/** One project as the dashboard sees it (ProjectViewModel's PortfolioItem). */
function itemOf(project) {
  const docs = s.docs.get(project.projectUuid) || [];
  const p = L.withSchedule(project, docs, today());
  const billed = L.billedTotal(s.payments.get(p.projectUuid) || []);
  const cost = L.currentCost(p, docs);
  return { p, docs, billed, cost, flags: L.attentionFlags(p, docs, billed, today()), waiting: L.waitingApproval(docs) };
}

/** Label on the left; value and a note (schedule variance, amount) on the right; the bar beneath. */
function progressLine(label, value, note, noteTone, fraction, tone, big = false) {
  return `<div class="pl${big ? ' big' : ''}">
    <div class="pl-head"><span>${esc(label)}</span><b>${esc(value)}</b>${note ? `<small class="n-${noteTone}">${note}</small>` : ''}</div>
    <div class="bar ${tone}" role="img" aria-label="${esc(label)} ${esc(value)}"><i style="width:${(clamp(fraction) * 100).toFixed(1)}%"></i></div>
  </div>`;
}

// Bar: green when finished; blue on plan or slipping under 10%; amber from 10% behind; red from 15% behind or
// once past the completion date (PortfolioRow.varianceColor's thresholds, plus the web's overdue rule).
function workDone(p, big) {
  const now = today(), level = L.varianceLevel(L.slippage(p));
  const tone = L.isCompleted(p) ? 'ok' : L.daysOverdue(p, now) > 0 ? 'behind' : level === 'ok' || level === 'neutral' ? 'primary' : level;
  const note = (tone === 'watch' || tone === 'behind' ? icon('warning', 'sm') : '') + esc(L.scheduleText(p, now));
  const noteTone = tone !== 'primary' ? tone : level === 'ok' ? 'ok' : 'muted';
  return progressLine('Work done', pct(p.actualAccomplishment), note, noteTone, (Number(p.actualAccomplishment) || 0) / 100, tone, big);
}
const billedLine = (it, big) => progressLine('Billed', L.billedLabel(it.billed, it.cost),
  it.billed > 0 ? esc(pesoWhole(it.billed)) : '', 'muted', it.cost > 0 ? it.billed / it.cost : 0, 'billed', big);
/** The status badge on cards and the project header; an overdue project says so instead of "On-going". */
function statusBadge(p) {
  const late = L.daysOverdue(p, today());
  return late ? `<span class="badge lv-behind">Overdue by ${late} ${late === 1 ? 'day' : 'days'}</span>`
    : `<span class="badge lv-${L.statusLevel(p)}">${esc(L.displayStatus(p))}</span>`;
}

function renderDashboard() {
  freshScreen();
  main.innerHTML = `
    <section class="card portfolio" id="portfolio"></section>
    <div class="toolbar">
      <label class="search">${icon('search')}<input id="q" type="search" placeholder="Search name, town or contractor" aria-label="Search projects" value="${esc(s.q)}"><kbd>${MAC ? '⌘' : 'Ctrl'} K</kbd></label>
      <div class="seg" id="scope" role="group" aria-label="Which projects"></div>
      <span class="count" id="shown" role="status"></span>
      <button class="text-btn help-btn" id="help">${icon('info')}What these mean</button>
    </div>
    <ul class="grid" id="grid"></ul>`;
  document.getElementById('q').oninput = e => { s.q = e.target.value; redrawDashboard(); };
  document.getElementById('help').onclick = showTermHelp;
  redrawDashboard();
}

function redrawDashboard() {
  const grid = document.getElementById('grid');
  if (!grid || !s.user) return;
  if (s.projectsError) { grid.outerHTML = `<p class="error">Could not load projects: ${esc(s.projectsError)}</p>`; return; }
  if (s.projects === null) { grid.innerHTML = '<li class="empty">Loading projects…</li>'; return; }

  const mineAvailable = L.seesAll(s.claims) && s.claims.role !== 'SUPER_VIEWER';
  const all = s.projects.map(itemOf);
  const inScope = mineAvailable && s.mineOnly ? all.filter(it => L.isMine(it.p, s.user.uid, s.claims)) : all;

  // Portfolio banner: count and a status breakdown (no total contract cost: user's rule, 2026-11-08). Work done and billed are never averaged
  // across projects (user's rule, 2026-11-08); each card shows its own.
  const done = inScope.filter(it => L.isCompleted(it.p)).length;
  // PortfolioCounts: one row of filters, each counting projects; only non-zero ones; pressing the active one clears it.
  const counts = COUNTS.map(c => ({ ...c, n: inScope.filter(it => it.flags.some(f => f.kind === c.kind)).length })).filter(c => c.n > 0);
  if (s.filter && !counts.some(c => c.kind === s.filter)) s.filter = null;
  const banner = document.getElementById('portfolio');
  const focused = document.activeElement?.closest?.('#portfolio [data-kind]')?.dataset.kind;
  banner.innerHTML = `
    <div class="pf-total">
      <h1>${inScope.length} ${inScope.length === 1 ? 'project' : 'projects'}</h1>
      <p class="pf-stats">${inScope.length - done} on-going · ${done} completed</p>
    </div>
    ${counts.length ? `<div class="pills" role="group" aria-label="Show only projects that are">
      ${counts.map(c => `<button class="pill ${c.tone}" data-kind="${c.kind}" aria-pressed="${s.filter === c.kind}">${icon(c.icon, 'sm')}<b>${c.n}</b> ${esc(c.label)}</button>`).join('')}
    </div>` : ''}
    ${finding(inScope)}`;
  banner.querySelectorAll('[data-kind]').forEach(b => { b.onclick = () => { s.filter = s.filter === b.dataset.kind ? null : b.dataset.kind; redrawDashboard(); }; });
  // The banner was rebuilt: keep keyboard focus on the filter just pressed.
  if (focused) banner.querySelector(`[data-kind="${focused}"]`)?.focus();

  const scopeEl = document.getElementById('scope');
  scopeEl.hidden = !mineAvailable;
  scopeEl.innerHTML = mineAvailable ? `
    <button data-mine="1" aria-pressed="${s.mineOnly}">My projects</button>
    <button data-mine="0" aria-pressed="${!s.mineOnly}">All projects</button>` : '';
  scopeEl.querySelectorAll('[data-mine]').forEach(b => { b.onclick = () => { s.mineOnly = b.dataset.mine === '1'; redrawDashboard(); }; });

  const q = s.q.trim().toLowerCase();
  const shown = inScope
    .filter(it => !s.filter || it.flags.some(f => f.kind === s.filter))
    .filter(it => !q || [it.p.name, it.p.location, it.p.contractor].join(' ').toLowerCase().includes(q))
    // Needs attention first (Portfolio.NEEDS_ATTENTION_SORT), then by name.
    .sort((a, b) => rank(a) - rank(b) || a.p.name.localeCompare(b.p.name));
  document.getElementById('shown').textContent = shown.length === inScope.length ? '' : `Showing ${shown.length} of ${inScope.length}`;

  if (shown.length === 0) {
    grid.innerHTML = `<li class="empty">${inScope.length
      ? `No project matches.<button class="text-btn" id="clear">Clear search and filters</button>`
      : 'No projects for this account yet. Projects appear here once the Super Admin assigns them in the app.'}</li>`;
    document.getElementById('clear')?.addEventListener('click', () => { s.q = ''; s.filter = null; renderDashboard(); });
    return;
  }
  grid.innerHTML = shown.map(tile).join('');
  loadPhotos(grid);
}

function showTermHelp() {
  const d = document.createElement('dialog');
  d.className = 'help';
  d.innerHTML = `<h2>What these mean</h2><dl>${TERM_HELP.map(([t, m]) => `<div><dt>${esc(t)}</dt><dd>${esc(m)}</dd></div>`).join('')}</dl>
    <form method="dialog"><button class="text-btn">Close</button></form>`;
  d.addEventListener('close', () => d.remove());
  document.body.append(d);
  d.showModal();
}

// The screen's one finding: the project the sort puts first, with its worst flag.
function finding(items) {
  const flagged = items.filter(it => it.flags.length).sort((a, b) => rank(a) - rank(b) || a.p.name.localeCompare(b.p.name));
  if (!flagged.length) return items.length ? `<p class="finding calm">${icon('check_circle', 'sm')}No project needs attention.</p>` : '';
  const [first] = flagged, f = first.flags[0], more = flagged.length - 1;
  return `<p class="finding">${icon('warning', 'sm')}<span>First to look at: <a href="#/p/${esc(first.p.projectUuid)}">${esc(first.p.name)}</a>,
    ${esc(f.message.charAt(0).toLowerCase() + f.message.slice(1))}${more ? `<span class="nw"> · ${more} more flagged</span>` : ''}</span></p>`;
}

const rank = it => (it.flags.some(f => f.level === 'critical') ? 0 : it.flags.length ? 1 : 2);

function tile(it) {
  const { p } = it;
  // Overdue goes in the status badge; behind shows on the Work done line; waiting has its own footer badge.
  const flags = it.flags.filter(f => !['behind', 'waiting', 'overdue'].includes(f.kind));
  const completion = L.displayDate(L.effectiveCompletion(p));
  return `<li><a class="card tile" href="#/p/${esc(p.projectUuid)}">
    <div class="tile-top">
      <div class="photo" data-ref="${esc(p.latestUpdatePhotoUrl || '')}">${icon('photo_camera')}</div>
      <div class="tile-name">
        <h2>${esc(p.name)}</h2>
        <span class="loc" title="${esc(p.location || '')}">${esc(p.location || '-')}</span>
      </div>
      <div class="tile-badges">
        ${statusBadge(p)}
        ${L.isViewOnly(p, s.user.uid, s.claims) ? `<span class="badge view">${icon('visibility', 'sm')}View only</span>` : ''}
      </div>
    </div>
    <div class="tile-bars">${workDone(p)}${billedLine(it)}</div>
    ${flags[0] ? `<p class="flag t-${flags[0].level}">${esc(flags[0].message)}${flags.length > 1 ? `  +${flags.length - 1} more` : ''}</p>` : ''}
    <div class="meta">
      <span>${icon('event', 'sm')}Completion ${esc(completion)}</span>
      ${it.waiting ? `<span class="badge lv-watch">${icon('description', 'sm')}${it.waiting === 1 ? '1 order waiting approval' : `${it.waiting} orders waiting approval`}</span>` : ''}
    </div>
  </a></li>`;
}

/* ---------- Project page (ProjectHeader, the four ProjectSection tabs) ---------- */

function renderProject(projectUuid, section, sub) {
  const page = s.page = { id: projectUuid, project: undefined, records: [], error: null, section, sub, chips: {}, month: null };
  main.innerHTML = '<p class="muted">Loading project…</p>';
  freshScreen();
  const draw = () => drawProject(page);
  s.unsubPage.push(onSnapshot(doc(db, 'projects', projectUuid), d => {
    page.project = d.exists() && d.data().isDeleted !== true ? { ...d.data(), projectUuid: d.id } : null;
    draw();
  }, e => { page.error = e.code === 'permission-denied' ? 'This account cannot open this project. Ask the Super Admin to assign it to you.' : e.message; draw(); }));
  s.unsubPage.push(onSnapshot(scoped('records', where('projectUuid', '==', projectUuid)), snap => {
    page.records = snap.docs.map(d => L.parseRecord({ ...d.data(), uuid: d.id })).filter(r => !r.isDeleted);
    draw();
  }, e => { page.error = e.message; draw(); }));
}

const backLink = `<a class="text-btn back" href="#/">${icon('arrow_back', 'sm')}All projects</a>`;
const kv = (k, v, cls = '') => {
  const nil = v === '' || v == null || v === '-';
  return `<div class="kv"><span>${esc(k)}</span><b class="${nil ? 'nil' : cls}">${nil ? '-' : esc(v)}</b></div>`;
};
const section = (title, ic, body, cls = '') => `<section class="card section ${cls}"><div class="section-head">${icon(ic)}<h2>${esc(title)}</h2></div>${body}</section>`;
const ISO = /^\d{4}-\d{2}-\d{2}$/;
const signedPct = v => `${v >= 0 ? '+' : ''}${(Number(v) || 0).toFixed(2)}%`;
const plural = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;
const emptyLine = text => `<p class="none-note">${icon('info', 'sm')}${esc(text)}</p>`;
/** A link to an attached file (a scan, letter or photo); its address is filled in once Storage gives it. */
const fileLink = (refText, label) => (L.storagePathOf(refText)
  ? `<a class="file" data-file="${esc(refText)}" target="_blank" rel="noopener">${icon(L.isPdf(refText) ? 'picture_as_pdf' : 'attach_file', 'sm')}${esc(label)}</a>`
  : `<span class="file gone">${icon('attach_file', 'sm')}${esc(label)} not uploaded yet</span>`);
/** ProjectSection: the project screen's four sections. */
const SECTIONS = [['summary', 'Summary', 'dashboard'], ['progress', 'Progress', 'trending_up'], ['payments', 'Payments', 'payments'], ['documents', 'Documents', 'folder_open']];
/** SectionChips: the views inside Progress (Lots only for housing) and Documents. */
const CHIPS = {
  progress: ['Weekly', 'Monthly', 'Photos', 'Weather', 'Issues'],
  documents: ['Suspension / Resumption', 'Time Extensions', 'Variation Orders', 'Letters to Contractor', 'Developer Requests', 'Related Files'],
};

function drawProject(page) {
  if (page !== s.page) return;
  const { project: stored, records, error } = page;
  if (error) { main.innerHTML = `${backLink}<p class="error">${esc(error)}</p>`; return; }
  if (stored === undefined) return;
  if (stored === null) { main.innerHTML = `${backLink}<p class="muted">This project was deleted or does not exist.</p>`; return; }
  const of = type => records.filter(r => r.type === type);
  const documents = of('pending_documents');
  const project = L.withSchedule(stored, documents, today());
  // Room lists billings by local id (creation order); the cloud has no id, so date then last edit.
  const payments = of('project_payments').sort((a, b) => String(a.date).localeCompare(String(b.date)) || a.updatedAt - b.updatedAt);
  // The phone lists weekly reports newest added first.
  const weekly = of('weekly_reports').sort((a, b) => (b.createdAtTimestamp || 0) - (a.createdAtTimestamp || 0));
  const cost = L.currentCost(project, documents);
  const billed = L.billedTotal(payments);
  const c = { page, project, records, of, documents, payments, weekly, cost, billed, now: today() };
  if (page.section === 'print') return drawPrint(c);

  const housing = /housing/i.test(project.projectType || '');
  const chips = page.section === 'progress' && housing ? ['Weekly', 'Monthly', 'Lots', 'Photos', 'Weather', 'Issues']
    : page.section === 'documents' && L.canViewAuditLogs(s.claims) ? [...CHIPS.documents, 'Audit log'] : CHIPS[page.section];
  const chip = chips && (chips.includes(page.chips[page.section]) ? page.chips[page.section] : chips[0]);
  const body = { progress: progressBody, payments: paymentsBody, documents: documentsBody }[page.section] || summaryBody;
  const focused = document.activeElement?.dataset?.chip;
  main.innerHTML = `
    ${backLink}
    <header class="card project-head">
      <div class="ph-top">
        <div class="ph-title"><h1>${esc(project.name)}</h1>
          <p class="ph-loc">${icon('location_on', 'sm')}${esc(project.location || '-')}</p></div>
        <div class="ph-badges">${statusBadge(project)}
          ${L.isViewOnly(project, s.user.uid, s.claims) ? `<span class="badge view">${icon('visibility', 'sm')}View only</span>` : ''}</div>
      </div>
      <div class="ph-bars">${workDone(project, true)}${billedLine({ billed, cost }, true)}</div>
    </header>
    <nav class="tabs" aria-label="Project sections">${SECTIONS.map(([key, label, ic]) => `
      <a href="#/p/${esc(page.id)}${key === 'summary' ? '' : `/${key}`}"${page.section === key ? ' aria-current="page"' : ''}>${icon(ic, 'sm')}${label}</a>`).join('')}
    </nav>
    ${chips ? `<div class="chips" role="group" aria-label="Show">${chips.map(x => `<button data-chip="${esc(x)}" aria-pressed="${x === chip}">${esc(x)}</button>`).join('')}</div>` : ''}
    ${body(c, chip)}`;
  main.querySelectorAll('[data-chip]').forEach(b => { b.onclick = () => { page.chips[page.section] = b.dataset.chip; drawProject(page); }; });
  if (focused) main.querySelector(`[data-chip="${CSS.escape(focused)}"]`)?.focus();
  const month = document.getElementById('month');
  if (month) month.onchange = () => { page.month = month.value; drawProject(page); };
  main.querySelectorAll('[data-wx]').forEach(el => { el[el.tagName === 'SELECT' ? 'onchange' : 'onclick'] = () => { page.wx[el.dataset.wx] = el.value; drawProject(page); }; });
  if (document.getElementById('site-photo')) showPhoto(project.latestUpdatePhotoUrl, document.getElementById('site-photo'));
  if (document.getElementById('lot-results')) bindLots(c);
  if (document.getElementById('issue-results')) bindIssues(c);
  if (document.getElementById('audit-results')) bindAudit(c);
  loadFiles(main);
}

/* ---------- Summary (SummarySection) ---------- */

function summaryBody({ project, records, of, documents, payments, weekly, cost, billed, now }) {
  const latest = weekly[0];
  const openIssues = of('project_issues').filter(L.isOpenIssue).length;
  const orders = documents.filter(d => SCHEDULE_ORDER.test(d.documentName || ''));
  const flags = L.attentionFlags(project, documents, billed, now);
  const housing = /housing/i.test(project.projectType || '');
  const finished = L.isCompleted(project);
  // KeyDatesCard: approved time extensions first, else the stored revised date; Days left counts down to the same date.
  const fromOrders = L.revisedCompletion(project, documents);
  const stored = (project.completionDateRevised || '').trim();
  const revised = fromOrders || (stored !== '-' ? stored : '');
  const dated = fromOrders ? { ...project, completionDateRevised: fromOrders } : project;
  const revisedDays = L.revisedDurationDays(project, L.addedDays(documents));

  // Needs attention: one alert box per flag; critical ones (overdue, 15%+ behind) carry a pulsing dot.
  const attention = flags.length ? section('Needs attention', 'notification_important', `<div class="alerts">${flags.map(f => `
    <p class="alert ${f.level}">${f.level === 'critical' ? '<i class="pulse" aria-hidden="true"></i>' : icon('warning', 'sm')}<b>${esc(f.message)}</b></p>`).join('')}</div>`) : '';
  const profile = section('Project profile', 'apartment', `
    <div class="site-photo" id="site-photo">${icon('architecture')}<span>No site photo yet</span></div>
    <div class="attrs">
      ${kv('Implementing office', project.implementingOffice)}
      ${kv('Contractor', project.contractor, 'hl')}
      ${kv('Project type', project.projectType)}
      ${housing ? kv('Land area', project.landArea) + kv('No. of units', project.numberOfUnits) : ''}
    </div>
    ${(() => { const items = L.scopeItems(project.scopeOfWork); return items.length ? `<p class="sub">Scope of works</p>${items.map((x, i) => `
      <div class="scope"><b>${i + 1}. ${esc(x.name)}</b>${kv('Amount', x.amount > 0 ? L.formatPhp(x.amount) : '-')}${kv('Weight', `${x.weightPct.toFixed(2)}%`)}</div>`).join('')}` : ''; })()}`);

  // Key dates as a timeline: filled = passed, hollow = ahead, red (late) = a completion date passed on an unfinished project.
  const step = (label, date, completion) => {
    const has = ISO.test(date || '');
    const state = !has ? 'none' : date > now ? 'next' : completion && !finished ? 'late' : 'done';
    return `<li class="step ${state}"><span>${esc(label)}</span><b>${has ? esc(L.displayDate(date)) : '-'}</b></li>`;
  };
  const daysLeft = L.daysLeftText(dated, now);
  const dates = section('Key dates', 'event', `
    <ol class="timeline">
      ${step('Date started', project.dateStarted, false)}
      ${step('Original completion', project.completionDateOriginal, true)}
      ${step('Revised completion', revised, true)}
    </ol>
    ${kv('Contract duration', `${project.contractDurationDays ?? 0} days`)}
    ${kv('Revised duration', revisedDays == null ? '-' : `${revisedDays} days`)}
    <div class="kv"><span>Days left</span><b><span class="badge ${daysLeft.startsWith('Overdue') ? 'lv-behind' : finished ? 'lv-ok' : 'lv-neutral'}">${esc(daysLeft)}</span></b></div>
    <p class="sub">Time extensions &amp; suspensions</p>
    ${orders.length ? orders.map(d => `<div class="log"><b>${esc(L.orderLabel(d.documentName))}</b><span>Status: ${esc(d.status)}${d.remarks ? ` · ${esc(L.withEnd(d.remarks))}` : ''}</span></div>`).join('')
      : `<p class="none-note">${icon('info', 'sm')}No time extension or suspension orders.</p>`}`);
  const costCard = section('Contract cost', 'payments', `<div class="money">
    <div><span>${icon('work', 'sm')}Original cost</span><b>${esc(L.formatPhp(L.originalCost(project)))}</b></div>
    <div><span>${icon('receipt_long', 'sm')}Current cost</span><b>${esc(L.formatPhp(cost))}</b></div></div>`);
  const issues = `<span class="badge ${openIssues ? 'lv-watch' : 'lv-ok quiet'}">${openIssues} open ${openIssues === 1 ? 'issue' : 'issues'}</span>`;
  const report = section('Latest weekly report', 'assignment', latest ? `
    <div class="rep-top"><p>${esc(latest.reportingWeek)}</p>${issues}</div>
    ${kv('Target', pct(latest.targetAccomplishmentPct))}${kv('Actual', pct(latest.actualAccomplishmentPct))}${kv('Submitted by', latest.submittedByStaff)}`
    : `<div class="empty-row"><i class="ghost" aria-hidden="true"></i><p class="muted">No weekly reports yet</p>${issues}</div>`);
  const construction = housing ? constructionCard(L.lotSummary(records), payments, now) : '';
  const paymentCard = section('Status of payment', 'receipt_long', payments.length === 0 ? `
    <div class="empty-ledger">${icon('receipt_long')}<p>No billings yet</p><div class="ghost-rows" aria-hidden="true"><i></i><i></i><i></i></div></div>` : `
    <div class="scroll"><table class="pay">
      <thead><tr><th>Billing / period covered</th><th>Date</th><th class="num">Net amount (PHP)</th><th class="num">%</th><th class="num">Balance (PHP)</th></tr></thead>
      <tbody>${payments.map(p => `<tr>
        <td>${esc(p.name || '-')}${p.periodCovered ? `<br><span class="muted">${esc(p.periodCovered)}</span>` : ''}<span class="pdate"><br>${esc(L.displayDate(p.date))}</span></td>
        <td>${esc(L.displayDate(p.date))}</td>
        <td class="num">${L.formatDecimal(L.netAmount(p))}</td>
        <td class="num">${pct(p.percentage)}</td>
        <td class="num">${L.formatDecimal(Number(p.balanceAmount) || 0)}</td></tr>`).join('')}
      </tbody>
      <tfoot><tr><th>Total</th><th></th>
        <th class="num">${L.formatDecimal(payments.reduce((t, p) => t + L.netAmount(p), 0))}</th>
        <th class="num">${cost > 0 ? pct(billed / cost * 100) : '-'}</th>
        <th class="num">${L.formatDecimal(cost - billed)}</th></tr></tfoot>
    </table></div>`, 'wide');

  return `
    <div class="bento">
      <div class="col c1">${attention}${profile}</div>
      <div class="col c2">${dates}</div>
      <div class="col c3">${costCard}${report}${construction}</div>
      ${paymentCard}
    </div>`;
}

/** ConstructionCard: saleable units by progress band in the lot map's colours, then the Status of billing; nothing without saleable lots. */
function constructionCard(summary, payments, now) {
  if (!summary.saleable) return '';
  return section('Status of construction', 'home_work', `
    <p class="muted asof">As of ${esc(L.displayDate(now))}</p>
    <div class="lot-bar" role="img" aria-label="${esc(L.LOT_BANDS.map((b, i) => `${b}: ${summary.bands[i]}`).join(', '))}">${summary.bands.map((n, i) => (n ? `<i class="lot-${i}" style="flex:${n}"></i>` : '')).join('')}</div>
    ${L.LOT_BANDS.map((b, i) => `<div class="band"><i class="dot lot-${i}" aria-hidden="true"></i>${kv(b, String(summary.bands[i]))}</div>`).join('')}
    <div class="band total"><b>Grand total</b><b class="num">${plural(summary.saleable, 'unit')}</b></div>
    <p class="sub strong">Status of billing</p>
    <table class="billing"><thead><tr><th>Billed for</th><th class="num">Lots billed</th><th class="num">% of ${summary.saleable}</th></tr></thead>
      <tbody>${L.billingStatus(payments, summary.saleable).map(r => `<tr><td>${esc(r.label)}</td><td class="num">${r.units}</td><td class="num">${r.share}</td></tr>`).join('')}</tbody></table>`);
}

/* ---------- Progress (WeeklyReportsTab, MonthlyReportsTab, PhotoGalleryTab) ---------- */

function progressBody(c, chip) {
  if (chip === 'Monthly') return monthlyView(c);
  if (chip === 'Lots') return lotsView(c);
  if (chip === 'Photos') return photosView(c);
  if (chip === 'Weather') return weatherView(c);
  if (chip === 'Issues') return issuesView(c);
  if (!c.weekly.length) return `<div class="card empty">${icon('event_note')}<p>No weekly engineer reports submitted yet</p></div>`;
  return `<h2 class="list-title">NHA weekly project engineer's report</h2><div class="stack">${c.weekly.map(r => reportCard(r, c.weekly, c.page.id)).join('')}</div>`;
}

const table = (head, rows, foot = '') => `<div class="scroll"><table class="grid-table"><thead><tr>${head.map(h => `<th${h.num ? ' class="num"' : ''}>${esc(h.text ?? h)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody>${foot}</table></div>`;
/** The Equipment table's status chip: Operational green, Idle or For Repair amber, anything else grey. */
const equipmentLevel = status => (/^operational$/i.test(status.trim()) ? 'ok' : /^(idle|for repair)$/i.test(status.trim()) ? 'watch' : 'neutral');

/** varianceColor as a text colour class; small slips stay grey. */
const noteLevel = v => { const lv = L.varianceLevel(Math.round(v * 100) / 100); return lv === 'neutral' ? 'muted' : lv; };

/** WeeklyReportCard: week, who and when, actual against target; the report's five tables and photos under "Report details". */
function reportCard(r, all, pid) {
  const target = Number(r.targetAccomplishmentPct) || 0, actual = Number(r.actualAccomplishmentPct) || 0;
  const prev = L.previousReport(all, r);
  const acc = L.accomplishmentOf(r.accomplishmentItemsJson);
  const activities = L.activitiesOf(r.activitiesJson), manpower = L.manpowerOf(r.manpowerJson), equipment = L.equipmentOf(r.equipmentJson), issues = L.issuesOf(r.issuesJson);
  const photos = L.attachmentList(r.attachedPhotoUrlsJson);
  const vCell = (v, tag = 'td') => `<${tag} class="num n-${noteLevel(v)}">${signedPct(v)}</${tag}>`;
  const crew = manpower.reduce((t, m) => t + m.count, 0);
  return `<article class="card report">
    <div class="rep-head">
      <div><h3>${esc(r.reportingWeek)}</h3><p class="muted">${esc(r.submittedByStaff || '-')} · Day ${esc(r.daysElapsed ?? 0)}, ${esc(r.remainingDays ?? 0)} days left</p></div>
      <a class="text-btn" href="#/p/${esc(pid)}/print/${esc(r.uuid)}">${icon('print', 'sm')}Print</a>
    </div>
    <p class="rep-figures"><span class="badge lv-${L.varianceLevel(Math.round((actual - target) * 100) / 100)}">Actual ${pct(actual)}</span>
      <span class="muted">Target ${pct(target)}${prev ? ` · ${signedPct(actual - (Number(prev.actualAccomplishmentPct) || 0))} from previous report` : ''}</span></p>
    <details>
      <summary>${icon('expand_more', 'sm')}Report details${photos.length ? ` · ${plural(photos.length, 'photo')}` : ''}</summary>
      <div class="rep-body">
        <h4>1. Weekly accomplishment status</h4>
        ${table(['Item', { text: 'Target', num: 1 }, { text: 'Actual', num: 1 }, { text: 'Variance', num: 1 }],
          acc.map(a => `<tr><td>${esc(a.itemDescription)} (${pct(a.weightPct)})</td><td class="num">${pct(a.targetPct)}</td><td class="num">${pct(a.actualPct)}</td>${vCell(a.variancePct)}</tr>`).join(''),
          `<tfoot><tr><th>Grand total</th><th class="num">${pct(target)}</th><th class="num">${pct(actual)}</th>${vCell(actual - target, 'th')}</tr></tfoot>`)}
        <h4>2. Activities of the week</h4>
        ${activities.length ? table(['Day of week', 'Activities', 'Remarks'], activities.map(a => `<tr><td><b>${esc(a.day)}</b></td><td>${esc(a.description)}</td><td class="muted">${esc(a.remarks || '-')}</td></tr>`).join(''))
          : /^\s*(\[\s*\])?\s*$/.test(r.activitiesJson || '') ? emptyLine('No activities logged.') : `<p class="raw">${esc(r.activitiesJson)}</p>`}
        <h4>3. Manpower summary</h4>
        ${manpower.length ? table(['Designation', { text: 'No. of personnel', num: 1 }], manpower.map(m => `<tr><td>${esc(m.designation)}</td><td class="num"><b>${m.count}</b></td></tr>`).join(''),
          `<tfoot><tr><th>Grand total weekly</th><th class="num">${plural(crew, 'personnel', 'personnel')}</th></tr></tfoot>`) : emptyLine('No manpower logged.')}
        <h4>4. Equipment summary</h4>
        ${equipment.length ? table(['Name of equipment', { text: 'No.', num: 1 }, 'Remarks / status'], equipment.map(e => `<tr><td>${esc(e.description)}</td><td class="num"><b>${e.count}</b></td><td><span class="badge lv-${equipmentLevel(e.status)}">${esc(e.status)}</span></td></tr>`).join(''))
          : emptyLine('No equipment logged.')}
        <h4>5. Issues encountered / action taken</h4>
        ${issues.length ? table(['Issues encountered', 'Action taken'], issues.map(i => `<tr><td>${esc(i.description)}</td><td><b>${esc(i.actionTaken)}</b></td></tr>`).join(''))
          : emptyLine('No issues encountered for this reporting period.')}
        ${photos.length ? `<h4>Attached site photos (${photos.length})</h4>${photoGrid(photos, L.photoDetails(r.photoDetailsJson))}` : ''}
      </div>
    </details>
  </article>`;
}

/** Photos as thumbnails that open full size; PDFs in the list as file links. */
function photoGrid(refs, details) {
  return `<ul class="photos">${refs.map(refText => {
    const d = details[refText] || {};
    const caption = [d.title, d.takenAt].filter(Boolean).map(esc).join('<br>');
    if (L.isPdf(refText)) return `<li>${fileLink(refText, d.title || 'Attached PDF')}</li>`;
    if (!L.storagePathOf(refText)) return `<li><span class="thumb gone">${icon('photo_camera')}<small>Not uploaded yet</small></span></li>`;
    return `<li><a class="thumb" data-file="${esc(refText)}" data-alt="${esc(d.title || 'Site photo')}" target="_blank" rel="noopener">${icon('photo_camera')}</a>${caption ? `<p>${caption}</p>` : ''}</li>`;
  }).join('')}</ul>`;
}

/** MonthlyReportSingleTableCard: the month's weekly figures; the month stands at its last week. */
function monthlyView({ page, weekly, of, now }) {
  const months = L.reportMonths(weekly, of('monthly_reports'), now);
  const month = months.includes(page.month) ? page.month : months[0];
  const rows = L.monthReports(weekly, month);
  const last = rows[rows.length - 1];
  const t = Number(last?.targetAccomplishmentPct) || 0, a = Number(last?.actualAccomplishmentPct) || 0, v = a - t;
  const level = L.varianceLevel(Math.round(v * 100) / 100);
  const key = P.monthKey(month); // the phone prints only a month it can read ("September 2026")
  return `<section class="card section month">
    <div class="month-head">
      <label class="field">Month <select id="month">${months.map(m => `<option${m === month ? ' selected' : ''}>${esc(m)}</option>`).join('')}</select></label>
      ${key ? `<a class="text-btn" href="#/p/${esc(page.id)}/print/${key}">${icon('print', 'sm')}Print</a>` : ''}
    </div>
    <h3>Monthly accomplishment report – ${esc(month)}</h3>
    <p><span class="badge lv-${level}">${v >= 0 ? `AHEAD (${signedPct(v)})` : `SLIPPAGE (${signedPct(v)})`}</span></p>
    ${rows.length ? table(['Reporting week', { text: 'Target %', num: 1 }, { text: 'Actual %', num: 1 }, { text: 'Variance %', num: 1 }],
      rows.map(r => { const rv = (Number(r.actualAccomplishmentPct) || 0) - (Number(r.targetAccomplishmentPct) || 0);
        return `<tr><td>${esc(r.reportingWeek)}</td><td class="num">${pct(r.targetAccomplishmentPct)}</td><td class="num">${pct(r.actualAccomplishmentPct)}</td><td class="num n-${noteLevel(rv)}"><b>${signedPct(rv)}</b></td></tr>`; }).join(''),
      `<tfoot><tr><th>As of month end</th><th class="num">${pct(t)}</th><th class="num">${pct(a)}</th><th class="num n-${noteLevel(v)}">${signedPct(v)}</th></tr></tfoot>`)
      : emptyLine(`No weekly reports found for ${month}.`)}
  </section>`;
}

/* ---------- Lots (SubdivisionPlanTab, read-only) ---------- */

// shortcut: lots show as a block-by-block grid, not over the plan PDF (the site cannot draw the PDF or know its page shape);
// the plan opens as a file instead. Roads drawn on the plan are not shown.
const lotState = page => (page.lots ||= { site: null, query: '', band: 'ALL', billing: 'ALL', completion: 'ALL', sel: null });
const lotSite = (c, st) => { const sites = L.sdpSites(c.records); return { sites, site: sites.find(x => x.site === st.site) || sites[0] }; };

function lotsView(c) {
  const st = lotState(c.page);
  const { sites, site } = lotSite(c, st);
  if (!site) return `<div class="card empty">${icon('home_work')}<p>No subdivision plan uploaded yet</p></div>`;
  const k = L.lotStats(site.lots);
  const tile = (label, value, cls = '') => `<div class="kpi ${cls}"><span>${label}</span><b>${value}</b></div>`;
  const select = (key, label) => `<label class="field">${label}<select data-lot-filter="${key}">${L.LOT_FILTERS[key].map(o => `<option${o === st[key] ? ' selected' : ''}>${esc(o)}</option>`).join('')}</select></label>`;
  return `<section class="card section lots">
    ${sites.length > 1 ? `<div class="chips" role="group" aria-label="Site">${sites.map(x => `<button data-lot-site="${esc(x.site)}" aria-pressed="${x === site}">${esc(x.site || 'Main site')}</button>`).join('')}</div>` : ''}
    <div class="lots-head"><h3>${esc(site.plan.planName || 'Subdivision plan')} <span class="muted">v${esc(site.plan.version ?? 1)}</span></h3>
      <span class="lots-links">${site.plan.pdfFileUrl ? fileLink(site.plan.pdfFileUrl, 'Plan PDF') : ''}
        ${site.plan.pdfFileUrl ? `<a class="text-btn" href="#/p/${esc(c.page.id)}/print/sdp">${icon('print', 'sm')}Print</a>` : ''}</span></div>
    <div class="kpis">${tile('Saleable lots', k.total)}${tile('M1 billed', k.m1, 'link')}${tile('M2 billed', k.m2, 'link')}${tile('Not billed', k.notBilled, 'muted')}${tile('Completed', k.completed, 'ok')}${tile('Avg progress', `${k.avg.toFixed(1)}%`, 'watch')}</div>
    <div class="lot-filters">
      <label class="field grow">Search<input type="search" id="lot-q" placeholder="Block, lot or unit (b3-l12)" value="${esc(st.query)}" autocomplete="off"></label>
      ${select('band', 'Progress')}${select('billing', 'Billing')}${select('completion', 'Status')}
    </div>
    <div id="lot-results">${lotResults(site, st)}</div>
  </section>`;
}

function lotResults(site, st) {
  if (!site.lots.length) return emptyLine('No lots drawn on this plan yet.');
  const n = site.lots.filter(l => L.lotMatches(l, st)).length;
  const sel = site.lots.find(l => l.uuid === st.sel);
  return `<p class="muted lot-count">${n === site.lots.length ? plural(n, 'lot') : `${n} of ${plural(site.lots.length, 'lot')} match`}</p>
    <div class="lot-legend">${L.LOT_BANDS.map((b, i) => `<span><i class="dot lot-${i}"></i>${esc(b)}</span>`).join('')}<span><i class="dot lot-n"></i>Not a housing unit</span></div>
    ${L.lotBlocks(site.lots).map(b => `<div class="lot-block"><h4>${esc(b.name)}</h4><div class="lot-grid">${b.lots.map(l => {
      const p = L.lotProgress(l);
      return `<button class="lot ${L.lotColour(l)}${L.lotMatches(l, st) ? '' : ' dim'}" data-lot="${esc(l.uuid)}" aria-pressed="${l.uuid === st.sel}"
        aria-label="${esc(`${L.lotLabel(l)}, ${p}%`)}" title="${esc(`${L.lotLabel(l)} · ${p}%`)}">${esc(l.lotNumber || '?')}</button>`;
    }).join('')}</div>${sel && b.lots.includes(sel) ? lotDetail(sel) : ''}</div>`).join('')}`;
}

/** LotInfoDialog's current status, read-only. */
function lotDetail(l) {
  const p = l.progress || {};
  const road = L.lotUse(l) === 'Road Lot';
  const photos = L.attachmentList(p.photosJson);
  const bill = (status, date, ref) => (status === 'BILLED' ? ['Billed', date && L.displayDate(date), ref].filter(Boolean).join(' · ') : 'Not billed');
  return `<div class="lot-detail" id="lot-detail">
    <div class="ld-head"><h3>${esc(L.lotLabel(l))}</h3><span class="lot-pct ${L.lotColour(l)}">${L.lotProgress(l)}%</span>
      <button class="text-btn" id="lot-close">${icon('close', 'sm')}Close</button></div>
    ${kv('Use', L.lotUse(l))}
    ${kv('Physical progress', `${L.lotProgress(l)}% (${p.constructionStatus || 'Not Started'})`)}
    ${road ? kv('Drainage progress', `${Number(p.drainageProgress) || 0}%`) : ''}
    ${kv('Developer billing', L.lotBillingLabel(l.progress))}
    ${kv('M1 (80%)', bill(p.billingStatus, p.billingDate, p.billingReference))}
    ${kv('M2 (20%)', bill(p.m2BillingStatus, p.m2BillingDate, p.m2BillingReference))}
    ${kv('Housing unit', l.housingUnitNumber || 'N/A')}
    ${kv('Area', Number(l.lotAreaSqM) > 0 ? `${Number(l.lotAreaSqM).toLocaleString('en-US', { maximumFractionDigits: 2 })} sq.m` : '-')}
    ${kv('Current activity', p.currentActivity)}${kv('Contractor', p.contractor)}
    ${kv('Start date', p.startDate && L.displayDate(p.startDate))}${kv('Target completion', p.targetCompletionDate && L.displayDate(p.targetCompletionDate))}
    ${kv('Remarks', p.remarks)}
    ${photos.length ? `<h4>Lot photos (${photos.length})</h4>${photoGrid(photos, {})}` : ''}
  </div>`;
}

function bindLots(c) {
  const st = lotState(c.page);
  const results = document.getElementById('lot-results');
  // Only the results redraw while typing, so the search box keeps its focus.
  const redraw = scroll => {
    results.innerHTML = lotResults(lotSite(c, st).site, st);
    bindResults();
    loadFiles(results);
    if (scroll) document.getElementById('lot-detail')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  };
  const bindResults = () => {
    results.querySelectorAll('[data-lot]').forEach(b => { b.onclick = () => { st.sel = st.sel === b.dataset.lot ? null : b.dataset.lot; redraw(true); }; });
    const close = document.getElementById('lot-close');
    if (close) close.onclick = () => { st.sel = null; redraw(false); };
  };
  bindResults();
  document.getElementById('lot-q').oninput = e => { st.query = e.target.value; redraw(false); };
  main.querySelectorAll('[data-lot-filter]').forEach(sel => { sel.onchange = () => { st[sel.dataset.lotFilter] = sel.value; redraw(false); }; });
  main.querySelectorAll('[data-lot-site]').forEach(b => { b.onclick = () => { st.site = b.dataset.lotSite; st.sel = null; drawProject(c.page); }; });
}

/* ---------- Weather (WeatherChartTab), read-only ---------- */

/** WeatherChartTab: each logged day as a 24-hour clock, by weekly report (7 days) or by month. */
function weatherView({ page, weekly }) {
  // Only logs saved with a weekly report reach the cloud, which matches the phone's submittedWeatherLogs.
  const logs = weekly.flatMap(r => (Array.isArray(r._weather) ? r._weather : []));
  if (!logs.length && !weekly.length) return `<div class="card empty">${icon('sunny')}<p>No Weather Logs Recorded Yet</p><small>Field staff can submit daily 24h weather logs when submitting weekly reports.</small></div>`;
  const st = (page.wx ||= { mode: weekly.length ? 'week' : 'month', report: '', month: '' });
  // label is HTML built from the date only.
  const clock = (log, day, label = '') => `<figure class="wx-day">${label ? `<figcaption>${label}</figcaption>` : ''}${P.weatherClock(log, day)}</figure>`;
  let body;
  if (st.mode === 'week') {
    const report = weekly.find(r => r.uuid === st.report) || weekly[0];
    const own = Array.isArray(report?._weather) ? report._weather : [];
    const days = report ? L.weekDays(report.reportingWeek) : [];
    body = !report ? emptyLine('No weekly reports submitted yet.') : `
      <label class="field">Report<select data-wx="report">${weekly.map((r, i) => `<option value="${esc(r.uuid)}"${r === report ? ' selected' : ''}>Week ${weekly.length - i}: ${esc(r.reportingWeek)}</option>`).join('')}</select></label>
      <h3 class="wx-title">Weekly report weather mapping <span class="muted">7 days</span></h3>
      <p class="muted small">Reporting period: ${esc(report.reportingWeek)}</p>
      ${days.length ? `<div class="wx-grid">${days.map(d => {
        const t = new Date(`${d}T00:00:00Z`), fmt = o => t.toLocaleDateString('en-US', { ...o, timeZone: 'UTC' });
        return clock(own.find(w => (w.date || '').trim() === d), Number(d.slice(8)), `${fmt({ weekday: 'short' })}<span>${fmt({ month: 'short', day: '2-digit' })}</span>`);
      }).join('')}</div>` : emptyLine('The site cannot read the dates of this report\'s week.')}`;
  } else {
    const months = L.weatherMonths(logs);
    const month = months.includes(st.month) ? st.month : months[0];
    body = !month ? emptyLine('No weather logs submitted with weekly reports yet.') : `
      <label class="field">Month<select data-wx="month">${months.map(m => `<option value="${m}"${m === month ? ' selected' : ''}>${esc(P.monthName(m))}</option>`).join('')}</select></label>
      <div class="wx-grid month">${Array.from({ length: L.daysInMonth(month) }, (_, i) => {
        const d = `${month}-${String(i + 1).padStart(2, '0')}`;
        return clock(logs.find(w => (w.date || '').trim() === d), i + 1);
      }).join('')}</div>`;
  }
  const mode = (key, label, ic) => `<button data-wx="mode" value="${key}" aria-pressed="${st.mode === key}">${icon(ic, 'sm')}${label}</button>`;
  return `<section class="card section wx">
    <div class="chips" role="group" aria-label="Weather view">${mode('week', 'Weekly Report Range', 'date_range')}${mode('month', 'Monthly View', 'calendar_month')}</div>
    ${body}
    <p class="wx-legend">${P.WEATHER.map(([, label, hex]) => `<span><i style="background:${hex}"></i>${label}</span>`).join('')}</p>
    <p class="muted small">Inner ring 12 AM to 11 AM, outer ring 12 PM to 11 PM. A day with no log stays blank.</p>
  </section>`;
}

/* ---------- Audit log (AuditLogTab, AuditLogCard), read-only ---------- */

const auditState = page => (page.audit ||= { query: '', filter: 'All' });

function auditView(c) {
  const st = auditState(c.page);
  return `<label class="field issue-q">Search<input type="search" id="audit-q" placeholder="Search audit logs (user, action, details, device)..." value="${esc(st.query)}" autocomplete="off"></label>
    <div class="chips" role="group" aria-label="Filter audit log">${L.AUDIT_FILTERS.map(f => `<button data-audit-filter="${f}" aria-pressed="${f === st.filter}">${f}</button>`).join('')}</div>
    <div id="audit-results">${auditResults(c, st)}</div>`;
}

function auditResults(c, st) {
  // ProjectDao.getAuditLogsForProject: newest first.
  const logs = c.of('audit_logs').sort((a, b) => (Number(b.timestamp) || 0) - (Number(a.timestamp) || 0)).filter(l => L.auditMatches(l, st.query, st.filter));
  if (!logs.length) return `<div class="card empty">${icon('history')}<p>No audit log records found</p></div>`;
  return `<ul class="issue-cards">${logs.map(auditCard).join('')}</ul>`;
}

function auditCard(log) {
  const [level, ic] = L.auditBadge(log.actionType);
  const t = new Date(Number(log.timestamp) || 0);
  const two = n => String(n).padStart(2, '0');
  const when = `${t.getFullYear()}-${two(t.getMonth() + 1)}-${two(t.getDate())} ${two(t.getHours())}:${two(t.getMinutes())}:${two(t.getSeconds())}`;
  const value = (label, v, cls) => (v?.trim() ? `<p><b class="${cls}">${label}:</b> ${esc(v)}</p>` : '');
  return `<li class="card audit-card">
    <div class="ac-head"><span class="ac-badge n-${level}">${icon(ic, 'sm')}${esc(log.actionType || '-')}</span><time class="muted small">${when}</time></div>
    <p class="ac-who">${icon('person', 'sm')}<b>${esc(log.user || '-')}</b>${icon('phone_android', 'sm')}<span class="muted">${esc(log.device?.trim() || 'Android Device')}</span></p>
    ${log.details?.trim() ? `<p class="ac-details">${esc(log.details)}</p>` : ''}
    ${log.oldValue?.trim() || log.newValue?.trim() ? `<div class="ac-values">${value('Old Value', log.oldValue, 'n-neutral')}${value('New Value', log.newValue, 'n-ok')}</div>` : ''}
  </li>`;
}

function bindAudit(c) {
  const st = auditState(c.page);
  const results = document.getElementById('audit-results');
  // Only the results redraw while typing, so the search box keeps its focus.
  const redraw = () => { results.innerHTML = auditResults(c, st); };
  document.getElementById('audit-q').oninput = e => { st.query = e.target.value; redraw(); };
  main.querySelectorAll('[data-audit-filter]').forEach(b => {
    b.onclick = () => {
      st.filter = b.dataset.auditFilter;
      main.querySelectorAll('[data-audit-filter]').forEach(x => x.setAttribute('aria-pressed', x === b));
      redraw();
    };
  });
}

/* ---------- Issues (IssuesTab, IssueCard), read-only ---------- */

const issueState = page => (page.issues ||= { query: '', filter: 'All' });

function issuesView(c) {
  const st = issueState(c.page);
  return `<label class="field issue-q">Search<input type="search" id="issue-q" placeholder="Search issues or actions taken..." value="${esc(st.query)}" autocomplete="off"></label>
    <div class="chips" role="group" aria-label="Filter issues">${L.ISSUE_FILTERS.map(f => `<button data-issue-filter="${f}" aria-pressed="${f === st.filter}">${f}</button>`).join('')}</div>
    <div id="issue-results">${issueResults(c, st)}</div>`;
}

function issueResults(c, st) {
  // ProjectDao lists issues newest logged first.
  const issues = c.of('project_issues').sort((a, b) => (Number(b.timestamp) || 0) - (Number(a.timestamp) || 0))
    .filter(i => L.issueMatches(i, st.query, st.filter, c.now));
  if (!issues.length) return `<div class="card empty">${icon('warning')}<p>${st.query || st.filter !== 'All' ? 'No matching issues found' : 'No Issues Logged'}</p></div>`;
  return `<ul class="issue-cards">${issues.map(i => issueCard(i, c.now)).join('')}</ul>`;
}

function issueCard(i, now) {
  const overdue = L.isOverdueIssue(i, now), critical = L.isCriticalIssue(i), status = i.status || 'Pending';
  const level = /resolved/i.test(status) ? 'ok' : /critical/i.test(status) ? 'behind' : 'watch';
  const block = (label, text, cls = '') => (text?.trim() ? `<h4 class="${cls}">${esc(label)}</h4><p>${esc(text)}</p>` : '');
  return `<li class="card issue-card${critical ? ' critical' : ''}">
    <div class="ic-head"><p>${icon('event', 'sm')}<span>${esc(L.displayDate(i.date))}${i.targetDate?.trim()
      ? `<span class="${overdue ? 'behind' : 'muted'}"> · Target ${esc(L.displayDate(i.targetDate.trim()))}${overdue ? ' (OVERDUE)' : ''}</span>` : ''}</span></p>
      <div class="ic-badges">${critical ? '<span class="badge lv-behind">CRITICAL</span>' : ''}<span class="badge lv-${level}">${esc(status.toUpperCase())}</span></div></div>
    ${block('Issue description', i.description || '-')}
    ${block(`Action taken${i.actionBy?.trim() ? ` by ${i.actionBy.trim()}` : ''}`, i.actionTaken, 'link')}
    ${block('Remarks / notes', i.remarks)}
    ${i.loggedBy?.trim() ? `<p class="muted small logged">Logged by: ${esc(i.loggedBy)}</p>` : ''}
  </li>`;
}

function bindIssues(c) {
  const st = issueState(c.page);
  const results = document.getElementById('issue-results');
  // Only the results redraw while typing, so the search box keeps its focus.
  const redraw = () => { results.innerHTML = issueResults(c, st); };
  document.getElementById('issue-q').oninput = e => { st.query = e.target.value; redraw(); };
  main.querySelectorAll('[data-issue-filter]').forEach(b => {
    b.onclick = () => {
      st.filter = b.dataset.issueFilter;
      main.querySelectorAll('[data-issue-filter]').forEach(x => x.setAttribute('aria-pressed', x === b));
      redraw();
    };
  });
}

/** PhotoGalleryTab: one album per reporting week, in report order. */
function photosView({ weekly }) {
  const albums = weekly.map(r => [r, L.attachmentList(r.attachedPhotoUrlsJson)]).filter(([, p]) => p.length);
  const total = albums.reduce((t, [, p]) => t + p.length, 0);
  if (!total) return `<div class="card empty">${icon('photo_library')}<p>No site update photos uploaded yet</p><small>Update photos attached to weekly reports will appear here.</small></div>`;
  return `<h2 class="list-title">Project update photo gallery (${plural(total, 'photo')})</h2>${albums.map(([r, refs]) => `
    <section class="card section album"><div class="section-head">${icon('photo_library')}<h2>${esc(r.reportingWeek)} · ${plural(refs.length, 'photo')}</h2></div>
      <p class="muted">By ${esc(r.submittedByStaff || '-')}</p>${photoGrid(refs, L.photoDetails(r.photoDetailsJson))}</section>`).join('')}`;
}

/* ---------- Payments (MoneySection, PaymentCard) ---------- */

function paymentsBody({ project, payments, cost, billed }) {
  const actual = Number(project.actualAccomplishment) || 0;
  const summary = section('Billing summary', 'payments', `
    ${kv('Current contract cost', L.formatPhp(cost))}
    ${kv('Total billed', payments.length ? L.formatPhp(billed) : 'No billings yet')}
    ${kv('Balance', L.formatPhp(cost - billed))}
    <div class="two-bars">${progressLine('Billed', L.billedLabel(billed, cost), '', 'muted', cost > 0 ? billed / cost : 0, 'billed')}${progressLine('Work done', pct(actual), '', 'muted', actual / 100, 'neutral')}</div>`);
  if (!payments.length) return `<div class="stack">${summary}<div class="empty-ledger">${icon('receipt_long')}<p>No billings yet</p></div></div>`;
  return `<div class="stack">${summary}<ul class="pay-cards">${payments.map(p => {
    const gross = Number(p.grossAmount) || 0, retention = Number(p.retentionPct) || 0, units = Number(p.units) || 0;
    return `<li class="card pay-card">
      <div class="pc-head"><div><h3>${esc(p.name || '-')}</h3><p class="muted">DV ${esc(p.dvNo || 'N/A')}</p></div>
        ${p.fileUrl ? fileLink(p.fileUrl, 'Scanned DV') : ''}</div>
      <p class="muted small">${esc(L.displayDate(p.date || '-'))} · ${esc(p.periodCovered || '-')}</p>
      ${p.billingType ? `<p class="muted small">${esc(p.billingType)} · ${units} ${L.billingUnitName(p.billingType)}${units === 1 ? '' : 's'} × ${esc(L.formatPhp(Number(p.unitPrice) || 0))}</p>` : ''}
      ${kv('Net amount', L.formatPhp(L.netAmount(p)))}
      ${retention > 0 ? kv('Gross amount', L.formatPhp(gross)) + kv(`Retention (${Math.trunc(retention)}%)`, L.formatPhp(gross - L.netAmount(p))) : ''}
      ${kv('Share of contract', pct(p.percentage))}
      ${kv('Balance after', L.formatPhp(Number(p.balanceAmount) || 0))}
      ${kv('Balance share', pct(p.balancePercentage))}
    </li>`;
  }).join('')}</ul></div>`;
}

/* ---------- Documents (DocumentsSection, ContractorLettersCard, DeveloperRequestsCard) ---------- */

/** LogEntry: a title and its lines (blank ones skipped), with any attached file under them. */
const entry = (title, lines, extra = '', cls = '') => `<div class="entry ${cls}"><b>${esc(title)}</b>${lines.filter(x => x && x.trim()).map(x => `<span>${esc(x)}</span>`).join('')}${extra}</div>`;
const orderEntry = (d, cls = '') => entry(L.orderLabel(d.documentName), [`Status: ${d.status || ''}`, L.withEnd(d.remarks)], d.fileUrl ? fileLink(d.fileUrl, 'Attached copy') : '', cls);
const ordersCard = (title, ic, items, emptyText) => section(title, ic, items || emptyLine(emptyText));

function documentsBody(c, chip) {
  const { of, documents, now } = c;
  if (chip === 'Audit log') return auditView(c);
  const named = text => documents.filter(d => (d.documentName || '').toLowerCase().includes(text));
  if (chip === 'Time Extensions') return ordersCard('Time extensions', 'more_time', named('time extension').map(d => orderEntry(d)).join(''), 'No time extensions.');
  if (chip === 'Variation Orders') {
    const vos = named('variation order');
    return ordersCard(`Variation orders (${vos.length})`, 'receipt_long', vos.map(vo => entry(vo.documentName, L.variationLines(vo), vo.fileUrl ? fileLink(vo.fileUrl, 'Attached copy') : '')).join(''), 'No variation orders recorded.');
  }
  if (chip === 'Letters to Contractor') return lettersCard(L.mergedLetters(of('contractor_letters'), documents));
  if (chip === 'Developer Requests') return requestsCard(of('developer_requests'), now);
  if (chip === 'Related Files') return relatedCard(L.relatedFiles(documents));
  const suspensions = documents.filter(L.isSuspension);
  const chains = suspensions.map(ws => [ws, L.continuances(ws, documents)]);
  // A continuance whose suspension was deleted still shows, at the end.
  const listed = new Set(chains.flatMap(([ws, list]) => [ws, ...list]));
  const orphans = named('work suspension').filter(d => !listed.has(d));
  return `<div class="stack">
    ${ordersCard('Work suspension orders', 'pause_circle', chains.map(([ws, list]) => orderEntry(ws) + list.map(d => orderEntry(d, 'nested')).join('')).join('') + orphans.map(d => orderEntry(d)).join(''), 'No work suspension orders.')}
    ${ordersCard('Work resumption orders', 'play_circle', named('work resumption').map(d => orderEntry(d)).join(''), 'No work resumption orders.')}
  </div>`;
}

/** ContractorLettersCard as a table: date and receipt, subject, the contractor's action, and the reply letter. */
function lettersCard(letters) {
  if (!letters.length) return section('Letters to contractor', 'mail', emptyLine('No letters to the contractor yet.'));
  return section('Letters to contractor', 'mail', table(['Date', 'Subject', 'Action taken by contractor', 'Reply'], letters.map(l => {
    const received = l.dateReceived ? `Received ${L.displayDate(l.dateReceived)}` : null;
    const receipt = l.receivedFileUrl ? fileLink(l.receivedFileUrl, received || 'Receiving copy') : `<small class="${received ? 'muted' : 'n-watch'}">${esc(received || 'Not received')}</small>`;
    const reply = l.replyFileUrl ? fileLink(l.replyFileUrl, 'Reply') : l.dateReplied ? '<small class="muted">Replied</small>' : '<span class="muted">-</span>';
    return `<tr><td class="nw">${esc(L.displayDate(l.dateSent || '-'))}<br>${receipt}</td><td>${esc(l.subject || '-')}</td>
      <td>${l.actionTaken ? esc(l.actionTaken) : '<span class="n-watch">None yet</span>'}</td><td>${reply}</td></tr>`;
  }).join('')));
}

/** DeveloperRequestsCard: requests not yet packaged stand out, with the days since NHA received them. */
function requestsCard(requests, now) {
  return section('Developer requests', 'move_to_inbox', requests.length ? requests.map(r => {
    const waiting = ISO.test(r.dateReceived || '') ? Math.round((Date.parse(now) - Date.parse(r.dateReceived)) / 86400000) : null;
    return entry(r.requestType || '-', [
      `Requested: ${r.dateRequested || '-'}  ·  Received: ${r.dateReceived || '-'}`,
      r.datePackaged ? `Packaged: ${r.datePackaged}` : `NOT YET PACKAGED${waiting == null ? '' : ` · ${waiting} days since received`}`,
      r.details,
    ], r.requestFileUrl ? fileLink(r.requestFileUrl, 'Request letter') : '', r.datePackaged ? '' : 'waiting');
  }).join('') : emptyLine('No requests from the developer.'));
}

/** RelatedFilesList: other project files with their status and attached copy. */
function relatedCard(files) {
  const level = st => (st === 'Approved' ? 'ok' : st === 'Notarized' || st === 'Signed' ? 'primary' : st === 'Submitted' ? 'watch' : 'neutral');
  return section(`Related files (${files.length})`, 'folder_open', files.length ? files.map(d => entry(d.documentName, [d.remarks || 'N/A'],
    `<span class="badge lv-${level(d.status)}">${esc(d.status || 'Pending')}</span>${d.fileUrl ? fileLink(d.fileUrl, 'Attached copy') : '<small class="muted">No copy attached</small>'}`)).join('')
    : emptyLine('No related project files or scanned documents uploaded yet.'));
}

/* ---------- Weekly report print (BlueBookForms.weeklyReport on an A4 sheet) ---------- */

async function drawPrint(c) {
  if (/^\d{4}-\d{2}$/.test(c.page.sub || '')) return drawMonthlyPrint(c);
  if (c.page.sub === 'sdp') return drawSdpPrint(c);
  const { page, project, of, documents, weekly } = c;
  const report = weekly.find(r => r.uuid === page.sub);
  const back = `<a class="text-btn back" href="#/p/${esc(page.id)}/progress">${icon('arrow_back', 'sm')}Weekly reports</a>`;
  if (!report) { main.innerHTML = `${back}<p class="muted">This weekly report was deleted or does not exist.</p>`; return; }
  // The file name "Save as PDF" offers, as the phone names it (BlueBookPrint.pdfName).
  document.title = `Weekly Report - ${(report.reportingWeek || '').replace(/^Week \d+: /, '')}`;
  const token = page.printToken = {};
  main.innerHTML = `<div class="print-bar no-print">${back}<button class="btn" id="do-print" disabled>${icon('print', 'sm')}Preparing photos…</button></div>
    <p class="muted no-print print-note">A4, as the phone prints it. Choose "Save as PDF" in the print window to keep a copy.</p>
    <div class="bb-scroll"><div class="bb" id="sheet"></div></div>`;
  // Photos are the report's uploaded images (PDFs and files not uploaded yet are left out, as on the phone).
  const titles = L.photoDetails(report.photoDetailsJson);
  const refs = L.attachmentList(report.attachedPhotoUrlsJson).filter(x => !L.isPdf(x));
  const photos = (await Promise.all(refs.map(async x => [await photoUrl(x), titles[x]?.title || '']))).filter(([src]) => src);
  if (page.printToken !== token || page !== s.page) return;
  const sheet = document.getElementById('sheet');
  sheet.innerHTML = P.weeklyReportHtml({
    project, report, docs: documents, issues: of('project_issues'), photos, banner: 'blue-book-header.png',
    letters: L.mergedLetters(of('contractor_letters'), documents), requests: of('developer_requests'),
    signatories: P.weeklySignatories(project, s.profile),
  });
  await Promise.all([...sheet.querySelectorAll('img')].map(img => img.decode().catch(() => {})));
  if (page.printToken !== token) return;
  const btn = document.getElementById('do-print');
  btn.disabled = false;
  btn.innerHTML = `${icon('print', 'sm')}Print`;
  btn.onclick = () => window.print();
}

/** The monthly form (BlueBookForms.monthlyReport) for `#/p/<id>/print/2026-09`. */
async function drawMonthlyPrint({ page, project, of, documents, payments, weekly }) {
  const month = P.monthName(page.sub);
  page.month = month; page.chips.progress = 'Monthly'; // the back link returns to this month
  document.title = `Monthly Report - ${month}`;
  const token = page.printToken = {};
  main.innerHTML = `<div class="print-bar no-print"><a class="text-btn back" href="#/p/${esc(page.id)}/progress">${icon('arrow_back', 'sm')}Monthly reports</a>
      <button class="btn" id="do-print" disabled>${icon('print', 'sm')}Preparing…</button></div>
    <p class="muted no-print print-note">A4, as the phone prints it. Choose "Save as PDF" in the print window to keep a copy.</p>
    <div class="bb-scroll"><div class="bb" id="sheet"></div></div>`;
  const sheet = document.getElementById('sheet');
  sheet.innerHTML = P.monthlyReportHtml({
    project, month: page.sub, weeks: L.monthReports(weekly, month), payments, issues: of('project_issues'), docs: documents,
    // shortcut: weather comes only from the logs synced with weekly reports; the phone also counts days not in a report.
    weather: weekly.flatMap(r => (Array.isArray(r._weather) ? r._weather : [])),
    saved: of('monthly_reports').find(r => (r.reportingMonth || '').trim().toLowerCase() === month.toLowerCase()),
    signatories: P.monthlySignatories(project, s.profile), banner: 'blue-book-header.png',
  });
  await Promise.all([...sheet.querySelectorAll('img')].map(img => img.decode().catch(() => {})));
  if (page.printToken !== token) return;
  const btn = document.getElementById('do-print');
  btn.disabled = false;
  btn.innerHTML = `${icon('print', 'sm')}Print`;
  btn.onclick = () => window.print();
}

/** The Status of Construction print (PrintPlanDialog + BlueBookForms.sdpPlan) for `#/p/<id>/print/sdp`. */
async function drawSdpPrint({ page, project, records }) {
  const sites = L.sdpSites(records);
  // PrintPlanDialog: several sites start as all sites on a landscape page; the choice stays once made.
  const st = (page.sdp ||= {});
  const all = st.all ?? sites.length > 1, landscape = st.landscape ?? sites.length > 1;
  const current = sites.find(x => x.site === page.lots?.site) || sites[0];
  const chosen = all && sites.length > 1 ? sites : current ? [current] : [];
  const many = chosen.length > 1;
  page.chips.progress = 'Lots'; // the back link returns to the Lots view
  document.title = `SDP Progress - ${project.name}`;
  const token = page.printToken = {};
  const opt = (value, label, on) => `<option value="${value}"${on ? ' selected' : ''}>${esc(label)}</option>`;
  main.innerHTML = `${landscape ? '<style>@page { size: A4 landscape; }</style>' : ''}
    <div class="print-bar no-print"><a class="text-btn back" href="#/p/${esc(page.id)}/progress">${icon('arrow_back', 'sm')}Lots</a>
      <div class="print-opts">
        ${sites.length > 1 ? `<label class="field">Sites<select id="sdp-sites">${opt('all', `All ${sites.length} sites on one page`, all)}${opt('one', `${current.site || 'Main site'} only`, !all)}</select></label>` : ''}
        <label class="field">Page<select id="sdp-page">${opt('l', many ? 'Landscape: sites side by side' : 'Landscape', landscape)}${opt('p', many ? 'Portrait: sites one above the other' : 'Portrait', !landscape)}</select></label>
        <button class="btn" id="do-print" disabled>${icon('print', 'sm')}Drawing the plan…</button></div></div>
    <p class="muted no-print print-note">A4, as the phone prints it. Choose "Save as PDF" in the print window to keep a copy.</p>
    <p class="error no-print" id="sdp-note" hidden></p>
    <div class="bb-scroll"><div class="bb full${landscape ? ' land' : ''}" id="sheet"></div></div>`;
  const sitesSel = document.getElementById('sdp-sites');
  if (sitesSel) sitesSel.onchange = () => { st.all = sitesSel.value === 'all'; if (st.all) st.landscape = true; drawProject(page); };
  document.getElementById('sdp-page').onchange = e => { st.landscape = e.target.value === 'l'; drawProject(page); };
  const sheet = document.getElementById('sheet');
  if (!chosen.length) { sheet.innerHTML = '<p>No subdivision plan uploaded yet.</p>'; return; }
  let plans;
  try { plans = await Promise.all(chosen.map(planImage)); } catch (e) {
    console.error('SDP print', e);
    if (page.printToken === token) sheet.innerHTML = `<p>The plan could not be drawn (${esc(e.message || e)}).</p>`;
    return;
  }
  if (page.printToken !== token || page !== s.page) return;
  // The form still prints when a plan PDF cannot be loaded: its lots are drawn on blank paper (the phone prints nothing then).
  const missing = chosen.filter((x, i) => plans[i].problem);
  if (missing.length) {
    const note = document.getElementById('sdp-note');
    note.hidden = false;
    note.textContent = `The plan PDF of ${missing.map(x => x.site || 'Main site').join(' and ')} could not be loaded in this browser (${plans.find(x => x.problem).problem}), so ${missing.length > 1 ? 'their' : 'its'} lots are drawn without the plan. Try again, or another browser.`;
  }
  sheet.innerHTML = P.sdpPlanHtml({
    project, landscape, asOf: today(), banner: 'blue-book-header.png',
    sheets: chosen.map((x, i) => ({ site: x.site, image: plans[i].image, summary: L.sdpSummary(x.lots) })),
  });
  // Wait for load, not decode(): decode() of a big data-URI plan can stay pending in a tab that is not painting.
  await Promise.all([...sheet.querySelectorAll('img')].map(img => (img.complete ? null : new Promise(done => { img.onload = img.onerror = done; }))));
  if (page.printToken !== token) return;
  const btn = document.getElementById('do-print');
  btn.disabled = false;
  btn.innerHTML = `${icon('print', 'sm')}Print`;
  btn.onclick = () => window.print();
}

/** The plan PDF's bytes: a plain download first, then the Storage SDK's own request if the browser refuses that one. */
async function planBytes(refText) {
  const path = L.storagePathOf(refText);
  if (!path) throw new Error('the PDF is not uploaded yet');
  try {
    const url = await photoUrl(refText);
    if (!url) throw new Error('the PDF is not uploaded yet');
    // no-store: a copy cached when the PDF was opened from its link may lack the CORS header the browser needs here.
    // Range: download managers such as IDM take over a plain PDF download (the page then gets "Failed to fetch"),
    // but leave a partial (206) answer alone; bytes=0- is still the whole file.
    const res = await fetch(url, { cache: 'no-store', headers: { Range: 'bytes=0-' } });
    if (!res.ok) throw new Error(`download failed, ${res.status}`);
    return await res.arrayBuffer();
  } catch (e) {
    console.error('SDP print: the plan PDF download failed; trying the Storage SDK', e);
    // The SDK retries a refused download for up to two minutes; give up sooner and print without the plan.
    return Promise.race([getBytes(ref(storage, path)), new Promise((_, fail) => setTimeout(() => fail(new Error(`download refused: ${e.message}`)), 15000))]);
  }
}

let pdfjs;
/**
 * ProjectViewModel.sdpPlanImage: the plan's first page inside its frame (saved, or around all lots) at about 300 dpi
 * across A4, each lot filled with its colour and outlined, turned the way the plan is turned on screen.
 * Returns {image: PNG data URI, problem}; when the PDF cannot be loaded, `problem` says why and the lots are drawn alone.
 */
async function planImage({ plan, lots }) {
  let doc = null, problem = '';
  try {
    const data = await planBytes(plan.pdfFileUrl);
    pdfjs ||= await import('./vendor/pdfjs/pdf.min.mjs');
    pdfjs.GlobalWorkerOptions.workerSrc = 'vendor/pdfjs/pdf.worker.min.mjs';
    // shortcut: no CJK cmaps are bundled; a plan with CJK text in non-embedded fonts would draw that text wrong.
    doc = await pdfjs.getDocument({ data, isEvalSupported: false, standardFontDataUrl: 'vendor/pdfjs/standard_fonts/' }).promise;
  } catch (e) {
    console.error('SDP print: the plan PDF could not be loaded', e);
    problem = e.code || e.message || String(e);
  }
  try {
    const pdfPage = doc && await doc.getPage(1);
    // shortcut: without the PDF its page size is unknown, so the lots go on an A4 landscape page; shapes may stretch.
    const base = pdfPage ? pdfPage.getViewport({ scale: 1 }) : { width: 842, height: 595 };
    const polys = lots.map(l => [l, L.lotPolygon(l)]);
    const f = L.parseFrame(plan.frame) || L.frameAround(polys.map(p => p[1])) || [0, 0, 1, 1];
    const fw = (f[2] - f[0]) * base.width, fh = (f[3] - f[1]) * base.height;
    const sc = 2800 / Math.max(fw, fh);
    const w = Math.max(1, Math.trunc(fw * sc)), h = Math.max(1, Math.trunc(fh * sc));
    const canvas = Object.assign(document.createElement('canvas'), { width: w, height: h });
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, w, h);
    if (pdfPage) {
      const viewport = pdfPage.getViewport({ scale: sc, offsetX: -f[0] * base.width * sc, offsetY: -f[1] * base.height * sc });
      await pdfPage.render({ canvasContext: ctx, viewport, intent: 'print' }).promise;
    }
    ctx.lineWidth = 2;
    for (const [l, pts] of polys) {
      if (pts.length < 3) continue;
      ctx.beginPath();
      pts.forEach(([x, y], i) => ctx[i ? 'lineTo' : 'moveTo']((x - f[0]) / (f[2] - f[0]) * w, (y - f[1]) / (f[3] - f[1]) * h));
      ctx.closePath();
      ctx.fillStyle = ctx.strokeStyle = L.lotPrintHex(l);
      ctx.globalAlpha = 115 / 255;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.stroke();
    }
    const deg = ((Number(plan.rotation) || 0) % 360 + 360) % 360;
    if (!deg) return { image: canvas.toDataURL('image/png'), problem };
    const rad = deg * Math.PI / 180, cos = Math.abs(Math.cos(rad)), sin = Math.abs(Math.sin(rad));
    const out = Object.assign(document.createElement('canvas'), { width: Math.round(w * cos + h * sin), height: Math.round(w * sin + h * cos) });
    const o = out.getContext('2d');
    o.fillStyle = '#fff';
    o.fillRect(0, 0, out.width, out.height);
    o.translate(out.width / 2, out.height / 2);
    o.rotate(rad); // clockwise, as Matrix.postRotate on the phone
    o.drawImage(canvas, -w / 2, -h / 2);
    return { image: out.toDataURL('image/png'), problem };
  } finally {
    doc?.destroy();
  }
}

/* ---------- Files and site photos from Storage ---------- */

const photoUrls = new Map();
/** Resolves an `nhafile:` ref to a download URL once; a missing file or no read access gives null. */
function photoUrl(refText) {
  const path = L.storagePathOf(refText);
  if (!path) return Promise.resolve(null);
  if (!photoUrls.has(path)) photoUrls.set(path, getDownloadURL(ref(storage, path)).catch(() => null));
  return photoUrls.get(path);
}

async function showPhoto(refText, box) {
  const url = await photoUrl(refText);
  if (url && box?.isConnected) { box.classList.add('has'); box.innerHTML = `<img src="${esc(url)}" alt="Latest site photo">`; }
}

function loadPhotos(root) {
  root.querySelectorAll('.photo[data-ref]').forEach(async box => {
    const url = await photoUrl(box.dataset.ref);
    if (url && box.isConnected) box.innerHTML = `<img src="${esc(url)}" alt="" loading="lazy">`;
  });
}

/** Fills in each attached-file link and thumbnail; one Storage cannot give says so. */
function loadFiles(root) {
  root.querySelectorAll('[data-file]').forEach(async a => {
    const url = await photoUrl(a.dataset.file);
    if (!a.isConnected) return;
    if (!url) {
      a.classList.add('gone');
      a.title = 'This file is not in the cloud, or this account cannot open it.';
      if (a.classList.contains('thumb')) a.insertAdjacentHTML('beforeend', '<small>Not available</small>');
      return;
    }
    a.href = url;
    if (a.classList.contains('thumb')) a.innerHTML = `<img src="${esc(url)}" alt="${esc(a.dataset.alt || '')}" loading="lazy">`;
  });
}
