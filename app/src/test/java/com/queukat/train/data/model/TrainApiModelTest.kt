package com.queukat.train.data.model

import com.google.gson.Gson
import com.queukat.train.util.AppDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrainApiModelTest {
    private val gson = Gson()

    @Test
    fun apiModelsExposeCamelCaseFields() {
        val stopType =
            StopType(
                stopTypeId = 4,
                nameMe = "station-me",
                nameEn = "station-en",
            )
        val stop =
            StopDto(
                stopId = 7,
                nameMe = "Bar-me",
                nameEn = "Bar",
                nameMeCyr = "Bar-cyr",
                stopTypeId = 4,
                latitude = 42.1,
                longitude = 19.1,
                local = 1,
                stopType = stopType,
            )
        val routeStop =
            RouteStop(
                routeStopId = 8,
                order = 2,
                stopId = stop.stopId,
                stop = stop,
            )
        val timetableItem =
            TimetableItem(
                timetableItemId = 9,
                timetableId = 10,
                routeStopId = routeStop.routeStopId,
                arrivalTime = "10:00",
                departureTime = "10:05",
                routestop = routeStop,
            )
        val routeInfo =
            RouteInfo(
                routeId = 11,
                validFrom = "2026-01-01",
                validTo = "2026-12-31",
            )
        val route =
            DirectRoute(
                timetableId = timetableItem.timetableId,
                routeId = routeInfo.routeId,
                trainNumber = "6100",
                trainTypeId = 3,
                international = 0,
                timetableItems = listOf(timetableItem),
                route = routeInfo,
            ).apply {
                startStation = "Bar"
                endStation = "Podgorica"
                validFrom = routeInfo.validFrom
                validTo = routeInfo.validTo
            }
        val response =
            RoutesResponse(
                price =
                    PriceInfo(
                        pricelistId = 12,
                        stopFromId = 7,
                        stopToId = 13,
                        class1Price = 4.5,
                        class2Price = 2.5,
                ),
                direct = listOf(route),
                connected = emptyMap(),
            )

        assertEquals(12, response.price?.pricelistId)
        assertEquals(7, response.price?.stopFromId)
        assertEquals(13, response.price?.stopToId)
        assertEquals(4.5, response.price?.class1Price ?: 0.0, 0.0)
        assertEquals(2.5, response.price?.class2Price ?: 0.0, 0.0)
        assertEquals(emptyMap<String, ConnectedRouteGroup>(), response.connected)

        val directRoute = response.direct?.single()
        assertEquals(10, directRoute?.timetableId)
        assertEquals(11, directRoute?.routeId)
        assertEquals("6100", directRoute?.trainNumber)
        assertEquals(3, directRoute?.trainTypeId)
        assertEquals(0, directRoute?.international)
        assertEquals("Bar", directRoute?.startStation)
        assertEquals("Podgorica", directRoute?.endStation)
        assertEquals("2026-01-01", directRoute?.validFrom)
        assertEquals("2026-12-31", directRoute?.validTo)

        val item = directRoute?.timetableItems?.single()
        assertEquals(9, item?.timetableItemId)
        assertEquals(10, item?.timetableId)
        assertEquals(8, item?.routeStopId)
        assertEquals("10:00", item?.arrivalTime)
        assertEquals("10:05", item?.departureTime)
        assertEquals(8, item?.routestop?.routeStopId)
        assertEquals(2, item?.routestop?.order)
        assertEquals(7, item?.routestop?.stopId)
        assertEquals("Bar", item?.routestop?.stop?.nameEn)
        assertEquals("Bar-me", item?.routestop?.stop?.nameMe)
        assertEquals("Bar-cyr", item?.routestop?.stop?.nameMeCyr)
        assertEquals(4, item?.routestop?.stop?.stopTypeId)
        assertEquals(42.1, item?.routestop?.stop?.latitude ?: 0.0, 0.0)
        assertEquals(19.1, item?.routestop?.stop?.longitude ?: 0.0, 0.0)
        assertEquals(1, item?.routestop?.stop?.local)
        assertEquals(4, item?.routestop?.stop?.stopType?.stopTypeId)
        assertEquals("station-me", item?.routestop?.stop?.stopType?.nameMe)
        assertEquals("station-en", item?.routestop?.stop?.stopType?.nameEn)
        assertEquals(11, directRoute?.route?.routeId)
        assertEquals("2026-01-01", directRoute?.route?.validFrom)
        assertEquals("2026-12-31", directRoute?.route?.validTo)
    }

    @Test
    fun serializedApiNamesMapToCamelCaseFields() {
        val response =
            gson.fromJson(
                """
                {
                  "price": {
                    "PricelistID": 1,
                    "StopFromID": 2,
                    "StopToID": 3,
                    "Class1Price": 4.0,
                    "Class2Price": 5.0
                  },
                  "direct": [
                    {
                      "TimetableID": 6,
                      "RouteID": 7,
                      "TrainNumber": "6101",
                      "TrainTypeID": 8,
                      "International": 0,
                      "timetable_items": [
                        {
                          "TimetableItemID": 9,
                          "TimetableID": 6,
                          "RouteStopID": 10,
                          "ArrivalTime": "11:00",
                          "DepartureTime": "11:05",
                          "routestop": {
                            "RouteStopID": 10,
                            "Order": 1,
                            "StopID": 2,
                            "stop": {
                              "StopID": 2,
                              "Name_me": "Bar-me",
                              "Name_en": "Bar",
                              "Name_me_cyr": "Bar-cyr",
                              "StopTypeID": 4,
                              "Latitude": 42.0,
                              "Longitude": 19.0,
                              "local": 1,
                              "stop_type": {
                                "StopTypeID": 4,
                                "Name_me": "station-me",
                                "Name_en": "station-en"
                              }
                            }
                          }
                        }
                      ],
                      "route": {
                        "RouteID": 7,
                        "ValidFrom": "2026-01-01",
                        "ValidTo": "2026-12-31"
                      }
                    }
                  ],
                  "connected": []
                }
                """.trimIndent(),
                RoutesResponse::class.java,
            )

        assertEquals(1, response.price?.pricelistId)
        assertEquals(2, response.price?.stopFromId)
        assertEquals(3, response.price?.stopToId)
        assertEquals(4.0, response.price?.class1Price ?: 0.0, 0.0)
        assertEquals(5.0, response.price?.class2Price ?: 0.0, 0.0)
        assertEquals(6, response.direct?.single()?.timetableId)
        assertEquals(7, response.direct?.single()?.routeId)
        assertEquals("6101", response.direct?.single()?.trainNumber)
        assertEquals(8, response.direct?.single()?.trainTypeId)
        assertEquals(0, response.direct?.single()?.international)
        assertEquals(9, response.direct?.single()?.timetableItems?.single()?.timetableItemId)
        assertEquals(10, response.direct?.single()?.timetableItems?.single()?.routeStopId)
        assertEquals("11:00", response.direct?.single()?.timetableItems?.single()?.arrivalTime)
        assertEquals("11:05", response.direct?.single()?.timetableItems?.single()?.departureTime)
        assertEquals(10, response.direct?.single()?.timetableItems?.single()?.routestop?.routeStopId)
        assertEquals(1, response.direct?.single()?.timetableItems?.single()?.routestop?.order)
        assertEquals(2, response.direct?.single()?.timetableItems?.single()?.routestop?.stopId)
        assertEquals("Bar", response.direct?.single()?.timetableItems?.single()?.routestop?.stop?.nameEn)
        assertEquals(7, response.direct?.single()?.route?.routeId)
        assertEquals("2026-01-01", response.direct?.single()?.route?.validFrom)
        assertEquals("2026-12-31", response.direct?.single()?.route?.validTo)
        assertEquals(emptyMap<String, ConnectedRouteGroup>(), response.connected)
    }

    @Test
    fun connectedEmptyArrayMapsToEmptyMap() {
        val response =
            gson.fromJson(
                """
                {
                  "price": null,
                  "direct": [],
                  "connected": []
                }
                """.trimIndent(),
                RoutesResponse::class.java,
            )

        assertEquals(emptyMap<String, ConnectedRouteGroup>(), response.connected)
    }

    @Test
    fun connectedObjectMapsToViaStopGroups() {
        val response =
            gson.fromJson(
                """
                {
                  "price": null,
                  "direct": [],
                  "connected": {
                    "8": {
                      "via_stop": {
                        "StopID": 8,
                        "Name_me": "Podgorica",
                        "Name_en": "Podgorica",
                        "Name_me_cyr": "Подгорица",
                        "StopTypeID": 4,
                        "Latitude": 42.432255,
                        "Longitude": 19.269167,
                        "local": 1,
                        "stop_type": {
                          "StopTypeID": 4,
                          "Name_me": "glavna stanica",
                          "Name_en": "main station"
                        }
                      },
                      "start": [
                        {
                          "TimetableID": 171,
                          "RouteID": 83,
                          "TrainNumber": "6100",
                          "TrainTypeID": 3,
                          "International": 0,
                          "timetable_items": []
                        }
                      ],
                      "finish": [
                        {
                          "TimetableID": 202,
                          "RouteID": 4,
                          "TrainNumber": "7100",
                          "TrainTypeID": 3,
                          "International": 0,
                          "timetable_items": []
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
                RoutesResponse::class.java,
            )

        val group = response.connected?.get("8")
        assertNotNull(group)
        assertEquals("Podgorica", group?.viaStop?.nameEn)
        assertEquals(171, group?.start?.single()?.timetableId)
        assertEquals("6100", group?.start?.single()?.trainNumber)
        assertEquals(202, group?.finish?.single()?.timetableId)
        assertEquals("7100", group?.finish?.single()?.trainNumber)
        assertEquals(
            listOf(171, 202),
            response.connectedRouteSegments().map { route -> route.timetableId },
        )
    }

    @Test
    fun stopNameForLanguageUsesFallbacks() {
        val stop =
            StopDto(
                stopId = 1,
                nameMe = "Bar-me",
                nameEn = "Bar",
                nameMeCyr = "Bar-cyr",
                stopTypeId = null,
                latitude = null,
                longitude = null,
                local = null,
                stopType = null,
            )

        assertEquals("Bar", stop.getNameForLanguage("en"))
        assertEquals("Bar-me", stop.getNameForLanguage("me"))
        assertEquals("Bar-cyr", stop.getNameForLanguage("ru"))
        assertEquals("Bar-cyr", stop.getNameForLanguage("meCyr"))
        assertEquals("Bar-me", stop.getNameForLanguage("unknown"))

        val cyrFallback = stop.copy(nameEn = null, nameMeCyr = null)
        val unknown = stop.copy(nameEn = null, nameMe = null, nameMeCyr = null)

        assertEquals("Bar-me", cyrFallback.getNameForLanguage("meCyr"))
        assertEquals("Unknown", unknown.getNameForLanguage("en"))
    }

    @Test
    fun appDispatchersAreConfigured() {
        assertNotNull(AppDispatchers.IO)
        assertNotNull(AppDispatchers.Default)
    }
}
