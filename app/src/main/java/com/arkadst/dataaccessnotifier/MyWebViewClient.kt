package com.arkadst.dataaccessnotifier

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

private val TAG = "MyWebViewClient"
class MyWebViewClient(
    private val extractAndReturnCookies: ((Map<String, String>) -> Unit, () -> Unit) -> Unit,
    private val onAuthComplete: (Map<String, String>) -> Unit,
    private val onAuthError: () -> Unit
) : WebViewClient(){

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)

        url?.let {
            if (it.startsWith(SUCCESS_URL)) {
                extractAndReturnCookies(onAuthComplete, onAuthError)
                Log.d(TAG, "Destroying WebView after auth complete")
                view.destroy()
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