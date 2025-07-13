package com.arkadst.dataaccessnotifier

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arkadst.dataaccessnotifier.ui.theme.DataAccessNotifierTheme
import com.arkadst.dataaccessnotifier.Utils.Companion.clearSavedCookies
import com.arkadst.dataaccessnotifier.Utils.Companion.fetchUserInfo
import com.arkadst.dataaccessnotifier.Utils.Companion.getURL
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.collections.forEach

private const val LOGIN_URL = "https://www.eesti.ee/timur/oauth2/authorization/govsso?callback_url=https://www.eesti.ee/auth/callback&locale=et"
private const val SUCCESS_URL = "https://www.eesti.ee/auth/callback"
private const val TAG = "CookieExtraction"
private const val API_TEST_URL = "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=rahvastikuregister"

class MainActivity : ComponentActivity() {

    private var jwtServiceIntent: Intent? = null

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
                //val cookieString = cookiePair[1].trim()
                cookieMap[cookieName] = cookie
                Log.d(TAG, "Cookie: $cookieName = $cookie")
            }
        }
        return cookieMap
    }



    override fun onCreate(savedInstanceState: Bundle?) {
//        Log.d("IsJobActive", jwtServiceIntent.toString())
        super.onCreate(savedInstanceState)
        LoginStateRepository.init(this)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            DataAccessNotifierTheme {
                AuthScreen()
            }
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
                                saveCookies(context, cookies)
                                fetchUserInfo(context)
                                loggingIn = false
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

                    @Suppress("DEPRECATION")
                    LaunchedEffect(Unit) {
                        val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
                        val runningService = am.getRunningServices(Integer.MAX_VALUE)
                            .find { it.service.className == ForegroundServiceMain::class.java.name }

                        if (runningService != null) {
                            Log.d(TAG, "JWT extension service is already running")
                            jwtServiceIntent = Intent().apply {
                                component = runningService.service
                            }
                        } else {
                            startJwtExtensionService()
                        }
                    }

                    LoggedInScreen(
                        modifier = Modifier.padding(innerPadding),
                        onLogout = {
                            scope.launch {
                                clearSavedCookies(context)
                            }
                            stopJwtExtensionService()
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

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            url?.let {
                                if (it.startsWith(SUCCESS_URL)) {
                                    extractAndReturnCookies(cookieManager, onAuthComplete, onAuthError)
                                    Log.d(TAG, "Destroying WebView after auth complete")
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            error?.let {
                                Log.e(TAG, "WebView error: ${it.description} (Code: ${it.errorCode})")
                                // Only trigger auth error for main frame errors
                                if (request?.isForMainFrame == true) {
                                    onAuthError()
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            errorResponse?.let {
                                Log.e(TAG, "HTTP error: ${it.statusCode} ${it.reasonPhrase}")
                                // Only trigger auth error for main frame HTTP errors
                                if (request?.isForMainFrame == true && it.statusCode >= 400) {
                                    onAuthError()
                                }
                            }
                        }
                    }

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
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = {
                    scope.launch {
                        getURL(context, API_TEST_URL)
                    }
                }) {
                    Text(text = "Test API Request")
                }
                Button(onClick = onLogout) {
                    Text(text = "Log out")
                }
            }
        }
    }



    private fun extractAndReturnCookies(
        cookieManager: CookieManager,
        onSuccess: (Map<String, String>) -> Unit,
        onError: () -> Unit
    ) {
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


    private fun startJwtExtensionService() {
        val workRequest = OneTimeWorkRequestBuilder<JwtExtentionWorker>()
            .setInitialDelay(0, TimeUnit.SECONDS)
            .setConstraints(workerConstraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            JWT_WORKER_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
        Log.d(TAG, "JWT extension service started")
    }

    private fun stopJwtExtensionService() {
//        jwtServiceIntent?.let { intent ->
//            stopService(intent)
//            Log.d(TAG, "JWT extension service stopped")
//        }
//        jwtServiceIntent = null
        WorkManager.getInstance(this).cancelAllWork()
        Log.d(TAG, "Cancelled all work")
    }
}

enum class AuthState {
    LoggedOut,
    LoggingIn,
    LoggedIn
}