package com.lianyu.ai.network.provider

/**
 * Provider for Alibaba DashScope (Tongyi Qwen) API.
 *
 * DashScope uses an OpenAI-compatible endpoint at:
 *   https://dashscope.aliyuncs.com/compatible-mode/v1/
 * All request/response formats are identical to OpenAI.
 */
class DashScopeProvider : OpenAiCompatibleProvider()
