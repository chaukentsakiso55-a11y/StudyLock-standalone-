package com.cyberpulse.studylock

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object CustomReferenceLibraryStore {
    data class LibraryInfo(
        val file: File,
        val displayName: String,
        val entries: Int
    )

    data class ImportResult(
        val success: Boolean,
        val imported: Int = 0,
        val message: String
    )

    private const val DIRECTORY = "custom_reference_libraries"
    private const val MAX_SINGLE_DB_BYTES = 120L * 1024L * 1024L
    private const val MAX_ZIP_TOTAL_BYTES = 350L * 1024L * 1024L
    private const val MAX_DATABASES_PER_ZIP = 30

    fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun libraryFiles(context: Context): List<File> =
        directory(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("db", "sqlite", "sqlite3") }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun list(context: Context): List<LibraryInfo> =
        libraryFiles(context).mapNotNull { file ->
            validateDatabase(file)?.let { count ->
                LibraryInfo(file, humanName(file.nameWithoutExtension), count)
            }
        }

    fun removeAll(context: Context): Boolean {
        var ok = true
        libraryFiles(context).forEach { if (!it.delete()) ok = false }
        return ok
    }

    fun importUri(context: Context, uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri).ifBlank { "studylock-library" }
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val isZip = displayName.lowercase().endsWith(".zip") || mime.contains("zip")

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                if (isZip) importZip(context, input, displayName)
                else importDatabaseStream(context, input, displayName)
            } ?: ImportResult(false, message = "StudyLock could not open that library file.")
        }.getOrElse { error ->
            ImportResult(
                false,
                message = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "StudyLock could not import that library."
            )
        }
    }

    private fun importDatabaseStream(
        context: Context,
        input: InputStream,
        displayName: String
    ): ImportResult {
        val temp = File.createTempFile("studylock-custom-", ".db", context.cacheDir)
        return try {
            copyLimited(input, temp, MAX_SINGLE_DB_BYTES)
            val entries = validateDatabase(temp)
                ?: return ImportResult(
                    false,
                    message = "This file is not a compatible StudyLock reference database."
                )
            val destination = destinationFor(context, displayName)
            temp.copyTo(destination, overwrite = false)
            ImportResult(
                true,
                imported = 1,
                message = "Imported ${humanName(destination.nameWithoutExtension)} with $entries reference entries."
            )
        } finally {
            temp.delete()
        }
    }

    private fun importZip(
        context: Context,
        input: InputStream,
        zipDisplayName: String
    ): ImportResult {
        var totalBytes = 0L
        var imported = 0
        val importedNames = mutableListOf<String>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val rawName = File(entry.name).name
                val extension = rawName.substringAfterLast('.', "").lowercase()
                if (extension !in setOf("db", "sqlite", "sqlite3")) {
                    zip.closeEntry()
                    continue
                }
                if (imported >= MAX_DATABASES_PER_ZIP) {
                    throw IllegalArgumentException("This archive contains too many database files.")
                }

                val temp = File.createTempFile("studylock-custom-zip-", ".db", context.cacheDir)
                try {
                    val copied = copyLimited(zip, temp, MAX_SINGLE_DB_BYTES)
                    totalBytes += copied
                    if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                        throw IllegalArgumentException("This archive is too large for a StudyLock library import.")
                    }
                    val entries = validateDatabase(temp)
                    if (entries != null) {
                        val preferredName = rawName.ifBlank { zipDisplayName.substringBeforeLast('.') + "-$imported.db" }
                        val destination = destinationFor(context, preferredName)
                        temp.copyTo(destination, overwrite = false)
                        imported++
                        importedNames += "${humanName(destination.nameWithoutExtension)} ($entries entries)"
                    }
                } finally {
                    temp.delete()
                    zip.closeEntry()
                }
            }
        }

        return if (imported > 0) {
            ImportResult(
                true,
                imported,
                "Imported $imported StudyLock ${if (imported == 1) "library" else "libraries"}: ${importedNames.take(3).joinToString()}."
            )
        } else {
            ImportResult(
                false,
                message = "No compatible StudyLock reference databases were found inside this ZIP."
            )
        }
    }

    private fun destinationFor(context: Context, requestedName: String): File {
        val base = sanitizeName(requestedName.substringBeforeLast('.').ifBlank { "studylock-library" })
        var candidate = File(directory(context), "$base.db")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory(context), "$base-$index.db")
            index++
        }
        return candidate
    }

    private fun validateDatabase(file: File): Int? = runCatching {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_SINGLE_DB_BYTES) return@runCatching null
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            if (!tableExists(db, "reference_entries")) return@use null
            val columns = db.rawQuery("PRAGMA table_info(reference_entries)", null).use { cursor ->
                buildSet {
                    val index = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (index >= 0) add(cursor.getString(index).orEmpty().lowercase())
                    }
                }
            }
            val hasText = "body" in columns || "content" in columns
            if (!hasText || !columns.containsAll(setOf("title", "subject", "grade", "source"))) return@use null
            db.rawQuery("SELECT COUNT(*) FROM reference_entries", null).use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getInt(0).takeIf { it > 0 }
            }
        }
    }.getOrNull()

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE (type='table' OR type='view') AND name=?",
            arrayOf(table)
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }

    private fun copyLimited(input: InputStream, destination: File, limit: Long): Long {
        var total = 0L
        FileOutputStream(destination, false).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IllegalArgumentException("Library file is too large.")
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
                }.orEmpty()
        }.getOrDefault("")
    }

    private fun sanitizeName(value: String): String {
        return value
            .replace(Regex("[^A-Za-z0-9._ -]+"), "-")
            .trim(' ', '.', '-')
            .take(80)
            .ifBlank { "studylock-library" }
    }

    private fun humanName(value: String): String =
        value.replace('-', ' ').replace('_', ' ').trim().ifBlank { "StudyLock Library" }
}
