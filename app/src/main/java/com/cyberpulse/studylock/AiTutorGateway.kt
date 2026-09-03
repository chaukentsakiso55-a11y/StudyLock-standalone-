package com.cyberpulse.studylock

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.java.GenerativeModelFutures
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiTutorGateway(
    private val firebaseApp: FirebaseApp?
) {
    data class Result(
        val success: Boolean,
        val text: String = "",
        val message: String = ""
    )

    private val executor = Executors.newCachedThreadPool()

    fun request(payload: String, callback: (Result) -> Unit) {
        executor.execute {
            val result = runCatching { execute(payload) }
                .getOrElse { error ->
                    Result(
                        success = false,
                        message = friendlyFirebaseAiError(error)
                    )
                }
            callback(result)
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun execute(payload: String): Result {
        val request = JSONObject(payload)
        val body = request.optJSONObject("body")
            ?: return Result(false, message = "The tutor request was incomplete.")
        val apiKey = request.optString("apiKey").trim()

        if (apiKey.isBlank()) {
            return requestFirebaseAi(body)
        }

        val primary = if (apiKey.startsWith("AIza")) {
            requestGemini(apiKey, body)
        } else {
            requestOpenRouter(
                apiKey = apiKey,
                selectedModel = request.optString("model", "openrouter/free"),
                body = body
            )
        }
        if (primary.success) return primary

        val firebaseFallback = runCatching { requestFirebaseAi(body) }
            .getOrElse { Result(false, message = friendlyFirebaseAiError(it)) }
        return if (firebaseFallback.success) firebaseFallback else primary
    }

    private fun requestFirebaseAi(body: JSONObject): Result {
        val app = firebaseApp
            ?: return Result(
                false,
                message = "StudyLock Firebase AI is not configured on this build."
            )
        val prompt = firebasePrompt(body)
        if (prompt.isBlank()) {
            return Result(false, message = "The tutor request did not contain a question.")
        }

        val ai = FirebaseAI.getInstance(app, GenerativeBackend.googleAI())
        val systemText = body.optString("system").trim()
        val model = ai.generativeModel(
            modelName = FIREBASE_MODEL,
            systemInstruction = systemText.takeIf(String::isNotBlank)?.let { instruction ->
                content { text(instruction) }
            }
        )
        val futures = GenerativeModelFutures.from(model)
        val response = futures
            .generateContent(content { text(prompt) })
            .get(40, TimeUnit.SECONDS)
        val text = response.text.orEmpty().trim()

        return if (text.isNotBlank()) {
            Result(true, text)
        } else {
            Result(false, message = "Firebase AI returned an empty answer.")
        }
    }

    private fun requestOpenRouter(
        apiKey: String,
        selectedModel: String,
        body: JSONObject
    ): Result {
        val messages = openAiMessages(body)
        val model = selectedModel.takeIf {
            it == "openrouter/free" || it == "openrouter/auto"
        } ?: "openrouter/free"
        val requestBody = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", body.optInt("max_tokens", 500).coerceIn(32, 2_000))
            .put("temperature", 0.4)
            .put("stream", false)

        val response = postJson(
            url = "https://openrouter.ai/api/v1/chat/completions",
            body = requestBody,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "HTTP-Referer" to "https://studylock.app",
                "X-OpenRouter-Title" to "StudyLock"
            )
        )
        if (response.code !in 200..299) {
            return providerFailure("OpenRouter", response.code, response.body)
        }
        return parseOpenAiResponse("OpenRouter", response.body)
    }

    private fun requestGemini(apiKey: String, body: JSONObject): Result {
        val contents = geminiContents(body)
        if (contents.length() == 0) {
            return Result(false, message = "The tutor request did not contain a question.")
        }

        val requestBody = JSONObject()
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.4)
                    .put("maxOutputTokens", body.optInt("max_tokens", 500).coerceIn(32, 2_000))
            )
        body.optString("system").takeIf(String::isNotBlank)?.let { system ->
            requestBody.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", system))
                )
            )
        }

        val response = postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-3.8-flash:generateContent",
            body = requestBody,
            headers = mapOf("x-goog-api-key" to apiKey)
        )
        if (response.code !in 200..299) {
            return providerFailure("Gemini", response.code, response.body)
        }
        val parts = JSONObject(response.body)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (index in 0 until parts.length()) {
                    val part = parts.optJSONObject(index)?.optString("text").orEmpty()
                    if (part.isNotBlank()) append(part)
                }
            }
        }.trim()
        return if (text.isNotBlank()) Result(true, text)
        else Result(false, message = "Gemini returned an empty answer.")
    }

    private fun firebasePrompt(body: JSONObject): String {
        val messages = body.optJSONArray("messages") ?: JSONArray()
        if (messages.length() == 0) return body.optString("prompt").trim()

        val start = (messages.length() - 16).coerceAtLeast(0)
        return buildString {
            for (index in start until messages.length()) {
                val message = messages.optJSONObject(index) ?: continue
                val text = message.opt("content")?.toString().orEmpty().trim()
                if (text.isBlank()) continue
                val role = when (message.optString("role")) {
                    "assistant", "model" -> "Tutor"
                    else -> "Student"
                }
                append(role).append(": ").append(text).append('\n')
            }
            append("Tutor:")
        }.trim()
    }

    private fun openAiMessages(body: JSONObject): JSONArray = JSONArray().apply {
        body.optString("system").takeIf(String::isNotBlank)?.let { system ->
            put(JSONObject().put("role", "system").put("content", system))
        }
        val sourceMessages = body.optJSONArray("messages") ?: JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            val content = message.opt("content")?.toString().orEmpty().trim()
            if (content.isBlank()) continue
            val role = when (message.optString("role")) {
                "assistant", "model" -> "assistant"
                else -> "user"
            }
            put(JSONObject().put("role", role).put("content", content))
        }
    }

    private fun geminiContents(body: JSONObject): JSONArray {
        data class Turn(val role: String, var text: String)

        val turns = mutableListOf<Turn>()
        val sourceMessages = body.optJSONArray("messages") ?: JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            val content = message.opt("content")?.toString().orEmpty().trim()
            if (content.isBlank()) continue
            val role = if (message.optString("role") == "assistant") "model" else "user"

            if (turns.isEmpty() && role == "model") continue
            val previous = turns.lastOrNull()
            if (previous != null && previous.role == role) {
                previous.text = previous.text + "\n\n" + content
            } else {
                turns += Turn(role, content)
            }
        }

        return JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject()
                        .put("role", turn.role)
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", turn.text))
                        )
                )
            }
        }
    }

    private fun parseOpenAiResponse(provider: String, body: String): Result {
        val text = runCatching {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
        }.getOrDefault("")
        return if (text.isNotBlank()) Result(true, text)
        else Result(false, message = "$provider returned an empty answer.")
    }

    private fun providerFailure(provider: String, code: Int, body: String): Result {
        val detail = runCatching {
            val json = JSONObject(body)
            val error = json.opt("error")
            when (error) {
                is JSONObject -> error.optString("message")
                is String -> error
                else -> ""
            }
        }.getOrDefault("")
        val friendly = when (code) {
            400 -> detail.take(180).ifBlank { "The selected model or request was rejected." }
            401, 403 -> "The API key was rejected or lacks permission."
            429 -> "The provider's usage limit was reached."
            else -> detail.take(180).ifBlank { "Request failed with status $code." }
        }
        return Result(false, message = "$provider: $friendly")
    }

    private fun friendlyFirebaseAiError(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return when {
            cause.contains("app check", ignoreCase = true) ->
                "StudyLock AI could not verify this app with Firebase App Check."
            cause.contains("not found", ignoreCase = true) ||
                cause.contains("not enabled", ignoreCase = true) ->
                "Firebase AI Logic is not enabled for the StudyLock project."
            cause.contains("quota", ignoreCase = true) ||
                cause.contains("resource exhausted", ignoreCase = true) ->
                "StudyLock AI has reached its current Firebase quota. Try again later."
            cause.isNotBlank() -> "StudyLock AI: ${cause.take(180)}"
            else -> "StudyLock AI could not answer right now. Check your connection and try again."
        }
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "StudyLock-Android/1.0.4")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(code, responseBody)
        } catch (error: IOException) {
            throw IOException("Check your internet connection and try again.", error)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        const val FIREBASE_MODEL = "gemini-3.7-flash"
    }
}
