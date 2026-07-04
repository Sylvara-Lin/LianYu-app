package com.lianyu.ai.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.DefaultCompanionSeeder
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.CompanionRepository
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CompanionListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CompanionRepository
    val companions: Flow<List<CompanionEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CompanionRepository(database.companionDao())
        companions = repository.getAllCompanions()
    }

    fun deleteCompanion(companion: CompanionEntity) {
        viewModelScope.launch {
            // 如果删除的是默认测试伴侣（含新旧版 tag），标记用户已主动删除
            val isDefaultCompanion = companion.tags.orEmpty()
                .split(',')
                .map { it.trim() }
                .any { it == DefaultCompanionSeeder.LEGACY_TAG || it == DefaultCompanionSeeder.defaultExperienceCompanionTag }
            if (isDefaultCompanion) {
                getApplication<Application>()
                    .getSharedPreferences("default_companion", android.content.Context.MODE_PRIVATE)
                    .edit { putBoolean("deleted_by_user", true) }
            }
            repository.deleteCompanion(companion)
        }
    }
}
