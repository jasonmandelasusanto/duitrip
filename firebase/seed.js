// Seed sample data into the local Firebase emulator.
// Run with: node firebase/seed.js   (requires the emulator running on localhost:8080)

process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = 'localhost:9099';

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');

initializeApp({ projectId: 'demo-duitrip' });
const db = getFirestore();
const authAdmin = getAuth();

async function seed() {
  console.log('Seeding Firebase emulator...');

  try {
    await authAdmin.createUser({ uid: 'user_alice', email: 'alice@example.com', displayName: 'Alice', password: 'test1234' });
    await authAdmin.createUser({ uid: 'user_bob', email: 'bob@example.com', displayName: 'Bob', password: 'test1234' });
    console.log('Auth users created');
  } catch (e) {
    console.log('Auth users may already exist:', e.message);
  }

  await db.collection('users').doc('user_alice').set({
    uid: 'user_alice', email: 'alice@example.com', displayName: 'Alice',
    photoURL: null, homeCurrency: 'IDR', createdAt: new Date(),
  });
  await db.collection('users').doc('user_bob').set({
    uid: 'user_bob', email: 'bob@example.com', displayName: 'Bob',
    photoURL: null, homeCurrency: 'THB', createdAt: new Date(),
  });

  await db.collection('trips').doc('trip_test').set({
    tripId: 'trip_test',
    name: 'Singapore Trip 2026',
    destination: 'Singapore',
    destinationCurrency: 'SGD',
    startDate: '2026-06-10',
    endDate: '2026-06-15',
    createdBy: 'user_alice',
    members: [
      { userId: 'user_alice', email: 'alice@example.com', displayName: 'Alice', photoURL: null, homeCurrency: 'IDR', role: 'owner', joinedAt: new Date(), ghostId: null },
      { userId: 'user_bob', email: 'bob@example.com', displayName: 'Bob', photoURL: null, homeCurrency: 'THB', role: 'member', joinedAt: new Date(), ghostId: null },
      { userId: null, email: null, displayName: 'Charlie', photoURL: null, homeCurrency: 'IDR', role: 'ghost', joinedAt: null, ghostId: 'ghost_charlie1' },
    ],
    // Denormalised arrays used by queries + Security Rules.
    memberUids: ['user_alice', 'user_bob'],
    invites: [],
    inviteEmails: [],
    customCategories: [],
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  const expenses = db.collection('trips').doc('trip_test').collection('expenses');

  await expenses.doc('exp_001').set({
    expenseId: 'exp_001',
    description: 'Hotel Marina Bay Sands',
    category: 'Accommodation',
    originalAmount: 600,
    originalCurrency: 'SGD',
    destinationCurrency: 'SGD',
    amountInDestinationCurrency: 600,
    exchangeRateUsed: 1,
    exchangeRateTimestamp: new Date().toISOString(),
    exchangeRates: { SGD: 1, IDR: 11200, THB: 26.5 },
    splitMode: 'percentage',
    paidBy: 'user_alice',
    splits: [
      { userId: 'user_alice', percentage: 34, amountInDestinationCurrency: 204, amountInHomeCurrency: 2284800, homeCurrency: 'IDR' },
      { userId: 'user_bob', percentage: 33, amountInDestinationCurrency: 198, amountInHomeCurrency: 5247, homeCurrency: 'THB' },
      { userId: 'ghost_charlie1', percentage: 33, amountInDestinationCurrency: 198, amountInHomeCurrency: 2217600, homeCurrency: 'IDR' },
    ],
    receiptUrl: null,
    createdBy: 'user_alice',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  await expenses.doc('exp_002').set({
    expenseId: 'exp_002',
    description: 'Flight SIN-BKK',
    category: 'Flight',
    originalAmount: 4200,
    originalCurrency: 'THB',
    destinationCurrency: 'SGD',
    amountInDestinationCurrency: 163.2,
    exchangeRateUsed: 0.03886,
    exchangeRateTimestamp: new Date().toISOString(),
    exchangeRates: { SGD: 1, IDR: 11200, THB: 26.5 },
    splitMode: 'equal',
    paidBy: 'user_bob',
    splits: [
      { userId: 'user_alice', percentage: 50, amountInDestinationCurrency: 81.6, amountInHomeCurrency: 913920, homeCurrency: 'IDR' },
      { userId: 'user_bob', percentage: 50, amountInDestinationCurrency: 81.6, amountInHomeCurrency: 2162.4, homeCurrency: 'THB' },
    ],
    receiptUrl: null,
    createdBy: 'user_bob',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  console.log('Seed complete. Open http://localhost:4000 to inspect.');
  process.exit(0);
}

seed().catch((e) => { console.error(e); process.exit(1); });
