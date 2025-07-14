package com.arkadst.dataaccessnotifier

import android.content.Context
import android.util.Log
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
            context.cookieDataStore.data
                .map { prefs ->
                    val isEmpty = prefs.asMap().isEmpty()
                    Log.d(TAG, "Cookies empty: $isEmpty")
                    !isEmpty
                }.collect { loggedIn ->
                    Log.d(TAG, "Setting logged in to: $loggedIn")
                    _isLoggedIn.value = loggedIn }
        }
    }
}