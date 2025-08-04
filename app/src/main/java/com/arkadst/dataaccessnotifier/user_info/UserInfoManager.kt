package com.arkadst.dataaccessnotifier.user_info

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import com.arkadst.dataaccessnotifier.userInfoDataStore
import com.arkadst.dataaccessnotifier.UserInfoProto
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlin.io.encoding.Base64

@Serializable
data class UserInfoJson(
    val personalCode: String = "",
    val firstName: String = ""
)

object UserInfoManager {

    private val jsonHandler = Json{ignoreUnknownKeys = true}
    suspend fun extractUserInfo(context: Context) {
        val cookieManager: CookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie("https://www.eesti.ee/")

        val token = extractJwtToken(cookieString)

        val chunks = token!!.split(".")
        val payload = Base64.Default.decode(chunks[1]).decodeToString()
        parseAndSaveUserInfo(context, parseToJsonElement(payload))
        setFirstUse(context)
    }

    /**
     * Extract JWT token from cookie string
     */
    private fun extractJwtToken(cookieString: String?): String? {
        if (cookieString.isNullOrEmpty()) {
            return null
        }

        return cookieString.split(";")
            .map { it.trim() }
            .find { it.startsWith("JWTTOKEN=") }
            ?.substringAfter("JWTTOKEN=")
    }

    /**
     * Parse user info from JsonElement and update the proto data store
     */
    private suspend fun parseAndSaveUserInfo(context: Context, jsonElement: JsonElement) {
        try {
            val userInfoJson = jsonHandler.decodeFromJsonElement(serializer<UserInfoJson>(), jsonElement)
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
            val cleanPersonalCode = userInfoJson.personalCode.removePrefix("EE")
            if (cleanPersonalCode.isNotEmpty()) {
                builder.personalCode = cleanPersonalCode
                Log.d("UserInfoManager", "Saved personal code: $cleanPersonalCode")
            } else {
                Log.w("UserInfoManager", "Personal code not found in response")
            }

            // Clean and process first name
            val cleanFirstName = userInfoJson.firstName
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
     * Set first use flag to true if unset.
     */
    suspend fun setFirstUse(context: Context) {
        context.userInfoDataStore.updateData { currentUserInfo ->
            if (currentUserInfo.hasFirstUse()) {
                Log.d("UserInfoManager", "First use already set, skipping update")
                return@updateData currentUserInfo
            }
            currentUserInfo.toBuilder()
                .setFirstUse(true)
                .build()
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
}
