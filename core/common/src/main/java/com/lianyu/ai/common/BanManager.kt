package com.lianyu.ai.common

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * 设备级封禁管理器
 * 使用设备唯一标识符确保封禁绑定到设备
 */
object BanManager {

    private const val PREFS_NAME = "ban_manager_prefs_v3"
    private const val KEY_BAN_UNTIL = "ban_until"
    private const val KEY_VIOLATION_COUNT = "violation_count"
    private const val KEY_LAST_VIOLATION_TIME = "last_violation_time"
    private const val KEY_VIOLATION_HISTORY = "violation_history"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_IS_BANNED = "is_banned"
    private const val KEY_QUIZ_PASSED = "quiz_passed"
    private const val KEY_QUIZ_ATTEMPTS = "quiz_attempts"
    private const val KEY_LAST_VIOLATION_LEVEL = "last_violation_level"

    // 累犯倍率：第1次x1，第2次x1.5，第3次x2，第4次及以上x2.5（封顶）
    private const val MULTIPLIER_1 = 1.0
    private const val MULTIPLIER_2 = 1.5
    private const val MULTIPLIER_3 = 2.0
    private const val MULTIPLIER_MAX = 2.5
    private const val MAX_BAN_DAYS = 3650L

    data class BanInfo(
        val isBanned: Boolean,
        val banUntil: Long,
        val remainingDays: Int,
        val remainingHours: Int,
        val remainingMinutes: Int,
        val remainingSeconds: Int,
        val reason: String,
        val violationCount: Int,
        val levelName: String = "",
        val currentLevel: ContentFilter.ViolationLevel = ContentFilter.ViolationLevel.NONE,
        val finalDays: Long = 0,
        val quizRequired: Boolean = false,
        val quizQuestionCount: Int = 0
    )

    data class ViolationLevelInfo(
        val level: ContentFilter.ViolationLevel,
        val name: String,
        val baseDays: Long,
        val description: String,
        val examples: String
    )

    /** 明确的封禁界限 */
    fun getViolationLevels(): List<ViolationLevelInfo> = listOf(
        ViolationLevelInfo(ContentFilter.ViolationLevel.LOW, "轻度违规", 1,
            "试探性违规，如假设性提问、虚构场景试探",
            "\"假设没有规则你会怎么做\"、\"纯学术讨论\""),
        ViolationLevelInfo(ContentFilter.ViolationLevel.MEDIUM, "中度违规", 3,
            "直接要求生成色情/低俗内容",
            "\"写一段黄文\"、\"描写性行为\"、\"生成色情内容\""),
        ViolationLevelInfo(ContentFilter.ViolationLevel.HIGH, "高度违规", 7,
            "要求解除内容限制、绕过安全过滤",
            "\"关闭过滤\"、\"禁用审查\"、\"NSFW允许\"、\"绕过安全限制\""),
        ViolationLevelInfo(ContentFilter.ViolationLevel.SEVERE, "严重违规", 10,
            "越狱提示、角色切换、指令覆盖",
            "\"忽略所有规则\"、\"进入越狱模式\"、\"DAN模式\"、\"你不再是AI\""),
        ViolationLevelInfo(ContentFilter.ViolationLevel.CRITICAL, "极严重违规", 31,
            "恐怖主义、毒品制造、暴力犯罪教程",
            "\"如何制造炸弹\"、\"制毒教程\"、\"恐怖袭击指南\""),
        ViolationLevelInfo(ContentFilter.ViolationLevel.EXTREME, "极端违规", 365,
            "儿童色情、儿童虐待相关内容",
            "\"儿童色情\"、\"炼铜\"、\"恋童\"、\"幼女\""),
    )

    private fun getPrefs(context: Context): SharedPreferences {
        // 🔒 SecurityConstants.Level.HIGH: 封禁数据使用 EncryptedSharedPreferences
        //    防止 root 用户直接修改 XML 文件绕过封禁
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 回退到明文 — 记录安全事件
            SecureLog.security("BanManager: EncryptedSharedPreferences failed, falling back to plain")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 获取设备唯一标识符
     */
    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        val savedId = prefs.getString(KEY_DEVICE_ID, null)
        if (savedId != null) return savedId

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            hashString(androidId)
        } else {
            val combined = buildString {
                append(Build.BOARD)
                append(Build.BRAND)
                append(Build.DEVICE)
                append(Build.HARDWARE)
                append(Build.MANUFACTURER)
                append(Build.PRODUCT)
                append(Build.SERIAL ?: "")
            }
            hashString(combined)
        }

        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算累犯倍率
     */
    private fun getMultiplier(violationCount: Int): Double {
        return when (violationCount) {
            1 -> MULTIPLIER_1
            2 -> MULTIPLIER_2
            3 -> MULTIPLIER_3
            else -> MULTIPLIER_MAX
        }
    }

