# Duitrip — Android app

Native Android client (Kotlin + Jetpack Compose) talking directly to Firebase
(Auth + Firestore). No backend server.

## Prerequisites

- **Android Studio** (latest stable) — bundles the JDK, Android SDK, and Gradle.
- A Firebase project with **Authentication** (Google + Email/Password) and
  **Cloud Firestore** enabled.

## First-time setup

1. In the Firebase console, add an **Android app** with package name
   `com.duitrip.app`, then download its `google-services.json` into `android/app/`.
   (A template is committed as `app/google-services.json.example`; the real file is
   gitignored.)
2. Put your Google **Web client ID** (Firebase console → Authentication → Google →
   Web SDK configuration) into `app/src/main/res/values/strings.xml` as
   `default_web_client_id` — needed for Google sign-in via Credential Manager.
3. Open the `android/` folder in Android Studio and let it **sync**. This regenerates
   the Gradle wrapper (`gradle/wrapper/gradle-wrapper.jar`) and downloads the SDK.

## Build the APK

```
cd android
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed release build (configure signing first)
./gradlew testDebugUnitTest  # run the business-logic unit tests
```

Install the debug APK on a device/emulator:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

> Note: `gradle/wrapper/gradle-wrapper.jar` is not committed here; Android Studio
> generates it on first sync (or run `gradle wrapper --gradle-version 8.9` if you have
> a local Gradle).
