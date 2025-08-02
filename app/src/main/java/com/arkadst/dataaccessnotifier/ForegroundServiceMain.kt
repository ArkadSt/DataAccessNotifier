package com.arkadst.dataaccessnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arkadst.dataaccessnotifier.NotificationManager.showAccessLogNotification
import com.arkadst.dataaccessnotifier.Utils.getURL
import com.arkadst.dataaccessnotifier.access_logs.LogEntryManager
import com.arkadst.dataaccessnotifier.access_logs.StoredAccessLogManager
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import com.arkadst.dataaccessnotifier.auth.AuthManager
import com.arkadst.dataaccessnotifier.core.Constants.DATA_TRACKER_API_URL
import com.arkadst.dataaccessnotifier.core.Constants.JWT_EXTEND_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.TimeUnit

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
                AlarmScheduler.scheduleNextRefresh(applicationContext, TimeUnit.SECONDS.toMillis(30), retries-1)
            }
            else -> {
                Log.e(TAG, "JWT extension failed: $responseCode")
                AuthManager.logOut(applicationContext)
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

    private suspend fun parseDataTrackerResponseBody(body: String) : List<LogEntryProto> {
        // Implement your parsing logic here
        parseToJsonElement(body).let { jsonElement : JsonElement ->
            jsonElement.jsonObject["findUsageResponses"]?.let { entries ->
                if (entries is JsonArray) {
                    return LogEntryManager.parseLogEntries(entries).filterNot { entry : LogEntryProto ->
                        val userInfo = UserInfoManager.getUserInfo(applicationContext)
                        entry.receiver.contains(userInfo.personalCode)
                    }
                } else {
                    Log.e(TAG, "Expected JsonArray but got ${entries.javaClass}")
                }
            }
        }
        return emptyList()
    }

    private suspend fun handleParsedEntries(entries: List<LogEntryProto>) {

        // Add new entries to storage
        StoredAccessLogManager.addAccessLogs(applicationContext, entries)


        UserInfoManager.setFirstUse(applicationContext, false)

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