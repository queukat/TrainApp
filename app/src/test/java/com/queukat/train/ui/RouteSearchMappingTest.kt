package com.queukat.train.ui

import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.ConnectedRouteGroup
import com.queukat.train.data.repository.RouteLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchMappingTest {
    @Test
    fun successWithRoutes_mapsToResults() {
        val result =
            RouteLookupResult.Success(
                RoutesResponse(
                    price = null,
                    direct =
                        listOf(
                            DirectRoute(
                                timetableId = 1,
                                routeId = 10,
                                trainNumber = "6100",
                                trainTypeId = 0,
                                international = 0,
                                timetableItems = emptyList(),
                            ),
                        ),
                    connected = emptyMap(),
                ),
            )

        val presentation = result.toRouteLookupPresentation()

        assertTrue(presentation is RouteLookupPresentation.Results)
    }

    @Test
    fun successWithEmptyResult_mapsToEmpty() {
        val result =
            RouteLookupResult.Success(
                RoutesResponse(
                    price = null,
                    direct = emptyList(),
                    connected = emptyMap(),
                ),
            )

        val presentation = result.toRouteLookupPresentation()

        assertEquals(RouteLookupPresentation.Empty, presentation)
    }

    @Test
    fun successWithOnlyConnectedRoutes_mapsToResults() {
        val result =
            RouteLookupResult.Success(
                RoutesResponse(
                    price = null,
                    direct = emptyList(),
                    connected =
                        mapOf(
                            "8" to
                                ConnectedRouteGroup(
                                    viaStop = null,
                                    start =
                                        listOf(
                                            DirectRoute(
                                                timetableId = 171,
                                                routeId = 83,
                                                trainNumber = "6100",
                                                trainTypeId = 3,
                                                international = 0,
                                                timetableItems = emptyList(),
                                            ),
                                        ),
                                    finish = emptyList(),
                                ),
                        ),
                ),
            )

        val presentation = result.toRouteLookupPresentation()

        assertTrue(presentation is RouteLookupPresentation.Results)
    }

    @Test
    fun apiOrServerFailure_mapsToServerError() {
        val result = RouteLookupResult.HttpError(code = 404, message = "Not Found")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(
                kind = RouteErrorKind.Server,
                httpCode = 404,
            ),
            presentation,
        )
    }

    @Test
    fun invalidPayload_mapsToInvalidResponseError() {
        val result = RouteLookupResult.InvalidResponse("Malformed HTML body")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(RouteErrorKind.InvalidResponse),
            presentation,
        )
    }

    @Test
    fun networkFailure_mapsToNetworkError() {
        val result = RouteLookupResult.NetworkError("timeout")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(RouteErrorKind.Network),
            presentation,
        )
    }
}