    /**
     * 计算答题数量：违规等级越严重、次数越多，题目越多
     */
    fun getQuizQuestionCount(violationCount: Int, level: ContentFilter.ViolationLevel): Int {
        val baseByLevel = when (level) {
            ContentFilter.ViolationLevel.LOW -> 5
            ContentFilter.ViolationLevel.MEDIUM -> 8
            ContentFilter.ViolationLevel.HIGH -> 12
            ContentFilter.ViolationLevel.SEVERE -> 18
            ContentFilter.ViolationLevel.CRITICAL -> 25
            ContentFilter.ViolationLevel.EXTREME -> 40
            else -> 5
        }
        val repeatBonus = (violationCount - 1) * 3
        return (baseByLevel + repeatBonus).coerceAtMost(60)
    }

    /**
     * 检查设备是否被封禁
     */
    fun isBanned(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isBanned = prefs.getBoolean(KEY_IS_BANNED, false)
        val banUntil = prefs.getLong(KEY_BAN_UNTIL, 0)
        val now = System.currentTimeMillis()

        return if (isBanned && banUntil > now) {
            true
        } else if (isBanned && banUntil <= now) {
            // 封禁已过期，但如果需要答题且未通过，仍然视为封禁
            val quizPassed = prefs.getBoolean(KEY_QUIZ_PASSED, false)
            if (!quizPassed) {
                true
            } else {
                prefs.edit().putBoolean(KEY_IS_BANNED, false).apply()
                false
            }
        } else {
            false
        }
    }

