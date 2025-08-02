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

    /**
     * Adds new entries to the persistent store and returns entries which have been successfully added, i.e. where not previously persisted.
     */
    suspend fun addAccessLogs(context: Context, logEntries: Collection<LogEntryProto>) : Collection<LogEntryProto> {
        val newEntries = logEntries.toMutableSet();
        context.accessLogsDataStore.updateData { currentLogs ->
            newEntries.removeAll(currentLogs.entriesList.toSet());
            if (newEntries.isEmpty()) {
                Log.d(TAG, "No new access log entries")
                currentLogs
            } else {
                Log.d(TAG, "${newEntries.size} new access log entries")
                // Notify user about new access log entries
                if (!UserInfoManager.isFirstUse(context)) {
                    newEntries.forEach { entryProto ->
                        showAccessLogNotification(context, entryProto)
                    }
                }
                UserInfoManager.setFirstUse(context, false)
                currentLogs.toBuilder().addAllEntries(newEntries).build()
            }
        }
        return newEntries
    }
}