# Duitrip

**Split trip expenses, effortlessly.** Duitrip is a native **Android** app for tracking
and splitting group travel costs across currencies — expenses, ghost members, live
settle-up, and analytics — backed entirely by **Firebase** (Auth + Cloud Firestore).
No server to run.

> Duitrip was originally a React PWA + FastAPI backend on Cloud Run. It's now a native
> Kotlin/Jetpack Compose app that talks directly to Firestore, with authorization
> enforced by Firestore Security Rules. The old web/backend lives in git history under
> the `web-legacy` tag.

## Features

- **Multi-currency expenses** — record in any currency; amounts convert to the trip's
  destination currency and each member's home currency, with the exchange rate **locked
  at record time**.
- **Flexible splits** — equal, by percentage, or exact amounts; rounding remainder
  always assigned to the payer.
- **Ghost members** — add people who aren't on the app yet, then promote/invite them.
- **Settle up** — greedy debt simplification shows the minimal set of "who pays whom".
- **Analytics** — spend by category, by day, by member, and your share vs the group.
- **Google + email/password sign-in**, offline-first Firestore cache.

## Tech stack

| Layer | Choice |
|-------|--------|
| UI | Kotlin + Jetpack Compose (Material 3), Navigation-Compose |
| Data | Firebase Auth + Cloud Firestore (direct, via the Android SDK) |
| Auth | Email/password + Google (Credential Manager) |
| Rates | [frankfurter.app](https://www.frankfurter.app/) |
| Build | Gradle (Kotlin DSL), min SDK 26 / target SDK 34 |

## Project layout

```
android/           # the Android app (Kotlin + Compose)
  app/src/main/java/com/duitrip/app/
    domain/        # pure business logic (splits, settlement, analytics) + unit tests
    data/          # Firestore repositories + models
    ui/            # Compose screens, navigation, theme
firebase/          # Firestore rules, emulator config, seed + backfill scripts
.github/workflows/ # CI: builds a release APK on version tags
```

## Getting started

### 1. Create a Firebase project

Enable **Authentication** (Google + Email/Password) and **Cloud Firestore**. Then add an
**Android app** with package name `com.duitrip.app` and download its
`google-services.json` into `android/app/` (see `android/app/google-services.json.example`).
This file is gitignored — never commit it.

Put your Google **Web client ID** (Auth → Google → Web SDK configuration) into
`android/app/src/main/res/values/strings.xml` as `default_web_client_id`.

### 2. Deploy the Firestore Security Rules

```
cd firebase
firebase deploy --only firestore:rules
```

> **Migrating existing data?** If your Firestore already has trips created by the old
> backend, run the additive backfill **before** deploying the strict rules, so existing
> members keep access:
>
> ```
> GOOGLE_APPLICATION_CREDENTIALS=./sa.json FIREBASE_PROJECT_ID=your-project \
>   node firebase/backfill-memberUids.js
> ```

### 3. Build the app

Open `android/` in Android Studio and let it sync (this downloads the SDK and generates
the Gradle wrapper), then Run. Or from the command line:

```
cd android
./gradlew testDebugUnitTest   # run the business-logic unit tests
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
```

### Releases (CI)

Pushing a version tag builds the APK and attaches it to the GitHub Release:

```
git tag v1.0.0 && git push origin v1.0.0
```

This requires a repo secret **`GOOGLE_SERVICES_JSON`** — the base64 of your
`google-services.json` — since that file isn't committed. See
[.github/workflows/android-release.yml](.github/workflows/android-release.yml).

## Security model

Firestore Security Rules ([firebase/firestore.rules](firebase/firestore.rules)) are the
authorization boundary: only trip members can read/write a trip and its expenses/
settlements, only the owner can archive, and expense deletion by a non-owner creator is
limited to 24 hours. A few softer invariants (max 20 custom categories, unique names,
"category in use", duplicate-invite checks) are enforced client-side.

## Local development with the emulator

```
cd firebase
firebase emulators:start --only firestore,auth   # project: demo-duitrip
node seed.js                                      # sample data
```

## License

[MIT](LICENSE) © Jason Mandela
