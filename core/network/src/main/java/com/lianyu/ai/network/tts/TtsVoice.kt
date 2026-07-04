package com.lianyu.ai.network.tts

data class TtsVoice(
    val id: String,
    val name: String,
    val gender: String,
    val language: String,
    val description: String = ""
)