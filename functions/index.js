const functions = require("firebase-functions/v1");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// Same region as the Firestore database, so triggers do not cross regions.
const REGION = "asia-southeast1";
const SUPER_ADMIN_EMAIL = "great.glenn17@gmail.com";
const { isApproved } = require("./approval");
const { auditAlert, skipReason } = require("./auditAlert");
const { GUARD_UID, key: projectKey, isLive, guardAction, isEmptyExtra } = require("./duplicateGuard");
const { ORDER_GUARD_UID, APPROVERS, LEGACY_MARGIN_MS, approvedDataOf, concernsOrders, orderFix } = require("./orderGuard");
// A phone uploads a new project first and its records right after, so the guard waits this long before it calls a
// new duplicate empty.
const GUARD_WAIT_MS = Number(process.env.DUPLICATE_GUARD_WAIT_MS ?? 20000);
// Roles a user may pick for themselves at registration. SUPER_ADMIN is never
// taken from the client-written profile: only the account whose Firebase Auth
// email is SUPER_ADMIN_EMAIL receives it. The profile's own email field is
// writable by the user, so it is never trusted for that decision.
const SELF_ASSIGNABLE_ROLES = ["ENGINEER_ADMIN", "FIELD_ENGINEER", "VIEWER", "SUPER_VIEWER"];
// The Firestore rules let a user sign up only as FIELD_ENGINEER or VIEWER, and let only the
// Super Admin set ENGINEER_ADMIN, SUPER_VIEWER or seesAllProjects on someone's profile.

/**
 * Triggered whenever a user profile is created or updated in Firestore users/{uid}.
 * Automatically mints authoritative custom claims in Firebase Auth.
 */
exports.syncUserCustomClaims = functions.region(REGION).firestore
  .document("users/{uid}")
  .onWrite(async (change, context) => {
    const uid = context.params.uid;
    const data = change.after.exists ? change.after.data() : null;

    if (!data) {
      console.log(`User ${uid} deleted. Skipping claims sync.`);
      return null;
    }

    // Read the real email from Firebase Auth, not from the client-written profile.
    let email;
    let authUser;
    try {
      authUser = await getAuth().getUser(uid);
      email = (authUser.email || "").trim().toLowerCase();
    } catch (err) {
      console.error(`Could not read Auth user ${uid}; claims not set:`, err);
      return null;
    }
    const profileRole = (data.role || "FIELD_ENGINEER").toUpperCase();
    let requestedRole = SELF_ASSIGNABLE_ROLES.includes(profileRole) ? profileRole : "FIELD_ENGINEER";

    if (email === SUPER_ADMIN_EMAIL.toLowerCase()) {
      // A verified email proves the account owns the address. An account that already holds the
      // claim keeps it, so the current Super Admin is never locked out by this check.
      const current = (authUser.customClaims || {}).role;
      if (authUser.emailVerified || current === "SUPER_ADMIN") {
        requestedRole = "SUPER_ADMIN";
      } else {
        console.warn(`Super Admin email on unverified account ${uid}; SUPER_ADMIN not granted.`);
      }
    }

    // approved: a new account can add or edit nothing until the Super Admin approves it.
    const approved = isApproved(data, authUser.customClaims, requestedRole === "SUPER_ADMIN");
    try {
      // allProjects: the Super Admin's "See all projects" switch; other projects open view-only.
      const allProjects = data.seesAllProjects === true;
      await getAuth().setCustomUserClaims(uid, { role: requestedRole, allProjects, approved });
      console.log(`Assigned claims { role: "${requestedRole}", allProjects: ${allProjects}, approved: ${approved} } to ${uid} (${email})`);
    } catch (err) {
      console.error(`Failed to assign custom claim to ${uid}:`, err);
    }
    // Record the decision on the profile so the Super Admin's user list shows who is waiting.
    // The write re-runs this trigger once; it then finds the flag set and writes nothing.
    if (data.approved !== approved) {
      await change.after.ref.update({ approved }).catch((err) => console.error(`Could not record approval for ${uid}:`, err));
    }

    const isNewUser = !change.before.exists;
    if (isNewUser && email !== SUPER_ADMIN_EMAIL.toLowerCase()) {
      await notifySuperAdminsOnRegistration(data);
    }

    return null;
  });

