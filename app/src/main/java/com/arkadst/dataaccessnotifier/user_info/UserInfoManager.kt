package com.arkadst.dataaccessnotifier.user_info

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import com.arkadst.dataaccessnotifier.userInfoDataStore
import com.arkadst.dataaccessnotifier.UserInfoProto
import com.arkadst.dataaccessnotifier.Utils.getURL
import kotlinx.serialization.json.Json.Default.parseToJsonElement

@Serializable
data class UserInfoJson(
    val personalCode: String = "",
    val firstName: String = ""
)

object UserInfoManager {

    suspend fun fetchUserInfo(context: Context) {
        try {
            val response = getURL(context, "https://www.eesti.ee/api/xroad/v2/rr/kodanik/info")
            val body: String = response.second
            Log.d("UserInfo", "Response Body: $body")

            parseAndSaveUserInfo(context, parseToJsonElement(body))
            setFirstUse(context, true)
        } catch (e: Exception) {
            Log.e("UserInfo", "Failed to fetch user info: ${e.message}", e)
        }
    }

    /**
     * Parse user info from JsonElement and update the proto data store
     */
    suspend fun parseAndSaveUserInfo(context: Context, jsonElement: JsonElement) {
        try {
            val userInfoJson = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(serializer<UserInfoJson>(), jsonElement)
            saveUserInfo(context, userInfoJson)
        } catch (e: Exception) {
            Log.e("UserInfoManager", "Failed to parse user info JsonElement: ${e.message}", e)
        }
    }

    /**
     * Save user info to proto data store
     */
    private suspend fun saveUserInfo(context: Context, userInfoJson: UserInfoJson) {
        context.userInfoDataStore.updateData { currentUserInfo ->
            val builder = currentUserInfo.toBuilder()

            // Clean and process personal code
            val cleanPersonalCode = userInfoJson.personalCode.cleanJsonString().removePrefix("EE")
            if (cleanPersonalCode.isNotEmpty()) {
                builder.personalCode = cleanPersonalCode
                Log.d("UserInfoManager", "Saved personal code: $cleanPersonalCode")
            } else {
                Log.w("UserInfoManager", "Personal code not found in response")
            }

            // Clean and process first name
            val cleanFirstName = userInfoJson.firstName.cleanJsonString()
            if (cleanFirstName.isNotEmpty()) {
                builder.firstName = cleanFirstName
                Log.d("UserInfoManager", "Saved first name: $cleanFirstName")
            } else {
                Log.w("UserInfoManager", "First name not found in response")
            }

            builder.build()
        }
    }

    /**
     * Set first use flag
     */
    suspend fun setFirstUse(context: Context, isFirstUse: Boolean) {
        context.userInfoDataStore.updateData { currentUserInfo ->
            currentUserInfo.toBuilder()
                .setFirstUse(isFirstUse)
                .build()
        }
    }

    /**
     * Set logged in status
     */
    suspend fun setLoggedIn(context: Context, isLoggedIn: Boolean) {
        context.userInfoDataStore.updateData { currentUserInfo ->
            currentUserInfo.toBuilder()
                .setLoggedIn(isLoggedIn)
                .build()
        }
    }

    /**
     * Check if this is the first use
     */
    suspend fun isFirstUse(context: Context): Boolean {
        return context.userInfoDataStore.data.first().firstUse
    }

    /**
     * Get the current user info
     */
    suspend fun getUserInfo(context: Context): UserInfoProto {
        return context.userInfoDataStore.data.first()
    }

    /**
     * Clean JSON string by removing quotes and trimming
     */
    private fun String.cleanJsonString(): String {
        return this.replace("\"", "").trim()
    }
}
