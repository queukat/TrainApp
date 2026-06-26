package com.queukat.train.ui

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
import androidx.compose.foundation.lazy.LazyListScope
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
import com.queukat.train.data.model.RecentSearchPreference
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.SavedRoutePreference
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.NotificationHelper
import kotlinx.coroutines.delay

private const val AUTO_REFRESH_INTERVAL_MS = 60_000L
private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

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
    val lang = rememberStationLanguage(prefs)

    val autoRefreshTime = remember { mutableStateOf(prefs.getBoolean("autoRefreshTime", true)) }
    val routesResponse = (routeSearchState as? RouteSearchUiState.Results)?.response
    val notices = MainNotices(routeNotice(routeSearchState), stopsNotice, reminderNotice(reminderUiState))

    AutoRefreshEffect(autoRefreshTime.value) { mainViewModel.refreshTimeToDeparture() }

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
            handleNotificationPermissionResult(
                granted = granted,
                pending = pendingReminderRequest,
                mainViewModel = mainViewModel,
                context = context,
            )
            pendingReminderRequest = null
        }

    ExactAlarmPermissionEffect(reminderUiState)

    val routeLabels = rememberRouteLabels(savedRoutes, recentSearches, stops, lang)

    Scaffold(
        topBar = { MainTopBar(onOpenSettings = onOpenSettings) },
    ) { innerPadding ->
        MainBody(
            modifier = Modifier.padding(innerPadding),
            state =
                MainBodyState(
                    loading = loading,
                    notices = notices,
                    searchState =
                        SearchPanelState(
                            fromStation = fromStation,
                            toStation = toStation,
                            selectedDate = selectedDate,
                            stops = stops,
                            language = lang,
                        ),
                    routeLabels = routeLabels,
                    routeResults = RouteResultsState(routesResponse, selectedDate, lang),
                ),
            actions =
                MainBodyActions(
                    searchActions =
                        SearchPanelActions(
                            onFromChanged = { mainViewModel.setFromStation(it) },
                            onToChanged = { mainViewModel.setToStation(it) },
                            onFromStopSelected = { stop, displayName -> mainViewModel.selectFromStop(stop, displayName) },
                            onToStopSelected = { stop, displayName -> mainViewModel.selectToStop(stop, displayName) },
                            onDatePicked = { dateStr -> mainViewModel.setSelectedDate(dateStr) },
                            onSearchClicked = {
                                performSearch(context, mainViewModel, fromStation, toStation, selectedDate)
                            },
                        ),
                    savedRouteActions =
                        SavedRouteActions(
                            onSelectRoute = { route -> mainViewModel.repeatSavedRoute(route, lang, selectedDate) },
                            onSelectRecentSearch = { route -> mainViewModel.repeatRecentSearch(route, lang, selectedDate) },
                            onSaveRoute = { saveCurrentRoute(context, mainViewModel, fromStation, toStation) },
                        ),
                    onReminderClick = { chosen -> reminderDialogRoute = chosen },
                ),
        )
    }

    FullRouteDialogHost(
        route = fullRoute,
        visible = showFullRouteDialog,
        stationLanguage = lang,
        onDismiss = {
            showFullRouteDialog = false
            mainViewModel.clearFullRoute()
        },
    )
    ReminderDialogHost(
        route = reminderDialogRoute,
        prefs = prefs,
        onDismiss = {
            reminderDialogRoute = null
            mainViewModel.clearReminderStatus()
        },
        onActionChosen = { route, action, minutes ->
            if (requiresNotificationPermission(action, context)) {
                pendingReminderRequest = PendingReminderRequest(route, action, minutes)
                notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
            } else {
                mainViewModel.handleReminderAction(route, context, action, minutes)
                reminderDialogRoute = null
            }
        },
    )
}

@Composable
private fun rememberStationLanguage(prefs: SharedPreferences): String =
    remember {
        val allowedLangs = setOf("en", "me", "meCyr")
        val raw = prefs.getString("appLanguage", null)
        val normalized = if (raw in allowedLangs) raw!! else "en"
        if (raw == null || raw !in allowedLangs) {
            prefs.edit { putString("appLanguage", normalized) }
        }
        normalized
    }

