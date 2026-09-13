package com.cyberpulse.studylock

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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

        // StudyLock intentionally ignores all client-supplied provider API keys.
        // AI requests are routed only through the managed Firebase backend or
        // Firebase AI Logic, so provider secrets are never stored in the APK,
        // WebView localStorage, or exposed to students.
        return requestManagedTutor(body)
    }

    private fun requestManagedTutor(body: JSONObject): Result {
        val backend = runCatching { requestStudyLockBackend(body) }
            .getOrElse { Result(false, message = friendlyBackendError(it)) }
        if (backend.success) return backend

        val direct = runCatching { requestFirebaseAi(body) }
            .getOrElse { Result(false, message = friendlyFirebaseAiError(it)) }
        if (direct.success) return direct

        return if (backend.message.contains("not deployed", ignoreCase = true)) {
            direct
        } else {
            backend
        }
    }

    private fun requestStudyLockBackend(body: JSONObject): Result {
        val app = firebaseApp
            ?: return Result(
                false,
                message = "StudyLock Firebase is not configured on this build."
            )
        val prompt = firebasePrompt(body)
        if (prompt.isBlank()) {
            return Result(false, message = "The tutor request did not contain a question.")
        }

        val functions = FirebaseFunctions.getInstance(app, FUNCTIONS_REGION)
        val response = Tasks.await(
            functions.getHttpsCallable(FUNCTION_NAME).call(
                mapOf(
                    "prompt" to prompt,
                    "system" to body.optString("system").take(4_000),
                    "maxTokens" to body.optInt("max_tokens", 500).coerceIn(64, 800)
                )
            ),
            55,
            TimeUnit.SECONDS
        )
        val result = response.data as? Map<*, *>
        val text = result?.get("text")?.toString().orEmpty().trim()
        return if (text.isNotBlank()) {
            Result(true, text)
        } else {
            Result(false, message = "StudyLock AI backend returned an empty answer.")
        }
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
        val response = runBlocking {
            model.generateContent(content { text(prompt) })
        }
        val text = response.text.orEmpty().trim()

        return if (text.isNotBlank()) {
            Result(true, text)
        } else {
            Result(false, message = "Firebase AI returned an empty answer.")
        }
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

    private fun friendlyBackendError(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return when {
            cause.contains("not_found", ignoreCase = true) ||
                cause.contains("not found", ignoreCase = true) ->
                "The StudyLock AI backend is not deployed yet."
            cause.contains("app check", ignoreCase = true) ||
                cause.contains("permission_denied", ignoreCase = true) ->
                "StudyLock AI could not verify this app with Firebase App Check."
            cause.contains("failed_precondition", ignoreCase = true) ||
                cause.contains("billing", ignoreCase = true) ||
                cause.contains("payment", ignoreCase = true) ->
                "StudyLock AI needs Vertex AI enabled and billing active on Firebase."
            cause.contains("resource_exhausted", ignoreCase = true) ||
                cause.contains("quota", ignoreCase = true) ->
                "StudyLock AI has reached its current quota. Try again later."
            cause.contains("deadline", ignoreCase = true) ||
                cause.contains("timeout", ignoreCase = true) ->
                "StudyLock AI took too long to answer. Try again."
            cause.isNotBlank() -> "StudyLock AI backend: ${cause.take(180)}"
            else -> "StudyLock AI backend could not answer right now."
        }
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

    private companion object {
        const val FIREBASE_MODEL = "gemini-3.7-flash"
        const val FUNCTIONS_REGION = "us-central1"
        const val FUNCTION_NAME = "studyLockTutor"
    }
}
