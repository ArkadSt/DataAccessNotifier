package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.datastore.preferences.core.Preferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val LOG = "SessionManagementCookieJar"
class SessionManagementCookieJar(private val context: Context) : CookieJar {
    val cookieBuffer = mutableMapOf<Preferences.Key<String>, String>()
    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString : String = cookieManager.getCookie(url.toString())
        return cookieString.split(";").mapNotNull { cookie ->
            Cookie.parse(url, cookie.trim())
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            Log.d(LOG, "Saving cookie: $cookie for URL: $url")
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
        cookieManager.flush()
    }
}