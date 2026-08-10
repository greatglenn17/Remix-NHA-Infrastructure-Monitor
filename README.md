# 🏢 NHA Infrastructure Monitor — Region III

[![Android Version](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Latest Release](https://img.shields.io/badge/Release-v1.0.7%20%28Build%20107%29-0284C7?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)
[![Security Audited](https://img.shields.io/badge/Security-SHA--256%20%7C%20AES--256--GCM-16A34A?style=for-the-badge)](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor)

An enterprise-grade, mobile-first Android application custom-engineered for the **National Housing Authority (NHA) Region III — Bulacan District Office**. Built to streamline infrastructure project monitoring, field inspections, daily weather tracking, billing verification, and multi-user cloud synchronization.

---

## 📱 How to Download & Install the APK on Mobile

### Option A: Download from GitHub Releases (Recommended)
1. Open your phone's web browser and go to:  
   👉 **[GitHub Releases Page](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases)**
2. Tap the latest release (`v1.0.7`).
3. Under **Assets**, tap **`NHA_Monitor_v1.0.7_Build107.apk`** to download the installation package.
4. Open the downloaded `.apk` file on your phone.
5. If prompted, allow **"Install from unknown sources"** for your browser, then tap **Install**.

---

## 👥 Role-Based Project Access Control (RBAC)

| User Role | Project Visibility & Access Rights |
| :--- | :--- |
| **👑 Super Admin** | **Global View:** Access and control **ALL** infrastructure projects across Region III. |
| **👷 Engineer Admin** | **Assigned View:** Access and manage **ONLY** assigned projects or created projects. |
| **🛠️ Field Engineer** | **Assigned View:** View and log field reports, site issues, and weather logs for assigned projects. |
| **👁️ Viewer** | **Read-Only View:** View assigned project dashboards, progress charts, and documents. |

---

## 🌟 Key Features & Capabilities

### 📊 1. Multi-Project Infrastructure Dashboard
- Real-time project tracking across all regional housing sites.
- Automated calculation of physical accomplishment percentage, target schedule, and **Slippage (% variance)**.
- Role-scoped project visibility enforcing strict access boundaries.

### 🔍 2. Field Inspection & Photo Documentation
- On-site digital inspection logs with findings and status tags.
- Photo gallery with timestamping and category tagging (*Progress*, *Defects*, *Site Inspection*).

### ☀️ 3. Daily Hourly Weather Chart & Automated Slippage Analysis
- Hourly weather tracking (08:00 to 17:00) with condition logging (*Fair*, *Cloudy*, *Rain Showers*, *Stormy*).
- Auto-calculation of **workable vs. unworkable days** to justify contract time extensions.

### 🗺️ 4. Subdivision Plan (SDP) Block & Lot Interactive Mapping
- Interactive vector mapping of housing blocks, lot boundaries, and road networks.
- Individual lot progress tracking, contractor assignments, and developer billing verification.

### 💰 5. Sub-Logs, Variation Orders & Billing Payments
- Full ledger tracking for Time Extensions, Variation Orders (VO), Work Suspensions, and Resumption Orders.
- Billing voucher records with gross amount, net payment, and balance tracking.

### ☁️ 6. AES-256 Encrypted Google Drive Cloud Synchronization
- One-tap cloud backup and restore powered by Google Drive REST API.
- All 19 database tables serialized with **AES-256-GCM encryption** and **SHA-256 cryptographic checksum integrity verification**.

---

## 🛡️ Security Architecture

| Security Feature | Implementation Detail |
| :--- | :--- |
| **Password Storage** | Cryptographic SHA-256 hashing applied to all local credentials (`hashPassword()`) |
| **Role Elevation** | Protected by a secret Principal Engineer PIN required for Admin promotion |
| **Cloud Encryption** | Payload encrypted with AES-256-GCM using user-derived key material |
| **Data Protection** | Local data stored via Android `EncryptedSharedPreferences` & Room Database |
| **Role-Based Access** | 4-Tier RBAC (*Super Admin*, *Engineer Admin*, *Field Engineer*, *Viewer*) |

---

## 🛠️ Technology Stack

- **UI Framework:** Android Jetpack Compose (Material3 Dark / Light Theme)
- **Architecture:** MVVM + Clean Repository Pattern (Kotlin Coroutines & Flow)
- **Local Database:** Room Database v2.6.1 (SQLite engine)
- **Cloud Backend:** Google Drive API v3 + Firebase Authentication
- **Build Tool:** Gradle (Kotlin DSL) target SDK 36 (Java 21 / JBR)

---

## 📜 Release History

| Version | Build | Highlights |
| :--- | :---: | :--- |
| **`v1.0.7`** | **`107`** | 🔐 Enforced Role Scoping: Super Admin views **ALL** projects; Engineer Admin & Field Engineer view **ONLY** assigned projects |
| **`v1.0.6`** | **`106`** | 🛡️ Fixed 4 critical security vulnerabilities (SHA-256 pass hashing, PIN protection) + expanded cloud sync to all 19 database tables |
| **`v1.0.5`** | **`105`** | Resolved startup race conditions and replaced sample import with safe cloud sync |

---

## 👤 Author & District Information

- **Principal Engineer:** Engr. Glenn C. Aprovechado  
- **Office:** National Housing Authority (NHA) — Bulacan District Office, Region III  
- **Repository:** [greatglenn17/Remix-NHA-Infrastructure-Monitor](https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor)
