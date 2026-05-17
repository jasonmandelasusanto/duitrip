# Duitrip — Product Requirements Document

**Version:** 1.8  
**Last Updated:** 2026-05-17  
**Status:** Ready for Development  

---

## 1. Overview

### 1.1 Product Summary

Duitrip is a mobile-first Progressive Web App (PWA) for collaborative trip expense tracking. It allows groups of travelers to log shared expenses, split costs by custom percentages, and view all amounts in their own preferred home currency — with exchange rates locked to the exact timestamp each expense was recorded.

### 1.2 Problem Statement

When traveling in groups across different countries and currencies, managing shared expenses is painful. Existing tools like Splitwise don't handle multi-currency trips naturally, ignore exchange rate fluctuation timing, and force everyone to use the same app. Manual tracking via spreadsheets is error-prone and non-collaborative.

### 1.3 Target Users

- Groups of 2–10 travelers on shared trips
- Users from different countries with different home currencies
- Travelers who want to log expenses on behalf of friends who don't use the app
- Primary use on mobile, secondary on desktop

### 1.4 Core Value Proposition

- Log expenses in any currency — destination, home, or any other
- Every expense converts to destination currency AND each member's home currency
- Exchange rates are locked to the timestamp of each expense — no retroactive recalculation
- Ghost members: log expenses for trip buddies who don't want to use the app
- Real-time collaboration — everyone sees updates instantly

---

## 2. Tech Stack

### 2.1 Frontend

| Layer | Technology |
|---|---|
| Framework | React + Vite + TypeScript |
| Styling | Tailwind CSS |
| PWA | vite-plugin-pwa |
| State Management | Zustand |
| Firebase SDK | firebase v10+ |
| Real-time DB | Firestore (via Firebase SDK) |
| Auth | Firebase Auth (Google OAuth) |
| HTTP Client | Axios |
| Routing | React Router v6 |
| Charts | Recharts (composable, works with Tailwind, lightweight) |
| Place Autocomplete | Nominatim (OpenStreetMap) — free, no API key |

### 2.2 Backend

| Layer | Technology |
|---|---|
| Framework | FastAPI (Python 3.11+) |
| Auth Verification | Firebase Admin SDK |
| Database | Firestore (via Firebase Admin SDK) |
| Exchange Rates | frankfurter.app (free, no API key) |
| Place Resolution | Nominatim (OpenStreetMap) — free, no API key |
| Hosting | Google Cloud Run (containerized via Docker) |
| Email (invites) | SendGrid free tier |
| Environment | Python-dotenv + GCP Secret Manager |

### 2.3 Infrastructure

| Service | Purpose |
|---|---|
| Firebase Auth | Google OAuth login |
| Firestore | Real-time database |
| Cloud Run (frontend) | Serves built React app via nginx Docker container |
| Cloud Run (backend) | Runs FastAPI via uvicorn Docker container |
| GCP Artifact Registry | Stores Docker images |
| GCP Secret Manager | Secrets (Firebase service account, SMTP, etc.) |

### 2.4 Docker Setup

Two separate containers, each deployed as its own Cloud Run service.

**Frontend container** — builds React app and serves via nginx:
```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
```

```nginx
# frontend/nginx.conf
server {
    listen 8080;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|ico|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

**Backend container** — runs FastAPI with uvicorn:
```dockerfile
# backend/Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

**Build & deploy:**
```bash
# Backend
docker build -t gcr.io/duitrip/backend ./backend
docker push gcr.io/duitrip/backend
gcloud run deploy duitrip-backend \
  --image gcr.io/duitrip/backend \
  --platform managed \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 10 \
  --set-secrets FIREBASE_SERVICE_ACCOUNT_JSON=firebase-sa:latest,SENDGRID_API_KEY=sendgrid-key:latest

# Frontend
docker build -t gcr.io/duitrip/frontend ./frontend
docker push gcr.io/duitrip/frontend
gcloud run deploy duitrip-frontend \
  --image gcr.io/duitrip/frontend \
  --platform managed \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 5
```

---

## 3. Architecture

### 3.1 High-Level Flow

```
[User Browser / PWA]
        |
        | Firebase Auth (Google OAuth)
        v
[React Frontend] <--Firestore real-time listener--> [Firestore DB]
        |
        | REST API calls (with Firebase ID token)
        v
[FastAPI Backend on Cloud Run]
        |
        |-- Verifies Firebase ID token
        |-- Handles split calculations
        |-- Fetches & snapshots exchange rates from frankfurter.app
        |-- Writes results back to Firestore
```

### 3.2 Auth Flow

1. User clicks "Sign in with Google"
2. Firebase Auth handles OAuth, returns ID token
3. Frontend stores ID token, sends it as `Authorization: Bearer <token>` on every API call
4. Backend verifies token via Firebase Admin SDK on every request
5. User identity (`uid`, `email`) is extracted from decoded token

---

## 4. Data Models (Firestore)

### 4.1 `users/{userId}`

```
{
  uid: string,                  // Firebase UID
  email: string,
  displayName: string,
  photoURL: string,
  homeCurrency: string,         // e.g. "IDR", "THB", "SGD"
  createdAt: timestamp
}
```

### 4.2 `trips/{tripId}`

