package com.arkadst.dataaccessnotifier

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object LoginStateRepository {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            context.cookieDataStore.data
                .map { prefs -> prefs.asMap().isNotEmpty() }
                .collect { loggedIn -> _isLoggedIn.value = loggedIn }
        }
    }
}