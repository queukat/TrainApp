package com.queukat.train.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queukat.train.R
import com.queukat.train.data.model.TimetableItem
import com.queukat.train.data.model.getNameForLanguage

@Composable
fun FullRouteDialog(
    route: List<TimetableItem>,
    trainNumber: String,
    stationLanguage: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK")
            }
        },
        title = {
            Text(text = stringResource(R.string.full_route_title, trainNumber))
        },
        text = {
            Column {
                route.forEach { item ->
                    FullRouteStopRow(item = item, stationLanguage = stationLanguage)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
    )
}

@Composable
private fun FullRouteStopRow(
    item: TimetableItem,
    stationLanguage: String,
) {
    val stationName = item.routestop?.stop?.getNameForLanguage(stationLanguage) ?: stringResource(R.string.unknown_station)
    val arr = item.arrivalTime ?: "-"
    val dep = item.departureTime ?: "-"
    val stopTypeId = item.routestop?.stop?.stopTypeId
    Column {
        Text(text = stringResource(R.string.full_route_stop_format, stationName, arr, dep))
        stopTypeLabelRes(stopTypeId)?.let { labelRes ->
            Text(
                text = stringResource(labelRes),
                fontSize = 11.sp,
                color =
                    if (isCrossingStopType(stopTypeId)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
