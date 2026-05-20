# CLAUDE.md — Duitrip

Primary context document for AI coding assistants. Keep this file up to date when making structural changes.

---

## 1. Project Overview

Duitrip is a multi-currency group trip expense tracker that lets travellers log expenses in any currency, split costs among trip members, and automatically calculate who owes whom using live and historical exchange rates. It solves the friction of settling group travel debts across different home currencies (e.g., a Singapore user and Indonesian user both want to see their share in their own currency).

**Status:** MVP — feature-complete for core expense/settlement flow, deployed to production on Cloud Run.

---

## 2. Tech Stack

### Frontend
| Technology | Version |
|---|---|
| React | 18.3.1 |
| TypeScript | 5.4.5 |
| Vite | 5.2.13 |
| React Router | 6.23.1 |
| Zustand | 4.5.2 |
| Axios | 1.7.2 |
| Firebase JS SDK | 10.12.0 |
| Recharts | 2.12.7 |
| Tailwind CSS | 3.4.4 |
| SheetJS (xlsx) | 0.18.5 |
| Vitest | 4.1.6 |
| vite-plugin-pwa | 0.20.0 |
| @marsidev/react-turnstile | 1.5.2 |

### Backend
| Technology | Version |
|---|---|
| Python | 3.11 |
| FastAPI | 0.111.0 |
| Uvicorn | 0.29.0 |
| firebase-admin | 6.5.0 |
| Pydantic / pydantic-settings | 2.7.1 / 2.2.1 |
| httpx | 0.27.0 |
| SendGrid | 6.11.0 |

### Infrastructure & Tooling
| Concern | Technology |
|---|---|
| Database | Firestore (NoSQL, document store) |
| Auth | Firebase Authentication (Google OAuth + email/password) |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Hosting | Google Cloud Run (single container, production) |
| CI | GitHub Actions (`.github/workflows/ci.yml`) |
| CD | Google Cloud Build (`cloudbuild.yaml`) |
| Container registry | Google Artifact Registry |
| Exchange rates | frankfurter.app (free, no API key) |
| CAPTCHA | Cloudflare Turnstile |
| Local dev orchestration | Docker Compose (`docker-compose.dev.yml`) |
| Firebase local emulation | Firebase Emulator Suite (Auth port 9099, Firestore port 8080, UI port 4000) |
| Backend linter | ruff |
| Frontend linter | ESLint with typescript-eslint |

---

## 3. Architecture Overview

### Pattern
**Single-container monolith in production.** The root `Dockerfile` does a multi-stage build: React is built by Node 20 and the output (`dist/`) is copied into the Python container as `static/`. FastAPI then serves the SPA at all non-`/api` paths via a catch-all route, and API endpoints at `/api/*`. There is no CDN, no separate frontend server in production.

**In development**, three separate Docker containers run via `docker-compose.dev.yml`:
- `firebase-emulator` — Firebase Auth + Firestore emulators
- `backend` — FastAPI with `--reload`, port 8001 on host
- `frontend` — Vite dev server, port 5173 on host

### Frontend ↔ Backend Communication
- **REST API** at `/api/*` via Axios (`frontend/src/services/api.ts`)
- **Firestore real-time listeners** (`onSnapshot`) for live trip data, expenses, and settlements — the frontend reads Firestore directly using the Firebase JS SDK, bypassing the backend for reads
- Every Axios request attaches a Firebase ID token as `Authorization: Bearer <token>`

### Data Flow
```
User action
  → React component calls api.post/patch/delete (Axios)
  → api.ts interceptor attaches Firebase ID token
  → FastAPI endpoint: dependencies.py verifies token via firebase_admin.auth.verify_id_token()
  → Router handler reads/writes Firestore via firebase_admin (prod) or google-cloud-firestore (emulator)
  → Firestore onSnapshot listener on frontend fires automatically
  → React state updated, UI re-renders
```

For settlement calculations and analytics, the frontend calls `GET /api/trips/{tripId}/settlement` or `/analytics` — these compute from all expense/settlement documents server-side and return aggregated data. There is no client-side settlement calculation (except for the balance card in `TripDetail.tsx`).

### External Services
| Service | What it does | Failure mode |
|---|---|---|
| `api.frankfurter.app` | Live and historical FX rates | Falls back to cached rates or 1.0 |
| Firebase Auth | Token issuance and verification | Hard failure — auth is required |
| Firebase Cloud Messaging | Push notifications for nudges | Silently skipped, non-critical |
| Cloudflare Turnstile | CAPTCHA on onboarding | Skipped if `CLOUDFLARE_TURNSTILE_SECRET_KEY` is empty |
| SendGrid | Invite emails | Used by `members.py` for invite flow |

### Background Jobs / Queues
None. No Celery, no queues. Rate fetching is synchronous within request handlers. The nudge cooldown check queries Firestore directly.

---

## 4. Folder Structure

