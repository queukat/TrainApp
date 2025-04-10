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

/**
 * ViewModel  MainActivity.
 * -   (fromStation, toStation, selectedDate),
 * -   (stops),
 * -   (routes),
 * -   (fullRoute)  ,
 * -  ,  loading,
 * -   (handleReminderAction).
 */
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

    /**
     *   «»   SharedPreferences  ё  StateFlow.
     */
    fun loadSavedRoutes() {
        val routes = prefs.getStringSet("saved_routes", emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()
        _savedRoutes.value = routes
    }

    /**
     *  (from - to)  «» ,   StateFlow.
     */
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

    // --  :

    fun setFromStation(text: String) {
        _fromStation.value = text
    }

    fun setToStation(text: String) {
        _toStation.value = text
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // --   + cumulative ( ):

    fun loadStops(force: Boolean = false) {
        viewModelScope.launch {
            try {
                _loading.value = true
                repo.ensureStopsUpToDate(force)
                _stops.value = repo.getAllStopsFromDb()

                //  (  UI)  cumulative-
                repo.ensureCumulativeCached()
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

    // --    API ( ):

    fun loadRoutes(from: String, to: String, date: String) {
        // :   ё "Belgrade Center",   "Beograd Centar"
        var fromForApi = from
        var toForApi = to
        if (fromForApi.equals("Belgrade Center", ignoreCase = true)) {
            fromForApi = "Beograd Centar"
        }
        if (toForApi.equals("Belgrade Center", ignoreCase = true)) {
            toForApi = "Beograd Centar"
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                withTimeout(10_000) {
                    val r = repo.getRoutes(fromForApi, toForApi, date)
                    _routes.value = r
                    _errorMessage.value = null //   ,  
                    if (r == null) {
                        _errorMessage.value = getApplication<Application>().getString(R.string.toast_no_results)
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

    // --    ( cumulative):

    fun loadFullRoute(routeId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val dr = repo.getFullRouteFromCumulative(routeId)
            _fullRoute.value = dr
        }
    }

    fun clearFullRoute() {
        _fullRoute.value = null
    }

    // --   (Push / Calendar / Both / None):

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

            when (action) {
                "push" -> {
                    ReminderUtils.schedulePushNotification(context, trainNum, depMillis, minutesBefore)
                }
                "calendar" -> {
                    addEventToCalendar(context, route, depMillis)
                }
                "both" -> {
                    ReminderUtils.schedulePushNotification(context, trainNum, depMillis, minutesBefore)
                    addEventToCalendar(context, route, depMillis)
                }
                "none" -> Unit
            }
        }
    }

    private fun addEventToCalendar(context: Context, route: DirectRoute, departureTimeMs: Long) {
        val endTimeMs = departureTimeMs + 60L * 60_000
        val trainNum = route.TrainNumber ?: "Unknown"
        val fromSt = route.timetable_items?.firstOrNull()?.routestop?.stop?.Name_en ?: "From"
        val toSt   = route.timetable_items?.lastOrNull()?.routestop?.stop?.Name_en  ?: "To"

        val title = "Train $trainNum: $fromSt → $toSt"
        val desc = "Generated from handleReminderAction"

        ReminderUtils.scheduleCalendarEvent(
            context,
            title = title,
            description = desc,
            beginTimeMs = departureTimeMs,
            endTimeMs = endTimeMs,
            locationUri = null
        )
    }

    // -- ё «time to departure» ( UI),  :

    fun refreshTimeToDeparture() {
        //  «» routes,  Compose   (, " X ").
        _routes.value = _routes.value
    }

    // --  ,    :

    fun clearError() {
        _errorMessage.value = null
    }
}
