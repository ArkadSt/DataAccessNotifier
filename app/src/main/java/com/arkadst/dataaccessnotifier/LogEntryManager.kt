package com.arkadst.dataaccessnotifier

import android.content.Context
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

object LogEntryManager {
    private const val TAG = "LogEntryManager"

    /**
     * Parse a JSON element into a LogEntryProto
     */
    fun parseLogEntry(logElement: JsonElement): LogEntryProto {
        val jsonObject = logElement.jsonObject

        val timestamp = jsonObject["logTime"]?.jsonPrimitive?.content ?: ""
        val receiver = jsonObject["receiver"]?.jsonPrimitive?.content ?: ""
        val infoSystem = jsonObject["infoSystemCode"]?.jsonPrimitive?.content ?: ""
        val action = jsonObject["action"]?.jsonPrimitive?.content ?: ""

        val hash = generateContentHash(timestamp, receiver, infoSystem, action)

        return LogEntryProto.newBuilder()
            .setTimestamp(timestamp)
            .setReceiver(receiver)
            .setInfoSystem(infoSystem)
            .setAction(action)
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
