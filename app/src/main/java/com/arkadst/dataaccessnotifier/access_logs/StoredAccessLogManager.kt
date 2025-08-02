package com.arkadst.dataaccessnotifier.access_logs

import android.content.Context
import android.util.Log
import com.arkadst.dataaccessnotifier.AccessLogsProto
import com.arkadst.dataaccessnotifier.LogEntryProto
import com.arkadst.dataaccessnotifier.NotificationManager.showAccessLogNotification
import com.arkadst.dataaccessnotifier.accessLogsDataStore
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager
import kotlinx.coroutines.flow.first

private const val TAG = "StoredAccessLogManager"

object StoredAccessLogManager {

    suspend fun addAccessLogs(context: Context, logEntries: List<LogEntryProto>) {
        context.accessLogsDataStore.updateData { currentLogs ->
            val existingHashes = currentLogs.entriesList.map { it.contentHash }.toSet()
            val builder = currentLogs.toBuilder()

            var newEntriesCounter = 0

            logEntries.forEach { logEntry ->
                if (!existingHashes.contains(logEntry.contentHash)) {
                    builder.addEntries(logEntry)
                    if (!UserInfoManager.isFirstUse(context))
                        showAccessLogNotification(context, logEntry)
                    newEntriesCounter++
                }
            }

            Log.d(TAG, "New entries found: $newEntriesCounter")

            builder.build()
        }
    }
}