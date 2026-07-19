/*
 * Firestore Security Rules tests — run against the emulator via:
 *   npm run test:rules   (from the firebase/ directory)
 *
 * Asserts the authorization that replaced the old backend: trip members can read/
 * write, non-members are denied, invited users can join, users only touch their own
 * profile, and expense creation is member-gated.
 */
const fs = require('fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const PROJECT_ID = 'demo-duitrip';
const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || 'localhost:8080').split(':');

let testEnv;
let passed = 0;
let failed = 0;

async function test(name, fn) {
  try {
    await fn();
    console.log(`  ✓ ${name}`);
    passed += 1;
  } catch (e) {
    console.error(`  ✗ ${name}\n    ${e.message}`);
    failed += 1;
  }
}

async function seedTrip() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'trips/t1'), {
      tripId: 't1',
      name: 'Trip',
      createdBy: 'alice',
      memberUids: ['alice'],
      inviteEmails: ['bob@example.com'],
      status: 'active',
    });
  });
}

async function main() {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: fs.readFileSync('firestore.rules', 'utf8'), host, port: Number(port) },
  });
  await testEnv.clearFirestore();
  await seedTrip();

  const alice = testEnv.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
  const bob = testEnv.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
  const mallory = testEnv.authenticatedContext('mallory', { email: 'mallory@example.com' }).firestore();

  await test('member can read their trip', () =>
    assertSucceeds(getDoc(doc(alice, 'trips/t1'))));

  await test('non-member cannot read the trip', () =>
    assertFails(getDoc(doc(mallory, 'trips/t1'))));

  await test('non-member cannot update the trip', () =>
    assertFails(updateDoc(doc(mallory, 'trips/t1'), { name: 'Hacked' })));

  await test('member can update the trip', () =>
    assertSucceeds(updateDoc(doc(alice, 'trips/t1'), { name: 'Renamed' })));

  await test('invited user can join (add self to memberUids)', () =>
    assertSucceeds(updateDoc(doc(bob, 'trips/t1'), { memberUids: ['alice', 'bob'] })));

  await test('uninvited user cannot add self to memberUids', async () => {
    // carol has no pending invite
    const carol = testEnv.authenticatedContext('carol', { email: 'carol@example.com' }).firestore();
    await assertFails(updateDoc(doc(carol, 'trips/t1'), { memberUids: ['alice', 'carol'] }));
  });

  await test('user can write their own profile', () =>
    assertSucceeds(setDoc(doc(alice, 'users/alice'), { uid: 'alice', email: 'alice@example.com', homeCurrency: 'SGD' })));

  await test('user cannot write another user profile', () =>
    assertFails(setDoc(doc(mallory, 'users/alice'), { uid: 'alice' })));

  await test('member can create an expense', () =>
    assertSucceeds(setDoc(doc(alice, 'trips/t1/expenses/e1'), { createdBy: 'alice', description: 'x', amountInDestinationCurrency: 10 })));

  await test('non-member cannot create an expense', () =>
    assertFails(setDoc(doc(mallory, 'trips/t1/expenses/e2'), { createdBy: 'mallory', description: 'x' })));

  await testEnv.cleanup();

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
