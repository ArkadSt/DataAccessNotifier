package com.arkadst.dataaccessnotifier.auth

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.arkadst.dataaccessnotifier.NotificationManager.showLogoutNotification
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager

object AuthManager {
    fun clearSavedCookies(context: Context) {
        Log.d("ClearCookies", "Clearing saved cookies")
//        context.cookieDataStore.edit { prefs ->
//            prefs.clear()
//        }

        CookieManager.getInstance().removeAllCookies(null)
        Log.d("ClearCookies", "Cleared all cookies")
    }



    suspend fun logOut(context: Context) {
        UserInfoManager.setLoggedIn(context, false)
        AlarmScheduler.cancelRefresh(context)
        clearSavedCookies(context)
        LoginStateRepository.setLoggedIn(context, false)
        showLogoutNotification(context)
    }
}