```
duitrip/
├── Dockerfile                    # Production multi-stage build (Node→Python, single container)
├── docker-compose.dev.yml        # Dev: firebase-emulator + backend + frontend services
├── cloudbuild.yaml               # Google Cloud Build CI/CD pipeline
├── .github/workflows/ci.yml      # GitHub Actions: backend lint+test, frontend type+lint+test+build
│
├── backend/
│   ├── Dockerfile                # Production backend image (python:3.11-slim, port 8080)
│   ├── Dockerfile.dev            # Dev backend image (with --reload, port 8000)
│   ├── requirements.txt          # Python runtime deps (no pytest — added in CI)
│   ├── .env.local                # Local dev secrets (gitignored)
│   └── app/
│       ├── main.py               # FastAPI app init, CORS, router mounting, SPA serving
│       ├── config.py             # Pydantic Settings class — all env var definitions
│       ├── dependencies.py       # get_current_user() — token verification dependency
│       ├── models/
│       │   ├── user.py           # UserProfile, UserUpdate
│       │   ├── trip.py           # TripCreate, TripUpdate, GhostMemberCreate, GhostMemberUpdate, GhostPromote, InviteCreate, CustomCategoryCreate
│       │   ├── expense.py        # ExpenseCreate, ExpenseUpdate, SplitInput
│       │   ├── settlement.py     # SettlementCreate
│       │   └── notification.py   # NudgeRequest
│       ├── routers/
│       │   ├── users.py          # /api/users/me — CRUD + onboarding init
│       │   ├── trips.py          # /api/trips — CRUD, duplicate, archive, nudge
│       │   ├── members.py        # /api/trips/{id}/members — ghost, promote, invite, remove
│       │   ├── expenses.py       # /api/trips/{id}/expenses — CRUD + comment sub-resource
│       │   ├── settlements.py    # /api/trips/{id}/settlement(s) — plan + history + balance
│       │   ├── analytics.py      # /api/trips/{id}/analytics — group + individual stats
│       │   ├── exchange_rates.py # /api/exchange-rates — proxy to frankfurter with cache
│       │   ├── categories.py     # /api/trips/{id}/categories — custom category CRUD
│       │   └── notifications.py  # /api/notifications — nudge inbox
│       ├── services/
│       │   ├── firestore.py      # get_db(), doc_to_dict(), stream_docs() — emulator-aware
│       │   ├── currency.py       # calculate_equal_splits(), calculate_percentage_splits(), calculate_exact_splits()
│       │   ├── exchange_rates.py # fetch_rates(), is_stale() — in-process TTL cache
│       │   ├── settlement.py     # calculate_balances(), simplify_debts()
│       │   └── turnstile.py      # verify_turnstile() — Cloudflare CAPTCHA check
│       └── utils/
│           ├── validators.py     # require_trip_member(), require_trip_owner()
│           ├── categories.py     # DEFAULT_CATEGORIES list, CATEGORY_EMOJI dict, validate_category(), get_emoji()
│           └── country_currency.py # COUNTRY_TO_CURRENCY dict, country_code_to_currency()
│
├── frontend/
│   ├── Dockerfile                # Production: node:20-alpine build → nginx:alpine serve
│   ├── Dockerfile.dev            # Dev: node:20-alpine, port 5173
│   ├── package.json              # deps + scripts: dev, build, preview, lint, test
│   ├── vite.config.ts            # Vite + React + PWA plugin config
│   ├── vitest.config.ts          # Vitest: node environment, src/**/*.test.ts
│   ├── tailwind.config.js        # Tailwind theme with custom colors (see Conventions)
│   ├── tsconfig*.json            # TypeScript project references
│   ├── .env.local                # Local dev env vars (gitignored)
│   ├── public/
│   │   ├── favicon.png
│   │   ├── icon-192.png          # PWA icon
│   │   ├── icon-512.png          # PWA icon
│   │   └── firebase-messaging-sw.js  # FCM background message service worker
│   └── src/
│       ├── main.tsx              # React root, BrowserRouter, AuthContext listener
│       ├── App.tsx               # Route definitions, AuthLayout, RequireAuth guards
│       ├── index.css             # Tailwind imports + .scrollbar-hide utility
│       ├── types/index.ts        # All shared TypeScript interfaces and DEFAULT_CATEGORIES
│       ├── store/
│       │   └── useAppStore.ts    # Zustand store: user, authLoading, trips
│       ├── services/
│       │   ├── firebase.ts       # Firebase app init, exports: app, auth, db
│       │   ├── api.ts            # Axios instance with Bearer token interceptor
│       │   └── auth.ts           # signInWithGoogle, signInWithEmailPassword, registerWithEmailPassword, signOut, friendlyAuthError
│       ├── hooks/
│       │   ├── useTrip.ts        # onSnapshot(doc 'trips/{id}') → {trip, loading}
│       │   ├── useExpenses.ts    # onSnapshot(collection 'trips/{id}/expenses' orderBy createdAt desc)
│       │   ├── useSettlements.ts # onSnapshot(collection 'trips/{id}/settlements' orderBy settledAt desc)
│       │   ├── useExchangeRates.ts # Client-side FX rate cache (1h TTL) via /api/exchange-rates
│       │   └── usePendingExpenses.ts # Offline queue via localStorage key 'duitrip_pending_expenses'
│       ├── pages/
│       │   ├── Landing.tsx       # Unauthenticated home + sign-in
│       │   ├── Onboarding.tsx    # First-time setup: display name + home currency + Turnstile
│       │   ├── Dashboard.tsx     # Trip list, multi-trip stats grid
│       │   ├── NewTrip.tsx       # Create trip with Nominatim autocomplete
│       │   ├── TripDetail.tsx    # Expense list + balance card — dual mobile/desktop layout
│       │   ├── TripAnalytics.tsx # Charts (group + individual views)
│       │   ├── AddExpense.tsx    # Create/edit expense with split modes, receipt, date picker
│       │   ├── Settlement.tsx    # Settle-up plan + settlement history
│       │   ├── Members.tsx       # Member list, invite, ghost management
│       │   ├── Profile.tsx       # User settings, photo, import/export
│       │   └── InviteAccept.tsx  # Accept trip invite from email link
│       ├── components/
│       │   ├── layout/
│       │   │   └── AppShell.tsx  # Desktop sidebar (fixed 240px lg+) + main content area
│       │   ├── ui/
│       │   │   ├── Button.tsx    # variants: primary, amber, ghost, danger; sizes: sm, md, lg
│       │   │   ├── Input.tsx     # Labeled input with error display
│       │   │   ├── Modal.tsx     # Bottom-sheet on mobile, centered on sm+
│       │   │   ├── Avatar.tsx    # Initials fallback, ghost indicator
│       │   │   ├── Badge.tsx
│       │   │   ├── ConfirmDialog.tsx
│       │   │   ├── KofiWidget.tsx
│       │   │   └── NotificationBell.tsx
│       │   ├── trip/
│       │   │   ├── TripHeader.tsx    # Trip name, dates, members, spend/budget stats
│       │   │   ├── TripCard.tsx      # Dashboard trip summary card
│       │   │   └── MemberList.tsx    # Member rows with role badges
│       │   ├── expense/
│       │   │   └── ExpenseCard.tsx   # Expandable card with receipt, comments, duplicate
│       │   ├── settlement/
│       │   │   └── SettlementCard.tsx # Outstanding transaction with nudge + mark settled
│       │   └── analytics/
│       │       ├── CategoryDonut.tsx
│       │       ├── SpendingByDay.tsx
│       │       ├── SpendingByMember.tsx
│       │       ├── MyTimeline.tsx
│       │       └── MyVsAverage.tsx
│       └── utils/
│           ├── currency.ts       # formatCurrency(), convertAmount()
│           ├── date.ts           # formatDateRange(), tripDays(), formatTimestamp()
│           ├── flag.ts           # currencyFlag() — currency code → flag emoji
│           ├── export.ts         # exportExpensesCSV(), exportSummaryCSV() — per-trip CSV
│           ├── dataExport.ts     # exportAllData() multi-sheet XLSX, parseImportXLSX(), parseXlsxDate()
│           ├── imageCompress.ts  # compressImage() — canvas resize to 1024px, JPEG 0.65 quality
│           ├── imageUpload.ts    # uploadProfilePicture() — canvas crop 128×128, JPEG 0.85
│           └── fcm.ts            # registerFcmToken() — request permission, register SW, save token
│
├── firebase/
│   ├── Dockerfile.emulator       # Java 21 + Node 20 for Firebase emulator
│   ├── firebase.json             # Emulator config: Auth 9099, Firestore 8080, UI 4000
│   ├── firestore.rules           # Allow all (dev only — production uses Firebase Console rules)
│   └── seed.js                   # Seeds test users and a trip_test trip with expenses
│
└── backend/tests/
    ├── __init__.py
    ├── test_currency.py          # Unit tests: split math + settlement simplification
    ├── test_users.py             # API tests: /users/me uid invariant (mocked Firestore)
    └── test_settlement_service.py # Unit tests: calculate_balances + simplify_debts edge cases
```