@Composable
private fun routeNotice(state: RouteSearchUiState): UiNotice? =
    when (state) {
        is RouteSearchUiState.Error -> state.notice
        RouteSearchUiState.Empty ->
            UiNotice(
                message = stringResource(R.string.toast_no_results),
                tone = UiNoticeTone.Info,
            )
        else -> null
    }

private fun reminderNotice(state: ReminderUiState): UiNotice? =
    when (state) {
        is ReminderUiState.Success -> state.notice
        is ReminderUiState.PermissionMissing -> state.notice
        is ReminderUiState.Failure -> state.notice
        ReminderUiState.Idle -> null
    }

@Composable
private fun AutoRefreshEffect(
    autoRefresh: Boolean,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(autoRefresh) {
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MS)
            if (autoRefresh) {
                onRefresh()
            }
        }
    }
}

@Composable
private fun ExactAlarmPermissionEffect(reminderUiState: ReminderUiState) {
    val context = LocalContext.current
    LaunchedEffect(reminderUiState) {
        if (shouldOpenExactAlarmSettings(reminderUiState)) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}

private fun shouldOpenExactAlarmSettings(state: ReminderUiState): Boolean =
    state is ReminderUiState.PermissionMissing &&
        state.permission == ReminderPermissionKind.ExactAlarm &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun rememberRouteLabels(
    savedRoutes: List<SavedRoutePreference>,
    recentSearches: List<RecentSearchPreference>,
    stops: List<com.queukat.train.data.db.StopEntity>,
    language: String,
): RouteLabels =
    remember(savedRoutes, recentSearches, stops, language) {
        val stopMap = stops.associateBy { it.stopId }
        RouteLabels(
            savedRoutes = savedRoutes.map { route -> route to routeLabel(route, stopMap, language) },
            recentSearches = recentSearches.map { route -> route to recentSearchLabel(route, stopMap, language) },
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
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
}

@Composable
private fun MainBody(
    modifier: Modifier,
    state: MainBodyState,
    actions: MainBodyActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(modifier)
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                noticeItems(state.notices)
                item {
                    SearchPanel(state = state.searchState, actions = actions.searchActions)
                }
                item {
                    SavedRoutesBlock(
                        savedRoutes = state.routeLabels.savedRoutes,
                        recentSearches = state.routeLabels.recentSearches,
                        onSelectRoute = actions.savedRouteActions.onSelectRoute,
                        onSelectRecentSearch = actions.savedRouteActions.onSelectRecentSearch,
                        onSaveRoute = actions.savedRouteActions.onSaveRoute,
                    )
                }
                routeResultsSection(state.routeResults, actions.onReminderClick)
            }
        }
    }
}

private fun LazyListScope.noticeItems(notices: MainNotices) {
    listOfNotNull(notices.routeNotice, notices.stopsNotice, notices.reminderNotice).forEach { notice ->
        item { StatusBanner(notice = notice) }
    }
}

private fun LazyListScope.routeResultsSection(
    state: RouteResultsState,
    onReminderClick: (DirectRoute) -> Unit,
) {
    val routes = state.routesResponse ?: return
    routeSection(
        titleRes = R.string.direct_routes_label,
        routes = routes.direct.orEmpty(),
        state = state,
        onReminderClick = onReminderClick,
    )
    connectedRouteSection(
        options = routes.connectedRouteOptions(state.selectedDate),
        state = state,
        onReminderClick = onReminderClick,
    )
}

