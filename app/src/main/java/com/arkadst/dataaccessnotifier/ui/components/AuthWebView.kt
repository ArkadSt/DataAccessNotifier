package com.arkadst.dataaccessnotifier.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.arkadst.dataaccessnotifier.MyWebViewClient
import com.arkadst.dataaccessnotifier.core.Constants

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebView(
    modifier: Modifier = Modifier,
    onAuthComplete: () -> Unit,
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
                    onAuthComplete,
                    onAuthError
                )

                loadUrl(Constants.LOGIN_URL)
            }
        }
    )
}
