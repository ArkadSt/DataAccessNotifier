package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import okhttp3.Response
import com.arkadst.dataaccessnotifier.NotificationManager.showLogoutNotification
import com.arkadst.dataaccessnotifier.access_logs.AccessLogsSerializer
import com.arkadst.dataaccessnotifier.user_info.UserInfoSerializer
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import java.util.concurrent.TimeUnit

const val RETRIES_KEY = "retries"
private const val COOKIE_PREFS = "auth_cookies"
const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
const val DATA_TRACKER_API_URL =
    "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=digiregistratuur&dataSystemCodes=elamislubade_ja_toolubade_register&dataSystemCodes=kinnistusraamat&dataSystemCodes=kutseregister&dataSystemCodes=maksukohustuslaste_register&dataSystemCodes=infosusteem_polis&dataSystemCodes=politsei_taktikalise_juhtimise_andmekogu&dataSystemCodes=pollumajandusloomade_register&dataSystemCodes=pollumajandustoetuste_ja_pollumassiivide_register&dataSystemCodes=rahvastikuregister&dataSystemCodes=retseptikeskus&dataSystemCodes=sotsiaalkaitse_infosusteem&dataSystemCodes=sotsiaalteenuste_ja_toetuste_register&dataSystemCodes=tooinspektsiooni_tooelu_infosusteem&dataSystemCodes=tootuskindlustuse_andmekogu"

val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(name = COOKIE_PREFS)
val Context.userInfoDataStore: DataStore<UserInfoProto> by dataStore(
    fileName = "user_info.pb",
    serializer = UserInfoSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("DataStore", "UserInfo proto corruption detected, resetting to default")
        UserInfoProto.getDefaultInstance()
    }
)
val Context.accessLogsDataStore: DataStore<AccessLogsProto> by dataStore(
    fileName = "access_logs.pb",
    serializer = AccessLogsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("DataStore", "AccessLogs proto corruption detected, resetting to default")
        AccessLogsProto.getDefaultInstance()
    }
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
                delay(TimeUnit.SECONDS.toMillis(10))
                return@withContext getURL(context, url)
            }

        }
    }

     fun clearSavedCookies(context: Context) {
        Log.d("ClearCookies", "Clearing saved cookies")
//        context.cookieDataStore.edit { prefs ->
//            prefs.clear()
//        }

        CookieManager.getInstance().removeAllCookies(null)
        Log.d("ClearCookies", "Cleared all cookies")
    }

    suspend fun fetchUserInfo(context: Context) {
        try {
            val response = getURL(context, "https://www.eesti.ee/api/xroad/v2/rr/kodanik/info")
            val body: String = response.second
            Log.d("UserInfo", "Response Body: $body")

            UserInfoManager.parseAndSaveUserInfo(context, parseToJsonElement(body))
            UserInfoManager.setFirstUse(context, true)
        } catch (e: Exception) {
            Log.e("UserInfo", "Failed to fetch user info: ${e.message}", e)
        }
    }

    suspend fun logOut(context: Context) {
        UserInfoManager.setLoggedIn(context, false)
        AlarmScheduler.cancelRefresh(context)
        clearSavedCookies(context)
        LoginStateRepository.setLoggedIn(context, false)
        showLogoutNotification(context)
    }


}