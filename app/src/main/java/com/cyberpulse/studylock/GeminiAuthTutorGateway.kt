package com.cyberpulse.studylock

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GeminiAuthTutorGateway {
    private val executor = Executors.newCachedThreadPool()

    fun request(payload: String, callback: (AiTutorGateway.Result) -> Unit) {
        executor.execute {
            val result = runCatching { execute(payload) }
                .getOrElse { error ->
                    AiTutorGateway.Result(
                        success = false,
                        message = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Gemini could not answer right now."
                    )
                }
            callback(result)
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun execute(payload: String): AiTutorGateway.Result {
        val request = JSONObject(payload)
        val apiKey = request.optString("apiKey").trim()
        if (!apiKey.startsWith("AQ.")) {
            return AiTutorGateway.Result(false, message = "A Gemini authorization key is required.")
        }
        val body = request.optJSONObject("body")
            ?: return AiData.error("The tutor request was incomplete.")
        val contents = geminiContents(body)
        if (contents.length() == 0) {
            return AiData.error("The tutor request did not contain a question.")
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
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
            body = requestBody,
            headers = mapOf("x-goog-api-key" to apiKey)
        )
        if (response.code !in 200..299) {
            return providerFailure(response.code, response.body)
        }

        val parts = runCatching {
            JSONObject(response.body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
        }.getOrNull()

        val text = buildString {
            if (parts != null) {
                for (index in 0 until parts.length()) {
                    val value = parts.optJSONObject(index)?.optString("text").orEmpty()
                    if (value.isNotBlank()) append(value)
                }
            }
        }.trim()

        return if (text.isNotBlank()) {
            AiTutorGateway.Result(true, text)
        } else {
            AiData.error("Gemini returned an empty answer.")
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
                previous.text += "\n\n$content"
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

    private fun providerFailure(code: Int, body: String): AiTutorGateway.Result {
        val detail = runCatching {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message").orEmpty()
        }.getOrDefault("")

        val friendly = when (code) {
            400 -> detail.take(180).ifBlank { "Gemini rejected the request." }
            401, 403 -> "The Gemini authorization key was rejected or lacks permission."
            429 -> "Gemini quota is currently exhausted. Try again later."
            else -> detail.take(180).ifBlank { "Gemini request failed with status $code." }
        }
        return AiData.error(friendly)
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String>
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "StudyLock-Android/1.0.14")
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

    private object AiData {
        fun error(message: String) = AiTutorGateway.Result(false, message = message)
    }
}
