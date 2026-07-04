package com.lianyu.ai.database

import android.content.Context
import com.lianyu.ai.database.dao.CompanionDao
import com.lianyu.ai.database.model.CompanionEntity

object DefaultCompanionSeeder {
    private const val MASK = 73
    private const val PREFS_NAME = "default_companion"
    private const val KEY_DELETED_BY_USER = "deleted_by_user"

    val defaultExperienceCompanionTag: String
        get() = reveal(TAG)

    private val defaultExperienceCompanionName: String
        get() = reveal(NAME)

    private val defaultExperienceCompanionPersona: String
        get() = reveal(PERSONA)

    private val defaultExperienceCompanionBackstory: String
        get() = reveal(BACKSTORY)

    private val defaultExperienceCompanionSpeakingStyle: String
        get() = reveal(SPEAKING_STYLE)

    private val defaultExperienceCompanionTags: String
        get() = reveal(TAGS)

    /**
     * 检查默认伴侣是否被用户删除，若未删除则确保存在。
     * 封装 SharedPreferences 检查和 DAO 操作，供 app 模块调用。
     *
     * 注意：必须在后台协程中调用，禁止在主线程同步执行数据库 IO。
     */
    suspend fun seedIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DELETED_BY_USER, false)) return
        runCatching {
            val db = AppDatabase.getDatabase(context.applicationContext)
            ensureDefaultTestCompanion(db.companionDao())
        }
    }

    fun createDefaultTestCompanion(now: Long = System.currentTimeMillis()): CompanionEntity {
        val persona = defaultExperienceCompanionPersona
        return CompanionEntity(
            name = defaultExperienceCompanionName,
            age = 22,
            personality = persona,
            backstory = defaultExperienceCompanionBackstory,
            speakingStyle = defaultExperienceCompanionSpeakingStyle,
            tags = defaultExperienceCompanionTags,
            rawPrompt = persona,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun ensureDefaultTestCompanion(companionDao: CompanionDao): Long? {
        val companions = companionDao.getAllCompanionsSync()
        companions.firstOrNull { it.isLegacyDefaultTestCompanion() }?.let { legacy ->
            companionDao.updateCompanion(createDefaultTestCompanion(now = legacy.createdAt).copy(id = legacy.id))
            return null
        }

        if (companions.any { it.isDefaultExperienceCompanion() }) {
            return null
        }

        return companionDao.insertCompanion(createDefaultTestCompanion())
    }

    private fun CompanionEntity.isDefaultExperienceCompanion(): Boolean {
        return name == defaultExperienceCompanionName ||
            tags.orEmpty()
                .split(',')
                .map { it.trim() }
                .any { it == defaultExperienceCompanionTag || it == LEGACY_TAG }
    }

    private fun CompanionEntity.isLegacyDefaultTestCompanion(): Boolean {
        return name == LEGACY_NAME ||
            tags.orEmpty()
                .split(',')
                .map { it.trim() }
                .any { it == LEGACY_TAG }
    }

    private fun reveal(data: IntArray): String {
        return data
            .map { (it xor MASK).toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
    }

    private val NAME = intArrayOf(172, 249, 198, 160, 248, 245)
    private val TAG = intArrayOf(45, 44, 47, 40, 60, 37, 61, 100, 44, 49, 57, 44, 59, 32, 44, 39, 42, 44, 100, 42, 38, 36, 57, 40, 39, 32, 38, 39)
    private val TAGS = intArrayOf(173, 244, 218, 160, 227, 197, 101, 160, 242, 209, 161, 231, 237, 101, 45, 44, 47, 40, 60, 37, 61, 100, 44, 49, 57, 44, 59, 32, 44, 39, 42, 44, 100, 42, 38, 36, 57, 40, 39, 32, 38, 39)
    private val PERSONA = intArrayOf(172, 249, 198, 160, 248, 245, 166, 245, 197, 123, 123, 172, 251, 200, 166, 245, 197, 172, 237, 223, 172, 217, 216, 172, 198, 193, 175, 213, 192, 174, 203, 240, 172, 209, 253, 174, 232, 229, 174, 211, 205, 173, 244, 218, 160, 227, 197, 161, 238, 219, 161, 192, 251, 170, 201, 203, 172, 223, 213, 175, 229, 235, 174, 221, 225, 161, 244, 242, 175, 212, 247, 161, 206, 227, 174, 205, 255, 174, 211, 205, 161, 230, 228, 175, 249, 221, 161, 200, 195, 172, 237, 224, 166, 245, 197, 173, 245, 211, 173, 241, 242, 172, 195, 225, 175, 199, 236, 161, 230, 212, 166, 245, 197, 172, 200, 255, 172, 249, 221, 172, 217, 217, 175, 238, 244, 166, 245, 197, 173, 244, 207, 160, 200, 206, 172, 193, 249, 175, 228, 234, 174, 242, 198, 160, 222, 231, 160, 235, 209, 173, 245, 211, 161, 231, 237, 174, 213, 214, 172, 210, 215, 172, 243, 221, 170, 201, 203, 172, 236, 240, 175, 209, 230, 173, 241, 243, 173, 243, 207, 172, 241, 231, 172, 195, 224, 175, 223, 249, 174, 221, 225, 175, 193, 254, 172, 246, 226, 160, 201, 214, 173, 244, 218, 160, 227, 197, 161, 200, 195, 172, 237, 224, 170, 201, 200, 172, 247, 231, 173, 246, 232, 161, 244, 229, 172, 198, 216, 172, 219, 197, 161, 206, 227, 172, 195, 225, 172, 210, 215, 172, 237, 196, 161, 201, 197, 172, 206, 207, 172, 237, 206, 174, 211, 205, 160, 242, 209, 161, 231, 237, 173, 243, 243, 174, 192, 224, 170, 201, 203)
    private val BACKSTORY = intArrayOf(173, 241, 243, 173, 243, 207, 172, 241, 231, 172, 195, 224, 175, 223, 249, 174, 221, 225, 175, 193, 254, 172, 246, 226, 160, 201, 214, 173, 244, 218, 160, 227, 197, 175, 233, 241, 172, 246, 202, 172, 195, 214, 161, 202, 244, 161, 201, 197, 160, 235, 205, 174, 244, 231, 174, 211, 205, 173, 243, 243, 174, 192, 224, 166, 245, 197, 160, 201, 203, 172, 217, 193, 173, 244, 218, 160, 227, 197, 161, 200, 195, 172, 237, 224, 170, 201, 200, 160, 201, 211, 174, 214, 236, 170, 201, 200, 172, 247, 231, 173, 246, 232, 175, 255, 193, 175, 200, 230, 172, 219, 197, 161, 206, 227, 172, 195, 225, 172, 210, 215, 172, 237, 196, 170, 201, 203)
    private val SPEAKING_STYLE = intArrayOf(161, 244, 242, 175, 212, 247, 170, 201, 200, 174, 210, 253, 175, 199, 236, 170, 201, 200, 172, 241, 239, 173, 241, 201, 174, 203, 240, 173, 246, 198, 174, 211, 231, 175, 205, 214, 166, 245, 197, 172, 210, 215, 172, 237, 196, 172, 249, 244, 160, 206, 198, 172, 202, 198, 174, 213, 214, 173, 243, 243, 160, 211, 198, 175, 192, 194, 161, 200, 195, 172, 237, 224, 170, 201, 203)
    private const val LEGACY_NAME = "测试小鱼"
    const val LEGACY_TAG = "default-test-companion"
}
