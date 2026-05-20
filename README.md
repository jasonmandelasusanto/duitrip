<p align="center">
  <img src="frontend/public/logo.png" alt="Duitrip" width="120" />
</p>

# Duitrip ✈️

Multi-currency trip expense tracker. Split costs across currencies with exchange rates locked at the moment of recording, so totals never drift after the fact.

## Features

- **Multi-currency splits** — equal, percentage, or exact amounts; exchange rates snapshotted at creation time
- **Ghost members** — add people who don't have an account yet; promote them later when they join
- **Debt simplification** — greedy algorithm minimises the number of settlement transactions
- **Settlement history** — record settlements with optional notes; edit or delete past records
- **Nudge notifications** — send an in-app reminder to a member to settle up (1-hour cooldown per sender/recipient/trip)
- **Analytics** — spending by category, by day, by member, personal vs group average
- **Expense management** — creator can edit or delete their own expenses (owners can edit any)
- **Member management** — trip owner can invite, remove members, and promote ghost members
- **Country flag emoji** — automatically shown next to trip titles based on destination currency
- **Profile pictures** — client-side canvas crop (128×128 JPEG) stored as base64 in Firestore
- **Account deletion** — permanent self-service deletion with "type delete to confirm" guard
- **PWA** — installable, persistent login across browser/app closes, works offline for reads
- **Cloudflare Turnstile** — optional CAPTCHA on sign-up/onboarding (skipped when site key is empty)
- **Email/password + Google sign-in** via Firebase Auth

## Tech Stack