/**
 * Triggered whenever a project is created or updated in Firestore projects/{projectId}.
 * Sends an FCM push notification to the devices of users assigned to a newly created
 * project (and all Super Admins), excluding the user who created it.
 */
exports.notifyOnProjectWrite = functions.region(REGION).firestore
  .document("projects/{projectId}")
  .onWrite(async (change, context) => {
    const projectId = context.params.projectId;
    const after = change.after.exists ? change.after.data() : null;

    if (!after) return null;

    const isNew = !change.before.exists;
    const isDeleted = after.isDeleted === true;
    const projectName = after.name || "Unnamed Project";
    const createdByUid = after.createdByUid || "";
    const updatedByUid = after.updatedByUid || "";
    const assignedUids = Array.isArray(after.assignedUids) ? after.assignedUids : [];

    if (isDeleted || !isNew) return null;

    const title = "📋 New Project Created";
    const body = `${projectName} has been added by ${after.assignedStaff || "a team member"}.`;

    try {
      const usersSnapshot = await getFirestore().collection("users").get();
      const tokens = [];

      for (const userDoc of usersSnapshot.docs) {
        if (userDoc.id === createdByUid) continue;
        if (updatedByUid && userDoc.id === updatedByUid) continue;
        // Only users assigned to the project, plus Super Admins, are notified.
        const isRecipient =
          assignedUids.includes(userDoc.id) || userDoc.get("role") === "SUPER_ADMIN";
        if (!isRecipient) continue;

        const tokenDocs = await getFirestore()
          .collection("users").doc(userDoc.id).collection("fcmTokens").get();

        tokenDocs.forEach((t) => {
          const token = t.data().token;
          if (token) tokens.push(token);
        });
      }

      if (tokens.length === 0) {
        console.log("No FCM tokens to notify.");
        return null;
      }

      const response = await getMessaging().sendEachForMulticast({
        tokens,
        data: {
          type: "PROJECT_CREATED",
          title,
          message: body,
          projectId,
          projectName,
          eventId: `proj_${projectId}_${Date.now()}`,
        },
        android: { priority: "high" },
      });

      console.log(`FCM for project ${projectId}: Success=${response.successCount} Failed=${response.failureCount}`);

      // Clean up stale/invalid tokens
      const staleCleanup = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
          const code = resp.error.code;
          if (
            code === "messaging/invalid-registration-token" ||
            code === "messaging/registration-token-not-registered"
          ) {
            staleCleanup.push(
              getFirestore().collectionGroup("fcmTokens")
                .where("token", "==", tokens[idx]).get()
                .then((snap) => snap.forEach((doc) => doc.ref.delete()))
            );
          }
        }
      });
      await Promise.allSettled(staleCleanup);

    } catch (error) {
      console.error(`FCM error for project ${projectId}:`, error);
    }

    return null;
  });

/**
 * Normalises an assignedUids value to a sorted JSON string for comparison.
 * A missing or non-array value counts as an empty list.
 */
function assignedUidsKey(uids) {
  return JSON.stringify(Array.isArray(uids) ? [...uids].sort() : []);
}

/**
 * Triggered whenever a project is written. When its assignedUids change, copies the
 * project's current list onto every record of that project so Firestore rules can check
 * access per record. The list is re-read from the project document right before writing,
 * so a retried or out-of-order event never writes a stale list. Only assignedUids is
 * written: revision and updatedAt stay untouched so the app does not treat this as a
 * content edit. Errors are rethrown so the event is retried (failurePolicy); the update
 * is idempotent.
 */
