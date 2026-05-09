package com.queukat.train.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.SavedRoutePreference
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.NotificationHelper
import kotlinx.coroutines.delay

private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: TrainViewModel,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current

    val savedRoutes by mainViewModel.savedRoutes.collectAsState()
    val recentSearches by mainViewModel.recentSearches.collectAsState()
    val fromStation by mainViewModel.fromStation.collectAsState()
    val toStation by mainViewModel.toStation.collectAsState()
    val selectedDate by mainViewModel.selectedDate.collectAsState()
    val stops by mainViewModel.stops.collectAsState()
    val routeSearchState by mainViewModel.routeSearchState.collectAsState()
    val stopsNotice by mainViewModel.stopsNotice.collectAsState()
    val loading by mainViewModel.loading.collectAsState()
    val fullRoute by mainViewModel.fullRoute.collectAsState()
    val reminderUiState by mainViewModel.reminderUiState.collectAsState()

    LaunchedEffect(Unit) {
        mainViewModel.loadSavedRoutes()
    }

    val prefs: SharedPreferences = context.getSharedPreferences("train_prefs", Activity.MODE_PRIVATE)

    // --- FIX: язык станций не должен зависеть от языка интерфейса ---
    // Если pref отсутствует/битый — ставим EN и сохраняем, чтобы дальше не “скакало”.
    val allowedLangs = setOf("en", "me", "meCyr")
    val lang =
        remember {
            val raw = prefs.getString("appLanguage", null)
            val normalized = if (raw in allowedLangs) raw!! else "en"
            if (raw == null || raw !in allowedLangs) {
                prefs.edit { putString("appLanguage", normalized) }
            }
            normalized
        }

    val autoRefreshTime = remember { mutableStateOf(prefs.getBoolean("autoRefreshTime", true)) }
    val routesResponse = (routeSearchState as? RouteSearchUiState.Results)?.response
    val routeNotice =
        when (val state = routeSearchState) {
            is RouteSearchUiState.Error -> state.notice
            RouteSearchUiState.Empty ->
                UiNotice(
                    message = stringResource(R.string.toast_no_results),
                    tone = UiNoticeTone.Info,
                )
            else -> null
        }
    val reminderNotice =
        when (val state = reminderUiState) {
            is ReminderUiState.Success -> state.notice
            is ReminderUiState.PermissionMissing -> state.notice
            is ReminderUiState.Failure -> state.notice
            ReminderUiState.Idle -> null
        }

    LaunchedEffect(autoRefreshTime.value) {
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MS)
            if (autoRefreshTime.value) {
                mainViewModel.refreshTimeToDeparture()
            }
        }
    }

    var showFullRouteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(fullRoute) {
        showFullRouteDialog = (fullRoute != null)
    }

    var reminderDialogRoute by remember { mutableStateOf<DirectRoute?>(null) }
    var pendingReminderRequest by remember { mutableStateOf<PendingReminderRequest?>(null) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val pending = pendingReminderRequest
            pendingReminderRequest = null

            if (granted && pending != null) {
                mainViewModel.handleReminderAction(
                    route = pending.route,
                    context = context,
                    action = pending.action,
                    minutesBefore = pending.minutesBefore,
                )
            } else {
                mainViewModel.reportNotificationPermissionDenied()
            }
        }

    LaunchedEffect(reminderUiState) {
        val state = reminderUiState
        if (
            state is ReminderUiState.PermissionMissing &&
            state.permission == ReminderPermissionKind.ExactAlarm &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    val savedRouteLabels =
        remember(savedRoutes, stops, lang) {
            val stopMap = stops.associateBy { it.stopId }
            savedRoutes.map { route ->
                route to routeLabel(route, stopMap, lang)
            }
        }
    val recentSearchLabels =
        remember(recentSearches, stops, lang) {
            val stopMap = stops.associateBy { it.stopId }
            recentSearches.map { route ->
                route to recentSearchLabel(route, stopMap, lang)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(
                            onClick = {
                                val uri = "https://ko-fi.com/queukat".toUri()
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(26.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_donut_2),
                                contentDescription = stringResource(R.string.label_support_dev_on_ko_fi),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }

                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.btn_settings),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (routeNotice != null) {
                        item {
                            StatusBanner(notice = routeNotice)
                        }
                    }

                    if (stopsNotice != null) {
                        item {
                            StatusBanner(notice = stopsNotice!!)
                        }
                    }

                    if (reminderNotice != null) {
                        item {
                            StatusBanner(notice = reminderNotice)
                        }
                    }

                    item {
                        SearchPanel(
                            fromStation = fromStation,
                            toStation = toStation,
                            selectedDate = selectedDate,
                            stops = stops,
                            language = lang,
                            onFromChanged = { mainViewModel.setFromStation(it) },
                            onToChanged = { mainViewModel.setToStation(it) },
                            onFromStopSelected = { stop, displayName ->
                                mainViewModel.selectFromStop(stop, displayName)
                            },
                            onToStopSelected = { stop, displayName ->
                                mainViewModel.selectToStop(stop, displayName)
                            },
                            onDatePicked = { dateStr -> mainViewModel.setSelectedDate(dateStr) },
                            onSearchClicked = {
                                if (fromStation.isBlank() || toStation.isBlank()) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.toast_select_stations_first,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@SearchPanel
                                }

                                val finalDate =
                                    selectedDate.ifBlank {
                                        DateTimeUtils.todayTrainDateString()
                                    }

                                mainViewModel.setSelectedDate(finalDate)
                                mainViewModel.loadRoutes(fromStation, toStation, finalDate)
                            },
                        )
                    }

                    item {
                        SavedRoutesBlock(
                            savedRoutes = savedRouteLabels,
                            recentSearches = recentSearchLabels,
                            onSelectRoute = { route ->
                                mainViewModel.repeatSavedRoute(route, lang, selectedDate)
                            },
                            onSelectRecentSearch = { route ->
                                mainViewModel.repeatRecentSearch(route, lang, selectedDate)
                            },
                            onSaveRoute = {
                                if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                    val saved = mainViewModel.saveRoute(fromStation, toStation)
                                    val messageRes =
                                        if (saved) {
                                            R.string.toast_route_saved
                                        } else {
                                            R.string.toast_select_stations_first
                                        }
                                    Toast
                                        .makeText(
                                            context,
                                            messageRes,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.toast_select_stations_first,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        )
                    }

                    routesResponse?.let { rr ->
                        val directRoutes = rr.direct.orEmpty()
                        val connectedRoutes = rr.connected.orEmpty()
                        val priceInfo = rr.price

                        if (directRoutes.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.direct_routes_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            items(directRoutes) { route ->
                                RouteCard(
                                    route = route,
                                    selectedDate = selectedDate,
                                    stationLanguage = lang,
                                    priceInfo = priceInfo,
                                    onReminderClick = { chosen -> reminderDialogRoute = chosen },
                                )
                            }
                        }

                        if (connectedRoutes.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.connected_routes_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            items(connectedRoutes) { route ->
                                RouteCard(
                                    route = route,
                                    selectedDate = selectedDate,
                                    stationLanguage = lang,
                                    priceInfo = priceInfo,
                                    onReminderClick = { chosen -> reminderDialogRoute = chosen },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullRouteDialog && fullRoute != null) {
        FullRouteDialog(
            route = fullRoute!!.timetable_items ?: emptyList(),
            trainNumber = fullRoute!!.TrainNumber ?: stringResource(R.string.unknown_label),
            stationLanguage = lang,
            onDismiss = {
                showFullRouteDialog = false
                mainViewModel.clearFullRoute()
            },
        )
    }

    if (reminderDialogRoute != null) {
        ReminderChoiceDialog(
            route = reminderDialogRoute!!,
            prefs = prefs,
            onDismiss = {
                reminderDialogRoute = null
                mainViewModel.clearReminderStatus()
            },
            onActionChosen = { action, minutes ->
                val route = reminderDialogRoute!!
                val requiresNotificationPermission =
                    (action == "push" || action == "both") &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !NotificationHelper.hasNotificationRuntimePermission(context)
                if (requiresNotificationPermission) {
                    pendingReminderRequest = PendingReminderRequest(route, action, minutes)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    mainViewModel.handleReminderAction(
                        route = route,
                        context = context,
                        action = action,
                        minutesBefore = minutes,
                    )
                }
                reminderDialogRoute = null
            },
        )
    }
}

@Preview(name = "MainScreen Light Theme", showBackground = true)
@Composable
fun MainScreenLightPreview() {
    TrainAppTheme(darkTheme = false) {
        val context = LocalContext.current
        val previewVM =
            remember {
                PreviewTrainViewModel(context.applicationContext as Application)
            }
        MainScreen(
            mainViewModel = previewVM,
            onOpenSettings = {},
        )
    }
}

@Preview(name = "MainScreen Dark Theme", showBackground = true)
@Composable
fun MainScreenDarkPreview() {
    TrainAppTheme(darkTheme = true) {
        val context = LocalContext.current
        val previewVM =
            remember {
                PreviewTrainViewModel(context.applicationContext as Application)
            }
        MainScreen(
            mainViewModel = previewVM,
            onOpenSettings = {},
        )
    }
}

private data class PendingReminderRequest(
    val route: DirectRoute,
    val action: String,
    val minutesBefore: Int,
)

private fun routeLabel(
    route: SavedRoutePreference,
    stopMap: Map<Int, com.queukat.train.data.db.StopEntity>,
    language: String,
): String = resolveSavedRouteLabel(route, stopMap, language)

private fun recentSearchLabel(
    route: com.queukat.train.data.model.RecentSearchPreference,
    stopMap: Map<Int, com.queukat.train.data.db.StopEntity>,
    language: String,
): String = resolveRecentSearchLabel(route, stopMap, language)
