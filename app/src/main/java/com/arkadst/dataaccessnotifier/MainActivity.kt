package com.arkadst.dataaccessnotifier


import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.arkadst.dataaccessnotifier.ui.theme.DataAccessNotifierTheme
import com.arkadst.dataaccessnotifier.NotificationManager.requestNotificationPermission
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import com.arkadst.dataaccessnotifier.auth.LoginStateRepository
import com.arkadst.dataaccessnotifier.ui.screens.AuthScreen
private const val TAG = "CookieExtraction"
class MainActivity : ComponentActivity() {

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Notification permission denied")
                Toast.makeText(this, "Notification permission denied. The app won't work without it.", Toast.LENGTH_SHORT).show()
                this.finishAffinity()
            }
        }

        LoginStateRepository.init(this)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            DataAccessNotifierTheme {
                AuthScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AlarmScheduler.requestExactAlarmPermissionIfNeeded(this)){
            requestNotificationPermission(this, notificationPermissionLauncher)
        }
    }
}