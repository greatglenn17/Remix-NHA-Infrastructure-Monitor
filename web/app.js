// Web viewer. Screens mirror the Android app (ui/dashboard, ui/detail). It writes only a new or edited issue, a weekly report's weather (saveEdit),
// made the way RecordSync writes so the phones take it, a new account's profile (signUp), your own name, position and
// office (editProfile), and the Super Admin's approvals and roles on the Users page (setUser).
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js';
import {
  initializeAuth, indexedDBLocalPersistence, browserLocalPersistence, connectAuthEmulator, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail,
  createUserWithEmailAndPassword,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import {
  getFirestore, connectFirestoreEmulator, collection, doc, getDoc, setDoc, updateDoc, query, where, onSnapshot, runTransaction, writeBatch,
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js';
import { getStorage, connectStorageEmulator, ref, getDownloadURL, getBytes, uploadBytes } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js';
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
  payments: new Map(), docs: new Map(), requests: new Map(), weekly: new Map(), unsub: [], unsubPage: [], page: null,
  filter: null, mineOnly: false, q: '',
};

/** Escapes text for HTML; every value from Firestore goes through it. */
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, cls = '') => `<span class="icon ${cls}" aria-hidden="true">${name}</span>`;
const today = () => new Date().toLocaleDateString('en-CA'); // yyyy-MM-dd in the viewer's time zone
const pct = n => `${(Number(n) || 0).toFixed(2)}%`;
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
  // signUp signs the new account in and out again; the sign-in screen stays until it is done.
  if (user && signingUp) return;
  s.unsub.forEach(u => u()); s.unsub = [];
  s.unsubPage.forEach(u => u()); s.unsubPage = [];
  s.user = user; s.projects = null; s.payments.clear(); s.docs.clear(); s.requests.clear(); s.weekly.clear(); s.page = null; s.profile = {};
  document.body.classList.toggle('signed-out', !user);
  if (!user) {
    who.innerHTML = '';
    const after = afterSignUp; afterSignUp = null;
    if (after) return renderSignIn('', after.note, after.email);
    // A failed sign-up signs out too; its form and message stay.
    return document.getElementById('signup') ? undefined : renderSignIn();
  }
  // Fresh token: the role claim may have changed since this browser last signed in.
  s.claims = (await user.getIdTokenResult(true)).claims;
  // Name and position for the weekly form's "Prepared by" when the project has none saved (signatoriesFor).
  getDoc(doc(db, 'users', user.uid)).then(d => { s.profile = { name: d.get('displayName') || '', title: d.get('position') || '', office: d.get('office') || '' }; }).catch(() => {});
  who.innerHTML = `
    <span class="email">${esc(user.email)}</span>
    <span class="badge view">${icon(L.isWriter(s.claims) ? 'edit' : 'visibility', 'sm')}${esc(ROLE_LABELS[s.claims.role] || 'No role yet')} · ${L.isWriter(s.claims) ? 'can edit issues' : 'read only'}</span>
    ${s.claims.role === 'SUPER_ADMIN' ? `<a class="text-btn" href="#/users">${icon('manage_accounts', 'sm')}Users</a>` : ''}
    <button id="profile" class="text-btn">${icon('person', 'sm')}Profile</button>
    <button id="out" class="text-btn">${icon('logout', 'sm')}Sign out</button>`;
  document.getElementById('out').onclick = () => signOut(auth);
  document.getElementById('profile').onclick = editProfile;
  s.unsub.push(onSnapshot(scoped('projects'), snap => {
    s.projectsError = null;
    s.projects = snap.docs.map(d => ({ ...d.data(), projectUuid: d.id }))
      .filter(p => p.isDeleted !== true && p.name);
    redrawDashboard();
  }, e => { s.projectsError = e.message; redrawDashboard(); }));
  s.unsub.push(listenRecords('project_payments', s.payments), listenRecords('pending_documents', s.docs),
    // For the attention flags: requests not yet packaged, and the newest weekly report (no field update in 14 days).
    listenRecords('developer_requests', s.requests), listenRecords('weekly_reports', s.weekly));
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
  if (location.hash === '#/users' && s.claims.role === 'SUPER_ADMIN') return renderUsers();
  if (m) renderProject(m[1], m[2] || 'summary', m[3] || null); else renderDashboard();
}

/* ---------- Sign in (LoginScreen: brand gradient hero beside the form) ---------- */

/** Messages for errors that say nothing about whether an account exists; null for the rest. */
const authTrouble = err => (err.code === 'auth/too-many-requests' ? 'Too many tries. Wait a few minutes, then try again.'
  : err.code === 'auth/network-request-failed' ? 'No internet connection. Check it and try again.' : null);

function renderSignIn(message = '', note = '', email = '') {
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
        <label class="field">Email address <input name="email" type="email" autocomplete="username" value="${esc(email)}" required></label>
        <label class="field">Password <input name="password" type="password" autocomplete="current-password" required></label>
        <p class="error" role="alert">${esc(message)}</p>
        ${note ? `<p class="note" role="status">${esc(note)}</p>` : ''}
        <button type="submit" class="btn">Sign in</button>
        <div class="row"><button type="button" id="reset" class="text-btn">Forgot password?</button></div>
        <p class="muted switch">Don't have an account? <button type="button" id="to-sign-up" class="text-btn">Create account</button></p>
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
  document.getElementById('to-sign-up').onclick = () => renderSignUp();
  if (email) form.password.focus();
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

let signingUp = false, afterSignUp = null;

/** LoginScreen in sign-up mode: the same fields, and only the Field Engineer and Viewer roles. */
function renderSignUp(message = '', f = { role: 'FIELD_ENGINEER' }) {
  const v = name => esc(f[name] || '');
  const role = (value, title, sub) => `<label class="check role"><input type="radio" name="role" value="${value}"${f.role === value ? ' checked' : ''}>
    <span><b>${title}</b><small class="muted">${sub}</small></span></label>`;
  main.innerHTML = `
    <div class="signin">
      <section class="hero">
        <img class="hero-logo" src="logo.svg" alt="" width="72" height="72">
        <h1>National Housing Authority</h1>
        <p>Infrastructure Monitoring System</p>
        <small>REGION III • BULACAN DISTRICT OFFICE</small>
        <p class="unofficial">Unofficial app. Not issued or endorsed by the National Housing Authority. Made for the Bulacan District Office; other district offices are welcome to try it.</p>
      </section>
      <form id="signup" class="signin-form" novalidate>
        <button type="button" id="to-sign-in" class="text-btn back">${icon('arrow_back', 'sm')}Back to sign in</button>
        <h2>Create account</h2>
        <p class="muted">The same account works in the NHA Monitor app.${emulator ? ' <b>Local emulator.</b>' : ''}</p>
        <div class="name-row">
          <label class="field">First name <input name="firstName" autocomplete="given-name" value="${v('firstName')}" required></label>
          <label class="field mi">M.I. <input name="middleInitial" maxlength="3" autocomplete="additional-name" value="${v('middleInitial')}"></label>
          <label class="field">Last name <input name="lastName" autocomplete="family-name" value="${v('lastName')}" required></label>
        </div>
        <label class="field">Email address <input name="email" type="email" autocomplete="email" value="${v('email')}" required></label>
        <label class="field">Password <input name="password" type="password" autocomplete="new-password" minlength="6" required></label>
        <label class="field">Retype password <input name="confirm" type="password" autocomplete="new-password" required></label>
        <label class="field">Position / designation (optional) <input name="position" autocomplete="organization-title" placeholder="e.g. Supervising Project Engineer" value="${v('position')}"></label>
        <label class="field">Office / department / region (optional) <input name="office" autocomplete="organization" placeholder="e.g. NHA Regional Office III" value="${v('office')}"></label>
        <fieldset class="roles"><legend>Initial user role</legend>
          ${role('FIELD_ENGINEER', 'Field Engineer', 'Reports, payments, weather and issues')}
          ${role('VIEWER', 'Viewer', 'Read-only dashboard')}
        </fieldset>
        <p class="muted small">Engineer Admin access is given by the Super Admin after you register. Adding and editing start once the Super Admin approves your account.</p>
        <p class="error" role="alert">${esc(message)}</p>
        <button type="submit" class="btn">Create account</button>
      </form>
    </div>`;
  const form = document.getElementById('signup');
  document.getElementById('to-sign-in').onclick = () => renderSignIn('', '', form.email.value.trim());
  form.onsubmit = async e => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(form));
    const errors = L.signUpErrors(f);
    if (errors.length) return renderSignUp(errors.join(' '), f);
    form.querySelector('[type=submit]').disabled = true;
    const trouble = await signUp(f);
    if (trouble) renderSignUp(trouble, f);
  };
}

/**
 * ProjectViewModel.authenticateWithFirebase(isSignUp): the Auth account, then its users/{uid} profile (from which
 * syncUserCustomClaims sets the role and alerts the Super Admin), then back to sign in like the app. An account whose
 * profile never saved gets it on the next try. Returns the error to show, or null.
 */
async function signUp(f) {
  const email = f.email.trim().toLowerCase(), password = f.password.trim();
  signingUp = true;
  try {
    let user;
    try {
      ({ user } = await createUserWithEmailAndPassword(auth, email, password));
    } catch (err) {
      if (err.code !== 'auth/email-already-in-use') throw err;
      // The app's retry: the same email and password sign in, and finish a profile an earlier try left unsaved.
      ({ user } = await signInWithEmailAndPassword(auth, email, password).catch(() => ({})));
      if (!user || (await getDoc(doc(db, 'users', user.uid))).exists()) throw err;
    }
    await setDoc(doc(db, 'users', user.uid), L.newProfile(user.uid, f, Date.now()));
    afterSignUp = { email, note: `Account created for ${email}. Type your password to sign in. You can view projects now; adding and editing start once the Super Admin approves your account.` };
    await signOut(auth);
    return null;
  } catch (err) {
    if (auth.currentUser) await signOut(auth).catch(() => {});
    return err.code === 'auth/email-already-in-use' ? 'This email is already registered. Go back and sign in instead.'
      : err.code === 'auth/invalid-email' ? 'Invalid email address format.'
        : err.code === 'auth/weak-password' ? 'Password must be at least 6 characters long.'
          : authTrouble(err) || 'Registration failed. Check your details and try again.';
  } finally {
    signingUp = false;
  }
}

/* ---------- Profile (EditProfileDialog) ---------- */

