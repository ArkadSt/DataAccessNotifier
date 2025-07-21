package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Response
import com.arkadst.dataaccessnotifier.NotificationManager.showLogoutNotification
import kotlinx.coroutines.flow.first

private const val COOKIE_PREFS = "auth_cookies"
private const val USER_INFO_PREFS = "user_info"
const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
const val DATA_TRACKER_API_URL =
    "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=digiregistratuur&dataSystemCodes=elamislubade_ja_toolubade_register&dataSystemCodes=kinnistusraamat&dataSystemCodes=kutseregister&dataSystemCodes=maksukohustuslaste_register&dataSystemCodes=infosusteem_polis&dataSystemCodes=politsei_taktikalise_juhtimise_andmekogu&dataSystemCodes=pollumajandusloomade_register&dataSystemCodes=pollumajandustoetuste_ja_pollumassiivide_register&dataSystemCodes=rahvastikuregister&dataSystemCodes=retseptikeskus&dataSystemCodes=sotsiaalkaitse_infosusteem&dataSystemCodes=sotsiaalteenuste_ja_toetuste_register&dataSystemCodes=tooinspektsiooni_tooelu_infosusteem&dataSystemCodes=tootuskindlustuse_andmekogu"

val PERSONAL_CODE_KEY = stringPreferencesKey("personalCode")
val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(name = COOKIE_PREFS)
val Context.userInfoDataStore: DataStore<Preferences> by preferencesDataStore(name = USER_INFO_PREFS)
val Context.accessLogsDataStore: DataStore<AccessLogsProto> by dataStore(
    fileName = "access_logs.pb",
    serializer = AccessLogsSerializer
)


private const val TAG = "getURL"

object Utils {

    suspend fun getURL(context: Context, url: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting API test request to: $url")

                val cookieJar = SessionManagementCookieJar(context)
                val client = okhttp3.OkHttpClient.Builder()
                    .cookieJar(cookieJar)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"
                    )
                    .build()

                val response: Response = client.newCall(request).execute()

                if (response.code !in 500..599) {
                    Log.d(TAG, "Saving ${cookieJar.cookieBuffer.size} cookies")
                    context.cookieDataStore.edit { prefs ->
                        cookieJar.cookieBuffer.forEach { (key, value) ->
                            prefs[key] = value
                        }
                    }
                }

                val returnValue = Pair(response.code, response.body.string())
                Log.d(TAG, "API Response Code: ${returnValue.first}")
                Log.d(TAG, "API Response: ${returnValue.second}")
                response.close()

                return@withContext returnValue
            } catch (ex: IOException) {
                Log.e(TAG, "Error during API request: ${ex.message}")
                delay(10 * 1000)
                return@withContext getURL(context, url)
            }

        }
    }

    suspend fun clearSavedCookies(context: Context) {
        Log.d("ClearCookies", "Clearing saved cookies")
        context.cookieDataStore.edit { prefs ->
            prefs.clear()
        }

        // Also clear WebView cookies
        // CookieManager.getInstance().removeAllCookies(null)
        // Log.d("ClearCookies", "Cleared all cookies")
    }

    suspend fun fetchUserInfo(context: Context) {
        val response = getURL(context, "https://www.eesti.ee/api/xroad/v2/rr/kodanik/info")
        val body: String = response.second
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

    suspend fun logOut(context: Context) {
        AlarmScheduler.cancelRefresh(context)
        clearSavedCookies(context)
        showLogoutNotification(context)
    }




}