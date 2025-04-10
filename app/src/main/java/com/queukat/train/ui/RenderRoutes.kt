package com.queukat.train.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse

/**
 *   : direct routes  connected routes,
 *  [selectedDate]  RouteCard   «ё/ ё».
 */
@Composable
fun RenderRoutes(
    routesResponse: RoutesResponse,
    selectedDate: String,
    onTrainSelected: (DirectRoute) -> Unit,
    onFullRouteNeeded: (Int) -> Unit
) {
    val directRoutes = routesResponse.direct.orEmpty()
    val connectedRoutes = routesResponse.connected.orEmpty()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Direct routes
        if (directRoutes.isNotEmpty()) {
            item {
                Text(
                    text = "Direct routes:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(directRoutes) { route ->
                RouteCard(
                    route = route,
                    selectedDate = selectedDate,   // : ё 
                    onTrainSelected = onTrainSelected,
                    onFullRouteNeeded = onFullRouteNeeded
                )
            }
        }
        // Connected routes
        if (connectedRoutes.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.connected_routes_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(connectedRoutes) { route ->
                RouteCard(
                    route = route,
                    selectedDate = selectedDate,
                    onTrainSelected = onTrainSelected,
                    onFullRouteNeeded = onFullRouteNeeded
                )
            }
        }
    }
}
