package com.queukat.train.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.model.TimetableItem

/**
 * ,    .
 */
@Composable
fun FullRouteDialog(
    route: List<TimetableItem>,
    trainNumber: String,
    onDismiss: () -> Unit
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
                    val stationName = item.routestop?.stop?.Name_en ?: "Unknown"
                    val arr = item.ArrivalTime ?: "-"
                    val dep = item.DepartureTime ?: "-"
                    Text("$stationName | Arr: $arr, Dep: $dep")
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    )
}
