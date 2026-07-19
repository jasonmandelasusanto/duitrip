# Duitrip — Project Checkpoint / Handoff

> State as of 2026-07-19. Written as a handoff so any tool/agent can continue.
> **Do not commit this file if it ever gains secrets. It currently contains none.**

## What this project is now

Native **Android app (Kotlin + Jetpack Compose)** talking **directly to Firebase**
(Auth + Cloud Firestore). It replaced a React PWA + FastAPI backend on Cloud Run
(old stack recoverable at git tag `web-legacy`). Same Firebase project as before
(`duitrip-app`) — same users, same data, no migration.

- Repo: https://github.com/jasonmandelasusanto/duitrip (currently **private**;
  history was rewritten/sanitized — every commit is `Jason Mandela <>`, no keys)
- Package: `com.duitrip.app` · minSdk 26 · target/compile 34 · Gradle 8.9 · AGP 8.5.2

## Current git state

Pushed through the CI-test workflow commit. **Local, possibly unpushed commits
(check `git log origin/main..HEAD`):**
- `1c4da03` Scale adaptive icon foreground to fit launcher mask
- `5f90a2f` Restore PWA feature parity in the native UI

Everything compiles (`assembleDebug` green), unit tests pass, all flows below were
hand-verified on an emulator against live Firebase.

## Architecture map

```
android/app/src/main/java/com/duitrip/app/
  domain/    pure logic, 1:1 ports of the old Python backend (UNIT TESTED):
             Money (floor2/banker's round2), SplitCalculator (equal/percent/exact,
             remainder→payer), SettlementCalculator (balances + greedy simplify),
             Analytics, ExpenseBuilder (rate-locked expense build), CountryCurrency,
             Categories, Currencies
  data/      AuthRepository (email/pw + Google via Credential Manager),
             UserRepository, TripRepository, ExpenseRepository, SettlementRepository,
             StorageRepository (UNUSED — Storage needs Blaze), FrankfurterClient
             (fx rates, 1h cache), NominatimClient (destination autocomplete),
             FirestoreExt (snapshot→Flow, IdGen: trip_/exp_/ghost_/stl_/custom_)
  data/model Trip/TripMember/TripInvite/CustomCategory, Expense/SplitEntry,
             Settlement, User — field names EXACTLY match Firestore docs.
             Computed getters MUST be @get:Exclude (guarded by ModelSerializationTest;
             a lambda getter once caused an infinite-cycle serialization crash)
  ui/        DuitripRoot (nav + auth gate), screens/* (Landing, Onboarding, Dashboard,
             NewTrip, TripDetail, AddExpense, Members, Settlement, Analytics, Profile,
             InviteAccept), components/*, theme/* (dark-only; tokens from old
             tailwind.config: bg #080C14, surface #111827, elevated #1A2235,
             teal #4DC3EA, danger #EF4444, success #10B981)
firebase/    firestore.rules (STRICT rules, NOT yet deployed), seed.js (emulator),
             backfill-memberUids.js (admin backfill script), test/rules.test.js
.github/workflows/
  android-test.yml     every push/PR → JVM unit tests (needs GOOGLE_SERVICES_JSON)
  firestore-rules.yml  on firebase/** changes → rules tests (JDK 21 required!)
  android-release.yml  on v* tags → signed release APK attached to GitHub Release
  android-e2e.yml      manual/tags only → emulator UI smoke test
```

## Data model & the memberUids story (IMPORTANT)

Firestore: `users/{uid}`, `trips/{tripId}` (embedded `members[]`, `invites[]`,
`customCategories[]`), subcollections `expenses/`, `settlements/`.

The app queries trips via `whereArrayContains("memberUids", uid)`. Old-backend trips
lacked that field → **a one-time backfill has ALREADY BEEN RUN on production**
(2026-07-19): all 8 real trips now have `memberUids` + `inviteEmails`. The app also
self-heals on dashboard load (`TripRepository.backfillMyTrips`), so future old-format
trips get repaired automatically.