```
{
  tripId: string,               // Auto-generated
  name: string,                 // e.g. "Bali Trip June 2026"
  destination: string,          // e.g. "Bali, Indonesia"
  destinationCurrency: string,  // e.g. "IDR" (auto-detected by destination)
  startDate: string,            // ISO date
  endDate: string,              // ISO date
  createdBy: string,            // userId
  members: [
    {
      userId: string | null,    // null for ghost members
      email: string | null,     // null for ghost members
      displayName: string,
      photoURL: string | null,  // null for ghost members
      homeCurrency: string,     // required even for ghost members (for conversion display)
      role: "owner" | "member" | "ghost",
      joinedAt: timestamp | null,
      ghostId: string | null    // local ID for ghost members e.g. "ghost_a1b2c3d4"
    }
  ],
  invites: [
    {
      email: string,
      invitedBy: string,        // userId
      invitedAt: timestamp,
      status: "pending" | "accepted" | "declined"
    }
  ],
  customCategories: [     // User-defined categories scoped to this trip
    {
      id: string,           // e.g. "custom_abc123"
      name: string,         // e.g. "Visa Fees"
      emoji: string,        // default "🏷️"
      createdBy: string,    // userId
      createdAt: timestamp
    }
  ],
  status: "active" | "settled" | "archived",
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**Trip display fields** (all stored in trip document, surfaced in Trip Header UI):
- `name` — trip name shown in header
- `destination` — free text shown with resolved country flag emoji
- `destinationCurrency` — shown as currency code badge
- `startDate` / `endDate` — shown as formatted range + auto-calculated duration in days
- `members[]` — avatars shown in header (max 5, then +N overflow)

**Ghost member rules:**
- `userId` is `null`; identified by `ghostId` (generated UUID prefixed `ghost_`)
- `role` is always `"ghost"`
- Cannot log in, accept invites, or interact with the app
- Can be assigned as `paidBy` and included in `splits`
- Cannot be removed if they have a non-zero balance
- Can be promoted to a real member by the trip owner (see Section 5.3)

### 4.3 `trips/{tripId}/expenses/{expenseId}`

```
{
  expenseId: string,
  description: string,                      // e.g. "Dinner at Jimbaran"
  category: string,                         // e.g. "Food", "Hotel", "Transport", "Tour", "Other"

  // Input — what the user typed
  originalAmount: number,
  originalCurrency: string,                 // May differ from destinationCurrency

  // Canonical — always in destination currency
  destinationCurrency: string,
  amountInDestinationCurrency: number,      // Converted at createdAt exchange rate

  // Exchange rate snapshot — locked at createdAt, never recalculated
  exchangeRateUsed: number,                 // Rate: originalCurrency → destinationCurrency
  exchangeRateTimestamp: string,            // ISO timestamp of rate fetch
  exchangeRates: {                          // Snapshot of all member home currency rates
    [currencyCode: string]: number          // Relative to destinationCurrency
  },

  splitMode: "equal" | "percentage" | "exact",  // How splits were calculated
  paidBy: string,                                // userId or ghostId
  splits: [
    {
      userId: string,                       // userId or ghostId
      percentage: number,                   // Must sum to 100
      amountInDestinationCurrency: number,
      amountInHomeCurrency: number,         // Converted using snapshotted rate
      homeCurrency: string
    }
  ],

  receiptUrl: string | null,
  createdBy: string,                        // Real userId who logged it
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**Critical rule:** `exchangeRates`, `exchangeRateUsed`, and all computed amounts are immutable after creation. Editing re-fetches live rates and creates a new snapshot with a new `exchangeRateTimestamp`.

### 4.4 `trips/{tripId}/settlements/{settlementId}`

```
{
  settlementId: string,
  fromUserId: string,           // userId or ghostId
  toUserId: string,             // userId or ghostId
  amountInDestinationCurrency: number,
  destinationCurrency: string,
  note: string | null,
  settledAt: timestamp,
  createdBy: string             // Real userId who marked it settled
}
```

---

## 5. API Specification

**Base URL:** `https://api.duitrip.com`  
**Auth:** All endpoints require `Authorization: Bearer <Firebase ID Token>` header

### 5.1 Users

#### `GET /users/me`
Returns current user profile.

**Response:**
```json
{
  "uid": "abc123",
  "email": "jason@example.com",
  "displayName": "Jason",
  "homeCurrency": "IDR"
}
```

#### `PATCH /users/me`
Update user profile (home currency, display name).

**Request Body:**
```json
{
  "homeCurrency": "IDR",
  "displayName": "Jason"
}
```

---

### 5.2 Trips

#### `POST /trips`
Create a new trip.

**Request Body:**
```json
{
  "name": "Bali Trip June 2026",
  "destination": "Bali, Indonesia",
  "startDate": "2026-06-10",
  "endDate": "2026-06-17"
}
```

**Backend Logic:**
- Resolve destination → currency via hardcoded map (see Section 9)
- Create trip document in Firestore
- Add creator as first member with role `"owner"`

**Response:**
```json
{
  "tripId": "trip_xyz",
  "destinationCurrency": "IDR"
}
```

#### `GET /trips`
List all trips the current user is a real member of (ghost members have no account to list from).

#### `GET /trips/{tripId}`
Get full trip details including real members, ghost members, and invite list.

#### `PATCH /trips/{tripId}`
Update trip metadata (name, dates, destination). Owner only. Changing destination updates `destinationCurrency` but does NOT retroactively recalculate existing expenses.

#### `DELETE /trips/{tripId}`
Soft-delete (set status to `"archived"`). Owner only.

---

### 5.3 Members & Ghost Members

#### `POST /trips/{tripId}/members/ghost`
Add a ghost member.

**Request Body:**
```json
{
  "displayName": "Budi",
  "homeCurrency": "IDR"
}
```

**Backend Logic:**
- Generate `ghostId = "ghost_" + uuid4()[:8]`
- Append to `trips/{tripId}.members[]` with `role: "ghost"`

**Response:**
```json
{
  "ghostId": "ghost_a1b2c3d4",
  "displayName": "Budi",
  "homeCurrency": "IDR",
  "role": "ghost"
}
```

#### `PATCH /trips/{tripId}/members/ghost/{ghostId}`
Update ghost member name or home currency. Any real member.

#### `POST /trips/{tripId}/members/ghost/{ghostId}/promote`
Promote ghost to real member when they decide to join.

**Request Body:**
```json
{
  "email": "budi@example.com"
}
```

**Backend Logic:**
- Send invite email
- On acceptance: replace all `ghostId` references in expenses with real `userId`
- Update member entry: set `userId`, `email`, `photoURL`, `role: "member"`, `joinedAt`

---

### 5.4 Invites

#### `POST /trips/{tripId}/invites`
Invite a real user by email.

**Backend Logic:**
- Check not already a member or invited
- Add to `trips/{tripId}.invites[]`
- Send invite email: `https://duitrip.com/invite/{tripId}`

#### `POST /trips/{tripId}/invites/accept`
Accept invite. Calling user's email must match a `pending` invite.

---

### 5.5 Expenses

#### `POST /trips/{tripId}/expenses`
Add a new expense.

**Request Body:**
```json
{
  "description": "Flight BKK-SIN",
  "category": "Transport",
  "originalAmount": 8500,
  "originalCurrency": "THB",
  "paidBy": "userId_somchai",
  "splitMode": "equal",         // "equal" | "percentage" | "exact"
  "splits": [
    // equal mode: omit amounts/percentages — backend calculates
    { "userId": "userId_somchai" },
    { "userId": "userId_jason" },
    { "userId": "ghost_a1b2c3d4" }

    // percentage mode:
    // { "userId": "userId_somchai", "percentage": 50 },
    // { "userId": "userId_jason", "percentage": 30 },
    // { "userId": "ghost_a1b2c3d4", "percentage": 20 }

    // exact mode:
    // { "userId": "userId_somchai", "exactAmount": 4250, "exactCurrency": "THB" },
    // { "userId": "userId_jason", "exactAmount": 99261, "exactCurrency": "IDR" },
    // { "userId": "ghost_a1b2c3d4", "exactAmount": 66.17, "exactCurrency": "SGD" }
  ]
}
```

**Backend Logic:**
1. Fetch live exchange rates from frankfurter.app:
   - `originalCurrency` → `destinationCurrency`
   - `destinationCurrency` → each member's `homeCurrency`
2. Compute `totalInDestinationCurrency = originalAmount * rate`
3. **Split calculation by mode:**

   **Equal mode:**
   - Base share = `floor(total / n * 100) / 100` (round down to 2 decimal places)
   - Remainder = `total - (base share * n)`
   - Assign remainder to payer's split entry
   - All members get base share except payer who gets `base share + remainder`

   **Percentage mode:**
   - Validate all `percentage` values sum to exactly 100
   - Each split: `amount = (percentage / 100) * total`
   - Apply same remainder rounding — assign any floating point residual to payer

   **Exact mode:**
   - Convert each `exactAmount` from `exactCurrency` → `destinationCurrency` using snapshot rate
   - Sum all converted amounts
   - Validate sum equals `totalInDestinationCurrency` within tolerance of ±0.02 (rounding tolerance)
   - Derive `percentage = convertedAmount / total * 100` for each split
   - Assign any residual from rounding to payer

4. For each split: compute `amountInHomeCurrency = amountInDestinationCurrency * memberRate`
5. Snapshot all rates with `exchangeRateTimestamp = now()`
6. Store `splitMode` on the expense document
7. Write to Firestore

**Response:**
```json
{
  "expenseId": "exp_abc",
  "description": "Flight BKK-SIN",
  "originalAmount": 8500,
  "originalCurrency": "THB",
  "destinationCurrency": "SGD",
  "amountInDestinationCurrency": 330.85,
  "exchangeRateUsed": 0.038924,
  "exchangeRateTimestamp": "2026-06-10T08:32:14Z",
  "splits": [
    {
      "userId": "userId_somchai",
      "percentage": 50,
      "amountInDestinationCurrency": 165.43,
      "amountInHomeCurrency": 4250,
      "homeCurrency": "THB"
    },
    {
      "userId": "userId_jason",
      "percentage": 30,
      "amountInDestinationCurrency": 99.26,
      "amountInHomeCurrency": 1487612,
      "homeCurrency": "IDR"
    },
    {
      "userId": "ghost_a1b2c3d4",
      "percentage": 20,
      "amountInDestinationCurrency": 66.17,
      "amountInHomeCurrency": 991741,
      "homeCurrency": "IDR"
    }
  ]
}
```

#### `GET /trips/{tripId}/expenses`
List all expenses sorted by `createdAt` descending.

Optional query params: `?category=Food`, `?paidBy=userId`, `?limit=20&offset=0`

Each expense in the list includes a `memberStatuses` array showing each member's share and whether it has been settled:

```json
{
  "expenseId": "exp_001",
  "description": "Hotel Marina Bay Sands",
  "amountInDestinationCurrency": 600,
  "paidBy": "user_jason",
  "memberStatuses": [
    {
      "userId": "user_jason",
      "displayName": "Jason",
      "isGhost": false,
      "isPayer": true,
      "amountInDestinationCurrency": 204,
      "amountInHomeCurrency": 2284800,
      "homeCurrency": "IDR",
      "status": "paid"
    },
    {
      "userId": "user_somchai",
      "displayName": "Somchai",
      "isGhost": false,
      "isPayer": false,
      "amountInDestinationCurrency": 198,
      "amountInHomeCurrency": 5247,
      "homeCurrency": "THB",
      "status": "outstanding"
    },
    {
      "userId": "ghost_budi01",
      "displayName": "Budi",
      "isGhost": true,
      "isPayer": false,
      "amountInDestinationCurrency": 198,
      "amountInHomeCurrency": 2217600,
      "homeCurrency": "IDR",
      "status": "outstanding"
    }
  ]
}
```

**Status values:**
- `"paid"` — this member is the payer; their share is considered settled by definition
- `"settled"` — a settlement record exists covering this member's share for this expense
- `"outstanding"` — share not yet settled

#### `GET /trips/{tripId}/expenses/{expenseId}`
Single expense detail including full rate snapshot.

#### `PATCH /trips/{tripId}/expenses/{expenseId}`
Edit expense. Creator or trip owner only.

**Backend Logic:**
- Re-fetch live rates (never reuse old snapshot)
- Recalculate all amounts
- Store new `exchangeRateTimestamp` — old snapshot overwritten
- UI must warn: "Editing will update exchange rates to current rates"

#### `DELETE /trips/{tripId}/expenses/{expenseId}`
Creator (within 24h) or trip owner.

---

### 5.6 Settlement

#### `GET /trips/{tripId}/settlement`
Calculate who owes whom.

**Backend Logic:**
1. For each member (real + ghost): `totalPaid` = sum of `amountInDestinationCurrency` where `paidBy == memberId`
2. For each member: `totalOwed` = sum of their `split.amountInDestinationCurrency`
3. `balance = totalPaid - totalOwed` (positive = owed, negative = owes)
4. Greedy simplification: match largest creditor with largest debtor iteratively
5. Fetch live rates for final amounts — label with timestamp

**Response:**
```json
{
  "calculatedAt": "2026-06-17T18:00:00Z",
  "ratesNote": "Settlement amounts in live rates as of 2026-06-17T18:00:00Z",
  "transactions": [
    {
      "from": { "userId": "ghost_a1b2c3d4", "displayName": "Budi", "isGhost": true },
      "to": { "userId": "userId_jason", "displayName": "Jason", "isGhost": false },
      "amountInDestinationCurrency": 66.17,
      "destinationCurrency": "SGD",
      "amountInFromHomeCurrency": 991741,
      "fromHomeCurrency": "IDR",
      "amountInToHomeCurrency": 66.17,
      "toHomeCurrency": "SGD"
    }
  ],
  "summary": {
    "totalExpenses": 1200.50,
    "destinationCurrency": "SGD",
    "perMember": [
      {
        "userId": "userId_jason",
        "displayName": "Jason",
        "isGhost": false,
        "totalPaid": 800.00,
        "totalOwed": 400.25,
        "balance": 399.75
      },
      {
        "userId": "ghost_a1b2c3d4",
        "displayName": "Budi",
        "isGhost": true,
        "totalPaid": 0,
        "totalOwed": 66.17,
        "balance": -66.17
      }
    ]
  }
}
```

#### `POST /trips/{tripId}/settlements`
Record a manual settlement (paid outside the app).

---

### 5.7 Personal Balance

#### `GET /trips/{tripId}/balance`
Returns the calling user's personal balance within the trip — how much they are owed and how much they owe, broken down per counterparty. Also returns balance for all members including ghosts (visible to all real members).

**Backend Logic:**
1. For each expense, check `paidBy` and `splits[]`
2. For each member pair, accumulate net flow in destination currency
3. Cross-reference `settlements` subcollection to subtract already-settled amounts
4. Return per-member balances with both destination currency and home currency equivalents (live rates)

**Response:**
```json
{
  "tripId": "trip_test",
  "destinationCurrency": "SGD",
  "calculatedAt": "2026-06-17T18:00:00Z",
  "myBalance": {
    "userId": "user_jason",
    "displayName": "Jason",
    "totalOwedToMe": 330.85,
    "totalOwedToMeInHomeCurrency": 3705520,
    "homeCurrency": "IDR",
    "totalIOwe": 0,
    "totalIOweInHomeCurrency": 0,
    "netBalance": 330.85,
    "netBalanceInHomeCurrency": 3705520,
    "owedToMeBy": [
      {
        "userId": "user_somchai",
        "displayName": "Somchai",
        "isGhost": false,
        "amount": 165.43,
        "amountInTheirCurrency": 4382,
        "theirCurrency": "THB",
        "status": "outstanding"
      },
      {
        "userId": "ghost_budi01",
        "displayName": "Budi",
        "isGhost": true,
        "amount": 165.42,
        "amountInTheirCurrency": 1852704,
        "theirCurrency": "IDR",
        "status": "outstanding"
      }
    ],
    "iOweTo": []
  },
  "allMembers": [
    {
      "userId": "user_jason",
      "displayName": "Jason",
      "isGhost": false,
      "netBalance": 330.85,
      "netBalanceInHomeCurrency": 3705520,
      "homeCurrency": "IDR",
      "status": "owed"
    },
    {
      "userId": "user_somchai",
      "displayName": "Somchai",
      "isGhost": false,
      "netBalance": -165.43,
      "netBalanceInHomeCurrency": -4382,
      "homeCurrency": "THB",
      "status": "owes"
    },
    {
      "userId": "ghost_budi01",
      "displayName": "Budi",
      "isGhost": true,
      "netBalance": -165.42,
      "netBalanceInHomeCurrency": -1852704,
      "homeCurrency": "IDR",
      "status": "owes"
    }
  ]
}
```

---

### 5.8 Analytics

#### `GET /trips/{tripId}/analytics`
Returns precomputed analytics data for both group and individual views.

**Backend Logic:**
- Aggregate all expenses in the trip
- Compute group totals by category, by day, by member
- Compute individual totals for the calling user
- All amounts in destination currency (consistent reference)
- Use snapshotted exchange rates per expense (not live rates)

**Response:**
```json
{
  "tripId": "trip_test",
  "destinationCurrency": "SGD",
  "dateRange": { "from": "2026-06-10", "to": "2026-06-15" },
  "group": {
    "totalSpend": 1200.50,
    "totalSpendPerMember": 400.17,
    "byCategory": [
      { "category": "Accommodation", "emoji": "🏨", "amount": 600.00, "percentage": 49.98 },
      { "category": "Flight", "emoji": "✈️", "amount": 330.85, "percentage": 27.56 },
      { "category": "Food & Drink", "emoji": "🍽️", "amount": 200.00, "percentage": 16.66 },
      { "category": "Transport", "emoji": "🚗", "amount": 69.65, "percentage": 5.80 }
    ],
    "byDay": [
      { "date": "2026-06-10", "amount": 930.85, "expenseCount": 2 },
      { "date": "2026-06-11", "amount": 120.00, "expenseCount": 3 },
      { "date": "2026-06-12", "amount": 149.65, "expenseCount": 4 }
    ],
    "byMember": [
      { "userId": "user_jason", "displayName": "Jason", "isGhost": false,
        "totalPaid": 800.00, "percentage": 66.6 },
      { "userId": "user_somchai", "displayName": "Somchai", "isGhost": false,
        "totalPaid": 330.85, "percentage": 27.6 },
      { "userId": "ghost_budi01", "displayName": "Budi", "isGhost": true,
        "totalPaid": 69.65, "percentage": 5.8 }
    ]
  },
  "individual": {
    "userId": "user_jason",
    "displayName": "Jason",
    "totalShare": 400.17,
    "totalShareInHomeCurrency": 4481904,
    "homeCurrency": "IDR",
    "byCategory": [
      { "category": "Accommodation", "emoji": "🏨", "amount": 204.00, "percentage": 50.98 },
      { "category": "Flight", "emoji": "✈️", "amount": 99.26, "percentage": 24.80 },
      { "category": "Food & Drink", "emoji": "🍽️", "amount": 66.67, "percentage": 16.66 },
      { "category": "Transport", "emoji": "🚗", "amount": 30.24, "percentage": 7.56 }
    ],
    "vsGroupAverage": {
      "myShare": 400.17,
      "groupAverage": 400.17,
      "difference": 0,
      "percentageDifference": 0
    },
    "timeline": [
      { "date": "2026-06-10", "expenseId": "exp_001", "description": "Hotel",
        "myShare": 204.00, "category": "Accommodation", "emoji": "🏨" },
      { "date": "2026-06-10", "expenseId": "exp_002", "description": "Flight SIN-BKK",
        "myShare": 99.26, "category": "Flight", "emoji": "✈️" }
    ]
  }
}
```

---

### 5.9 Custom Categories

#### `POST /trips/{tripId}/categories`
Add a custom category to a trip.

**Request Body:**
```json
{
  "name": "Visa Fees",
  "emoji": "🪪"
}
```

**Backend Logic:**
- Validate name is unique within trip (case-insensitive)
- Validate max 20 custom categories not exceeded
- Generate `id = "custom_" + uuid4()[:8]`
- Append to `trips/{tripId}.customCategories[]`

#### `DELETE /trips/{tripId}/categories/{categoryId}`
Delete a custom category. Fails if any expenses use it.

---

### 5.7 Exchange Rates

#### `GET /exchange-rates`
Query params: `?base=IDR&symbols=THB,SGD,USD`

**Backend Logic:**
- Proxy to frankfurter.app
- In-memory cache TTL = 1 hour
- On failure: return last cached value with `"stale": true` + warning

---

## 6. Frontend Pages & Components

### 6.1 Page Structure

```
/                               → Landing / Login
/onboarding                     → Set home currency (first login only)
/dashboard                      → Trip list
/trips/new                      → Create trip
/trips/{tripId}                 → Trip detail (expense list) — default tab
/trips/{tripId}/analytics       → Analytics tab
/trips/{tripId}/expenses/new    → Add expense
/trips/{tripId}/expenses/{id}   → Expense detail / edit
/trips/{tripId}/settlement      → Settlement summary
/trips/{tripId}/members         → Members + ghost management + invites
/invite/{tripId}                → Accept invite landing
/profile                        → User profile + home currency
```

### 6.2 Key Components

#### Add Expense Form
- Description (text input)
- Category (pill selector — shows default categories first, then trip custom categories, then "+ Add category" option at the end)
- **Currency selector** — defaults to destination currency; shows member home currencies as quick-pick options; supports any ISO 4217 code
- Amount input
- **Live preview** — shows converted amount in destination currency below input (debounced 300ms, uses cached rates, no API call)
- Paid by — dropdown includes real members AND ghost members
- Split mode selector: **Equal** (default) / **Percentage** / **Exact Amount**
  - **Equal:** auto-distributes evenly across all members (real + ghost); shown as read-only confirmation chips; remainder assigned to payer
  - **Percentage:** manual % input per member; live sum counter shows remaining % to allocate; submit disabled until sum = 100
  - **Exact Amount:** fixed amount input per member; each member can use any currency (destination or their own home currency); backend converts all to destination currency at snapshot rate and validates sum equals total; remainder assigned to payer
- Confirmation on submit: "Flight THB 8,500 = SGD 330.85 @ 0.038924 · Jun 10, 08:32 SGT"

#### Trip Header (shown on all trip sub-pages)

Full-width header card showing trip context at a glance:

```
┌─────────────────────────────────────────────┐
│  🇸🇬  Singapore Trip 2026                    │
│  Jun 10 – Jun 15, 2026  ·  6 days           │
│  SGD  ·  3 members                          │
│                                             │
│  [Jason] [Somchai] [Budi 👻]                │
│                                             │
│  Total spent: SGD 1,200.50                  │
│  Your share:  SGD 400.17  ≈ IDR 4,481,904   │
└─────────────────────────────────────────────┘
```

Fields shown:
- Destination country flag emoji + trip name
- Date range + duration in days (auto-calculated)
- Destination currency code + member count
- Member avatars (real members with photo, ghost with initials + 👻)
- Total group spend in destination currency
- Calling user's total share in destination + home currency

---

#### Tab Navigation (inside trip)

Bottom tab bar on mobile, top tabs on desktop:

```
[ Expenses ]  [ Analytics ]  [ Members ]  [ Settle Up ]
```

- **Expenses** — default tab, expense list + personal balance card
- **Analytics** — charts tab
- **Members** — member management, ghost management, invites
- **Settle Up** — settlement screen (amber CTA style)

---

#### Analytics Tab — Group View

Toggle between **Group** and **Me** at the top of the analytics tab.

**Group charts (top to bottom):**

1. **Spending by Category** — donut chart
   - Each segment = one category
   - Center label: total spend in destination currency
   - Legend below with category emoji, name, amount, and percentage
   - Teal/amber/complementary palette for segments

2. **Spending by Day** — vertical bar chart
   - X axis: trip dates
   - Y axis: amount in destination currency
   - Bars colored teal; hover/tap shows date + amount + expense count
   - Highlights the highest-spend day with amber color

3. **Spending by Member** — horizontal bar chart
   - Each bar = one member (real + ghost)
   - Shows amount paid on behalf of group (paidBy total)
   - Ghost members shown with 👻 suffix
   - Average line shown as dashed vertical line

---

#### Analytics Tab — Individual View

**Individual charts (top to bottom):**

1. **My Spending by Category** — donut chart
   - Same structure as group donut but filtered to calling user's shares
   - Center label: my total share

2. **My Share vs Group Average** — single horizontal comparison bar
   - My share on top, group average below
   - Green if below average, amber if above
   - Shows difference: "+SGD 12.50 above average" or "-SGD 8.00 below average"

3. **My Expense Timeline** — vertical timeline list (not a chart)
   - Chronological list of expenses where user has a share
   - Each row: date · category emoji · description · my share amount
   - Groups by date with date headers
   - Tappable — opens expense detail

---

#### Personal Balance Card (Trip Detail — top of screen, always visible)

Shown above the expense list. Updates in real-time via Firestore listener on settlements.

```
┌─────────────────────────────────────────────┐
│  Your Balance                                │
│                                             │
│  You are owed    SGD 330.85                 │  ← text-success
│                  ≈ IDR 3,705,520            │  ← text-secondary, smaller
│                                             │
│  Somchai owes you  SGD 165.43  ⏳           │
│  Budi owes you     SGD 165.42  ⏳  👻       │  ← ghost indicator
└─────────────────────────────────────────────┘
```

If user owes money:
```
┌─────────────────────────────────────────────┐
│  Your Balance                                │
│                                             │
│  You owe         SGD 165.43                 │  ← text-danger
│                  ≈ THB 4,382                │
│                                             │
│  You owe Jason   SGD 165.43  ⏳             │
└─────────────────────────────────────────────┘
```

If fully settled:
```
┌─────────────────────────────────────────────┐
│  Your Balance                                │
│  ✓ All settled                              │  ← text-success
└─────────────────────────────────────────────┘
```

#### Expense Card (List View)
- Original amount + currency (what was actually paid) — primary
- Amount in destination currency — secondary
- Amount in current user's home currency — muted, small
- Tap to expand: shows rate + timestamp AND per-member share status
- Paid by avatar — ghost shows initials only, no photo
- "Logged by [name]" label if `paidBy` ≠ `createdBy`

#### Expense Share Status (expanded view inside Expense Card)

```
Hotel · SGD 600.00 · Paid by Jason
─────────────────────────────────────
✓  Jason    SGD 204.00  IDR 2,284,800   (payer)
⏳  Somchai  SGD 198.00  THB 5,247       outstanding
⏳  Budi     SGD 198.00  IDR 2,217,600  👻 outstanding
```

Status icons:
- `✓` green — payer or settled
- `⏳` amber — outstanding
- `👻` — ghost member indicator

#### Members Screen
- Real members: avatar, name, home currency, balance
- Ghost members: initials avatar, "(not on app)" badge, balance, "Promote" button
- "Add trip buddy (no app)" button → name + home currency form
- "Invite member" button → email input

#### Settlement Screen
- Banner: "Rates are live as of [timestamp]"
- Ghost transactions labeled "(collect offline)"
- "Mark as settled" per transaction

### 6.3 Real-time Updates

Firestore `onSnapshot` on:
- `trips/{tripId}/expenses` — live expense feed for all connected members
- `trips/{tripId}` — member changes, ghost additions, invite acceptances

---

## 7. Currency Handling Rules

### 7.1 Input Currency
- User selects any currency when adding an expense
- Default: trip `destinationCurrency`
- Quick-pick dropdown: destination currency + all member home currencies
- Manual entry: any valid ISO 4217 code

### 7.2 Conversion at Creation
- Backend converts `originalAmount` → `amountInDestinationCurrency` using live rate at `createdAt`
- All member splits converted to each member's `homeCurrency` at this same moment
- Rate fetched from frankfurter.app at the moment the API call is processed

### 7.3 Rate Immutability
- Once saved, `exchangeRates` snapshot is permanent for that expense
- All displayed conversions for that expense always use snapshotted rates
- UI always shows: "@ 0.038924 THB/SGD · Jun 10, 08:32"
- Editing invalidates snapshot and creates a new one with live rates — user warned before confirm

### 7.4 Settlement Rates
- Settlement totals use live rates at time of viewing
- Clearly labeled with fetch timestamp
- Individual expense breakdowns use their own snapshotted rates

---

## 8. Ghost Member Rules (Full Detail)

| Scenario | Behavior |
|---|---|
| Adding ghost | Any real trip member can add |
| Editing ghost | Any real member can update name/currency |
| Ghost as paidBy | Allowed — real member logs on their behalf |
| Ghost in splits | Allowed — included in debt calculation |
| Ghost balance non-zero | Cannot be removed |
| Ghost balance zero | Owner can remove |
| Promoting ghost | Owner sends invite email; on acceptance all `ghostId` refs replaced with real `userId` |
| Ghost in settlement | Labeled "(not on app)"; collect offline |

---

## 9. Destination → Currency Resolution

Destination resolution uses a three-layer approach: Nominatim for place lookup, a static country→currency table for mapping, and a manual override as the final fallback.

### 9.1 Resolution Flow

```
User types destination (e.g. "Bali")
  → Frontend: Nominatim autocomplete as user types (debounced 400ms)
  → Returns list of matches with country info:
      - Bali, Indonesia → country_code: "ID"
      - Bali, India     → country_code: "IN"
  → User selects one from the dropdown
  → Frontend maps country_code → currency via COUNTRY_TO_CURRENCY table
  → UI shows confirmation chip: "Indonesia · IDR"
  → User can manually override the currency if needed
  → Trip created with confirmed currency
```

### 9.2 Nominatim API Usage

**Autocomplete endpoint (called from frontend as user types):**
```
GET https://nominatim.openstreetmap.org/search
  ?q={destination}
  &format=json
  &addressdetails=1
  &limit=5
  &featuretype=city,state,country

Required header: User-Agent: Duitrip/1.0 (contact@duitrip.com)
```

**Response fields used:**
- `display_name` — shown in dropdown (e.g. "Bali, Indonesia")
- `address.country_code` — ISO 3166-1 alpha-2 (e.g. "id")
- `address.country` — shown in confirmation chip (e.g. "Indonesia")

**Rate limit:** Max 1 request/second. Debounce frontend input to 400ms; this is sufficient.

### 9.3 Country → Currency Table (backend + frontend)

Static table of ~180 entries. Countries rarely change currencies; this table needs updating only on geopolitical currency changes.

```python
# backend/app/utils/country_currency.py
COUNTRY_TO_CURRENCY: dict[str, str] = {
    "AD": "EUR", "AE": "AED", "AF": "AFN", "AG": "XCD", "AL": "ALL",
    "AM": "AMD", "AO": "AOA", "AR": "ARS", "AT": "EUR", "AU": "AUD",
    "AZ": "AZN", "BA": "BAM", "BB": "BBD", "BD": "BDT", "BE": "EUR",
    "BF": "XOF", "BG": "BGN", "BH": "BHD", "BI": "BIF", "BJ": "XOF",
    "BN": "BND", "BO": "BOB", "BR": "BRL", "BS": "BSD", "BT": "BTN",
    "BW": "BWP", "BY": "BYN", "BZ": "BZD", "CA": "CAD", "CD": "CDF",
    "CF": "XAF", "CG": "XAF", "CH": "CHF", "CI": "XOF", "CL": "CLP",
    "CM": "XAF", "CN": "CNY", "CO": "COP", "CR": "CRC", "CU": "CUP",
    "CV": "CVE", "CY": "EUR", "CZ": "CZK", "DE": "EUR", "DJ": "DJF",
    "DK": "DKK", "DM": "XCD", "DO": "DOP", "DZ": "DZD", "EC": "USD",
    "EE": "EUR", "EG": "EGP", "ER": "ERN", "ES": "EUR", "ET": "ETB",
    "FI": "EUR", "FJ": "FJD", "FR": "EUR", "GA": "XAF", "GB": "GBP",
    "GD": "XCD", "GE": "GEL", "GH": "GHS", "GM": "GMD", "GN": "GNF",
    "GQ": "XAF", "GR": "EUR", "GT": "GTQ", "GW": "XOF", "GY": "GYD",
    "HN": "HNL", "HR": "EUR", "HT": "HTG", "HU": "HUF", "ID": "IDR",
    "IE": "EUR", "IL": "ILS", "IN": "INR", "IQ": "IQD", "IR": "IRR",
    "IS": "ISK", "IT": "EUR", "JM": "JMD", "JO": "JOD", "JP": "JPY",
    "KE": "KES", "KG": "KGS", "KH": "KHR", "KI": "AUD", "KM": "KMF",
    "KN": "XCD", "KP": "KPW", "KR": "KRW", "KW": "KWD", "KZ": "KZT",
    "LA": "LAK", "LB": "LBP", "LC": "XCD", "LI": "CHF", "LK": "LKR",
    "LR": "LRD", "LS": "LSL", "LT": "EUR", "LU": "EUR", "LV": "EUR",
    "LY": "LYD", "MA": "MAD", "MC": "EUR", "MD": "MDL", "ME": "EUR",
    "MG": "MGA", "MH": "USD", "MK": "MKD", "ML": "XOF", "MM": "MMK",
    "MN": "MNT", "MR": "MRU", "MT": "EUR", "MU": "MUR", "MV": "MVR",
    "MW": "MWK", "MX": "MXN", "MY": "MYR", "MZ": "MZN", "NA": "NAD",
    "NE": "XOF", "NG": "NGN", "NI": "NIO", "NL": "EUR", "NO": "NOK",
    "NP": "NPR", "NR": "AUD", "NZ": "NZD", "OM": "OMR", "PA": "PAB",
    "PE": "PEN", "PG": "PGK", "PH": "PHP", "PK": "PKR", "PL": "PLN",
    "PT": "EUR", "PW": "USD", "PY": "PYG", "QA": "QAR", "RO": "RON",
    "RS": "RSD", "RU": "RUB", "RW": "RWF", "SA": "SAR", "SB": "SBD",
    "SC": "SCR", "SD": "SDG", "SE": "SEK", "SG": "SGD", "SI": "EUR",
    "SK": "EUR", "SL": "SLL", "SM": "EUR", "SN": "XOF", "SO": "SOS",
    "SR": "SRD", "SS": "SSP", "ST": "STN", "SV": "USD", "SY": "SYP",
    "SZ": "SZL", "TD": "XAF", "TG": "XOF", "TH": "THB", "TJ": "TJS",
    "TL": "USD", "TM": "TMT", "TN": "TND", "TO": "TOP", "TR": "TRY",
    "TT": "TTD", "TV": "AUD", "TZ": "TZS", "UA": "UAH", "UG": "UGX",
    "US": "USD", "UY": "UYU", "UZ": "UZS", "VA": "EUR", "VC": "XCD",
    "VE": "VES", "VN": "VND", "VU": "VUV", "WS": "WST", "YE": "YER",
    "ZA": "ZAR", "ZM": "ZMW", "ZW": "ZWL",
    # Special territories
    "HK": "HKD", "MO": "MOP", "TW": "TWD", "PS": "ILS",
}

def country_code_to_currency(country_code: str) -> str:
    return COUNTRY_TO_CURRENCY.get(country_code.upper(), "USD")
```

### 9.4 Disambiguation UI

When Nominatim returns multiple places with the same name, the frontend shows all options in a dropdown with country context. User must explicitly select one — no silent auto-resolution.

Example for "Bali":
```
┌─────────────────────────────────┐
│ 🔍 Bali                         │
├─────────────────────────────────┤
│ 📍 Bali, Indonesia · IDR        │
│ 📍 Bali, India     · INR        │
│ 📍 Bali, Brazil    · BRL        │
└─────────────────────────────────┘
```

After selection, a confirmation chip appears below the destination input:
```
[🇮🇩 Indonesia · IDR  ✎]
```

The ✎ icon opens a currency override modal — user can type any ISO 4217 code.

### 9.5 Fallback Hierarchy

| Layer | Trigger | Action |
|---|---|---|
| 1. Nominatim autocomplete | User types destination | Show place suggestions with country + currency |
| 2. User disambiguation | Multiple results returned | User picks the correct one |
| 3. Manual override | Nominatim fails, wrong result, or obscure destination | User types currency code directly |

---

## 10. Exchange Rate Strategy

- **Provider:** frankfurter.app (free, ECB data, no API key)
- **Endpoint:** `https://api.frankfurter.app/latest?base={currency}&symbols={csv}`
- **Cache:** In-memory TTL = 1 hour
- **Failure fallback:** Return last cached value with `"stale": true` + frontend warning banner
- **On expense creation:** Fetch live, snapshot immediately, store permanently
- **On expense edit:** Re-fetch live, replace snapshot, warn user
- **For settlement:** Fetch live at view time, label timestamp clearly

---

## 11. Expense Categories

### 11.1 Default Categories

Fixed system-wide categories available in every trip. Cannot be deleted, but can be supplemented with custom categories.

| Category | Emoji | Notes |
|---|---|---|
| Flight | ✈️ | Typically paid in origin currency before trip |
| Accommodation | 🏨 | Hotels, Airbnb, hostels |
| Food & Drink | 🍽️ | Meals, cafes, groceries |
| Transport | 🚗 | Taxis, trains, buses, car rental |
| Tour & Activities | 🎟️ | Guided tours, entry tickets |
| Entertainment | 🎉 | Clubs, events, shows |
| Shopping | 🛍️ | Retail purchases |
| Gift | 🎁 | Souvenirs, gifts for others |
| Health & Medical | 💊 | Pharmacy, clinic, insurance |
| Communication | 📱 | SIM cards, roaming, WiFi |
| Other | 📌 | Catch-all fallback |

### 11.2 Custom Categories

Trip members can add custom categories scoped to a specific trip.

**Stored in:** `trips/{tripId}.customCategories[]`

```
customCategories: [
  {
    id: string,           // e.g. "custom_abc123"
    name: string,         // e.g. "Visa Fees"
    emoji: string,        // user picks from emoji picker, default "🏷️"
    createdBy: string,    // userId
    createdAt: timestamp
  }
]
```

**Rules:**
- Any real trip member can add a custom category
- Custom category names must be unique within the trip (case-insensitive)
- Maximum 20 custom categories per trip
- Cannot delete a custom category if it has expenses assigned to it
- Custom categories are not shared across trips

### 11.3 Category Enum (Backend)

```python
# backend/app/utils/categories.py

DEFAULT_CATEGORIES = [
    "Flight",
    "Accommodation",
    "Food & Drink",
    "Transport",
    "Tour & Activities",
    "Entertainment",
    "Shopping",
    "Gift",
    "Health & Medical",
    "Communication",
    "Other",
]

def validate_category(category: str, custom_categories: list[str]) -> bool:
    all_valid = DEFAULT_CATEGORIES + custom_categories
    return category in all_valid
```

---

## 12. User Flows

### 12.1 New Trip Flow

1. Sign in with Google
2. First time: set home currency → saved to `users/{uid}`
3. Dashboard → "New Trip"
4. Enter trip name and dates
5. Type destination → Nominatim autocomplete shows place suggestions with country + currency
6. If multiple matches (e.g. "Bali, Indonesia · IDR" vs "Bali, India · INR") → user picks one
7. Confirmation chip appears: "🇮🇩 Indonesia · IDR" with option to override currency
8. Submit → backend creates trip with confirmed destination and currency
9. Redirect to trip detail (empty)
10. Invite real members by email and/or add ghost members by name

### 12.2 Add Expense Flow (Flexible Currency)

1. Inside trip → tap "+"
2. Select currency (defaults to destination; change to THB, IDR, etc.)
3. Enter amount — live preview shows destination currency equivalent below input
4. Select paid by (real or ghost member)
5. Split mode defaults to **Equal** — shows each member's auto-calculated share as read-only chips
6. Switch to **Percentage** — inputs appear per member; live counter shows remaining % to allocate
7. Switch to **Exact Amount** — amount input per member with individual currency selector (can differ per member)
8. Submit → backend converts, validates, snapshots rates, assigns remainder to payer, writes to Firestore
9. All real members see expense in real-time via Firestore listener
10. Expense displays: "THB 8,500 = SGD 330.85 @ 0.038924 · Jun 10, 08:32"

### 12.3 Ghost Member Flow

1. Owner taps "Members" → "Add trip buddy (no app)"
2. Enter name + home currency → ghost created
3. Any real member logs expenses with ghost as payer or split participant
4. Ghost appears in settlement: "Budi owes Jason SGD 66.17 (≈ IDR 991,741) — collect offline"
5. If Budi decides to join: owner taps "Promote" → enters email → invite sent
6. Budi accepts → all historical ghost references updated to real account

### 12.4 Balance Tracking Flow

1. User opens trip → Personal Balance Card shown immediately at top
2. Card shows net position: "You are owed SGD 330.85" or "You owe SGD 165.43"
3. Breakdown shows per-counterparty amounts in destination + home currencies
4. Ghost members shown with 👻 indicator — labeled "collect offline"
5. User taps any expense → expands to show per-member share status (paid / settled / outstanding)
6. As settlements are recorded, balance card updates in real-time

### 12.5 Settlement Flow

1. Tap "Settle Up" (amber CTA button)
2. Live rates fetched, net balances calculated, greedy simplification applied
3. Transactions listed with rate timestamp disclaimer
4. Ghost debts labeled "(not on app) — collect offline"
5. Real members mark their transactions settled in-app
6. Ghost settlements: real member manually confirms and marks settled
7. Personal Balance Card updates instantly after each settlement is recorded

---

## 13. Non-Functional Requirements

### 13.1 Performance
- Initial page load < 3s on 4G mobile
- Firestore real-time updates < 1s latency
- Exchange rate API < 500ms (cached)
- Live currency preview: debounced 300ms, uses cached rates (no API call per keystroke)

### 13.2 Security
- All API endpoints require valid Firebase ID token
- Ghost members have no login and cannot be impersonated
- Firestore security rules enforce real-member-only access

### 13.3 Firestore Security Rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
    }

    match /trips/{tripId} {
      allow read: if request.auth.uid in resource.data.members
                      .filter(m, m.role != 'ghost')
                      .map(m, m.userId);
      allow create: if request.auth != null;
      allow update, delete: if request.auth.uid == resource.data.createdBy;

      match /expenses/{expenseId} {
        allow read, write: if request.auth.uid in
          get(/databases/$(database)/documents/trips/$(tripId))
          .data.members.filter(m, m.role != 'ghost').map(m, m.userId);
      }

      match /settlements/{settlementId} {
        allow read, write: if request.auth.uid in
          get(/databases/$(database)/documents/trips/$(tripId))
          .data.members.filter(m, m.role != 'ghost').map(m, m.userId);
      }
    }
  }
}
```

### 13.4 Scalability
- Cloud Run: min-instances 0, max-instances 10
- Firestore scales automatically

---

## 14. Project Structure

### 14.1 Frontend (`/frontend`)

```
frontend/
├── public/
│   ├── icon-192.png
│   ├── icon-512.png
│   └── manifest.json
├── src/
│   ├── components/
│   │   ├── ui/               # Button, Input, Modal, Badge, Avatar
│   │   ├── expense/          # ExpenseCard, ExpenseForm, SplitInput, CurrencySelector
│   │   ├── trip/             # TripCard, TripHeader, MemberList, GhostMemberCard
│   │   ├── analytics/        # CategoryDonut, SpendingByDay, SpendingByMember, MyTimeline, MyVsAverage
│   │   └── settlement/       # SettlementCard, TransactionList
│   ├── pages/
│   │   ├── Landing.tsx
│   │   ├── Onboarding.tsx
│   │   ├── Dashboard.tsx
│   │   ├── TripDetail.tsx          # Expenses tab (default)
│   │   ├── TripAnalytics.tsx       # Analytics tab
│   │   ├── AddExpense.tsx
│   │   ├── ExpenseDetail.tsx
│   │   ├── Settlement.tsx
│   │   ├── Members.tsx
│   │   ├── InviteAccept.tsx
│   │   └── Profile.tsx
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useTrip.ts
│   │   ├── useExpenses.ts        # Firestore onSnapshot
│   │   └── useExchangeRates.ts   # Cached rates for live preview
│   ├── store/
│   │   └── useAppStore.ts        # Zustand
│   ├── services/
│   │   ├── api.ts                # Axios + auth interceptor
│   │   ├── firebase.ts
│   │   └── auth.ts
│   ├── types/
│   │   └── index.ts
│   ├── utils/
│   │   ├── currency.ts           # Format + convert using snapshotted rates
│   │   └── date.ts
│   ├── App.tsx
│   └── main.tsx
├── vite.config.ts
├── tailwind.config.ts
└── package.json
```

### 14.2 Backend (`/backend`)

```
backend/
├── app/
│   ├── main.py
│   ├── dependencies.py           # Firebase token verification
│   ├── config.py
│   ├── routers/
│   │   ├── users.py
│   │   ├── trips.py
│   │   ├── members.py            # Ghost + real member management
│   │   ├── expenses.py
│   │   ├── settlements.py
│   │   ├── analytics.py          # Group + individual analytics aggregation
│   │   └── exchange_rates.py
│   ├── services/
│   │   ├── firestore.py
│   │   ├── exchange_rates.py     # frankfurter.app + TTL cache
│   │   ├── settlement.py         # Greedy debt simplification
│   │   └── currency.py           # Destination → currency + conversion
│   ├── models/
│   │   ├── user.py
│   │   ├── trip.py               # Includes GhostMember model
│   │   ├── expense.py            # Includes rate snapshot fields
│   │   └── settlement.py
│   └── utils/
│       └── validators.py
├── requirements.txt
├── Dockerfile
└── .env.example
```

---

## 15. Design System

### 15.1 Color Palette

Duitrip uses a dark mode-only theme with a teal + amber palette reflecting travel, ocean, and warmth.

#### Base (Backgrounds & Surfaces)

| Token | Hex | Usage |
|---|---|---|
| `bg-base` | `#080C14` | App background |
| `bg-surface` | `#111827` | Cards, list items |
| `bg-elevated` | `#1A2235` | Modals, bottom sheets, dropdowns |
| `bg-border` | `#263348` | Dividers, input borders |

