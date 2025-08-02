package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
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
import com.arkadst.dataaccessnotifier.access_logs.AccessLogsSerializer
import com.arkadst.dataaccessnotifier.auth.SessionManagementCookieJar
import com.arkadst.dataaccessnotifier.user_info.UserInfoSerializer
import java.util.concurrent.TimeUnit

const val RETRIES_KEY = "retries"
private const val COOKIE_PREFS = "auth_cookies"

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
                    .build()

                client.newCall(request).execute().use { response ->
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

                    return@withContext returnValue
                }

            } catch (ex: IOException) {
                Log.e(TAG, "Error during API request: ${ex.message}")
                delay(TimeUnit.SECONDS.toMillis(10))
                return@withContext getURL(context, url)
            }

        }
    }

}