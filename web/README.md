# NHA Monitor Web

A web version of the NHA Monitor app. It reads the same Firestore data as the phones
(`projects` and `records` on `nha-monitor-bulacan-2026`). Its record writes are logging and editing issues (Progress → Issues → Log issue / Edit), adding a billing (Payments → Add billing) and a weekly report's
24-hour weather (Progress → Weather → Edit weather),
for the people the app lets edit: approved Engineer Admins and Field Engineers assigned to the project, and the Super
Admin. A new issue is written as the phones upload one (revision 1, logged by the signed-in name); an edit as the phones
write a change (revision up by one, a new edit time, every other field kept) together with an audit log entry with "Website" as the device, so phones take it like another phone's change.
A phone with its own unsent edit of the same issue keeps its version. The Cloud Function
`notifySuperAdminsOnAudit` pushes that audit entry to the Super Admin's phones while the project is on-going, as it does for app edits. Everything else
is read only.

- Sign-in uses the same email and password as the app.
- **Create account** on the sign-in screen registers the way the app's sign-up does: a Firebase Auth account, then
  its `users/{uid}` profile (name, position, office, and Field Engineer or Viewer). `syncUserCustomClaims` sets the
  role and alerts the Super Admin, the account waits for approval before it can edit, and the page goes back to
  sign in like the app. Trying again with the same email and password finishes a profile that never saved.
- **Profile** (in the header) is the app's "Update Profile & Position": full name, position and office, all required.
  It updates only those fields of your existing `users/{uid}` profile (an account without one sets it up in the app
  first); printed forms here use the new values at once, and phones pick them up when they next sync the profile.
- **Add billing** is the app's Add Billing / Disbursement Voucher form: name, DV No., date, period covered, what a housing
  project bills for (units × amount per lot or unit, the amount filled in from the latest billing of that type) or the gross,
  and retention. Share and balance are saved from the gross with the current contract cost, as the app saves them, with the
  app's "Payment Creation" audit entry. The scanned DV is attached on the phone for now: the bucket's CORS allows downloads only.
- **Edit weather** changes a weekly report's weather the way the app's weekly report form does: the report's record
  gets the new `_weather` rows (revision up by one), with the app's "Edited Weekly Report" audit entry. Only the days
  changed are rewritten; the other stored days stay exactly as they were.
- **Users** (Super Admin only, in the header) is the app's "Manage users & roles": approve a new account, give one of
  Engineer Admin, Field Engineer, Viewer or Super Viewer, and turn "See all projects" on or off. Each change is one
  `users/{uid}` update, as the app writes it; `syncUserCustomClaims` updates that person's access.
- Roles come from the same custom claims. The Super Admin, Super Viewers and accounts with
  "See all projects" see every project. Everyone else sees only the projects assigned to them.
  Unassigned projects are tagged **View only**, except for Super Viewers (same as the app).
- Screens: the dashboard (project count, total cost, status pills, attention filters, search, project cards with their own
  Work done and Billed; portfolio averages are deliberately not shown) and four project tabs:
  - **Summary**: profile, key dates with the revised completion from approved orders, contract
    cost, latest weekly report, open issues, status of payment, and the lots status card (the six
    `SdpSummary` bands) for housing projects.
  - **Progress**: weekly reports (all sections and photos), monthly reports by month, every
    photo, and for housing projects the **Lots** of each site's plan in use: the phone's KPIs,
    its search and filters, and the plan PDF with each lot coloured over it, as on the phone (drag to move, wheel
    or pinch to zoom, the zoom bar, double-click for 250%). Click a lot for its card, and "History & details" for its
    status, billing and photos. The filters dim the lots that do not match; billed lots have a dashed blue edge. **Weather** shows each logged day as the phone's 24-hour clock, by weekly report
    (7 days) or by month. **Issues** lists the logged site issues newest first, with the phone's search and
    its All / Critical / Pending / Overdue / Resolved chips.
  - **Payments**: the billing list with retention, housing units and the attached files.
  - **Documents**: suspension/resumption orders, time extensions, variation orders, letters to
    the contractor, developer requests and related files, each with its scan. The **Audit log**
    (who changed what, newest first, with the phone's search and filter chips) shows for the
    Super Admin, Engineer Admins and Field Engineers, not for viewers (same as the app).
- **Print** (from a weekly report, or a month on the Monthly view): the A4 weekly or monthly
  Project Engineer's Report laid out as the phone prints it (`BlueBookPrint.kt`,
  `BlueBookForms.kt`). Choose "Save as PDF" in the print window to keep it.
- **Project Profile** print (Print on the Summary's profile card): the A4 Project Profile sheet as the phone prints it,
  with the orders under their headings and the person printing as "Prepared By".
- **Status of Construction** print (Print on the Lots view): the plan PDF's first page inside its frame, each lot
  coloured by progress, and the legend of units per band, for one site or all sites on one landscape or portrait
  page, as the phone prints it. The PDF is drawn by pdf.js 4.10.38, bundled in `web/vendor/pdfjs/` (Apache 2.0, see
  `licenses/pdfjs-Apache-2.0.txt`; its standard fonts carry their own licences).

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
node --test "web/*.test.mjs"
```

## One-time setup for the live project

1. Done on 2026-11-07: the Web app "NHA Monitor Web" is registered and its config is in
   `web/firebase-config.js`. These values are public by design, and the security rules protect
   the data. To print the config again:
   `firebase apps:sdkconfig WEB 1:1091951535339:web:9f23ffea71ebca0f868aae`.
2. Authentication > Settings > **Authorized domains**: `nha-monitor-bulacan-2026.web.app` and
   `localhost` are there by default. Add any custom domain you use.

## Security headers

`firebase.json` sends a Content-Security-Policy (scripts only from this site and `www.gstatic.com`,
data only from `*.googleapis.com`, no frames, and the site cannot be framed), plus
`X-Frame-Options: DENY`, `nosniff`, a strict referrer policy and a Permissions-Policy. The
`127.0.0.1` entries in the policy are only for the local emulators. If you add a script, font,
image or API host, add it to the policy too or the browser will block it. Auth is started with
`initializeAuth` and no popup/redirect helper, so no Google sign-in iframe is needed.

## Deploy

`firebase.json` now serves `web/` (it used to serve the old `public/` prototype, which pointed
at another Firebase project). Deploy hosting only:

```bash
firebase deploy --only hosting --project nha-monitor-bulacan-2026
```

The site is then live at https://nha-monitor-bulacan-2026.web.app. Rules and functions are not
touched by this command.

## Not on the site yet

Editing anything other than issues.
