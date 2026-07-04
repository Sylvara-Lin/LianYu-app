package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiResponse
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.AiTool
import com.lianyu.ai.domain.ToolRegistry
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AI 工具调用执行循环（从 ChatViewModel 抽取，方案B：独立类 + 委托存根）。
 *
 * 流程：AI 返回 tool_calls → 执行本地工具 → 把结果追加到 history → 重新调用 AI。
 * 最多 [maxRounds] 轮，防死循环。最后一轮若仍为 tool_calls，转为提示文本。
 *
 * 安全约束：createOrder 等涉及支付的工具需用户确认——当前实现直接执行
 * （createOrder 工具的 description 已告知 AI 先确认，且 AI 系统提示词注入了说明）。
 * 后续可在此拦截特定工具名做用户确认交互。
 *
 * @param aiService AI 服务网关，用于 [sendMessage] 调用
 */
class AiToolLoopRunner(private val aiService: AiServiceProvider) {

    suspend fun executeWithToolLoop(
        companionInfo: AiCompanionInfo,
        history: List<AiChatMessage>,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean,
        tools: List<AiTool>,
        maxRounds: Int = 3
    ): AiResponse {
        // 用可变列表承载 history，工具调用中间轮次追加消息但不入库
        val mutableHistory = history.toMutableList()
        var currentResponse = aiService.sendMessage(companionInfo, mutableHistory.toList(), stickerProbability, ntpTimeEnabled, tools)

        var round = 0
        while (!currentResponse.toolCalls.isNullOrEmpty() && round < maxRounds) {
            round++
            val activeToolCalls = currentResponse.toolCalls!!
            ChatDebugLog.log("[ToolLoop] round $round: ${activeToolCalls.size} calls")

            for (toolCall in activeToolCalls) {
                val tool = ToolRegistry.get(toolCall.name)
                val result = if (tool != null) {
                    runCatching {
                        withTimeoutOrNull(TimeoutBudgets.MCP_READ_MS) {
                            tool.execute(toolCall.arguments)
                        } ?: "工具执行超时"
                    }.getOrElse {
                        "工具执行失败: ${it.message}"
                    }
                } else {
                    "工具 ${toolCall.name} 不存在"
                }
                ChatDebugLog.log("[ToolLoop] ${toolCall.name} executed, resultLen=${result.length}")

                // AiChatMessage 不支持 role=tool，用 user 角色携带 "工具调用结果" 前缀让 AI 理解
                mutableHistory.add(
                    AiChatMessage(
                        isFromUser = false,
                        content = "[工具调用结果] ${toolCall.name}:\n$result",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // 重新调用 AI，让它基于工具结果生成回复
            currentResponse = aiService.sendMessage(
                companionInfo,
                mutableHistory.toList(),
                stickerProbability,
                ntpTimeEnabled,
                tools
            )
        }

        // 超过最大轮次仍是 tool_calls → 转为提示
        if (!currentResponse.toolCalls.isNullOrEmpty()) {
            ChatDebugLog.log("[ToolLoop] reached max rounds $maxRounds")
            return AiResponse(
                content = "我已经帮你处理了相关操作，但还有部分工具调用未能完成。你可以告诉我具体想做什么，我来帮你。",
                reasoningContent = null,
                toolCalls = null,
                finishReason = "stop"
            )
        }

        return currentResponse
    }
}
