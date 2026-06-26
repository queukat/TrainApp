package com.queukat.train.ui

import com.queukat.train.data.model.ConnectedRouteGroup
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RouteStop
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.StopDto
import com.queukat.train.data.model.TimetableItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedRouteSupportTest {
    @Test
    fun connectedRouteOptionsChooseEarliestValidFinishLeg() {
        val bar = stop(1, "Bar")
        val podgorica = stop(8, "Podgorica")
        val danilovgrad = stop(24, "Danilovgrad")
        val firstLeg =
            route(
                timetableId = 171,
                trainNumber = "6100",
                items =
                    listOf(
                        item(bar, arrival = null, departure = "05:13:00"),
                        item(podgorica, arrival = "06:16:00", departure = "06:21:00"),
                    ),
            )
        val earliestFinishLeg =
            route(
                timetableId = 202,
                trainNumber = "7100",
                items =
                    listOf(
                        item(podgorica, arrival = "06:22:00", departure = "06:24:00"),
                        item(danilovgrad, arrival = "07:00:00", departure = null),
                    ),
            )
        val laterFinishLeg =
            route(
                timetableId = 203,
                trainNumber = "7101",
                items =
                    listOf(
                        item(podgorica, arrival = "07:25:00", departure = "07:30:00"),
                        item(danilovgrad, arrival = "08:05:00", departure = null),
                    ),
            )
        val response =
            RoutesResponse(
                price = null,
                direct = emptyList(),
                connected =
                    mapOf(
                        "8" to
                            ConnectedRouteGroup(
                                viaStop = podgorica,
                                start = listOf(firstLeg),
                                finish = listOf(laterFinishLeg, earliestFinishLeg),
                            ),
                    ),
            )

        val option = response.connectedRouteOptions("2026-06-25").single()

        assertEquals("Podgorica", option.viaStop?.nameEn)
        assertEquals(171, option.firstLeg.timetableId)
        assertEquals(202, option.secondLeg.timetableId)
        assertEquals(8L, option.transferWaitMinutes)
        assertEquals(107L, option.totalDurationMinutes)
    }

    @Test
    fun transferRiskMarksShortConnections() {
        assertEquals(TransferRisk.TooShort, transferRisk(4))
        assertEquals(TransferRisk.Tight, transferRisk(5))
        assertEquals(TransferRisk.Tight, transferRisk(14))
        assertEquals(TransferRisk.Normal, transferRisk(15))
    }

    private fun route(
        timetableId: Int,
        trainNumber: String,
        items: List<TimetableItem>,
    ): DirectRoute =
        DirectRoute(
            timetableId = timetableId,
            routeId = timetableId + 1000,
            trainNumber = trainNumber,
            trainTypeId = 3,
            international = 0,
            timetableItems = items,
        )

    private fun item(
        stop: StopDto,
        arrival: String?,
        departure: String?,
    ): TimetableItem =
        TimetableItem(
            timetableItemId = stop.stopId,
            timetableId = null,
            routeStopId = stop.stopId,
            arrivalTime = arrival,
            departureTime = departure,
            routestop =
                RouteStop(
                    routeStopId = stop.stopId,
                    order = stop.stopId,
                    stopId = stop.stopId,
                    stop = stop,
                ),
        )

    private fun stop(
        id: Int,
        name: String,
    ): StopDto =
        StopDto(
            stopId = id,
            nameMe = name,
            nameEn = name,
            nameMeCyr = name,
            stopTypeId = 4,
            latitude = null,
            longitude = null,
            local = 1,
            stopType = null,
        )
}
