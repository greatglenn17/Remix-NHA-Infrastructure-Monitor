# 🏢 NHA Infrastructure Monitor — Region III

[![Android Version](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Latest Release](https://img.shields.io/badge/Release-v1.4.1%20%28Build%20141%29-0284C7?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)
[![Security Audited](https://img.shields.io/badge/Security-SHA--256%20%7C%20AES--256--GCM-16A34A?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor)

An enterprise-grade, mobile-first Android application custom-engineered for the **National Housing Authority (NHA) Region III — Bulacan District Office**. Built to streamline infrastructure project monitoring, field inspections, daily weather tracking, billing verification, real-time super admin notifications, and multi-user cloud synchronization.

---

## 📱 How to Download & Install the APK on Mobile

### Option A: Download from GitHub Releases (Recommended)
1. Open your phone's web browser and go to:  
   👉 **[GitHub Releases Page](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)**
2. Tap the latest release (`v1.4.1`).
3. Under **Assets**, tap **`NHA_Monitor_v1.4.1_Build141.apk`** to download the installation package.
4. Open the downloaded `.apk` file on your phone.
5. If prompted, allow **"Install from unknown sources"** for your browser, then tap **Install**.

---

## 📜 Release History

| Version | Build | Highlights |
| :--- | :---: | :--- |
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
