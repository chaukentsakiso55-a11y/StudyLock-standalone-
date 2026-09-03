# StudyLock Exact Hybrid

This repository rebuilds StudyLock around the supplied standalone HTML without changing its interface bytes. The same HTML is stored at the repository root and inside the Android app assets. Native Kotlin code adds the Android-only capabilities that HTML cannot provide.

## Included

- Byte-identical StudyLock HTML and SHA-256 verification
- Exact Focus, Planner, Tutor, Quiz, Notes, Progress, Learn and Rewards screens
- Existing four-track music player and custom audio upload
- Background music continuity through an Android foreground service
- Native Android voice input for Tutor and Quiz
- Native blocked-app redirection during active focus sessions
- Back-button exit protection during active focus sessions
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

The blocker reads only the active app package name. It does not retrieve screen text or perform gestures. The exact HTML defaults map Instagram, TikTok, YouTube, and X/Twitter to their Android packages. An Android package name can also be entered directly into the HTML block list.

## Verify the exact HTML

```bash
./scripts/verify-html.sh
```

Expected SHA-256:

```text
3920e817ef6e294ca603e0b72d29834833c9ddd22d5fea4286594345c05a4803
```

## Build

Open the repository in Android Studio with JDK 17, or run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions uploads `app-debug.apk`, the exact HTML, and `SHA256SUMS-ci.txt` together.
