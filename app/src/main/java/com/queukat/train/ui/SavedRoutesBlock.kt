package com.queukat.train.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.model.RecentSearchPreference
import com.queukat.train.data.model.SavedRoutePreference

@Composable
fun SavedRoutesBlock(
    savedRoutes: List<Pair<SavedRoutePreference, String>>,
    recentSearches: List<Pair<RecentSearchPreference, String>>,
    onSelectRoute: (SavedRoutePreference) -> Unit,
    onSelectRecentSearch: (RecentSearchPreference) -> Unit,
    onSaveRoute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_repeat_routes),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(12.dp))

            Button(onClick = onSaveRoute) {
                Text(stringResource(R.string.btn_save_route))
            }
        }

        if (savedRoutes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.label_favorite_routes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )

            savedRoutes.forEach { (route, label) ->
                RouteShortcutCard(
                    label = label,
                    supportingText = stringResource(R.string.label_favorite_route_shortcut),
                    onClick = { onSelectRoute(route) }
                )
            }
        }

        if (recentSearches.isNotEmpty()) {
            Text(
                text = stringResource(R.string.label_recent_searches),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )

            recentSearches.forEach { (route, label) ->
                RouteShortcutCard(
                    label = label,
                    supportingText = stringResource(
                        R.string.label_recent_search_last_used,
                        DateUtils.getRelativeTimeSpanString(
                            route.lastSearchedAtMs,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        )
                    ),
                    onClick = { onSelectRecentSearch(route) }
                )
            }
        }
    }
}

@Composable
private fun RouteShortcutCard(
    label: String,
    supportingText: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.btn_search),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SavedRoutesBlockPreview() {
    val savedRoutes = listOf(
        SavedRoutePreference(1, 8, "Bar", "Podgorica") to "Bar - Podgorica",
        SavedRoutePreference(8, 22, "Podgorica", "Bijelo Polje") to "Podgorica - Bijelo Polje"
    )
    val recentSearches = listOf(
        RecentSearchPreference(1, 8, "Bar", "Podgorica", 1_711_780_000_000L) to "Bar - Podgorica"
    )

    SavedRoutesBlock(
        savedRoutes = savedRoutes,
        recentSearches = recentSearches,
        onSelectRoute = {},
        onSelectRecentSearch = {},
        onSaveRoute = {}
    )
}
