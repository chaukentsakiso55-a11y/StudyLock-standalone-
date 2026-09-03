package com.cyberpulse.studylock

object BlockedAppResolver {
    private val aliases = linkedMapOf(
        "instagram" to setOf("com.instagram.android"),
        "tiktok" to setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "youtube" to setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.google.android.youtube.tv"
        ),
        "twitter" to setOf("com.twitter.android"),
        "x.com" to setOf("com.twitter.android"),
        "facebook" to setOf("com.facebook.katana", "com.facebook.lite"),
        "snapchat" to setOf("com.snapchat.android"),
        "reddit" to setOf("com.reddit.frontpage"),
        "netflix" to setOf("com.netflix.mediaclient"),
        "chrome" to setOf("com.android.chrome"),
        "whatsapp" to setOf("com.whatsapp")
    )

    private val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+){1,}$")
    private val packageRoots = setOf("app", "co", "com", "dev", "io", "net", "org", "za")

    fun resolve(entries: List<String>): Set<String> = buildSet {
        entries.forEach { entry ->
            val normalized = entry.trim().lowercase()
            if (
                packagePattern.matches(normalized) &&
                normalized.substringBefore('.') in packageRoots
            ) add(normalized)
            aliases.forEach { (alias, packages) ->
                if (normalized.contains(alias)) addAll(packages)
            }
        }
    }
}