exports.syncProjectAssignmentToRecords = functions.region(REGION)
  .runWith({ failurePolicy: true })
  .firestore
  .document("projects/{projectId}")
  .onWrite(async (change, context) => {
    const projectId = context.params.projectId;
    if (!change.after.exists) return null;

    const after = change.after.data();
    // Never write [] onto records just because the field is missing.
    if (!Array.isArray(after.assignedUids)) return null;

    const before = change.before.exists ? change.before.data() : null;
    if (assignedUidsKey(before && before.assignedUids) === assignedUidsKey(after.assignedUids)) {
      return null;
    }

    try {
      const db = getFirestore();
      const current = await db.collection("projects").doc(projectId).get();
      if (!current.exists) return null;
      const assignedUids = current.data().assignedUids;
      if (!Array.isArray(assignedUids)) return null;

      const snap = await db.collection("records").where("projectUuid", "==", projectId).get();
      const BATCH_SIZE = 400;
      for (let i = 0; i < snap.docs.length; i += BATCH_SIZE) {
        const batch = db.batch();
        snap.docs.slice(i, i + BATCH_SIZE).forEach((doc) => {
          batch.update(doc.ref, { assignedUids });
        });
        await batch.commit();
      }
      console.log(`Updated assignedUids on ${snap.size} records of project ${projectId}`);
    } catch (error) {
      console.error(`Failed to sync assignments to records of project ${projectId}:`, error);
      throw error;
    }

    return null;
  });

// A deleted record stays as a tombstone so offline phones learn of the delete; every
// phone listening to it reads it again on each app open, so it is removed after this long.
const RECORD_TOMBSTONE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Daily: hard-deletes record tombstones older than RECORD_TOMBSTONE_TTL_MS. Project
 * tombstones are kept: a phone that missed the delete would re-upload a project that is
 * missing from the cloud, while records are only uploaded when edited. A phone offline
 * longer than the TTL keeps its local copy of a record deleted meanwhile.
 */
exports.purgeOldRecordTombstones = functions.region(REGION).pubsub
  .schedule("every 24 hours")
  .onRun(async () => {
    const db = getFirestore();
    const cutoff = Date.now() - RECORD_TOMBSTONE_TTL_MS;
    // Filtered here, not in the query: isDeleted plus an updatedAt range would need a composite index.
    const snap = await db.collection("records").where("isDeleted", "==", true).select("updatedAt").get();
    const old = snap.docs.filter((doc) => (doc.get("updatedAt") || 0) < cutoff);
    const writer = db.bulkWriter();
    old.forEach((doc) => writer.delete(doc.ref));
    await writer.close();
    console.log(`Purged ${old.length} of ${snap.size} record tombstones older than 30 days`);
    return null;
  });

/** The FCM tokens of every Super Admin's devices (role SUPER_ADMIN, plus the designated account), leaving out `exceptUid`'s. */
async function superAdminTokens(exceptUid = "") {
  const superAdminUsers = await getFirestore()
    .collection("users").where("role", "==", "SUPER_ADMIN").get();
  const uids = new Set(superAdminUsers.docs.map((d) => d.id));
  const designated = await getAuth().getUserByEmail(SUPER_ADMIN_EMAIL).catch(() => null);
  if (designated) uids.add(designated.uid);
  const tokens = [];
  for (const id of uids) {
    if (id === exceptUid) continue;
    const tokenDocs = await getFirestore()
      .collection("users").doc(id).collection("fcmTokens").get();
    tokenDocs.forEach((t) => {
      const token = t.data().token;
      if (token) tokens.push(token);
    });
  }
  return tokens;
}

/**
 * Triggered when a record is created. A new audit log entry (written by the app or the website with each change)
 * on an on-going (unfinished) project by anyone but the Super Admin is pushed to the Super Admin's devices, with the alert the app builds in
 * ProjectViewModel.logAuditAction. Other record types return at once.
 */
