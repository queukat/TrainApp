package com.queukat.train.data.repository

import com.queukat.train.data.model.RoutesResponse

sealed interface RouteLookupResult {
    data class Success(val response: RoutesResponse) : RouteLookupResult
    data class HttpError(
        val code: Int,
        val message: String? = null,
        val responsePreview: String? = null
    ) : RouteLookupResult
    data class NetworkError(val message: String? = null) : RouteLookupResult
    data class InvalidResponse(val message: String? = null) : RouteLookupResult
}
