package com.cyberpulse.studylock

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class OfflineTutorReferenceGateway(context: Context) {
    data class Result(
        val success: Boolean,
        val text: String = "",
        val message: String = ""
    )

    private data class Reference(
        val title: String,
        val subject: String,
        val grade: String,
        val source: String,
        val content: String
    )

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()

    fun answer(question: String, callback: (Result) -> Unit) {
        executor.execute {
            val result = runCatching { answerNow(question) }
                .getOrElse { error ->
                    Result(
                        success = false,
                        message = error.localizedMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: "StudyLock could not search the Offline Tutor Library."
                    )
                }
            callback(result)
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun answerNow(question: String): Result {
        val cleanedQuestion = question.trim()
        if (cleanedQuestion.isBlank()) {
            return Result(false, message = "Type a question for the Offline Tutor Library.")
        }

        OfflineTutorLibraryStore.ensureStarterInstalled(appContext)
        val baseFile = OfflineTutorLibraryStore.libraryFile(appContext)
        val customFiles = CustomReferenceLibraryStore.libraryFiles(appContext)
        val databaseFiles = buildList {
            if (baseFile.isFile && baseFile.length() > 0L) add(baseFile)
            addAll(customFiles)
        }.distinctBy { it.absolutePath }

        if (databaseFiles.isEmpty()) {
            return Result(
                false,
                message = "No offline Tutor libraries are installed. Open Settings and check Offline Study Libraries."
            )
        }

        val tokens = searchTokens(cleanedQuestion)
        if (tokens.isEmpty()) {
            return Result(false, message = "Try a more specific study question.")
        }

        val references = databaseFiles
            .flatMap { file -> runCatching { queryFile(file, tokens) }.getOrDefault(emptyList()) }
            .distinctBy { reference ->
                listOf(
                    reference.title.trim().lowercase(Locale.ROOT),
                    reference.subject.trim().lowercase(Locale.ROOT),
                    reference.grade.trim().lowercase(Locale.ROOT),
                    reference.content.take(120).trim().lowercase(Locale.ROOT)
                ).joinToString("|")
            }
            .sortedByDescending { reference -> scoreReference(reference, tokens) }
            .take(8)

        if (references.isEmpty()) {
            return Result(
                false,
                message = "The installed Offline Tutor libraries do not contain a close enough reference for that question."
            )
        }

        val answer = buildString {
            append("Based on the Offline Tutor Library:\n\n")
            references.take(4).forEachIndexed { index, reference ->
                val excerpt = bestExcerpt(reference.content, tokens)
                if (excerpt.isBlank()) return@forEachIndexed
                if (index > 0) append("\n\n")
                append(excerpt)
            }

            append("\n\nReferences:\n")
            references.take(5).forEachIndexed { index, reference ->
                append(index + 1)
                    .append(". ")
                    .append(reference.title.ifBlank { "StudyLock Reference" })
                if (reference.subject.isNotBlank()) append(" — ").append(reference.subject)
                if (reference.grade.isNotBlank()) append(" (").append(reference.grade).append(")")
                if (reference.source.isNotBlank()) append(" — ").append(reference.source)
                append('\n')
            }
            append("\nOffline answer • generated only from installed StudyLock reference libraries")
        }.trim()

        return Result(success = answer.isNotBlank(), text = answer)
    }

    private fun queryFile(file: File, tokens: List<String>): List<Reference> {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return database.use { db ->
            val fromFts = if (tableExists(db, "reference_fts")) {
                runCatching { queryFts(db, tokens) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            if (fromFts.isNotEmpty()) fromFts else queryFallback(db, tokens)
        }
    }

    private fun queryFts(db: SQLiteDatabase, tokens: List<String>): List<Reference> {
        val matchQuery = tokens.joinToString(" OR ") { "$it*" }
        val bodyColumn = if (columnExists(db, "reference_fts", "body")) "body" else "content"
        return db.rawQuery(
            "SELECT title, subject, grade, source, $bodyColumn FROM reference_fts " +
                "WHERE reference_fts MATCH ? LIMIT 8",
            arrayOf(matchQuery)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Reference(
                            title = cursor.getString(0).orEmpty(),
                            subject = cursor.getString(1).orEmpty(),
                            grade = cursor.getString(2).orEmpty(),
                            source = cursor.getString(3).orEmpty(),
                            content = cursor.getString(4).orEmpty()
                        )
                    )
                }
            }
        }
    }

    private fun queryFallback(db: SQLiteDatabase, tokens: List<String>): List<Reference> {
        if (!tableExists(db, "reference_entries")) return emptyList()
        val bodyColumn = when {
            columnExists(db, "reference_entries", "body") -> "body"
            columnExists(db, "reference_entries", "content") -> "content"
            else -> return emptyList()
        }
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        tokens.take(5).forEach { token ->
            clauses += "(lower(title) LIKE ? OR lower(subject) LIKE ? OR lower($bodyColumn) LIKE ?)"
            val like = "%$token%"
            args += like
            args += like
            args += like
        }
        if (clauses.isEmpty()) return emptyList()

        return db.rawQuery(
            "SELECT title, subject, grade, source, $bodyColumn FROM reference_entries WHERE " +
                clauses.joinToString(" OR ") +
                " LIMIT 8",
            args.toTypedArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Reference(
                            title = cursor.getString(0).orEmpty(),
                            subject = cursor.getString(1).orEmpty(),
                            grade = cursor.getString(2).orEmpty(),
                            source = cursor.getString(3).orEmpty(),
                            content = cursor.getString(4).orEmpty()
                        )
                    )
                }
            }
        }
    }

    private fun scoreReference(reference: Reference, tokens: List<String>): Int {
        val title = reference.title.lowercase(Locale.ROOT)
        val subject = reference.subject.lowercase(Locale.ROOT)
        val body = reference.content.lowercase(Locale.ROOT)
        return tokens.sumOf { token ->
            (if (title.contains(token)) 4 else 0) +
                (if (subject.contains(token)) 3 else 0) +
                (if (body.contains(token)) 1 else 0)
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE (type='table' OR type='view') AND name=?",
            arrayOf(table)
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean =
        runCatching {
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        return@use true
                    }
                }
                false
            }
        }.getOrDefault(false)

    private fun searchTokens(question: String): List<String> {
        val stopWords = setOf(
            "about", "after", "again", "also", "and", "are", "because", "been", "before",
            "can", "could", "does", "explain", "for", "from", "give", "have", "how", "into",
            "more", "should", "that", "the", "their", "then", "there", "these", "they", "this",
            "what", "when", "where", "which", "why", "with", "would", "your"
        )
        return question
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
            .take(8)
            .toList()
    }

    private fun bestExcerpt(content: String, tokens: List<String>): String {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ""
        val sentences = normalized.split(Regex("(?<=[.!?])\\s+"))
        if (sentences.size <= 2) return normalized.take(950)

        val ranked = sentences.mapIndexed { index, sentence ->
            val lower = sentence.lowercase(Locale.ROOT)
            val score = tokens.sumOf { token -> if (lower.contains(token)) 1 else 0 }
            Triple(index, score, sentence)
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.second }.thenBy { it.first })
            .take(3)
            .sortedBy { it.first }
            .map { it.third.trim() }

        val chosen = if (ranked.isEmpty()) sentences.take(2) else ranked
        return chosen.joinToString(" ").take(950)
    }
}
