package com.lianyu.ai.common

/**
 * AI 伴侣的角色类型。
 *
 * 区分女友/男友两种基础角色，用于：
 * - UI 角色选择与展示
 * - 系统提示词中的语言风格、语气词、情感表达差异
 * - 默认人设（[com.lianyu.ai.database.RolePresets]）的切换
 */
enum class CompanionRole {
    GIRLFRIEND,
    BOYFRIEND;

    companion object {
        fun fromName(name: String?): CompanionRole = when (name?.uppercase()) {
            "BOYFRIEND" -> BOYFRIEND
            else -> GIRLFRIEND
        }
    }
}
