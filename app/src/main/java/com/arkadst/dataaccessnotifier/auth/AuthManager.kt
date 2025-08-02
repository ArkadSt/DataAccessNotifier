package com.arkadst.dataaccessnotifier.auth

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.arkadst.dataaccessnotifier.NotificationManager.showLogoutNotification
import com.arkadst.dataaccessnotifier.alarm.AlarmScheduler
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager

object AuthManager {
    suspend fun logOut(context: Context) {
        UserInfoManager.setLoggedIn(context, false)
        AlarmScheduler.cancelRefresh(context)
        CookieManager.getInstance().removeAllCookies(null)
        LoginStateRepository.setLoggedIn(context, false)
        showLogoutNotification(context)
    }
}