#### Brand — Teal (Primary)

| Token | Hex | Usage |
|---|---|---|
| `teal-light` | `#38BDF8` | Hover states, highlights |
| `teal` | `#0EA5E9` | Primary buttons, links, active nav, trip headers |
| `teal-dark` | `#0284C7` | Pressed states |

#### Brand — Amber (Accent)

| Token | Hex | Usage |
|---|---|---|
| `amber-light` | `#FCD34D` | Highlights, badges |
| `amber` | `#F59E0B` | Currency amounts, "Settle Up" CTA, category pills |
| `amber-dark` | `#D97706` | Pressed states |

#### Semantic Colors

| Token | Hex | Usage |
|---|---|---|
| `success` | `#10B981` | Positive balance (owed to you), settled state |
| `danger` | `#EF4444` | Negative balance (you owe), errors |
| `warning` | `#F97316` | Stale rates banner, offline warning |
| `info` | `#0EA5E9` | Informational states (reuses teal) |

#### Text

| Token | Hex | Usage |
|---|---|---|
| `text-primary` | `#F1F5F9` | Body text, headings |
| `text-secondary` | `#94A3B8` | Subtext, labels, descriptions |
| `text-muted` | `#475569` | Timestamps, disabled labels |
| `text-disabled` | `#334155` | Disabled inputs |

