package com.arkadst.dataaccessnotifier

// LoginStateRepository.kt
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LoginStateRepository {
    private lateinit var prefs: SharedPreferences
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        Log.d("LoginStateRepository", "Preference changed")
        _isLoggedIn.value = prefs.all.isNotEmpty()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("auth_cookies", Context.MODE_PRIVATE)
        _isLoggedIn.value = prefs.all.isNotEmpty()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
}