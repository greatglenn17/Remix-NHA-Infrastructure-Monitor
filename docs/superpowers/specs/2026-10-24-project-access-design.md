# Account-based project access

Date: 2026-10-24
Status: approved design, not implemented

## Goal

Engineers can read and change only the projects assigned to their account, and the
cloud enforces it. Today every signed-in user can read and write every project, record
and file; the app only hides other projects on screen by comparing the typed
"Assigned Field Staff" name with the user's name.

## Decisions (from the owner)

- Only the Super Admin sees every project. Engineer Admins, Field Engineers and Viewers
  see only projects assigned to their account.
- Only the Super Admin adds or removes people on a project. Whoever creates a project is
  assigned to it automatically and cannot change who else is on it.
- Viewers can read their assigned projects but cannot write anything (matches the role
  description "Read-Only Access").

## Data model

- `projects/{projectUuid}` gains `assignedUids: string[]`. `assignedStaff` stays as the
  display text (names joined with ", ").
- Room `projects` gains `assignedUids TEXT NOT NULL DEFAULT ''` holding a JSON array
  (database 22 -> 23). `Project.assignedUids` is parsed with a small helper.
- Every `records/{uuid}` document carries `assignedUids`, an exact copy of its project's
  list. The app writes it on upload (from the local project); a Cloud Function rewrites it
  on all of a project's records whenever the project's list changes.
- Attachment references gain the project: `nhafile:<projectUuid>/<fileId>.jpg|pdf`, stored
  in Cloud Storage at `attachments/<projectUuid>/<fileId>.<ext>`. The local file is still
  named `<fileId>.<ext>`. References without a project (made by 1.4.x) stay readable
  locally; in the cloud their old path becomes Super Admin only.

## Cloud rules

`isSuperAdmin()` is the existing custom claim check. `isWriter()` is signed in and
role claim is not `VIEWER`.

Projects:
- read: Super Admin, or `request.auth.uid in resource.data.assignedUids`.
- create: Super Admin, or a writer whose uid is in `request.resource.data.assignedUids`
  and the list has exactly that one uid.
- update: Super Admin, or a writer in `resource.data.assignedUids` whose change does not
  touch `assignedUids` (`diff(...).affectedKeys().hasAny(['assignedUids'])` is false).
- delete: never (deletion is the `isDeleted` tombstone, which keeps `assignedUids` so
  assigned phones learn about it).

Records:
- read: Super Admin, or uid in `resource.data.assignedUids`.
- create/update: Super Admin, or a writer assigned to the project named by
  `request.resource.data.projectUuid` (`get()` of the project), and
  `request.resource.data.assignedUids == project.assignedUids`. On update the writer must
  also be in the stored record's `assignedUids`.
- delete: never (unchanged).

Storage `attachments/{projectUuid}/{fileName}`:
- read/write/delete: Super Admin, or uid in the project's `assignedUids` via
  `firestore.get`; writes also require a writer, an image or PDF, under 20 MB.
- `attachments/{fileName}` (old 1.4.x path): Super Admin only.

## App changes

Sync (FirestoreSyncManager):
- Super Admin listens to all projects and records, as now.
- Everyone else listens with `whereArrayContains("assignedUids", uid)` on both
  collections.
- Project upload: the Super Admin writes the full document including `assignedUids`.
  Others first `update()` without `assignedUids`; if the document does not exist yet
  (NOT_FOUND), they `set()` it with `assignedUids = [their uid]`.
- Record upload adds `assignedUids` from the local project.
- Unassignment: for a non-admin, a server snapshot that no longer contains a local
  project whose `assignedUids` lacks the user and which has no pending upload removes
  that project locally with the sync triggers switched off, so nothing is tombstoned in
  the cloud. A project the user created that the cloud has never seen is still uploaded.

On screen:
- The dashboard filter becomes: Super Admin sees all; others see projects whose
  `assignedUids` contains their uid. The name-matching filter (including the "Jam" special
  case) is removed.
- Create: the creator's uid and name are assigned automatically (already done for the
  name in 1.4.1).
- Edit: for the Super Admin, "Assigned Field Staff" becomes a multi-select list of
  registered users read from `users`; others see it read-only.
- Attachment pickers get the project from a `LocalProjectUuid` composition local provided
  by the project screen and the weekly report dialog.

## Cloud Functions

- `syncProjectAssignmentToRecords` (new): on a project write whose `assignedUids`
  changed, batch-update `assignedUids` on every record with that `projectUuid`.
- `notifyOnProjectWrite`: send only to users in the project's `assignedUids` plus Super
  Admins, excluding the user who made the change.
- `syncUserCustomClaims`: unchanged.

## Existing data

A one-time admin script (run from the developer machine with the owner's Firebase CLI
login) matches each live project's `assignedStaff` to `users.displayName`
(case-insensitive, trimmed) and sets `assignedUids`; unmatched projects get `[]` and are
visible to the Super Admin only. The function then copies the lists onto records.
Today: 2 accounts, 1 live project.

## Rollout order

1. Release 1.5.0 (works under both old and new rules: it only adds fields and filters).
2. Install it on both phones and confirm they sync.
3. Run the migration script, deploy the functions, then deploy the new Firestore and
   Storage rules.
Phones on 1.4.x stop syncing after step 3; today only the two test phones exist.

## Testing

- Rules tests against the Firebase emulator (`@firebase/rules-unit-testing`): outsider
  cannot read or write a project, its records or its files; Viewer cannot write; an
  assigned engineer can edit but cannot change `assignedUids`; creator can create with
  only their own uid; a record with a wrong `assignedUids` is rejected; Super Admin can
  do everything.
- Unit tests for the `assignedUids` JSON helper, the attachment reference parser (old
  and new format) and the unassignment decision.
- On the phones: phone B (Engineer Admin) creates a project and sees it; phone A (Super
  Admin) sees it and removes phone B's account; the project disappears from phone B and
  is not marked deleted in the cloud; phone A re-adds phone B and it comes back with its
  records and photos.

## Out of scope

- Approving self-chosen roles at registration (less important now that no role widens
  project visibility).
- Subdivision Plan sync.