### 15.2 Usage Rules

- **Teal** — primary actions: main buttons, active navigation, links, trip header backgrounds
- **Amber** — financial highlights: expense amounts, currency labels, "Settle Up" button, category badges
- **Green/Red** — strictly balance indicators only; never used for UI chrome or decorative elements
- **Orange** — warnings only (stale exchange rate banner, offline mode)
- Never use pure white `#FFFFFF` — use `text-primary` (`#F1F5F9`) instead
- All backgrounds must be from the base palette — no ad-hoc dark colors

### 15.3 Tailwind Config

```typescript
// tailwind.config.ts
import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: {
          base: '#080C14',
          surface: '#111827',
          elevated: '#1A2235',
          border: '#263348',
        },
        teal: {
          light: '#38BDF8',
          DEFAULT: '#0EA5E9',
          dark: '#0284C7',
        },
        amber: {
          light: '#FCD34D',
          DEFAULT: '#F59E0B',
          dark: '#D97706',
        },
        success: '#10B981',
        danger: '#EF4444',
        warning: '#F97316',
        text: {
          primary: '#F1F5F9',
          secondary: '#94A3B8',
          muted: '#475569',
          disabled: '#334155',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
```

### 15.4 Component Examples

**Primary button (teal):**
```tsx
<button className="bg-teal hover:bg-teal-dark text-white font-semibold px-4 py-2 rounded-xl transition-colors">
  Add Expense
</button>
```

