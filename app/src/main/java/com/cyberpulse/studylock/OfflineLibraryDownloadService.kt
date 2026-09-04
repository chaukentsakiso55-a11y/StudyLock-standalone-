package com.cyberpulse.studylock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.File
import java.io.FileInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors

class OfflineLibraryDownloadService : Service() {
    private var downloadTask: FileDownloadTask? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var lastNotificationPercent = -1
    private var cancelling = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelling = true
                downloadTask?.cancel()
                OfflineTutorLibraryStore.tempFile(this).delete()
                OfflineTutorLibraryStore.markReady(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (downloadTask == null) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Preparing Offline Tutor Library", "Checking download…")
                    )
                    beginDownload()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginDownload() {
        OfflineTutorLibraryStore.markChecking(this)
        val gateway = FirebaseGateway(applicationContext)
        val app = gateway.firebaseApp
        if (app == null) {
            finishWithError("Firebase Storage is not configured for this StudyLock build.")
            return
        }

        gateway.ensureTutorIdentity { authResult ->
            if (!authResult.success) {
                finishWithError(authResult.message)
                return@ensureTutorIdentity
            }

            val reference = FirebaseStorage.getInstance(app)
                .reference
                .child(BuildConfig.OFFLINE_LIBRARY_STORAGE_PATH)

            reference.metadata
                .addOnSuccessListener { metadata ->
                    val totalBytes = metadata.sizeBytes.coerceAtLeast(0L)
                    val remoteVersion = metadata.getCustomMetadata("version")
                        ?.toIntOrNull()
                        ?: BuildConfig.OFFLINE_LIBRARY_VERSION
                    val sha256 = metadata.getCustomMetadata("sha256").orEmpty().trim().lowercase()

                    OfflineTutorLibraryStore.markRemoteMetadata(
                        this,
                        totalBytes,
                        remoteVersion,
                        sha256
                    )

                    if (totalBytes <= 0L) {
                        finishWithError("The Offline Tutor Library package is empty.")
                        return@addOnSuccessListener
                    }

                    val requiredFreeSpace = totalBytes + MIN_FREE_SPACE_BYTES
                    if (filesDir.usableSpace < requiredFreeSpace) {
                        finishWithError(
                            "Not enough storage. Free at least ${formatBytes(requiredFreeSpace)} and try again."
                        )
                        return@addOnSuccessListener
                    }

                    startTransfer(reference, totalBytes, remoteVersion, sha256)
                }
                .addOnFailureListener { error ->
                    if (error is StorageException && error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        OfflineTutorLibraryStore.markUnavailable(
                            this,
                            "The Offline Tutor Library has not been published to StudyLock's download server yet.",
                            published = false
                        )
                        finishNotification("Offline Tutor Library unavailable", "The library package is not published yet.")
                    } else {
                        finishWithError(friendlyStorageError(error))
                    }
                }
        }
    }

