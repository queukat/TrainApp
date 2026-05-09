package com.queukat.train.ui

import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.model.SavedRoutePreference

internal fun stopMatchesText(
    stop: StopEntity,
    inputText: String,
): Boolean {
    val normalized = inputText.trim()
    return stop.nameEn.equals(normalized, ignoreCase = true) ||
        stop.nameMe.equals(normalized, ignoreCase = true) ||
        (stop.nameMeCyr?.equals(normalized, ignoreCase = true) == true)
}

internal fun findStopByAnyName(
    stops: List<StopEntity>,
    inputText: String,
): StopEntity? {
    val normalized = inputText.trim()
    if (normalized.isBlank()) return null

    val matches = stops.filter { stopMatchesText(it, normalized) }
    if (matches.isEmpty()) return null
    if (matches.size == 1) return matches.first()

    return matches.firstOrNull { it.nameMe.equals(normalized, ignoreCase = true) }
        ?: matches.firstOrNull { it.nameEn.equals(normalized, ignoreCase = true) }
        ?: matches.firstOrNull { it.nameMeCyr.equals(normalized, ignoreCase = true) }
}

internal fun migrateLegacySavedRoutes(
    legacyRoutes: Set<String>,
    stops: List<StopEntity>,
): Set<SavedRoutePreference> {
    return legacyRoutes
        .mapNotNull { legacy ->
            val parts = legacy.split(" - ", limit = 2)
            if (parts.size != 2) return@mapNotNull null

            val fromStop = findStopByAnyName(stops, parts[0])
            val toStop = findStopByAnyName(stops, parts[1])
            if (fromStop == null || toStop == null) return@mapNotNull null

            SavedRoutePreference(
                fromStopId = fromStop.stopId,
                toStopId = toStop.stopId,
                fromFallbackName = parts[0],
                toFallbackName = parts[1],
            )
        }.toSet()
}

internal fun resolveSavedRouteLabel(
    route: SavedRoutePreference,
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
