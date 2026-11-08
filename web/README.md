# NHA Monitor Web (view only)

A read-only web version of the NHA Monitor app. It reads the same Firestore data as the phones
(`projects` and `records` on `nha-monitor-bulacan-2026`). It never writes, so phones on any
version keep working.

- Sign-in uses the same email and password as the app.
- Roles come from the same custom claims. The Super Admin, Super Viewers and accounts with
  "See all projects" see every project. Everyone else sees only the projects assigned to them.
  Unassigned projects are tagged **View only**, except for Super Viewers (same as the app).
- Screens: the dashboard (project count, total cost, status pills, attention filters, search, project cards with their own
  Work done and Billed; portfolio averages are deliberately not shown) and the project **Summary** (profile,
  key dates, contract cost, latest weekly report, open issues, status of payment).

The layouts, type and components follow the Android app (`app/src/main/java/com/example/ui/`).
The colours are a dark "command center" palette (a #0B0F17 canvas with a faint dot grid, #111827
panels) with emerald / amber / rose status colours. The site is always dark, even on a device set
to light mode. The dashboard's attention counts use the app's ProjectAttention
thresholds. Inter is bundled in `web/fonts/` (SIL Open Font License, see `licenses/Inter-OFL.txt`).
The icons are Material Symbols from Google Fonts. See `DESIGN.md` at the repo root for the tokens.

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

1. Done on 2026-11-07: the Web app "NHA Monitor Web" is registered and its config is in
   `web/firebase-config.js`. These values are public by design, and the security rules protect
   the data. To print the config again:
   `firebase apps:sdkconfig WEB 1:1091951535339:web:9f23ffea71ebca0f868aae`.
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
