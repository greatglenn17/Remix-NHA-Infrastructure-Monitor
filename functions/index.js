const functions = require("firebase-functions/v1");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// Same region as the Firestore database, so triggers do not cross regions.
const REGION = "asia-southeast1";
const SUPER_ADMIN_EMAIL = "great.glenn17@gmail.com";
// Roles a user may pick for themselves at registration. SUPER_ADMIN is never
// taken from the client-written profile: only the account whose Firebase Auth
// email is SUPER_ADMIN_EMAIL receives it. The profile's own email field is
// writable by the user, so it is never trusted for that decision.
const SELF_ASSIGNABLE_ROLES = ["ENGINEER_ADMIN", "FIELD_ENGINEER", "VIEWER"];

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
    try {
      const authUser = await getAuth().getUser(uid);
      email = (authUser.email || "").trim().toLowerCase();
    } catch (err) {
      console.error(`Could not read Auth user ${uid}; claims not set:`, err);
      return null;
    }
    const profileRole = (data.role || "FIELD_ENGINEER").toUpperCase();
    let requestedRole = SELF_ASSIGNABLE_ROLES.includes(profileRole) ? profileRole : "FIELD_ENGINEER";

    if (email === SUPER_ADMIN_EMAIL.toLowerCase()) {
      requestedRole = "SUPER_ADMIN";
    }

    try {
      await getAuth().setCustomUserClaims(uid, { role: requestedRole });
      console.log(`Assigned claim { role: "${requestedRole}" } to ${uid} (${email})`);
    } catch (err) {
      console.error(`Failed to assign custom claim to ${uid}:`, err);
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

/**
 * Send FCM notification to all Super Admin devices when a new subordinate registers.
 */
async function notifySuperAdminsOnRegistration(userData) {
  try {
    const superAdminUsers = await getFirestore()
      .collection("users").where("role", "==", "SUPER_ADMIN").get();

    const tokens = [];
    for (const doc of superAdminUsers.docs) {
      const tokenDocs = await getFirestore()
        .collection("users").doc(doc.id).collection("fcmTokens").get();
      tokenDocs.forEach((t) => {
        const token = t.data().token;
        if (token) tokens.push(token);
      });
    }

    if (tokens.length === 0) return;

    await getMessaging().sendEachForMulticast({
      tokens,
      data: {
        type: "REGISTRATION_ALERT",
        title: "👤 New User Registration",
        message: `${userData.displayName || userData.email} registered as ${userData.role || "FIELD_ENGINEER"}.`,
        userEmail: userData.email || "",
        displayName: userData.displayName || "",
      },
      android: { priority: "high" },
    });
  } catch (error) {
    console.error("FCM registration notification error:", error);
  }
}
