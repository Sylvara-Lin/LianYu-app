package com.lianyu.ai.feature.localmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelStateResolverTest {
    @Test
    fun downloadedAndEnabledResolvesToEnabled() {
        val state = LocalModelStateResolver.resolve(
            isFilePresent = true,
            isEnabled = true,
            downloadId = null,
            progress = null,
            error = null
        )

        assertEquals(LocalModelUiStatus.ENABLED, state.status)
        assertEquals(100, state.progressPercent)
    }

    @Test
    fun missingFileWithNoDownloadResolvesToNotDownloaded() {
        val state = LocalModelStateResolver.resolve(
            isFilePresent = false,
            isEnabled = false,
            downloadId = null,
            progress = null,
            error = null
        )

        assertEquals(LocalModelUiStatus.NOT_DOWNLOADED, state.status)
        assertEquals(0, state.progressPercent)
    }

    @Test
    fun activeDownloadUsesProgress() {
        val state = LocalModelStateResolver.resolve(
            isFilePresent = false,
            isEnabled = false,
            downloadId = 42L,
            progress = 37,
            error = null
        )

        assertEquals(LocalModelUiStatus.DOWNLOADING, state.status)
        assertEquals(37, state.progressPercent)
    }

    @Test
    fun activeDownloadKeepsDownloadingEvenWhenTargetFileExists() {
        val state = LocalModelStateResolver.resolve(
            isFilePresent = true,
            isEnabled = false,
            downloadId = 42L,
            progress = 3,
            error = null
        )

        assertEquals(LocalModelUiStatus.DOWNLOADING, state.status)
        assertEquals(3, state.progressPercent)
    }
}
