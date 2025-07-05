package com.arkadst.dataaccessnotifier

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DataTrackerPollingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

}