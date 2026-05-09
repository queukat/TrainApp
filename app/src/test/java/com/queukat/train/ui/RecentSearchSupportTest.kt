package com.queukat.train.ui

import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.RecentSearchPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchSupportTest {
    private val bar =
        StopEntity(
            stopId = 1,
            nameEn = "Bar",
            nameMe = "Bar",
            nameMeCyr = "Бар",
            stopTypeId = 1,
            latitude = null,
            longitude = null,
            local = 1,
        )

    private val podgorica =
        StopEntity(
            stopId = 8,
            nameEn = "Podgorica",
            nameMe = "Podgorica",
            nameMeCyr = "Подгорица",
            stopTypeId = 1,
            latitude = null,
            longitude = null,
            local = 1,
        )

    private val bijeloPolje =
        StopEntity(
            stopId = 22,
            nameEn = "Bijelo Polje",
            nameMe = "Bijelo Polje",
            nameMeCyr = "Бијело Поље",
            stopTypeId = 1,
            latitude = null,
            longitude = null,
            local = 1,
        )

    @Test
    fun upsertRecentSearch_movesDuplicatePairToTop() {
        val existing =
            listOf(
                RecentSearchPreference(22, 8, "Bijelo Polje", "Podgorica", 200L),
                RecentSearchPreference(1, 8, "Bar", "Podgorica", 100L),
            )

        val updated =
            upsertRecentSearch(
                existing = existing,
                fromStop = bar,
                toStop = podgorica,
                searchedAtMs = 300L,
            )

        assertEquals(2, updated.size)
        assertEquals(1, updated.first().fromStopId)
        assertEquals(8, updated.first().toStopId)
        assertEquals(300L, updated.first().lastSearchedAtMs)
    }

    @Test
    fun upsertRecentSearch_trimsToConfiguredLimit() {
        val existing =
            listOf(
                RecentSearchPreference(22, 8, "Bijelo Polje", "Podgorica", 500L),
                RecentSearchPreference(8, 22, "Podgorica", "Bijelo Polje", 400L),
                RecentSearchPreference(8, 1, "Podgorica", "Bar", 300L),
                RecentSearchPreference(1, 22, "Bar", "Bijelo Polje", 200L),
                RecentSearchPreference(22, 1, "Bijelo Polje", "Bar", 100L),
            )

        val updated =
            upsertRecentSearch(
                existing = existing,
                fromStop = bar,
                toStop = podgorica,
                searchedAtMs = 600L,
                maxEntries = 5,
            )

        assertEquals(5, updated.size)
        assertEquals(1, updated.first().fromStopId)
        assertEquals(8, updated.first().toStopId)
        assertTrue(updated.none { it.fromStopId == 22 && it.toStopId == 1 })
    }

    @Test
    fun resolveRecentSearchLabel_prefersStopIdAndRequestedLanguage() {
        val label =
            resolveRecentSearchLabel(
                route =
                    RecentSearchPreference(
                        fromStopId = 1,
                        toStopId = 22,
                        fromFallbackName = "Old From",
                        toFallbackName = "Old To",
                        lastSearchedAtMs = 123L,
                    ),
                stopMap = listOf(bar, podgorica, bijeloPolje).associateBy { it.stopId },
                language = "meCyr",
            )

        assertEquals("Бар - Бијело Поље", label)
    }
}