**Accent button (amber) — Settle Up:**
```tsx
<button className="bg-amber hover:bg-amber-dark text-bg-base font-semibold px-4 py-2 rounded-xl transition-colors">
  Settle Up
</button>
```

**Card:**
```tsx
<div className="bg-bg-surface border border-bg-border rounded-2xl p-4">
  ...
</div>
```

**Positive balance:**
```tsx
<span className="text-success font-semibold">+SGD 330.85</span>
```

**Negative balance:**
```tsx
<span className="text-danger font-semibold">-SGD 66.17</span>
```

**Currency amount (amber):**
```tsx
<span className="text-amber font-mono text-lg">SGD 600.00</span>
```

---

## 16. Git Configuration

### 16.1 `.gitignore`

```gitignore
# Environment files — never commit these
.env
.env.local
.env.production
.env.*.local
backend/.env
backend/.env.local
backend/.env.production
frontend/.env
frontend/.env.local
frontend/.env.production

# Firebase service account — highly sensitive
*service-account*.json
*firebase-adminsdk*.json
serviceAccountKey.json

# Dependencies
node_modules/
__pycache__/
*.pyc
*.pyo
.venv/
venv/
*.egg-info/

# Build outputs
dist/
build/
.vite/
*.tsbuildinfo

# Docker
*.log
docker-compose.override.yml

# Firebase emulator data — can be committed if you want persistent seed state, otherwise ignore
firebase/.emulator-data/

# IDE
.vscode/
.idea/
*.swp
*.swo
.DS_Store
Thumbs.db

# Python
.mypy_cache/
.pytest_cache/
.ruff_cache/
htmlcov/
.coverage

# GCP
.gcloud/
```