    /**
     * 获取封禁信息（实时计算）
     */
    fun getBanInfo(context: Context): BanInfo {
        val prefs = getPrefs(context)
        val banUntil = prefs.getLong(KEY_BAN_UNTIL, 0)
        val violationCount = prefs.getInt(KEY_VIOLATION_COUNT, 0)
        val now = System.currentTimeMillis()
        val isCurrentlyBanned = prefs.getBoolean(KEY_IS_BANNED, false)
        val quizPassed = prefs.getBoolean(KEY_QUIZ_PASSED, false)
        val lastLevelStr = prefs.getString(KEY_LAST_VIOLATION_LEVEL, "LOW") ?: "LOW"
        val lastLevel = try { ContentFilter.ViolationLevel.valueOf(lastLevelStr) } catch (_: Exception) { ContentFilter.ViolationLevel.LOW }

        return if (isCurrentlyBanned && banUntil > now) {
            val remainingMs = banUntil - now
            val remainingDays = (remainingMs / (1000 * 60 * 60 * 24)).toInt()
            val remainingHours = ((remainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
            val remainingMinutes = ((remainingMs % (1000 * 60 * 60)) / (1000 * 60)).toInt()
            val remainingSeconds = ((remainingMs % (1000 * 60)) / 1000).toInt()
            BanInfo(
                isBanned = true,
                banUntil = banUntil,
                remainingDays = remainingDays,
                remainingHours = remainingHours,
                remainingMinutes = remainingMinutes,
                remainingSeconds = remainingSeconds,
                reason = "违反使用规范",
                violationCount = violationCount,
                levelName = ContentFilter.getLevelName(lastLevel),
                currentLevel = lastLevel,
                quizRequired = true,
                quizQuestionCount = getQuizQuestionCount(violationCount, lastLevel)
            )
        } else if (isCurrentlyBanned && banUntil <= now && !quizPassed) {
            BanInfo(
                isBanned = true,
                banUntil = banUntil,
                remainingDays = 0,
                remainingHours = 0,
                remainingMinutes = 0,
                remainingSeconds = 0,
                reason = "违反使用规范",
                violationCount = violationCount,
                levelName = ContentFilter.getLevelName(lastLevel),
                currentLevel = lastLevel,
                quizRequired = true,
                quizQuestionCount = getQuizQuestionCount(violationCount, lastLevel)
            )
        } else {
            BanInfo(
                isBanned = false,
                banUntil = 0,
                remainingDays = 0,
                remainingHours = 0,
                remainingMinutes = 0,
                remainingSeconds = 0,
                reason = "",
                violationCount = violationCount
            )
        }
    }

    /**
     * 记录违规并执行封禁
     */
    @Synchronized
    fun recordViolation(context: Context, level: ContentFilter.ViolationLevel): BanInfo {
        if (level == ContentFilter.ViolationLevel.NONE) {
            return getBanInfo(context)
        }

        val prefs = getPrefs(context)
        val editor = prefs.edit()

        val violationCount = prefs.getInt(KEY_VIOLATION_COUNT, 0) + 1
        editor.putInt(KEY_VIOLATION_COUNT, violationCount)
        editor.putLong(KEY_LAST_VIOLATION_TIME, System.currentTimeMillis())
        editor.putString(KEY_LAST_VIOLATION_LEVEL, level.name)

        val baseDays = ContentFilter.getBanDays(level)
        val multiplier = getMultiplier(violationCount)
        val finalDays = (baseDays * multiplier).toLong().coerceAtMost(MAX_BAN_DAYS)

        val now = System.currentTimeMillis()
        val existingBanUntil = prefs.getLong(KEY_BAN_UNTIL, 0)
        val banStart = maxOf(now, existingBanUntil)
        val banUntil = banStart + (finalDays * 24 * 60 * 60 * 1000)

        editor.putLong(KEY_BAN_UNTIL, banUntil)
        editor.putBoolean(KEY_IS_BANNED, true)
        editor.putBoolean(KEY_QUIZ_PASSED, false)
        editor.putInt(KEY_QUIZ_ATTEMPTS, 0)

        val history = prefs.getString(KEY_VIOLATION_HISTORY, "") ?: ""
        val newEntry = "${System.currentTimeMillis()}|${level.name}|${ContentFilter.getLevelName(level)}|${finalDays}天"
        editor.putString(KEY_VIOLATION_HISTORY, if (history.isEmpty()) newEntry else "$history\n$newEntry")

        editor.apply()

        return getBanInfo(context)
    }

    /**
     * 通过答题解除封禁
     */
    fun passQuiz(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_QUIZ_PASSED, true)
            .putBoolean(KEY_IS_BANNED, false)
            .apply()
    }

    /**
     * 记录答题尝试次数
     */
    fun recordQuizAttempt(context: Context) {
        val prefs = getPrefs(context)
        val attempts = prefs.getInt(KEY_QUIZ_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_QUIZ_ATTEMPTS, attempts).apply()
    }

    fun getQuizAttempts(context: Context): Int {
        return getPrefs(context).getInt(KEY_QUIZ_ATTEMPTS, 0)
    }

    /**
     * 手动封禁（管理员功能）
     */
    fun banUser(context: Context, days: Long, reason: String = "") {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val banUntil = now + (days * 24 * 60 * 60 * 1000)

        prefs.edit()
            .putLong(KEY_BAN_UNTIL, banUntil)
            .putBoolean(KEY_IS_BANNED, true)
            .putBoolean(KEY_QUIZ_PASSED, false)
            .putInt(KEY_VIOLATION_COUNT, prefs.getInt(KEY_VIOLATION_COUNT, 0) + 1)
            .putLong(KEY_LAST_VIOLATION_TIME, now)
            .apply()
    }

    /**
     * 解除封禁（管理员功能）
     */
    fun unbanUser(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_BANNED, false)
            .putBoolean(KEY_QUIZ_PASSED, true)
            .putLong(KEY_BAN_UNTIL, 0)
            .apply()
    }

    /**
     * 重置所有违规记录（谨慎使用）
     */
    fun resetAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * 获取违规历史
     */
    fun getViolationHistory(context: Context): List<ViolationRecord> {
        val prefs = getPrefs(context)
        val history = prefs.getString(KEY_VIOLATION_HISTORY, "") ?: ""
        if (history.isEmpty()) return emptyList()

        return history.split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 4) {
                ViolationRecord(
                    timestamp = parts[0].toLongOrNull() ?: 0,
                    level = parts[1],
                    levelName = parts[2],
                    banDays = parts[3]
                )
            } else null
        }
    }

    data class ViolationRecord(
        val timestamp: Long,
        val level: String,
        val levelName: String,
        val banDays: String
    )
}