| Layer | Tech |
|-------|------|
| Frontend | React 18 + Vite + TypeScript + Tailwind CSS |
| State | Zustand |
| Charts | Recharts |
| Backend | FastAPI (Python 3.11) + Uvicorn |
| Database | Firestore (Firebase) |
| Auth | Firebase Auth (email/password + Google OAuth) |
| Exchange rates | [frankfurter.app](https://www.frankfurter.app/) — 1-hour in-memory cache |
| Local dev | Docker Compose + Firebase Emulator Suite |
| CI | GitHub Actions (lint → type-check) |
| Deploy | Google Cloud Run (single container) via Cloud Build |

## Local Development

### Prerequisites

- Docker & Docker Compose
- Java 21+ (only needed if running the Firebase emulator outside Docker)

### Start everything

```bash
docker compose -f docker-compose.dev.yml up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8001 |
| Firebase Emulator UI | http://localhost:4000 |

### Seed test data

The emulator automatically imports seed data from `firebase/.emulator-data` on startup and exports it on graceful shutdown, so data persists across restarts.

To re-seed from scratch:

```bash
node firebase/seed.js
```

### Test accounts

| Name | Email | Password | Home currency |
|------|-------|----------|---------------|
| Jason | `jason@test.com` | `test1234` | IDR |
| Somchai | `somchai@test.com` | `test1234` | THB |

There is one pre-seeded trip ("Singapore Trip 2026") with two expenses and a ghost member (Budi).

### Environment variables

**`frontend/.env.local`**

```env
VITE_FIREBASE_API_KEY=fake-api-key-for-emulator
VITE_FIREBASE_AUTH_DOMAIN=demo-duitrip.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=demo-duitrip
VITE_FIREBASE_STORAGE_BUCKET=demo-duitrip.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=000000000000
VITE_FIREBASE_APP_ID=1:000000000000:web:000000000000000000000000
VITE_API_BASE_URL=http://localhost:8001
VITE_USE_FIREBASE_EMULATOR=true
VITE_TURNSTILE_SITE_KEY=       # leave empty to skip CAPTCHA locally
```

**`backend/.env.local`**

```env
FIREBASE_PROJECT_ID=demo-duitrip
FIREBASE_SERVICE_ACCOUNT_JSON=  # leave empty for emulator mode
ALLOWED_ORIGINS=http://localhost:5173
CLOUDFLARE_TURNSTILE_SECRET_KEY=  # leave empty to skip CAPTCHA locally
```

## Project Structure

```
duitrip/
├── Dockerfile                  # Production: multi-stage (React build + Python)
├── docker-compose.dev.yml      # Local dev stack
├── cloudbuild.yaml             # Google Cloud Build pipeline
├── .github/workflows/ci.yml    # GitHub Actions CI
│
├── frontend/
│   ├── src/
│   │   ├── pages/              # Route-level components
│   │   ├── components/         # Shared UI + feature components
│   │   ├── contexts/           # AuthContext (Firebase auth listener)
│   │   ├── hooks/              # useTrip, useExpenses, useExchangeRates
│   │   ├── services/           # Firebase, Axios API client, auth helpers
│   │   ├── store/              # Zustand app store
│   │   ├── utils/              # Currency formatting, date helpers, image upload
│   │   └── types/              # Shared TypeScript types
│   └── ...
│
├── backend/
│   ├── app/
│   │   ├── routers/            # FastAPI routers (users, trips, expenses, notifications, …)
│   │   ├── models/             # Pydantic request/response models
│   │   ├── services/           # Firestore, exchange rates, settlement, Turnstile
│   │   └── main.py             # App entrypoint; serves SPA static files in prod
│   └── tests/
│
└── firebase/
    ├── Dockerfile.emulator     # Firebase Emulator (Java 21 + Node 20)
    ├── firebase.json
    ├── firestore.rules
    ├── seed.js                 # Creates test users, trip, and expenses
    └── .emulator-data/         # Persisted emulator state (auto-exported on stop)
```

## API

All endpoints are prefixed with `/api`. Auth requires a Firebase ID token in the `Authorization: Bearer <token>` header.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/trips` | List the current user's trips |
| POST | `/api/trips` | Create a trip |
| GET | `/api/trips/:id` | Get a trip |
| PATCH | `/api/trips/:id` | Update trip details |
| GET | `/api/trips/:id/expenses` | List expenses (paginated) |
| POST | `/api/trips/:id/expenses` | Add an expense |
| PATCH | `/api/trips/:id/expenses/:eid` | Edit an expense (creator or owner) |
| DELETE | `/api/trips/:id/expenses/:eid` | Delete an expense (creator within 24 h, or owner) |
| GET | `/api/trips/:id/settlement` | Calculate outstanding settlement plan |
| POST | `/api/trips/:id/settlement` | Record a settlement with optional note |
| GET | `/api/trips/:id/settlements` | Settlement history |
| PATCH | `/api/trips/:id/settlements/:sid` | Edit a settlement note |
| DELETE | `/api/trips/:id/settlements/:sid` | Delete a settlement record |
| GET | `/api/trips/:id/analytics` | Spending analytics |
| POST | `/api/trips/:id/invites` | Invite a member by email |
| POST | `/api/trips/:id/invites/accept` | Accept a pending invite |
| POST | `/api/trips/:id/members/ghost` | Add a ghost member |
| PATCH | `/api/trips/:id/members/ghost/:gid` | Update a ghost member's name/currency |
| POST | `/api/trips/:id/members/ghost/:gid/promote` | Send invite to promote ghost to real member |
| DELETE | `/api/trips/:id/members/:mid` | Remove a member (owner only) |
| POST | `/api/trips/:id/nudge` | Send a settlement reminder notification (1-hour cooldown) |
| GET | `/api/exchange-rates/:from/:to` | Spot rate (cached 1 h) |
| GET | `/api/users/me` | Current user profile |
| PATCH | `/api/users/me` | Update display name, home currency, or photo |
| POST | `/api/users/me/init` | Onboarding (sets home currency, verifies Turnstile) |
| DELETE | `/api/users/me` | Permanently delete account and Firebase Auth user |
| GET | `/api/notifications` | List in-app notifications for current user |
| POST | `/api/notifications/read-all` | Mark all notifications as read |
| DELETE | `/api/notifications/:id` | Dismiss a notification |
| GET | `/health` | Health check (no auth) |

Interactive docs: http://localhost:8001/api/docs

## Deployment (Google Cloud Run)

The `cloudbuild.yaml` builds a single Docker image (frontend bundled into the Python backend) and deploys it to Cloud Run.

### One-time setup

```bash
# Enable required APIs
gcloud services enable cloudbuild.googleapis.com run.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com

# Create Artifact Registry repo
gcloud artifacts repositories create duitrip \
  --repository-format=docker --location=us-central1

# Store secrets
gcloud secrets create FIREBASE_SERVICE_ACCOUNT_JSON --data-file=service-account.json
gcloud secrets create CLOUDFLARE_TURNSTILE_SECRET_KEY --data-file=-  # paste key, Ctrl-D

# Grant Cloud Build permissions (replace PROJECT_NUMBER)
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:PROJECT_NUMBER@cloudbuild.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:PROJECT_NUMBER@cloudbuild.gserviceaccount.com" \
  --role="roles/run.admin"
```

Then connect your GitHub repo in the Cloud Build console and set the `_VITE_FIREBASE_*` substitution variables to your production Firebase project values.

## Running Tests

```bash
# Backend unit tests
cd backend
pip install -r requirements.txt pytest pytest-asyncio
pytest tests/ -v
```

## License

MIT
