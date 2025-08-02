package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
import com.arkadst.dataaccessnotifier.user_info.UserInfoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "LoginStateRepository"
object LoginStateRepository {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            context.userInfoDataStore.data
                .map { userInfo ->
                    val isLoggedIn = userInfo.loggedIn
                    Log.d(TAG, "Logged in state: $isLoggedIn")
                    isLoggedIn
                }.collect { loggedIn ->
                    Log.d(TAG, "Setting logged in to: $loggedIn")
                    _isLoggedIn.value = loggedIn
                }
        }
    }

    suspend fun setLoggedIn(context: Context, isLoggedIn: Boolean) {
        Log.d(TAG, "Setting logged in to: $isLoggedIn")
        UserInfoManager.setLoggedIn(context, isLoggedIn)
    }
}