/** "Update Profile & Position": the name, position and office every phone and printed form shows for this account. */
async function editProfile() {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const done = () => { d.close(); d.remove(); };
  let stored;
  try { stored = await getDoc(doc(db, 'users', s.user.uid)); } catch { stored = null; }
  const v = name => esc(stored?.get(name) || '');
  d.innerHTML = `<form novalidate>
    <h2>Update profile &amp; position</h2>
    <p class="muted">Update your official title, position, and office location below.</p>
    <label class="field">Full name<input name="fullName" autocomplete="name" value="${v('displayName')}" required></label>
    <label class="field">Position / title<input name="position" autocomplete="organization-title" placeholder="e.g. Supervising Project Engineer" value="${v('position')}" required></label>
    <label class="field">Office / department / region<input name="office" autocomplete="organization" placeholder="e.g. NHA Regional Office III" value="${v('office')}" required></label>
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save changes</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  const values = () => ({ name: form.fullName.value.trim(), position: form.position.value.trim(), office: form.office.value.trim() });
  const initial = JSON.stringify(values());
  const close = () => (JSON.stringify(values()) === initial || confirm('Discard your changes to your profile?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values();
    if (!f.name || !f.position || !f.office) { err.textContent = 'Fill in your full name, position and office.'; return; }
    if (JSON.stringify(f) === initial) { done(); return; }
    // An account without a profile gets it from the app; a profile made here would have no role for the claims function.
    if (!stored?.exists()) { err.textContent = 'Sign in to the NHA Monitor app once to set up your profile, then try again.'; return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      // syncUserProfile's fields; role, approval and "See all projects" stay as they are.
      await updateDoc(doc(db, 'users', s.user.uid), { displayName: f.name, position: f.position, office: f.office, updatedAt: Date.now() });
      s.profile = { name: f.name, title: f.position, office: f.office };
      done();
    } catch (x) {
      err.textContent = `Could not save: ${x.message}`;
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/* ---------- Users (ManageUsersDialog), Super Admin only ---------- */

function renderUsers() {
  document.title = 'Users · NHA Monitor';
  main.innerHTML = `
    <a class="text-btn back" href="#/">${icon('arrow_back', 'sm')}All projects</a>
    <div class="users-head"><h1>Manage users &amp; roles</h1>
      <p class="muted">A new role or approval applies the next time that person opens the app or this website.</p></div>
    <p class="note users-msg" role="status" hidden></p>
    <ul class="user-cards" id="users"><li class="empty">Loading users…</li></ul>`;
  const list = document.getElementById('users'), msg = main.querySelector('.users-msg');
  let users = [];
  const say = text => { msg.textContent = text; msg.hidden = !text; };
  s.unsubPage.push(onSnapshot(collection(db, 'users'), snap => {
    users = L.manageableUsers(snap.docs.map(d => L.userRow(d.id, d.data())));
    list.innerHTML = users.length ? users.map(userCard).join('') : '<li class="empty">No other users have registered yet.</li>';
  }, e => { list.innerHTML = `<li class="error">Could not load users: ${esc(e.message)}</li>`; }));
  list.onclick = e => {
    const b = e.target.closest('button[data-uid]');
    const user = b && users.find(u => u.uid === b.dataset.uid);
    if (!user || b.getAttribute('aria-pressed') === 'true') return;
    if (b.dataset.approve !== undefined) setUser(user, { approved: true }, `${user.name} is approved. It applies the next time they open the app.`, say);
    else setUser(user, { role: b.dataset.role }, `${user.name} is now ${ROLE_LABELS[b.dataset.role]}. It applies the next time they open the app.`, say);
  };
  list.onchange = e => {
    const user = users.find(u => u.uid === e.target.dataset.seesAll);
    if (!user) return;
    const on = e.target.checked;
    setUser(user, { seesAllProjects: on }, on ? `${user.name} can now open every project, view only. It applies the next time they open the app.`
      : `${user.name} now sees only their own projects. It applies the next time they open the app.`, say);
  };
}

/** One account: approval, the four roles and "See all projects" (not for a Super Viewer, who already sees all). */
function userCard(u) {
  const role = u.role || 'FIELD_ENGINEER'; // as the claims function reads a profile without one
  const about = [u.position, u.office].filter(Boolean).join(' · ');
  return `<li class="card user-card">
    <div class="uc-who"><b>${esc(u.name)}</b>
      ${u.email && u.email !== u.name ? `<span class="muted small">${esc(u.email)}</span>` : ''}
      ${about ? `<span class="muted small">${esc(about)}</span>` : ''}</div>
    ${u.approved ? '' : `<div class="uc-approve"><span class="waiting">Waiting for approval: can only view</span>
      <button class="btn" data-uid="${esc(u.uid)}" data-approve>Approve</button></div>`}
    <div class="seg" role="group" aria-label="Role of ${esc(u.name)}">${L.ASSIGNABLE_ROLES.map(r =>
      `<button data-uid="${esc(u.uid)}" data-role="${r}" aria-pressed="${r === role}">${ROLE_LABELS[r]}</button>`).join('')}</div>
    ${role === 'SUPER_VIEWER' ? '' : `<label class="check"><input type="checkbox" data-sees-all="${esc(u.uid)}"${u.seesAllProjects ? ' checked' : ''}>
      <span>See all projects <small class="muted">Others' projects open view only</small></span></label>`}
  </li>`;
}

/** ProjectViewModel.approveUser / setUserRole / setUserSeesAll: one users/{uid} update; syncUserCustomClaims does the rest. */
async function setUser(user, change, done, say) {
  if (!navigator.onLine) return say('You are offline. Connect to the internet, then try again.');
  main.querySelectorAll(`[data-uid="${CSS.escape(user.uid)}"], [data-sees-all="${CSS.escape(user.uid)}"]`).forEach(el => { el.disabled = true; });
  try {
    await updateDoc(doc(db, 'users', user.uid), L.userChange(user, change));
    say(done);
  } catch (err) {
    say(`Could not change it: ${err.message}`);
    // The listener only redraws on a change, so put the controls back as they were.
    main.querySelectorAll(`[data-uid="${CSS.escape(user.uid)}"], [data-sees-all="${CSS.escape(user.uid)}"]`).forEach(el => { el.disabled = false; });
    const box = main.querySelector(`[data-sees-all="${CSS.escape(user.uid)}"]`);
    if (box) box.checked = user.seesAllProjects;
  }
}

/* ---------- Dashboard (DashboardScreen + PortfolioCounts + PortfolioRow) ---------- */

/** One project as the dashboard sees it (ProjectViewModel's PortfolioItem). */
function itemOf(project) {
  const docs = s.docs.get(project.projectUuid) || [];
  const p = L.withSchedule(project, docs, today());
  const billed = L.billedTotal(s.payments.get(p.projectUuid) || []);
  const cost = L.currentCost(p, docs);
  const lastUpdate = L.latestReportDay(s.weekly.get(p.projectUuid) || []);
  return { p, docs, billed, cost, lastUpdate, flags: L.attentionFlags(p, docs, billed, today(), s.requests.get(p.projectUuid) || [], lastUpdate), waiting: L.waitingApproval(docs) };
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
  it.billed > 0 ? esc(L.formatPhp(it.billed)) : '', 'muted', it.cost > 0 ? it.billed / it.cost : 0, 'billed', big);
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
      <span>${icon('update', 'sm')}${it.lastUpdate ? `Field update ${esc(L.displayDate(it.lastUpdate))}` : 'No field update yet'}</span>
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
const section = (title, ic, body, cls = '', action = '') => `<section class="card section ${cls}"><div class="section-head">${icon(ic)}<h2>${esc(title)}</h2>${action}</div>${body}</section>`;
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
  const weeklyAdd = document.getElementById('weekly-add');
  if (weeklyAdd) weeklyAdd.onclick = () => weeklyForm(c);
  main.querySelectorAll('[data-edit-weekly]').forEach(b => { b.onclick = () => { const r = c.weekly.find(x => x.uuid === b.dataset.editWeekly); if (r) weeklyForm(c, r); }; });
  const billingAdd = document.getElementById('billing-add');
  if (billingAdd) billingAdd.onclick = () => billingForm(c);
  // The saved billing, not the card's net one: an edit starts from what is stored.
  main.querySelectorAll('[data-edit-billing]').forEach(b => { b.onclick = () => { const p = payments.find(x => x.uuid === b.dataset.editBilling); if (p) billingForm(c, p); }; });
  const letterAdd = document.getElementById('letter-add');
  if (letterAdd) letterAdd.onclick = () => editLetter(c, null);
  const requestAdd = document.getElementById('request-add');
  if (requestAdd) requestAdd.onclick = () => editRequest(c, null);
  const relatedAdd = document.getElementById('related-add');
  if (relatedAdd) relatedAdd.onclick = () => relatedFile(c, null);
  main.querySelectorAll('[data-edit-related]').forEach(b => { b.onclick = () => { const f = documents.find(x => x.uuid === b.dataset.editRelated); if (f) relatedFile(c, f); }; });
  main.querySelectorAll('[data-add-order]').forEach(b => { b.onclick = () => addOrder(c, b.dataset.addOrder); });
  main.querySelectorAll('[data-edit-order]').forEach(b => { b.onclick = () => { const o = documents.find(x => x.uuid === b.dataset.editOrder); if (o) editOrder(c, o); }; });
  main.querySelectorAll('[data-edit-photos]').forEach(b => { b.onclick = () => { const r = c.weekly.find(x => x.uuid === b.dataset.editPhotos); if (r) editPhotos(c, r); }; });
  main.querySelectorAll('[data-edit-letter]').forEach(b => { b.onclick = () => { const l = c.of('contractor_letters').find(x => x.uuid === b.dataset.editLetter); if (l) editLetter(c, l); }; });
  main.querySelectorAll('[data-edit-request]').forEach(b => { b.onclick = () => { const r = c.of('developer_requests').find(x => x.uuid === b.dataset.editRequest); if (r) editRequest(c, r); }; });
  const editWx = main.querySelector('[data-edit-weather]');
  if (editWx) editWx.onclick = () => { const r = c.weekly.find(x => x.uuid === editWx.dataset.editWeather); if (r) editWeather(c, r); };
  if (document.getElementById('audit-results')) bindAudit(c);
  loadFiles(main);
}

/* ---------- Summary (SummarySection) ---------- */

function summaryBody({ page, project, records, of, documents, payments, weekly, cost, billed, now }) {
  const latest = weekly[0];
  const openIssues = of('project_issues').filter(L.isOpenIssue).length;
  const orders = L.chained(documents.filter(d => SCHEDULE_ORDER.test(d.documentName || '')));
  const flags = L.attentionFlags(project, documents, billed, now, of('developer_requests'), L.latestReportDay(weekly));
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
    <p class="alert ${f.level}">${f.level === 'critical' ? '<i class="pulse" aria-hidden="true"></i>' : icon(f.level === 'stale' ? 'schedule' : 'warning', 'sm')}<b>${esc(f.message)}</b></p>`).join('')}</div>`) : '';
  const profile = section('Project profile', 'apartment', `
    <div class="site-photo" id="site-photo">${icon('architecture')}<span>No site photo yet</span></div>
    <div class="attrs">
      ${kv('Implementing office', project.implementingOffice)}
      ${kv('Contractor', project.contractor, 'hl')}
      ${kv('Project type', project.projectType)}
      ${housing ? kv('Land area', project.landArea) + kv('No. of units', project.numberOfUnits) : ''}
    </div>
    ${(() => { const items = L.scopeItems(project.scopeOfWork); return items.length ? `<p class="sub">Scope of works</p>${items.map((x, i) => `
      <div class="scope"><b>${i + 1}. ${esc(x.name)}</b>${kv('Amount', x.amount > 0 ? L.formatPhp(x.amount) : '-')}${kv('Weight', `${x.weightPct.toFixed(2)}%`)}</div>`).join('')}` : ''; })()}`,
    '', `<a class="text-btn" href="#/p/${esc(page.id)}/print/profile">${icon('print', 'sm')}Print</a>`);

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
      <tbody>${L.netBillings(payments).map(p => `<tr>
        <td>${esc(p.name || '-')}${p.periodCovered ? `<br><span class="muted">${esc(p.periodCovered)}</span>` : ''}<span class="pdate"><br>${esc(L.displayDate(p.date))}</span></td>
        <td>${esc(L.displayDate(p.date))}</td>
        <td class="num">${L.formatDecimal(L.netAmount(p))}</td>
        <td class="num">${pct(p.percentage)}</td>
        <td class="num">${L.formatDecimal(Number(p.balanceAmount) || 0)}</td></tr>`).join('')}
      </tbody>
      <tfoot><tr><th>Total</th><th></th>
        <th class="num">${L.formatDecimal(billed)}</th>
        <th class="num">${cost > 0 ? pct(billed / cost * 100) : '-'}</th>
        <th class="num">${L.formatDecimal(cost - billed)}</th></tr></tfoot>
    </table></div>`, 'wide');

  return `
    <div class="bento">
      <div class="col c1">${attention}${profile}</div>
      <div class="col c2">${dates}${barChartCard(project, documents, now)}</div>
      <div class="col c3">${costCard}${report}${construction}</div>
      ${paymentCard}
    </div>`;
}

/** BarChartCard: the chart in force, today's planned % and its S-curve by month. */
function barChartCard(project, documents, now) {
  const plan = L.planOn(project, documents, now);
  if (!plan) return section('Approved bar chart', 'stacked_line_chart', emptyLine('No bar chart yet. The target % is typed in each weekly report.'));
  const { version, day } = plan, last = L.chartLastDay(version), [month, cell] = L.cellOf(Math.min(Math.max(day, 1), last));
  const inForce = v => `${v.label} · from ${L.displayDate(v.effective)}`;
  return section('Approved bar chart', 'stacked_line_chart', `
    ${kv('In force', inForce(version))}
    ${kv('Planned as of today', `${plan.plannedPct.toFixed(2)}%${day >= 1 && day <= last ? ` · Month ${month}, cell ${cell}` : ''}`)}
    ${kv('Chart length', `${last} calendar days`)}
    ${plan.versions.filter(v => v !== version).map(v => kv('Also on file', inForce(v))).join('')}
    <p class="sub">S-curve</p>
    ${table(['Month', { text: 'Monthly', num: true }, { text: 'Cumulative', num: true }], L.chartMonths(version).map(m =>
      `<tr><td>${m.no}</td><td class="num">${m.monthlyPct.toFixed(2)}%</td><td class="num">${m.cumulativePct.toFixed(2)}%</td></tr>`).join(''))}`);
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
  const editable = L.canEdit(c.project, s.user.uid, s.claims);
  const add = editable ? `<button class="btn weekly-add" id="weekly-add">${icon('add_circle', 'sm')}New weekly report</button>` : '';
  if (!c.weekly.length) return `${add}<div class="card empty">${icon('event_note')}<p>No weekly engineer reports submitted yet</p></div>`;
  return `${add}<h2 class="list-title">NHA weekly project engineer's report</h2><div class="stack">${c.weekly.map(r => reportCard(r, c.weekly, c.page.id, editable)).join('')}</div>`;
}

const table = (head, rows, foot = '') => `<div class="scroll"><table class="grid-table"><thead><tr>${head.map(h => `<th${h.num ? ' class="num"' : ''}>${esc(h.text ?? h)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody>${foot}</table></div>`;
/** The Equipment table's status chip: Operational green, Idle or For Repair amber, anything else grey. */
const equipmentLevel = status => (/^operational$/i.test(status.trim()) ? 'ok' : /^(idle|for repair)$/i.test(status.trim()) ? 'watch' : 'neutral');

/** varianceColor as a text colour class; small slips stay grey. */
const noteLevel = v => { const lv = L.varianceLevel(Math.round(v * 100) / 100); return lv === 'neutral' ? 'muted' : lv; };

/** WeeklyReportCard: week, who and when, actual against target; the report's five tables and photos under "Report details". */
function reportCard(r, all, pid, editable = false) {
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
      <div class="rep-actions">${editable ? `<button class="text-btn" data-edit-weekly="${esc(r.uuid)}">${icon('edit', 'sm')}Edit</button>` : ''}
        <a class="text-btn" href="#/p/${esc(pid)}/print/${esc(r.uuid)}">${icon('print', 'sm')}Print</a></div>
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
        ${issues.length ? table(['Issues encountered', 'Action taken'], issues.map(i => `<tr><td>${esc(i.description)}</td><td><b>${esc([i.actionBy, i.actionTaken].filter(x => x.trim()).join(': '))}</b></td></tr>`).join(''))
          : emptyLine('No issues encountered for this reporting period.')}
        ${photos.length || editable ? `<h4>Attached site photos (${photos.length})</h4>` : ''}
        ${photos.length ? photoGrid(photos, L.photoDetails(r.photoDetailsJson)) : ''}
        ${editable ? photosButton(r, photos.length) : ''}
      </div>
    </details>
  </article>`;
}

/** Opens editPhotos for a weekly report. */
const photosButton = (r, count) => `<button class="text-btn entry-edit" data-edit-photos="${esc(r.uuid)}">${icon('photo_camera', 'sm')}${count ? 'Edit photos' : 'Add photos'}</button>`;

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

