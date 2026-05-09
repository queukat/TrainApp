package com.queukat.train.data.model

data class SavedRoutePreference(
    val fromStopId: Int,
    val toStopId: Int,
    val fromFallbackName: String = "",
    val toFallbackName: String = "",
)
