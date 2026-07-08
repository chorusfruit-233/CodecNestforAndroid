package com.fruit.ffmpeggui.data

import android.content.Context
import com.fruit.ffmpeggui.core.JobHistoryItem
import com.fruit.ffmpeggui.core.JobStatus
import org.json.JSONArray
import org.json.JSONObject

interface JobHistoryRepository {
    fun load(): List<JobHistoryItem>
    fun add(item: JobHistoryItem)
    fun clear()
}

class SharedPreferencesJobHistoryRepository(context: Context) : JobHistoryRepository {
    private val preferences = context.getSharedPreferences("job_history", Context.MODE_PRIVATE)

    override fun load(): List<JobHistoryItem> {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toHistoryItem())
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun add(item: JobHistoryItem) {
        val items = (listOf(item) + load()).take(MAX_ITEMS)
        preferences.edit().putString(KEY_ITEMS, JSONArray(items.map { it.toJson() }).toString()).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_ITEMS).apply()
    }

    private fun JSONObject.toHistoryItem(): JobHistoryItem = JobHistoryItem(
        id = optString("id"),
        title = optString("title"),
        commandPreview = optString("commandPreview"),
        status = runCatching { JobStatus.valueOf(optString("status")) }.getOrDefault(JobStatus.Failed),
        startedAtMillis = optLong("startedAtMillis"),
        finishedAtMillis = optLong("finishedAtMillis"),
        outputName = optString("outputName").ifBlank { null },
        logTail = optString("logTail")
    )

    private fun JobHistoryItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("commandPreview", commandPreview)
        .put("status", status.name)
        .put("startedAtMillis", startedAtMillis)
        .put("finishedAtMillis", finishedAtMillis)
        .put("outputName", outputName)
        .put("logTail", logTail)

    private companion object {
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 30
    }
}
