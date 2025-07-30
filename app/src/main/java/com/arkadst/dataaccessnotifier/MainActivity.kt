package com.arkadst.dataaccessnotifier

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arkadst.dataaccessnotifier.ui.theme.DataAccessNotifierTheme
import com.arkadst.dataaccessnotifier.Utils.fetchUserInfo
import com.arkadst.dataaccessnotifier.Utils.getURL
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arkadst.dataaccessnotifier.NotificationManager.requestNotificationPermission
import com.arkadst.dataaccessnotifier.Utils.logOut
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import kotlinx.coroutines.flow.map

const val LOGIN_URL = "https://www.eesti.ee/timur/oauth2/authorization/govsso?callback_url=https://www.eesti.ee/auth/callback&locale=et"
const val SUCCESS_URL = "https://www.eesti.ee/auth/callback"
private const val TAG = "CookieExtraction"
private const val API_TEST_URL = "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=rahvastikuregister"

class MainActivity : ComponentActivity() {

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private suspend fun saveCookies(context: Context, cookies: Map<String, String>) {
        context.cookieDataStore.edit { storedCookies ->
            cookies.forEach { (name, value) ->
                storedCookies[stringPreferencesKey(name)] = value
            }
        }
        Log.d(TAG, "Saved ${cookies.size} cookies")
    }


    private fun cookieStringToMap(cookieString: String): Map<String, String>{
        val cookieMap = mutableMapOf<String, String>()
        cookieString.split(";").forEach { cookie ->
            val cookiePair = cookie.trim().split("=")
            if (cookiePair.size == 2) {
                val cookieName = cookiePair[0].trim()
                cookieMap[cookieName] = cookie
                Log.d(TAG, "Cookie: $cookieName = $cookie")
            }
        }
        return cookieMap
    }



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

    @Composable
    fun AuthScreen() {

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val isLoggedIn by LoginStateRepository.isLoggedIn.collectAsState()
        var loggingIn by remember { mutableStateOf(false) }
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
                        onAuthComplete = { cookies ->
                            scope.launch {
                                //saveCookies(context, cookies)
                                fetchUserInfo(context)
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
                                logOut(context)
                            }
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun LoginButton(
        modifier: Modifier = Modifier,
        onLoginClick: () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onLoginClick) {
                Text(text = "Log in")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun AuthWebView(
        modifier: Modifier = Modifier,
        onAuthComplete: (Map<String, String>) -> Unit,
        onAuthError: () -> Unit
    ) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {


                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                    }

                    webViewClient = MyWebViewClient(
                        { onSuccess, onError ->
                            extractAndReturnCookies( onSuccess, onError)
                        },
                        onAuthComplete,
                        onAuthError
                    )

                    loadUrl(LOGIN_URL)
                }
            }
        )
    }

    @Composable
    fun LoggedInScreen(
        modifier: Modifier = Modifier,
        onLogout: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Read first name from DataStore - fixed to use remember to avoid recomposition issues
        val firstNameFlow = remember {
            context.userInfoDataStore.data.map { preferences ->
                preferences[FIRST_NAME_KEY] ?: ""
            }
        }
        val firstName by firstNameFlow.collectAsState(initial = "")

        // Use collectAsState to automatically update when new entries are added
        val logEntries by LogEntryManager.loadLogEntriesFlow(context).collectAsState(initial = emptyList())

        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
        ) {
            // Welcome message with first name
            if (firstName.isNotEmpty()) {
                Text(
                    text = "Welcome, $firstName!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Header row with title and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Data Access Monitor",
                    style = MaterialTheme.typography.headlineSmall
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Test API button - smaller and moved to header
                    // Comment out the test button since it's for testing only
                    /*
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                getURL(context, API_TEST_URL)
                            }
                        }
                    ) {
                        Text(text = "Test", style = MaterialTheme.typography.bodySmall)
                    }
                    */

                    Button(onClick = onLogout) {
                        Text(text = "Log out")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log entries section with improved spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${logEntries.size} entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable list of log entries
            if (logEntries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No activity yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Data access logs will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(logEntries.size) { index ->
                        LogEntryItem(logEntry = logEntries[index])
                    }
                }
            }
        }
    }

    @Composable
    fun LogEntryItem(logEntry: LogEntryProto) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = logEntry.infoSystem,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = LogEntryManager.formatDisplayTime(logEntry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = logEntry.receiver,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = logEntry.action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }


    private fun extractAndReturnCookies(
        onSuccess: (Map<String, String>) -> Unit,
        onError: () -> Unit
    ) {

        val cookieManager = CookieManager.getInstance()

        val domains = listOf(
            "https://www.eesti.ee",
        )

        val allCookies = mutableMapOf<String, String>()

        domains.forEach { domain ->
            val cookies = cookieManager.getCookie(domain)
            cookies?.let { cookieString ->
                if (cookieString.isNotEmpty()) {
                    Log.d(TAG, "Cookies from $domain: $cookieString")
                    val cookieMap = cookieStringToMap(cookieString)
                    allCookies.putAll(cookieMap)
                }
            }
        }

        Log.d(TAG, "Total extracted cookies: ${allCookies.size}")
        if (allCookies.isNotEmpty()) {
            onSuccess(allCookies)
        } else {
            onError()
        }
    }
}

enum class AuthState {
    LoggedOut,
    LoggingIn,
    LoggedIn
}