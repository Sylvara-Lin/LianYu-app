package com.lianyu.ai.database.model

import com.lianyu.ai.common.CompanionRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一个角色类型（女友/男友）对应的完整默认人设。
 *
 * 与 [CompanionEntity] 解耦，便于在切换角色时独立保存/恢复不同角色的设定，
 * 同时不污染单条聊天记录的 companionId，保证聊天记录连续。
 */
@Serializable
@SerialName("RP")
data class RoleProfile(
    val role: CompanionRole = CompanionRole.GIRLFRIEND,
    val name: String,
    val age: Int? = null,
    val personality: String,
    val backstory: String? = null,
    val speakingStyle: String? = null,
    val rawPrompt: String? = null,
    val systemPrompt: String? = null,
    val tags: String? = null,
    val bodyType: String? = null,
    val profession: String? = null,
    val personalityTags: String? = null
) {
    /**
     * 将本角色预设应用到已有伴侣实体，保留 id、亲密度、创建时间等用户数据。
     *
     * 注意：角色类型与角色专属字段（身材/职业/性格标签）不写入 [CompanionEntity]，
     * 以保持 susu 数据库版本 v19 不变；它们由 [com.lianyu.ai.database.RolePresetStore]
     * 与 [com.lianyu.ai.database.repository.UserRepository] 单独维护。
     */
    fun applyTo(companion: CompanionEntity): CompanionEntity = companion.copy(
        name = name,
        age = age,
        personality = personality,
        backstory = backstory,
        speakingStyle = speakingStyle,
        rawPrompt = rawPrompt ?: personality,
        systemPrompt = systemPrompt,
        tags = tags,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * 以本预设创建新的默认伴侣实体。
     *
     * 注意：角色类型与角色专属字段（身材/职业/性格标签）不写入 [CompanionEntity]，
     * 以保持 susu 数据库版本 v19 不变。
     */
    fun createCompanion(now: Long = System.currentTimeMillis()): CompanionEntity = CompanionEntity(
        name = name,
        avatarUrl = null,
        age = age,
        personality = personality,
        backstory = backstory,
        speakingStyle = speakingStyle,
        tags = tags,
        rawPrompt = rawPrompt ?: personality,
        systemPrompt = systemPrompt,
        createdAt = now,
        updatedAt = now
    )
}
