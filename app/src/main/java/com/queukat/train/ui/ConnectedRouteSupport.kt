package com.queukat.train.ui

import com.queukat.train.data.model.ConnectedRouteGroup
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.StopDto
import com.queukat.train.util.DateTimeUtils
import java.util.Calendar

private const val MILLIS_PER_MINUTE = 60_000L
private const val TIGHT_TRANSFER_MINUTES = 15L
private const val TOO_SHORT_TRANSFER_MINUTES = 5L

enum class TransferRisk {
    Normal,
    Tight,
    TooShort,
}

data class ConnectedRouteOption(
    val viaStop: StopDto?,
    val firstLeg: DirectRoute,
    val secondLeg: DirectRoute,
    val transferWaitMinutes: Long,
    val totalDurationMinutes: Long?,
)

fun RoutesResponse.connectedRouteOptions(selectedDate: String): List<ConnectedRouteOption> =
    connected
        .orEmpty()
        .flatMap { (viaStopKey, group) ->
            group.connectedRouteOptions(
                viaStopId = group.viaStop?.stopId ?: viaStopKey.toIntOrNull(),
                selectedDate = selectedDate,
            )
        }.sortedBy { option ->
            routeDepartureMs(option.firstLeg, selectedDate) ?: Long.MAX_VALUE
        }

fun transferRisk(waitMinutes: Long): TransferRisk =
    when {
        waitMinutes < TOO_SHORT_TRANSFER_MINUTES -> TransferRisk.TooShort
        waitMinutes < TIGHT_TRANSFER_MINUTES -> TransferRisk.Tight
        else -> TransferRisk.Normal
    }

private fun ConnectedRouteGroup.connectedRouteOptions(
    viaStopId: Int?,
    selectedDate: String,
): List<ConnectedRouteOption> {
    if (viaStopId == null || selectedDate.isBlank()) return emptyList()
    val finishRoutes = finish.orEmpty()
    if (finishRoutes.isEmpty()) return emptyList()

    return start.orEmpty().mapNotNull { firstLeg ->
        val firstArrivalAtTransfer =
            routeTimeAtStopMs(
                route = firstLeg,
                stopId = viaStopId,
                selectedDate = selectedDate,
                preferDeparture = false,
            ) ?: return@mapNotNull null

        finishRoutes
            .mapNotNull { secondLeg ->
                secondLeg.toConnectionCandidate(
                    firstLeg = firstLeg,
                    firstArrivalAtTransfer = firstArrivalAtTransfer,
                    viaStopId = viaStopId,
                    selectedDate = selectedDate,
                    viaStop = viaStop,
                )
            }.minByOrNull { option -> option.transferWaitMinutes }
    }
}

private fun DirectRoute.toConnectionCandidate(
    firstLeg: DirectRoute,
    firstArrivalAtTransfer: Long,
    viaStopId: Int,
    selectedDate: String,
    viaStop: StopDto?,
): ConnectedRouteOption? {
    val rawDeparture =
        routeTimeAtStopMs(
            route = this,
            stopId = viaStopId,
            selectedDate = selectedDate,
            preferDeparture = true,
        ) ?: return null

    val departureAtTransfer = adjustAfter(rawDeparture, firstArrivalAtTransfer)
    val transferWaitMinutes = (departureAtTransfer - firstArrivalAtTransfer) / MILLIS_PER_MINUTE
    if (transferWaitMinutes < 0) return null

    val firstDeparture = routeDepartureMs(firstLeg, selectedDate)
    val secondArrival =
        routeArrivalMs(this, selectedDate)
            ?.let { arrival -> adjustAfter(arrival, departureAtTransfer) }
    val totalDurationMinutes =
        if (firstDeparture != null && secondArrival != null) {
            ((secondArrival - firstDeparture) / MILLIS_PER_MINUTE).takeIf { it > 0 }
        } else {
            null
        }

    return ConnectedRouteOption(
        viaStop = viaStop,
        firstLeg = firstLeg,
        secondLeg = this,
        transferWaitMinutes = transferWaitMinutes,
        totalDurationMinutes = totalDurationMinutes,
    )
}

fun routeDepartureMs(
    route: DirectRoute,
    selectedDate: String,
): Long? =
    route
        .timetableItems
        ?.firstOrNull()
        ?.departureTime
        ?.let { time -> parseRouteTime(selectedDate, time) }

fun routeArrivalMs(
    route: DirectRoute,
    selectedDate: String,
): Long? =
    route
        .timetableItems
        ?.lastOrNull()
        ?.let { item -> item.arrivalTime ?: item.departureTime }
        ?.let { time -> parseRouteTime(selectedDate, time) }

private fun routeTimeAtStopMs(
    route: DirectRoute,
    stopId: Int,
    selectedDate: String,
    preferDeparture: Boolean,
): Long? {
    val item =
        route.timetableItems
            ?.firstOrNull { timetableItem -> timetableItem.routestop?.stopId == stopId }
            ?: return null
    val time =
        if (preferDeparture) {
            item.departureTime ?: item.arrivalTime
        } else {
            item.arrivalTime ?: item.departureTime
        }
    return time?.let { parseRouteTime(selectedDate, it) }
}

private fun parseRouteTime(
    selectedDate: String,
    time: String,
): Long? =
    if (selectedDate.isBlank() || time.isBlank()) {
        null
    } else {
        DateTimeUtils.parseDateTime("$selectedDate $time")?.time
    }

private fun adjustAfter(
    valueMs: Long,
    floorMs: Long,
): Long {
    if (valueMs >= floorMs) return valueMs
    return Calendar.getInstance(DateTimeUtils.TRAIN_TIME_ZONE).apply {
        timeInMillis = valueMs
        add(Calendar.DATE, 1)
    }.timeInMillis
}