exports.notifySuperAdminsOnAudit = functions.region(REGION).firestore
  .document("records/{recordId}")
  .onCreate(async (snap, context) => {
    const record = snap.data();
    if (record.type !== "audit_logs") return null;
    try {
      const uid = record.updatedByUid || "";
      const [actor, project] = await Promise.all([
        uid ? getFirestore().collection("users").doc(uid).get() : null,
        getFirestore().collection("projects").doc(record.projectUuid || "-").get(),
      ]);
      // The profile's role, else the sign-in account's role claim (what the Firestore rules check).
      const role = (actor && actor.exists && actor.get("role"))
        || (uid ? ((await getAuth().getUser(uid).catch(() => null))?.customClaims?.role || "") : "");
      const projectData = project.exists ? project.data() : null;
      const skip = skipReason(record, role, Date.now(), projectData);
      if (skip) { console.log(`Audit alert ${context.params.recordId}: skipped, ${skip}`); return null; }
      const alert = auditAlert(record, role, Date.now(), projectData);
      const tokens = await superAdminTokens(uid);
      if (tokens.length === 0) { console.log(`Audit alert ${context.params.recordId}: skipped, no Super Admin device has a push token`); return null; }
      const projectName = project.get("name") || "";
      const response = await getMessaging().sendEachForMulticast({
        tokens,
        data: {
          type: "AUDIT_ALERT",
          title: alert.title,
          message: alert.message,
          projectName,
          actorName: alert.actorName,
          actorRole: role,
          actionType: alert.actionType,
          eventId: `audit_${context.params.recordId}`,
        },
        android: { priority: "high" },
      });
      const errors = response.responses.filter((r) => !r.success).map((r) => (r.error && r.error.code) || "unknown");
      console.log(`Audit alert ${context.params.recordId}: Success=${response.successCount} Failed=${response.failureCount}${errors.length ? ` (${errors.join(", ")})` : ""}`);
    } catch (error) {
      console.error(`Audit alert error for ${context.params.recordId}:`, error);
    }
    return null;
  });

/**
 * Triggered when a device saves its push token under an account (each sign-in and token refresh). The same token
 * under any other account, saved earlier, is deleted, so a phone gets only the pushes of the account signed in on it now. The app
 * names a token's document after the token's last 20 characters, so each account is checked at that one path.
 */
exports.releaseTokenFromOtherUsers = functions.region(REGION).firestore
  .document("users/{uid}/fcmTokens/{tokenId}")
  .onWrite(async (change, context) => {
    if (!change.after.exists) return null;
    const token = change.after.get("token");
    if (!token) return null;
    const { uid, tokenId } = context.params;
    const users = await getFirestore().collection("users").get();
    const copies = await Promise.all(users.docs.filter((u) => u.id !== uid)
      .map((u) => u.ref.collection("fcmTokens").doc(tokenId).get()));
    // Only copies saved before this one: a late or retried run must not remove the account signed in now.
    const savedAt = change.after.updateTime.toMillis();
    const stale = copies.filter((d) => d.exists && d.get("token") === token && d.updateTime.toMillis() < savedAt);
    await Promise.all(stale.map((d) => d.ref.delete()));
    if (stale.length) console.log(`Token ${tokenId}: removed from ${stale.length} other account(s)`);
    return null;
  });

/**
 * Send FCM notification to all Super Admin devices when a new subordinate registers.
 */
async function notifySuperAdminsOnRegistration(userData) {
  try {
    const tokens = await superAdminTokens();
    if (tokens.length === 0) return;

    await getMessaging().sendEachForMulticast({
      tokens,
      data: {
        type: "REGISTRATION_ALERT",
        title: "👤 New User Registration",
        message: `${userData.displayName || userData.email} registered as ${userData.role || "FIELD_ENGINEER"} and is waiting for your approval in Manage users.`,
        userEmail: userData.email || "",
        displayName: userData.displayName || "",
      },
      android: { priority: "high" },
    });
  } catch (error) {
    console.error("FCM registration notification error:", error);
  }
}

/** The FCM tokens of one account's devices. */
async function tokensOf(uid) {
  if (!uid) return [];
  const tokenDocs = await getFirestore().collection("users").doc(uid).collection("fcmTokens").get();
  return tokenDocs.docs.map((t) => t.data().token).filter(Boolean);
}

