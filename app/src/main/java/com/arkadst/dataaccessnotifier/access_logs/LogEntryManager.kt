package com.arkadst.dataaccessnotifier.access_logs

import android.content.Context
import android.util.Log
import com.arkadst.dataaccessnotifier.LogEntryProto
import com.arkadst.dataaccessnotifier.accessLogsDataStore
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class LogEntryJson(
    val logTime: String? = null,
    val receiver: String? = null,
    val infoSystemCode: String? = null,
    val action: String? = null
)

object LogEntryManager {
    /**
     * Parse a JSON element into a LogEntryProto
     */
    fun parseLogEntry(logElement: JsonElement): LogEntryProto {
        val logEntryJson = Json.decodeFromJsonElement(serializer<LogEntryJson>(), logElement)

        val builder = LogEntryProto.newBuilder()

        // Only set fields if they're not null
        logEntryJson.logTime?.let { builder.setTimestamp(it) }
        logEntryJson.receiver?.let { builder.setReceiver(it) }
        logEntryJson.infoSystemCode?.let { builder.setInfoSystem(it) }
        logEntryJson.action?.let { builder.setAction(it) }

        return builder.build()
    }

    /**
     * Parse multiple JSON elements into LogEntryProto list
     */
    fun parseLogEntries(logElements: List<JsonElement>): List<LogEntryProto> {
        return logElements.map { parseLogEntry(it) }
    }

    /**
     * Format timestamp for display (includes year)
     */
    fun formatDisplayTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            date?.let {
                val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                "${dateFormat.format(it)}, ${timeFormat.format(it)}"
            } ?: timestamp
        } catch (e: ParseException) {
            Log.w("LogEntryManager", "Failed to parse timestamp: $timestamp", e)
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