---

## 5. Key Conventions & Patterns

### ID Generation
All IDs are prefixed with a type string to make them self-describing:
```
trip_{12 hex chars}       — trips
exp_{12 hex chars}        — expenses
ghost_{8 hex chars}       — ghost members
notif_{12 hex chars}      — notifications
stl_{12 hex chars}        — settlements
custom_{8 hex chars}      — custom categories
pending_{epoch ms}        — offline pending expenses (localStorage only)
```
Ghost member identification: **any userId/ghostId string that starts with `"ghost_"` is a ghost**. This prefix check is used throughout the codebase (`str(uid).startswith("ghost_")`).

### Naming Conventions
- **Python**: snake_case for functions and variables, PascalCase for Pydantic models
- **TypeScript/React**: camelCase for variables and functions, PascalCase for components and interfaces
- **Firestore fields**: camelCase (`paidBy`, `amountInDestinationCurrency`, `createdAt`)
- **File names**: PascalCase for React components (`ExpenseCard.tsx`), camelCase for utilities (`formatCurrency.ts`)
- **API routes**: kebab-case paths (`/trips/{id}/settle-up` — except this project uses `/settlement`)
- **Environment variables**: `VITE_` prefix for frontend, plain names for backend

### Tailwind Color Tokens
Custom colors defined in `tailwind.config.js` (do not use raw Tailwind colors — always use these tokens):
```
bg-base        — page background (dark)
bg-surface     — card/panel background
bg-elevated    — hover/input background
bg-border      — border color
text-primary   — main text
text-secondary — secondary text
text-muted     — placeholder/meta text
teal           — primary action color
amber          — currency/money highlights
danger         — destructive actions
success        — positive states
warning        — caution states
```

