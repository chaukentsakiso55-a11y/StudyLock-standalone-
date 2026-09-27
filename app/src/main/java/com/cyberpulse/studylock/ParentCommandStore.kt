package com.cyberpulse.studylock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ParentCommandStore {
    private const val PREFS = "studylock_parent_commands"
    private const val KEY_QUEUE = "queue"
    private const val MAX_QUEUE = 50

    @Synchronized
    fun enqueue(context: Context, command: JSONObject) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val queue = runCatching { JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val trimmed = JSONArray()
        val start = (queue.length() - (MAX_QUEUE - 1)).coerceAtLeast(0)
        for (index in start until queue.length()) trimmed.put(queue.opt(index))
        trimmed.put(command)
        prefs.edit().putString(KEY_QUEUE, trimmed.toString()).apply()
    }

    @Synchronized
    fun drain(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val queue = runCatching { JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        prefs.edit().putString(KEY_QUEUE, "[]").apply()
        return queue
    }
}
