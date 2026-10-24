const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const SUPER_ADMIN_EMAIL = "great.glenn17@gmail.com";
// Roles a user may pick for themselves at registration. SUPER_ADMIN is never
// taken from the client-written profile: only SUPER_ADMIN_EMAIL receives it.
const SELF_ASSIGNABLE_ROLES = ["ENGINEER_ADMIN", "FIELD_ENGINEER", "VIEWER"];

/**
 * Triggered whenever a user profile is created or updated in Firestore users/{uid}.
 * Automatically mints authoritative custom claims in Firebase Auth.
 */
exports.syncUserCustomClaims = functions.firestore
  .document("users/{uid}")
  .onWrite(async (change, context) => {
    const uid = context.params.uid;
    const data = change.after.exists ? change.after.data() : null;

    if (!data) {
      console.log(`User ${uid} deleted. Skipping claims sync.`);
      return null;
    }

    const email = (data.email || "").trim().toLowerCase();
    const profileRole = (data.role || "FIELD_ENGINEER").toUpperCase();
    let requestedRole = SELF_ASSIGNABLE_ROLES.includes(profileRole) ? profileRole : "FIELD_ENGINEER";

    if (email === SUPER_ADMIN_EMAIL.toLowerCase()) {
      requestedRole = "SUPER_ADMIN";
    }

    try {
      await admin.auth().setCustomUserClaims(uid, { role: requestedRole });
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
 * Sends FCM push notification to ALL other authenticated devices for real-time sync awareness.
 */
exports.notifyOnProjectWrite = functions.firestore
  .document("projects/{projectId}")
  .onWrite(async (change, context) => {
    const projectId = context.params.projectId;
    const after = change.after.exists ? change.after.data() : null;

    if (!after) return null;

    const isNew = !change.before.exists;
    const isDeleted = after.isDeleted === true;
    const projectName = after.name || "Unnamed Project";
    const createdByUid = after.createdByUid || "";

    if (isDeleted || !isNew) return null;

    const title = "📋 New Project Created";
    const body = `${projectName} has been added by ${after.assignedStaff || "a team member"}.`;

    try {
      const usersSnapshot = await admin.firestore().collection("users").get();
      const tokens = [];

      for (const userDoc of usersSnapshot.docs) {
        if (userDoc.id === createdByUid) continue;

        const tokenDocs = await admin.firestore()
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

      const response = await admin.messaging().sendEachForMulticast({
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
              admin.firestore().collectionGroup("fcmTokens")
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
 * Send FCM notification to all Super Admin devices when a new subordinate registers.
 */
async function notifySuperAdminsOnRegistration(userData) {
  try {
    const superAdminUsers = await admin.firestore()
      .collection("users").where("role", "==", "SUPER_ADMIN").get();

    const tokens = [];
    for (const doc of superAdminUsers.docs) {
      const tokenDocs = await admin.firestore()
        .collection("users").doc(doc.id).collection("fcmTokens").get();
      tokenDocs.forEach((t) => {
        const token = t.data().token;
        if (token) tokens.push(token);
      });
    }

    if (tokens.length === 0) return;

    await admin.messaging().sendEachForMulticast({
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
