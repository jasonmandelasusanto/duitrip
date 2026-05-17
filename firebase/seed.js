// Run with: node firebase/seed.js
// Requires emulator running on localhost:8080

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

  // Create auth users
  try {
    await authAdmin.createUser({ uid: 'user_jason', email: 'jason@test.com', displayName: 'Jason', password: 'test1234' });
    await authAdmin.createUser({ uid: 'user_somchai', email: 'somchai@test.com', displayName: 'Somchai', password: 'test1234' });
    console.log('Auth users created');
  } catch (e) {
    console.log('Auth users may already exist:', e.message);
  }

  // Firestore users
  await db.collection('users').doc('user_jason').set({
    uid: 'user_jason',
    email: 'jason@test.com',
    displayName: 'Jason',
    photoURL: null,
    homeCurrency: 'IDR',
    createdAt: new Date(),
  });

  await db.collection('users').doc('user_somchai').set({
    uid: 'user_somchai',
    email: 'somchai@test.com',
    displayName: 'Somchai',
    photoURL: null,
    homeCurrency: 'THB',
    createdAt: new Date(),
  });

  // Test trip
  await db.collection('trips').doc('trip_test').set({
    tripId: 'trip_test',
    name: 'Singapore Trip 2026',
    destination: 'Singapore',
    destinationCurrency: 'SGD',
    startDate: '2026-06-10',
    endDate: '2026-06-15',
    createdBy: 'user_jason',
    members: [
      {
        userId: 'user_jason', email: 'jason@test.com', displayName: 'Jason',
        photoURL: null, homeCurrency: 'IDR', role: 'owner', joinedAt: new Date(), ghostId: null,
      },
      {
        userId: 'user_somchai', email: 'somchai@test.com', displayName: 'Somchai',
        photoURL: null, homeCurrency: 'THB', role: 'member', joinedAt: new Date(), ghostId: null,
      },
      {
        userId: null, email: null, displayName: 'Budi',
        photoURL: null, homeCurrency: 'IDR', role: 'ghost', joinedAt: null, ghostId: 'ghost_budi01',
      },
    ],
    invites: [],
    customCategories: [],
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  const tripRef = db.collection('trips').doc('trip_test').collection('expenses');

  await tripRef.doc('exp_001').set({
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
    paidBy: 'user_jason',
    splits: [
      { userId: 'user_jason', percentage: 34, amountInDestinationCurrency: 204, amountInHomeCurrency: 2284800, homeCurrency: 'IDR' },
      { userId: 'user_somchai', percentage: 33, amountInDestinationCurrency: 198, amountInHomeCurrency: 5247, homeCurrency: 'THB' },
      { userId: 'ghost_budi01', percentage: 33, amountInDestinationCurrency: 198, amountInHomeCurrency: 2217600, homeCurrency: 'IDR' },
    ],
    receiptUrl: null,
    createdBy: 'user_jason',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  await tripRef.doc('exp_002').set({
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
    paidBy: 'user_somchai',
    splits: [
      { userId: 'user_jason', percentage: 50, amountInDestinationCurrency: 81.6, amountInHomeCurrency: 913920, homeCurrency: 'IDR' },
      { userId: 'user_somchai', percentage: 50, amountInDestinationCurrency: 81.6, amountInHomeCurrency: 2162.4, homeCurrency: 'THB' },
    ],
    receiptUrl: null,
    createdBy: 'user_somchai',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  console.log('Seed complete. Open http://localhost:4000 to inspect.');
  process.exit(0);
}

seed().catch((e) => { console.error(e); process.exit(1); });
