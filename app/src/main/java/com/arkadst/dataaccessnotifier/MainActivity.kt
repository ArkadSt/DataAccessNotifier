package com.arkadst.dataaccessnotifier

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arkadst.dataaccessnotifier.ui.theme.DataAccessNotifierTheme
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val LOGIN_URL = "https://www.eesti.ee/timur/oauth2/authorization/govsso?callback_url=https://www.eesti.ee/auth/callback&locale=et"
        private const val SUCCESS_URL = "https://www.eesti.ee/auth/callback"
        private const val TAG = "CookieExtraction"
        private const val COOKIE_PREFS = "auth_cookies"
        private const val API_TEST_URL = "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=rahvastikuregister"
        private const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
        private const val JWT_WORK_NAME = "jwt_extension_work"

        private fun saveCookies(context: Context, cookies: Map<String, String>) {
            val prefs = context.getSharedPreferences(COOKIE_PREFS, MODE_PRIVATE)
            prefs.edit {

                cookies.forEach { (name, value) ->
                    putString(name, value)
                }

            }
            Log.d(TAG, "Saved ${cookies.size} cookies")
        }

        private suspend fun getURL(context: Context, url: String): Response {
            return withContext(Dispatchers.IO) {
                Log.d(TAG, "Starting API test request to: $url")

                val client = okhttp3.OkHttpClient.Builder()
                    .cookieJar(SessionManagementCookieJar(context))
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .build()

                val response: Response = client.newCall(request).execute()

                Log.d(TAG, "API Response Code: ${response.code}")
                Log.d(TAG, "API Response: ${response.body?.string()}")

                return@withContext response
            }
        }


        private fun cookieStringToMap(cookieString: String): Map<String, String>{
            val cookieMap = mutableMapOf<String, String>()
            cookieString.split(";").forEach { cookie ->
                val cookiePair = cookie.trim().split("=")
                if (cookiePair.size == 2) {
                    val cookieName = cookiePair[0].trim()
                    val cookieValue = cookiePair[1].trim()
                    cookieMap[cookieName] = cookieValue
                    Log.d(TAG, "Cookie: $cookieName = $cookieValue")
                }
            }
            return cookieMap
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        var authState by remember { mutableStateOf(AuthState.LoggedOut) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (authState) {
                AuthState.LoggedOut -> {
                    LoginButton(
                        modifier = Modifier.padding(innerPadding),
                        onLoginClick = {
                            Toast.makeText(context, "Logging in...", Toast.LENGTH_SHORT).show()
                            authState = AuthState.LoggingIn
                        }
                    )
                }

                AuthState.LoggingIn -> {
                    AuthWebView(
                        modifier = Modifier.padding(innerPadding),
                        onAuthComplete = { cookies ->
                            saveCookies(context, cookies)
                            authState = AuthState.LoggedIn
                            startJwtExtensionWork()
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                        },
                        onAuthError = {
                            authState = AuthState.LoggedOut
                            Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AuthState.LoggedIn -> {
                    LoggedInScreen(
                        modifier = Modifier.padding(innerPadding),
                        onLogout = {
                            clearSavedCookies()
                            stopJwtExtensionWork()
                            authState = AuthState.LoggedOut
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
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url?.let {
                                if (it.startsWith(SUCCESS_URL)) {
                                    extractAndReturnCookies(cookieManager, onAuthComplete, onAuthError)
                                    return true
                                }
                            }
                            return false
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
                Button(onClick = {
                    scope.launch {
                        getURL(context, JWT_EXTEND_URL)
                    }
                }){
                    Text(text = "Extend JWT Token")
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
//            "https://eesti.ee",
//            ".eesti.ee"  // Try domain cookies
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



    private fun clearSavedCookies() {
        getSharedPreferences(COOKIE_PREFS, MODE_PRIVATE)
            .edit {
                clear()
            }

        // Also clear WebView cookies
        CookieManager.getInstance().removeAllCookies(null)
        Log.d(TAG, "Cleared all cookies")
    }

    private fun startJwtExtensionWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val jwtExtensionWork = PeriodicWorkRequestBuilder<JwtExtensionWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                JWT_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                jwtExtensionWork
            )

        Log.d(TAG, "JWT extension work scheduled")
    }

    private fun stopJwtExtensionWork() {
        WorkManager.getInstance(this).cancelUniqueWork(JWT_WORK_NAME)
        Log.d(TAG, "JWT extension work cancelled")
    }

    class JwtExtensionWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

        companion object {
            private const val TAG = "JwtExtensionWorker"
        }

        override suspend fun doWork(): Result {
            return try {

                getURL(applicationContext, API_TEST_URL)

                val randomDelaySeconds = (0..300).random().toLong() // 0-5 minutes in seconds
                Log.d(TAG, "Adding random delay of ${randomDelaySeconds}s before JWT extension")

                kotlinx.coroutines.delay(randomDelaySeconds * 1000)

                Log.d(TAG, "Starting JWT extension work")

                val response = getURL(applicationContext, JWT_EXTEND_URL)

                if (response.code == 200) {

                    Log.d(TAG, "JWT extension successful")
                    Result.success()

                } else {
                    Log.e(TAG, "JWT extension failed")
                    Result.retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "JWT extension work failed", e)
                Result.retry()
            }
        }
    }


}



enum class AuthState {
    LoggedOut,
    LoggingIn,
    LoggedIn
}