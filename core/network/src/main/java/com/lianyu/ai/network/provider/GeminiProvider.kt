package com.lianyu.ai.network.provider

/**
 * Provider for Google Gemini API (via OpenAI-compatible endpoint).
 *
 * Gemini uses the OpenAI-compatible endpoint at:
 *   https://generativelanguage.googleapis.com/v1beta/openai/
 * All request/response formats are identical to OpenAI.
 */
class GeminiProvider : OpenAiCompatibleProvider()
