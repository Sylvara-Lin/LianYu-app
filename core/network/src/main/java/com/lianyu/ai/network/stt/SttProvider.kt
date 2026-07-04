package com.lianyu.ai.network.stt

enum class SttProvider(val displayName: String, val description: String) {
    ANDROID("系统语音识别", "使用Android内置SpeechRecognizer，无需配置"),
    SILICONFLOW("硅基流动", "SenseVoice 多语言语音识别，需配置API Key"),
    SHERPA_ONNX("Sherpa-ONNX", "离线流式语音识别，端侧运行无需联网")
}
