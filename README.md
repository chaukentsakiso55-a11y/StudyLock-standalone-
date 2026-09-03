# StudyLock Exact Hybrid

This repository rebuilds StudyLock around the supplied standalone HTML without changing its interface bytes. The same HTML is stored at the repository root and inside the Android app assets. Native Kotlin code adds the Android-only capabilities that HTML cannot provide.

## Included

- Visually identical StudyLock HTML with device-fix SHA-256 verification
- Exact Focus, Planner, Tutor, Quiz, Notes, Progress, Learn and Rewards screens
- Existing four-track music player and custom audio upload
- Background music continuity through an Android foreground service
- Native Android voice input for Tutor and Quiz
- Permission-gated native blocked-app redirection during active focus sessions
- Persistent, PBKDF2-hashed parent password required to end a session early
- Persistent custom block list with installed-app label and package matching
- Back-button exit protection during active focus sessions
- Native Gemini/OpenRouter tutor bridge that avoids Android WebView CORS failures
- Firebase Authentication and Cloud Firestore sync bridge
- Firebase App Check providers for debug and Play Integrity builds
- GitHub Actions test, lint, APK build and checksum artifact

## Firebase connection

The Android application ID is `com.studylock.student` and the code targets the `studylock-family` Firebase project. The previously published API key is intentionally not committed again.

Add the rotated value to your private `local.properties` file:

```properties
STUDYLOCK_FIREBASE_API_KEY=your_rotated_firebase_web_api_key
```

For GitHub Actions, add the same value as the repository secret `STUDYLOCK_FIREBASE_API_KEY`. You can optionally override `STUDYLOCK_FIREBASE_APP_ID`; the registered student app ID is already the non-secret default.

Enable Email/Password and Anonymous authentication in Firebase Authentication. Deploy `firestore.rules` before using Cloud Firestore. Register the final signing-certificate SHA-256 in Firebase App Check before enforcing Play Integrity.

## Enable real app blocking

1. Install and open the Android app.
2. Start a focus session.
3. Android opens Accessibility settings the first time.
4. Enable **StudyLock app blocking**.

The blocker reads only the active app package name. It does not retrieve screen text or perform gestures. Focus cannot start until Accessibility access is enabled. StudyLock maps common names and domains to packages, checks installed-app labels for custom entries, and accepts Android package names directly.

## AI tutor setup

Open StudyLock Settings, paste either a Google AI Studio Gemini key (`AIza...`) or an OpenRouter key (`sk-or-v1-...`), save it, and press **Test connection**. Gemini keys use `gemini-3.8-flash`; OpenRouter keys default to the current `openrouter/free` router. The key stays on the device and is excluded from Firebase state sync.

## Verify the exact HTML

```bash
./scripts/verify-html.sh
```

Expected SHA-256:

```text
cdb73b446b821a877df14927daaa00cea95171b753d180b1c52edf1733f4b3ca
```

## Build

Open the repository in Android Studio with JDK 17, or run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions uploads `app-debug.apk`, the exact HTML, and `SHA256SUMS-ci.txt` together.
