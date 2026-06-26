package com.queukat.train.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.PriceInfo
import com.queukat.train.data.model.RouteStop
import com.queukat.train.data.model.StopDto
import com.queukat.train.data.model.TimetableItem
import com.queukat.train.data.model.getNameForLanguage
import com.queukat.train.ui.theme.CustomGreen
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.ReminderUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val MILLIS_PER_MINUTE = 60_000L

@Composable
fun RouteCard(
    route: DirectRoute,
    selectedDate: String,
    stationLanguage: String,
    priceInfo: PriceInfo? = null,
    onReminderClick: (DirectRoute) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val unknownLabel = stringResource(R.string.unknown_label)
    val endpoints = routeEndpoints(route, stationLanguage, unknownLabel)
    val timing = routeTiming(route, selectedDate, locale)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .border(width = 1.dp, color = routeBorderColor(timing.isPast), shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = routeBackgroundColor(timing.isPast)),
    ) {
        RouteCardBody(
            state =
                RouteCardBodyState(
                    route = route,
                    endpoints = endpoints,
                    timing = timing,
                    stationLanguage = stationLanguage,
                    priceInfo = priceInfo,
                    locale = locale,
                    expanded = expanded,
                ),
            context = context,
            onReminderClick = onReminderClick,
        )
    }
}

@Composable
private fun RouteCardBody(
    state: RouteCardBodyState,
    context: android.content.Context,
    onReminderClick: (DirectRoute) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
    ) {
        RouteSummaryRow(
            route = state.route,
            endpoints = state.endpoints,
            timing = state.timing,
            priceInfo = state.priceInfo,
            locale = state.locale,
            onReminderClick = onReminderClick,
        )
        if (state.expanded) {
            Spacer(Modifier.height(6.dp))
            ExpandedStopsList(
                route = state.route,
                stationLanguage = state.stationLanguage,
                locale = state.locale,
                context = context,
            )
        }
    }
}

@Composable
private fun RouteSummaryRow(
    route: DirectRoute,
    endpoints: RouteEndpoints,
    timing: RouteTiming,
    priceInfo: PriceInfo?,
    locale: Locale,
    onReminderClick: (DirectRoute) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RouteTimesColumn(endpoints, timing)
        RouteActionsColumn(route, priceInfo, locale, onReminderClick)
    }
}

@Composable
private fun RowScope.RouteTimesColumn(
    endpoints: RouteEndpoints,
    timing: RouteTiming,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.train_label, endpoints.trainNumber, endpoints.startName, endpoints.endName),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        RouteTimeRangeText(timing)
        if (endpoints.isInternational) {
            Text(
                text = stringResource(R.string.label_international_train),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        DepartureStatusText(timing)
        TravelDurationText(timing)
        ScheduleValidityText(endpoints.validTo)
    }
}

