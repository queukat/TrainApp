package com.queukat.train.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbModelTest {
    @Test
    fun stopEntityNameForLanguageUsesFallbacks() {
        val stop =
            StopEntity(
                stopId = 1,
                nameEn = "Bar",
                nameMe = "Bar-me",
                nameMeCyr = "Bar-cyr",
                stopTypeId = 2,
                latitude = 42.1,
                longitude = 19.1,
                local = 1,
            )

        assertEquals("Bar", stop.getNameForLanguage("en"))
        assertEquals("Bar-me", stop.getNameForLanguage("me"))
        assertEquals("Bar-cyr", stop.getNameForLanguage("ru"))
        assertEquals("Bar-cyr", stop.getNameForLanguage("meCyr"))
        assertEquals("Bar-me", stop.getNameForLanguage("unknown"))

        val withoutCyr = stop.copy(nameMeCyr = null)
        val withoutLocalName = stop.copy(nameMe = "")

        assertEquals("Bar-me", withoutCyr.getNameForLanguage("ru"))
        assertEquals("Bar", withoutLocalName.getNameForLanguage("unknown"))
        assertEquals(1, stop.stopId)
        assertEquals(2, stop.stopTypeId)
        assertEquals(42.1, stop.latitude ?: 0.0, 0.0)
        assertEquals(19.1, stop.longitude ?: 0.0, 0.0)
        assertEquals(1, stop.local)
    }

    @Test
    fun routeInfoEntityCarriesRouteMetadata() {
        val entity =
            RouteInfoEntity(
                routeId = 10,
                startNameEn = "Bar",
                startNameMe = "Bar-me",
                startNameMeCyr = "Bar-cyr",
                endNameEn = "Podgorica",
                endNameMe = "Podgorica-me",
                endNameMeCyr = "Podgorica-cyr",
                validFrom = "2026-01-01",
                validTo = "2026-12-31",
            )

        assertEquals(10, entity.routeId)
        assertEquals("Bar", entity.startNameEn)
        assertEquals("Bar-me", entity.startNameMe)
        assertEquals("Bar-cyr", entity.startNameMeCyr)
        assertEquals("Podgorica", entity.endNameEn)
        assertEquals("Podgorica-me", entity.endNameMe)
        assertEquals("Podgorica-cyr", entity.endNameMeCyr)
        assertEquals("2026-01-01", entity.validFrom)
        assertEquals("2026-12-31", entity.validTo)
    }

    @Test
    fun crossingStopsAreNotPassengerSearchStops() {
        val station =
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
        val crossing =
            station.copy(
                stopId = 5,
                nameEn = "Zeta",
                nameMe = "Zeta",
                nameMeCyr = "Зета",
                stopTypeId = 3,
            )

        assertTrue(station.isPassengerSearchStop())
        assertFalse(crossing.isPassengerSearchStop())
    }

    @Test
    fun apiRouteNameUsesMontenegrinApiName() {
        val stop =
            StopEntity(
                stopId = 31,
                nameEn = "Belgrade Center",
                nameMe = "Beograd Centar",
                nameMeCyr = "Београд Центар",
                stopTypeId = 4,
                latitude = null,
                longitude = null,
                local = 0,
            )

        assertEquals("Beograd Centar", stop.apiRouteName())
    }

    @Test
    fun apiRouteNameFallsBackWhenMontenegrinNameIsMissing() {
        val stop =
            StopEntity(
                stopId = 31,
                nameEn = "Belgrade Center",
                nameMe = "",
                nameMeCyr = "Београд Центар",
                stopTypeId = 4,
                latitude = null,
                longitude = null,
                local = 0,
            )

        assertEquals("Београд Центар", stop.apiRouteName())
    }
}
