package com.queukat.train.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.core.net.toUri
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.ui.theme.TrainAppTheme
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: TrainViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current

    //    ViewModel
    val savedRoutes by mainViewModel.savedRoutes.collectAsState()
    val fromStation by mainViewModel.fromStation.collectAsState()
    val toStation by mainViewModel.toStation.collectAsState()
    val selectedDate by mainViewModel.selectedDate.collectAsState()
    val stops by mainViewModel.stops.collectAsState()
    val routesResponse by mainViewModel.routes.collectAsState()
    val loading by mainViewModel.loading.collectAsState()
    val errorMessage by mainViewModel.errorMessage.collectAsState()
    val fullRoute by mainViewModel.fullRoute.collectAsState()

    //      ё 
    LaunchedEffect(Unit) {
        mainViewModel.loadSavedRoutes()
    }

    // ё   prefs (    )
    val prefs: SharedPreferences = context.getSharedPreferences("train_prefs", Activity.MODE_PRIVATE)
    val lang = prefs.getString("appLanguage", Locale.getDefault().language) ?: "en"
    val autoRefreshTime = remember { mutableStateOf(prefs.getBoolean("autoRefreshTime", true)) }

    //   «» ViewModel,   « X »
    LaunchedEffect(autoRefreshTime.value) {
        while (true) {
            delay(60_000)
            if (autoRefreshTime.value) {
                mainViewModel.refreshTimeToDeparture()
            }
        }
    }

    //    (FullRouteDialog)
    var showFullRouteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(fullRoute) {
        showFullRouteDialog = (fullRoute != null)
    }

    //    (ReminderChoiceDialog)
    var reminderDialogRoute by remember { mutableStateOf<DirectRoute?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // --- Иконка «Пончик» слева (заливаем цветом primary) ---
                        FilledIconButton(
                            onClick = {
                                val uri = "https://ko-fi.com/queukat".toUri()
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    uri
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(26.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            // Если у вас есть своё "ic_donut", используйте painterResource(R.drawable.ic_donut)
                            Icon(
                                painter = painterResource(R.drawable.ic_donut_2),
                                contentDescription = stringResource(R.string.label_support_dev_on_ko_fi),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // --- Иконка «Настройки» справа ---
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.btn_settings)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background) // <- Фон всего экрана
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    // 1) Ошибка, если есть
                    if (!errorMessage.isNullOrEmpty()) {
                        item {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // 2) Поисковая панель (SearchPanel)
                    item {
                        SearchPanel(
                            fromStation = fromStation,
                            toStation = toStation,
                            selectedDate = selectedDate,
                            stops = stops,
                            language = lang,
                            onFromChanged = { mainViewModel.setFromStation(it) },
                            onToChanged = { mainViewModel.setToStation(it) },
                            onDatePicked = { dateStr -> mainViewModel.setSelectedDate(dateStr) },
                            onSearchClicked = {
                                if (fromStation.isBlank() || toStation.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        R.string.toast_select_stations_first,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@SearchPanel
                                }

                                val finalDate = selectedDate.ifBlank {
                                    val cal = Calendar.getInstance()
                                    String.format(
                                        Locale.getDefault(),
                                        "%04d-%02d-%02d",
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH) + 1,
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    )
                                }

                                mainViewModel.loadRoutes(fromStation, toStation, finalDate)
                            }
                        )
                    }

                    // 3) Блок сохранённых маршрутов (SavedRoutesBlock)
                    item {
                        SavedRoutesBlock(
                            fromStation = fromStation,
                            toStation = toStation,
                            savedRoutes = savedRoutes,
                            onSelectRoute = { routeStr ->
                                val parts = routeStr.split(" - ")
                                if (parts.size == 2) {
                                    mainViewModel.setFromStation(parts[0])
                                    mainViewModel.setToStation(parts[1])
                                }
                            },
                            onSaveRoute = {
                                if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                    mainViewModel.saveRoute(fromStation, toStation)
                                    Toast.makeText(
                                        context,
                                        R.string.toast_route_saved,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.toast_select_stations_first,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }

                    // 4) Если есть routesResponse – показываем direct/connected маршруты
                    routesResponse?.let { rr ->
                        val directRoutes = rr.direct.orEmpty()
                        val connectedRoutes = rr.connected.orEmpty()
                        val priceInfo = rr.price

                        if (directRoutes.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.direct_routes_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            items(directRoutes) { route ->
                                RouteCard(
                                    route = route,
                                    selectedDate = selectedDate,
                                    priceInfo = priceInfo,
                                    onTrainSelected = { chosen -> reminderDialogRoute = chosen },
                                    onFullRouteNeeded = { routeId ->
                                        mainViewModel.loadFullRoute(routeId)
                                    },
                                    onReminderClick = { chosen -> reminderDialogRoute = chosen }
                                )
                            }
                        }

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
                                    priceInfo = priceInfo,
                                    onTrainSelected = { chosen -> reminderDialogRoute = chosen },
                                    onFullRouteNeeded = { routeId ->
                                        mainViewModel.loadFullRoute(routeId)
                                    },
                                    onReminderClick = { chosen -> reminderDialogRoute = chosen }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог "Full Route"
    if (showFullRouteDialog && fullRoute != null) {
        FullRouteDialog(
            route = fullRoute!!.timetable_items ?: emptyList(),
            trainNumber = fullRoute!!.TrainNumber ?: stringResource(R.string.unknown_label),
            onDismiss = {
                showFullRouteDialog = false
                mainViewModel.clearFullRoute()
            }
        )
    }

    // Диалог выбора напоминания (ReminderChoiceDialog)
    if (reminderDialogRoute != null) {
        ReminderChoiceDialog(
            route = reminderDialogRoute!!,
            prefs = prefs,
            onDismiss = { reminderDialogRoute = null },
            onActionChosen = { action, minutes ->
                mainViewModel.handleReminderAction(
                    route = reminderDialogRoute!!,
                    context = context,
                    action = action,
                    minutesBefore = minutes
                )
                reminderDialogRoute = null
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "MainScreen Light Theme", showBackground = true)
@Composable
fun MainScreenLightPreview() {
    TrainAppTheme(darkTheme = false) {
        val context = LocalContext.current
        val previewVM = remember {
            PreviewTrainViewModel(context.applicationContext as Application)
        }
        MainScreen(
            mainViewModel = previewVM,
            onOpenSettings = {}
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "MainScreen Dark Theme", showBackground = true)
@Composable
fun MainScreenDarkPreview() {
    TrainAppTheme(darkTheme = true) {
        val context = LocalContext.current
        val previewVM = remember {
            PreviewTrainViewModel(context.applicationContext as Application)
        }
        MainScreen(
            mainViewModel = previewVM,
            onOpenSettings = {}
        )
    }
}
