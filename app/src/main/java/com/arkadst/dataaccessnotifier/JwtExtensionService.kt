package com.arkadst.dataaccessnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arkadst.dataaccessnotifier.Utils.Companion.getURL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "JwtExtensionChannel"
private const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
private const val NOTIFICATION_ID = 1

class JwtExtensionService: Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startJwtExtension()
        return START_STICKY
    }

    private fun startJwtExtension() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
//                try {
                    //getURL(applicationContext, API_TEST_URL)
                    //val randomDelay = (0..60).random().toLong()
                    //delay(randomDelay * 1000)
                    val response = getURL(applicationContext, JWT_EXTEND_URL)
                    if (response.code != 200) {
                        Log.e(TAG, "JWT extension failed: ${response.code}")
                    }
                    delay(60 * 1000) // 60 seconds
//                } catch (e: Exception) {
//                    Log.e(TAG, "JWT extension failed", e)
//                    delay(30 * 1000)
//                }
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
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JwtExtensionService"
    }
}