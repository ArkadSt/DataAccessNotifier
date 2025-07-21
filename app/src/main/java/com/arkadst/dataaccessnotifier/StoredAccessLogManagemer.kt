package com.arkadst.dataaccessnotifier

import android.content.Context
import kotlinx.coroutines.flow.first

object StoredAccessLogManagemer {
    suspend fun addAccessLog(context: Context, logEntry: String) {
        context.accessLogsDataStore.updateData { currentLogs ->
            val existingLogs = currentLogs.logsList.toHashSet()
            existingLogs.add(logEntry)

            AccessLogsProto.newBuilder()
                .addAllLogs(existingLogs)
                .build()
        }
    }

    suspend fun hasAccessLog(context: Context, logEntry: String): Boolean {
        val logs = context.accessLogsDataStore.data.first()
        return logs.logsList.contains(logEntry)
    }

    suspend fun getAllAccessLogs(context: Context): Set<String> {
        val logs = context.accessLogsDataStore.data.first()
        return logs.logsList.toHashSet()
    }

    suspend fun clearAccessLogs(context: Context) {
        context.accessLogsDataStore.updateData {
            AccessLogsProto.getDefaultInstance()
        }
    }
}