package com.queukat.train.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queukat.train.R
import com.queukat.train.data.model.*
import com.queukat.train.ui.theme.CustomGreen
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.PriceInfo
import com.queukat.train.data.model.RouteStop
import com.queukat.train.data.model.StopDto
import com.queukat.train.data.model.TimetableItem
import com.queukat.train.util.ReminderUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RouteCard(
    route: DirectRoute,
    selectedDate: String,
    priceInfo: PriceInfo? = null,
    onTrainSelected: (DirectRoute) -> Unit,
    onFullRouteNeeded: (Int) -> Unit,
    onReminderClick: (DirectRoute) -> Unit = {}
) {
    val context = LocalContext.current

    // Fallback ( route.startStation / endStation  )
    val fallbackFirst = route.timetable_items
        ?.firstOrNull()
        ?.routestop
        ?.stop
        ?.Name_en
        ?: stringResource(R.string.unknown_label)

    val fallbackLast = route.timetable_items
        ?.lastOrNull()
        ?.routestop
        ?.stop
        ?.Name_en
        ?: stringResource(R.string.unknown_label)

    val startName = route.startStation ?: fallbackFirst
    val endName   = route.endStation   ?: fallbackLast
    val trainNum  = route.TrainNumber ?: stringResource(R.string.unknown_label)

    // 1) ё    / 
    val departureTimeString = route.timetable_items?.firstOrNull()?.DepartureTime ?: ""
    val lastArrivalTimeString = route.timetable_items?.lastOrNull()?.ArrivalTime ?: ""

    // 2)  datetime ( selectedDate)
    val departureDateTime = if (selectedDate.isNotBlank()) {
        DateTimeUtils.parseDateTime("$selectedDate $departureTimeString")
    } else null

    // 2)  
    var arrivalDateTime = if (selectedDate.isNotBlank()) {
        DateTimeUtils.parseDateTime("$selectedDate $lastArrivalTimeString")
    } else null

    // 3)   <  =>  
    if (departureDateTime != null && arrivalDateTime != null) {
        if (arrivalDateTime.before(departureDateTime)) {
            val cal = Calendar.getInstance()
            cal.time = arrivalDateTime
            cal.add(Calendar.DATE, 1) // +1 
            arrivalDateTime = cal.time
        }
    }

    // 4)   
    val departureMs = departureDateTime?.time
    val arrivalMs = arrivalDateTime?.time

    // , «ё  »
    val isPast = (departureMs != null && departureMs < System.currentTimeMillis())

    //  :   ё — variant,  surface
    val cardBackgroundColor = if (isPast) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    //  :    ё — primary,  
    val borderColor = if (isPast) Color.Transparent else MaterialTheme.colorScheme.primary

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                //   (Train..., " X "  " ...")
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // : "Train 432 (Bar - Beograd Centar)"
                    Text(
                        text = stringResource(R.string.train_label, trainNum, startName, endName),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    // " X "  " ё"
                    if (!isPast && departureMs != null) {
                        val timeString = DateTimeUtils.getTimeUntilDepartureString(
                            departureTimeMs = departureMs,
                            nowMs = System.currentTimeMillis(),
                            formatHourMin = stringResource(R.string.time_in_h_and_m),
                            formatMin = stringResource(R.string.time_in_m)
                        )
                        if (timeString.isNotEmpty()) {
                            Text(
                                text = timeString,
                                fontSize = 14.sp,
                                color = CustomGreen
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.train_departed),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // " : X  Y "
                    if (!isPast && departureMs != null && arrivalMs != null) {
                        val diffMin = (arrivalMs - departureMs) / 60000
                        if (diffMin > 0) {
                            val hours = diffMin / 60
                            val mins = diffMin % 60
                            val durationStr = if (hours > 0) {
                                stringResource(R.string.time_in_h_and_m, hours, mins)
                            } else {
                                stringResource(R.string.time_in_m, diffMin)
                            }
                            Text(
                                text = stringResource(R.string.label_travel_time, durationStr),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                //   -    
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { onReminderClick(route) }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.label_reminder),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    //  ,  
                    priceInfo?.let { pi ->
                        val c1 = pi.Class1Price
                        val c2 = pi.Class2Price
                        if (c1 != null || c2 != null) {
                            if (c1 != null && c2 != null) {
                                val s1 = String.format(Locale.getDefault(), "%.2f€", c1)
                                val s2 = String.format(Locale.getDefault(), "%.2f€", c2)
                                Text(
                                    text = stringResource(R.string.two_class_prices_format, s1, s2),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else if (c1 != null) {
                                val s1 = String.format(Locale.getDefault(), "%.2f€", c1)
                                Text(
                                    text = stringResource(R.string.one_class_price_format, s1),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else if (c2 != null) {
                                val s2 = String.format(Locale.getDefault(), "%.2f€", c2)
                                Text(
                                    text = stringResource(R.string.two_class_only_price_format, s2),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            //    
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                val stopsList = route.timetable_items.orEmpty()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    stopsList.forEachIndexed { index, item ->
                        val arrRaw = item.ArrivalTime ?: ""
                        val depRaw = item.DepartureTime ?: ""
                        val shortArrival = if (arrRaw.length >= 5) arrRaw.substring(0, 5) else arrRaw
                        val shortDeparture = if (depRaw.length >= 5) depRaw.substring(0, 5) else depRaw
                        val dwellMin = getDwellMinutes(arrRaw, depRaw)

                        val stationName = item.routestop?.stop?.Name_en
                            ?: stringResource(R.string.unknown_station)
                        val stopTypeId = item.routestop?.stop?.StopTypeID

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val circleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                Canvas(modifier = Modifier.size(8.dp)) {
                                    drawCircle(color = circleColor)
                                }
                                if (index < stopsList.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(30.dp)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))

                            //  :  , ,  
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stationName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (shortArrival.isNotEmpty() || shortDeparture.isNotEmpty()) {
                                    val lineText = if (dwellMin >= 5) {
                                        stringResource(
                                            R.string.stop_arr_dep_with_dwell,
                                            shortArrival,
                                            dwellMin,
                                            shortDeparture
                                        )
                                    } else {
                                        stringResource(
                                            R.string.stop_arr_dep,
                                            shortArrival,
                                            shortDeparture
                                        )
                                    }
                                    Text(
                                        text = lineText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                //  stopTypeId=3 → "cargo crossing only"
                                if (stopTypeId == 3) {
                                    Text(
                                        text = stringResource(R.string.label_crossing_no_passengers),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val lat = item.routestop?.stop?.Latitude ?: 42.0
                                    val lng = item.routestop?.stop?.Longitude ?: 19.0
                                    ReminderUtils.openLocationInMaps(context, lat, lng, stationName)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_map),
                                    contentDescription = stringResource(R.string.open_in_maps),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ё    (dep - arr).      <0, ё 0.
 */
private fun getDwellMinutes(arrivalTime: String, departureTime: String): Long {
    if (arrivalTime.isBlank() || departureTime.isBlank()) return 0
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return try {
        val arrDate = sdf.parse(arrivalTime)
        val depDate = sdf.parse(departureTime)
        if (arrDate != null && depDate != null) {
            val diff = depDate.time - arrDate.time
            if (diff > 0) diff / 60000 else 0
        } else 0
    } catch (e: Exception) {
        0
    }
}

@Composable
@Preview(name = "RouteCard Light Theme", showBackground = true)
fun PreviewRouteCardLight() {
    TrainAppTheme(darkTheme = false) {
        val sampleRoute = DirectRoute(
            TimetableID = 1,
            RouteID = 101,
            TrainNumber = "Local 745",
            TrainTypeID = 0,
            International = 0,
            timetable_items = listOf(
                TimetableItem(
                    TimetableItemID = 1,
                    TimetableID = 1,
                    RouteStopID = 100,
                    ArrivalTime = "20:10:00",
                    DepartureTime = "20:15:00",
                    routestop = RouteStop(
                        RouteStopID = 777,
                        Order = 1,
                        StopID = 777,
                        stop = StopDto(
                            StopID = 777,
                            Name_me = "Bar",
                            Name_en = "Bar",
                            Name_me_cyr = "",
                            StopTypeID = 4,
                            Latitude = 42.0876,
                            Longitude = 19.1052,
                            local = 1,
                            stop_type = null
                        )
                    )
                ),
                TimetableItem(
                    TimetableItemID = 2,
                    TimetableID = 1,
                    RouteStopID = 101,
                    ArrivalTime = "07:05:00", //  
                    DepartureTime = "07:10:00",
                    routestop = RouteStop(
                        RouteStopID = 888,
                        Order = 2,
                        StopID = 888,
                        stop = StopDto(
                            StopID = 888,
                            Name_me = "Beograd Centar",
                            Name_en = "Belgrade Center",
                            Name_me_cyr = " ",
                            StopTypeID = 4,
                            Latitude = 44.820599,
                            Longitude = 20.4622,
                            local = 0,
                            stop_type = null
                        )
                    )
                )
            )
        )

        val samplePrice = PriceInfo(
            PricelistID = 99,
            StopFromID = 1,
            StopToID = 2,
            Class1Price = 5.50,
            Class2Price = 4.20
        )

        RouteCard(
            route = sampleRoute,
            selectedDate = "2025-04-06",
            priceInfo = samplePrice,
            onTrainSelected = {},
            onFullRouteNeeded = {},
            onReminderClick = {}
        )
    }
}

@Composable
@Preview(name = "RouteCard Dark Theme", showBackground = true)
fun PreviewRouteCardDark() {
    TrainAppTheme(darkTheme = true) {
        val sampleRoute = DirectRoute(
            TimetableID = 1,
            RouteID = 101,
            TrainNumber = "Local 745",
            TrainTypeID = 0,
            International = 0,
            timetable_items = listOf(
                TimetableItem(
                    TimetableItemID = 1,
                    TimetableID = 1,
                    RouteStopID = 100,
                    ArrivalTime = "20:10:00",
                    DepartureTime = "20:15:00",
                    routestop = RouteStop(
                        RouteStopID = 777,
                        Order = 1,
                        StopID = 777,
                        stop = StopDto(
                            StopID = 777,
                            Name_me = "Bar",
                            Name_en = "Bar",
                            Name_me_cyr = "",
                            StopTypeID = 4,
                            Latitude = 42.0876,
                            Longitude = 19.1052,
                            local = 1,
                            stop_type = null
                        )
                    )
                ),
                TimetableItem(
                    TimetableItemID = 2,
                    TimetableID = 1,
                    RouteStopID = 101,
                    ArrivalTime = "07:05:00", //  
                    DepartureTime = "07:10:00",
                    routestop = RouteStop(
                        RouteStopID = 888,
                        Order = 2,
                        StopID = 888,
                        stop = StopDto(
                            StopID = 888,
                            Name_me = "Beograd Centar",
                            Name_en = "Belgrade Center",
                            Name_me_cyr = " ",
                            StopTypeID = 4,
                            Latitude = 44.820599,
                            Longitude = 20.4622,
                            local = 0,
                            stop_type = null
                        )
                    )
                )
            )
        )

        val samplePrice = PriceInfo(
            PricelistID = 99,
            StopFromID = 1,
            StopToID = 2,
            Class1Price = 5.50,
            Class2Price = 4.20
        )

        RouteCard(
            route = sampleRoute,
            selectedDate = "2025-04-06",
            priceInfo = samplePrice,
            onTrainSelected = {},
            onFullRouteNeeded = {},
            onReminderClick = {}
        )
    }
}
