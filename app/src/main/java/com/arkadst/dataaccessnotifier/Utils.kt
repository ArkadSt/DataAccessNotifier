package com.arkadst.dataaccessnotifier

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Response

class Utils {
    companion object {
        private const val TAG = "getURL"
        private const val COOKIE_PREFS = "auth_cookies"
        suspend fun getURL(context: Context, url: String): Response {
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
                //Log.d(TAG, "API Response: ${response.body?.string()}")

                return@withContext response
            }
        }

         fun clearSavedCookies(context: Context) {
            context.getSharedPreferences(COOKIE_PREFS, MODE_PRIVATE)
                .edit {
                    clear()
                }

            // Also clear WebView cookies
            CookieManager.getInstance().removeAllCookies(null)
            Log.d("ClearCookies", "Cleared all cookies")
        }

        suspend fun fetchUserInfo(context: Context) {
            val response = getURL(context, "https://www.eesti.ee/api/xroad/v2/rr/kodanik/info")
            val body: String = response.body.string()
            parseToJsonElement(body).let { jsonElement : JsonElement ->
                Log.d("UserInfo", "User Info: $jsonElement")
                jsonElement.jsonObject["personalCode"]?.let { personalCode ->

                    Constants.Companion.personalCode = personalCode.toString().replace("EE", "")
                    Log.d("UserInfo", "Personal code: ${Constants.Companion.personalCode}")
                } ?: Log.d("UserInfo", "Personal code not found in response")
            }
        }
    }
}