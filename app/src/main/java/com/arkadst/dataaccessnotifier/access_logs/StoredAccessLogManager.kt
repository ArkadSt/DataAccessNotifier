package com.arkadst.dataaccessnotifier.access_logs

import android.content.Context
import com.arkadst.dataaccessnotifier.AccessLogsProto
import com.arkadst.dataaccessnotifier.LogEntryProto
import com.arkadst.dataaccessnotifier.accessLogsDataStore
import kotlinx.coroutines.flow.first

object StoredAccessLogManager {

    suspend fun addAccessLog(context: Context, logEntry: LogEntryProto) {
        context.accessLogsDataStore.updateData { currentLogs ->
            val existingHashes = currentLogs.entriesList.map { it.contentHash }.toSet()

            val builder = currentLogs.toBuilder()
            if (!existingHashes.contains(logEntry.contentHash)) {
                builder.addEntries(logEntry)
            }

            builder.build()
        }
    }

    suspend fun hasAccessLog(context: Context, logEntry: LogEntryProto): Boolean {
        val logs = context.accessLogsDataStore.data.first()
        return logs.entriesList.any { it.contentHash == logEntry.contentHash }
    }

    suspend fun getAllAccessLogs(context: Context): List<LogEntryProto> {
        val logs = context.accessLogsDataStore.data.first()
        return logs.entriesList.sortedByDescending { it.timestamp }
    }

    suspend fun clearAccessLogs(context: Context) {
        context.accessLogsDataStore.updateData {
            AccessLogsProto.getDefaultInstance()
        }
    }

    suspend fun getAccessLogCount(context: Context): Int {
        val logs = context.accessLogsDataStore.data.first()
        return logs.entriesCount
    }
}