/** Gives each of these project documents its `serverCreatedAt`, its creation time on the server, where it differs. */
async function stampCreationTimes(docs) {
  const unstamped = docs.filter((doc) => doc.get("serverCreatedAt") !== doc.createTime.toMillis());
  if (unstamped.length === 0) return 0;
  const writer = getFirestore().bulkWriter();
  // A document removed meanwhile has nothing to stamp.
  unstamped.forEach((doc) => writer.update(doc.ref, { serverCreatedAt: doc.createTime.toMillis() }).catch(() => {}));
  await writer.close();
  return unstamped.length;
}

/**
 * Triggered whenever a project is written. Two jobs, in one trigger so they cannot race:
 *
 * 1. `serverCreatedAt` is set to the document's own creation time on the server, whatever a client wrote there, on
 *    every project that lacks it (the ones from before this function too: unstamped, they would sort as the newest).
 *    The phones use it, not the client's createdAt, to tell the older of two same-name projects (ProjectReconciler),
 *    so a project uploaded with a made-up date cannot pass for the original.
 * 2. Two live projects must not share a name and location (duplicateGuard.js). A project that becomes live under an
 *    older project's name is marked deleted when it is new and empty; when it has data, or a phone sent it again
 *    after that, it stays for the Super Admins to choose. A rename into another project's name is put back
 *    (deleting there would let anyone who can rename a project delete it), and a deleted project brought back under
 *    a name only newer projects have is deleted again. The Super Admins and whoever made the write are told.
 */
exports.guardProjectDocument = functions.region(REGION).firestore
  .document("projects/{projectId}")
  .onWrite(async (change, context) => {
    if (!change.after.exists) return null;
    const id = context.params.projectId;
    const db = getFirestore();
    const projects = db.collection("projects");
    const ref = projects.doc(id);
    const after = change.after.data();
    const createdMs = change.after.createTime.toMillis();
    try {
      const decide = async () => {
        const all = await projects.get();
        await stampCreationTimes(all.docs);
        const others = all.docs.map((d) => ({ id: d.id, data: d.data(), createdMs: d.createTime.toMillis() }));
        return guardAction({ id, before: change.before.exists ? change.before.data() : null, after, createdMs, others });
      };
      let act = await decide();
      // Look again after the wait: the older project may be gone by then, and another may hold the name.
      if (act?.kind === "extra") {
        await new Promise((resolve) => setTimeout(resolve, GUARD_WAIT_MS));
        act = await decide();
      }
      if (!act) return null;

      // In one transaction, so a record or an edit that arrives meanwhile is seen or makes this run again.
      const outcome = await db.runTransaction(async (tx) => {
        const current = (await tx.get(ref)).data();
        const other = (await tx.get(projects.doc(act.of))).data();
        // Edited again, or the name freed, since the write this run is for: nothing to settle, or another run's.
        if (!isLive(current) || !isLive(other) || projectKey(current) !== projectKey(after) || projectKey(other) !== projectKey(after)) return null;
        // One revision up and a new edit time, so every phone takes the change (RemoteVersion.isNewer).
        const edit = { revision: (Number(current.revision) || 1) + 1, updatedAt: Date.now(), updatedByUid: GUARD_UID };
        if (act.kind === "rename") {
          tx.update(ref, { name: act.name, location: act.location, ...edit });
          return "renamed";
        }
        if (act.kind === "revived") {
          // Without duplicateOf: a phone sends a project with that mark again (FirestoreSyncManager.reconcile).
          tx.update(ref, { isDeleted: true, duplicateOf: FieldValue.delete(), ...edit });
          return "revived";
        }
        const records = await tx.get(db.collection("records").where("projectUuid", "==", id).select("type", "isDeleted"));
        // duplicateOf: removed once already and sent again by a phone that holds work for it.
        if (current.duplicateOf || !isEmptyExtra(current, createdMs, records.docs.map((d) => d.data()))) return "kept";
        tx.update(ref, { isDeleted: true, duplicateOf: act.of, ...edit });
        return "removed";
      });
      if (!outcome) return null;
      console.log(`Project ${id}: same name and location as project ${act.of} (${outcome})`);

      const name = String(after.name);
      const [title, message] = {
        removed: ["Duplicate project removed", `"${name}" already exists, so the new copy was removed. The Super Admin can assign you to the existing project.`],
        kept: ["Duplicate project", `Two projects are named "${name}". The newer one has data, so it was not removed: delete the one that should not stay.`],
        renamed: ["Project name already used", `"${name}" already exists, so "${act.name}" keeps its name.`],
        revived: ["Project not restored", `"${name}" was not restored: another project now has that name.`],
      }[outcome];
      const tokens = [...new Set([...(await superAdminTokens()), ...(outcome === "kept" ? [] : await tokensOf(after.updatedByUid))])];
      if (tokens.length === 0) return null;
      await getMessaging().sendEachForMulticast({
        tokens,
        data: {
          // The phones list an AUDIT_ALERT in the bell.
          type: "AUDIT_ALERT",
          title,
          message,
          projectName: name,
          actorName: "",
          actorRole: "",
          actionType: "Duplicate Project",
          eventId: `duplicate_${id}`,
        },
        android: { priority: "high" },
      });
    } catch (error) {
      console.error(`Duplicate guard error for project ${id}:`, error);
    }
    return null;
  });

