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
- Managed Firebase callable AI Tutor backend powered by Vertex AI\n- No student-entered API key; Firebase App Check protects tutor requests\n- Direct Firebase AI Logic and personal-provider paths remain emergency fallbacks
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

## AI tutor backend

The Android app calls the Firebase callable function `studyLockTutor` first. The function uses the Firebase/Google Cloud service identity to call Vertex AI, so students never paste an AI key and no provider key is stored in the APK.

One owner-side deployment is required:

1. Upgrade `studylock-family` to the Blaze plan.
2. Enable the Vertex AI API.
3. Grant the Cloud Functions runtime service account the **Vertex AI User** role.
4. Run `firebase deploy --only functions:studyLockTutor`.
5. Keep App Check enforced. For debug APKs, register the device's App Check debug token.

The function scales to zero and caps at five instances to limit costs. If it is not deployed, the app tries Firebase AI Logic directly, still without asking the student for a key.

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