// SubdivisionPlanTab in monitoring mode: the plan with each lot coloured over it. Drag to move, wheel or pinch to zoom,
// double-click for 250%; click a lot for its card, and "History & details" for LotInfoDialog.
const lotState = page => (page.lots ||= { site: null, query: '', band: 'ALL', billing: 'ALL', completion: 'ALL', sel: null, more: false, view: null });
const lotSite = (c, st) => { const sites = L.sdpSites(c.records); return { sites, site: sites.find(x => x.site === st.site) || sites[0] }; };
const ZOOMS = [[0.25, '25%'], [0.5, '50%'], [1, '100%'], [2.5, '250%']];

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
    <p class="muted lot-count" id="lot-count"></p>
    <div class="lot-legend">${L.LOT_BANDS.map((b, i) => `<span><i class="dot lot-${i}"></i>${esc(b)}</span>`).join('')}<span><i class="dot lot-n"></i>Not a housing unit</span><span><i class="dot billed"></i>Billed</span></div>
    ${site.lots.length ? `<div class="lot-viewer" id="lot-plan">
      <div class="lot-stage" id="lot-stage"><p class="muted lot-wait">Drawing the plan…</p></div>
      <div class="zoom-bar" role="group" aria-label="Zoom">
        ${ZOOMS.map(([z, label]) => `<button data-zoom="${z}">${label}</button>`).join('')}
        <button data-zoom-step="0.8" aria-label="Zoom out">−</button><b id="zoom-pct">100%</b><button data-zoom-step="1.25" aria-label="Zoom in">+</button>
        <button data-zoom="reset">Reset</button></div>
      <div class="lot-card" id="lot-card" hidden></div>
    </div>` : emptyLine('No lots drawn on this plan yet.')}
    <div id="lot-results"></div>
  </section>`;
}

/** The selected lot's card under the plan (the phone's bottom sheet). */
function lotCard(l, editable = false) {
  const p = l.progress || {};
  const pct = L.lotProgress(l), billing = L.lotBillingLabel(l.progress), billed = billing !== 'NOT BILLED';
  const road = L.lotUse(l) === 'Road Lot';
  const line = (road ? `Road: ${L.ROAD_STATES[L.roadState(pct)]} • Drainage: ${Number(p.drainageProgress) >= 100 ? 'Completed' : 'Not completed'}`
    : `Progress: ${pct}% (${p.constructionStatus || 'Not Started'})`) + ` • Area: ${Number(l.lotAreaSqM) || 0} sq.m • Unit: ${l.housingUnitNumber || 'N/A'}`;
  return `<div class="lc-head"><span class="lot-pct ${L.lotColour(l)}">${icon('home_work', 'sm')}</span><b>${esc(L.lotLabel(l))}</b>
      <span class="lc-bill${billed ? ' on' : ''}">${billed ? `● ${esc(billing)}` : 'NOT BILLED'}</span>
      <button class="icon-btn" id="lot-card-close" aria-label="Close">${icon('close', 'sm')}</button></div>
    <p>${esc(line)}</p>
    ${p.lastModifiedBy ? `<p class="lc-upd">Last updated: ${esc(p.lastModifiedDate ? L.displayDate(p.lastModifiedDate) : '')} by ${esc(p.lastModifiedBy)}</p>` : ''}
    ${editable ? `<button class="btn lc-more" id="lot-edit">${icon('edit', 'sm')}Update progress</button>` : ''}
    <button class="btn lc-more" id="lot-more">${icon('history', 'sm')}History &amp; details</button>`;
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

/**
 * UpdateLotProgressDialog without the lot's name and use (those stay on the phone): the progress band (a road lot's
 * state and drainage), status, activity, remarks and photos, and the M1 and M2 billing marks, saved together.
 * A photo added here has no "Block X Lot Y" stamp.
 */
function editLot(c, lot) {
  const p = lot.progress || {}, pct = L.lotProgress(lot), road = L.lotUse(lot) === 'Road Lot', label = L.lotLabel(lot);
  const refs = L.attachmentList(p.photosJson), drained = Number(p.drainageProgress) >= 100;
  const billed = [p.billingStatus === 'BILLED', p.m2BillingStatus === 'BILLED'];
  const info = i => { const [date, by, refNo] = i ? [p.m2BillingDate, p.m2BilledBy, p.m2BillingReference] : [p.billingDate, p.billedBy, p.billingReference];
    return `${date || 'N/A'} • By: ${by || 'N/A'}${refNo ? ` • Ref No: ${refNo}` : ''}`; };
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  d.innerHTML = `<form novalidate>
    <h2>${esc(label)} construction &amp; billing</h2>
    <label class="field">${road ? 'Road' : 'Physical progress'}<select name="band">${(road ? L.ROAD_STATES : L.LOT_BANDS).map((o, i) =>
      `<option value="${i}"${i === (road ? L.roadState(pct) : L.lotBand(pct)) ? ' selected' : ''}>${esc(o)}</option>`).join('')}</select></label>
    ${road ? `<label class="check"><input type="checkbox" name="drainage"${drained ? ' checked' : ''}>Drainage completed</label>` : ''}
    <label class="field">Construction status<input name="constructionStatus" value="${esc(p.constructionStatus ?? 'Not Started')}" autocomplete="off"></label>
    <label class="field">Current activity (e.g. rebar, concreting)<input name="currentActivity" value="${esc(p.currentActivity || '')}" autocomplete="off"></label>
    <label class="field">Remarks<input name="remarks" value="${esc(p.remarks || '')}" autocomplete="off"></label>
    <h3>Lot photos (${refs.length})</h3>
    ${refs.length ? `<ul class="photo-edit">${refs.map((refText, i) => `<li>
      ${L.storagePathOf(refText) ? `<a class="thumb" data-file="${esc(refText)}" data-alt="Photo of ${esc(label)}" target="_blank" rel="noopener">${icon('photo_camera')}</a>`
        : `<span class="thumb gone">${icon('photo_camera')}<small>Not uploaded yet</small></span>`}
      <label class="check"><input type="checkbox" data-drop="${i}">Remove this photo</label></li>`).join('')}</ul>` : ''}
    <label class="field">Add photos<input type="file" name="photos" accept="image/*" multiple></label>
    <fieldset class="lot-billing"><legend>Developer billing</legend>
      ${[0, 1].map(i => `<label class="check"><input type="checkbox" name="m${i + 1}"${billed[i] ? ' checked' : ''}><span>${i ? 'M2 · 20%' : 'M1 · 80%'} billed${billed[i] ? `<br><small class="muted">${esc(info(i))}</small>` : ''}</span></label>
      <div data-bill="${i}"><label class="field">Billing reference / voucher no. (optional)<input name="ref${i + 1}" autocomplete="off"></label>
        <label class="field">Billing remarks (optional)<input name="note${i + 1}" autocomplete="off"></label></div>`).join('')}
      <small class="muted">M2 can be marked once M1 is billed.</small>
    </fieldset>
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  // M2 follows M1: it is billed after M1 and unbilled before M1. A newly ticked mark asks for its reference.
  const billing = () => {
    form.m1.disabled = form.m1.checked && form.m2.checked;
    form.m2.disabled = !form.m1.checked && !form.m2.checked;
    [form.m1, form.m2].forEach((m, i) => { form.querySelector(`[data-bill="${i}"]`).style.display = m.checked && !billed[i] ? '' : 'none'; });
  };
  form.m1.onchange = form.m2.onchange = billing;
  billing();
  // The status follows the band picked; it can still be retyped.
  form.band.onchange = () => { form.constructionStatus.value = L.statusForProgress(L.pickedProgress(pct, Number(form.band.value), road)); };
  const values = () => ({
    progress: L.pickedProgress(pct, Number(form.band.value), road),
    drainage: !road ? null : form.drainage.checked === drained ? Number(p.drainageProgress) || 0 : form.drainage.checked ? 100 : 0,
    constructionStatus: form.constructionStatus.value, currentActivity: form.currentActivity.value, remarks: form.remarks.value,
    photos: refs.filter((_, i) => !form.querySelector(`[data-drop="${i}"]`).checked),
    billing: [1, 2].map(i => ({ billed: form[`m${i}`].checked, reference: form[`ref${i}`].value, remarks: form[`note${i}`].value })),
  });
  const changed = () => JSON.stringify(values()) !== initial || form.photos.files.length > 0;
  const initial = JSON.stringify(values());
  const done = () => { d.close(); d.remove(); };
  const close = () => (!changed() || confirm('Discard your changes to this lot?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    if (!changed()) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const f = values();
      // Every photo is prepared before the first upload, so a file that is not a photo stops the save with nothing sent.
      const blobs = [];
      for (const file of form.photos.files) {
        const blob = await attachmentBlob(file, L.PHOTO_MAX_EDGE);
        if (blob[1] === 'pdf') throw new Error('Only photos can be added here.');
        blobs.push(blob);
      }
      for (const blob of blobs) f.photos.push(await uploadFile(c, blob));
      const saved = L.lotSave(lot, f, { user: s.profile.name || s.user.email, at: `${today()} ${new Date().toTimeString().slice(0, 5)}`, contractor: c.project.contractor || '' });
      if (saved) {
        await (lot.progress ? saveEdits(c, [[lot.progress, saved.changes, saved.audits]], 'lot')
          : addRecords(c, [[ids => L.newLotProgressRecord(ids, lot, saved.changes), saved.audits]]));
      }
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  loadFiles(d);
  d.showModal();
}

const planDrawings = new Map();
/** The plan page (without lots), drawn once per plan. */
function planBase(site) {
  const key = [site.plan.uuid, site.plan.pdfFileUrl, site.plan.frame, site.plan.rotation].join('|');
  if (!planDrawings.has(key)) planDrawings.set(key, planDrawing(site, false).then(d => { if (d.problem) planDrawings.delete(key); return d; }));
  return planDrawings.get(key);
}

async function bindLots(c) {
  const st = lotState(c.page);
  const site = lotSite(c, st).site;
  const viewer = document.getElementById('lot-plan');
  const count = document.getElementById('lot-count');
  const card = document.getElementById('lot-card');
  const more = document.getElementById('lot-results');
  let d = null, svg = null, stage = null;

  // The lots as shapes over the plan: colour by band, the rest dimmed by the filters, the selection outlined.
  const paint = () => {
    const n = site.lots.filter(l => L.lotMatches(l, st)).length;
    count.textContent = n === site.lots.length ? plural(n, 'lot') : `${n} of ${plural(site.lots.length, 'lot')} match`;
    if (svg) svg.innerHTML = site.lots.map(l => {
      const pts = d.shapes.get(l.uuid);
      if (!pts) return '';
      const billed = L.lotBillingLabel(l.progress) !== 'NOT BILLED';
      return `<polygon data-lot="${esc(l.uuid)}" class="${L.lotColour(l)}${L.lotMatches(l, st) ? (billed ? ' billed' : '') : ' dim'}${l.uuid === st.sel ? ' sel' : ''}" points="${pts}"><title>${esc(`${L.lotLabel(l)} · ${L.lotProgress(l)}%`)}</title></polygon>`;
    }).join('');
    const sel = site.lots.find(l => l.uuid === st.sel);
    if (card) {
      card.hidden = !sel;
      card.innerHTML = sel ? lotCard(sel, L.canEdit(c.project, s.user.uid, s.claims)) : '';
      if (sel) {
        const edit = document.getElementById('lot-edit');
        if (edit) edit.onclick = () => editLot(c, sel);
        document.getElementById('lot-card-close').onclick = () => { st.sel = null; st.more = false; paint(); };
        document.getElementById('lot-more').onclick = () => { st.more = !st.more; paint(); if (st.more) document.getElementById('lot-detail')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); };
      }
    }
    more.innerHTML = sel && st.more ? lotDetail(sel) : '';
    loadFiles(more);
    const close = document.getElementById('lot-close');
    if (close) close.onclick = () => { st.more = false; paint(); };
  };
  const pick = id => { st.sel = id; st.more = false; paint(); };

  document.getElementById('lot-q').oninput = e => {
    st.query = e.target.value;
    // As on the phone: when the search finds one lot, it is selected and centred at 200%.
    const found = st.query.trim() ? site.lots.filter(l => L.lotMatches(l, st)) : [];
    if (found.length === 1 && d?.centres.has(found[0].uuid)) { st.sel = found[0].uuid; centreOn(d.centres.get(found[0].uuid), 2); }
    paint();
  };
  main.querySelectorAll('[data-lot-filter]').forEach(sel => { sel.onchange = () => { st[sel.dataset.lotFilter] = sel.value; paint(); }; });
  main.querySelectorAll('[data-lot-site]').forEach(b => { b.onclick = () => { st.site = b.dataset.lotSite; st.sel = null; st.more = false; st.view = null; drawProject(c.page); }; });
  paint();
  if (!viewer) return;

  // The view: the plan fitted to the viewer is 100%; x, y move it, s zooms it (0.2 to 5, as on the phone).
  let fit = 1, W = 0, H = 0;
  const view = () => st.view;
  const apply = () => {
    const v = view();
    stage.style.transform = `translate(${v.x}px, ${v.y}px) scale(${v.s})`;
    document.getElementById('zoom-pct').textContent = `${Math.round(v.s * 100)}%`;
    viewer.querySelectorAll('[data-zoom]').forEach(b => b.setAttribute('aria-pressed', Math.abs(Number(b.dataset.zoom) - v.s) < 0.08));
  };
  const reset = () => { st.view = { s: 1, x: (W - d.width * fit) / 2, y: (H - d.height * fit) / 2 }; apply(); };
  const zoomAt = (s, px = W / 2, py = H / 2) => {
    const v = view(), ns = Math.min(5, Math.max(0.2, s));
    st.view = { s: ns, x: px - (px - v.x) * ns / v.s, y: py - (py - v.y) * ns / v.s };
    apply();
  };
  const centreOn = ([cx, cy], s) => { st.view = { s, x: W / 2 - cx * fit * s, y: H / 2 - cy * fit * s }; apply(); };

  d = await planBase(site);
  if (!viewer.isConnected) return;
  // Each lot's points in plan-image pixels, and its centre for the search.
  d.shapes = new Map(); d.centres = new Map();
  for (const l of site.lots) {
    const pts = L.lotPolygon(l).map(([x, y]) => d.place(x, y));
    if (pts.length < 3) continue;
    d.shapes.set(l.uuid, pts.map(q => q.map(n => n.toFixed(1)).join(',')).join(' '));
    d.centres.set(l.uuid, [pts.reduce((a, q) => a + q[0], 0) / pts.length, pts.reduce((a, q) => a + q[1], 0) / pts.length]);
  }
  W = viewer.clientWidth; H = viewer.clientHeight;
  fit = Math.min(W / d.width, H / d.height);
  stage = document.getElementById('lot-stage');
  stage.style.width = `${d.width * fit}px`;
  stage.style.height = `${d.height * fit}px`;
  stage.innerHTML = `<img src="${d.image}" alt="Subdivision plan" draggable="false">
    <svg viewBox="0 0 ${d.width} ${d.height}" preserveAspectRatio="none" aria-hidden="true"></svg>`;
  svg = stage.querySelector('svg');
  if (d.problem) viewer.insertAdjacentHTML('afterbegin', `<p class="error lot-note">The plan PDF could not be loaded in this browser (${esc(d.problem)}), so the lots are drawn without it.</p>`);
  if (!st.view) reset(); else apply();
  paint();

  viewer.querySelectorAll('[data-zoom]').forEach(b => { b.onclick = () => (b.dataset.zoom === 'reset' ? reset() : Number(b.dataset.zoom) === 1 ? reset() : zoomAt(Number(b.dataset.zoom))); });
  viewer.querySelectorAll('[data-zoom-step]').forEach(b => { b.onclick = () => zoomAt(view().s * Number(b.dataset.zoomStep)); });

  // Drag to move, two fingers to pinch, a click without a drag picks the lot under it (or clears the selection).
  const local = e => { const r = viewer.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
  const down = new Map();
  let moved = false;
  stage.onpointerdown = e => {
    down.set(e.pointerId, local(e));
    if (down.size === 1) moved = false;
    stage.setPointerCapture(e.pointerId);
  };
  stage.onpointermove = e => {
    const was = down.get(e.pointerId);
    if (!was) return;
    const now = local(e);
    if (down.size === 1) {
      if (!moved && Math.hypot(now[0] - was[0], now[1] - was[1]) < 4) return;
      moved = true;
      const v = view();
      st.view = { ...v, x: v.x + now[0] - was[0], y: v.y + now[1] - was[1] };
      apply();
    } else if (down.size === 2) {
      moved = true;
      const other = [...down].find(([id]) => id !== e.pointerId)[1];
      const before = Math.hypot(was[0] - other[0], was[1] - other[1]), after = Math.hypot(now[0] - other[0], now[1] - other[1]);
      if (before > 0) zoomAt(view().s * after / before, (now[0] + other[0]) / 2, (now[1] + other[1]) / 2);
    }
    down.set(e.pointerId, now);
  };
  stage.onpointerup = stage.onpointercancel = e => {
    const tap = e.type === 'pointerup' && down.size === 1 && !moved;
    down.delete(e.pointerId);
    if (!tap) return;
    const hit = document.elementsFromPoint(e.clientX, e.clientY).find(el => el.dataset?.lot);
    pick(hit && hit.dataset.lot !== st.sel ? hit.dataset.lot : null);
  };
  stage.ondblclick = e => { const [px, py] = local(e); view().s > 1.2 ? reset() : zoomAt(2.5, px, py); };
  viewer.onwheel = e => { e.preventDefault(); const [px, py] = local(e); zoomAt(view().s * Math.exp(-e.deltaY * 0.0015), px, py); };
}

/* ---------- Weather (WeatherChartTab); writers edit a weekly report's 24-hour weather (AddWeeklyReportDialog) ---------- */

/** WeatherChartTab: each logged day as a 24-hour clock, by weekly report (7 days) or by month. */
function weatherView({ page, weekly, project }) {
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
      ${days.length && L.canEdit(project, s.user.uid, s.claims) ? `<button class="text-btn wx-edit-btn" data-edit-weather="${esc(report.uuid)}">${icon('edit', 'sm')}Edit weather</button>` : ''}
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

/* ---------- Issues (IssuesTab, IssueCard); writers log and edit them (AddIssueDialog) ---------- */

const issueState = page => (page.issues ||= { query: '', filter: 'All' });

function issuesView(c) {
  const st = issueState(c.page);
  const add = L.canEdit(c.project, s.user.uid, s.claims) ? `<button class="btn issue-add" id="issue-add">${icon('add_circle', 'sm')}Log issue</button>` : '';
  return `${add}<label class="field issue-q">Search<input type="search" id="issue-q" placeholder="Search issues or actions taken..." value="${esc(st.query)}" autocomplete="off"></label>
    <div class="chips" role="group" aria-label="Filter issues">${L.ISSUE_FILTERS.map(f => `<button data-issue-filter="${f}" aria-pressed="${f === st.filter}">${f}</button>`).join('')}</div>
    <div id="issue-results">${issueResults(c, st)}</div>`;
}

function issueResults(c, st) {
  // ProjectDao lists issues newest logged first.
  const issues = c.of('project_issues').sort((a, b) => (Number(b.timestamp) || 0) - (Number(a.timestamp) || 0))
    .filter(i => L.issueMatches(i, st.query, st.filter, c.now));
  if (!issues.length) return `<div class="card empty">${icon('warning')}<p>${st.query || st.filter !== 'All' ? 'No matching issues found' : 'No Issues Logged'}</p></div>`;
  const editable = L.canEdit(c.project, s.user.uid, s.claims);
  return `<ul class="issue-cards">${issues.map(i => issueCard(i, c.now, editable)).join('')}</ul>`;
}

function issueCard(i, now, editable = false) {
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
    ${editable ? `<div class="ic-actions"><button class="text-btn" data-edit-issue="${esc(i.uuid)}">${icon('edit', 'sm')}Edit</button></div>` : ''}
  </li>`;
}

function bindIssues(c) {
  const st = issueState(c.page);
  const results = document.getElementById('issue-results');
  // Only the results redraw while typing, so the search box keeps its focus.
  const redraw = () => { results.innerHTML = issueResults(c, st); };
  results.onclick = e => {
    const uuid = e.target.closest('[data-edit-issue]')?.dataset.editIssue;
    const issue = uuid && c.of('project_issues').find(i => i.uuid === uuid);
    if (issue) editIssue(c, issue);
  };
  const add = document.getElementById('issue-add');
  if (add) add.onclick = () => editIssue(c, null);
  document.getElementById('issue-q').oninput = e => { st.query = e.target.value; redraw(); };
  main.querySelectorAll('[data-issue-filter]').forEach(b => {
    b.onclick = () => {
      st.filter = b.dataset.issueFilter;
      main.querySelectorAll('[data-issue-filter]').forEach(x => x.setAttribute('aria-pressed', x === b));
      redraw();
    };
  });
}

/** AddIssueDialog: a new issue (`issue` null) or an edit, with the same fields and checks; Save writes it and an audit log entry. */
function editIssue(c, issue) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const isNew = !issue;
  issue ||= { date: today(), description: '', actionTaken: '', actionBy: '', targetDate: '', isCritical: 0, status: 'Pending' };
  const status = issue.status || 'Pending';
  const statuses = L.ISSUE_STATUSES.includes(status) ? L.ISSUE_STATUSES : [status, ...L.ISSUE_STATUSES];
  const opt = (v, cur, label = v) => `<option value="${esc(v)}"${v === cur ? ' selected' : ''}>${esc(label)}</option>`;
  d.innerHTML = `<form novalidate>
    <h2>${isNew ? 'Log issue' : 'Edit issue'}</h2>
    <label class="field">Date<input type="date" name="date" value="${esc(issue.date)}" required></label>
    <label class="field">Issue / Concern<textarea name="description" rows="3" required>${esc(issue.description)}</textarea></label>
    <label class="field">Action taken<textarea name="actionTaken" rows="3" required>${esc(issue.actionTaken)}</textarea></label>
    <label class="field">Action taken by<select name="actionBy">${opt('', issue.actionBy || '', 'Not set')}${L.ISSUE_ACTION_BY.map(v => opt(v, issue.actionBy || '')).join('')}</select></label>
    <label class="field">Target date (optional)<input type="date" name="targetDate" value="${esc(issue.targetDate || '')}"></label>
    <label class="check"><input type="checkbox" name="isCritical"${L.isCriticalIssue(issue) ? ' checked' : ''}>Critical issue</label>
    <label class="field">Status<select name="status">${statuses.map(v => opt(v, status)).join('')}</select></label>
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  const values = () => {
    const f = Object.fromEntries(new FormData(form));
    return { date: f.date, description: f.description.trim(), actionTaken: f.actionTaken.trim(), actionBy: f.actionBy, targetDate: f.targetDate, isCritical: form.isCritical.checked ? 1 : 0, status: f.status }; // 1/0, as Room's rows sync
  };
  const initial = JSON.stringify(values());
  // rememberDiscardGuard: closing with unsaved changes asks first.
  const done = () => { d.close(); d.remove(); };
  const close = () => (JSON.stringify(values()) === initial || confirm(isNew ? 'Discard this new issue?' : 'Discard your changes to this issue?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values(), errors = L.issueErrors(f);
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (!isNew && JSON.stringify(f) === initial) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      await (isNew ? addIssue(c, f) : saveEdit(c, issue, f, 'issue', {
        // ProjectViewModel.updateProjectIssue's entry, word for word.
        actionType: 'Issue Creation', details: `Updated issue from ${f.date}`, oldValue: 'Previous issue state',
        newValue: `Status: ${f.status}, Remarks: ${issue.remarks || ''}`,
      }));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * OrderFieldInputs: the details of the order a new request asks for, one group for each request type (bindOrderFields
 * shows the chosen type's). A Partial suspension picks the bar chart items it pauses.
 */
function orderFields(c, heading = '<h3>Order requested</h3>') {
  const text = (name, label, attrs = '') => `<label class="field">${label}<input name="${name}" autocomplete="off" ${attrs}></label>`;
  const date = (name, label) => `<label class="field">${label}<input type="date" name="${name}"></label>`;
  const pick = (name, label, options) => `<label class="field">${label}<select name="${name}">${options.map(o => `<option>${o}</option>`).join('')}</select></label>`;
  // The End date has no name: it only follows the days, and is not saved on its own.
  const span = p => `${text(`${p}Days`, 'No. of days', 'inputmode="numeric"')}${date(`${p}Effectivity`, 'Effectivity date')}<label class="field">End date<input type="date" data-end="${p}"></label>`;
  const group = (type, body) => `<div class="order-fields" data-order="${type}">${body}</div>`;
  const items = L.chartItemNames(c.project), suspensions = L.suspensionsByNo(c.documents);
  const [ws, co, wr, vo, te] = L.REQUEST_TYPES;
  return `${heading}<p class="muted" data-order-note></p>
    ${group(ws, text('wsNo', 'Suspension No.') + pick('wsType', 'Type of suspension', ['Full', 'Partial']) + span('ws') + text('wsReason', 'Reason')
      + (items.length ? `<fieldset data-items><legend>Work items suspended (their target pauses; the rest go on)</legend>
        ${items.map(i => `<label class="check"><input type="checkbox" name="wsItems" value="${esc(i)}">${esc(i)}</label>`).join('')}</fieldset>` : ''))}
    ${group(co, suspensions.length ? `<label class="field">Suspension continued<select name="coOf">
        ${suspensions.map((x, i) => `<option value="${esc(x.uuid)}"${i === suspensions.length - 1 ? ' selected' : ''}>${esc(L.orderLabel(x.documentName))}</option>`).join('')}</select></label>
      <b data-co-name></b>${pick('coType', 'Type of suspension', ['Full', 'Partial'])}${span('co')}`
      : '<p class="n-behind">No work suspension order is logged yet, so there is nothing to continue. Log the suspension first.</p>')}
    ${group(wr, text('wrNo', 'Resumption No.') + pick('wrType', 'Type of resumption', ['Full', 'Partial']) + date('wrEffectivity', 'Effectivity date') + text('wrRemarks', 'Remarks'))}
    ${group(vo, `${text('voNo', 'VO No.')}${pick('voType', 'Type of variation', ['Additive', 'Deductive'])}
      <label class="field">Amount (PHP)<input name="voAmount" inputmode="decimal" autocomplete="off"><small class="muted" data-vo-hint></small></label>
      ${text('voReason', 'Reason')}<label class="field">Date approved<input type="date" name="voDateApproved"><small class="muted">Leave blank if not yet approved</small></label>`)}
    ${group(te, text('teNo', 'Extension No.', 'inputmode="numeric"') + text('teDays', 'No. of days', 'inputmode="numeric"') + text('teReason', 'Reason'))}`;
}

/** Wires orderFields: the group shown, the End date and the days following each other, and what the phone's form fills in. */
function bindOrderFields(c, form) {
  const show = () => {
    const type = form.requestType.value;
    form.querySelectorAll('[data-order]').forEach(g => { g.style.display = g.dataset.order === type ? '' : 'none'; });
    form.querySelector('[data-order-note]').textContent = `Saved to Documents › ${type} as Pending.`;
    const items = form.querySelector('[data-items]');
    if (items) items.style.display = form.wsType.value === 'Partial' ? '' : 'none';
    form.querySelector('[data-vo-hint]').textContent = form.voType.value === 'Deductive' ? 'Will be subtracted from the contract cost' : 'Will be added to the contract cost';
  };
  form.requestType.onchange = form.wsType.onchange = form.voType.onchange = show;
  show();
  for (const p of ['ws', 'co']) {
    const days = form[`${p}Days`], eff = form[`${p}Effectivity`], end = form.querySelector(`[data-end="${p}"]`);
    if (!days) continue; // no suspension to continue
    // The effectivity date is day 1 (ProjectSchedule.endText, daysText).
    days.oninput = eff.onchange = () => { const n = /^\d+$/.test(days.value.trim()) ? +days.value : 0; end.value = (n > 0 && eff.value && L.lastDay(eff.value, n)) || ''; };
    end.onchange = () => { const n = Math.round((Date.parse(end.value) - Date.parse(eff.value)) / 86400000) + 1; if (n >= 1) days.value = n; };
  }
  if (form.coOf) {
    // Named and started where the suspension picked and its continuances so far end; Partial when the suspension is.
    const suspension = () => c.documents.find(x => x.uuid === form.coOf.value);
    const next = () => L.nextContinuance(suspension(), c.documents, form.coType.value);
    const name = () => { form.querySelector('[data-co-name]').textContent = next()[0]; };
    form.coType.onchange = name;
    form.coOf.onchange = () => {
      form.coType.value = /^Partial/.test(L.orderLabel(suspension().documentName)) ? 'Partial' : 'Full';
      name(); form.coEffectivity.value = next()[1]; form.coEffectivity.onchange();
    };
    form.coOf.onchange();
  }
  // A time extension for a suspension starts with the days its approved suspensions stopped work; still editable.
  form.teReason.oninput = () => {
    const n = L.suspensionExtensionDays(c.documents);
    if (!form.teDays.value.trim() && n > 0 && /suspen/i.test(form.teReason.value)) form.teDays.value = n;
  };
}

/** orderFields' values, by name, with the work items ticked. */
const orderValues = form => ({ ...textFields(form), wsItems: new FormData(form).getAll('wsItems') });

/**
 * AddCustomDocumentDialog for one kind of order (the card's Add): its details, filed in Documents as Pending.
 * `continued` is the suspension a continuance starts on (OrderDialogs' "Add Continuance").
 */
function addOrder(c, type, continued = '') {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  d.innerHTML = `<form novalidate>
    <h2>New ${esc(type.toLowerCase())}</h2>
    <input type="hidden" name="requestType" value="${esc(type)}">
    ${orderFields(c, '')}
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  bindOrderFields(c, form);
  if (continued && form.coOf) { form.coOf.value = continued; form.coOf.onchange(); }
  const snapshot = () => JSON.stringify(orderValues(form));
  const initial = snapshot();
  const done = () => { d.close(); d.remove(); };
  const close = () => (snapshot() === initial || confirm('Discard this new order?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const o = orderValues(form), suspension = c.documents.find(x => x.uuid === o.coOf);
    const errors = L.orderErrors(type, o, suspension);
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const filed = L.orderDocument(type, o, suspension, c.documents);
      await addRecord(c, ids => L.newDocumentRecord(ids, filed), L.addedDocumentAudit(filed.documentName));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * OrderStatusDialog: an order's status and remarks; a suspension's End date moves its Duration. Only approvers set or
 * undo Approved and delete; others update an order still in progress. A suspension also offers its next continuance.
 */
function editOrder(c, o) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const canApprove = L.canApprove(s.claims), locked = !canApprove && o.status === 'Approved', off = locked ? ' disabled' : '';
  const statuses = L.ORDER_STATUSES.includes(o.status) ? L.ORDER_STATUSES : [o.status || '', ...L.ORDER_STATUSES];
  const hasEnd = !locked && /work suspension/i.test(o.documentName || '') && L.suspensionEnd(o.remarks);
  d.innerHTML = `<form novalidate>
    <h2>Update status</h2>
    <b>${esc(L.orderLabel(o.documentName))}</b>
    <label class="field">Status<select name="status"${off}>${statuses.map(v => `<option${v === (o.status || '') ? ' selected' : ''}${v === 'Approved' && !canApprove ? ' disabled' : ''}>${esc(v)}</option>`).join('')}</select></label>
    ${canApprove ? '' : '<p class="muted">Only an Engineer Admin can approve.</p>'}
    ${hasEnd ? '<label class="field">End date<input type="date" data-end></label>' : ''}
    <label class="field">Remarks<input name="remarks" value="${esc(L.withEnd(o.remarks))}" autocomplete="off"${off}></label>
    ${L.isSuspension(o) ? `<button type="button" class="text-btn" data-continue>${icon('add_circle', 'sm')}Add continuance of work suspension</button>` : ''}
    <p class="error" role="alert"></p>
    <div class="actions">${canApprove ? `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete order</button>` : ''}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn"${off}>Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn'), end = d.querySelector('[data-end]');
  if (end) {
    // The End follows the remarks typed; picking another End changes the Duration to match.
    (form.remarks.oninput = () => { end.value = L.suspensionEnd(form.remarks.value); })();
    end.onchange = () => { form.remarks.value = L.withEndDate(form.remarks.value, end.value); form.remarks.oninput(); };
  }
  const snapshot = () => JSON.stringify([form.status.value, form.remarks.value]);
  const initial = snapshot();
  const done = () => { d.close(); d.remove(); };
  const close = () => (snapshot() === initial || confirm('Discard your changes to this order?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  const more = d.querySelector('[data-continue]');
  if (more) more.onclick = () => { done(); addOrder(c, L.REQUEST_TYPES[1], o.uuid); };
  // The phone logs nothing for a deleted order.
  bindDelete(d, `Delete ${L.orderLabel(o.documentName)}? This cannot be undone.`, () => saveEdits(c, [[o, null, null]], 'order'));
  form.onsubmit = async e => {
    e.preventDefault();
    const status = form.status.value, remarks = L.keepOrderFields(o.remarks, form.remarks.value);
    if (locked || (status === (o.status || '') && remarks === (o.remarks || ''))) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      await saveEdit(c, o, { status, remarks }, 'order', L.orderStatusAudit(o, status));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * AddRelatedDocumentDialog (`file` null): a related file's type or title, reference, status, remarks and scanned copy.
 * For one already listed it is ViewScannedCopyDialog, with the list's delete: attach or replace its scanned copy, or
 * delete the entry. The phone does not edit a related file's status or remarks, so neither does this.
 */
function relatedFile(c, file) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const isNew = !file;
  d.innerHTML = `<form novalidate>
    <h2>${isNew ? 'Add related project document' : esc(file.documentName)}</h2>
    ${isNew ? `<label class="field">Document type<select name="docType">${L.RELATED_TYPES.map(o => `<option>${esc(o)}</option>`).join('')}</select></label>
    <label class="field" data-title>Specific document title<input name="title" placeholder="e.g. Environmental Compliance Certificate" autocomplete="off"></label>
    <label class="field">Reference / serial no. / date<input name="reference" placeholder="e.g. Ref: NHA-2026-001 | Date: 2026-01-15" autocomplete="off"></label>
    <label class="field">Document status<select name="status">${L.RELATED_STATUSES.map(o => `<option${o === 'Pending' ? ' selected' : ''}>${o}</option>`).join('')}</select></label>
    <label class="field">Remarks / description<input name="remarks" autocomplete="off"></label>`
    : `<p class="muted">Status: ${esc(file.status || 'Pending')} | ${esc(file.remarks || 'No remarks')}</p>`}
    ${fileField('fileUrl', 'Scanned copy', file?.fileUrl)}
    <p class="error" role="alert"></p>
    <div class="actions">${isNew ? '' : `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete file</button>`}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  if (isNew) (form.docType.onchange = () => { form.querySelector('[data-title]').style.display = form.docType.value === L.RELATED_TYPES.at(-1) ? '' : 'none'; })();
  const snapshot = () => JSON.stringify(textFields(form));
  const initial = snapshot();
  const done = () => { d.close(); d.remove(); };
  const close = () => ((snapshot() === initial && !hasPicked(form)) || confirm(isNew ? 'Discard this new document?' : 'Discard your changes to this file?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  // The phone logs nothing for a deleted document.
  bindDelete(d, `Delete "${file?.documentName || ''}"? This cannot be undone.`, () => saveEdits(c, [[file, null, null]], 'file'));
  form.onsubmit = async e => {
    e.preventDefault();
    const filed = isNew && L.relatedDocument(textFields(form));
    if (isNew && !filed.documentName) { err.textContent = 'Type the document title.'; return; }
    if (!isNew && !hasPicked(form)) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const { fileUrl = '' } = await uploadPicked(c, form);
      await (isNew ? addRecord(c, ids => L.newDocumentRecord(ids, { ...filed, fileUrl }), L.relatedAddedAudit(filed.documentName, fileUrl))
        : saveEdit(c, file, { fileUrl }, 'file', L.fileAttachedAudit(file, fileUrl)));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * DeveloperRequestDialog: a new request (`r` null) or an edit: type, details and dates, including the date packaged.
 * A new request also takes the details of the order it asks for, which is filed in Documents as Pending.
 */
function editRequest(c, r) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const isNew = !r;
  r ||= { dateReceived: today() };
  const type = r.requestType || L.REQUEST_TYPES[0];
  const types = L.REQUEST_TYPES.includes(type) ? L.REQUEST_TYPES : [type, ...L.REQUEST_TYPES];
  d.innerHTML = `<form novalidate>
    <h2>${isNew ? 'New developer request' : 'Developer request'}</h2>
    <label class="field">Request for<select name="requestType">${types.map(v => `<option${v === type ? ' selected' : ''}>${esc(v)}</option>`).join('')}</select></label>
    <label class="field">Details (letter no., reason)<input name="details" value="${esc(r.details || '')}"></label>
    <label class="field">Date of developer's request<input type="date" name="dateRequested" value="${esc(r.dateRequested || '')}"></label>
    <label class="field">Date received by NHA<input type="date" name="dateReceived" value="${esc(r.dateReceived || '')}" required></label>
    <label class="field">Date packaged (blank: not yet)<input type="date" name="datePackaged" value="${esc(r.datePackaged || '')}"></label>
    ${r.datePackaged ? '' : '<button type="button" class="text-btn" data-today>Packaged today</button>'}
    ${fileField('requestFileUrl', "Developer's request letter", r.requestFileUrl)}
    ${isNew ? orderFields(c) : ''}
    <p class="error" role="alert"></p>
    <div class="actions">${isNew ? '' : `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete request</button>`}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  if (isNew) bindOrderFields(c, form);
  const values = () => {
    const f = Object.fromEntries(new FormData(form));
    return { requestType: f.requestType, details: f.details.trim(), dateRequested: f.dateRequested, dateReceived: f.dateReceived, datePackaged: f.datePackaged };
  };
  const order = () => orderValues(form); // a new request only
  const snapshot = () => JSON.stringify(isNew ? order() : values());
  const initial = snapshot();
  const done = () => { d.close(); d.remove(); };
  const close = () => ((snapshot() === initial && !hasPicked(form)) || confirm(isNew ? 'Discard this new request?' : 'Discard your changes to this request?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  bindDelete(d, `Delete this ${r.requestType || ''} request? This cannot be undone.`, () => saveEdits(c, [[r, null, L.deletedRequestAudit(r)]], 'request'));
  const packedToday = d.querySelector('[data-today]');
  if (packedToday) packedToday.onclick = () => { form.datePackaged.value = today(); };
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values(), o = order(), suspension = c.documents.find(x => x.uuid === o.coOf);
    const errors = [...L.requestErrors(f), ...(isNew ? L.orderErrors(f.requestType, o, suspension) : [])];
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (!isNew && snapshot() === initial && !hasPicked(form)) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const saved = { ...f, ...await uploadPicked(c, form) };
      if (isNew) {
        // The request, then the order it asks for, each with its audit entry, as the phone saves them.
        const filed = L.orderDocument(f.requestType, o, suspension, c.documents);
        await addRecords(c, [[ids => L.newRequestRecord(ids, saved), L.requestAudit(f)], [ids => L.newDocumentRecord(ids, filed), L.addedDocumentAudit(filed.documentName)]]);
      } else await saveEdit(c, r, saved, 'request', L.requestAudit(f));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * ContractorLetterDialog: a new letter (`l` null) or an edit, with its date and subject, when the contractor received
 * it and NHA's receiving copy, what the contractor did, and when the contractor replied and the reply letter.
 */
function editLetter(c, l) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const isNew = !l;
  l ||= { dateSent: today() };
  d.innerHTML = `<form novalidate>
    <h2>${isNew ? 'New letter to contractor' : 'Letter to contractor'}</h2>
    <label class="field">Date of letter<input type="date" name="dateSent" value="${esc(l.dateSent || '')}" required></label>
    <label class="field">Subject<input name="subject" value="${esc(l.subject || '')}" required autocomplete="off"></label>
    <label class="field">Date received by the contractor (blank: not yet)<input type="date" name="dateReceived" value="${esc(l.dateReceived || '')}"></label>
    ${fileField('receivedFileUrl', 'Receiving copy', l.receivedFileUrl)}
    <label class="field">Action taken by contractor<textarea name="actionTaken" rows="3">${esc(l.actionTaken || '')}</textarea></label>
    <label class="field">Date of reply (blank: no reply yet)<input type="date" name="dateReplied" value="${esc(l.dateReplied || '')}"></label>
    ${fileField('replyFileUrl', "Contractor's reply letter", l.replyFileUrl)}
    <p class="error" role="alert"></p>
    <div class="actions">${isNew ? '' : `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete letter</button>`}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  const values = () => {
    const f = Object.fromEntries(new FormData(form));
    return { dateSent: f.dateSent, subject: f.subject.trim(), actionTaken: f.actionTaken.trim(), dateReplied: f.dateReplied, dateReceived: f.dateReceived };
  };
  const initial = JSON.stringify(values());
  const done = () => { d.close(); d.remove(); };
  const close = () => ((JSON.stringify(values()) === initial && !hasPicked(form)) || confirm(isNew ? 'Discard this new letter?' : 'Discard your changes to this letter?')) && done();
  bindDelete(d, `Delete the letter "${l.subject || ''}"? This cannot be undone.`, () => saveEdits(c, [[l, null, L.deletedLetterAudit(l)]], 'letter'));
  // A signed receiving copy means the contractor has it; a reply letter in hand means the contractor replied.
  for (const [file, date] of [['receivedFileUrl', 'dateReceived'], ['replyFileUrl', 'dateReplied']]) {
    form[file].onchange = () => { if (form[file].files[0] && !form[date].value) form[date].value = today(); };
  }
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values(), errors = L.letterErrors(f);
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (!isNew && JSON.stringify(f) === initial && !hasPicked(form)) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const saved = { ...f, ...await uploadPicked(c, form) };
      await (isNew ? addRecord(c, ids => L.newLetterRecord(ids, saved), L.letterAudit(f)) : saveEdit(c, l, saved, 'letter', L.letterAudit(f)));
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/**
 * Wires a form's Delete button: asks `question` first, then runs `remove` with the form's buttons off and closes the
 * form. The record is marked deleted, as a phone's delete is uploaded; its files stay in the bucket.
 */
function bindDelete(d, question, remove) {
  const del = d.querySelector('[data-delete]'), save = d.querySelector('.btn'), err = d.querySelector('.error');
  if (!del) return;
  del.onclick = async () => {
    if (!confirm(question)) return;
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then delete again.'; return; }
    del.disabled = save.disabled = true; err.textContent = '';
    try {
      await remove();
      d.close(); d.remove();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not delete. Try again.';
      del.disabled = save.disabled = false;
    }
  };
}

/* ---------- Attaching files (Attachments.store and upload) ---------- */

/**
 * A form's file input for an attachment column; `attached` is the reference the record holds now, if any, and then a
 * box to remove it follows (no name, so it stays out of the form's fields).
 */
const fileField = (name, label, attached) => `<label class="field">${esc(label)} (photo or PDF)<input type="file" name="${name}" accept="image/*,application/pdf">
  ${attached ? '<small class="muted">A file is attached. Choosing one here replaces it.</small>' : ''}</label>
  ${attached ? `<label class="check"><input type="checkbox" data-remove="${name}">Remove the attached file</label>` : ''}`;

/** A form's text fields, without the files picked in it. */
const textFields = form => Object.fromEntries([...new FormData(form)].filter(([, v]) => typeof v === 'string'));
/** True when a file was picked in the form or an attached one is ticked for removal. */
const hasPicked = form => [...form.querySelectorAll('input[type=file]')].some(i => i.files[0]) || Boolean(form.querySelector('input[data-remove]:checked'));

/** What the phone keeps of a picked file: a PDF as it is, anything else as a JPEG no larger than `maxEdge`. [blob, extension, content type]. */
async function attachmentBlob(file, maxEdge = L.SCAN_MAX_EDGE) {
  const head = new Uint8Array(await file.slice(0, 4).arrayBuffer());
  if (String.fromCharCode(...head) === '%PDF') {
    if (file.size >= L.MAX_FILE_BYTES) throw new Error('The file must be smaller than 20 MB.');
    return [file, 'pdf', 'application/pdf'];
  }
  if (file.size > L.MAX_FILE_BYTES * 3) throw new Error('The file must be smaller than 20 MB.');
  let bitmap;
  try { bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' }); } catch { throw new Error('Only photos and PDF files can be attached.'); }
  const [width, height] = L.scaledSize(bitmap.width, bitmap.height, maxEdge);
  const canvas = Object.assign(document.createElement('canvas'), { width, height });
  const g = canvas.getContext('2d');
  g.fillStyle = '#fff'; // a see-through PNG would turn black as a JPEG
  g.fillRect(0, 0, width, height);
  g.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  const blob = await new Promise(done => canvas.toBlob(done, 'image/jpeg', 0.85));
  if (!blob) throw new Error('Only photos and PDF files can be attached.');
  return [blob, 'jpg', 'image/jpeg'];
}

/**
 * Uploads the files picked in a form to attachments/<projectUuid>/<id>.jpg|.pdf, where the phones look for them, and
 * returns { input name: its `nhafile:` reference } to save on the record ('' for a column whose Remove box is ticked).
 * shortcut: a replaced file, or one whose record then fails to save, stays in the bucket (only the phone that added a file cleans it up).
 */
async function uploadPicked(c, form) {
  // A ticked Remove box clears its column; a file picked for the same column wins.
  const refs = Object.fromEntries([...form.querySelectorAll('input[data-remove]:checked')].map(i => [i.dataset.remove, '']));
  for (const input of form.querySelectorAll('input[type=file]')) {
    if (input.files[0]) refs[input.name] = await uploadFile(c, await attachmentBlob(input.files[0]));
  }
  return refs;
}

/** Uploads one prepared file (attachmentBlob's result) to this project's attachments and returns its reference. */
async function uploadFile(c, [blob, ext, contentType]) {
  const refText = L.newFileRef(c.page.id, crypto.randomUUID(), ext);
  try {
    await uploadBytes(ref(storage, L.storagePathOf(refText)), blob, { contentType });
  } catch (x) {
    throw x.code === 'storage/unauthorized' ? Object.assign(new Error('denied'), { code: 'permission-denied' }) : new Error('The file could not be uploaded. Check the connection, then save again.');
  }
  return refText;
}

/**
 * A weekly report's site photos, as AddWeeklyReportDialog's photo strip: a title for each, take one off, add more.
 * Save rewrites only the report's two photo columns, with the app's "Edited Weekly Report" entry, and points the
 * project's card photo at the newest report photo left (ProjectViewModel.refreshSitePhoto).
 * shortcut: no geo-tag stamp, and a removed photo's file stays in the bucket.
 */
function editPhotos(c, report) {
  const refs = L.attachmentList(report.attachedPhotoUrlsJson), details = L.photoDetails(report.photoDetailsJson);
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  d.innerHTML = `<form novalidate>
    <h2>Site photos</h2>
    <p class="muted small">${esc(report.reportingWeek || '')}</p>
    ${refs.length ? `<ul class="photo-edit">${refs.map((refText, i) => `<li>
      ${L.isPdf(refText) ? fileLink(refText, 'Attached PDF') : L.storagePathOf(refText)
        ? `<a class="thumb" data-file="${esc(refText)}" data-alt="${esc(details[refText]?.title || 'Site photo')}" target="_blank" rel="noopener">${icon('photo_camera')}</a>`
        : `<span class="thumb gone">${icon('photo_camera')}<small>Not uploaded yet</small></span>`}
      <div><label class="field">Title<input data-title="${i}" value="${esc(details[refText]?.title || '')}" autocomplete="off"></label>
        <label class="check"><input type="checkbox" data-drop="${i}">Remove this photo</label></div>
    </li>`).join('')}</ul>` : emptyLine('No photos on this report yet.')}
    <label class="field">Add photos<input type="file" name="photos" accept="image/*" multiple></label>
    <div class="photo-new"></div>
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn'), fresh = d.querySelector('.photo-new');
  // Each picked photo gets a title box of its own.
  form.photos.onchange = () => {
    fresh.innerHTML = [...form.photos.files].map((file, i) => `<label class="field">Title for ${esc(file.name)}<input data-new-title="${i}" autocomplete="off"></label>`).join('');
  };
  const values = () => ({
    titles: Object.fromEntries(refs.map((refText, i) => [refText, form.querySelector(`[data-title="${i}"]`).value.trim()])),
    removed: refs.filter((_, i) => form.querySelector(`[data-drop="${i}"]`).checked),
    newTitles: [...form.photos.files].map((_, i) => fresh.querySelector(`[data-new-title="${i}"]`)?.value.trim() || ''),
  });
  const initial = JSON.stringify(values());
  const done = () => { d.close(); d.remove(); };
  const close = () => (JSON.stringify(values()) === initial || confirm('Discard your changes to these photos?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values();
    if (JSON.stringify(f) === initial) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      // Every photo is prepared before the first upload, so a file that is not a photo stops the save with nothing sent.
      const blobs = [];
      for (const file of form.photos.files) {
        const blob = await attachmentBlob(file, L.PHOTO_MAX_EDGE);
        if (blob[1] === 'pdf') throw new Error('Only photos can be added here.');
        blobs.push(blob);
      }
      const added = [];
      for (const [i, blob] of blobs.entries()) added.push([await uploadFile(c, blob), f.newTitles[i]]);
      const changes = L.editedPhotos(report, { titles: f.titles, removed: f.removed, added });
      const cover = L.latestSitePhoto(c.weekly.map(r => (r.uuid === report.uuid ? { ...r, ...changes } : r)));
      await saveEdits(c, [[report, changes, { actionType: 'Edited Weekly Report', details: `Modified report for ${report.reportingWeek}` }]], 'weekly report', { latestUpdatePhotoUrl: cover });
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  loadFiles(d);
  d.showModal();
}

/**
 * AddWeeklyReportDialog's Week, Crew and Work steps as one form, for a new report or a saved one (`report`). The
 * report's photos and 24-hour weather are changed on the saved report (Add photos, Edit weather). With an approved bar
 * chart the targets are its plan for the week's last day; they follow the week picked.
 */
function weeklyForm(c, report = null) {
  const d = document.createElement('dialog');
  d.className = 'help edit-form wk-form';
  const start = report ? L.weeklyStart(report) : { first: L.weekSaturday(today()), lines: [], equipment: [], activities: [{}], issues: [],
    manpower: L.MANPOWER_START.map(designation => ({ designation, count: 0 })) };
  const opt = (v, label = v, cur = '') => `<option value="${esc(v)}"${v === cur ? ' selected' : ''}>${esc(label)}</option>`;
  // A saved value outside the list stays a choice, so opening and saving does not change it.
  const pick = (choices, cur = '') => (cur && !choices.includes(cur) ? [cur, ...choices] : choices).map(v => opt(v, v, cur)).join('');
  const text = (r, key) => esc(r[key] || '');
  const count = r => `<label class="field n">No.<input data-f="count" type="number" min="0" step="1" value="${esc(r.count ?? 1)}"></label>`;
  // Remarks the form has no box for ride along, so an edit keeps them.
  const kept = r => `<input type="hidden" data-f="remarks" value="${text(r, 'remarks')}">`;
  const rowOf = {
    manpower: r => `<label class="field">Designation<input data-f="designation" list="wk-designations" value="${text(r, 'designation')}"></label>${count(r)}${kept(r)}`,
    equipment: r => `<label class="field">Name of equipment<input data-f="description" list="wk-equipment" value="${text(r, 'description')}"></label>${count(r)}
      <label class="field">Status<select data-f="status">${pick(L.EQUIPMENT_STATUSES, r.status)}</select></label>${kept(r)}`,
    activities: r => `<label class="field">From<select data-f="from">${pick(L.ACTIVITY_DAYS, r.from)}</select></label>
      <label class="field">To (optional)<select data-f="to">${opt('', 'Same day')}${pick(L.ACTIVITY_DAYS, r.to)}</select></label>
      <label class="field full">Activity<textarea data-f="description" rows="2">${text(r, 'description')}</textarea></label>
      <label class="field full">Remarks (optional)<input data-f="remarks" value="${text(r, 'remarks')}"></label>`,
    issues: r => `<label class="field full">Issue encountered<textarea data-f="description" rows="2">${text(r, 'description')}</textarea></label>
      <label class="field full">Action taken<textarea data-f="actionTaken" rows="2">${text(r, 'actionTaken')}</textarea></label>
      <label class="field">Action taken by<select data-f="actionBy">${opt('', 'Not set')}${pick(L.ISSUE_ACTION_BY, r.actionBy)}</select></label>${kept(r)}`,
  };
  const row = (kind, r = {}) => `<div class="wk-row">${rowOf[kind](r)}<button type="button" class="icon-btn" data-drop-row aria-label="Remove this row">${icon('close', 'sm')}</button></div>`;
  const list = (kind, title, label) => `<h3>${title}</h3><div class="wk-rows" data-rows="${kind}">${start[kind].map(r => row(kind, r)).join('')}</div>
    <button type="button" class="text-btn wk-add" data-add-row="${kind}">${icon('add_circle', 'sm')}${label}</button>`;
  d.innerHTML = `<form novalidate>
    <h2>${report ? 'Edit weekly report' : 'New weekly report'}</h2>
    <label class="field">First day of the week<input type="date" name="first" value="${esc(start.first)}" required></label>
    <p class="muted small" id="wk-week"></p>
    <h3>Weekly accomplishment</h3>
    <p class="muted small" id="wk-note"></p>
    <div class="wk-rows" id="wk-lines"></div>
    <p class="wk-total" id="wk-total" aria-live="polite"></p>
    ${list('manpower', 'Manpower', 'Add designation')}
    ${list('equipment', 'Equipment', 'Add equipment')}
    ${list('activities', 'Activities of the week', 'Add activity')}
    ${list('issues', 'Issues encountered / action taken', 'Add issue')}
    <datalist id="wk-designations">${L.MANPOWER_DESIGNATIONS.map(v => opt(v)).join('')}</datalist>
    <datalist id="wk-equipment">${L.EQUIPMENT_NAMES.map(v => opt(v)).join('')}</datalist>
    <p class="error" role="alert"></p>
    <div class="actions">${report && L.canDeleteReport(s.claims) ? `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete report</button>` : ''}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.actions .btn');
  const box = d.querySelector('#wk-lines');
  let lines = [];
  const rowsOf = kind => [...form.querySelectorAll(`[data-rows="${kind}"] .wk-row`)].map(r => Object.fromEntries([...r.querySelectorAll('[data-f]')].map(i => [i.dataset.f, i.value])));
  const typed = () => [...box.querySelectorAll('[data-line]')].map(el => ({ target: el.querySelector('[data-target]').value, actual: el.querySelector('[data-actual]').value }));
  const values = () => {
    const now = typed();
    return { first: form.first.value, lines: lines.map((l, k) => ({ name: l.name, weightPct: l.weightPct, ...now[k] })),
      manpower: rowsOf('manpower'), equipment: rowsOf('equipment'), activities: rowsOf('activities'), issues: rowsOf('issues') };
  };
  const total = () => {
    const t = L.weeklyTotals(values().lines);
    d.querySelector('#wk-total').textContent = `Overall: target ${pct(t.target)}, actual ${pct(t.actual)}, variance ${signedPct(t.actual - t.target)}`;
  };
  // The lines follow the week: with a chart their targets are its plan for the week's last day. What was typed stays.
  const drawLines = () => {
    if (!form.first.value) return;
    const label = L.weekLabel(form.first.value), was = box.children.length ? typed() : start.lines;
    lines = L.weeklyLines(c.project, c.documents, L.weekDays(label)[6]);
    const charted = lines[0].targetPct != null;
    d.querySelector('#wk-week').textContent = label;
    d.querySelector('#wk-note').textContent = charted ? 'Targets are the approved bar chart\'s plan for the last day of this week.'
      : lines.some(l => l.weightPct > 0) ? 'No bar chart yet: type each target.' : 'This project has no scope of works yet, so the overall figures stay at 0%.';
    box.innerHTML = lines.map((l, k) => `<fieldset class="wk-row" data-line><legend>${esc(l.name)} (${pct(l.weightPct)})</legend>
      <label class="field">Target %<input data-target inputmode="decimal" placeholder="0.00" value="${esc(charted ? l.targetPct.toFixed(2) : was[k]?.target ?? '')}"${charted ? ' readonly' : ''}></label>
      <label class="field">Actual %<input data-actual inputmode="decimal" placeholder="0.00" value="${esc(was[k]?.actual ?? '')}"></label></fieldset>`).join('');
    total();
  };
  form.first.onchange = drawLines;
  box.oninput = total;
  form.onclick = e => {
    const kind = e.target.closest('[data-add-row]')?.dataset.addRow;
    if (kind) {
      const rows = form.querySelector(`[data-rows="${kind}"]`);
      rows.insertAdjacentHTML('beforeend', row(kind));
      rows.lastElementChild.querySelector('[data-f]').focus();
    }
    e.target.closest('[data-drop-row]')?.closest('.wk-row').remove();
  };
  drawLines();
  const initial = JSON.stringify(values());
  const done = () => { d.close(); d.remove(); };
  const close = () => (JSON.stringify(values()) === initial || confirm(report ? 'Discard your changes to this weekly report?' : 'Discard this new weekly report?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values(), errors = L.weeklyErrors(f);
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (report && JSON.stringify(f) === initial) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      if (report) {
        const edit = L.editedWeeklyReport(report, f, c.project, c.weekly);
        await saveEdits(c, [[report, edit.changes, edit.audit]], 'weekly report', edit.projectFields);
      } else {
        const added = L.newWeeklyReport(f, c.project, s.profile.name || s.user.email, c.weekly);
        await addRecords(c, [[added.build, added.audit]], added.projectFields);
      }
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  // deleteWeeklyReport: the project's site photo becomes the newest left on the other reports.
  if (report) bindDelete(d, `Delete the weekly report for ${report.reportingWeek}? This cannot be undone.`, () => saveEdits(c, [[report, null, L.deletedWeeklyAudit(report)]], 'weekly report',
    { latestUpdatePhotoUrl: L.latestSitePhoto(c.weekly.filter(r => r.uuid !== report.uuid)) }));
  document.body.append(d);
  d.showModal();
}

/** A new record (`build` gets its ids and stamps) and its audit entry in one write, with the project's assignedUids. */
const addRecord = (c, build, entry) => addRecords(c, [[build, entry]]);

/**
 * addRecord for several records that must be added together (`items`: [build, audit entry or entries] each), all or
 * none; `projectFields` are saved on the project with them (saveProject).
 */
async function addRecords(c, items, projectFields = null) {
  const uid = s.user.uid;
  await runTransaction(db, async t => {
    const project = await t.get(doc(db, 'projects', c.page.id)), now = Date.now();
    const ids = () => ({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids: project.get('assignedUids') ?? [], uid, now });
    for (const [build, entry] of items) {
      const audits = [entry].flat().map(e => L.auditRecord({ ...ids(), user: s.profile.name || s.user.email, ...e }));
      for (const record of [build(ids()), ...audits]) t.set(doc(db, 'records', record.uuid), record);
    }
    saveProject(t, project, projectFields, uid, now);
  });
}

/** In a transaction: `fields` that differ from the project's are saved on it, one revision up, the way a phone uploads a project change. */
function saveProject(t, project, fields, uid, now) {
  if (fields && Object.entries(fields).some(([key, value]) => (project.get(key) ?? '') !== value)) {
    t.update(project.ref, { ...fields, revision: (Number(project.get('revision')) || 1) + 1, updatedAt: now, updatedByUid: uid });
  }
}

/**
 * Writes a record edit (`changes` merged into its data) and its audit log entry in one transaction, the way RecordSync
 * uploads: revision + 1, this edit's time and editor, the project's assignedUids. It refuses when the record changed
 * since the form opened. `noun` names the record in those messages.
 */
const saveEdit = (c, rec, changes, noun, entry) => saveEdits(c, [[rec, changes, entry]], noun);

/**
 * saveEdit for several records that must change together (`edits`: [record, changes, audit entry] each; null changes
 * delete it; the entry may be several, or null to log nothing, where the phone logs nothing), all or none. `projectFields`, when they differ from the project's, are saved on it in the same
 * transaction, one revision up, the way a phone uploads a project change.
 */
async function saveEdits(c, edits, noun, projectFields = null) {
  const uid = s.user.uid;
  await runTransaction(db, async t => {
    const [project, ...stored] = await Promise.all([t.get(doc(db, 'projects', c.page.id)), ...edits.map(([rec]) => t.get(doc(db, 'records', rec.uuid)))]);
    const now = Date.now();
    edits.forEach(([rec, changes, entry], i) => {
      const was = stored[i];
      if (!was.exists() || was.get('isDeleted') === true) throw new Error(`This ${noun} was deleted on another device.`);
      if (was.get('revision') !== rec.revision || was.get('updatedAt') !== rec.updatedAt) {
        throw new Error(`This ${noun} was just changed on another device. Close this form and edit it again.`);
      }
      const assignedUids = project.get('assignedUids') ?? was.get('assignedUids') ?? [];
      t.update(was.ref, changes ? L.editedRecord(was.data(), changes, { uid, now, assignedUids }) : L.deletedRecord(was.data(), { uid, now, assignedUids }));
      for (const e of [entry].flat().filter(Boolean)) {
        const audit = L.auditRecord({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids, uid, now, user: s.profile.name || s.user.email, ...e });
        t.set(doc(db, 'records', audit.uuid), audit);
      }
    });
    saveProject(t, project, projectFields, uid, now);
  });
}

/**
 * AddWeeklyReportDialog's 24-hour weather for one weekly report: pick a condition, then tap or drag over hours (or
 * "Whole day"). Save rewrites only the days changed, inside the report's record, with the app's audit entry.
 */
function editWeather(c, report) {
  const days = L.weatherForm(report), changed = new Set();
  let brush = 'RAINY';
  const label = Object.fromEntries(P.WEATHER.map(([k, l]) => [k, l]));
  const colour = Object.fromEntries(P.WEATHER.map(([k, , hex]) => [k, hex]));
  const hourName = h => `${h % 12 || 12} ${h < 12 ? 'AM' : 'PM'}`;
  const cell = (di, h) => `<button type="button" class="wx-h" data-d="${di}" data-h="${h}" style="background:${colour[days[di].hours[h]]}"
    aria-label="${esc(hourName(h))}: ${esc(label[days[di].hours[h]])}">${h % 12 || 12}</button>`;
  const d = document.createElement('dialog');
  d.className = 'help edit-form wx-edit';
  d.innerHTML = `<form novalidate>
    <h2>Edit weather</h2>
    <p class="muted small">${esc(report.reportingWeek)}. Pick a condition, then tap or drag over the hours.</p>
    <div class="seg wx-brush" role="group" aria-label="Condition">${L.WEATHER_KEYS.map(k =>
      `<button type="button" data-brush="${k}" aria-pressed="${k === brush}"><i style="background:${colour[k]}"></i>${esc(label[k])}</button>`).join('')}</div>
    ${days.map((day, di) => {
      const t = new Date(`${day.date}T00:00:00Z`), fmt = o => t.toLocaleDateString('en-US', { ...o, timeZone: 'UTC' });
      return `<fieldset class="wx-row"><legend>${fmt({ weekday: 'short' })}, ${fmt({ month: 'short', day: '2-digit' })}${day.logged ? '' : ' <span class="muted">(no log yet)</span>'}
        <button type="button" class="text-btn" data-all="${di}">Whole day</button>
        <select data-preset="${di}" aria-label="Preset for this day"><option value="">Preset…</option>${L.WEATHER_PRESETS.map(([name], i) => `<option value="${i}">${esc(name)}</option>`).join('')}</select></legend>
        <div class="wx-hours"><span class="muted">AM</span>${Array.from({ length: 12 }, (_, h) => cell(di, h)).join('')}
        <span class="muted">PM</span>${Array.from({ length: 12 }, (_, h) => cell(di, h + 12)).join('')}</div></fieldset>`;
    }).join('')}
    <p class="error" role="alert"></p>
    <div class="actions"><button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">Save</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  const paint = (di, hours, as = () => brush) => {
    for (const h of hours) {
      const k = as(h);
      if (days[di].hours[h] === k) continue;
      days[di].hours[h] = k; changed.add(days[di].date);
      const b = form.querySelector(`[data-d="${di}"][data-h="${h}"]`);
      b.style.background = colour[k]; b.setAttribute('aria-label', `${hourName(h)}: ${label[k]}`);
    }
  };
  form.onchange = e => {
    const sel = e.target.closest('[data-preset]');
    if (!sel || sel.value === '') return;
    const [, hours] = L.WEATHER_PRESETS[Number(sel.value)];
    paint(Number(sel.dataset.preset), [...Array(24).keys()], h => hours[h]);
    sel.value = '';
  };
  // A mouse or pen paints from pointer down and drags; a finger paints on tap, so it can still scroll the form.
  let dragging = null, pressed = false; // dragging: the last hour painted, {di, h}
  form.onclick = e => {
    const b = e.target.closest('button');
    if (b?.dataset.brush) { brush = b.dataset.brush; form.querySelectorAll('[data-brush]').forEach(x => x.setAttribute('aria-pressed', x === b)); }
    else if (b?.dataset.all !== undefined) paint(Number(b.dataset.all), [...Array(24).keys()]);
    else if (b?.dataset.h !== undefined && !pressed) paint(Number(b.dataset.d), [Number(b.dataset.h)]); // a tap or the keyboard
    pressed = false;
  };
  const under = e => document.elementFromPoint(e.clientX, e.clientY)?.closest?.('.wx-h');
  form.addEventListener('pointerdown', e => {
    const b = under(e);
    if (!b || e.pointerType === 'touch') return;
    pressed = true; e.preventDefault();
    dragging = { di: Number(b.dataset.d), h: Number(b.dataset.h) };
    paint(dragging.di, [dragging.h]);
  });
  // Pointer events skip cells when the pointer moves fast, so fill every hour between the last one and this one.
  form.addEventListener('pointermove', e => {
    const b = dragging && under(e);
    if (!b) return;
    const di = Number(b.dataset.d), h = Number(b.dataset.h);
    const from = di === dragging.di ? Math.min(h, dragging.h) : h, to = di === dragging.di ? Math.max(h, dragging.h) : h;
    paint(di, Array.from({ length: to - from + 1 }, (_, i) => from + i));
    dragging = { di, h };
  });
  const up = () => { dragging = null; };
  addEventListener('pointerup', up);
  const done = () => { removeEventListener('pointerup', up); d.close(); d.remove(); };
  const close = () => (!changed.size || confirm('Discard your changes to this weather?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  form.onsubmit = async e => {
    e.preventDefault();
    const changes = L.weatherChanges(report, days, changed);
    if (!changes) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      // ProjectViewModel.updateWeeklyReportWithWeather's entry, word for word.
      await saveEdit(c, report, changes, 'weekly report', { actionType: 'Edited Weekly Report', details: `Modified report and weather for ${report.reportingWeek}` });
      done();
    } catch (x) {
      err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.';
      save.disabled = false;
    }
  };
  document.body.append(d);
  d.showModal();
}

/** MainActivity's addProjectIssue: a new issue logged by this account, and ProjectViewModel's audit entry, in one batch. */
async function addIssue(c, f) {
  const uid = s.user.uid, now = Date.now();
  const project = await getDoc(doc(db, 'projects', c.page.id));
  const assignedUids = project.get('assignedUids') ?? [];
  const user = s.profile.name || s.user.email;
  const issue = L.newIssueRecord({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids, uid, now, loggedBy: user }, f);
  const audit = L.auditRecord({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids, uid, now, user, ...L.newIssueAudit(f) });
  const batch = writeBatch(db);
  batch.set(doc(db, 'records', issue.uuid), issue);
  batch.set(doc(db, 'records', audit.uuid), audit);
  await batch.commit();
}

/**
 * AddPaymentDialog for a new billing or, with `old`, an edit of that one: the same fields, choices and checks, a live
 * gross / retention / net. Save writes a new billing with its share and balance, or the edit with the later balances
 * it moves, each with the app's audit entry.
 */
function billingForm(c, old = null) {
  // An edited billing keeps its type and retention as choices, whatever the project type is now.
  const housing = /housing/i.test(c.project.projectType || '') || Boolean(old?.billingType);
  const types = !old?.billingType || L.HOUSING_BILLING_TYPES.includes(old.billingType) ? L.HOUSING_BILLING_TYPES : [old.billingType, ...L.HOUSING_BILLING_TYPES];
  const oldRetention = Number(old?.retentionPct) || 0, retentions = L.RETENTIONS.includes(oldRetention) ? L.RETENTIONS : [...L.RETENTIONS, oldRetention];
  const money = v => (Number(v) > 0 ? Number(v).toFixed(2) : '');
  const d = document.createElement('dialog');
  d.className = 'help edit-form';
  const opt = (v, label = v, cur = '') => `<option value="${esc(v)}"${v === cur ? ' selected' : ''}>${esc(label)}</option>`;
  d.innerHTML = `<form novalidate>
    <h2>${old ? 'Edit billing' : 'Add billing / disbursement voucher'}</h2>
    <label class="field">Billing name<input name="name" value="${esc(old?.name || '')}" placeholder="e.g. 3rd Progress Billing" autocomplete="off"></label>
    <label class="field">DV No. (Disbursement Voucher)<input name="dvNo" value="${esc(old?.dvNo || '')}" placeholder="e.g. DV-2026-08-0199" autocomplete="off"></label>
    <label class="field">Date<input type="date" name="date" value="${esc(old ? old.date || '' : today())}" required></label>
    <label class="field">Period covered<input name="periodCovered" value="${esc(old ? old.periodCovered || '' : L.monthPeriod(today()))}" autocomplete="off"></label>
    ${housing ? `<label class="field">Billed for<select name="billingType">${opt('', 'Other')}${types.map(t => opt(t, t, old?.billingType)).join('')}</select></label>` : ''}
    <label class="field" data-by-unit style="display:none">Number of lots<input name="units" value="${esc(Number(old?.units) > 0 ? String(old.units) : '')}" inputmode="numeric" autocomplete="off"></label>
    <label class="field" data-by-unit style="display:none">Amount per lot (PHP)<input name="unitPrice" value="${money(old?.unitPrice)}" inputmode="decimal" autocomplete="off"></label>
    <label class="field" data-gross>Gross amount (PHP)<input name="grossAmount" value="${money(old?.grossAmount)}" inputmode="decimal" placeholder="e.g. 15000000" autocomplete="off"></label>
    <label class="field">Retention<select name="retentionPct">${retentions.map(r => opt(String(r), r ? `${r}%` : 'None', String(oldRetention))).join('')}</select></label>
    <div class="billing-preview" aria-live="polite"></div>
    ${fileField('fileUrl', 'Scanned DV', old?.fileUrl)}
    <p class="error" role="alert"></p>
    <div class="actions">${old ? `<button type="button" class="text-btn danger" data-delete>${icon('delete', 'sm')}Delete billing</button>` : ''}<button type="button" class="text-btn" data-cancel>Cancel</button><button class="btn">${old ? 'Save changes' : 'Save billing'}</button></div>
  </form>`;
  const form = d.querySelector('form'), err = d.querySelector('.error'), save = d.querySelector('.btn');
  const values = () => ({ billingType: '', ...textFields(form) });
  // Picking a housing type starts its amount from the latest billing of that type.
  const lastPrice = type => [...c.payments].reverse().find(p => p.billingType === type && Number(p.unitPrice) > 0)?.unitPrice;
  const showType = () => {
    const type = values().billingType, unit = L.billingUnitName(type);
    // .field sets its own display, so the hidden attribute would not hide it.
    const [units, price] = d.querySelectorAll('[data-by-unit]');
    for (const el of [units, price]) el.style.display = type ? '' : 'none';
    d.querySelector('[data-gross]').style.display = type ? 'none' : '';
    units.firstChild.textContent = `Number of ${unit}s`;
    price.firstChild.textContent = `Amount per ${unit} (PHP)`;
  };
  const onType = () => {
    showType();
    const last = lastPrice(values().billingType);
    form.unitPrice.value = last ? Number(last).toFixed(2) : '';
  };
  const preview = () => {
    const f = values(), gross = L.billingGross(f), r = Number(f.retentionPct) || 0;
    d.querySelector('.billing-preview').innerHTML = gross > 0
      ? `${kv('Gross amount', L.formatPhp(gross))}${r ? kv(`Retention (${r}%)`, L.formatPhp(gross * r / 100)) : ''}${kv('Net amount', L.formatPhp(gross * (1 - r / 100)))}` : '';
  };
  if (housing) form.billingType.onchange = () => { onType(); preview(); };
  form.oninput = preview;
  if (old) { showType(); preview(); }
  const initial = JSON.stringify(values());
  const done = () => { d.close(); d.remove(); };
  const close = () => ((JSON.stringify(values()) === initial && !hasPicked(form)) || confirm(old ? 'Discard your changes to this billing?' : 'Discard this new billing?')) && done();
  d.querySelector('[data-cancel]').onclick = close;
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  const failed = x => { err.textContent = x.code === 'permission-denied' ? 'This account cannot edit this project.' : x.message || 'Could not save. Try again.'; };
  const del = d.querySelector('[data-delete]');
  if (del) del.onclick = async () => {
    const edits = L.deletedBillingEdits(c.payments, old, c.cost), later = edits.length - 1;
    if (!confirm(`Delete "${old.name || 'this billing'}"? This cannot be undone.${later ? ` The balance of ${plural(later, 'later billing')} will be recalculated.` : ''}`)) return;
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then delete again.'; return; }
    del.disabled = save.disabled = true; err.textContent = '';
    try {
      await saveEdits(c, edits.map(([p, changes]) => [p, changes, changes ? L.editedPaymentAudit({ ...p, ...changes }) : L.deletedPaymentAudit(p)]), 'billing');
      done();
    } catch (x) { failed(x); del.disabled = save.disabled = false; }
  };
  form.onsubmit = async e => {
    e.preventDefault();
    const f = values(), errors = L.billingErrors(f);
    if (errors.length) { err.textContent = errors.join(' '); return; }
    if (old && JSON.stringify(f) === initial && !hasPicked(form)) { done(); return; }
    if (!navigator.onLine) { err.textContent = 'You are offline. Connect to the internet, then save again.'; return; }
    save.disabled = true; err.textContent = '';
    try {
      const refs = await uploadPicked(c, form);
      // Each billing saved gets its own entry, as MoneySection sends each one to updateProjectPayment; the scan goes on the edited one.
      await (old ? saveEdits(c, L.editedBillings(c.payments, old, f, c.cost).map(([p, changes]) => [p, p === old ? { ...changes, ...refs } : changes, L.editedPaymentAudit({ ...p, ...changes })]), 'billing')
        : addBilling(c, { ...f, ...refs }));
      done();
    } catch (x) { failed(x); save.disabled = false; }
  };
  document.body.append(d);
  d.showModal();
}

/** ProjectViewModel.addProjectPayment: the billing and its audit entry in one batch, against the billings shown now. */
async function addBilling(c, f) {
  const uid = s.user.uid, now = Date.now();
  const project = await getDoc(doc(db, 'projects', c.page.id));
  const assignedUids = project.get('assignedUids') ?? [];
  const payment = L.newPaymentRecord({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids, uid, now }, f, c.payments, c.cost);
  const audit = L.auditRecord({ uuid: crypto.randomUUID(), projectUuid: c.page.id, assignedUids, uid, now, user: s.profile.name || s.user.email, ...L.newPaymentAudit(payment) });
  const batch = writeBatch(db);
  batch.set(doc(db, 'records', payment.uuid), payment);
  batch.set(doc(db, 'records', audit.uuid), audit);
  await batch.commit();
}

/** PhotoGalleryTab: one album per reporting week, in report order. */
function photosView({ weekly, project }) {
  const editable = L.canEdit(project, s.user.uid, s.claims);
  const albums = weekly.map(r => [r, L.attachmentList(r.attachedPhotoUrlsJson)]).filter(([, p]) => p.length);
  const total = albums.reduce((t, [, p]) => t + p.length, 0);
  if (!total) return `<div class="card empty">${icon('photo_library')}<p>No site update photos uploaded yet</p><small>Update photos attached to weekly reports will appear here.</small></div>`;
  return `<h2 class="list-title">Project update photo gallery (${plural(total, 'photo')})</h2>${albums.map(([r, refs]) => `
    <section class="card section album"><div class="section-head">${icon('photo_library')}<h2>${esc(r.reportingWeek)} · ${plural(refs.length, 'photo')}</h2>${editable ? photosButton(r, refs.length) : ''}</div>
      <p class="muted">By ${esc(r.submittedByStaff || '-')}</p>${photoGrid(refs, L.photoDetails(r.photoDetailsJson))}</section>`).join('')}`;
}

/* ---------- Payments (MoneySection, PaymentCard) ---------- */

function paymentsBody({ project, payments, cost, billed }) {
  const actual = Number(project.actualAccomplishment) || 0, editable = L.canEdit(project, s.user.uid, s.claims);
  const summary = section('Billing summary', 'payments', `
    ${kv('Current contract cost', L.formatPhp(cost))}
    ${kv('Total billed', payments.length ? L.formatPhp(billed) : 'No billings yet')}
    ${kv('Balance', L.formatPhp(cost - billed))}
    <div class="two-bars">${progressLine('Billed', L.billedLabel(billed, cost), '', 'muted', cost > 0 ? billed / cost : 0, 'billed')}${progressLine('Work done', pct(actual), '', 'muted', actual / 100, 'neutral')}</div>
    ${editable ? `<button class="btn billing-add" id="billing-add">${icon('add_circle', 'sm')}Add billing</button>` : ''}`);
  if (!payments.length) return `<div class="stack">${summary}<div class="empty-ledger">${icon('receipt_long')}<p>No billings yet</p></div></div>`;
  return `<div class="stack">${summary}<ul class="pay-cards">${L.netBillings(payments).map(p => {
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
      ${editable ? `<button class="text-btn entry-edit" data-edit-billing="${esc(p.uuid)}">${icon('edit', 'sm')}Edit</button>` : ''}
    </li>`;
  }).join('')}</ul></div>`;
}

/* ---------- Documents (DocumentsSection, ContractorLettersCard, DeveloperRequestsCard) ---------- */

/** LogEntry: a title and its lines (blank ones skipped), with any attached file under them. */
const entry = (title, lines, extra = '', cls = '') => `<div class="entry ${cls}"><b>${esc(title)}</b>${lines.filter(x => x && x.trim()).map(x => `<span>${esc(x)}</span>`).join('')}${extra}</div>`;
/** An order's attached copy and, when `editable`, the button that opens its status. */
const orderExtra = (d, editable) => (d.fileUrl ? fileLink(d.fileUrl, 'Attached copy') : '')
  + (editable ? `<button class="text-btn entry-edit" data-edit-order="${esc(d.uuid)}">${icon('edit', 'sm')}Update status</button>` : '');
const orderEntry = (d, editable, cls = '') => entry(L.orderLabel(d.documentName), [`Status: ${d.status || ''}`, L.withEnd(d.remarks)], orderExtra(d, editable), cls);
/** `addType`, when set, is the kind of order the card's Add button files. */
const ordersCard = (title, ic, items, emptyText, addType = '') => section(title, ic, items || emptyLine(emptyText), '',
  addType ? `<button class="text-btn" data-add-order="${esc(addType)}">${icon('add_circle', 'sm')}Add</button>` : '');

function documentsBody(c, chip) {
  const { of, documents, now } = c;
  if (chip === 'Audit log') return auditView(c);
  const named = text => documents.filter(d => (d.documentName || '').toLowerCase().includes(text));
  const editable = L.canEdit(c.project, s.user.uid, s.claims), [ws, , wr, vo, te] = L.REQUEST_TYPES, add = type => (editable ? type : '');
  if (chip === 'Time Extensions') return ordersCard('Time extensions', 'more_time', named('time extension').map(d => orderEntry(d, editable)).join(''), 'No time extensions.', add(te));
  if (chip === 'Variation Orders') {
    const vos = named('variation order');
    return ordersCard(`Variation orders (${vos.length})`, 'receipt_long', vos.map(d => entry(d.documentName, L.variationLines(d), orderExtra(d, editable))).join(''), 'No variation orders recorded.', add(vo));
  }
  if (chip === 'Letters to Contractor') return lettersCard(L.mergedLetters(of('contractor_letters'), documents), editable);
  if (chip === 'Developer Requests') return requestsCard(of('developer_requests'), now, editable);
  if (chip === 'Related Files') return relatedCard(L.relatedFiles(documents), editable);
  const suspensions = documents.filter(L.isSuspension);
  const chains = suspensions.map(ws => [ws, L.continuances(ws, documents)]);
  // A continuance whose suspension was deleted still shows, at the end.
  const listed = new Set(chains.flatMap(([ws, list]) => [ws, ...list]));
  const orphans = named('work suspension').filter(d => !listed.has(d));
  return `<div class="stack">
    ${ordersCard('Work suspension orders', 'pause_circle', chains.map(([s0, list]) => orderEntry(s0, editable) + list.map(d => orderEntry(d, editable, 'nested')).join('')).join('') + orphans.map(d => orderEntry(d, editable)).join(''), 'No work suspension orders.', add(ws))}
    ${ordersCard('Work resumption orders', 'play_circle', named('work resumption').map(d => orderEntry(d, editable)).join(''), 'No work resumption orders.', add(wr))}
  </div>`;
}

/**
 * ContractorLettersCard as a table: date and receipt, subject, the contractor's action, and the reply letter; `editable`
 * adds New letter, and Edit on the letter records.
 * shortcut: an old "Letter to Contractor No. N" document has no Edit (the phone turns it into a letter record on its first edit).
 */
function lettersCard(letters, editable = false) {
  const add = editable ? `<button class="text-btn" id="letter-add">${icon('add_circle', 'sm')}New letter</button>` : '';
  if (!letters.length) return section('Letters to contractor', 'mail', emptyLine('No letters to the contractor yet.'), '', add);
  return section('Letters to contractor', 'mail', table(['Date', 'Subject', 'Action taken by contractor', 'Reply', ...(editable ? [''] : [])], letters.map(l => {
    const received = l.dateReceived ? `Received ${L.displayDate(l.dateReceived)}` : null;
    const receipt = l.receivedFileUrl ? fileLink(l.receivedFileUrl, received || 'Receiving copy') : `<small class="${received ? 'muted' : 'n-watch'}">${esc(received || 'Not received')}</small>`;
    const reply = l.replyFileUrl ? fileLink(l.replyFileUrl, 'Reply') : l.dateReplied ? '<small class="muted">Replied</small>' : '<span class="muted">-</span>';
    return `<tr><td class="nw">${esc(L.displayDate(l.dateSent || '-'))}<br>${receipt}</td><td>${esc(l.subject || '-')}</td>
      <td>${l.actionTaken ? esc(l.actionTaken) : '<span class="n-watch">None yet</span>'}</td><td>${reply}</td>
      ${editable ? `<td>${l.type === 'contractor_letters' ? `<button class="text-btn entry-edit" data-edit-letter="${esc(l.uuid)}">${icon('edit', 'sm')}Edit</button>` : ''}</td>` : ''}</tr>`;
  }).join('')), '', add);
}

/** DeveloperRequestsCard: requests not yet packaged stand out, with the days since NHA received them; `editable` adds Add request and Edit. */
function requestsCard(requests, now, editable = false) {
  const add = editable ? `<button class="text-btn" id="request-add">${icon('add_circle', 'sm')}Add request</button>` : '';
  return section('Developer requests', 'move_to_inbox', requests.length ? requests.map(r => {
    const waiting = ISO.test(r.dateReceived || '') ? Math.round((Date.parse(now) - Date.parse(r.dateReceived)) / 86400000) : null;
    const title = L.requestTitle(r);
    return entry(title, [
      `Requested: ${r.dateRequested || '-'}  ·  Received: ${r.dateReceived || '-'}`,
      r.datePackaged ? `Packaged: ${r.datePackaged}` : `NOT YET PACKAGED${waiting == null ? '' : ` · ${waiting} days since received`}`,
      title === (r.requestType || '-') && r.details?.trim() && `Details: ${r.details}`, // an order number is in the title instead
    ], (r.requestFileUrl ? fileLink(r.requestFileUrl, 'Request letter') : '')
      + (editable ? `<button class="text-btn entry-edit" data-edit-request="${esc(r.uuid)}">${icon('edit', 'sm')}${r.datePackaged ? 'Edit' : 'Edit or mark packaged'}</button>` : ''),
    r.datePackaged ? '' : 'waiting');
  }).join('') : emptyLine('No requests from the developer.'), '', add);
}

/** RelatedFilesList: other project files with their status and attached copy; `editable` adds Add file, and the button that attaches a copy or deletes. */
function relatedCard(files, editable = false) {
  const level = st => (st === 'Approved' ? 'ok' : st === 'Notarized' || st === 'Signed' ? 'primary' : st === 'Submitted' ? 'watch' : 'neutral');
  return section(`Related files (${files.length})`, 'folder_open', files.length ? files.map(d => entry(d.documentName, [d.remarks || 'N/A'],
    `<span class="badge lv-${level(d.status)}">${esc(d.status || 'Pending')}</span>${d.fileUrl ? fileLink(d.fileUrl, 'Attached copy') : '<small class="muted">No copy attached</small>'}`
    + (editable ? `<button class="text-btn entry-edit" data-edit-related="${esc(d.uuid)}">${icon(d.fileUrl ? 'edit' : 'upload_file', 'sm')}${d.fileUrl ? 'Edit' : 'Attach copy'}</button>` : ''))).join('')
    : emptyLine('No related project files or scanned documents uploaded yet.'), '',
  editable ? `<button class="text-btn" id="related-add">${icon('add_circle', 'sm')}Add file</button>` : '');
}

/* ---------- Weekly report print (BlueBookForms.weeklyReport on an A4 sheet) ---------- */

async function drawPrint(c) {
  if (/^\d{4}-\d{2}$/.test(c.page.sub || '')) return drawMonthlyPrint(c);
  if (c.page.sub === 'sdp') return drawSdpPrint(c);
  if (c.page.sub === 'profile') return drawProfilePrint(c);
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
    project, report, docs: documents, photos, banner: 'blue-book-header.png',
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

/** The Project Profile sheet (BlueBookForms.projectProfile, Print on the Summary's profile card) for `#/p/<id>/print/profile`. */
function drawProfilePrint({ page, project, documents }) {
  document.title = `Project Profile - ${project.name}`; // the file name "Save as PDF" offers, as on the phone
  // 12 mm page margins, as the phone prints this sheet (not the 6 mm of its full-page prints).
  main.innerHTML = `<style>@page { margin: 12mm; }</style><div class="print-bar no-print"><a class="text-btn back" href="#/p/${esc(page.id)}">${icon('arrow_back', 'sm')}Summary</a>
      <button class="btn" id="do-print">${icon('print', 'sm')}Print</button></div>
    <p class="muted no-print print-note">A4, as the phone prints it. Choose "Save as PDF" in the print window to keep a copy.</p>
    <div class="bb-scroll"><div class="bb" id="sheet">${P.projectProfileHtml({
      project, docs: documents, asOf: today(), banner: 'blue-book-header.png',
      // The phone's Prepared By is the person printing; an account without an office reads as the district office.
      preparer: { name: s.profile.name || '', title: s.profile.title || '', office: s.profile.office || 'Bulacan District Office' },
    })}</div></div>`;
  document.getElementById('do-print').onclick = () => window.print();
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
  try { plans = await Promise.all(chosen.map(x => planDrawing(x))); } catch (e) {
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
 * across A4, each lot filled with its colour and outlined (unless `withLots` is false), turned the way the plan is turned
 * on screen. Returns {image: PNG data URI, width, height, place(x, y) → that plan point in image pixels, problem};
 * when the PDF cannot be loaded, `problem` says why and the lots are drawn alone.
 */
async function planDrawing({ plan, lots }, withLots = true) {
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
    const px = (x, y) => [(x - f[0]) / (f[2] - f[0]) * w, (y - f[1]) / (f[3] - f[1]) * h];
    ctx.lineWidth = 2;
    for (const [l, pts] of withLots ? polys : []) {
      if (pts.length < 3) continue;
      ctx.beginPath();
      pts.forEach(([x, y], i) => ctx[i ? 'lineTo' : 'moveTo'](...px(x, y)));
      ctx.closePath();
      ctx.fillStyle = ctx.strokeStyle = L.lotPrintHex(l);
      ctx.globalAlpha = 115 / 255;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.stroke();
    }
    const deg = ((Number(plan.rotation) || 0) % 360 + 360) % 360;
    if (!deg) return { image: canvas.toDataURL('image/png'), width: w, height: h, place: px, problem };
    const rad = deg * Math.PI / 180, cos = Math.abs(Math.cos(rad)), sin = Math.abs(Math.sin(rad));
    const out = Object.assign(document.createElement('canvas'), { width: Math.round(w * cos + h * sin), height: Math.round(w * sin + h * cos) });
    // The same clockwise turn about the centre, for points.
    const c = Math.cos(rad), sn = Math.sin(rad);
    const place = (x, y) => { const [a, b] = px(x, y), dx = a - w / 2, dy = b - h / 2; return [dx * c - dy * sn + out.width / 2, dx * sn + dy * c + out.height / 2]; };
    const o = out.getContext('2d');
    o.fillStyle = '#fff';
    o.fillRect(0, 0, out.width, out.height);
    o.translate(out.width / 2, out.height / 2);
    o.rotate(rad); // clockwise, as Matrix.postRotate on the phone
    o.drawImage(canvas, -w / 2, -h / 2);
    return { image: out.toDataURL('image/png'), width: out.width, height: out.height, place, problem };
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
