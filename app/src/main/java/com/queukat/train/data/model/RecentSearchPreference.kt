package com.queukat.train.data.model

data class RecentSearchPreference(
    val fromStopId: Int,
    val toStopId: Int,
    val fromFallbackName: String = "",
    val toFallbackName: String = "",
    val lastSearchedAtMs: Long
)
