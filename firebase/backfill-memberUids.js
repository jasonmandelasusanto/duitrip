#!/usr/bin/env node
/*
 * One-time backfill: populate `memberUids` and `inviteEmails` on existing trip docs
 * so the native app's queries and the strict Firestore Rules work for data created
 * by the old backend.
 *
 * This is additive and idempotent — it never deletes or moves anything.
 *
 * RUN THIS BEFORE deploying the new firestore.rules, or existing users could be
 * temporarily locked out of trips that don't have memberUids yet.
 *
 * Usage:
 *   # Against production (needs a service-account key with Firestore access):
 *   GOOGLE_APPLICATION_CREDENTIALS=./sa.json FIREBASE_PROJECT_ID=your-project \
 *     node backfill-memberUids.js
 *
 *   # Against the local emulator:
 *   FIRESTORE_EMULATOR_HOST=localhost:8080 FIREBASE_PROJECT_ID=demo \
 *     node backfill-memberUids.js
 */
const admin = require('firebase-admin');

admin.initializeApp({ projectId: process.env.FIREBASE_PROJECT_ID });
const db = admin.firestore();

function realUids(members = []) {
  return [...new Set(
    members.filter((m) => m.role !== 'ghost' && m.userId).map((m) => m.userId),
  )];
}
function pendingEmails(invites = []) {
  return [...new Set(
    invites.filter((i) => i.status === 'pending').map((i) => i.email).filter(Boolean),
  )];
}

async function main() {
  const snap = await db.collection('trips').get();
  let updated = 0;
  for (const doc of snap.docs) {
    const data = doc.data();
    const memberUids = realUids(data.members);
    const inviteEmails = pendingEmails(data.invites);
    await doc.ref.update({ memberUids, inviteEmails });
    updated += 1;
    console.log(`✓ ${doc.id}: memberUids=${memberUids.length} inviteEmails=${inviteEmails.length}`);
  }
  console.log(`\nBackfilled ${updated} trip(s).`);
}

main().then(() => process.exit(0)).catch((e) => {
  console.error(e);
  process.exit(1);
});
