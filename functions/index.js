/**
 * NextHelp push delivery.
 *
 * Firestore triggers that watch ticket documents and the comments subcollection,
 * then send FCM messages to every participant except whoever caused the change:
 *
 *  - Ticket created          -> agents get a triage heads-up (unless they created it)
 *  - Assignment changed      -> new agent + creator are notified
 *  - Status changed          -> creator + assigned agent are notified
 *  - Comment added           -> creator + assigned agent (minus comment author)
 *
 * Recipients are resolved to device tokens via `fcmTokens/{token}` documents
 * ({ token, userId, updatedAt }), written by the Android client on sign-in.
 *
 * The client stamps `updatedBy` (uid) on status/assignment writes and `authorId`
 * on comments so we can suppress self-notifications. Invalid/expired tokens are
 * pruned automatically after a failed send.
 */

const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const TICKETS = "tickets";
const TOKENS = "fcmTokens";

/** Truncate helper for notification bodies. */
function snippet(text, max = 120) {
  const clean = (text || "").replace(/\s+/g, " ").trim();
  return clean.length > max ? `${clean.slice(0, max - 1)}…` : clean;
}

/**
 * Sends a notification to every registered device of the given users.
 * Silently skips null/duplicate recipients and prunes dead tokens.
 */
async function notifyUsers(userIds, { title, body, type, ticketId }) {
  const targets = [...new Set(userIds.filter(Boolean))];
  if (targets.length === 0) return;

  const db = admin.firestore();
  const snapshot = await db
    .collection(TOKENS)
    .where("userId", "in", targets)
    .get();

  const docs = snapshot.docs.filter((d) => Boolean(d.get("token")));
  if (docs.length === 0) {
    logger.info("No tokens for users", { targets, ticketId });
    return;
  }

  const message = {
    tokens: docs.map((d) => d.get("token")),
    notification: { title, body },
    data: { type: type || "general", ticketId: ticketId || "" },
    android: { priority: "high" },
  };

  try {
    const response = await admin.messaging().sendEachForMulticast(message);
    logger.info("Push sent", {
      ticketId,
      success: response.successCount,
      failure: response.failureCount,
    });

    // Prune tokens the FCM backend reports as invalid/unregistered.
    const dead = [];
    response.responses.forEach((r, i) => {
      if (!r.success && ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(r.error?.code)) {
        dead.push(docs[i].ref.delete().catch(() => {}));
      }
    });
    await Promise.all(dead);
  } catch (err) {
    logger.error("Failed to send multicast push", { ticketId, err });
  }
}

/** Participants minus the actor who triggered the change. */
function recipients(ticket, actorId) {
  return [ticket.creatorId, ticket.assignedAgentId].filter((id) => id && id !== actorId);
}

// ---------------------------------------------------------------------------
// tickets/{ticketId} — created / assignment & status changes
// ---------------------------------------------------------------------------

exports.onTicketWritten = onDocumentWritten(`${TICKETS}/{ticketId}`, async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.data();
  if (!after) return;

  const ticketId = event.params.ticketId;
  const label = after.ticketNumber || `#${ticketId.slice(0, 8)}`;
  const actor = after.updatedBy || null;

  // --- Created -------------------------------------------------------------
  if (!before) {
    if (after.assignedAgentId) {
      await notifyUsers([after.assignedAgentId].filter((id) => id !== actor), {
        title: "New assignment",
        body: `${after.requesterName || "A user"} filed ${label}: ${snippet(after.subject)}`,
        type: "assignment",
        ticketId,
      });
      return;
    }
    // Unassigned new ticket: heads-up for the support team.
    const handlersSnap = await admin
      .firestore()
      .collection("users")
      .where("role", "in", ["SUPPORT_AGENT", "SUPPORT_MANAGER", "ADMIN"])
      .get();
    const handlerIds = handlersSnap.docs.map((d) => d.id).filter((id) => id !== after.creatorId && id !== actor);
    await notifyUsers(handlerIds, {
      title: "New support ticket",
      body: `${label}: ${snippet(after.subject)}`,
      type: "new_ticket",
      ticketId,
    });
    return;
  }

  // --- Assignment changed ----------------------------------------------------
  if (before.assignedAgentId !== after.assignedAgentId) {
    const newAgent = after.assignedAgentId;
    if (newAgent) {
      const selfAssigned = newAgent === actor;
      await notifyUsers([after.creatorId, newAgent].filter((id) => id && id !== actor), {
        title: selfAssigned ? `${after.assignedAgentName || "An agent"} picked up ${label}` : "New ticket assignment",
        body: snippet(after.subject),
        type: "assignment",
        ticketId,
      });
    } else {
      // Unassigned: let the previous agent know unless they did it themselves.
      await notifyUsers(before.assignedAgentId === actor ? [] : [before.assignedAgentId], {
        title: "Ticket unassigned",
        body: `${label} was returned to the queue`,
        type: "unassigned",
        ticketId,
      });
    }
    return;
  }

  // --- Status changed --------------------------------------------------------
  if (before.status !== after.status) {
    await notifyUsers(recipients(after, actor), {
      title: `${label} → ${String(after.status || "").replace(/_/g, " ").toLowerCase()}`,
      body: snippet(after.subject),
      type: "status",
      ticketId,
    });
  }
});

// ---------------------------------------------------------------------------
// tickets/{ticketId}/comments/{commentId} — new comment
// ---------------------------------------------------------------------------

exports.onCommentCreated = onDocumentCreated(`${TICKETS}/{ticketId}/comments/{commentId}`, async (event) => {
  const comment = event.data.data();
  if (!comment) return;

  const ticketId = event.params.ticketId;
  const ticketSnap = await admin.firestore().collection(TICKETS).doc(ticketId).get();
  if (!ticketSnap.exists) return;

  const ticket = ticketSnap.data();
  const label = ticket.ticketNumber || `#${ticketId.slice(0, 8)}`;

  await notifyUsers(recipients(ticket, comment.authorId), {
    title: `New comment on ${label}`,
    body: `${comment.authorName || "Someone"}: ${snippet(comment.content)}`,
    type: "comment",
    ticketId,
  });
});
