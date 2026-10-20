package com.homesweetlove.instaytgrabber.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticEntry(
    val timeMillis: Long,
    val code: String,
    val stage: String,
    val message: String
)

/** Private, capped error journal. No request URLs, cookies, or media history stored. */
object DiagnosticLog {
    private const val MAX_ENTRIES = 120
    private const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private lateinit var journal: File

    var entries by mutableStateOf<List<DiagnosticEntry>>(emptyList())
        private set

    @Synchronized
    fun attach(context: Context) {
        journal = File(context.applicationContext.filesDir, "diagnostic-errors.json")
        entries = runCatching {
            val data = JSONArray(journal.takeIf(File::exists)?.readText() ?: "[]")
            (0 until data.length()).mapNotNull { index ->
                val obj = data.optJSONObject(index) ?: return@mapNotNull null
                DiagnosticEntry(
                    timeMillis = obj.optLong("time"),
                    code = obj.optString("code"),
                    stage = obj.optString("stage"),
                    message = obj.optString("message")
                )
            }
        }.getOrDefault(emptyList()).filter { System.currentTimeMillis() - it.timeMillis in 0..MAX_AGE_MS }
            .takeLast(MAX_ENTRIES)
    }

    @Synchronized
    fun failure(code: String, stage: String, throwable: Throwable?) {
        record(code, stage, throwable?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" } ?: "알 수 없는 오류")
    }

    @Synchronized
    fun record(code: String, stage: String, description: String) {
        if (!::journal.isInitialized) return
        val item = DiagnosticEntry(
            timeMillis = System.currentTimeMillis(),
            code = code.take(40),
            stage = stage.take(60),
            message = sanitize(description)
        )
        entries = (entries.filter {
            item.timeMillis - it.timeMillis in 0..MAX_AGE_MS
        } + item).takeLast(MAX_ENTRIES)
        save()
    }

    @Synchronized
    fun clear() {
        entries = emptyList()
        if (::journal.isInitialized) runCatching { journal.delete() }
    }

    @Synchronized
    fun report(): String = buildString {
        appendLine("InstaYtGrabber diagnostic errors")
        appendLine("Entries: ${entries.size} (max 120, last 7 days)")
        appendLine("Sensitive URLs and session values redacted.")
        entries.asReversed().forEach { item ->
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(item.timeMillis))
            appendLine("[$time] ${item.code} | ${item.stage} | ${item.message}")
        }
    }

    private fun save() {
        if (!::journal.isInitialized) return
        runCatching {
            val arr = JSONArray()
            entries.forEach { item ->
                arr.put(JSONObject().apply {
                    put("time", item.timeMillis)
                    put("code", item.code)
                    put("stage", item.stage)
                    put("message", item.message)
                })
            }
            val temp = File(journal.parentFile, journal.name + ".tmp")
            temp.writeText(arr.toString())
            if (!temp.renameTo(journal)) {
                journal.writeText(arr.toString())
                temp.delete()
            }
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("""(?i)\b(sessionid|csrftoken|ds_user_id|ig_did|authorization|password|token|cookie|mid|rur)\s*[:=]\s*[^\s,;&]+"""), "$1=<redacted>")
            .replace(Regex("""(?i)https?://[^\s<>"']+"""), "<url-redacted>")
            .replace(Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/\-=]+"""), "Bearer <redacted>")
            .replace(Regex("""(?i)@[A-Za-z0-9._]{1,30}"""), "@<account>")
            .take(600)
            .ifBlank { "(메시지 없음)" }
    }
}