### State Management
- **Zustand** (`useAppStore`) for global state: `user`, `authLoading`, `trips`
- **Firestore `onSnapshot`** for real-time trip/expense/settlement data (via `useTrip`, `useExpenses`, `useSettlements`)
- **Local component state** (`useState`) for UI state (modals, form inputs, loading flags)
- **`useExchangeRates`** for client-side FX rate caching — in-memory module-level cache with 1h TTL
- **`usePendingExpenses`** for offline queue — persisted to `localStorage` key `duitrip_pending_expenses`

### Desktop Layout Pattern
Every trip sub-page (`TripDetail`, `TripAnalytics`, `Members`, `Settlement`) uses a dual-layout pattern:
```tsx
// Mobile: lg:hidden
<div className="lg:hidden max-w-lg mx-auto px-4 pt-6 pb-24">
  {/* back button, TripHeader, horizontal tab bar, content */}
</div>

// Desktop: hidden lg:flex
<div className="hidden lg:flex min-h-screen">
  <div className="w-80 shrink-0 border-r ...">  {/* left panel: TripHeader + vertical tabs */}
  <div className="flex-1 min-w-0 px-6 ...">     {/* right panel: page content */}
</div>
```
The `AppShell` provides the outer global sidebar (w-60, fixed) at `lg+`. Trip pages add their own `w-80` secondary panel.

### Error Handling
- **Backend**: `HTTPException` with status codes (401/403/404/429). No global exception handler — FastAPI defaults apply.
- **Frontend API errors**: Caught in component `try/catch` blocks, often `alert()`'d for simplicity. No global error boundary.
- **Firebase Auth errors**: Mapped to user-friendly messages in `friendlyAuthError()` in `auth.ts`.
- **Exchange rate failures**: Backend falls back to cached rates or `1.0`. Frontend `useExchangeRates` swallows errors silently.
- **FCM failures**: Wrapped in `try/except` in `trips.py` nudge handler — never breaks the nudge flow.

### API Response Conventions
- Success: returns the created/updated object or `{"ok": True}`
- Errors: `{"detail": "..."}` (FastAPI default)
- No pagination envelope — lists are returned as plain JSON arrays
- Timestamps: Python `datetime` objects are serialized to ISO 8601 by FastAPI (`2026-05-20T12:00:00+00:00`)
- `doc_to_dict()` in `firestore.py` adds an `"id"` field to every Firestore document

### Authentication / Authorization Flow
1. User signs in via Firebase Auth (Google popup or email/password)
2. Firebase issues a JWT ID token (1-hour expiry, auto-refreshed by SDK)
3. `api.ts` interceptor calls `auth.currentUser.getIdToken()` before every request and sets `Authorization: Bearer <token>`
4. `get_current_user()` in `dependencies.py` calls `firebase_admin.auth.verify_id_token(token)` and returns `{"uid", "email", "name"}`
5. Every protected route has `current_user: dict = Depends(get_current_user)`
6. Trip membership is checked via `require_trip_member()` or `require_trip_owner()` in `utils/validators.py`
7. Ghost members cannot authenticate — they are referenced by `ghostId`, not `userId`

---

## 6. Data Models

### Firestore Collection Structure
```
users/
  {uid}/                        # User profile document

trips/
  {tripId}/                     # Trip document
    expenses/
      {expenseId}/              # Expense subcollection
    settlements/
      {settlementId}/           # Settlement subcollection

notifications/
  {notifId}/                    # Flat collection, queried by toUserId
```

### Trip document
```
tripId: string                  # "trip_{12hex}"
name: string
destination: string
destinationCurrency: string     # 3-letter ISO code, the "trip currency"
startDate: string               # "YYYY-MM-DD"
endDate: string                 # "YYYY-MM-DD"
budget: number | null
budgetCurrency: string          # defaults to destinationCurrency
createdBy: string               # uid of owner
members: TripMember[]           # embedded array
memberUids: string[]            # flat uid array for Firestore array_contains queries
invites: TripInvite[]           # pending invitations
customCategories: CustomCategory[]
status: "active" | "archived"   # soft delete
createdAt: Timestamp
updatedAt: Timestamp
```

**TripMember** (embedded in trip.members):
```
userId: string | null           # null for ghosts
email: string | null
displayName: string
photoURL: string | null
homeCurrency: string            # for home-currency conversion display
role: "owner" | "member" | "ghost"
joinedAt: Timestamp | null
ghostId: string | null          # "ghost_{8hex}" — set only for ghosts
```