/**
 * Daily: stamps the projects guardProjectDocument has not seen (it stamps them all on any project write, so this only
 * matters while no project is written). After its first run it finds nothing to do.
 */
exports.stampProjectCreationTimes = functions.region(REGION).pubsub
  .schedule("every 24 hours")
  .onRun(async () => {
    const snap = await getFirestore().collection("projects").get();
    console.log(`Stamped ${await stampCreationTimes(snap.docs)} of ${snap.size} projects with their creation time`);
    return null;
  });

/** The role in an account's sign-in token, which is what firestore.rules go by; "" for an account that is gone. */
async function roleOf(uid) {
  if (!uid) return "";
  try {
    return (await getAuth().getUser(uid)).customClaims?.role || "";
  } catch (error) {
    if (error.code === "auth/user-not-found") return "";
    throw error;
  }
}

/**
 * The time before which a record is taken as it stood: nobody's order writes were checked then. It is set when the
 * guard first runs, and that run puts every order approved by then on record (orderGuard/{recordId}), so that one a
 * Field Engineer wrote last, on an older app or after a role change, is not taken for theirs.
 */
async function orderGuardStart(db) {
  const start = db.doc("orderGuardStart/start");
  let snap = await start.get();
  if (!snap.exists) {
    // Fails when another run was first; that run's time is the one read back.
    await start.create({ at: FieldValue.serverTimestamp() }).catch(() => {});
    snap = await start.get();
  }
  const since = Timestamp.fromMillis(snap.get("at").toMillis() - LEGACY_MARGIN_MS);
  if (!snap.get("recorded")) {
    const records = await db.collection("records").where("type", "==", "pending_documents").get();
    const writer = db.bulkWriter();
    for (const doc of records.docs) {
      const approvedData = approvedDataOf(doc.data());
      // create: a record the guard has ruled on meanwhile keeps that ruling.
      if (approvedData && doc.updateTime < since) writer.create(db.doc(`orderGuard/${doc.id}`), { approvedData, at: doc.updateTime }).catch(() => {});
    }
    await writer.close();
    await start.update({ recorded: true });
  }
  return since;
}

/**
 * Triggered whenever a record is written. Only an Engineer Admin or the Super Admin approves an order or changes an
 * approved one (orderGuard.js), and the rules cannot see an order's status. So this keeps, in orderGuard/{recordId}
 * where no client can write, the order as an approver last approved it, and puts the record back when anyone else's
 * write makes it differ; the Super Admins and the writer are told.
 *
 * Runs can come late and out of order, so nothing is concluded from the order of the runs: each write shows the
 * record before and after it, each with the server's time and its writer (updatedByUid, which the rules hold to the
 * signed-in account), and the latest state an approver wrote is the one on record. The record as it is now, the
 * guard's own writes included, is then held to that.
 */
