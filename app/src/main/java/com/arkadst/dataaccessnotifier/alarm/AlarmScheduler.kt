package com.arkadst.dataaccessnotifier.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.arkadst.dataaccessnotifier.RETRIES_KEY

private const val TAG = "AlarmScheduler"
object AlarmScheduler {

    private fun createPendingIntent(context: Context, intent: Intent) : PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isAlarmSet(context: Context): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }

    fun requestExactAlarmPermissionIfNeeded(context: Context) : Boolean{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Alarm permission requested")
                return true
            }
        }
        return false
    }
    fun scheduleNextRefresh(context: Context, interval: Long = 5 * 60 * 1000L, retries: Int = 20) {
        if (retries <= 0) {
            Log.w(TAG, "No retries left, not scheduling next refresh")
            return
        }
        Log.d(TAG, "Scheduling next refresh in $interval ms")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra(RETRIES_KEY, retries)
        val triggerAt = System.currentTimeMillis() + interval


        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            createPendingIntent(context, intent)
        )

    }

    fun cancelRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = createPendingIntent(context, intent)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun ensureAlarmScheduled(context: Context, interval: Long = 60 * 1000L) {
        if (isAlarmSet(context)) {
            Log.d(TAG, "Alarm already scheduled")
        } else {
            Log.d(TAG, "Scheduling new alarm")
            scheduleNextRefresh(context, interval)
        }
    }
}