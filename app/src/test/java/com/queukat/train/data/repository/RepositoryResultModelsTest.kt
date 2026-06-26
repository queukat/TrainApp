package com.queukat.train.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RepositoryResultModelsTest {
    @Test
    fun stopsSyncResultsCarryExpectedPayloads() {
        assertSame(StopsSyncResult.UpToDate, StopsSyncResult.UpToDate)
        assertEquals(StopsSyncResult.Refreshed(count = 3), StopsSyncResult.Refreshed(count = 3))
        assertEquals(
            StopsSyncResult.Failed(message = "Network"),
            StopsSyncResult.Failed(message = "Network"),
        )
        assertEquals(StopsSyncResult.Failed(), StopsSyncResult.Failed(message = null))
    }
}
