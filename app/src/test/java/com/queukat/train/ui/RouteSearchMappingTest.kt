package com.queukat.train.ui

import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.repository.RouteLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchMappingTest {

    @Test
    fun successWithRoutes_mapsToResults() {
        val result = RouteLookupResult.Success(
            RoutesResponse(
                price = null,
                direct = listOf(
                    DirectRoute(
                        TimetableID = 1,
                        RouteID = 10,
                        TrainNumber = "6100",
                        TrainTypeID = 0,
                        International = 0,
                        timetable_items = emptyList()
                    )
                ),
                connected = emptyList()
            )
        )

        val presentation = result.toRouteLookupPresentation()

        assertTrue(presentation is RouteLookupPresentation.Results)
    }

    @Test
    fun successWithEmptyResult_mapsToEmpty() {
        val result = RouteLookupResult.Success(
            RoutesResponse(
                price = null,
                direct = emptyList(),
                connected = emptyList()
            )
        )

        val presentation = result.toRouteLookupPresentation()

        assertEquals(RouteLookupPresentation.Empty, presentation)
    }

    @Test
    fun apiOrServerFailure_mapsToServerError() {
        val result = RouteLookupResult.HttpError(code = 404, message = "Not Found")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(
                kind = RouteErrorKind.Server,
                httpCode = 404
            ),
            presentation
        )
    }

    @Test
    fun invalidPayload_mapsToInvalidResponseError() {
        val result = RouteLookupResult.InvalidResponse("Malformed HTML body")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(RouteErrorKind.InvalidResponse),
            presentation
        )
    }

    @Test
    fun networkFailure_mapsToNetworkError() {
        val result = RouteLookupResult.NetworkError("timeout")

        val presentation = result.toRouteLookupPresentation()

        assertEquals(
            RouteLookupPresentation.Error(RouteErrorKind.Network),
            presentation
        )
    }
}
