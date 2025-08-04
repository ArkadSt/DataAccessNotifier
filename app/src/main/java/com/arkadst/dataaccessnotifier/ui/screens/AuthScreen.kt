package com.arkadst.dataaccessnotifier.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.arkadst.dataaccessnotifier.auth.LoginStateRepository
import com.arkadst.dataaccessnotifier.MainActivity
import com.arkadst.dataaccessnotifier.ui.components.LoginButton
import com.arkadst.dataaccessnotifier.ui.components.AuthWebView
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import com.arkadst.dataaccessnotifier.auth.AuthManager
import com.arkadst.dataaccessnotifier.core.Constants.ACTION_TRIGGER_LOGIN
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager
import kotlinx.coroutines.launch

enum class AuthState {
    LoggedOut,
    LoggingIn,
    LoggedIn
}

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoggedIn by LoginStateRepository.isLoggedIn.collectAsState()
    var loggingIn by remember { mutableStateOf(false) }

    // Handle intent from notification to trigger login
    LaunchedEffect(Unit) {
        val activity = context as? MainActivity
        if (activity?.intent?.action == ACTION_TRIGGER_LOGIN) {
            loggingIn = true
            activity.intent.action = null
        }
    }

    val authState = when {
        loggingIn -> AuthState.LoggingIn
        isLoggedIn -> AuthState.LoggedIn
        else -> AuthState.LoggedOut
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (authState) {
            AuthState.LoggedOut -> {
                LoginButton(
                    modifier = Modifier.padding(innerPadding),
                    onLoginClick = {
                        Toast.makeText(context, "Logging in...", Toast.LENGTH_SHORT).show()
                        loggingIn = true
                    }
                )
            }

            AuthState.LoggingIn -> {
                AuthWebView(
                    modifier = Modifier.padding(innerPadding),
                    onAuthComplete = {
                        scope.launch {
                            UserInfoManager.extractUserInfo(context)
                            loggingIn = false
                            LoginStateRepository.setLoggedIn(context, true)
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAuthError = {
                        loggingIn = false
                        Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            AuthState.LoggedIn -> {
                LaunchedEffect(Unit) {
                    AlarmScheduler.ensureAlarmScheduled(context, 0L)
                }

                LoggedInScreen(
                    modifier = Modifier.padding(innerPadding),
                    onLogout = {
                        scope.launch {
                            AuthManager.logOut(context)
                        }
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