### 16.2 `.env.example` Files

**`backend/.env.example`:**
```env
# Firebase — get from GCP Console > Firebase > Project Settings
FIREBASE_PROJECT_ID=duitrip
FIREBASE_SERVICE_ACCOUNT_JSON=   # base64-encoded service account JSON from GCP

# CORS
ALLOWED_ORIGINS=https://duitrip.com,http://localhost:5173

# SendGrid — get from sendgrid.com
SENDGRID_API_KEY=SG.xxxxxxxxxxxx
FROM_EMAIL=noreply@duitrip.com

# Emulator overrides (local dev only — set in .env.local, not here)
# FIRESTORE_EMULATOR_HOST=firebase-emulator:8080
# FIREBASE_AUTH_EMULATOR_HOST=firebase-emulator:9099
```

**`frontend/.env.example`:**
```env
# Firebase Web SDK config — get from GCP Console > Firebase > Project Settings > Web App
VITE_FIREBASE_API_KEY=AIzaSy...
VITE_FIREBASE_AUTH_DOMAIN=duitrip.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=duitrip
VITE_FIREBASE_STORAGE_BUCKET=duitrip.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=000000000000
VITE_FIREBASE_APP_ID=1:000000000000:web:000000000000000000000000

# Backend API
VITE_API_BASE_URL=https://api.duitrip.com

# Firebase Emulator (set to true for local dev, false for production)
VITE_USE_FIREBASE_EMULATOR=false
VITE_FIRESTORE_EMULATOR_HOST=localhost:8080
VITE_AUTH_EMULATOR_HOST=localhost:9099
```

