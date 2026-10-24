# Account-Based Project Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Engineers can read and change only projects assigned to their Firebase account, enforced by Firestore and Storage rules; only the Super Admin sees everything and edits assignments.

**Architecture:** Projects carry `assignedUids`; every record document carries a copy, kept current by a Cloud Function. Non-admin phones listen with `whereArrayContains("assignedUids", uid)`. Rules check membership on projects, records (`get()` of the project) and attachment files (`firestore.get`, files moved under the project's folder).

**Tech Stack:** Kotlin, Jetpack Compose, Room, Firebase Firestore/Storage/Auth, Cloud Functions (Node 22, firebase-functions v4), Firebase emulator + `@firebase/rules-unit-testing`.

**Spec:** `docs/superpowers/specs/2026-10-24-project-access-design.md`

## Global Constraints

- Only `SUPER_ADMIN` (custom claim `role`) sees every project and may change `assignedUids`.
- A creator's project gets `assignedUids = [creatorUid]` (exactly one uid when not Super Admin).
- `VIEWER` may read assigned projects but never write projects, records or files.
- Project and record documents are never physically deleted; deletion stays the `isDeleted` tombstone and keeps `assignedUids`.
- Attachment reference format: `nhafile:<projectUuid>/<fileId>.jpg|pdf`; Storage path `attachments/<projectUuid>/<fileId>.<ext>`; local file name `<fileId>.<ext>`. Old `nhafile:<fileId>.<ext>` refs keep working locally.
- Room database goes from version 22 to 23; `projects.assignedUids TEXT NOT NULL DEFAULT ''` (JSON array string).
- Current user uid comes from `FirebaseAuth.getInstance().currentUser?.uid`, not `UserAccount.id` (which can be a local id).
- Rollout order: release app 1.5.0 → install on both phones → migration script → deploy functions → deploy firestore + storage rules.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- Super Admin unassigning someone must not tombstone that project's records in the cloud (local removal runs with `sync_state.applyingRemote = 1`). Test in Task 4.
- An engineer whose local project list is stale (admin just changed assignments) must still be able to save edits: project uploads by non-admins never send `assignedUids` on update. Test in Task 3 (rules) and Task 4 (upload builder).
- A project created offline by an engineer and not yet uploaded must not be removed as "unassigned". Test in Task 4 (`AssignmentPolicy.shouldRemoveLocally` with pending upload).
- A Viewer opening the app must still sync read-only without error loops: their uploads are skipped, not retried forever. Test in Task 4.
- Records whose project has no `assignedUids` yet (pre-migration) must stay visible to the Super Admin and not crash `buildDocument`. Test in Task 4 (empty list allowed for admin).

---

### Task 1: `assignedUids` on projects (model, Room 22→23, helper)

**Files:**
- Create: `app/src/main/java/com/example/data/model/AssignedUids.kt`
- Modify: `app/src/main/java/com/example/data/model/Project.kt` (add field after `assignedStaff`)
- Modify: `app/src/main/java/com/example/data/local/AppDatabase.kt` (version 23, `MIGRATION_22_23`, register it)
- Test: `app/src/test/java/com/example/AssignedUidsTest.kt`, `app/src/test/java/com/example/DatabaseTest.kt` (existing migration test style)

**Interfaces:**
- Produces: `Project.assignedUids: String = ""`; `object AssignedUids { fun parse(json: String): List<String>; fun toJson(uids: List<String>): String; fun contains(json: String, uid: String): Boolean }`

- [ ] **Step 1: Write failing tests** `parse("")` → `emptyList()`; `parse("[\"a\",\"b\"]")` → `listOf("a","b")`; `parse("garbage")` → `emptyList()`; `toJson(listOf("a"))` → `"[\"a\"]"`; `contains("[\"a\"]","a")` true, `contains("","a")` false; duplicates removed by `toJson(listOf("a","a"))` → `"[\"a\"]"`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.example.AssignedUidsTest` → FAIL (unresolved reference).
- [ ] **Step 3: Implement** `AssignedUids` with `org.json.JSONArray`; add `val assignedUids: String = ""` to `Project`; `MIGRATION_22_23` runs `ALTER TABLE projects ADD COLUMN assignedUids TEXT NOT NULL DEFAULT ''`; bump `version = 23`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` → all PASS (Room schema validation in `DatabaseTest` included).
- [ ] **Step 5: Commit** `feat: store assigned account uids on projects`

### Task 2: Attachment references carry the project

**Files:**
- Modify: `app/src/main/java/com/example/data/files/Attachments.kt`
- Modify: `app/src/main/java/com/example/ui/components/AttachmentPicker.kt` (add `val LocalProjectUuid = compositionLocalOf { "" }`, picker reads it)
- Modify: `app/src/main/java/com/example/ui/detail/ProjectDetailScreen.kt` and `app/src/main/java/com/example/MainActivity.kt` (wrap project content and the weekly report dialog in `CompositionLocalProvider(LocalProjectUuid provides project.projectUuid)`)
- Modify: `app/src/main/java/com/example/ui/dialogs/AddWeeklyReportDialog.kt` (pass `LocalProjectUuid.current` to `import`/`importCameraPhoto`)
- Test: `app/src/test/java/com/example/AttachmentsTest.kt`

**Interfaces:**
- Produces: `Attachments.import(context, uri, kind, projectUuid: String): String`; `Attachments.importCameraPhoto(context, file, projectUuid: String): String`; `Attachments.storagePathOf(ref: String): String?` (`attachments/<p>/<f>` or legacy `attachments/<f>`); `Attachments.projectOf(ref: String): String?`; outbox `file` events use `entityUuid = "<projectUuid>/<fileName>"` (or `<fileName>` for legacy).
- Consumes: nothing new.

- [ ] **Step 1: Write failing tests** `refsIn` finds both `nhafile:<uuid>/<uuid>.jpg` and legacy `nhafile:<uuid>.pdf`; `projectOf` returns the project uuid for the new format and null for legacy; `storagePathOf("nhafile:P/F.jpg")` → `"attachments/P/F.jpg"` (with real uuids); `storagePathOf("nhafile:../x.jpg")` → null.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.example.AttachmentsTest` → FAIL.
- [ ] **Step 3: Implement**: extend `refPattern`/`nameOf` to the optional `<projectUuid>/` segment (both uuids validated by the existing 36-char pattern); `store` takes `projectUuid` and returns `nhafile:$projectUuid/$name`; `upload`/`ensureLocal`/`collectGarbage` use `storagePathOf`; GC enqueues DELETE with the full ref path. Blank `projectUuid` in `import` throws `IllegalStateException("No project selected")`.
- [ ] **Step 4: Run** unit tests → PASS; `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: store attachments under their project`

### Task 3: Firestore and Storage rules with emulator tests

**Files:**
- Modify: `firestore.rules`, `storage.rules`, `firebase.json` (add `"emulators": {"firestore": {"port": 8080}, "storage": {"port": 9199}}`)
- Create: `rules-tests/package.json` (devDeps `@firebase/rules-unit-testing`, `firebase`, `mocha`), `rules-tests/access.test.js`

**Interfaces:**
- Produces: rules exactly as in the spec's "Cloud rules" section. Helpers: `isSuperAdmin()`, `isWriter()` (`signedIn() && request.auth.token.get('role', '') != 'VIEWER'`, so a missing claim does not error), `assigned(uids)` (`request.auth.uid in uids`).

- [ ] **Step 1: Write failing tests** (mocha, contexts via `authenticatedContext(uid, {role})`): seed `projects/P = {assignedUids:["eng"], name:"X"}`, `records/R = {uuid:"R", projectUuid:"P", revision:1, assignedUids:["eng"]}`, file `attachments/P/f.jpg`.
  - outsider (`other`, FIELD_ENGINEER): `get projects/P` denied; `get records/R` denied; read `attachments/P/f.jpg` denied; `query records where assignedUids array-contains "other"` allowed (empty).
  - `eng` (FIELD_ENGINEER): get P allowed; `update P {name:"Y"}` allowed; `update P {assignedUids:["eng","x"]}` denied; set record with `assignedUids:["eng"]` allowed, with `["eng","x"]` denied; upload `image/jpeg` 1 KB to `attachments/P/g.jpg` allowed.
  - `viewer` (VIEWER, assigned): get P allowed; update P denied; record write denied; file upload denied.
  - create `projects/N` by `eng` with `assignedUids:["eng"]` allowed, with `["eng","x"]` denied, with `["x"]` denied.
  - `admin` (SUPER_ADMIN): everything above allowed, including changing `assignedUids`; `attachments/f.jpg` (legacy) readable only by admin.
  - any user: `delete projects/P` denied.
- [ ] **Step 2: Run** `cd rules-tests && npm install && npx firebase-tools@latest emulators:exec --only firestore,storage --project demo-nha "npx mocha access.test.js"` (with `JAVA_HOME` = Android Studio JBR, its `bin` on PATH) → tests FAIL on the outsider/viewer cases.
- [ ] **Step 3: Implement** the rules per spec.
- [ ] **Step 4: Run** the same command → all PASS.
- [ ] **Step 5: Commit** `feat: enforce project assignment in Firestore and Storage rules` (rules are NOT deployed yet; deployment is Task 8).

### Task 4: Sync scoped to the user's projects

**Files:**
- Create: `app/src/main/java/com/example/data/remote/AssignmentPolicy.kt`
- Modify: `app/src/main/java/com/example/data/remote/FirestoreSyncManager.kt`, `app/src/main/java/com/example/data/remote/RecordSync.kt`
- Test: `app/src/test/java/com/example/AssignmentPolicyTest.kt`

**Interfaces:**
- Consumes: `AssignedUids` (Task 1).
- Produces:
  - `object AssignmentPolicy { fun shouldRemoveLocally(isSuperAdmin: Boolean, myUid: String, localAssignedUids: String, inServerSnapshot: Boolean, hasPendingUpload: Boolean, createdByMe: Boolean): Boolean; fun canWrite(role: String?): Boolean; fun projectUploadFields(isSuperAdmin: Boolean, exists: Boolean, myUid: String, localAssignedUids: String): List<String>? }` — `projectUploadFields` returns the `assignedUids` value to send, or null to leave the field out.
  - `RecordSync.buildDocument(...)` adds `"assignedUids"` (list from the local project).
  - `RecordSync.removeProjectLocally(projectId: Long)` deletes the project row inside `writeRemote` (triggers off).
  - Role is read once per flush from `auth.currentUser.getIdToken(false).await().claims["role"]`.

- [ ] **Step 1: Write failing tests** for `AssignmentPolicy`:
  - `shouldRemoveLocally(false,"u","[\"x\"]",inServerSnapshot=false,hasPendingUpload=false,createdByMe=false)` → true
  - same with `hasPendingUpload=true` → false; with `createdByMe=true` and `localAssignedUids=""` → false; with `isSuperAdmin=true` → false; with `inServerSnapshot=true` → false; with `localAssignedUids="[\"u\"]"` and not in snapshot → false (it will arrive; never delete own assignment on a cache gap).
  - `canWrite("VIEWER")` false, `canWrite("FIELD_ENGINEER")` true, `canWrite(null)` true.
  - `projectUploadFields(isSuperAdmin=false, exists=true, ...)` → null; `(false, exists=false, "u", "")` → `["u"]`; `(true, true, "a", "[\"b\"]")` → `["b"]`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.example.AssignmentPolicyTest` → FAIL.
- [ ] **Step 3: Implement** `AssignmentPolicy`, then in `FirestoreSyncManager`:
  - listeners: if not Super Admin, add `.whereArrayContains("assignedUids", uid)` to both `projects` and `records`;
  - `reconcile` (non-admin, `fromServer`): for local projects missing from the snapshot, call `recordSync.removeProjectLocally(id)` when `shouldRemoveLocally` is true, instead of queuing an upload;
  - remote → local project copy includes `assignedUids` (`AssignedUids.toJson(doc.get("assignedUids") as? List<String> ?: emptyList())`) in both insert and update paths;
  - project upload: Viewer (`!canWrite`) marks the event synced without uploading; non-admin does `update(map)` and on `FirebaseFirestoreException.Code.NOT_FOUND` does `set(map + ("assignedUids" to listOf(uid)))`; admin does `set(map + assignedUids, merge)`;
  - record upload: Viewer skips (mark synced); others include `assignedUids` via `buildDocument`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: sync only the projects assigned to the user`

### Task 5: Dashboard filter and assignment editing

**Files:**
- Modify: `app/src/main/java/com/example/ui/viewmodel/ProjectViewModel.kt` (filter at the `userScopedProjects` combine; `createProject` sets `assignedUids = AssignedUids.toJson(listOf(uid))`; new `registeredUsers: StateFlow<List<RegisteredUser>>` loaded from Firestore `users` for the Super Admin; `updateProjectAssignment(project, users: List<RegisteredUser>)`)
- Create: `app/src/main/java/com/example/ui/dialogs/AssignStaffField.kt` (multi-select of `RegisteredUser`, read-only text when `editable = false`)
- Modify: `app/src/main/java/com/example/ui/dialogs/EditProjectDialog.kt` (replace the free-text "Assigned Field Staff / Manager" field with `AssignStaffField`)
- Test: `app/src/test/java/com/example/AssignmentPolicyTest.kt` (add filter test)

**Interfaces:**
- Consumes: `AssignedUids`, `AssignmentPolicy` (Tasks 1, 4).
- Produces: `data class RegisteredUser(val uid: String, val name: String)`; `AssignmentPolicy.isVisible(isSuperAdmin: Boolean, myUid: String?, assignedUids: String): Boolean`; `ProjectViewModel.updateProjectAssignment(project: Project, users: List<RegisteredUser>)` saves `assignedUids` and `assignedStaff = users.joinToString(", ") { it.name }` through `saveProjectChange`.

- [ ] **Step 1: Write failing test** `isVisible(true, null, "")` true; `isVisible(false, "u", "[\"u\"]")` true; `isVisible(false, "u", "[\"x\"]")` false; `isVisible(false, null, "[\"u\"]")` false.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `isVisible` and use it as the only non-admin filter (remove name matching and the "Jam" case); add the field and Super Admin-only editing; users load from `firestore.collection("users").get()` mapping `displayName`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → PASS.
- [ ] **Step 5: Commit** `feat: assign projects to accounts from the edit screen`

### Task 6: Cloud Functions

**Files:**
- Modify: `functions/index.js`

**Interfaces:**
- Produces: `exports.syncProjectAssignmentToRecords` (`firestore.document("projects/{projectId}").onWrite`, region `asia-southeast1`): if `assignedUids` differs between before/after (compare sorted JSON), batch-update (≤ 400 per batch) `assignedUids` on all `records` where `projectUuid == projectId`. `notifyOnProjectWrite` collects tokens only from users whose uid is in `after.assignedUids` or whose `role == "SUPER_ADMIN"`, excluding `after.updatedByUid`.

- [ ] **Step 1: Implement** both changes.
- [ ] **Step 2: Verify** `cd functions && node -e "require('./index.js')"` loads without error.
- [ ] **Step 3: Commit** `feat: copy project assignments to records and notify only assigned users` (deploy is Task 8).

### Task 7: Release 1.5.0 and phone check (old rules still live)

- [ ] **Step 1:** Bump `versionCode = 150`, `versionName = "1.5.0"`; `./gradlew testDebugUnitTest lintDebug assembleDebug` → tests pass, 0 lint errors.
- [ ] **Step 2:** Install on phone A (`PDNP06J000012674`, Super Admin) and phone B (`RFGL91AT28W`, Engineer Admin); both open without crashes and show their projects.
- [ ] **Step 3:** Release: update `update.json` (sha256 of the APK) and README (badge, install steps, new row at line 27), commit, push, `gh release create v1.5.0 ... --latest`, verify raw `update.json` version and downloaded APK sha256.

### Task 8: Migrate data, deploy functions and rules, end-to-end check

**Files:**
- Create: `scripts/migrate-assignments.js` (Node, uses the Firebase CLI refresh token like the scratch tools; dry-run by default, `--apply` writes)

- [ ] **Step 1:** Run `node scripts/migrate-assignments.js` (dry run): prints each live project, its `assignedStaff`, matched uids. Expected today: 1 live project matched to the creator's account.
- [ ] **Step 2:** Run with `--apply`; re-run dry run → every live project has `assignedUids`.
- [ ] **Step 3:** `npx -y firebase-tools@latest deploy --only functions --project nha-monitor-bulacan-2026` → deploy complete; then `deploy --only firestore:rules,storage` → released.
- [ ] **Step 4:** Phones: B creates "ZZ TEST ACCESS DELETE ME" and sees it; A sees it; A removes B from it → it disappears from B within a minute and the cloud doc has `isDeleted == false`; A adds B back → project and its records return on B. A Viewer test is covered by Task 3's rules tests.
- [ ] **Step 5:** Commit the script: `chore: add one-time assignment migration script`.
