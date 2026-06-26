package com.queukat.train.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.getNameForLanguage
import com.queukat.train.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConnectedRouteCard(
    option: ConnectedRouteOption,
    selectedDate: String,
    stationLanguage: String,
    onReminderClick: (DirectRoute) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val unknown = stringResource(R.string.unknown_label)
    val viaName = option.viaStop?.getNameForLanguage(stationLanguage) ?: unknown

    Card(
        modifier =
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ConnectedHeader(option, viaName, onReminderClick)
            ConnectedLegText(option.firstLeg, selectedDate, stationLanguage, locale, unknown)
            TransferWaitText(option.transferWaitMinutes)
            ConnectedLegText(option.secondLeg, selectedDate, stationLanguage, locale, unknown)
            TotalDurationText(option.totalDurationMinutes)
            ScheduleValidityText(option.firstLeg, option.secondLeg)
        }
    }
}

@Composable
private fun ConnectedHeader(
    option: ConnectedRouteOption,
    viaName: String,
    onReminderClick: (DirectRoute) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.label_transfer_at, viaName),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (option.firstLeg.shouldShowInternationalLabel() || option.secondLeg.shouldShowInternationalLabel()) {
                Text(
                    text = stringResource(R.string.label_international_train),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        IconButton(onClick = { onReminderClick(option.firstLeg) }) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = stringResource(R.string.label_reminder),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ConnectedLegText(
    route: DirectRoute,
    selectedDate: String,
    stationLanguage: String,
    locale: Locale,
    unknown: String,
) {
    val start = routeEndpointName(route, first = true, stationLanguage, unknown)
    val end = routeEndpointName(route, first = false, stationLanguage, unknown)
    val timeRange = routeTimeRange(route, selectedDate, locale)
    val train = route.trainNumber ?: unknown
    Text(
        text = stringResource(R.string.connected_leg_format, train, start, end, timeRange),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
    )
}

@Composable
private fun TransferWaitText(waitMinutes: Long) {
    val text =
        when (transferRisk(waitMinutes)) {
            TransferRisk.TooShort -> stringResource(R.string.label_transfer_too_short, formatDuration(waitMinutes))
            TransferRisk.Tight -> stringResource(R.string.label_transfer_tight, formatDuration(waitMinutes))
            TransferRisk.Normal -> stringResource(R.string.label_transfer_wait, formatDuration(waitMinutes))
        }
    val color =
        when (transferRisk(waitMinutes)) {
            TransferRisk.TooShort -> MaterialTheme.colorScheme.error
            TransferRisk.Tight -> MaterialTheme.colorScheme.tertiary
            TransferRisk.Normal -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        }
    Text(text = text, fontSize = 13.sp, color = color)
}

@Composable
private fun TotalDurationText(totalDurationMinutes: Long?) {
    if (totalDurationMinutes == null) return
    Text(
        text = stringResource(R.string.label_travel_time, formatDuration(totalDurationMinutes)),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    )
}

@Composable
private fun ScheduleValidityText(
    firstLeg: DirectRoute,
    secondLeg: DirectRoute,
) {
    val validTo =
        listOfNotNull(
            firstLeg.validTo ?: firstLeg.route?.validTo,
            secondLeg.validTo ?: secondLeg.route?.validTo,
        ).minOrNull()
            ?: return
    Text(
        text = stringResource(R.string.label_schedule_valid_until, validTo),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
}

private fun routeEndpointName(
    route: DirectRoute,
    first: Boolean,
    stationLanguage: String,
    unknown: String,
): String {
    val item =
        if (first) {
            route.timetableItems?.firstOrNull()
        } else {
            route.timetableItems?.lastOrNull()
        }
    val routeEndpoint =
        if (first) {
            route.startStation
        } else {
            route.endStation
        }
    return item?.routestop?.stop?.getNameForLanguage(stationLanguage)
        ?: routeEndpoint
        ?: unknown
}

private fun routeTimeRange(
    route: DirectRoute,
    selectedDate: String,
    locale: Locale,
): String {
    val departureMs = routeDepartureMs(route, selectedDate)
    val arrivalMs = routeArrivalMs(route, selectedDate)
    if (departureMs == null || arrivalMs == null) return "-"

    val fmt =
        SimpleDateFormat("HH:mm", locale).apply {
            timeZone = DateTimeUtils.TRAIN_TIME_ZONE
        }
    return "${fmt.format(departureMs)} - ${fmt.format(arrivalMs)}"
}

@Composable
private fun formatDuration(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.time_in_h_and_m, hours, minutes)
    } else {
        stringResource(R.string.time_in_m, totalMinutes)
    }
}
