package com.lianyu.ai.database.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * 扩展字段（ext_json）统一读写助手。
 *
 * 所有业务实体表均包含 `ext_json` 列（TEXT NOT NULL DEFAULT '{}'），
 * 用于吸收未来功能扩展所需的任意键值对，避免 ALTER TABLE 与数据库版本升级。
 *
 * 固定数据库环境（v22 锁定）后，新功能所需的实体属性应优先存入 ext_json，
 * 仅在确有必要且经评审后才允许新增列（此时必须升级版本并补充迁移脚本）。
 *
 * 使用示例：
 * ```kotlin
 * // 读取
 * val tag: String? = companion.extJson.readExt("customTag")
 * val flags: List<String>? = companion.extJson.readExt("flags")
 *
 * // 写入（返回新的 JSON 字符串，配合 data class copy 使用）
 * val newExt = companion.extJson.writeExt("customTag", "value")
 * companion.copy(extJson = newExt)
 *
 * // 移除
 * val cleared = companion.extJson.removeExt("customTag")
 *
 * // 判断是否存在
 * if (companion.extJson.hasExt("customTag")) { ... }
 * ```
 */
@PublishedApi
internal val extJsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 读取扩展字段，缺失或解析失败时返回 null。 */
inline fun <reified T> String.readExt(key: String): T? {
    if (isBlank() || this == "{}") return null
    return runCatching {
        val obj = extJsonParser.decodeFromString<JsonObject>(this)
        val element = obj[key] ?: return null
        extJsonParser.decodeFromJsonElement(serializer<T>(), element)
    }.getOrNull()
}

/** 写入扩展字段，返回更新后的 JSON 字符串。 */
inline fun <reified T> String.writeExt(key: String, value: T): String {
    val obj = if (isBlank() || this == "{}") {
        JsonObject(emptyMap())
    } else {
        runCatching { extJsonParser.decodeFromString<JsonObject>(this) }
            .getOrDefault(JsonObject(emptyMap()))
    }
    val newElement = extJsonParser.encodeToJsonElement(serializer(), value)
    val mutable = obj.toMutableMap()
    mutable[key] = newElement
    return extJsonParser.encodeToString(JsonObject(mutable))
}

/** 移除指定扩展字段，返回更新后的 JSON 字符串。 */
fun String.removeExt(key: String): String {
    if (isBlank() || this == "{}") return this
    return runCatching {
        val obj = extJsonParser.decodeFromString<JsonObject>(this)
        val mutable = obj.toMutableMap()
        mutable.remove(key)
        extJsonParser.encodeToString(JsonObject(mutable))
    }.getOrDefault(this)
}

/** 判断是否包含指定扩展字段。 */
fun String.hasExt(key: String): Boolean {
    if (isBlank() || this == "{}") return false
    return runCatching {
        val obj = extJsonParser.decodeFromString<JsonObject>(this)
        obj.containsKey(key)
    }.getOrDefault(false)
}
