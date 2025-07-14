package com.arkadst.dataaccessnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arkadst.dataaccessnotifier.Utils.Companion.clearSavedCookies
import com.arkadst.dataaccessnotifier.Utils.Companion.getURL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.text.get


private const val CHANNEL_ID = "JwtExtensionChannel"
private const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
private const val DATA_TRACKER_API_URL = "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=digiregistratuur&dataSystemCodes=elamislubade_ja_toolubade_register&dataSystemCodes=kinnistusraamat&dataSystemCodes=kutseregister&dataSystemCodes=maksukohustuslaste_register&dataSystemCodes=infosusteem_polis&dataSystemCodes=politsei_taktikalise_juhtimise_andmekogu&dataSystemCodes=pollumajandusloomade_register&dataSystemCodes=pollumajandustoetuste_ja_pollumassiivide_register&dataSystemCodes=rahvastikuregister&dataSystemCodes=retseptikeskus&dataSystemCodes=sotsiaalkaitse_infosusteem&dataSystemCodes=sotsiaalteenuste_ja_toetuste_register&dataSystemCodes=tooinspektsiooni_tooelu_infosusteem&dataSystemCodes=tootuskindlustuse_andmekogu"
private const val NOTIFICATION_ID = 1
private const val TAG = "ForegroundServiceMain"

class ForegroundServiceMain: Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                extendJwtSession() && pollDataTracker()
            } finally {
                stopSelf()
            }

        }

        return START_NOT_STICKY
    }

    private suspend fun extendJwtSession() : Boolean {
        val response = getURL(applicationContext, JWT_EXTEND_URL)

        when (response.code) {
            200 -> {
                Log.d(TAG, "JWT session extended successfully")
                return true
            }
            500 -> {
                Log.e(TAG, "JWT extension failed: Server error. Retrying...")
                delay(1000)
                extendJwtSession()
            }
            else -> {
                Log.e(TAG, "JWT extension failed: ${response.code}")
                clearSavedCookies(applicationContext)
            }
        }
        return false
    }

    private suspend fun pollDataTracker() : Boolean {

            val response = getURL(applicationContext, DATA_TRACKER_API_URL)
            if (response.code == 200) {
                response.body.string().let { body ->
                    Log.d(TAG, "Data tracker response: $body")
                    val entries = parseDataTrackerResponseBody(body)
                    Log.d(TAG, "Parsed entries: $entries")
                    handleParsedEntries(entries)
                }
                return true
            } else {
                Log.e(TAG, "Data tracker API call failed: ${response.code}")
                return false
            }
    }

    private suspend fun parseDataTrackerResponseBody(body: String) : Map<Int, String> {
        val returnMap = mutableMapOf<Int, String>()
        // Implement your parsing logic here
        parseToJsonElement(body).let { jsonElement : JsonElement ->
            jsonElement.jsonObject["findUsageResponses"]?.let { entries ->
                if (entries is JsonArray) {
                    entries.filterNot { entry ->
                        val personalCode : String = applicationContext.userInfoDataStore.data.first().asMap()[PERSONAL_CODE_KEY]?.toString()!!
                        val receiver = entry.jsonObject["receiver"]?.toString()
                        receiver?.contains(personalCode) == true
                    }.forEach { entry ->
                        returnMap.put(entry.toString().hashCode(), entry.toString())
                    }
                } else {
                    Log.e(TAG, "Expected JsonArray but got: $entries")
                }
            }
        }
        return returnMap
    }

    private fun handleParsedEntries(entries: Map<Int, String>) {

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
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

}