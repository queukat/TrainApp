package com.queukat.train.ui

import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.SavedRoutePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteSupportTest {
    private val stops =
        listOf(
            StopEntity(
                stopId = 1,
                nameEn = "Bar",
                nameMe = "Bar",
                nameMeCyr = "Бар",
                stopTypeId = 1,
                latitude = null,
                longitude = null,
                local = 1,
            ),
            StopEntity(
                stopId = 8,
                nameEn = "Podgorica",
                nameMe = "Podgorica",
                nameMeCyr = "Подгорица",
                stopTypeId = 1,
                latitude = null,
                longitude = null,
                local = 1,
            ),
        )

    @Test
    fun legacySavedRoutes_migrateToStopIds() {
        val migrated =
            migrateLegacySavedRoutes(
                legacyRoutes = setOf("Bar - Podgorica"),
                stops = stops,
            )

        assertEquals(
            setOf(
                SavedRoutePreference(
                    fromStopId = 1,
                    toStopId = 8,
                    fromFallbackName = "Bar",
                    toFallbackName = "Podgorica",
                ),
            ),
            migrated,
        )
    }

    @Test
    fun legacySavedRoutes_resolveAcrossDifferentNameLanguages() {
        val migrated =
            migrateLegacySavedRoutes(
                legacyRoutes = setOf("Бар - Podgorica"),
                stops = stops,
            )

        assertTrue(migrated.any { it.fromStopId == 1 && it.toStopId == 8 })
    }

    @Test
    fun legacySavedRoutes_ignoreCrossingStops() {
        val migrated =
            migrateLegacySavedRoutes(
                legacyRoutes = setOf("Zeta - Podgorica"),
                stops =
                    stops +
                        StopEntity(
                            stopId = 5,
                            nameEn = "Zeta",
                            nameMe = "Zeta",
                            nameMeCyr = "Зета",
                            stopTypeId = 3,
                            latitude = null,
                            longitude = null,
                            local = 1,
                        ),
            )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun savedRouteLabel_prefersStopIdAndRequestedLanguage() {
        val label =
            resolveSavedRouteLabel(
                route =
                    SavedRoutePreference(
                        fromStopId = 1,
                        toStopId = 8,
                        fromFallbackName = "Old From",
                        toFallbackName = "Old To",
                    ),
                stopMap = stops.associateBy { it.stopId },
                language = "meCyr",
            )

        assertEquals("Бар - Подгорица", label)
    }
}
