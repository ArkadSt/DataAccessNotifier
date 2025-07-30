package com.arkadst.dataaccessnotifier

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest

private const val TAG = "ReAuthWebViewHeadless"

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class ReAuthWebViewHeadless(context: Context, private val onComplete: (Boolean) -> Unit) : WebView(context) {

    private val cookieManager: CookieManager = CookieManager.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCompleted = false

    init {
        try {
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Add memory optimization settings
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (isCompleted) return

                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished loading: $url")
                    if (url.startsWith("https://govsso.ria.ee/login/init?login_challenge=")){
                        view.evaluateJavascript(
                            """
                        (function() {
                            var btn = document.querySelector('button[formaction="/login/continuesession"]');
                            if (btn) {
                                btn.click();
                                return "Button clicked";
                            }
                            return "Button not found";
                        })();
                        """.trimIndent()
                        ) { result -> Log.d(TAG, result ?: "No result from JS") }
                    }

                    if (url.startsWith(SUCCESS_URL)) {
                        Log.d(TAG, "Re-authentication successful")
                        completeAuthentication(true)
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "WebView error: ${error?.description}")
                    completeAuthentication(false)
                }
            }

            loadUrl(LOGIN_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WebView", e)
            completeAuthentication(false)
        }
    }

    private fun completeAuthentication(success: Boolean) {
        if (isCompleted) return
        isCompleted = true

        try {
            Log.d(TAG, cookieManager.getCookie("https://www.eesti.ee"))
            // Flush cookies before cleanup
            cookieManager.flush()

            // Delay cleanup to allow pending IPC operations to complete
            mainHandler.postDelayed({
                try {
                    // More thorough cleanup
                    stopLoading()
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    removeAllViews()
                    onPause()
                    destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during WebView cleanup", e)
                }
            }, 1000) // Increased to 1 second for better safety

            // Call completion callback
            onComplete(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error completing authentication", e)
            onComplete(false)
        }
    }

    fun cleanup() {
        if (!isCompleted) {
            Log.d(TAG, "Cleaning up WebView due to timeout or error")
            completeAuthentication(false)
        }
    }
}