Timestamps are Firestore `Timestamp` (not strings); `startDate/endDate` are strings;
`exchangeRateTimestamp` is an ISO string. `exchangeRates` map is the frankfurter
snapshot (base = destinationCurrency) locked at record time.

## Firebase / GCP state

- Plan: user wants **Spark (free)**. Blaze was only needed by the old Cloud Run.
  **TODO (user, in GCP console): delete Cloud Run service `duitrip` + Cloud Build
  trigger, then downgrade to Spark.** Never delete the project itself.
- **Firestore rules in production are still permissive (auth-required but open).**
  The strict rules in `firebase/firestore.rules` are written + tested but NOT
  deployed. Backfill is done, so deploying them is now safe — but the app-side
  self-heal scans all trips, which strict rules would block; harmless (it fails
  soft), but consider removing `backfillMyTrips` once rules are strict.
- Google Sign-In: WORKS. `default_web_client_id` comes from google-services.json
  (do NOT redefine it in strings.xml — duplicate resource). Release keystore SHA-1
  `F4:7D:2E:8F:...:38:4D` is registered in Firebase.
- `android/app/google-services.json` is REAL and **gitignored** — never commit.
- Receipt-photo upload: NOT implemented (Firebase Storage requires Blaze).

## Secrets & credentials (NONE are in the repo)

GitHub Actions secrets already set: `GOOGLE_SERVICES_JSON` (base64 of the json),
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` (= `duitrip`).

⚠️ The release keystore + its password live ONLY in a **temporary scratchpad**:
`C:\Users\Admin\AppData\Local\Temp\claude\c--Users-Admin-personal-files-git-repositories-duitrip\099326b9-daee-4122-acec-44e73cee3ac0\scratchpad\`
(`release.jks`, `release-keystore-b64.txt`, `signing-credentials.txt`).
**BACK THESE UP PERMANENTLY NOW** — losing the keystore means you can't update the
app under the same signature. (They also exist inside the GitHub secrets.)

## Local dev environment (this Windows machine)

- JDK 21: `C:\Program Files\Java\jdk-21.0.10` (set JAVA_HOME)
- Android SDK: `C:\Android\sdk` (set ANDROID_HOME); `android/local.properties`
  already points to it
- Gradle: `C:\Gradle\gradle-8.9\bin\gradle.bat` (no wrapper jar committed)
- Emulator AVD: `duitrip_test` (Pixel 6, API 34).
  Run: `C:\Android\sdk\emulator\emulator.exe -avd duitrip_test -no-window -gpu swiftshader_indirect`
- Build: `gradle -p android assembleDebug` → app/build/outputs/apk/debug/
- Tests: `gradle -p android testDebugUnitTest` (8 suites)
- Release: push tag `v*` → CI builds signed APK onto the GitHub Release

## Verified working (emulator, live Firebase)

Register/sign-in (email + Google), onboarding currency, dashboard, create trip with
Nominatim autocomplete (picked place's country sets currency), ghost members
(add/edit/promote UI), expenses in any currency with equal/percent/exact splits,
expandable expense rows w/ per-member paid/owes + home-currency amounts,
edit/delete expense (24h creator rule), category filter chips, custom categories
(inline create), edit/archive trip, settle-up (balance card, greedy plan, FX
conversions, record w/ note, undo), analytics (category/day/member/timeline),
adaptive launcher icon.

## Known gaps / next steps

1. Push `1c4da03` + `5f90a2f`, tag a release, install the new APK.
2. User must confirm their 8 real trips appear on their phone (backfill done;
   force-close app first). If not: compare their auth uid vs `members[].userId`
   in a trip doc.
3. Delete Cloud Run + Cloud Build trigger → downgrade to Spark.
4. Deploy strict `firestore.rules` (safe now) — then optionally remove self-heal.
5. Repo is still private → make public when ready (history already sanitized;
   working tree scanned clean).
6. Optional: receipt upload (needs Blaze), percentage/exact split UI polish,
   Compose UI tests beyond the smoke test.
7. Temp files cleanup: none needed — all test accounts/trips created during
   verification were deleted from Firebase.
