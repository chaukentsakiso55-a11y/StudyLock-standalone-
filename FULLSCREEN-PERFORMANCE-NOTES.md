# StudyLock 1.0.9 fullscreen and performance notes

- Immersive fullscreen hides Android system bars and allows transient swipe reveal.
- WebView layout uses the complete display width and height with cutout-safe padding.
- Android performance CSS removes live backdrop blur, animated background orbs, layered glass refraction, and long touch transitions.
- WebView scripts are injected sequentially instead of as one large parse burst.
- Renderer crash recovery recreates the activity rather than leaving a frozen WebView.
- Remote Google fonts are skipped on Android so the app uses the system font without a network/layout-shift penalty.
- Blocked-app package resolution is cached until the blocked list changes.
- Focus persistence skips redundant SharedPreferences writes when the calculated session end time has not meaningfully changed.
- Exact StudyLock HTML remains unchanged; Android-specific changes are layered on top.
