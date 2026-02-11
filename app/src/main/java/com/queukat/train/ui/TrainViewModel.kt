package com.queukat.train.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.train.R
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.ReminderUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "TrainViewModel"

open class TrainViewModel(
    application: Application,
    private val repo: TrainRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)

    private val _savedRoutes = MutableStateFlow<List<String>>(emptyList())
    val savedRoutes = _savedRoutes.asStateFlow()

    init {
        loadSavedRoutes()
    }

    fun loadSavedRoutes() {
        val routes = prefs.getStringSet("saved_routes", emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()
        _savedRoutes.value = routes
    }

    fun saveRoute(from: String, to: String) {
        viewModelScope.launch {
            if (from.isNotBlank() && to.isNotBlank()) {
                val route = "$from - $to"
                val current = prefs.getStringSet("saved_routes", emptySet())?.toMutableSet() ?: mutableSetOf()
                current.add(route)
                prefs.edit { putStringSet("saved_routes", current) }
                loadSavedRoutes()
            }
        }
    }

    val _fromStation = MutableStateFlow("")
    val fromStation = _fromStation.asStateFlow()

    val _toStation = MutableStateFlow("")
    val toStation = _toStation.asStateFlow()

    val _selectedDate = MutableStateFlow("")
    val selectedDate = _selectedDate.asStateFlow()

    private val _stops = MutableStateFlow<List<StopEntity>>(emptyList())
    val stops = _stops.asStateFlow()

    private val _routes = MutableStateFlow<RoutesResponse?>(null)
    val routes = _routes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _fullRoute = MutableStateFlow<DirectRoute?>(null)
    val fullRoute = _fullRoute.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun setFromStation(text: String) { _fromStation.value = text }
    fun setToStation(text: String) { _toStation.value = text }
    fun setSelectedDate(date: String) { _selectedDate.value = date }

    fun loadStops(force: Boolean = false) {
        viewModelScope.launch {
            try {
                _loading.value = true
                repo.ensureStopsUpToDate(force)
                _stops.value = repo.getAllStopsFromDb()

                // ВАЖНО: не прогреваем cumulative на старте — это тормозит Android 9.
                // Cumulative подтянется только когда реально понадобится Full Route.

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stops: ${e.message}", e)
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.toast_failed_load_stops,
                    e.localizedMessage ?: ""
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadRoutes(from: String, to: String, date: String) {
        var fromForApi = from
        var toForApi = to
        if (fromForApi.equals("Belgrade Center", ignoreCase = true)) fromForApi = "Beograd Centar"
        if (toForApi.equals("Belgrade Center", ignoreCase = true)) toForApi = "Beograd Centar"

        viewModelScope.launch {
            _loading.value = true
            try {
                withTimeout(10_000) {
                    val r = repo.getRoutes(fromForApi, toForApi, date)
                    _routes.value = r
                    _errorMessage.value = null
                    if (r == null || (r.direct.isNullOrEmpty() && r.connected.isNullOrEmpty())) {
                        _errorMessage.value = getApplication<Application>().getString(R.string.toast_no_results)
                        if (r == null) _routes.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load routes: ${e.message}", e)
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.toast_failed_load_routes,
                    e.localizedMessage ?: ""
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadFullRoute(routeId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _fullRoute.value = repo.getFullRouteFromCumulative(routeId)
        }
    }

    fun clearFullRoute() {
        _fullRoute.value = null
    }

    fun handleReminderAction(
        route: DirectRoute,
        context: Context,
        action: String,
        minutesBefore: Int
    ) {
        viewModelScope.launch {
            val depTime = route.timetable_items?.firstOrNull()?.DepartureTime ?: ""
            if (depTime.isBlank()) {
                _errorMessage.value = context.getString(R.string.toast_no_departure_time)
                return@launch
            }

            val dateStr = selectedDate.value.ifBlank { return@launch }
            val depDateTime = DateTimeUtils.parseDateTime("$dateStr $depTime") ?: run {
                _errorMessage.value = context.getString(R.string.toast_cant_parse_departure)
                return@launch
            }

            val depMillis = depDateTime.time
            val trainNum = route.TrainNumber ?: "Unknown"

            val stationName = route.startStation
                ?: route.timetable_items?.firstOrNull()?.routestop?.stop?.Name_en
                ?: context.getString(R.string.unknown_station)

            when (action) {
                "push" -> ReminderUtils.schedulePushNotification(
                    context = context,
                    trainNumber = trainNum,
                    departureTimeMs = depMillis,
                    minutesBefore = minutesBefore,
                    stationName = stationName
                )
                "calendar" -> addEventToCalendar(context, route, depMillis)
                "both" -> {
                    ReminderUtils.schedulePushNotification(
                        context = context,
                        trainNumber = trainNum,
                        departureTimeMs = depMillis,
                        minutesBefore = minutesBefore,
                        stationName = stationName
                    )
                    addEventToCalendar(context, route, depMillis)
                }
                "none" -> Unit
            }
        }
    }

    private fun addEventToCalendar(context: Context, route: DirectRoute, departureTimeMs: Long) {
        val endTimeMs = departureTimeMs + 60L * 60_000
        val trainNum = route.TrainNumber ?: "Unknown"
        val fromSt = route.startStation
            ?: route.timetable_items?.firstOrNull()?.routestop?.stop?.Name_en
            ?: "From"
        val toSt = route.endStation
            ?: route.timetable_items?.lastOrNull()?.routestop?.stop?.Name_en
            ?: "To"

        val title = "Train $trainNum: $fromSt → $toSt"
        val desc = "Generated from reminder action"

        ReminderUtils.scheduleCalendarEvent(
            context,
            title = title,
            description = desc,
            beginTimeMs = departureTimeMs,
            endTimeMs = endTimeMs,
            locationUri = null
        )
    }

    fun refreshTimeToDeparture() {
        _routes.value = _routes.value?.copy()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
