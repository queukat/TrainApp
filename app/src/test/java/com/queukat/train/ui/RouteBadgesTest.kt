package com.queukat.train.ui

import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RouteStop
import com.queukat.train.data.model.StopDto
import com.queukat.train.data.model.TimetableItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBadgesTest {
    @Test
    fun internationalLabelIsHiddenForLocalOnlySegment() {
        val route =
            route(
                international = 1,
                firstLocal = 1,
                lastLocal = 1,
            )

        assertFalse(route.shouldShowInternationalLabel())
    }

    @Test
    fun internationalLabelIsShownForCrossBorderSegment() {
        val route =
            route(
                international = 1,
                firstLocal = 1,
                lastLocal = 0,
            )

        assertTrue(route.shouldShowInternationalLabel())
    }

    @Test
    fun internationalLabelIsHiddenWhenApiFlagIsNotSet() {
        val route =
            route(
                international = 0,
                firstLocal = 1,
                lastLocal = 0,
            )

        assertFalse(route.shouldShowInternationalLabel())
    }

    private fun route(
        international: Int,
        firstLocal: Int?,
        lastLocal: Int?,
    ): DirectRoute =
        DirectRoute(
            timetableId = 236,
            routeId = 95,
            trainNumber = "432",
            trainTypeId = 3,
            international = international,
            timetableItems =
                listOf(
                    item(stopId = 1, name = "Bar", local = firstLocal),
                    item(stopId = 8, name = "Podgorica", local = lastLocal),
                ),
        )

    private fun item(
        stopId: Int,
        name: String,
        local: Int?,
    ): TimetableItem =
        TimetableItem(
            timetableItemId = stopId,
            timetableId = 236,
            routeStopId = stopId,
            arrivalTime = null,
            departureTime = null,
            routestop =
                RouteStop(
                    routeStopId = stopId,
                    order = stopId,
                    stopId = stopId,
                    stop =
                        StopDto(
                            stopId = stopId,
                            nameMe = name,
                            nameEn = name,
                            nameMeCyr = name,
                            stopTypeId = 4,
                            latitude = null,
                            longitude = null,
                            local = local,
                            stopType = null,
                        ),
                ),
        )
}