exports.guardOrderRecord = functions.region(REGION).firestore
  .document("records/{recordId}")
  .onWrite(async (change, context) => {
    if (!change.after.exists) return null;
    const id = context.params.recordId;
    const before = change.before.exists ? change.before.data() : null;
    const after = change.after.data();
    try {
      // Most writes are not about an order, or leave it as it was (the server copying a project's assigned accounts
      // onto its records): nothing is read for those.
      if (after.type !== "pending_documents" || !concernsOrders(before, after)) return null;
      if (before && before.data === after.data && before.isDeleted === after.isDeleted && before.updatedByUid === after.updatedByUid) return null;
      const db = getFirestore();
      const since = await orderGuardStart(db);
      const roles = new Map();
      const role = (uid) => roles.get(uid) ?? roles.set(uid, roleOf(uid)).get(uid);
      const seen = [];
      for (const snap of [change.before, change.after]) {
        if (snap.exists && (snap.updateTime < since || APPROVERS.includes(await role(snap.get("updatedByUid"))))) seen.push(snap);
      }

      const ref = change.after.ref;
      const mark = db.doc(`orderGuard/${id}`);
      const undone = await db.runTransaction(async (tx) => {
        const [record, marked] = await tx.getAll(ref, mark);
        const now = record.data();
        if (!now) return null;
        let approvedData = marked.get("approvedData") ?? null;
        let at = marked.get("at");
        let changed = false;
        for (const snap of seen) {
          if (at && snap.updateTime <= at) continue;
          [approvedData, at, changed] = [approvedDataOf(snap.data()), snap.updateTime, true];
        }
        const writer = now.updatedByUid || "";
        const writerRole = await role(writer);
        const fix = orderFix({ now, role: writerRole, approvedData });
        // The approved order with a file attached since: kept with it, or putting the order back later would drop the file.
        if (!fix && approvedData != null && now.data !== approvedData && now.isDeleted !== true && !APPROVERS.includes(writerRole)) {
          [approvedData, changed] = [now.data, true];
        }
        if (changed) tx.set(mark, { approvedData, at });
        if (!fix) return null;
        // One revision up and a new edit time, so every phone takes the record back (RemoteVersion.isNewer).
        tx.update(ref, { ...fix.patch, revision: (Number(now.revision) || 1) + 1, updatedAt: Date.now(), updatedByUid: ORDER_GUARD_UID });
        return { name: fix.name, uid: writer, projectUuid: now.projectUuid || "" };
      });
      if (!undone) return null;
      console.log(`Record ${id}: an order change by ${undone.uid || "an unknown account"} was put back`);
      // Its own earlier write, corrected once a late run showed what an approver had done: nobody to tell.
      if (undone.uid === ORDER_GUARD_UID) return null;

      const [actor, project] = await Promise.all([
        undone.uid ? db.collection("users").doc(undone.uid).get() : null,
        undone.projectUuid ? db.collection("projects").doc(undone.projectUuid).get() : null,
      ]);
      const who = actor?.get("displayName") || actor?.get("email") || "Someone";
      const tokens = [...new Set([...(await superAdminTokens()), ...(await tokensOf(undone.uid))])];
      if (tokens.length === 0) return null;
      await getMessaging().sendEachForMulticast({
        tokens,
        data: {
          // The phones list an AUDIT_ALERT in the bell.
          type: "AUDIT_ALERT",
          title: "Order put back",
          message: `${who} saved a change to "${undone.name}" that only an Engineer Admin can make (approving an order, or changing an approved one), so it was put back.`,
          projectName: String(project?.get("name") || ""),
          actorName: String(who),
          actorRole: String(actor?.get("role") || ""),
          actionType: "Order Guard",
          eventId: `order_${id}_${Date.now()}`,
        },
        android: { priority: "high" },
      });
    } catch (error) {
      console.error(`Order guard error for record ${id}:`, error);
    }
    return null;
  });