**Business rule**: Ghost members are identified by `ghostId` starting with `"ghost_"`. `require_trip_member()` excludes ghosts — only real members with `userId` can call API endpoints.

### Expense document
```
expenseId: string               # "exp_{12hex}"
description: string
category: string                # must be in DEFAULT_CATEGORIES or customCategories
originalAmount: number          # amount in the currency user entered
originalCurrency: string
destinationCurrency: string     # snapshot of trip currency at time of creation
amountInDestinationCurrency: number  # converted amount (rate-locked)
exchangeRateUsed: number        # rate applied (originalCurrency → destinationCurrency)
exchangeRateTimestamp: Timestamp
exchangeRates: Record<string, number>  # full rates snapshot used
splitMode: "equal" | "percentage" | "exact"
paidBy: string                  # userId of payer
splits: SplitEntry[]
notes: string
receiptUrl: string | null       # base64 JPEG data URL (stored in Firestore, not Storage)
isRecurring: boolean
expenseDate: string             # "YYYY-MM-DD" — logical date, used for historical rate lookup
createdBy: string
createdAt: Timestamp            # set to expenseDate at midnight UTC, not wall-clock time
updatedAt: Timestamp
```

**SplitEntry** (embedded in expense.splits):
```
userId: string
percentage: number
amountInDestinationCurrency: number
amountInHomeCurrency: number    # in member's home currency at time of expense
homeCurrency: string
```

**Business rule**: Exchange rates are locked at expense creation. `expenseDate` controls which date is used: past date → historical rate from frankfurter.app; today or future → "latest". Historical rates are cached indefinitely (they never change).

**Business rule**: `createdAt` is set to `expenseDate` midnight UTC, not the wall-clock time of the API call. This allows historical expense ordering to work correctly.

### Settlement document
```
settlementId: string            # "stl_{12hex}"
fromUserId: string              # who paid (the debtor)
toUserId: string                # who received (the creditor)
amountInDestinationCurrency: number
destinationCurrency: string
note: string | null
settledAt: Timestamp
createdBy: string
```

### User document
```
uid: string
email: string
displayName: string
photoURL: string | null         # base64 data URL (128×128 JPEG)
homeCurrency: string            # absence or empty string triggers onboarding redirect
fcmToken: string | null         # FCM push token, updated on login
createdAt: Timestamp
```

### Notification document
```
notifId: string                 # "notif_{12hex}"
toUserId: string
fromUid: string
fromName: string
tripId: string
tripName: string
amount: number
currency: string
read: boolean
createdAt: string               # ISO string (not Timestamp) — intentional inconsistency
```

### Default Categories (ordered)
`Flight`, `Accommodation`, `Food & Drink`, `Transport`, `Tour & Activities`, `Entertainment`, `Shopping`, `Gift`, `Health & Medical`, `Communication`, `Other`

---

## 7. Environment Variables

### Frontend (prefix: `VITE_` — baked into the build bundle)

| Variable | What it controls | Required |
|---|---|---|
| `VITE_FIREBASE_API_KEY` | Firebase project API key | Yes |
| `VITE_FIREBASE_AUTH_DOMAIN` | Firebase auth domain | Yes |
| `VITE_FIREBASE_PROJECT_ID` | Firebase project ID | Yes |
| `VITE_FIREBASE_STORAGE_BUCKET` | Firebase storage bucket | Yes |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | Firebase messaging sender ID | Yes |
| `VITE_FIREBASE_APP_ID` | Firebase app ID | Yes |
| `VITE_API_BASE_URL` | Backend base URL (e.g. `https://api.duitrip.com`). Empty string = same origin | No (empty = same-origin) |
| `VITE_USE_FIREBASE_EMULATOR` | `"true"` to point Firebase SDK at local emulators | No (default false) |
| `VITE_FIRESTORE_EMULATOR_HOST` | Firestore emulator host, e.g. `localhost:8080` | Dev only |
| `VITE_AUTH_EMULATOR_HOST` | Auth emulator host, e.g. `http://localhost:9099` | Dev only |
| `VITE_TURNSTILE_SITE_KEY` | Cloudflare Turnstile site key. Empty = CAPTCHA skipped | No |
| `VITE_FIREBASE_VAPID_KEY` | Firebase VAPID key for FCM web push. Empty = push skipped | No |

### Backend

| Variable | What it controls | Required |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Firestore project ID | Yes |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Base64-encoded service account JSON. Empty = uses ADC (emulator) | Production only |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins, e.g. `https://duitrip.com,https://www.duitrip.com` | Yes |
| `SENDGRID_API_KEY` | SendGrid API key for invite emails | Yes (invite feature) |
| `FROM_EMAIL` | Sender address for invite emails | Yes (invite feature) |
| `CLOUDFLARE_TURNSTILE_SECRET_KEY` | Turnstile secret. Empty = CAPTCHA verification skipped | No |
| `FIRESTORE_EMULATOR_HOST` | e.g. `firebase-emulator:8080`. Switches Firestore client to emulator mode | Dev only |
| `FIREBASE_AUTH_EMULATOR_HOST` | e.g. `firebase-emulator:9099`. Switches Auth to emulator | Dev only |

