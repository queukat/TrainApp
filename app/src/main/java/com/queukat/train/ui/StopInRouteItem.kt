// StopInRouteItem.kt
package com.queukat.train.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queukat.train.R

@Composable
fun StopInRouteItem(
    stationName: String,
    time: String,
    onShowMapClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stationName, fontSize = 14.sp, color = Color.Black)
            Text(time, fontSize = 12.sp, color = Color.Gray)
        }
        IconButton(onClick = onShowMapClicked) {
            Icon(
                painter = painterResource(R.drawable.ic_google_map),
                contentDescription = "Show on map"
            )
        }
    }
}
