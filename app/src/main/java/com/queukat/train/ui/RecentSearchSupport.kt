package com.queukat.train.ui

import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.model.RecentSearchPreference

internal const val MAX_RECENT_SEARCHES = 5

internal fun upsertRecentSearch(
    existing: List<RecentSearchPreference>,
    fromStop: StopEntity,
    toStop: StopEntity,
    searchedAtMs: Long,
    maxEntries: Int = MAX_RECENT_SEARCHES,
): List<RecentSearchPreference> {
    val updated =
        buildList {
            add(
                RecentSearchPreference(
                    fromStopId = fromStop.stopId,
                    toStopId = toStop.stopId,
                    fromFallbackName = fromStop.nameEn,
                    toFallbackName = toStop.nameEn,
                    lastSearchedAtMs = searchedAtMs,
                ),
            )
            addAll(
                existing.filterNot {
                    it.fromStopId == fromStop.stopId && it.toStopId == toStop.stopId
                },
            )
        }

    return updated.take(maxEntries)
}

internal fun resolveRecentSearchLabel(
    route: RecentSearchPreference,
    stopMap: Map<Int, StopEntity>,
    language: String,
): String {
    val fromName =
        stopMap[route.fromStopId]?.getNameForLanguage(language)
            ?: route.fromFallbackName.ifBlank { route.fromStopId.toString() }
    val toName =
        stopMap[route.toStopId]?.getNameForLanguage(language)
            ?: route.toFallbackName.ifBlank { route.toStopId.toString() }
    return "$fromName - $toName"
}
