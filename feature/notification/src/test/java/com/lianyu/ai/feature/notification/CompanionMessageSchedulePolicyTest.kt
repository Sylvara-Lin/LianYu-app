package com.lianyu.ai.feature.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionMessageSchedulePolicyTest {

    @Test
    fun periodicIntervalsRespectWorkManagerMinimums() {
        val interval = CompanionMessageSchedulePolicy.sanitizePeriodicInterval(
            intervalMinutes = 2,
            flexMinutes = 1
        )

        assertEquals(15L, interval.intervalMinutes)
        assertEquals(5L, interval.flexMinutes)
    }

    @Test
    fun customIntervalIsUsedWhenEnabledAndValid() {
        val interval = CompanionMessageSchedulePolicy.initialInterval(
            useCustomInterval = true,
            customIntervalMinutes = 45
        )

        assertEquals(45L, interval.intervalMinutes)
        assertEquals(15L, interval.flexMinutes)
    }

    @Test
    fun highIntimacyIntervalIsStillSafeForPeriodicWork() {
        val interval = CompanionMessageSchedulePolicy.forIntimacy(
            intimacy = 100,
            randomMinuteSelector = { min, _ -> min }
        )

        assertEquals(15L, interval.intervalMinutes)
        assertEquals(5L, interval.flexMinutes)
    }
}