@Composable
private fun RouteTimeRangeText(timing: RouteTiming) {
    if (timing.timeRange.isNotEmpty()) {
        Text(
            text = timing.timeRange,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun DepartureStatusText(timing: RouteTiming) {
    val departureMs = timing.departureMs
    if (!timing.isPast && departureMs != null) {
        val timeString =
            DateTimeUtils.getTimeUntilDepartureString(
                departureTimeMs = departureMs,
                nowMs = System.currentTimeMillis(),
                formatHourMin = stringResource(R.string.time_in_h_and_m),
                formatMin = stringResource(R.string.time_in_m),
                formatDayHour = stringResource(R.string.time_in_d_and_h),
                prefixFormat = stringResource(R.string.time_until_prefix),
            )
        if (timeString.isNotEmpty()) {
            Text(text = timeString, fontSize = 14.sp, color = CustomGreen)
        }
    } else {
        Text(
            text = stringResource(R.string.train_departed),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun TravelDurationText(timing: RouteTiming) {
    val durationMinutes = timing.durationMinutes
    if (!timing.isPast && durationMinutes != null) {
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        val durationText =
            if (hours > 0) {
                stringResource(R.string.time_in_h_and_m, hours, mins)
            } else {
                stringResource(R.string.time_in_m, durationMinutes)
            }
        Text(
            text = stringResource(R.string.label_travel_time, durationText),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ScheduleValidityText(validTo: String?) {
    if (!validTo.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.label_schedule_valid_until, validTo),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
        )
    }
}

@Composable
private fun RouteActionsColumn(
    route: DirectRoute,
    priceInfo: PriceInfo?,
    locale: Locale,
    onReminderClick: (DirectRoute) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        IconButton(onClick = { onReminderClick(route) }) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = stringResource(R.string.label_reminder),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        PriceInfoText(priceInfo, locale)
    }
}

@Composable
private fun PriceInfoText(
    priceInfo: PriceInfo?,
    locale: Locale,
) {
    val c1 = priceInfo?.class1Price
    val c2 = priceInfo?.class2Price
    val text =
        when {
            c1 != null && c2 != null ->
                stringResource(R.string.two_class_prices_format, euro(locale, c1), euro(locale, c2))
            c1 != null -> stringResource(R.string.one_class_price_format, euro(locale, c1))
            c2 != null -> stringResource(R.string.two_class_only_price_format, euro(locale, c2))
            else -> null
        }
    if (text != null) {
        Text(text = text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    } else {
        Text(
            text = stringResource(R.string.label_fare_unavailable),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
        )
    }
}

@Composable
private fun ExpandedStopsList(
    route: DirectRoute,
    stationLanguage: String,
    locale: Locale,
    context: android.content.Context,
) {
    val stopsList = route.timetableItems.orEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        stopsList.forEachIndexed { index, item ->
            RouteStopRow(
                item = item,
                isLast = index == stopsList.lastIndex,
                stationLanguage = stationLanguage,
                locale = locale,
                context = context,
            )
        }
    }
}

@Composable
private fun RouteStopRow(
    item: TimetableItem,
    isLast: Boolean,
    stationLanguage: String,
    locale: Locale,
    context: android.content.Context,
) {
    val stationName = item.routestop?.stop?.getNameForLanguage(stationLanguage) ?: stringResource(R.string.unknown_station)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopTimelineMarker(showLine = !isLast)
        Spacer(Modifier.width(8.dp))
        StopDetails(item, stationName, locale)
        StopMapButton(item, stationName, context)
    }
}

@Composable
private fun StopTimelineMarker(showLine: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val circleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = circleColor) }
        if (showLine) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun RowScope.StopDetails(
    item: TimetableItem,
    stationName: String,
    locale: Locale,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = stationName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        StopTimeText(item, locale)
        StopTypeText(item.routestop?.stop?.stopTypeId)
    }
}

@Composable
private fun StopTypeText(stopTypeId: Int?) {
    val labelRes = stopTypeLabelRes(stopTypeId) ?: return
    val color =
        if (isCrossingStopType(stopTypeId)) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        }
    Text(
        text = stringResource(labelRes),
        fontSize = 11.sp,
        color = color,
    )
}

@Composable
private fun StopTimeText(
    item: TimetableItem,
    locale: Locale,
) {
    val arrRaw = item.arrivalTime.orEmpty()
    val depRaw = item.departureTime.orEmpty()
    val shortArrival = arrRaw.takeTimePrefix()
    val shortDeparture = depRaw.takeTimePrefix()
    if (shortArrival.isNotEmpty() || shortDeparture.isNotEmpty()) {
        val dwellMin = getDwellMinutes(arrRaw, depRaw, locale)
        val lineText =
            if (dwellMin >= 5) {
                stringResource(R.string.stop_arr_dep_with_dwell, shortArrival, dwellMin, shortDeparture)
            } else {
                stringResource(R.string.stop_arr_dep, shortArrival, shortDeparture)
            }
        Text(
            text = lineText,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StopMapButton(
    item: TimetableItem,
    stationName: String,
    context: android.content.Context,
) {
    val lat = item.routestop?.stop?.latitude
    val lng = item.routestop?.stop?.longitude
    if (lat == null || lng == null) return

    IconButton(
        onClick = {
            ReminderUtils.openLocationInMaps(context, lat, lng, stationName)
        },
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google_map),
            contentDescription = stringResource(R.string.open_in_maps),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class RouteEndpoints(
    val startName: String,
    val endName: String,
    val trainNumber: String,
    val isInternational: Boolean,
    val validTo: String?,
)

private data class RouteCardBodyState(
    val route: DirectRoute,
    val endpoints: RouteEndpoints,
    val timing: RouteTiming,
    val stationLanguage: String,
    val priceInfo: PriceInfo?,
    val locale: Locale,
    val expanded: Boolean,
)

private data class RouteTiming(
    val departureMs: Long?,
    val isPast: Boolean,
    val timeRange: String,
    val durationMinutes: Long?,
)

@Composable
private fun routeBackgroundColor(isPast: Boolean): Color =
    if (isPast) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

@Composable
private fun routeBorderColor(isPast: Boolean): Color =
    if (isPast) Color.Transparent else MaterialTheme.colorScheme.primary

private fun routeEndpoints(
    route: DirectRoute,
    stationLanguage: String,
    unknownLabel: String,
): RouteEndpoints {
    val fallbackFirst = route.timetableItems?.firstOrNull()?.routestop?.stop?.getNameForLanguage(stationLanguage)
    val fallbackLast = route.timetableItems?.lastOrNull()?.routestop?.stop?.getNameForLanguage(stationLanguage)
    return RouteEndpoints(
        startName = fallbackFirst?.ifBlank { route.startStation ?: unknownLabel } ?: unknownLabel,
        endName = fallbackLast?.ifBlank { route.endStation ?: unknownLabel } ?: unknownLabel,
        trainNumber = route.trainNumber ?: unknownLabel,
        isInternational = route.shouldShowInternationalLabel(),
        validTo = route.validTo ?: route.route?.validTo,
    )
}

private fun routeTiming(
    route: DirectRoute,
    selectedDate: String,
    locale: Locale,
): RouteTiming {
    val departureRaw = route.timetableItems?.firstOrNull()?.departureTime.orEmpty()
    val arrivalRaw = route.arrivalTimeFallback()
    val departureDateTime = parseRouteDateTime(selectedDate, departureRaw)
    val arrivalDateTime = adjustedArrivalTime(departureDateTime, parseRouteDateTime(selectedDate, arrivalRaw))
    val departureMs = departureDateTime?.time
    val arrivalMs = arrivalDateTime?.time

    return RouteTiming(
        departureMs = departureMs,
        isPast = departureMs != null && departureMs < System.currentTimeMillis(),
        timeRange = timeRangeText(departureMs, arrivalMs, locale),
        durationMinutes = travelDurationMinutes(departureMs, arrivalMs),
    )
}

private fun DirectRoute.arrivalTimeFallback(): String =
    timetableItems
        ?.lastOrNull()
        ?.arrivalTime
        ?.takeIf { it.isNotBlank() }
        ?: timetableItems
            ?.lastOrNull()
            ?.departureTime
            .orEmpty()

private fun parseRouteDateTime(
    selectedDate: String,
    time: String,
): java.util.Date? =
    if (selectedDate.isNotBlank()) {
        DateTimeUtils.parseDateTime("$selectedDate $time")
    } else {
        null
    }

private fun adjustedArrivalTime(
    departure: java.util.Date?,
    arrival: java.util.Date?,
): java.util.Date? {
    if (departure == null || arrival == null || !arrival.before(departure)) return arrival
    return Calendar.getInstance(DateTimeUtils.TRAIN_TIME_ZONE).apply {
        time = arrival
        add(Calendar.DATE, 1)
    }.time
}

private fun timeRangeText(
    departureMs: Long?,
    arrivalMs: Long?,
    locale: Locale,
): String {
    if (departureMs == null || arrivalMs == null) return ""
    val fmt =
        SimpleDateFormat("HH:mm", locale).apply {
            timeZone = DateTimeUtils.TRAIN_TIME_ZONE
        }
    return "${fmt.format(departureMs)} - ${fmt.format(arrivalMs)}"
}

private fun travelDurationMinutes(
    departureMs: Long?,
    arrivalMs: Long?,
): Long? {
    if (departureMs == null || arrivalMs == null) return null
    val diffMin = (arrivalMs - departureMs) / MILLIS_PER_MINUTE
    return diffMin.takeIf { it > 0 }
}

private fun String.takeTimePrefix(): String = if (length >= 5) substring(0, 5) else this

private fun euro(
    locale: Locale,
    value: Double,
): String = String.format(locale, "%.2f€", value)

private fun getDwellMinutes(
    arrivalTime: String,
    departureTime: String,
    locale: Locale,
): Long {
    if (arrivalTime.isBlank() || departureTime.isBlank()) return 0
    val sdf = SimpleDateFormat("HH:mm:ss", locale)
    return try {
        val arrDate = sdf.parse(arrivalTime)
        val depDate = sdf.parse(departureTime)
        if (arrDate != null && depDate != null) {
            val diff = depDate.time - arrDate.time
            if (diff > 0) diff / MILLIS_PER_MINUTE else 0
        } else {
            0
        }
    } catch (_: Exception) {
        0
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewRouteCardLight() {
    TrainAppTheme(darkTheme = false) {
        val sampleRoute =
            DirectRoute(
                timetableId = 1,
                routeId = 101,
                trainNumber = "Local 745",
                trainTypeId = 0,
                international = 0,
                timetableItems =
                    listOf(
                        TimetableItem(
                            timetableItemId = 1,
                            timetableId = 1,
                            routeStopId = 100,
                            arrivalTime = "20:10:00",
                            departureTime = "20:15:00",
                            routestop =
                                RouteStop(
                                    routeStopId = 777,
                                    order = 1,
                                    stopId = 777,
                                    stop =
                                        StopDto(
                                            stopId = 777,
                                            nameMe = "Bar",
                                            nameEn = "Bar",
                                            nameMeCyr = "",
                                            stopTypeId = 4,
                                            latitude = 42.0876,
                                            longitude = 19.1052,
                                            local = 1,
                                            stopType = null,
                                        ),
                                ),
                        ),
                    ),
            )

        RouteCard(
            route = sampleRoute,
            selectedDate = "2025-04-06",
            stationLanguage = "en",
            priceInfo = null,
            onReminderClick = {},
        )
    }
}
