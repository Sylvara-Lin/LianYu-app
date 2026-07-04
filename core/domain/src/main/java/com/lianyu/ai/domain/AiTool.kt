package com.lianyu.ai.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * 可被 AI 调用的工具契约。
 *
 * 实现类负责：
 * - 声明工具名、描述、参数 JSON Schema（OpenAI function calling 格式）
 * - 执行工具并返回结果 JSON 字符串
 *
 * 属于 core:domain 零依赖模块，不引入任何 feature 数据类。
 */
interface AiTool {
    /** 工具名（全局唯一，建议用 `<模块>_<动作>` 前缀，如 `luckin_query_shops`） */
    val name: String
    /** 工具描述，供 AI 理解何时调用此工具 */
    val description: String
    /** OpenAI function parameters JSON Schema 字符串 */
    val parametersJsonSchema: String
    /**
     * 执行工具。
     * @param argumentsJson AI 传入的参数 JSON 字符串
     * @return 结果 JSON 字符串（会作为 tool message 回传给 AI）
     */
    suspend fun execute(argumentsJson: String): String
}

/**
 * 工具注册中心。
 *
 * object 单例，不依赖任何 feature 模块。
 * feature 模块在 app 启动时把自己的 AiTool 实现注册进来，
 * ChatViewModel 通过 [all] 拿到工具列表传给 AI。
 */
object ToolRegistry {
    private val tools = ConcurrentHashMap<String, AiTool>()

    /** 注册工具（同名覆盖） */
    fun register(tool: AiTool) {
        tools[tool.name] = tool
    }

    /** 注销工具 */
    fun unregister(name: String) {
        tools.remove(name)
    }

    /** 按名查找工具 */
    fun get(name: String): AiTool? = tools[name]

    /** 全部已注册工具 */
    fun all(): List<AiTool> = tools.values.toList()

    /** 是否有已注册工具 */
    fun isNotEmpty(): Boolean = tools.isNotEmpty()

    /**
     * 序列化为 OpenAI tools 数组 JSON 字符串。
     * 格式：[{"type":"function","function":{"name","description","parameters":{...}}}]
     */
    fun toolDefinitionsJson(): String {
        if (tools.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        tools.values.forEachIndexed { index, tool ->
            if (index > 0) sb.append(",")
            sb.append("{\"type\":\"function\",\"function\":{")
            sb.append("\"name\":\"").append(escapeJson(tool.name)).append("\",")
            sb.append("\"description\":\"").append(escapeJson(tool.description)).append("\",")
            sb.append("\"parameters\":").append(tool.parametersJsonSchema)
            sb.append("}}")
        }
        sb.append("]")
        return sb.toString()
    }

    /** 清空全部工具（测试 / 重置用） */
    fun clear() {
        tools.clear()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
