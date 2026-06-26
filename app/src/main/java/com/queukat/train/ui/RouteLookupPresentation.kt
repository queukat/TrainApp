package com.queukat.train.ui

import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.hasRouteResults
import com.queukat.train.data.repository.RouteLookupResult

sealed interface RouteLookupPresentation {
    data class Results(
        val response: RoutesResponse,
    ) : RouteLookupPresentation

    data object Empty : RouteLookupPresentation

    data class Error(
        val kind: RouteErrorKind,
        val httpCode: Int? = null,
    ) : RouteLookupPresentation
}

internal fun RouteLookupResult.toRouteLookupPresentation(): RouteLookupPresentation =
    when (this) {
        is RouteLookupResult.Success -> {
            val routes = response
            if (!routes.hasRouteResults()) {
                RouteLookupPresentation.Empty
            } else {
                RouteLookupPresentation.Results(routes)
            }
        }

        is RouteLookupResult.HttpError -> {
            RouteLookupPresentation.Error(
                kind = RouteErrorKind.Server,
                httpCode = code,
            )
        }
        is RouteLookupResult.NetworkError -> RouteLookupPresentation.Error(RouteErrorKind.Network)
        is RouteLookupResult.InvalidResponse -> {
            RouteLookupPresentation.Error(RouteErrorKind.InvalidResponse)
        }
    }
