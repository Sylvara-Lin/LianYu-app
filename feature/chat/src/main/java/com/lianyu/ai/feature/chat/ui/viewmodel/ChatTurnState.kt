package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.StickerInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex

/**
 * Encapsulates per-turn mutable state for a chat conversation.
 *
 * Extracted from ChatViewModel to give explicit lifecycle to previously
 * scattered mutable fields: sticker state, send job, and concurrency mutex.
 */
class ChatTurnState {
    /** Whether a sticker was already sent during this turn. */
    var stickerSentThisTurn: Boolean = false

    /** Sticker queued by TextProcessor but not yet persisted. Used to randomize sticker/text order. */
    var pendingSticker: StickerInfo? = null

    /** Message ID of the pending sticker to broadcast (stale sticker pattern). */
    var lastStickerMsgId: Long = -1

    /** Content of the pending stale sticker message. */
    var lastStickerContent: String = ""

    /** Active message-sending coroutine job, cancelled when a new message starts. */
    var sendMessageJob: Job? = null

    /** Mutex for atomic sticker send operations. */
    val stickerMutex: Mutex = Mutex()

    /** Reset turn state for a new message. */
    fun reset() {
        stickerSentThisTurn = false
        pendingSticker = null
        lastStickerMsgId = -1
        lastStickerContent = ""
    }

    /** Cancel the active send job if any. Returns the cancelled job or null. */
    fun cancelSendJob() {
        sendMessageJob?.cancel()
    }

    /** Start a new send job, cancelling any previous one. */
    fun replaceSendJob(job: Job) {
        sendMessageJob?.cancel()
        sendMessageJob = job
    }

    /** Broadcast stale sticker if pending. Returns true if a stale sticker was broadcast. */
    fun flushStaleSticker(broadcast: (Long, String) -> Unit): Boolean {
        if (lastStickerMsgId > 0) {
            broadcast(lastStickerMsgId, lastStickerContent)
            lastStickerMsgId = -1
            lastStickerContent = ""
            return true
        }
        return false
    }

    /** Set the stale sticker pending for next-turn broadcast. */
    fun enqueueStaleSticker(msgId: Long, content: String) {
        lastStickerMsgId = msgId
        lastStickerContent = content
    }
}