private fun LazyListScope.routeSection(
    titleRes: Int,
    routes: List<DirectRoute>,
    state: RouteResultsState,
    onReminderClick: (DirectRoute) -> Unit,
) {
    if (routes.isNotEmpty()) {
        item {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
        items(routes) { route ->
            RouteCard(
                route = route,
                selectedDate = state.selectedDate,
                stationLanguage = state.stationLanguage,
                priceInfo = state.routesResponse?.price,
                onReminderClick = onReminderClick,
            )
        }
    }
}

private fun LazyListScope.connectedRouteSection(
    options: List<ConnectedRouteOption>,
    state: RouteResultsState,
    onReminderClick: (DirectRoute) -> Unit,
) {
    if (options.isNotEmpty()) {
        item {
            Text(
                text = stringResource(R.string.connected_routes_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
        items(options) { option ->
            ConnectedRouteCard(
                option = option,
                selectedDate = state.selectedDate,
                stationLanguage = state.stationLanguage,
                onReminderClick = onReminderClick,
            )
        }
    }
}

@Composable
private fun FullRouteDialogHost(
    route: DirectRoute?,
    visible: Boolean,
    stationLanguage: String,
    onDismiss: () -> Unit,
) {
    if (visible && route != null) {
        FullRouteDialog(
            route = route.timetableItems ?: emptyList(),
            trainNumber = route.trainNumber ?: stringResource(R.string.unknown_label),
            stationLanguage = stationLanguage,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ReminderDialogHost(
    route: DirectRoute?,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onActionChosen: (DirectRoute, String, Int) -> Unit,
) {
    if (route != null) {
        ReminderChoiceDialog(
            route = route,
            prefs = prefs,
            onDismiss = onDismiss,
            onActionChosen = { action, minutes -> onActionChosen(route, action, minutes) },
        )
    }
}

private fun handleNotificationPermissionResult(
    granted: Boolean,
    pending: PendingReminderRequest?,
    mainViewModel: TrainViewModel,
    context: android.content.Context,
) {
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

private fun requiresNotificationPermission(
    action: String,
    context: android.content.Context,
): Boolean =
    (action == "push" || action == "both") &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !NotificationHelper.hasNotificationRuntimePermission(context)

private fun performSearch(
    context: android.content.Context,
    mainViewModel: TrainViewModel,
    fromStation: String,
    toStation: String,
    selectedDate: String,
) {
    if (fromStation.isBlank() || toStation.isBlank()) {
        Toast.makeText(context, R.string.toast_select_stations_first, Toast.LENGTH_SHORT).show()
        return
    }

    val finalDate = selectedDate.ifBlank { DateTimeUtils.todayTrainDateString() }
    mainViewModel.setSelectedDate(finalDate)
    mainViewModel.loadRoutes(fromStation, toStation, finalDate)
}

private fun saveCurrentRoute(
    context: android.content.Context,
    mainViewModel: TrainViewModel,
    fromStation: String,
    toStation: String,
) {
    if (fromStation.isBlank() || toStation.isBlank()) {
        Toast.makeText(context, R.string.toast_select_stations_first, Toast.LENGTH_SHORT).show()
        return
    }

    val messageRes =
        if (mainViewModel.saveRoute(fromStation, toStation)) {
            R.string.toast_route_saved
        } else {
            R.string.toast_select_stations_first
        }
    Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
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

private data class MainNotices(
    val routeNotice: UiNotice?,
    val stopsNotice: UiNotice?,
    val reminderNotice: UiNotice?,
)

private data class RouteLabels(
    val savedRoutes: List<Pair<SavedRoutePreference, String>>,
    val recentSearches: List<Pair<RecentSearchPreference, String>>,
)

private data class RouteResultsState(
    val routesResponse: RoutesResponse?,
    val selectedDate: String,
    val stationLanguage: String,
)

private data class SavedRouteActions(
    val onSelectRoute: (SavedRoutePreference) -> Unit,
    val onSelectRecentSearch: (RecentSearchPreference) -> Unit,
    val onSaveRoute: () -> Unit,
)

private data class MainBodyState(
    val loading: Boolean,
    val notices: MainNotices,
    val searchState: SearchPanelState,
    val routeLabels: RouteLabels,
    val routeResults: RouteResultsState,
)

private data class MainBodyActions(
    val searchActions: SearchPanelActions,
    val savedRouteActions: SavedRouteActions,
    val onReminderClick: (DirectRoute) -> Unit,
)

private fun routeLabel(
    route: SavedRoutePreference,
    stopMap: Map<Int, com.queukat.train.data.db.StopEntity>,
    language: String,
): String = resolveSavedRouteLabel(route, stopMap, language)

private fun recentSearchLabel(
    route: RecentSearchPreference,
    stopMap: Map<Int, com.queukat.train.data.db.StopEntity>,
    language: String,
): String = resolveRecentSearchLabel(route, stopMap, language)
