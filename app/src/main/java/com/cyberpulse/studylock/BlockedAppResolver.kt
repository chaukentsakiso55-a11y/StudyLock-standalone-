package com.cyberpulse.studylock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

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
        "samsung internet" to setOf("com.sec.android.app.sbrowser"),
        "whatsapp" to setOf("com.whatsapp"),
        "telegram" to setOf("org.telegram.messenger"),
        "discord" to setOf("com.discord"),
        "spotify" to setOf("com.spotify.music"),
        "roblox" to setOf("com.roblox.client")
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

    fun resolve(context: Context, entries: List<String>): Set<String> = buildSet {
        addAll(resolve(entries))
        installedApplications(context).forEach { application ->
            if (matches(context, application.packageName, entries.toSet())) {
                add(application.packageName)
            }
        }
    }

    fun matches(context: Context, packageName: String, entries: Set<String>): Boolean {
        if (packageName in resolve(entries.toList())) return true

        val packageKey = canonical(packageName)
        val entryKeys = entries.mapNotNull { entry ->
            canonicalEntry(entry).takeIf { it.length >= 3 }
        }
        if (entryKeys.any { entryKey ->
                packageKey.contains(entryKey) || entryKey.contains(packageKey)
            }
        ) return true

        val label = runCatching {
            val application = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            context.packageManager.getApplicationLabel(application).toString()
        }.getOrNull() ?: return false

        val labelKey = canonical(label)
        return entryKeys.any { entryKey ->
            entryKey == labelKey ||
                labelKey.contains(entryKey) ||
                entryKey.contains(labelKey)
        }
    }

    private fun installedApplications(context: Context): List<ApplicationInfo> = runCatching {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(launcherIntent, 0)
        }
        activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
    }.getOrDefault(emptyList())

    private fun canonicalEntry(entry: String): String = canonical(
        entry
            .trim()
            .lowercase()
            .replace(Regex("^https?://"), "")
            .replace(Regex("^www\\."), "")
            .substringBefore('/')
            .substringBefore(".com")
            .substringBefore(".net")
            .substringBefore(".org")
    )

    private fun canonical(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")
}
