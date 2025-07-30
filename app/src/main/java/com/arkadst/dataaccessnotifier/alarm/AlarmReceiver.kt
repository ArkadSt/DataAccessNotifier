package com.arkadst.dataaccessnotifier.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.arkadst.dataaccessnotifier.ForegroundServiceMain

private const val TAG = "AlarmReceiver"
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received")

        val serviceIntent = Intent(context, ForegroundServiceMain::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)


    }
}