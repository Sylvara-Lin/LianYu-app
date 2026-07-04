package com.lianyu.ai.database

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.RoleProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 持久化每个角色类型的独立人设快照。
 *
 * 切换角色时，当前角色的自定义设定会被保存；目标角色的设定会被恢复，
 * 从而保证两种角色都有连贯、独立的交互体验，同时不丢失聊天记录。
 */
class RolePresetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 保存某角色类型的预设。
     */
    fun savePreset(role: CompanionRole, profile: RoleProfile) {
        prefs.edit { putString(keyFor(role), json.encodeToString(profile)) }
    }

    /**
     * 读取某角色类型的预设；若用户未自定义过，返回默认预设。
     */
    fun getPreset(role: CompanionRole): RoleProfile {
        val raw = prefs.getString(keyFor(role), null) ?: return RolePresets.defaultFor(role)
        return runCatching { json.decodeFromString<RoleProfile>(raw) }.getOrNull()
            ?: RolePresets.defaultFor(role)
    }

    /**
     * 将当前伴侣实体的字段保存为某角色类型的快照。
     *
     * 注意：为保持 susu 数据库版本 v19 不变，角色专属字段（身材/职业/性格标签）
     * 不写入 [CompanionEntity]，而是保留在 SharedPreferences 的 RolePreset 中。
     * 快照只保存伴侣表中实际存在的字段，角色预设的独立字段保持原值。
     */
    fun snapshotFromCompanion(role: CompanionRole, companion: CompanionEntity) {
        val existingPreset = getPreset(role)
        val profile = RoleProfile(
            role = role,
            name = companion.name,
            age = companion.age,
            personality = companion.personality,
            backstory = companion.backstory,
            speakingStyle = companion.speakingStyle,
            rawPrompt = companion.rawPrompt,
            systemPrompt = companion.systemPrompt,
            tags = companion.tags,
            bodyType = existingPreset.bodyType,
            profession = existingPreset.profession,
            personalityTags = existingPreset.personalityTags
        )
        savePreset(role, profile)
    }

    companion object {
        private const val PREFS_NAME = "role_presets"
        private fun keyFor(role: CompanionRole) = "preset_${role.name.lowercase()}"
    }
}
