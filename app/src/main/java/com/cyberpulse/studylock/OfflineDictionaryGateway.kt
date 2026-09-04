package com.cyberpulse.studylock

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class OfflineDictionaryGateway(context: Context) {
    data class Result(
        val success: Boolean,
        val payload: String = "",
        val message: String = ""
    )

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var database: SQLiteDatabase? = null

    fun lookup(rawWord: String, callback: (Result) -> Unit) {
        executor.execute {
            val result = runCatching { lookupInternal(rawWord) }
                .getOrElse { error ->
                    Result(
                        success = false,
                        message = error.localizedMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: "The offline dictionary could not be opened."
                    )
                }
            callback(result)
        }
    }

    fun close() {
        executor.shutdownNow()
        runCatching { database?.close() }
        database = null
    }

    private fun lookupInternal(rawWord: String): Result {
        val word = normalizeWord(rawWord)
        if (word.isBlank()) {
            return Result(false, message = "Type an English word to look up.")
        }

        val db = ensureDatabase()
        val direct = findEntries(db, word)
        if (direct.isNotEmpty()) {
            return Result(true, buildPayload(word, word, direct, true).toString())
        }

        val aliases = findAliases(db, word)
        for (lemma in aliases) {
            val aliased = findEntries(db, lemma)
            if (aliased.isNotEmpty()) {
                return Result(true, buildPayload(word, lemma, aliased, true).toString())
            }
        }

        for (candidate in morphologyCandidates(word)) {
            val stemmed = findEntries(db, candidate)
            if (stemmed.isNotEmpty()) {
                return Result(true, buildPayload(word, candidate, stemmed, true).toString())
            }
        }

        if (isKnownWord(db, word)) {
            val generic = listOf(
                DictionaryEntry(
                    pos = "word",
                    definition = "This spelling is present in StudyLock's bundled offline English lexicon. A detailed lexical definition is not available in the compact WordNet entry set on this device.",
                    example = ""
                )
            )
            return Result(true, buildPayload(word, word, generic, false).toString())
        }

        return Result(
            success = true,
            payload = JSONObject()
                .put("found", false)
                .put("word", word)
                .put("offline", true)
                .toString()
        )
    }

    private fun ensureDatabase(): SQLiteDatabase {
        database?.takeIf { it.isOpen }?.let { return it }

        val dictionaryDir = File(appContext.noBackupFilesDir, "studylock_dictionary")
        if (!dictionaryDir.exists()) dictionaryDir.mkdirs()
        val databaseFile = File(dictionaryDir, DATABASE_FILE_NAME)
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val installedVersion = preferences.getInt(KEY_ASSET_VERSION, 0)

        if (!databaseFile.exists() || installedVersion != BuildConfig.DICTIONARY_ASSET_VERSION) {
            val temporary = File(dictionaryDir, "$DATABASE_FILE_NAME.tmp")
            if (temporary.exists()) temporary.delete()
            runCatching {
                appContext.assets.open(ASSET_NAME).use { input ->
                    temporary.outputStream().buffered().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
            }.getOrElse { error ->
                temporary.delete()
                throw IllegalStateException(
                    "This StudyLock build does not contain the full offline dictionary database.",
                    error
                )
            }

            if (databaseFile.exists()) databaseFile.delete()
            if (!temporary.renameTo(databaseFile)) {
                temporary.copyTo(databaseFile, overwrite = true)
                temporary.delete()
            }
            preferences.edit().putInt(KEY_ASSET_VERSION, BuildConfig.DICTIONARY_ASSET_VERSION).apply()
        }

        return SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).also { opened -> database = opened }
    }

    private fun findEntries(db: SQLiteDatabase, word: String): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        db.rawQuery(
            "SELECT pos, definition, example FROM entries WHERE word = ? LIMIT 16",
            arrayOf(word)
        ).use { cursor ->
            val posIndex = cursor.getColumnIndexOrThrow("pos")
            val definitionIndex = cursor.getColumnIndexOrThrow("definition")
            val exampleIndex = cursor.getColumnIndexOrThrow("example")
            while (cursor.moveToNext()) {
                val definition = cursor.getString(definitionIndex).orEmpty().trim()
                if (definition.isBlank()) continue
                entries += DictionaryEntry(
                    pos = cursor.getString(posIndex).orEmpty().ifBlank { "word" },
                    definition = definition,
                    example = cursor.getString(exampleIndex).orEmpty().trim()
                )
            }
        }
        return entries.distinctBy { Triple(it.pos, it.definition, it.example) }
    }

    private fun findAliases(db: SQLiteDatabase, word: String): List<String> {
        val aliases = mutableListOf<String>()
        db.rawQuery(
            "SELECT lemma FROM aliases WHERE word = ? LIMIT 8",
            arrayOf(word)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.trim()?.takeIf { it.isNotBlank() }?.let(aliases::add)
            }
        }
        return aliases.distinct()
    }

    private fun isKnownWord(db: SQLiteDatabase, word: String): Boolean =
        db.rawQuery("SELECT 1 FROM lexicon WHERE word = ? LIMIT 1", arrayOf(word)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun buildPayload(
        requestedWord: String,
        matchedWord: String,
        entries: List<DictionaryEntry>,
        definitionAvailable: Boolean
    ): JSONObject {
        val grouped = linkedMapOf<String, MutableList<DictionaryEntry>>()
        entries.forEach { entry -> grouped.getOrPut(entry.pos) { mutableListOf() }.add(entry) }

        val meanings = JSONArray()
        grouped.entries.take(4).forEach { (pos, values) ->
            val definitions = JSONArray()
            values.take(4).forEach { entry ->
                definitions.put(
                    JSONObject()
                        .put("definition", entry.definition)
                        .apply {
                            if (entry.example.isNotBlank()) put("example", entry.example)
                        }
                )
            }
            meanings.put(
                JSONObject()
                    .put("partOfSpeech", friendlyPartOfSpeech(pos))
                    .put("definitions", definitions)
            )
        }

        val entry = JSONObject()
            .put("word", requestedWord)
            .put("phonetic", "")
            .put("phonetics", JSONArray())
            .put("meanings", meanings)

        return JSONObject()
            .put("found", true)
            .put("offline", true)
            .put("definitionAvailable", definitionAvailable)
            .put("word", requestedWord)
            .put("matchedWord", matchedWord)
            .put("source", "StudyLock offline dictionary")
            .put("entry", entry)
    }

    private fun friendlyPartOfSpeech(pos: String): String = when (pos.lowercase(Locale.ROOT)) {
        "n", "noun" -> "noun"
        "v", "verb" -> "verb"
        "a", "s", "adj", "adjective" -> "adjective"
        "r", "adv", "adverb" -> "adverb"
        else -> pos.ifBlank { "word" }
    }

    private fun morphologyCandidates(word: String): List<String> {
        val candidates = linkedSetOf<String>()
        if (word.endsWith("'s") && word.length > 3) candidates += word.dropLast(2)
        if (word.endsWith("ies") && word.length > 4) candidates += word.dropLast(3) + "y"
        if (word.endsWith("ves") && word.length > 4) {
            candidates += word.dropLast(3) + "f"
            candidates += word.dropLast(3) + "fe"
        }
        if (word.endsWith("ing") && word.length > 5) {
            val base = word.dropLast(3)
            candidates += base
            candidates += base + "e"
            if (base.length > 2 && base.last() == base[base.lastIndex - 1]) candidates += base.dropLast(1)
        }
        if (word.endsWith("ed") && word.length > 4) {
            val base = word.dropLast(2)
            candidates += base
            candidates += base + "e"
            if (base.length > 2 && base.last() == base[base.lastIndex - 1]) candidates += base.dropLast(1)
        }
        if (word.endsWith("es") && word.length > 3) candidates += word.dropLast(2)
        if (word.endsWith("s") && word.length > 3) candidates += word.dropLast(1)
        if (word.endsWith("er") && word.length > 4) candidates += word.dropLast(2)
        if (word.endsWith("est") && word.length > 5) candidates += word.dropLast(3)
        return candidates.filter { it.isNotBlank() && it != word }
    }

    private fun normalizeWord(rawWord: String): String = rawWord
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .take(MAX_WORD_LENGTH)

    private data class DictionaryEntry(
        val pos: String,
        val definition: String,
        val example: String
    )

    private companion object {
        const val ASSET_NAME = "studylock_dictionary.db"
        const val DATABASE_FILE_NAME = "studylock_dictionary.db"
        const val PREFERENCES_NAME = "studylock_offline_dictionary"
        const val KEY_ASSET_VERSION = "asset_version"
        const val MAX_WORD_LENGTH = 96
    }
}
