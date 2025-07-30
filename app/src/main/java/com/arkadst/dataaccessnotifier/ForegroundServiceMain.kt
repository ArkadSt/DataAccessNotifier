package com.arkadst.dataaccessnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arkadst.dataaccessnotifier.NotificationManager.showAccessLogNotification
import com.arkadst.dataaccessnotifier.Utils.getURL
import com.arkadst.dataaccessnotifier.Utils.isFirstUse
import com.arkadst.dataaccessnotifier.Utils.logOut
import com.arkadst.dataaccessnotifier.Utils.setFirstUse
import com.arkadst.dataaccessnotifier.access_logs.StoredAccessLogManager
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private const val CHANNEL_ID = "JwtExtensionChannel"
private const val NOTIFICATION_ID = 1
private const val TAG = "ForegroundServiceMain"

class ForegroundServiceMain: Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (extendJwtSession(intent.getIntExtra(RETRIES_KEY, 20))) {
                    pollDataTracker()
                    AlarmScheduler.scheduleNextRefresh(applicationContext)
                }
            } finally {
                stopSelf()
            }

        }

        return START_NOT_STICKY
    }

    private suspend fun extendJwtSession(retries : Int) : Boolean {
        val responseCode = getURL(applicationContext, JWT_EXTEND_URL).first

        when (responseCode) {
            200 -> {
                Log.d(TAG, "JWT session extended successfully")
                return true
            }
            500 -> {
                Log.e(TAG, "JWT extension failed: 500 Internal Server Error. Retrying in 30 seconds.")
                AlarmScheduler.scheduleNextRefresh(applicationContext, 30 * 1000L, retries-1)
            }
            else -> {
                Log.e(TAG, "JWT extension failed: $responseCode")
                logOut(applicationContext)
            }
        }
        return false
    }

    private suspend fun pollDataTracker() : Boolean {

            val response = getURL(applicationContext, DATA_TRACKER_API_URL)
            val statusCode = response.first
            if (statusCode == 200) {
                response.second.let { body ->
                    Log.d(TAG, "Data tracker response: $body")
                    val entries = parseDataTrackerResponseBody(body)
                    Log.d(TAG, "Parsed entries: $entries")
                    handleParsedEntries(entries)
                }
                return true
            } else {
                Log.e(TAG, "Data tracker API call failed: $statusCode")
                return false
            }
    }

    private suspend fun parseDataTrackerResponseBody(body: String) : List<JsonElement> {
        // Implement your parsing logic here
        parseToJsonElement(body).let { jsonElement : JsonElement ->
            jsonElement.jsonObject["findUsageResponses"]?.let { entries ->
                if (entries is JsonArray) {
                    return entries.filterNot { entry : JsonElement ->
                        val personalCode : String = applicationContext.userInfoDataStore.data.first().asMap()[PERSONAL_CODE_KEY]?.toString()!!
                        val receiver = entry.jsonObject["receiver"]?.toString()
                        receiver?.contains(personalCode) == true
                    }
                } else {
                    Log.e(TAG, "Expected JsonArray but got ${entries.javaClass}")
                }
            }
        }
        return emptyList()
    }

    private suspend fun handleParsedEntries(entries: List<JsonElement>) {
        // Use LogEntryManager to parse entries into LogEntryProto objects
        val parsedEntries = LogEntryManager.parseLogEntries(entries)

        // Filter out entries that already exist using LogEntryProto objects
        val newEntries = parsedEntries.filterNot { entry ->
            StoredAccessLogManager.hasAccessLog(applicationContext, entry)
        }

        // Add new entries to storage
        newEntries.forEach { entry ->
            StoredAccessLogManager.addAccessLog(applicationContext, entry)
        }

        if (newEntries.isEmpty()) {
            Log.d(TAG, "No new entries found")
        } else {
            Log.d(TAG, "New entries found: ${newEntries.size}")
        }

        if (isFirstUse(applicationContext)) {
            setFirstUse(applicationContext, false)
        } else {
            Log.d(TAG, "Not first use, showing notifications for ${newEntries.size} new entries")
            // Directly use LogEntryProto objects for notifications
            newEntries.forEach { entry ->
                showAccessLogNotification(applicationContext, entry)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "JWT Extension Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JWT Extension Service")
            .setContentText("Keeping your session alive")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

}