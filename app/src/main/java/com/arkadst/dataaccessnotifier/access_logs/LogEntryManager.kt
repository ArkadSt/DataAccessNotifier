package com.arkadst.dataaccessnotifier.access_logs

import android.content.Context
import com.arkadst.dataaccessnotifier.LogEntryProto
import com.arkadst.dataaccessnotifier.accessLogsDataStore
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class LogEntryJson(
    val logTime: String = "",
    val receiver: String = "",
    val infoSystemCode: String = "",
    val action: String = ""
)

object LogEntryManager {
    /**
     * Parse a JSON element into a LogEntryProto
     */
    fun parseLogEntry(logElement: JsonElement): LogEntryProto {
        val logEntryJson = Json.decodeFromJsonElement(serializer<LogEntryJson>(), logElement)

        val hash = generateContentHash(
            logEntryJson.logTime,
            logEntryJson.receiver,
            logEntryJson.infoSystemCode,
            logEntryJson.action
        )

        return LogEntryProto.newBuilder()
            .setTimestamp(logEntryJson.logTime)
            .setReceiver(logEntryJson.receiver)
            .setInfoSystem(logEntryJson.infoSystemCode)
            .setAction(logEntryJson.action)
            .setContentHash(hash)
            .build()
    }

    /**
     * Parse multiple JSON elements into LogEntryProto list
     */
    fun parseLogEntries(logElements: List<JsonElement>): List<LogEntryProto> {
        return logElements.map { parseLogEntry(it) }
    }

    /**
     * Generate a content hash for deduplication
     */
    fun generateContentHash(timestamp: String, receiver: String, infoSystem: String, action: String): String {
        return "$timestamp|$receiver|$infoSystem|$action"
    }

    /**
     * Format timestamp for display (includes year)
     */
    fun formatDisplayTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            date?.let { outputFormat.format(it) } ?: timestamp
        } catch (_: Exception) {
            timestamp
        }
    }

    /**
     * Create expanded notification content for an entry
     */
    fun formatExpandedNotification(entry: LogEntryProto): String {
        return "🕒 Time: ${formatDisplayTime(entry.timestamp)}\n🏢 Receiver: ${entry.receiver}\n💾 System: ${entry.infoSystem}\n📋 Action: ${entry.action}"
    }

    /**
     * Load log entries from persistent storage as a Flow for reactive updates
     */
    fun loadLogEntriesFlow(context: Context) = context.accessLogsDataStore.data.map { accessLogsProto ->
        accessLogsProto.entriesList.sortedByDescending { it.timestamp }
    }
}
