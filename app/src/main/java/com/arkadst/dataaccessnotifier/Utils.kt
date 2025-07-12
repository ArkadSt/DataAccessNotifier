package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Response

private const val COOKIE_PREFS = "auth_cookies"
private const val USER_INFO_PREFS = "user_info"
private const val ACCESS_LOGS_PREFS = "access_logs"
val PERSONAL_CODE_KEY = stringPreferencesKey("personalCode")

val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(name = COOKIE_PREFS)
val Context.userInfoDataStore : DataStore<Preferences> by preferencesDataStore(name = USER_INFO_PREFS)
val Context.accessLogsDataStore : DataStore<Preferences> by preferencesDataStore(name = ACCESS_LOGS_PREFS)

private const val TAG = "getURL"
class Utils {
    companion object {
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

         suspend fun clearSavedCookies(context: Context) {
             context.cookieDataStore.edit { prefs ->
                 prefs.clear()
             }

            // Also clear WebView cookies
            // CookieManager.getInstance().removeAllCookies(null)
            // Log.d("ClearCookies", "Cleared all cookies")
        }

        suspend fun fetchUserInfo(context: Context) {
            val response = getURL(context, "https://www.eesti.ee/api/xroad/v2/rr/kodanik/info")
            val body: String = response.body.string()
            Log.d("UserInfo", "Response Body: $body")
            parseToJsonElement(body).let { jsonElement: JsonElement ->
                Log.d("UserInfo", "User Info: $jsonElement")
                jsonElement.jsonObject["personalCode"]?.let {
                    context.userInfoDataStore.edit { prefs ->
                        prefs[PERSONAL_CODE_KEY] = it.toString().replace("EE", "").replace("\"", "")
                    }
                } ?: Log.d("UserInfo", "Personal code not found in response")
            }
        }

    }
}