    private fun startTransfer(
        reference: com.google.firebase.storage.StorageReference,
        totalBytes: Long,
        remoteVersion: Int,
        sha256: String
    ) {
        val tempFile = OfflineTutorLibraryStore.tempFile(this)
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        cancelling = false
        OfflineTutorLibraryStore.markDownloading(this, 0L, totalBytes)
        updateDownloadNotification(0L, totalBytes)

        downloadTask = reference.getFile(tempFile).apply {
            addOnProgressListener { snapshot ->
                val transferred = snapshot.bytesTransferred.coerceAtLeast(0L)
                val total = snapshot.totalByteCount.takeIf { it > 0L } ?: totalBytes
                OfflineTutorLibraryStore.markDownloading(this@OfflineLibraryDownloadService, transferred, total)
                updateDownloadNotification(transferred, total)
            }
            addOnSuccessListener {
                downloadTask = null
                executor.execute {
                    runCatching {
                        verifyAndInstall(tempFile, totalBytes, remoteVersion, sha256)
                    }.onSuccess {
                        val installedBytes = OfflineTutorLibraryStore.libraryFile(this@OfflineLibraryDownloadService).length()
                        OfflineTutorLibraryStore.markInstalled(
                            this@OfflineLibraryDownloadService,
                            remoteVersion,
                            installedBytes
                        )
                        finishNotification(
                            "Offline Tutor Library ready",
                            "Installed ${formatBytes(installedBytes)} for offline study."
                        )
                    }.onFailure { error ->
                        tempFile.delete()
                        finishWithError(error.message ?: "The downloaded library could not be verified.")
                    }
                }
            }
            addOnFailureListener { error ->
                downloadTask = null
                tempFile.delete()
                if (cancelling || (error is StorageException && error.errorCode == StorageException.ERROR_CANCELED)) {
                    OfflineTutorLibraryStore.markReady(this@OfflineLibraryDownloadService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    finishWithError(friendlyStorageError(error))
                }
            }
        }
    }

    private fun verifyAndInstall(
        tempFile: File,
        expectedBytes: Long,
        remoteVersion: Int,
        expectedSha256: String
    ) {
        if (!tempFile.isFile || tempFile.length() <= 0L) {
            error("The downloaded library file is missing.")
        }
        if (expectedBytes > 0L && tempFile.length() != expectedBytes) {
            error("The Offline Tutor Library download was incomplete.")
        }

        FileInputStream(tempFile).use { input ->
            val header = ByteArray(16)
            if (input.read(header) != header.size || String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") {
                error("The downloaded library is not a valid StudyLock database.")
            }
        }

        val database = SQLiteDatabase.openDatabase(
            tempFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        database.use { db ->
            val tableExists = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='reference_entries'",
                null
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) > 0
            }
            if (!tableExists) error("The downloaded library does not contain StudyLock reference entries.")

            val entries = db.rawQuery("SELECT COUNT(*) FROM reference_entries", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            if (entries < MIN_REFERENCE_ENTRIES) {
                error("The downloaded reference library is unexpectedly small.")
            }
        }

        if (expectedSha256.isNotBlank()) {
            val actualSha256 = sha256(tempFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                error("The Offline Tutor Library checksum did not match. Please download it again.")
            }
        }

        val destination = OfflineTutorLibraryStore.libraryFile(this)
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        if (!destination.isFile || destination.length() <= 0L) {
            error("StudyLock could not finish installing the Offline Tutor Library.")
        }

        OfflineTutorLibraryStore.markInstalled(this, remoteVersion, destination.length())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun updateDownloadNotification(transferred: Long, total: Long) {
        val percent = if (total > 0L) ((transferred * 100L) / total).toInt().coerceIn(0, 100) else 0
        if (percent == lastNotificationPercent && transferred > 0L) return
        lastNotificationPercent = percent

        val text = if (total > 0L) {
            "$percent% • ${formatBytes(transferred)} / ${formatBytes(total)}"
        } else {
            "${formatBytes(transferred)} downloaded"
        }
        val notification = buildNotification("Downloading Offline Tutor Library", text).let { base ->
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading Offline Tutor Library")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent, total <= 0L)
                .build()
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun finishWithError(message: String) {
        OfflineTutorLibraryStore.markError(this, message)
        finishNotification("Offline Tutor Library download failed", message.take(120))
    }

    private fun finishNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(title, text, ongoing = false)
        )
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        text: String,
        ongoing: Boolean = true
    ): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Offline Tutor Library",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "StudyLock offline reference library downloads"
            }
        )
    }

    private fun friendlyStorageError(error: Throwable): String {
        val raw = error.localizedMessage.orEmpty().trim()
        return when {
            error is StorageException && error.errorCode == StorageException.ERROR_QUOTA_EXCEEDED ->
                "The StudyLock library download server has reached its current storage quota."
            error is StorageException && error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ->
                "StudyLock could not authenticate the library download."
            error is StorageException && error.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
                "This account is not currently allowed to download the Offline Tutor Library."
            raw.isNotBlank() -> raw.take(180)
            else -> "The Offline Tutor Library could not be downloaded. Check your connection and try again."
        }
    }

    private fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        val mb = value / (1024.0 * 1024.0)
        return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }

    override fun onDestroy() {
        if (downloadTask != null && !cancelling) {
            downloadTask?.cancel()
            OfflineTutorLibraryStore.markError(
                this,
                "The library download was interrupted. Tap Download Library to try again."
            )
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.cyberpulse.studylock.action.DOWNLOAD_OFFLINE_LIBRARY"
        const val ACTION_CANCEL = "com.cyberpulse.studylock.action.CANCEL_OFFLINE_LIBRARY"
        private const val CHANNEL_ID = "studylock_offline_library"
        private const val NOTIFICATION_ID = 33017
        private const val MIN_FREE_SPACE_BYTES = 96L * 1024L * 1024L
        private const val MIN_REFERENCE_ENTRIES = 1_000L
    }
}