---

## 8. How to Run Locally

### Prerequisites
- Docker + Docker Compose
- Node.js ≥20 (for host-side TypeScript checks only)

### 1. Create environment files

**`frontend/.env.local`:**
```bash
VITE_FIREBASE_API_KEY=your-key
VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=your-project-id
VITE_FIREBASE_STORAGE_BUCKET=your-project.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=000000000000
VITE_FIREBASE_APP_ID=1:000000000000:web:abc123
VITE_API_BASE_URL=http://localhost:8001
VITE_USE_FIREBASE_EMULATOR=true
VITE_FIRESTORE_EMULATOR_HOST=localhost:8080
VITE_AUTH_EMULATOR_HOST=http://localhost:9099
VITE_TURNSTILE_SITE_KEY=
VITE_FIREBASE_VAPID_KEY=
```

**`backend/.env.local`:**
```bash
FIREBASE_PROJECT_ID=demo-duitrip
FIREBASE_SERVICE_ACCOUNT_JSON=
ALLOWED_ORIGINS=http://localhost:5173
SENDGRID_API_KEY=
FROM_EMAIL=noreply@localhost
CLOUDFLARE_TURNSTILE_SECRET_KEY=
```

### 2. Start all services
```bash
docker compose -f docker-compose.dev.yml up --build
```

Services start on:
- Firebase Emulator UI: http://localhost:4000
- Backend API: http://localhost:8001/api/docs
- Frontend: http://localhost:5173

### 3. Seed test data (optional)
```bash
docker exec -it duitrip-firebase-emulator-1 node /app/seed.js
```
Creates: `jason@test.com` (SGD), `somchai@test.com` (THB), and a trip `trip_bali_2025` with expenses.

### 4. Install dependencies (host-side, for IDE support)
```bash
cd frontend && npm install
cd ../backend && pip install -r requirements.txt pytest pytest-asyncio ruff
```

### 5. Run tests
```bash
# Backend
cd backend && pytest tests/ -v

# Frontend (requires Node ≥20)
cd frontend && npm test

# Frontend type-check
cd frontend && npx tsc --noEmit

# Backend lint
cd backend && ruff check app/
```

### 6. Build for production (local test)
```bash
docker build \
  --build-arg VITE_FIREBASE_API_KEY=your-key \
  --build-arg VITE_FIREBASE_AUTH_DOMAIN=... \
  --build-arg VITE_FIREBASE_PROJECT_ID=... \
  --build-arg VITE_FIREBASE_STORAGE_BUCKET=... \
  --build-arg VITE_FIREBASE_MESSAGING_SENDER_ID=... \
  --build-arg VITE_FIREBASE_APP_ID=... \
  -t duitrip:local .
docker run -p 8080:8080 \
  -e FIREBASE_PROJECT_ID=... \
  -e FIREBASE_SERVICE_ACCOUNT_JSON=... \
  -e ALLOWED_ORIGINS=http://localhost:8080 \
  duitrip:local
```

---

## 9. Key Business Logic Locations

### Exchange Rate Locking
**`backend/app/routers/expenses.py` → `_build_expense()`** (lines 33–138)

This is the most complex function in the backend. It:
1. Determines `rate_date`: `"latest"` if `expenseDate` is today or future, else `expenseDate` for historical rates
2. Fetches rates for all relevant currencies in one call to `fetch_rates()`
3. Converts `originalAmount × originalCurrency → amountInDestinationCurrency` using the locked rate
4. Computes splits by mode (equal/percentage/exact) with remainder going to payer
5. Converts each split to home currency for each member
6. Sets `createdAt` to `expenseDate` midnight UTC (not wall-clock time)

**Risk**: Any change here affects financial calculations. All split logic is in `backend/app/services/currency.py` and has unit tests.

### Debt Simplification
**`backend/app/services/settlement.py` → `simplify_debts()`**

Greedy algorithm: sorts creditors and debtors by amount descending, matches them greedily. This minimises the number of transactions but is not guaranteed to be globally optimal for all graphs. Threshold: balances under `0.005` are considered zero.

**`calculate_balances()`** computes net balances: payer gets credit for the full expense, each split member is debited their share. Settlements adjust balances (fromUserId gets credit back, toUserId is debited).

### Offline Expense Queue
**`frontend/src/hooks/usePendingExpenses.ts`**

Expenses that fail to submit (offline) are enqueued to `localStorage['duitrip_pending_expenses']`. Auto-flush triggers on `window.online` event and on component mount. Each pending expense stores the full API payload.

### FCM Push Notifications
**`frontend/src/utils/fcm.ts` → `registerFcmToken()`** called on login.
**`backend/app/routers/trips.py` → `nudge_member()`** sends FCM via `firebase_admin.messaging`.
**`frontend/public/firebase-messaging-sw.js`** handles background messages.

