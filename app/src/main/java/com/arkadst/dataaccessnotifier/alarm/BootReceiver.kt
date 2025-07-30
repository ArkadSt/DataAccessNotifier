package com.arkadst.dataaccessnotifier.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            AlarmScheduler.ensureAlarmScheduled(context, 60 * 1000L); // custom method to reschedule your alarm
        }
    }

}