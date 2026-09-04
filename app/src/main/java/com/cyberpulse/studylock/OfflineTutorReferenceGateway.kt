package com.cyberpulse.studylock

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

        val file = OfflineTutorLibraryStore.libraryFile(appContext)
        if (!OfflineTutorLibraryStore.isInstalled(appContext)) {
            return Result(
                false,
                message = "You're offline and the Offline Tutor Library is not installed. Open Settings and tap Download Library."
            )
        }

        val tokens = searchTokens(cleanedQuestion)
        if (tokens.isEmpty()) {
            return Result(false, message = "Try a more specific study question.")
        }

        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        val references = database.use { db ->
            if (tableExists(db, "reference_fts")) {
                queryFts(db, tokens)
            } else {
                queryFallback(db, tokens)
            }
        }

        if (references.isEmpty()) {
            return Result(
                false,
                message = "The installed Offline Tutor Library does not contain a close enough reference for that question."
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
            append("\nOffline answer • generated only from the installed StudyLock reference library")
        }.trim()

        return Result(success = answer.isNotBlank(), text = answer)
    }

    private fun queryFts(db: SQLiteDatabase, tokens: List<String>): List<Reference> {
        val matchQuery = tokens.joinToString(" OR ") { "$it*" }
        return db.rawQuery(
            "SELECT title, subject, grade, source, content FROM reference_fts " +
                "WHERE reference_fts MATCH ? LIMIT 6",
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
        val token = tokens.first()
        val like = "%$token%"
        return db.rawQuery(
            "SELECT title, subject, grade, source, content FROM reference_entries " +
                "WHERE lower(title) LIKE ? OR lower(subject) LIKE ? OR lower(content) LIKE ? LIMIT 6",
            arrayOf(like, like, like)
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

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE (type='table' OR type='view') AND name=?",
            arrayOf(table)
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }

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
