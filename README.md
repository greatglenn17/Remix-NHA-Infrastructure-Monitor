# 🏢 NHA Infrastructure Monitor — Region III

[![Android Version](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Latest Release](https://img.shields.io/badge/Release-v2.0.1%20%28Build%20201%29-0284C7?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)
[![Security Audited](https://img.shields.io/badge/Security-SHA--256%20%7C%20AES--256--GCM-16A34A?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor)

An enterprise-grade, mobile-first Android application custom-engineered for the **National Housing Authority (NHA) Region III — Bulacan District Office**. Built to streamline infrastructure project monitoring, field inspections, daily weather tracking, billing verification, real-time super admin notifications, and multi-user cloud synchronization.

---

## 📱 How to Download & Install the APK on Mobile

### Option A: Download from GitHub Releases (Recommended)
1. Open your phone's web browser and go to:  
   👉 **[GitHub Releases Page](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)**
2. Tap the latest release (`v2.0.1`).
3. Under **Assets**, tap **`NHA_Monitor_v2.0.1_Build201.apk`** to download the installation package.
4. Open the downloaded `.apk` file on your phone.
5. If prompted, allow **"Install from unknown sources"** for your browser, then tap **Install**.

---

## 📜 Release History

| Version | Build | Highlights |
| :--- | :---: | :--- |
| **`v2.0.1`** | **`201`** | 🏷️ Subdivision Plan: Block and Lot names can be edited in a lot's Update Progress; each lot is billed twice, M1 (80%) then M2 (20%), with its own date and reference; lots already marked billed count as M1
| **`v2.0.0`** | **`200`** | 💳 Summary has a Status of payment table (each billing, date, gross amount, %, balance, and the total); scope of works weights show two decimals
| **`v1.9.9`** | **`199`** | ⌨️ A signatory Office or Position not in the list can be typed while the list is open
| **`v1.9.8`** | **`198`** | 📨 Letters to Contractor with reason and date received; weekly VIII lists that week's developer requests and letters; signatories get an Office (Bulacan District Office / Region 3 Office); Documents has Suspension / Resumption (with continuances), Time Extensions, Variation Orders and Letters tabs; print opens a zoomable preview; photos open full screen with pinch zoom; Needs attention shows only when something needs action
| **`v1.9.7`** | **`197`** | 🗺️ Subdivision Plan: crop the plan to its frame; mark each lot Saleable, Non-saleable, Road or Open Space; road lots are tagged Road completed and Drainage completed; the plan is colour-coded without lot labels; Status of Construction prints on a full A4 page with the legend at the bottom-left. Orders: Full / Partial on suspensions, continuances and resumptions, named "Partial Work Suspension Order No. 1" and "1st Continuance of …"; the Project Profile lists orders as days, dates, amount and reason
| **`v1.9.6`** | **`196`** | 📝 Monthly Report: unworkable days list only the logged months with a grand total; issues record whether the Implementing Office or the Contractor/Developer took the action; variation orders, suspensions and time extensions have a reason; sections after Project Status are numbered III to VIII; table titles no longer print alone at the bottom of a page
| **`v1.9.5`** | **`195`** | 🛠️ Field Feedback: edit the scope of works with Wt% worked out from the amounts; only approved time extensions add contract days, and a logged suspension can get a Continuance; two-decimal percentages; +/− buttons for manpower and equipment; activities over a range of days; separate weekly signatories; scan or photograph the DV; weekly photo albums with geo-tag and titles; lots take a typed %, several lots can get the same progress at once, and lot photos are stamped with block and lot; developer requests are tracked until packaged; issues get a target date and show on that week's print |
| **`v1.9.4`** | **`194`** | 🖨️ Blue Book Forms: print or save as PDF the Project Profile and the weekly and monthly Project Engineer's Reports on A4, with the NHA header; weekly reports use the app's weather clocks; Prepared, Checked and Noted by names and positions (with dropdowns, including Officer-in-Charge) are asked before printing and remembered per project; Edit Project adds Projected Beneficiaries, Board Resolution, Notice of Award and Notice to Proceed |
| **`v1.9.3`** | **`193`** | 🗺️ Subdivision Lots: lots and open spaces are found automatically on a CAD-exported plan PDF, with tap-to-add, renumbering and block split/join; lot areas come from the plan scale; plans, lots and progress sync between phones; lots are coloured by progress in five bands (0–25% red, 26–50% orange, 51–75% yellow, 76–99% blue, 100%/RFO green) with a matching filter and legend; Viewers see the plan read-only |
| **`v1.9.2`** | **`192`** | 🔐 Roles and Sign-in: only the Super Admin gives roles, from Manage users & roles in the account menu; sign-up offers Field Engineer or Viewer, sign-in no longer picks a role, the admin PIN and the instant password reset are gone, the on-device password copy is salted, and Viewers no longer see billing or weekly-report add buttons |
| **`v1.9.1`** | **`191`** | 🧾 Payments and Orders: the Money section is now Payments, Documents keeps only Related Files and the Audit log (new projects no longer get an automatic checklist), the Summary shows the project profile second, time extensions and suspensions are listed and approved in Key dates (approval is for Engineer Admins), and project cards and the profile show the latest site photo |
| **`v1.9.0`** | **`190`** | 📝 Phones and Forms: long forms (weekly, monthly, project and lot inspection) open full-screen on phones, the weekly report is split into 5 steps, every form has date pickers, number keyboards, errors next to the field, a fixed Save button and a "Discard changes?" prompt, and Add weekly report and Add billing sit at the bottom of the screen |
| **`v1.8.1`** | **`181`** | 🗂️ Project Detail Redesign: projects open in four sections (Summary, Progress, Money, Documents) with a header that shrinks on scroll, billings shown as cards, and the portfolio and project side by side on wide screens |
| **`v1.7.1`** | **`171`** | ↩️ Back Button Fix: The phone's Back button inside a project returns to the Portfolio home instead of closing the app |
| **`v1.7.0`** | **`170`** | 📊 Portfolio Home: an oversight dashboard with counts for slippage, billing ahead of work, pending approvals and deadlines, a ranked Needs attention list, and work-done vs billed bars for every project |
| **`v1.6.0`** | **`160`** | 💰 Billing Bar & Variation Types: Each dashboard project card shows how much of the contract has been billed. Variation Orders are marked Additive or Deductive (deductive amounts are subtracted), and the revised contract cost from approved orders is used by the Overview tab, the Payments tab totals and the billing bar |
| **`v1.5.1`** | **`151`** | 🗓️ Weekly Report Week Fix: A new weekly report starts on the current week (Monday to Sunday) instead of a fixed August date, and its daily weather follows that week |
| **`v1.5.0`** | **`150`** | 🔐 Account-Based Project Access: Engineers and viewers see and sync only the projects assigned to their account, enforced in the cloud. The Super Admin sees every project and assigns people from a checklist on the edit screen; a new project is assigned to its creator. Photos and scans are stored per project |
| **`v1.4.2`** | **`142`** | 🧹 Sample Data Removed: New projects start with every checklist document Pending and no remarks. Forms no longer prefill sample names, statuses or fixed dates (payments, project start, monthly sign-offs, suspension/resumption orders), and untouched sample values already saved are cleared |
| **`v1.4.1`** | **`141`** | 👷 Project Assignment Fix: A new project is assigned to the engineer who creates it (instead of the placeholder "Juan Dela Cruz"), so it appears on their phone straight away |
| **`v1.4.0`** | **`140`** | 📷 Photo & Scan Sync: Weekly report photos and document/payment scans upload to Firebase Storage and appear on every phone. Photos are shrunk to 2048 px (scans 2500 px, PDFs unchanged); the camera keeps the full original in the Gallery. Take Photo with Camera now attaches the real photo, attachments keep working after a restart, and the sample pictures were removed |
| **`v1.3.1`** | **`131`** | 🔁 Sync Conflict Fix: When two phones edit the same report, document, issue, payment or project offline, every phone now ends up with the last saved edit instead of keeping different text |
| **`v1.3.0`** | **`130`** | ☁️ Report & Record Sync: Weekly reports (with weather), monthly reports, documents, issues and payments now sync to every phone through Firestore, including edits and deletions. Records already on a phone upload automatically after updating. Photos and scanned files are not synced yet |
| **`v1.2.9`** | **`129`** | 🧹 Empty Project Defaults & VO Approval Date: New projects start with empty Land Area and No. of Units instead of sample 4.5 / 120 values (blank ones show "-"), and variation orders get an optional Date Approved field that shows in the Variation Orders Log |
| **`v1.2.8`** | **`128`** | 📝 Variation Order Shortcut: Add variation orders straight from the Variation Orders Log on the Overview tab (opens the Pending Docs form set to Variation Order), and new ones appear in the log right away while Pending. Only Approved variation orders change the revised contract cost |
| **`v1.2.7`** | **`127`** | 🗓️ Date Fixes & Backup Cleanup: The Monthly tab opens on the current month when a project has no reports, and the Google Drive backup button is hidden until Drive backup really works (projects still sync to every phone through Firestore) |
| **`v1.2.6`** | **`126`** | 🔄 Sync & Weekly Report Fixes: Deleting a project from the dashboard now removes it on every phone, edits upload to the cloud right away instead of on the next app start, weekly reports compute real elapsed and remaining days, the weekly form starts empty instead of with sample data, and editing a weekly report keeps its manpower, equipment, issues and activities |
| **`v1.2.5`** | **`125`** | ☁️ Firestore Cross-Device Sync, Duplicate Merge & Push Notifications: Projects sync to every phone through Firestore, duplicate project cards are merged automatically, Google Drive restore keeps project identity, new projects trigger notifications, and Android 7 crashes are fixed. Sign in once after updating to turn on cloud sync |
| **`v1.2.2`** | **`122`** | 🌦️ Removed generated placeholder weather logs and suppressed duplicate project cards |
| **`v1.2.1`** | **`121`** | 🔐 Security and data safety corrective release |
| **`v1.2.0`** | **`120`** | 🧹 Automatic Room Database Duplicate Purge & Normalized Card Deduplication: Automatically purges all pre-existing duplicate project records on Jam Princess's phone and all subordinate screens |
| **`v1.1.9`** | **`119`** | 🎯 Scope of Works Exact Field Alignment: Aligned project Scope of Works rendering between Super Admin and subordinate screens to match Jam Princess's exact input |
| **`v1.1.8`** | **`118`** | 👥 Strict Role Scoping & Duplicate Card Prevention: Super Admin syncs & views all subordinate projects, Engineer Admins view only their assigned projects, duplicate project cards deduplicated |
| **`v1.1.7`** | **`117`** | 🔄 Bi-Directional Auto-Sync & Subordinate Project Cloud Push: Automatically uploads pre-existing and newly created subordinate projects to the Centralized Live Cloud Sync Hub |
| **`v1.1.6`** | **`116`** | 🔧 Universal Cloud Sync Envelope Parsing Fix: Resolved `No value for checksumSha256` error to support both encrypted and direct JSON cloud payloads |
| **`v1.1.5`** | **`115`** | 🛡️ Guaranteed Zero-Data-Loss In-Place Update & Permanent Account Preservation: Ensures 100% preservation of all subordinate accounts and project data across all app updates |
| **`v1.1.4`** | **`114`** | 🌐 Centralized Live Cloud Sync Hub: Seamlessly syncs projects across all devices without requiring individual OAuth login |
| **`v1.1.3`** | **`113`** | ⚡ Live 15s Cloud Sync & Subordinate Project Alerts: Real-time background sync loop automatically fetches newly created projects and alerts Super Admin |
| **`v1.1.2`** | **`112`** | ☁️ Cross-Device User Registration Cloud Sync: Registered subordinate accounts are now automatically synchronized across all phones via Google Drive |
| **`v1.1.1`** | **`111`** | 🔧 In-App Auto-Updater Download Fix: Corrected APK download target URL & added automatic browser fallback |
| **`v1.1.0`** | **`110`** | 🔔 User Registration Notification Fix: Guaranteed Super Admin alert dispatch on all new Engineer Admin / Field Engineer registrations |

---

## 👤 Author & District Information

- **Principal Engineer:** Engr. Glenn C. Aprovechado  
- **Office:** National Housing Authority (NHA) — Bulacan District Office, Region III  
- **Repository:** [greatglenn17/Remix-NHA-Infrastructure-Monitor](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor)
