package com.lianyu.ai.feature.chat.data

import android.content.Context
import android.util.Log
import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.KeywordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object KeywordBridge {

    private const val TAG = "KeywordBridge"
    private val initialized = AtomicBoolean(false)

    suspend fun initialize(context: Context) {
        if (initialized.get()) return
        try {
            val allKeywords = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                db.keywordDao().getAllEnabled()
            }

            if (allKeywords.isEmpty()) {
                Log.w(TAG, "数据库无关键词数据，使用内置默认值")
                return
            }

            val grouped = allKeywords.groupBy { it.level }.mapValues { (_, entities) ->
                entities.map { entity ->
                    ContentFilter.KeywordData(
                        value = entity.pattern ?: entity.keyword,
                        isPattern = entity.pattern != null
                    )
                }
            }

            ContentFilter.injectKeywords(grouped)
            initialized.set(true)
            Log.i(TAG, "从加密数据库加载 ${allKeywords.size} 条关键词成功")
        } catch (e: Exception) {
            Log.e(TAG, "从DB加载关键词失败，使用内置默认值", e)
        }
    }

    fun reset() {
        ContentFilter.clearInjectedKeywords()
        initialized.set(false)
        Log.i(TAG, "已重置，回退到内置默认值")
    }

    fun isInitialized(): Boolean = initialized.get()
}