### 16.3 Setup Instructions for New Developer

Add this to the project `README.md`:

```markdown
## Local Setup

1. Clone the repo
2. Copy env examples:
   ```bash
   cp backend/.env.example backend/.env.local
   cp frontend/.env.example frontend/.env.local
   ```
3. Install Firebase CLI and Java 11+:
   ```bash
   npm install -g firebase-tools
   firebase login
   ```
4. Start local stack:
   ```bash
   docker compose -f docker-compose.dev.yml up --build
   ```
5. Seed test data (first time only):
   ```bash
   node firebase/seed.js
   ```
6. Open http://localhost:5173
```


---

## 17. Local Development

Local development runs entirely on your machine with no dependency on real GCP or Firebase services. The Firebase Emulator Suite provides local Firestore and Auth. Hot reload is enabled for both frontend and backend.

### 15.1 Prerequisites

- Docker + Docker Compose
- Node.js 20+ (for Firebase CLI and seed script)
- Java 11+ (required by Firebase Emulator)

```bash
# Install Firebase CLI (one-time)
npm install -g firebase-tools

# Authenticate Firebase CLI (one-time)
firebase login
```

---

### 15.2 Development Dockerfiles

**`frontend/Dockerfile.dev`** — Vite dev server with hot reload:
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

**`backend/Dockerfile.dev`** — uvicorn with hot reload:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]
```

---

### 15.3 `docker-compose.dev.yml`

```yaml
version: '3.8'

services:
  firebase-emulator:
    image: node:20-alpine
    working_dir: /app
    volumes:
      - ./firebase:/app
      - firebase_data:/app/.emulator-data
    ports:
      - "4000:4000"   # Emulator UI
      - "8080:8080"   # Firestore
      - "9099:9099"   # Auth
    command: >
      sh -c "npm install -g firebase-tools &&
             firebase emulators:start
             --only firestore,auth
             --import=.emulator-data
             --export-on-exit=.emulator-data"
    environment:
      - FIREBASE_PROJECT_ID=demo-duitrip
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:4000"]
      interval: 10s
      timeout: 5s
      retries: 10

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile.dev
    ports:
      - "8000:8000"
    volumes:
      - ./backend:/app           # Hot reload
    env_file:
      - ./backend/.env.local
    environment:
      - FIRESTORE_EMULATOR_HOST=firebase-emulator:8080
      - FIREBASE_AUTH_EMULATOR_HOST=firebase-emulator:9099
    depends_on:
      firebase-emulator:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.dev
    ports:
      - "5173:5173"
    volumes:
      - ./frontend/src:/app/src  # Hot reload — avoid mounting node_modules
    env_file:
      - ./frontend/.env.local
    depends_on:
      - backend

volumes:
  firebase_data:
