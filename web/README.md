# NHA Monitor Web (view only)

A read-only web version of the NHA Monitor app. It reads the same Firestore data as the phones
(`projects` and `records` on `nha-monitor-bulacan-2026`). It never writes, so phones on any
version keep working.

- Sign-in uses the same email and password as the app.
- Roles come from the same custom claims. The Super Admin, Super Viewers and accounts with
  "See all projects" see every project. Everyone else sees only the projects assigned to them.
  Unassigned projects are tagged **View only**.
- Screens: the dashboard (counts, search, project list) and the project **Summary** (profile,
  key dates, contract cost, latest weekly report, open issues, status of payment).

Plain HTML and JavaScript modules. There is no build step. The Firebase JS SDK 10.12.2 loads from
Google's CDN. `logic.js` holds the rules copied from the Android app (contract cost, days left,
net amounts...). Each rule names the Kotlin it mirrors.

## Run locally (emulators, no live data)

You need Node and the Firebase CLI. The emulators also need Java; Android Studio's
`jbr\bin` works. From the repo root:

```bash
firebase emulators:start --only auth,firestore,storage,hosting --project demo-nha
```

In a second terminal:

```bash
node web/emulator/seed.mjs
```

Open http://127.0.0.1:5000/?emulator and sign in with one of the test accounts listed at the top of
`web/emulator/seed.mjs`. Without `?emulator` the page talks to the live project.

Tests for the copied rules:

```bash
node --test web/logic.test.mjs
```

## One-time setup for the live project

1. Firebase console > nha-monitor-bulacan-2026 > Project settings > Your apps > **Add app > Web**
   (no Hosting checkbox needed). Copy `apiKey` and `appId` into `web/firebase-config.js`. These
   values are public by design, and the security rules protect the data.
2. Authentication > Settings > **Authorized domains**: `nha-monitor-bulacan-2026.web.app` and
   `localhost` are there by default. Add any custom domain you use.

## Deploy

`firebase.json` now serves `web/` (it used to serve the old `public/` prototype, which pointed
at another Firebase project). Deploy hosting only:

```bash
firebase deploy --only hosting --project nha-monitor-bulacan-2026
```

The site is then live at https://nha-monitor-bulacan-2026.web.app. Rules and functions are not
touched by this command.

## Not in this first version

Progress, Payments and Documents sections, weekly report printing, editing, and photos other
than the Summary's site photo.
