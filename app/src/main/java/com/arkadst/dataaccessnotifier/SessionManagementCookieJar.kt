package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import androidx.core.content.edit

class SessionManagementCookieJar(private val context: Context) : CookieJar {

    companion object {
        private const val COOKIE_PREFS = "auth_cookies"
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val prefs = context.getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->

            val cookie = Cookie.parse(url, "$key=$value")
            //Log.d(SessionManagementCookieJar.javaClass.name,  cookie.toString())
            cookie

        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        val prefs = context.getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            cookies.forEach { cookie ->
                Log.d(SessionManagementCookieJar.javaClass.name,  cookie.toString())
                putString(cookie.name, cookie.value)
            }
        }
    }
}