FCM is entirely optional — missing VAPID key (`VITE_FIREBASE_VAPID_KEY`) skips registration silently. FCM send failures in the backend are caught and ignored.

### Trip Currency Resolution
**`backend/app/routers/trips.py` → `_resolve_currency()`** and **`frontend/src/pages/NewTrip.tsx`**

Frontend uses Nominatim (OpenStreetMap geocoding) to suggest a currency based on the selected destination. Backend has a fallback `_resolve_currency()` for when `destinationCurrency` is not sent. Both use `COUNTRY_TO_CURRENCY` / `COUNTRY_CURRENCY` lookup tables.

### Import/Export
**`frontend/src/utils/dataExport.ts`**

- `exportAllData(trips, api)`: fetches all trips' expenses and settlements via API, generates a 3-sheet XLSX (Expenses, Settlements, Members) using SheetJS
- `parseImportXLSX(file, trips)`: parses an uploaded XLSX "Expenses" sheet, resolves trip names and member display names, returns `ImportRow[]` with `valid` flag and per-row `errors[]`
- Import submits to `POST /api/trips/{tripId}/expenses` with `splitMode: 'equal'` and empty `splits: []` (equal split among all members)

---

## 10. Testing

### Backend (pytest)
```bash
cd backend
pytest tests/ -v --tb=short
```

**Test files:**
| File | Coverage |
|---|---|
| `tests/test_currency.py` | `calculate_equal_splits`, `calculate_percentage_splits`, `calculate_exact_splits`, basic `calculate_balances` + `simplify_debts` |
| `tests/test_users.py` | `/api/users/me` — uid always present, various Firestore doc states (mocked) |
| `tests/test_settlement_service.py` | `calculate_balances` (multi-expense, partial settlement, net-zero invariant) + `simplify_debts` (2/3/4-person, threshold, rounding) |

**What's NOT tested:**
- Expense creation (`_build_expense`) — the most complex function, no tests
- Exchange rate fetching (calls external API)
- Members/invite flow
- Analytics aggregation
- Any auth path (no integration tests with real Firebase)

**Mocking strategy:** `test_users.py` uses `unittest.mock.patch("app.routers.users.get_db")` and `app.dependency_overrides[get_current_user]`. Pure function tests need no mocks.

### Frontend (Vitest)
```bash
cd frontend
npm test        # requires Node ≥20
```

**Test files:**
| File | Coverage |
|---|---|
| `src/utils/currency.test.ts` | `formatCurrency` (8 cases), `convertAmount` (4 cases) |
| `src/utils/dataExport.test.ts` | `parseXlsxDate` (6 cases: undefined, ISO, datetime, locale, Excel serial, unparseable) |

**What's NOT tested:**
- React components (no jsdom, no React Testing Library)
- API calls / Axios interceptors
- Firestore hooks
- All pages and business logic in components

### CI (GitHub Actions)
`.github/workflows/ci.yml` runs on push/PR to `main`:
- **`backend-test`** job: Python 3.11, `ruff check app/`, `pytest tests/ -v`
- **`frontend-test`** job: Node 20.19, `npm run lint`, `npx tsc --noEmit`, `npm test`, `npm run build`

The backend test job sets `FIRESTORE_EMULATOR_HOST` and `FIREBASE_AUTH_EMULATOR_HOST` but does **not** start the Firebase emulator — tests that hit Firestore directly would fail. All current tests use mocks.

---

## 11. Gotchas & Known Issues

### Firestore Timestamps vs ISO strings
Firestore `onSnapshot` returns `Timestamp` objects for datetime fields, not ISO strings. Any code that receives Firestore documents via the real-time listeners (not the REST API) must handle both. See `formatTimestamp()` in `frontend/src/utils/date.ts` — it checks for `.toDate()` method before treating as string. The `exchangeRateTimestamp` field on `Expense` is typed as `unknown` for this reason.

### `memberUids` array must stay in sync with `members`
Firestore queries use `where("memberUids", "array_contains", uid)` to list trips. This flat array must always match the `userId` values in the `members` array. If you add/remove members without updating `memberUids`, the trip will disappear from or incorrectly appear in the user's trip list. All member mutations in `members.py` update both fields.

### Ghost members cannot call API endpoints
`require_trip_member()` filters out ghost members (role === "ghost"). Ghost members exist only as embedded data — they have no Firebase Auth account and cannot make authenticated requests.

### `receipt` stored as base64 in Firestore, not Storage
Receipt images (`ExpenseCreate.receiptUrl`) are base64 JPEG data URLs stored directly in the Firestore expense document. This will hit Firestore document size limits (~1MB) for large images. `imageCompress.ts` resizes to max 1024px at 65% JPEG quality, but this is not enforced server-side.

### `createdAt` on expenses is set to `expenseDate`, not wall-clock time
`_build_expense()` sets `createdAt` to `expenseDate` midnight UTC. `useExpenses` orders by `createdAt desc`. This means expenses are sorted by their logical date, not when they were entered — intentional, but surprising.

