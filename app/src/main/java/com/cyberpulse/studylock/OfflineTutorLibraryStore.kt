package com.cyberpulse.studylock

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object OfflineTutorLibraryStore {
    private const val PREFS = "studylock_offline_tutor_library_v1"
    private const val DIRECTORY = "offline_tutor_library"
    private const val FILE_NAME = "studylock_reference_library.db"
    private const val TEMP_FILE_NAME = "studylock_reference_library.db.part"
    private const val STARTER_ASSET = "studylock_reference_starter.db"

    private const val KEY_STATUS = "status"
    private const val KEY_TRANSFERRED = "transferred"
    private const val KEY_TOTAL = "total"
    private const val KEY_ERROR = "error"
    private const val KEY_REMOTE_VERSION = "remote_version"
    private const val KEY_INSTALLED_VERSION = "installed_version"
    private const val KEY_REMOTE_SHA256 = "remote_sha256"
    private const val KEY_PUBLISHED = "published"
    private const val KEY_LAST_UPDATED = "last_updated"
    private const val KEY_STARTER = "starter_installed"

    fun libraryDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun libraryFile(context: Context): File = File(libraryDirectory(context), FILE_NAME)

    fun tempFile(context: Context): File = File(libraryDirectory(context), TEMP_FILE_NAME)

    fun ensureStarterInstalled(context: Context) {
        val destination = libraryFile(context)
        if (destination.isFile && destination.length() > 0L) return

        val temp = File(libraryDirectory(context), "$FILE_NAME.starter")
        runCatching {
            context.assets.open(STARTER_ASSET).use { input ->
                FileOutputStream(temp, false).use { output -> input.copyTo(output) }
            }
            if (!temp.isFile || temp.length() <= 0L) error("Starter library asset is empty")
            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            if (!destination.isFile || destination.length() <= 0L) {
                error("Starter library could not be installed")
            }

            prefs(context).edit()
                .putString(KEY_STATUS, "installed")
                .putInt(KEY_INSTALLED_VERSION, 1)
                .putLong(KEY_TRANSFERRED, destination.length())
                .putLong(KEY_TOTAL, destination.length())
                .putBoolean(KEY_STARTER, true)
                .putString(KEY_ERROR, "")
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()
        }.onFailure {
            temp.delete()
        }
    }

    fun isInstalled(context: Context): Boolean {
        ensureStarterInstalled(context)
        return libraryFile(context).let { it.isFile && it.length() > 0L }
    }

    fun state(context: Context): JSONObject {
        ensureStarterInstalled(context)
        val prefs = prefs(context)
        val installedFile = libraryFile(context)
        val installed = installedFile.isFile && installedFile.length() > 0L
        val starter = installed && prefs.getBoolean(KEY_STARTER, false)
        val status = prefs.getString(KEY_STATUS, null)
            ?: if (installed) "installed" else "ready"
        val total = prefs.getLong(KEY_TOTAL, 0L)
        val transferred = when {
            status == "installed" && installed -> installedFile.length()
            else -> prefs.getLong(KEY_TRANSFERRED, 0L)
        }
        val remoteVersion = prefs.getInt(KEY_REMOTE_VERSION, BuildConfig.OFFLINE_LIBRARY_VERSION)
        val installedVersion = prefs.getInt(KEY_INSTALLED_VERSION, if (starter) 1 else 0)
        val published = prefs.getBoolean(KEY_PUBLISHED, false)

        return JSONObject().apply {
            put("available", BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank())
            put("published", published)
            put("installed", installed)
            put("starterInstalled", starter)
            put("status", if (!installed && status == "installed") "ready" else status)
            put("progress", if (total > 0L) ((transferred * 100L) / total).coerceIn(0L, 100L) else 0L)
            put("transferredBytes", transferred)
            put("totalBytes", total)
            put("installedBytes", if (installed) installedFile.length() else 0L)
            put("freeBytes", context.filesDir.usableSpace)
            put("remoteVersion", remoteVersion)
            put("installedVersion", installedVersion)
            put("updateAvailable", installed && published && (starter || remoteVersion > installedVersion))
            put("error", prefs.getString(KEY_ERROR, "").orEmpty())
            put("storagePath", BuildConfig.OFFLINE_LIBRARY_STORAGE_PATH)
            put("lastUpdatedAt", prefs.getLong(KEY_LAST_UPDATED, 0L))
        }
    }

    fun markChecking(context: Context) {
        ensureStarterInstalled(context)
        prefs(context).edit()
            .putString(KEY_STATUS, "checking")
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markRemoteMetadata(
        context: Context,
        totalBytes: Long,
        remoteVersion: Int,
        sha256: String
    ) {
        prefs(context).edit()
            .putBoolean(KEY_PUBLISHED, true)
            .putLong(KEY_TOTAL, totalBytes.coerceAtLeast(0L))
            .putInt(KEY_REMOTE_VERSION, remoteVersion.coerceAtLeast(1))
            .putString(KEY_REMOTE_SHA256, sha256)
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markDownloading(context: Context, transferred: Long, total: Long) {
        prefs(context).edit()
            .putString(KEY_STATUS, "downloading")
            .putLong(KEY_TRANSFERRED, transferred.coerceAtLeast(0L))
            .putLong(KEY_TOTAL, total.coerceAtLeast(0L))
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markInstalled(context: Context, version: Int, bytes: Long) {
        prefs(context).edit()
            .putString(KEY_STATUS, "installed")
            .putInt(KEY_INSTALLED_VERSION, version.coerceAtLeast(1))
            .putLong(KEY_TRANSFERRED, bytes.coerceAtLeast(0L))
            .putLong(KEY_TOTAL, bytes.coerceAtLeast(0L))
            .putBoolean(KEY_PUBLISHED, true)
            .putBoolean(KEY_STARTER, false)
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markReady(context: Context) {
        ensureStarterInstalled(context)
        val installed = libraryFile(context).let { it.isFile && it.length() > 0L }
        prefs(context).edit()
            .putString(KEY_STATUS, if (installed) "installed" else "ready")
            .putLong(KEY_TRANSFERRED, if (installed) libraryFile(context).length() else 0L)
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markUnavailable(context: Context, message: String, published: Boolean = false) {
        ensureStarterInstalled(context)
        val installed = libraryFile(context).let { it.isFile && it.length() > 0L }
        prefs(context).edit()
            .putString(KEY_STATUS, if (installed) "installed" else "unavailable")
            .putBoolean(KEY_PUBLISHED, published)
            .putString(KEY_ERROR, message)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun markError(context: Context, message: String) {
        ensureStarterInstalled(context)
        val installed = libraryFile(context).let { it.isFile && it.length() > 0L }
        prefs(context).edit()
            .putString(KEY_STATUS, if (installed) "installed" else "error")
            .putString(KEY_ERROR, message)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun remoteSha256(context: Context): String =
        prefs(context).getString(KEY_REMOTE_SHA256, "").orEmpty()

    fun remoteVersion(context: Context): Int =
        prefs(context).getInt(KEY_REMOTE_VERSION, BuildConfig.OFFLINE_LIBRARY_VERSION)

    fun remove(context: Context): Boolean {
        val libraryDeleted = !libraryFile(context).exists() || libraryFile(context).delete()
        val tempDeleted = !tempFile(context).exists() || tempFile(context).delete()
        prefs(context).edit()
            .putString(KEY_STATUS, "ready")
            .putInt(KEY_INSTALLED_VERSION, 0)
            .putLong(KEY_TRANSFERRED, 0L)
            .putBoolean(KEY_STARTER, false)
            .putString(KEY_ERROR, "")
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
        ensureStarterInstalled(context)
        return libraryDeleted && tempDeleted
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
