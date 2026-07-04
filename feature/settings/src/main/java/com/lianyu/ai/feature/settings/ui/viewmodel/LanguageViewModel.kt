package com.lianyu.ai.feature.settings.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("language_prefs", Application.MODE_PRIVATE)

    private val _language = MutableStateFlow("zh-CN")
    val language: StateFlow<String> = _language

    init {
        _language.value = prefs.getString("language", "zh-CN") ?: "zh-CN"
    }

    fun setLanguage(code: String) {
        _language.value = code
        // [R18 FIX] commit() 同步写磁盘阻塞主线程，改用 apply() 异步写入
        prefs.edit().putString("language", code).apply()
    }

    fun applyLanguage(activity: android.app.Activity) {
        activity.recreate()
    }
}
