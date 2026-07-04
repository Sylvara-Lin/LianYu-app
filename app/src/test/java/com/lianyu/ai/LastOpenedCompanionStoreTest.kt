package com.lianyu.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastOpenedCompanionStoreTest {
    @Test
    fun restoresLastOpenedCompanionWhenItStillExists() {
        val restoredId = LastOpenedCompanionStore.resolveInitialCompanionId(
            lastOpenedId = 2L,
            availableCompanionIds = listOf(1L, 2L, 3L)
        )

        assertEquals(2L, restoredId)
    }

    @Test
    fun ignoresMissingLastOpenedCompanion() {
        val restoredId = LastOpenedCompanionStore.resolveInitialCompanionId(
            lastOpenedId = 9L,
            availableCompanionIds = listOf(1L, 2L, 3L)
        )

        assertNull(restoredId)
    }

    @Test
    fun ignoresUnsetLastOpenedCompanion() {
        val restoredId = LastOpenedCompanionStore.resolveInitialCompanionId(
            lastOpenedId = -1L,
            availableCompanionIds = listOf(1L, 2L, 3L)
        )

        assertNull(restoredId)
    }
}