```

---

### 15.4 Firebase Emulator Config

**`firebase/firebase.json`:**
```json
{
  "emulators": {
    "auth": { "port": 9099 },
    "firestore": { "port": 8080 },
    "ui": { "enabled": true, "port": 4000 }
  },
  "firestore": {
    "rules": "firestore.rules"
  }
}
```

**`firebase/.firebaserc`:**
```json
{
  "projects": {
    "default": "demo-duitrip"
  }
}
```

**`firebase/firestore.rules`** — permissive for local dev only, never deploy this:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

---

### 15.5 Seed Data

**`firebase/seed.js`** — populates emulator with a test trip, members (including ghost), and expenses:

```javascript
// Run with: node firebase/seed.js
// Requires emulator to be running on localhost:8080

process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'demo-duitrip' });
const db = getFirestore();

async function seed() {
  // Test users
  await db.collection('users').doc('user_jason').set({
    uid: 'user_jason',
    email: 'jason@test.com',
    displayName: 'Jason',
    homeCurrency: 'IDR',
    createdAt: new Date(),
  });

  await db.collection('users').doc('user_somchai').set({
    uid: 'user_somchai',
    email: 'somchai@test.com',
    displayName: 'Somchai',
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
      { userId: 'user_jason', email: 'jason@test.com', displayName: 'Jason',
        homeCurrency: 'IDR', role: 'owner', joinedAt: new Date(), ghostId: null },
      { userId: 'user_somchai', email: 'somchai@test.com', displayName: 'Somchai',
        homeCurrency: 'THB', role: 'member', joinedAt: new Date(), ghostId: null },
      { userId: null, email: null, displayName: 'Budi',
        homeCurrency: 'IDR', role: 'ghost', joinedAt: null, ghostId: 'ghost_budi01' },
    ],
    invites: [],
    customCategories: [],
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  // Test expenses
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
    paidBy: 'user_jason',
    splits: [
      { userId: 'user_jason', percentage: 34, amountInDestinationCurrency: 204,
        amountInHomeCurrency: 2284800, homeCurrency: 'IDR' },
      { userId: 'user_somchai', percentage: 33, amountInDestinationCurrency: 198,
        amountInHomeCurrency: 5247, homeCurrency: 'THB' },
      { userId: 'ghost_budi01', percentage: 33, amountInDestinationCurrency: 198,
        amountInHomeCurrency: 2217600, homeCurrency: 'IDR' },
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
    paidBy: 'user_somchai',
    splits: [
      { userId: 'user_jason', percentage: 50, amountInDestinationCurrency: 81.6,
        amountInHomeCurrency: 913920, homeCurrency: 'IDR' },
      { userId: 'user_somchai', percentage: 50, amountInDestinationCurrency: 81.6,
        amountInHomeCurrency: 2162.4, homeCurrency: 'THB' },
    ],
    receiptUrl: null,
    createdBy: 'user_somchai',
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  console.log('Seed complete. Open http://localhost:4000 to inspect data.');
  process.exit(0);
}

seed().catch(console.error);
```

---

### 15.6 Environment Files

**`backend/.env.local`** — no real Firebase credentials needed:
```env
FIREBASE_PROJECT_ID=demo-duitrip
FIRESTORE_EMULATOR_HOST=firebase-emulator:8080
FIREBASE_AUTH_EMULATOR_HOST=firebase-emulator:9099
FIREBASE_SERVICE_ACCOUNT_JSON=
ALLOWED_ORIGINS=http://localhost:5173
SENDGRID_API_KEY=
FROM_EMAIL=noreply@duitrip.com
```

**`frontend/.env.local`:**
```env
VITE_FIREBASE_API_KEY=fake-api-key-for-emulator
VITE_FIREBASE_AUTH_DOMAIN=demo-duitrip.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=demo-duitrip
VITE_FIREBASE_STORAGE_BUCKET=demo-duitrip.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=000000000000
VITE_FIREBASE_APP_ID=1:000000000000:web:000000000000000000000000
VITE_API_BASE_URL=http://localhost:8000
VITE_USE_FIREBASE_EMULATOR=true
VITE_FIRESTORE_EMULATOR_HOST=localhost:8080
VITE_AUTH_EMULATOR_HOST=localhost:9099
```

**`frontend/src/services/firebase.ts`** — emulator connection logic:
```typescript
import { initializeApp } from 'firebase/app';
import { getAuth, connectAuthEmulator } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

if (import.meta.env.VITE_USE_FIREBASE_EMULATOR === 'true') {
  connectAuthEmulator(auth, `http://${import.meta.env.VITE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  connectFirestoreEmulator(db, 'localhost', 8080);
}
```

---

### 15.7 Running Local Dev

```bash
# Start full local stack
docker compose -f docker-compose.dev.yml up --build

# Seed test data (run once after emulator is healthy)
node firebase/seed.js

# Stop
docker compose -f docker-compose.dev.yml down

# Stop and wipe emulator data
docker compose -f docker-compose.dev.yml down -v
```

**Local access points:**

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8000 |
| API docs (Swagger) | http://localhost:8000/docs |
| Firebase Emulator UI | http://localhost:4000 |
| Firestore Emulator | localhost:8080 |
| Auth Emulator | localhost:9099 |

---

## 18. Environment Variables (Production)

### Backend `.env`
```
FIREBASE_PROJECT_ID=duitrip
FIREBASE_SERVICE_ACCOUNT_JSON=<base64 encoded service account JSON>
ALLOWED_ORIGINS=https://duitrip.com,http://localhost:5173
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USER=apikey
SMTP_PASSWORD=<sendgrid api key>
FROM_EMAIL=noreply@duitrip.com
```

### Frontend `.env`
```
VITE_FIREBASE_API_KEY=
VITE_FIREBASE_AUTH_DOMAIN=
VITE_FIREBASE_PROJECT_ID=
VITE_FIREBASE_STORAGE_BUCKET=
VITE_FIREBASE_MESSAGING_SENDER_ID=
VITE_FIREBASE_APP_ID=
VITE_API_BASE_URL=https://api.duitrip.com
VITE_USE_FIREBASE_EMULATOR=false
```

---

## 19. MVP Build Order

Follow strictly. Do not jump ahead.

1. **Auth** — Firebase Google OAuth, onboarding (home currency)
2. **Trips** — Create trip, destination → currency detection, trip list
3. **Members** — Invite by email, accept invite, add ghost members
4. **Expenses (basic)** — Add expense in destination currency, equal split, list view
5. **Flexible currency input** — Currency selector, live preview, backend conversion
6. **Exchange rate snapshots** — Store rates + timestamp per expense; display in UI
7. **Custom splits** — Equal (default), percentage, and exact amount modes; remainder to payer; ghost member support
8. **Real-time sync** — Firestore `onSnapshot` on expenses + trip doc
9. **Balance tracking** — Personal balance card per user, per-expense share status (paid/settled/outstanding)
10. **Settlement** — Greedy debt calc, ghost handling, live rates with timestamp label
11. **Analytics** — Trip header with stats, group charts (category donut, daily bar, member bar), individual charts (my donut, vs average, timeline)
12. **Ghost promotion** — Promote ghost → real member, backfill expense references
13. **Custom categories** — Add/delete per trip, emoji picker, enforce constraints
14. **PWA** — Manifest, service worker, install prompt
15. **Polish** — Offline banner, stale rate warning, empty states, error handling, loading skeletons

---

## 20. Out of Scope (V1)

- Receipt photo upload
- Push notifications
- In-app payments / payment gateway
- Trip budgets / spending limits
- Export to PDF/CSV
- Native mobile app (iOS/Android)
- Multi-currency settlement (all settlement in destination currency for MVP)

---

## 21. Decisions & Open Questions

| Question | Decision |
|---|---|
| frankfurter.app is down | Return last cached rate with stale warning banner |
| Non-owner deletes own expense | Allowed within 24 hours of creation |
| Member leaves trip | Allowed only if balance is zero |
| Ghost removed from trip | Owner only; balance must be zero |
| Editing expense changes rates | Yes — re-fetches live rates, warns user before confirm |
| Settlement currency | Destination currency; also shown in each party's home currency |
| Ghost in settlement | Labeled "(not on app)"; collect offline |
| Invite emails | SendGrid free tier (100/day) — sufficient for MVP |
| Destination currency changed mid-trip | Updates trip doc only; existing expenses unchanged |
| Ghost home currency | Required when adding ghost — needed for conversion display |
| Custom category deleted with expenses | Not allowed — must reassign expenses first |
| Custom categories shared across trips | No — scoped per trip |
| Max custom categories per trip | 20 |
| Split rounding remainder | Assigned to payer in all modes |
| Exact mode validation tolerance | ±0.02 in destination currency |
| Default split mode | Equal, auto-applied when expense form opens |
