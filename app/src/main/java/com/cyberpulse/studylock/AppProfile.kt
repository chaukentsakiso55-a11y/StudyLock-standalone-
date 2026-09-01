package com.cyberpulse.studylock

val appSpec = AppSpec(
    name = "StudyLock",
    shortName = "SL",
    tagline = "Focus with purpose. Finish what matters.",
    hero = "Start a protected study session and turn distractions into measurable progress.",
    primary = 0xFF00E6C7,
    secondary = 0xFF4C8DFF,
    focusLabel = "Protected study session",
    logHint = "Add a goal, homework item or reflection",
    features = listOf(
        AppFeature("Focus Lock", "Prepare a 25-minute to 5-hour study block.", "FOCUS"),
        AppFeature("Live Tutor", "Tutor interface prepared for the later AI connection.", "TUTOR"),
        AppFeature("Blocked Apps", "Organize the apps that should be unavailable while studying.", "SHIELD"),
        AppFeature("Study Plan", "Capture tasks and decide what comes next.", "PLAN"),
        AppFeature("Progress", "Keep a private record of completed sessions.", "STREAK"),
        AppFeature("Safety Settings", "Prepare PIN and focus rules without cloud dependency.", "LOCAL")
    ),
    metrics = listOf(
        AppMetric("Minimum", "25 min"),
        AppMetric("Maximum", "5 hours"),
        AppMetric("Tutor", "Phase 2"),
        AppMetric("Sync", "Family app")
    ),
    about = "StudyLock is a Cyber Pulse focus product created to help students protect study time. This standalone foundation works without an account or Firebase."
)
