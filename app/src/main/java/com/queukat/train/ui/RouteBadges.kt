package com.queukat.train.ui

import com.queukat.train.data.model.DirectRoute

private const val LOCAL_STOP_FLAG = 1

internal fun DirectRoute.shouldShowInternationalLabel(): Boolean {
    if (international != 1) return false

    val firstLocalFlag = timetableItems?.firstOrNull()?.routestop?.stop?.local
    val lastLocalFlag = timetableItems?.lastOrNull()?.routestop?.stop?.local
    val knownEndpointFlags = listOfNotNull(firstLocalFlag, lastLocalFlag)

    return knownEndpointFlags.size < 2 || knownEndpointFlags.any { flag -> flag != LOCAL_STOP_FLAG }
}
