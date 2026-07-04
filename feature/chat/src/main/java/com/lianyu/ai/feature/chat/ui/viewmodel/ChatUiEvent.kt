package com.lianyu.ai.feature.chat.ui.viewmodel

import androidx.compose.runtime.Stable

/**
 * One-shot UI events emitted by ChatViewModel via SharedFlow.
 * These are consumed exactly once by the UI layer — unlike StateFlow
 * which retains the last value and re-emits on resubscription.
 */
@Stable
sealed class ChatUiEvent {
    /** Non-blocking info: message sent, stream started, etc. */
    data class Info(val message: String) : ChatUiEvent()

    /** Recoverable error to show as SnackBar/Toast */
    data class Error(val message: String) : ChatUiEvent()

    /** Message was blocked by content filter */
    data class ContentBlocked(val reason: String) : ChatUiEvent()

    /** Stream completed successfully */
    data object StreamCompleted : ChatUiEvent()
}
