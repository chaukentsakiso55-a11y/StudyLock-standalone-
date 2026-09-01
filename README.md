# StudyLock

Focus with purpose. Finish what matters.

This repository contains the first native Android foundation for **StudyLock**, a Cyber Pulse product.

## Included now

- Kotlin and Jetpack Compose Android project
- Dark neon liquid-glass interface
- Home, workspace, progress and settings navigation
- Local activity log and configurable focus timer
- Offline-first preferences and progress
- Unit test and GitHub Actions APK build
- No embedded API keys or fake cloud connection

## Firebase phase

Firebase is deliberately deferred. Authentication, Firestore/Realtime Database, remote sync, Cloud Functions and App Check should be added together after the Firebase projects and security rules are agreed.

## Open in Android Studio

1. Clone or download this repository.
2. Open the repository root in Android Studio.
3. Use JDK 17 and allow Gradle sync to finish.
4. Run the `app` configuration on Android 8.0 or newer.

The debug APK is also built by the **Android build** GitHub Actions workflow.

## Build configuration

- Minimum Android: API 26
- Target/compile SDK: API 35
- Android Gradle Plugin: 8.9.2
- Gradle wrapper: 8.11.1
- Kotlin: 2.1.20

## Ownership

Cyber Pulse · Foundation version 0.1.0
