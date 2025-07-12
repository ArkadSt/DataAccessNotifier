package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val COOKIE_PREFS = "auth_cookies"
private const val LOG = "SessionManagementCookieJar"
class SessionManagementCookieJar(private val context: Context) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return runBlocking {
            val prefs = context.cookieDataStore.data.first()
            prefs.asMap().mapNotNull { (key, value) ->
                val cookie = Cookie.parse(url, "$key=$value")
                cookie
            }
        }

    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        runBlocking {
            context.cookieDataStore.edit { prefs ->
                cookies.forEach { cookie ->
                    Log.d(LOG, cookie.toString())
                    prefs[stringPreferencesKey(cookie.name)] = cookie.value
                }
            }
        }
    }
}