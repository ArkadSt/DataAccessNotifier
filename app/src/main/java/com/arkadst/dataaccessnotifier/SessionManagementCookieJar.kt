package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
private const val LOG = "SessionManagementCookieJar"
class SessionManagementCookieJar(private val context: Context) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return runBlocking {
            val prefs = context.cookieDataStore.data.first()
            prefs.asMap().mapNotNull { (_, cookieString) ->
                val cookie = Cookie.parse(url, "$cookieString")
                Log.d(LOG, "Loaded cookie: $cookie")
                cookie
            }
        }

    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        runBlocking {
            Log.d(LOG, "Saving cookies for URL: $url")
            context.cookieDataStore.edit { prefs ->
                cookies.forEach { cookie : Cookie ->
                    Log.d(LOG, "Received cookie: $cookie")
                    if (cookie.expiresAt < System.currentTimeMillis()) {
                        Log.d(LOG, "Skipping expired cookie: $cookie")
                    } else {
                        prefs[stringPreferencesKey(cookie.name)] = cookie.toString()
                    }

                }
            }
        }
    }
}