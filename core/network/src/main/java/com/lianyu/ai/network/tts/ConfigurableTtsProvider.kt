package com.lianyu.ai.network.tts

interface ConfigurableTtsProvider {
    fun updateConfig(config: TtsConfig)
}