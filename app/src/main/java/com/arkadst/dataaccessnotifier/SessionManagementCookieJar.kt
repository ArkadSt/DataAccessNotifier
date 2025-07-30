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


class SessionManagementCookieJar(private val context: Context) : CookieJar {
    val cookieBuffer = mutableMapOf<Preferences.Key<String>, String>()
    private val LOG = "SessionManagementCookieJar"
    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString : String = cookieManager.getCookie(url.toString())
        return cookieString.split(";").mapNotNull { cookie ->
            Cookie.parse(url, cookie.trim())
        }

//        return runBlocking {
//            val prefs = context.cookieDataStore.data.first()
//            prefs.asMap().mapNotNull { (_, cookieString) ->
//                val cookie = Cookie.parse(url, "$cookieString")
//                Log.d(LOG, "Loaded cookie: $cookie")
//                cookie
//            }
//        }

    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            Log.d(LOG, "Saving cookie: $cookie for URL: $url")
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
        cookieManager.flush()
//        runBlocking {
//            Log.d(LOG, "Saving cookies for URL: $url")
//            context.cookieDataStore.edit { prefs ->
//                cookies.forEach { cookie : Cookie ->
//                    Log.d(LOG, "Received cookie: $cookie")
//                    if (cookie.expiresAt < System.currentTimeMillis()) {
//                        Log.d(LOG, "Skipping expired cookie: $cookie")
//                    } else {
//                        cookieBuffer[stringPreferencesKey(cookie.name)] = cookie.toString()
//                    }
//
//                }
//            }
//        }
    }
}