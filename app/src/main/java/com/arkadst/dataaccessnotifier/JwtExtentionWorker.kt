package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arkadst.dataaccessnotifier.Utils.Companion.clearSavedCookies
import com.arkadst.dataaccessnotifier.Utils.Companion.getURL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.TimeUnit
import kotlin.collections.filterNot

private const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
private const val TAG = "JwtExtentionWorker"
private const val DATA_TRACKER_API_URL = "https://www.eesti.ee/andmejalgija/api/v1/usages?dataSystemCodes=digiregistratuur&dataSystemCodes=elamislubade_ja_toolubade_register&dataSystemCodes=kinnistusraamat&dataSystemCodes=kutseregister&dataSystemCodes=maksukohustuslaste_register&dataSystemCodes=infosusteem_polis&dataSystemCodes=politsei_taktikalise_juhtimise_andmekogu&dataSystemCodes=pollumajandusloomade_register&dataSystemCodes=pollumajandustoetuste_ja_pollumassiivide_register&dataSystemCodes=rahvastikuregister&dataSystemCodes=retseptikeskus&dataSystemCodes=sotsiaalkaitse_infosusteem&dataSystemCodes=sotsiaalteenuste_ja_toetuste_register&dataSystemCodes=tooinspektsiooni_tooelu_infosusteem&dataSystemCodes=tootuskindlustuse_andmekogu"

class JwtExtentionWorker (appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams){

        override suspend fun doWork(): Result {
            Log.d(TAG, "Starting JWT extension work")
            return (extendJwtSession() && pollDataTracker()).let { success ->
                if (success) {
                    Log.d(TAG, "JWT extension and data tracker polling completed successfully")
                    Log.d(TAG, "Scheduling next work")
                    scheduleNextWork()
                    Result.success()
                } else {
                    Log.e(TAG, "JWT extension or data tracker polling failed")
                    Result.failure()
                }
            }
    }

    private suspend fun extendJwtSession() : Boolean {
        val response = getURL(applicationContext, JWT_EXTEND_URL)
        if (response.code != 200) {
            Log.e(TAG, "JWT extension failed: ${response.code}")
            clearSavedCookies(applicationContext)
            return false
        } else {
            Log.d(TAG, "JWT session extended successfully.")
            return true
        }
    }

    private suspend fun pollDataTracker() : Boolean {

        val response = getURL(applicationContext, DATA_TRACKER_API_URL)
        if (response.code == 200) {
            response.body.string().let { body ->
                Log.d(TAG, "Data tracker response: $body")
                val entries = parseDataTrackerResponseBody(body)
                Log.d(TAG, "Parsed entries: $entries")
                handleParsedEntries(entries)
            }
            return true
        } else {
            Log.e(TAG, "Data tracker API call failed: ${response.code}")
            return false
        }

    }

    private suspend fun parseDataTrackerResponseBody(body: String) : Map<Int, String> {
        val returnMap = mutableMapOf<Int, String>()
        // Implement your parsing logic here
        parseToJsonElement(body).let { jsonElement : JsonElement ->
            jsonElement.jsonObject["findUsageResponses"]?.let { entries ->
                if (entries is JsonArray) {
                    entries.filterNot { entry ->
                        val personalCode : String = applicationContext.userInfoDataStore.data.first().asMap()[PERSONAL_CODE_KEY]?.toString()!!
                        val receiver = entry.jsonObject["receiver"]?.toString()
                        receiver?.contains(personalCode) == true
                    }.forEach { entry ->
                        returnMap.put(entry.toString().hashCode(), entry.toString())
                    }
                } else {
                    Log.e(TAG, "Expected JsonArray but got: $entries")
                }
            }
        }
        return returnMap
    }

    private suspend fun handleParsedEntries(entries: Map<Int, String>) {

    }

    private fun scheduleNextWork() {
        val workRequest = OneTimeWorkRequestBuilder<JwtExtentionWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setConstraints(workerConstraints)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                JWT_WORKER_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
    }
}