### Historical rate cache `is_stale()` bug
`is_stale()` in `exchange_rates.py` constructs `cache_key` without the `date:` prefix used in `fetch_rates()`. It will always return `False` for any query that was cached with a date prefix. This is a minor display issue (stale warning won't show when it should).

### `notifications.createdAt` is an ISO string, not a Timestamp
All other datetime fields use Firestore Timestamps. `notifications.createdAt` was written as `datetime.now(timezone.utc).isoformat()` — a plain string. The nudge cooldown check compares this string directly with `cutoff.isoformat()`, which works but is fragile. Do not change the format without updating the comparison.

### Desktop layout requires `min-w-0` on flex children
`AppShell` uses `flex flex-col min-h-screen lg:flex-row`. Without `min-w-0` on `<main>`, flex items can overflow. Always include `min-w-0` on the right panel of any `lg:flex` layout.

### vitest 4.x requires Node ≥20.12
`vitest` 4.x depends on `rolldown` which requires `node:util.styleText` (added in Node 20.12). The local development host may run an older Node — CI is pinned to Node 20.19 to avoid this. Do not run `npm test` locally unless you have Node ≥20.12.

### `firebase-messaging-sw.js` receives config via URL query params
The FCM service worker is registered with Firebase config in query params (`/firebase-messaging-sw.js?apiKey=...`). This is unconventional but necessary since the SW runs in a separate context without access to `import.meta.env`. The config is not sensitive (it's already in the app bundle), but the pattern is worth knowing.

### Backend emulator uses `google-cloud-firestore` with `AnonymousCredentials`, not `firebase_admin`
`get_db()` in `firestore.py` detects `FIRESTORE_EMULATOR_HOST` and uses `google.cloud.firestore.Client` with `AnonymousCredentials` instead of `firebase_admin.firestore.client()`. This bypasses credential loading. In production, `firebase_admin` is used. The two clients have compatible APIs but different import paths.

### Trip "delete" is a soft archive
`DELETE /api/trips/{tripId}` sets `status: "archived"` — it does not delete the Firestore document or its subcollections (expenses, settlements). Archived trips are filtered from `GET /api/trips` but can still be accessed directly by ID.

### SendGrid invite emails — no template
`members.py` uses SendGrid's `send` API. If `SENDGRID_API_KEY` is empty, invite emails silently fail. There is no email template ID — the email is constructed inline. The invite accept URL uses `window.location.origin` on the frontend, not a configured env var.

---

## 12. Deployment

### Pipeline (Automated via Google Cloud Build)
Triggered manually or on GitHub push (requires connecting repo in Cloud Build console):

```
cloudbuild.yaml steps:
  1. build   — docker build (multi-stage: Node build → Python image), tag with COMMIT_SHA + latest
  2. push    — push both tags to Artifact Registry (us-central1-docker.pkg.dev/your-firebase-project-id/duitrip/app)
  3. deploy  — gcloud run deploy duitrip --image ...:COMMIT_SHA
```

Cloud Run is configured with: 512Mi memory, 1 CPU, 0–3 instances, port 8080.

### Secrets (Cloud Build reads from Secret Manager)
- `FIREBASE_SERVICE_ACCOUNT_JSON` — base64-encoded service account JSON
- `CLOUDFLARE_TURNSTILE_SECRET_KEY` — Turnstile server key

### Non-sensitive build args (hardcoded in `cloudbuild.yaml substitutions`)
Firebase client config, Turnstile site key, allowed CORS origins.

### Environment Differences
| Concern | Dev | Production |
|---|---|---|
| Firebase | Local emulators (Auth 9099, Firestore 8080) | Real Firebase project (`your-firebase-project-id`) |
| Container | 3 separate containers via docker-compose | Single container on Cloud Run |
| Frontend serving | Vite dev server (port 5173) | FastAPI static file mount (`/static`) |
| Backend port | 8000 (container), 8001 (host) | 8080 |
| Firestore client | `google-cloud-firestore` + AnonymousCredentials | `firebase_admin.firestore` + service account |
| CORS | `http://localhost:5173` | `https://duitrip.com,https://www.duitrip.com` |

### Manual Steps Required Before First Deploy
See `cloudbuild.yaml` header comments for one-time GCP setup:
1. Enable Cloud Build, Cloud Run, Secret Manager, Artifact Registry APIs
2. Create Artifact Registry repository `duitrip` in `us-central1`
3. Store secrets in Secret Manager
4. Grant Cloud Build SA roles: `secretmanager.secretAccessor`, `run.admin`, `iam.serviceAccountUser`

### Rollback
```bash
# List recent revisions
gcloud run revisions list --service duitrip --region us-central1

# Route 100% traffic to a previous revision
gcloud run services update-traffic duitrip \
  --to-revisions REVISION_NAME=100 \
  --region us-central1
```

### Firestore Rules
Production Firestore security rules are managed in the **Firebase Console** (not in this repository). `firebase/firestore.rules` is for the local emulator only (allow-all). If you modify production rules, update them in the Firebase Console and document the change — they are not version-controlled here.
