package com.cyberpulse.studylock

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
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

    private data class ReferenceEntry(
        val title: String,
        val subject: String,
        val grade: String,
        val source: String,
        val body: String
    )

    private const val DIRECTORY = "custom_reference_libraries"
    private const val MAX_SINGLE_DB_BYTES = 160L * 1024L * 1024L
    private const val MAX_IMPORT_BYTES = 420L * 1024L * 1024L
    private const val MAX_TEXT_BYTES = 24L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 24L * 1024L * 1024L
    private const val MAX_DATABASES_PER_ZIP = 40
    private const val MAX_REFERENCES_PER_IMPORT = 60_000
    private const val MAX_BODY_CHARS = 180_000

    private val databaseExtensions = setOf("db", "sqlite", "sqlite3")
    private val textExtensions = setOf(
        "json", "jsonl", "ndjson", "txt", "md", "markdown", "csv", "html", "htm"
    )

    fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun libraryFiles(context: Context): List<File> =
        directory(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in databaseExtensions }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
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
        val displayName = queryDisplayName(context, uri).ifBlank { "studylock-library.pack" }
        val temp = File.createTempFile("studylock-import-", ".pack", context.cacheDir)
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(false, message = "StudyLock could not open that library file.")
            input.use { copyLimited(it, temp, MAX_IMPORT_BYTES) }
            importDownloadedFile(context, temp, displayName)
        } catch (error: Throwable) {
            ImportResult(
                false,
                message = error.localizedMessage?.takeIf { it.isNotBlank() }?.take(220)
                    ?: "StudyLock could not import that library pack."
            )
        } finally {
            temp.delete()
        }
    }

    private fun importDownloadedFile(context: Context, file: File, displayName: String): ImportResult {
        if (file.length() <= 0L) {
            return ImportResult(false, message = "The selected library file is empty.")
        }
        return when {
            looksLikeZip(file) -> importZipFile(context, file, displayName)
            looksLikeSqlite(file) -> importDatabaseFile(context, file, displayName)
            else -> importTextFile(context, file, displayName, displayName)
        }
    }

    private fun importDatabaseFile(context: Context, file: File, displayName: String): ImportResult {
        if (file.length() > MAX_SINGLE_DB_BYTES) {
            return ImportResult(false, message = "That database is too large for StudyLock to import safely.")
        }

        val compatibleEntries = validateDatabase(file)
        if (compatibleEntries != null) {
            val destination = destinationFor(context, displayName)
            file.copyTo(destination, overwrite = false)
            return ImportResult(
                true,
                imported = 1,
                message = "Imported ${humanName(destination.nameWithoutExtension)} with $compatibleEntries reference entries."
            )
        }

        val converted = extractGenericDatabase(file, displayName)
        if (converted.isNotEmpty()) {
            return createCanonicalDatabase(context, displayName, converted)
        }

        return ImportResult(
            false,
            message = "StudyLock opened this SQLite file, but it did not contain readable study-reference text."
        )
    }

    private fun importTextFile(
        context: Context,
        file: File,
        displayName: String,
        sourcePath: String
    ): ImportResult {
        if (file.length() > MAX_TEXT_BYTES) {
            return ImportResult(false, message = "That text-based library pack is too large to convert on this device.")
        }
        val bytes = file.readBytes()
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.isBlank()) {
            return ImportResult(false, message = "StudyLock could not find readable text inside this library file.")
        }
        val entries = parseTextAsset(displayName, sourcePath, text)
        if (entries.isEmpty()) {
            return ImportResult(
                false,
                message = "This pack did not contain readable JSON, text, Markdown, CSV, or HTML study references."
            )
        }
        return createCanonicalDatabase(context, displayName, entries)
    }

    private fun importZipFile(context: Context, file: File, zipDisplayName: String): ImportResult {
        var totalBytes = 0L
        var importedDatabases = 0
        var convertedTextEntries = 0
        val importedNames = mutableListOf<String>()
        val textReferences = mutableListOf<ReferenceEntry>()

        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val entryPath = entry.name.replace('\\', '/')
                val rawName = File(entryPath).name.ifBlank { "library-entry" }
                val extension = rawName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val bytes = readEntryBytes(zip, MAX_ENTRY_BYTES)
                totalBytes += bytes.size
                if (totalBytes > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("This library archive is too large for StudyLock to import safely.")
                }

                if (looksLikeSqlite(bytes) || extension in databaseExtensions) {
                    if (importedDatabases >= MAX_DATABASES_PER_ZIP) {
                        throw IllegalArgumentException("This archive contains too many database files.")
                    }
                    val temp = File.createTempFile("studylock-db-entry-", ".db", context.cacheDir)
                    try {
                        temp.writeBytes(bytes)
                        val result = importDatabaseFile(context, temp, rawName)
                        if (result.success) {
                            importedDatabases += result.imported.coerceAtLeast(1)
                            importedNames += humanName(rawName.substringBeforeLast('.'))
                        }
                    } finally {
                        temp.delete()
                    }
                } else if (extension in textExtensions || looksLikeText(bytes)) {
                    if (bytes.size.toLong() <= MAX_TEXT_BYTES) {
                        val text = bytes.toString(Charsets.UTF_8)
                        val parsed = parseTextAsset(rawName, entryPath, text)
                        if (parsed.isNotEmpty()) {
                            val room = MAX_REFERENCES_PER_IMPORT - textReferences.size
                            if (room > 0) textReferences += parsed.take(room)
                        }
                    }
                }

                zip.closeEntry()
            }
        }

        if (textReferences.isNotEmpty()) {
            val textName = zipDisplayName.substringBeforeLast('.').ifBlank { "website-library" }
            val result = createCanonicalDatabase(context, "$textName-converted.db", textReferences)
            if (result.success) {
                convertedTextEntries = textReferences.size
                importedDatabases += result.imported.coerceAtLeast(1)
                importedNames += "$textName converted"
            }
        }

        return if (importedDatabases > 0) {
            val details = buildString {
                append("Imported $importedDatabases StudyLock ")
                append(if (importedDatabases == 1) "library" else "libraries")
                if (convertedTextEntries > 0) append(" with $convertedTextEntries converted text references")
                if (importedNames.isNotEmpty()) append(": ${importedNames.distinct().take(4).joinToString()}")
                append('.')
            }
            ImportResult(true, importedDatabases, details)
        } else {
            ImportResult(
                false,
                message = "StudyLock could not find a usable database or readable JSON/TXT/Markdown/CSV/HTML reference inside this ZIP."
            )
        }
    }

    private fun parseTextAsset(name: String, path: String, rawText: String): List<ReferenceEntry> {
        val text = rawText.trim()
        if (text.isBlank()) return emptyList()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "json" -> parseJson(text, name, path)
            "jsonl", "ndjson" -> parseJsonLines(text, name, path)
            "csv" -> parseCsv(text, name, path)
            "html", "htm" -> listOfNotNull(referenceFromPlainText(name, path, stripHtml(text)))
            else -> {
                if (text.startsWith("{") || text.startsWith("[")) {
                    parseJson(text, name, path).ifEmpty {
                        listOfNotNull(referenceFromPlainText(name, path, text))
                    }
                } else {
                    listOfNotNull(referenceFromPlainText(name, path, text))
                }
            }
        }
    }

    private fun parseJson(text: String, name: String, path: String): List<ReferenceEntry> = runCatching {
        val root = JSONTokener(text).nextValue()
        val output = mutableListOf<ReferenceEntry>()
        val grade = inferGrade(path)
        val subject = inferSubject(path, grade)
        collectJson(root, output, source = path, defaultGrade = grade, defaultSubject = subject, titleHint = humanName(name.substringBeforeLast('.')), depth = 0)
        output.take(MAX_REFERENCES_PER_IMPORT)
    }.getOrDefault(emptyList())

    private fun parseJsonLines(text: String, name: String, path: String): List<ReferenceEntry> {
        val output = mutableListOf<ReferenceEntry>()
        text.lineSequence().take(MAX_REFERENCES_PER_IMPORT).forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEachIndexed
            runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()?.let { value ->
                collectJson(
                    value,
                    output,
                    source = "$path#${index + 1}",
                    defaultGrade = inferGrade(path),
                    defaultSubject = inferSubject(path, inferGrade(path)),
                    titleHint = humanName(name.substringBeforeLast('.')),
                    depth = 0
                )
            }
        }
        return output.take(MAX_REFERENCES_PER_IMPORT)
    }

    private fun collectJson(
        value: Any?,
        output: MutableList<ReferenceEntry>,
        source: String,
        defaultGrade: String,
        defaultSubject: String,
        titleHint: String,
        depth: Int
    ) {
        if (value == null || value == JSONObject.NULL || depth > 10 || output.size >= MAX_REFERENCES_PER_IMPORT) return
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectJson(value.opt(index), output, source, defaultGrade, defaultSubject, titleHint, depth + 1)
                    if (output.size >= MAX_REFERENCES_PER_IMPORT) break
                }
            }
            is JSONObject -> {
                val grade = optString(value, "grade", "level", "schoolGrade", "year").ifBlank { defaultGrade }
                val subject = optString(value, "subject", "course", "learningArea", "category").ifBlank { defaultSubject }
                val title = optString(value, "title", "name", "topic", "heading", "question").ifBlank { titleHint }
                val sourceValue = optString(value, "source", "url", "reference", "origin").ifBlank { source }
                val body = optString(
                    value,
                    "body", "content", "text", "summary", "description", "notes", "answer", "lesson", "definition", "explanation"
                )

                if (body.isNotBlank()) {
                    output += ReferenceEntry(
                        title = title.ifBlank { "StudyLock Reference" }.take(220),
                        subject = subject.take(120),
                        grade = normalizeGrade(grade).take(60),
                        source = sourceValue.take(500),
                        body = body.trim().take(MAX_BODY_CHARS)
                    )
                    return
                }

                val keys = value.keys()
                while (keys.hasNext() && output.size < MAX_REFERENCES_PER_IMPORT) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is JSONObject || child is JSONArray) {
                        val inferredGrade = inferGrade(key).ifBlank { grade }
                        val inferredSubject = if (inferGrade(key).isNotBlank()) subject else inferSubjectFromKey(key).ifBlank { subject }
                        collectJson(
                            child,
                            output,
                            source,
                            inferredGrade,
                            inferredSubject,
                            humanName(key),
                            depth + 1
                        )
                    } else if (child is String && child.trim().length >= 45) {
                        output += ReferenceEntry(
                            title = humanName(key).take(220),
                            subject = subject.take(120),
                            grade = normalizeGrade(grade).take(60),
                            source = source.take(500),
                            body = child.trim().take(MAX_BODY_CHARS)
                        )
                    }
                }
            }
            is String -> referenceFromPlainText(titleHint, source, value)?.let(output::add)
        }
    }

    private fun parseCsv(text: String, name: String, path: String): List<ReferenceEntry> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(MAX_REFERENCES_PER_IMPORT + 1).toList()
        if (lines.isEmpty()) return emptyList()
        val headers = parseCsvLine(lines.first()).map { it.trim().lowercase(Locale.ROOT) }
        if (headers.isEmpty()) return emptyList()
        val bodyIndex = firstHeader(headers, "body", "content", "text", "summary", "description", "notes", "answer")
        val titleIndex = firstHeader(headers, "title", "name", "topic", "heading", "question")
        val subjectIndex = firstHeader(headers, "subject", "course", "learningarea", "category")
        val gradeIndex = firstHeader(headers, "grade", "level", "year")
        val sourceIndex = firstHeader(headers, "source", "url", "reference", "origin")

        val output = mutableListOf<ReferenceEntry>()
        lines.drop(1).forEachIndexed { index, line ->
            val row = parseCsvLine(line)
            fun valueAt(i: Int): String = if (i >= 0 && i < row.size) row[i].trim() else ""
            val body = if (bodyIndex >= 0) valueAt(bodyIndex) else row.joinToString(" • ") { it.trim() }
            if (body.isBlank()) return@forEachIndexed
            output += ReferenceEntry(
                title = valueAt(titleIndex).ifBlank { "${humanName(name.substringBeforeLast('.'))} ${index + 1}" }.take(220),
                subject = valueAt(subjectIndex).ifBlank { inferSubject(path, inferGrade(path)) }.take(120),
                grade = normalizeGrade(valueAt(gradeIndex).ifBlank { inferGrade(path) }).take(60),
                source = valueAt(sourceIndex).ifBlank { path }.take(500),
                body = body.take(MAX_BODY_CHARS)
            )
        }
        return output
    }

    private fun referenceFromPlainText(name: String, path: String, rawText: String): ReferenceEntry? {
        val body = rawText.replace("\u0000", "").trim().take(MAX_BODY_CHARS)
        if (body.length < 20) return null
        val grade = inferGrade(path)
        val firstHeading = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#") }
            ?.trimStart('#', ' ')
            .orEmpty()
        return ReferenceEntry(
            title = firstHeading.ifBlank { humanName(name.substringBeforeLast('.')) }.take(220),
            subject = inferSubject(path, grade).take(120),
            grade = normalizeGrade(grade).take(60),
            source = path.take(500),
            body = body
        )
    }

    private fun extractGenericDatabase(file: File, displayName: String): List<ReferenceEntry> = runCatching {
        val output = mutableListOf<ReferenceEntry>()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0).orEmpty())
                }
            }

            tables.forEach { table ->
                if (output.size >= MAX_REFERENCES_PER_IMPORT) return@forEach
                val columns = tableColumns(db, table)
                if (columns.isEmpty()) return@forEach
                val bodyColumn = firstColumn(columns, "body", "content", "text", "summary", "description", "notes", "answer", "definition", "explanation")
                    .ifBlank { columns.firstOrNull { it !in setOf("id", "_id", "rowid") }.orEmpty() }
                if (bodyColumn.isBlank()) return@forEach
                val titleColumn = firstColumn(columns, "title", "name", "topic", "heading", "question")
                val subjectColumn = firstColumn(columns, "subject", "course", "learningarea", "category")
                val gradeColumn = firstColumn(columns, "grade", "level", "year")
                val sourceColumn = firstColumn(columns, "source", "url", "reference", "origin")

                val selected = linkedSetOf(bodyColumn).apply {
                    if (titleColumn.isNotBlank()) add(titleColumn)
                    if (subjectColumn.isNotBlank()) add(subjectColumn)
                    if (gradeColumn.isNotBlank()) add(gradeColumn)
                    if (sourceColumn.isNotBlank()) add(sourceColumn)
                }.toList()
                val sql = "SELECT ${selected.joinToString { quoteIdentifier(it) }} FROM ${quoteIdentifier(table)} LIMIT ${MAX_REFERENCES_PER_IMPORT - output.size}"
                db.rawQuery(sql, null).use { cursor ->
                    while (cursor.moveToNext() && output.size < MAX_REFERENCES_PER_IMPORT) {
                        fun read(column: String): String {
                            if (column.isBlank()) return ""
                            val index = selected.indexOf(column)
                            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index).orEmpty() else ""
                        }
                        val body = read(bodyColumn).trim()
                        if (body.length < 15) continue
                        output += ReferenceEntry(
                            title = read(titleColumn).ifBlank { "${humanName(table)} reference" }.take(220),
                            subject = read(subjectColumn).take(120),
                            grade = normalizeGrade(read(gradeColumn)).take(60),
                            source = read(sourceColumn).ifBlank { "$displayName#$table" }.take(500),
                            body = body.take(MAX_BODY_CHARS)
                        )
                    }
                }
            }
        }
        output
    }.getOrDefault(emptyList())

    private fun createCanonicalDatabase(
        context: Context,
        requestedName: String,
        rawEntries: List<ReferenceEntry>
    ): ImportResult {
        val entries = rawEntries
            .asSequence()
            .filter { it.body.isNotBlank() }
            .distinctBy { "${it.title}\u0000${it.subject}\u0000${it.grade}\u0000${it.body.take(500)}" }
            .take(MAX_REFERENCES_PER_IMPORT)
            .toList()
        if (entries.isEmpty()) return ImportResult(false, message = "No usable study references were found in that pack.")

        val destination = destinationFor(context, requestedName)
        return runCatching {
            SQLiteDatabase.openOrCreateDatabase(destination, null).use { db ->
                db.execSQL(
                    "CREATE TABLE reference_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "title TEXT NOT NULL," +
                        "subject TEXT NOT NULL DEFAULT ''," +
                        "grade TEXT NOT NULL DEFAULT ''," +
                        "source TEXT NOT NULL DEFAULT ''," +
                        "body TEXT NOT NULL)"
                )
                db.beginTransaction()
                try {
                    entries.forEach { entry ->
                        val values = ContentValues().apply {
                            put("title", entry.title.ifBlank { "StudyLock Reference" })
                            put("subject", entry.subject)
                            put("grade", entry.grade)
                            put("source", entry.source)
                            put("body", entry.body)
                        }
                        db.insertOrThrow("reference_entries", null, values)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_reference_subject ON reference_entries(subject)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_reference_grade ON reference_entries(grade)")
            }

            val count = validateDatabase(destination)
                ?: throw IllegalStateException("StudyLock could not verify the converted library database.")
            ImportResult(
                true,
                imported = 1,
                message = "Imported ${humanName(destination.nameWithoutExtension)} with $count reference entries."
            )
        }.getOrElse { error ->
            destination.delete()
            ImportResult(false, message = error.localizedMessage ?: "StudyLock could not convert that library pack.")
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
            val columns = tableColumns(db, "reference_entries").map { it.lowercase(Locale.ROOT) }.toSet()
            val hasText = "body" in columns || "content" in columns
            if (!hasText || !columns.containsAll(setOf("title", "subject", "grade", "source"))) return@use null
            db.rawQuery("SELECT COUNT(*) FROM reference_entries", null).use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getInt(0).takeIf { it > 0 }
            }
        }
    }.getOrNull()

    private fun tableColumns(db: SQLiteDatabase, table: String): List<String> = runCatching {
        db.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
            buildList {
                val index = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (index >= 0) add(cursor.getString(index).orEmpty())
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE (type='table' OR type='view') AND name=?",
            arrayOf(table)
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }

    private fun firstColumn(columns: List<String>, vararg candidates: String): String {
        val map = columns.associateBy { it.lowercase(Locale.ROOT).replace("_", "") }
        candidates.forEach { candidate ->
            map[candidate.lowercase(Locale.ROOT).replace("_", "")]?.let { return it }
        }
        return ""
    }

    private fun firstHeader(headers: List<String>, vararg candidates: String): Int {
        candidates.forEach { candidate ->
            val index = headers.indexOfFirst { it.replace("_", "") == candidate.replace("_", "") }
            if (index >= 0) return index
        }
        return -1
    }

    private fun optString(objectValue: JSONObject, vararg names: String): String {
        names.forEach { name ->
            val value = objectValue.opt(name)
            if (value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) {
                val text = value.toString().trim()
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char == '"') {
                if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
            } else if (char == ',' && !quoted) {
                result += current.toString()
                current.setLength(0)
            } else {
                current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun inferGrade(value: String): String {
        val match = Regex("(?i)(?:grade|gr)[\\s_\\-/]*(\\d{1,2})").find(value)
        return match?.groupValues?.getOrNull(1)?.let { "Grade $it" }.orEmpty()
    }

    private fun normalizeGrade(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        val digits = Regex("\\b(\\d{1,2})\\b").find(trimmed)?.groupValues?.getOrNull(1)
        return if (digits != null && trimmed.length <= 20) "Grade $digits" else trimmed
    }

    private fun inferSubject(path: String, grade: String): String {
        val pieces = path.replace('\\', '/').split('/').map { it.substringBeforeLast('.') }
        return pieces.asReversed()
            .map(::humanName)
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    !candidate.equals(grade, ignoreCase = true) &&
                    !Regex("(?i)^grade\\s*\\d+$").matches(candidate) &&
                    candidate.lowercase(Locale.ROOT) !in setOf("library", "libraries", "content", "data", "pack", "resources")
            }.orEmpty()
    }

    private fun inferSubjectFromKey(key: String): String {
        val clean = humanName(key)
        if (clean.lowercase(Locale.ROOT) in setOf("entries", "items", "documents", "resources", "topics", "lessons", "data", "content")) return ""
        if (inferGrade(clean).isNotBlank()) return ""
        return clean.takeIf { it.length in 3..80 }.orEmpty()
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun readEntryBytes(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IllegalArgumentException("A file inside this library pack is too large.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

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

    private fun looksLikeZip(file: File): Boolean =
        runCatching { file.inputStream().use { looksLikeZip(it.readNBytesCompat(4)) } }.getOrDefault(false)

    private fun looksLikeSqlite(file: File): Boolean =
        runCatching { file.inputStream().use { looksLikeSqlite(it.readNBytesCompat(16)) } }.getOrDefault(false)

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] in byteArrayOf(3, 5, 7) && bytes[3] in byteArrayOf(4, 6, 8)

    private fun looksLikeSqlite(bytes: ByteArray): Boolean =
        bytes.size >= 16 && bytes.copyOfRange(0, 15).toString(Charsets.US_ASCII) == "SQLite format 3"

    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(4096)
        val suspicious = sample.count { value ->
            val unsigned = value.toInt() and 0xff
            unsigned == 0 || (unsigned < 9) || (unsigned in 14..31)
        }
        return suspicious * 20 < sample.size
    }

    private fun InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == count) buffer else buffer.copyOf(offset)
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

    private fun quoteIdentifier(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

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
