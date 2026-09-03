package com.cyberpulse.studylock

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiTutorGateway {
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
                        message = error.message
                            ?: "The AI provider could not be reached."
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
        val apiKey = request.optString("apiKey").trim()
        if (apiKey.isBlank()) {
            return Result(
                success = false,
                message = "Add a Gemini or OpenRouter API key in Settings."
            )
        }
        val body = request.optJSONObject("body")
            ?: return Result(false, message = "The tutor request was incomplete.")
        return if (apiKey.startsWith("AIza")) {
            requestGemini(apiKey, body)
        } else {
            requestOpenRouter(
                apiKey = apiKey,
                selectedModel = request.optString("model", "openrouter/free"),
                body = body
            )
        }
    }

    private fun requestOpenRouter(
        apiKey: String,
        selectedModel: String,
        body: JSONObject
    ): Result {
        val messages = JSONArray()
        body.optString("system").takeIf(String::isNotBlank)?.let { system ->
            messages.put(JSONObject().put("role", "system").put("content", system))
        }
        val sourceMessages = body.optJSONArray("messages") ?: JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            messages.put(
                JSONObject()
                    .put("role", message.optString("role", "user"))
                    .put("content", message.opt("content") ?: "")
            )
        }

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
        val json = JSONObject(response.body)
        val text = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        return if (text.isNotBlank()) Result(true, text)
        else Result(false, message = "OpenRouter returned an empty answer.")
    }

    private fun requestGemini(apiKey: String, body: JSONObject): Result {
        val contents = JSONArray()
        val sourceMessages = body.optJSONArray("messages") ?: JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            val content = message.opt("content")?.toString().orEmpty()
            if (content.isBlank()) continue
            contents.put(
                JSONObject()
                    .put(
                        "role",
                        if (message.optString("role") == "assistant") "model" else "user"
                    )
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", content))
                    )
            )
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
            400 -> "The selected model or request was rejected."
            401, 403 -> "The API key was rejected or lacks permission."
            429 -> "The provider's usage limit was reached. Try again later."
            else -> detail.take(180).ifBlank { "Request failed with status $code." }
        }
        return Result(false, message = "$provider: $friendly")
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
            setRequestProperty("User-Agent", "StudyLock-Android/1.0.1")
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
}
