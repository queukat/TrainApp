package com.queukat.train.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.queukat.train.R
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RecentSearchPreference
import com.queukat.train.data.model.SavedRoutePreference
import com.queukat.train.data.model.getNameForLanguage
import com.queukat.train.data.repository.StopsSyncResult
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.PushReminderScheduleResult
import com.queukat.train.util.ReminderUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "TrainViewModel"
private const val PREFS_NAME = "train_prefs"
private const val SAVED_ROUTES_KEY = "saved_routes_v2"
private const val LEGACY_SAVED_ROUTES_KEY = "saved_routes"
private const val RECENT_SEARCHES_KEY = "recent_searches_v1"
private const val ROUTE_LOOKUP_TIMEOUT_MS = 10_000L

open class TrainViewModel(
    application: Application,
    private val repo: TrainRepository,
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _savedRoutes = MutableStateFlow<List<SavedRoutePreference>>(emptyList())
    val savedRoutes = _savedRoutes.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<RecentSearchPreference>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()

    private val _fromStation = MutableStateFlow("")
    val fromStation = _fromStation.asStateFlow()

    private val _toStation = MutableStateFlow("")
    val toStation = _toStation.asStateFlow()

    private val _selectedDate = MutableStateFlow("")
    val selectedDate = _selectedDate.asStateFlow()

    private val fromStopIdState = MutableStateFlow<Int?>(null)
    private val toStopIdState = MutableStateFlow<Int?>(null)

    private val _stops = MutableStateFlow<List<StopEntity>>(emptyList())
    val stops = _stops.asStateFlow()

    private val _routeSearchState = MutableStateFlow<RouteSearchUiState>(RouteSearchUiState.Idle)
    val routeSearchState = _routeSearchState.asStateFlow()

    private val _stopsNotice = MutableStateFlow<UiNotice?>(null)
    val stopsNotice = _stopsNotice.asStateFlow()

    private val _fullRoute = MutableStateFlow<DirectRoute?>(null)
    val fullRoute = _fullRoute.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _reminderUiState = MutableStateFlow<ReminderUiState>(ReminderUiState.Idle)
    val reminderUiState = _reminderUiState.asStateFlow()

    init {
        loadSavedRoutes()
        loadRecentSearches()
    }

    fun loadSavedRoutes() {
        _savedRoutes.value =
            readSavedRoutesFromPrefs()
                .sortedBy { route ->
                    val fromName = findStopById(route.fromStopId)?.nameEn ?: route.fromFallbackName
                    val toName = findStopById(route.toStopId)?.nameEn ?: route.toFallbackName
                    "$fromName-$toName"
                }
    }

    fun loadRecentSearches() {
        _recentSearches.value =
            readRecentSearchesFromPrefs()
                .sortedByDescending { it.lastSearchedAtMs }
    }

    fun setFromStation(text: String) {
        _fromStation.value = text
        if (!selectedStopStillMatches(fromStopIdState.value, text)) {
            fromStopIdState.value = null
        }
    }

    fun setToStation(text: String) {
        _toStation.value = text
        if (!selectedStopStillMatches(toStopIdState.value, text)) {
            toStopIdState.value = null
        }
    }

    fun selectFromStop(
        stop: StopEntity,
        displayName: String,
    ) {
        fromStopIdState.value = stop.stopId
        _fromStation.value = displayName
    }

    fun selectToStop(
        stop: StopEntity,
        displayName: String,
    ) {
        toStopIdState.value = stop.stopId
        _toStation.value = displayName
    }

    fun applySavedRoute(
        route: SavedRoutePreference,
        language: String,
    ) {
        applyRouteSelection(
            fromStopId = route.fromStopId,
            toStopId = route.toStopId,
            fromFallbackName = route.fromFallbackName,
            toFallbackName = route.toFallbackName,
            language = language,
        )
    }

    fun repeatSavedRoute(
        route: SavedRoutePreference,
        language: String,
        preferredDate: String,
    ) {
        applySavedRoute(route, language)
        launchSearchForCurrentSelection(preferredDate)
    }

    fun repeatRecentSearch(
        route: RecentSearchPreference,
        language: String,
        preferredDate: String,
    ) {
        applyRouteSelection(
            fromStopId = route.fromStopId,
            toStopId = route.toStopId,
            fromFallbackName = route.fromFallbackName,
            toFallbackName = route.toFallbackName,
            language = language,
        )
        launchSearchForCurrentSelection(preferredDate)
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun loadStops(force: Boolean = false) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val cachedStops = repo.getAllStopsFromDb()

            if (cachedStops.isNotEmpty()) {
                _stops.value = cachedStops
                migrateLegacySavedRoutesIfNeeded()
                loadSavedRoutes()
                _loading.value = false
            } else {
                _loading.value = true
            }

            try {
                when (val syncResult = repo.ensureStopsUpToDate(force)) {
                    is StopsSyncResult.Refreshed,
                    StopsSyncResult.UpToDate,
                    -> {
                        _stops.value = repo.getAllStopsFromDb()
                        migrateLegacySavedRoutesIfNeeded()
                        loadSavedRoutes()
                        _stopsNotice.value = null
                    }

                    is StopsSyncResult.Failed -> {
                        _stopsNotice.value =
                            if (cachedStops.isNotEmpty()) {
                                UiNotice(
                                    message = app.getString(R.string.stops_refresh_using_cache),
                                    tone = UiNoticeTone.Warning,
                                )
                            } else {
                                UiNotice(
                                    message =
                                        app.getString(
                                            R.string.toast_failed_load_stops,
                                            syncResult.message ?: "",
                                        ),
                                    tone = UiNoticeTone.Error,
                                )
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stops: ${e.message}", e)
                _stopsNotice.value =
                    UiNotice(
                        message =
                            app.getString(
                                R.string.toast_failed_load_stops,
                                e.localizedMessage ?: "",
                            ),
                        tone = UiNoticeTone.Error,
                    )
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveRoute(
        from: String,
        to: String,
    ): Boolean {
        val fromStop = resolveStopForUserInput(from, fromStopIdState.value) ?: return false
        val toStop = resolveStopForUserInput(to, toStopIdState.value) ?: return false

        fromStopIdState.value = fromStop.stopId
        toStopIdState.value = toStop.stopId

        val current = readSavedRoutesFromPrefs().toMutableSet()
        current.add(
            SavedRoutePreference(
                fromStopId = fromStop.stopId,
                toStopId = toStop.stopId,
                fromFallbackName = fromStop.nameEn,
                toFallbackName = toStop.nameEn,
            ),
        )
        writeSavedRoutes(current)
        return true
    }

    fun loadRoutes(
        from: String,
        to: String,
        date: String,
    ) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val fromStop = resolveStopForUserInput(from, fromStopIdState.value)
            val toStop = resolveStopForUserInput(to, toStopIdState.value)

            if (fromStop == null || toStop == null) {
                _routeSearchState.value =
                    RouteSearchUiState.Error(
                        kind = RouteErrorKind.StationSelection,
                        notice =
                            UiNotice(
                                message = app.getString(R.string.toast_select_stations_first),
                                tone = UiNoticeTone.Warning,
                            ),
                    )
                return@launch
            }

            fromStopIdState.value = fromStop.stopId
            toStopIdState.value = toStop.stopId
            _loading.value = true
            _routeSearchState.value = RouteSearchUiState.Loading

            try {
                withTimeout(ROUTE_LOOKUP_TIMEOUT_MS) {
                    when (
                        val presentation =
                            repo
                                .getRoutes(fromStop.nameMe, toStop.nameMe, date)
                                .toRouteLookupPresentation()
                    ) {
                        is RouteLookupPresentation.Results -> {
                            recordRecentSearch(fromStop, toStop)
                            _routeSearchState.value =
                                RouteSearchUiState.Results(
                                    presentation.response,
                                )
                        }

                        RouteLookupPresentation.Empty -> {
                            recordRecentSearch(fromStop, toStop)
                            _routeSearchState.value = RouteSearchUiState.Empty
                        }

                        is RouteLookupPresentation.Error -> {
                            val messageRes =
                                when (presentation.kind) {
                                    RouteErrorKind.Server -> R.string.error_routes_server
                                    RouteErrorKind.Network -> R.string.error_routes_network
                                    RouteErrorKind.InvalidResponse -> R.string.error_routes_invalid_response
                                    RouteErrorKind.StationSelection -> R.string.toast_select_stations_first
                                }

                            val message =
                                if (presentation.kind == RouteErrorKind.Server) {
                                    app.getString(messageRes, presentation.httpCode ?: -1)
                                } else {
                                    app.getString(messageRes)
                                }

                            _routeSearchState.value =
                                RouteSearchUiState.Error(
                                    kind = presentation.kind,
                                    notice =
                                        UiNotice(
                                            message = message,
                                            tone = UiNoticeTone.Error,
                                        ),
                                )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Timed out loading routes", e)
                _routeSearchState.value =
                    RouteSearchUiState.Error(
                        kind = RouteErrorKind.Network,
                        notice =
                            UiNotice(
                                message = app.getString(R.string.error_routes_timeout),
                                tone = UiNoticeTone.Error,
                            ),
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load routes: ${e.message}", e)
                _routeSearchState.value =
                    RouteSearchUiState.Error(
                        kind = RouteErrorKind.InvalidResponse,
                        notice =
                            UiNotice(
                                message =
                                    app.getString(
                                        R.string.toast_failed_load_routes,
                                        e.localizedMessage ?: "",
                                    ),
                                tone = UiNoticeTone.Error,
                            ),
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

    fun reportNotificationPermissionDenied() {
        _reminderUiState.value =
            ReminderUiState.PermissionMissing(
                permission = ReminderPermissionKind.Notification,
                notice =
                    UiNotice(
                        message =
                            getApplication<Application>().getString(
                                R.string.reminder_status_notification_permission_missing,
                            ),
                        tone = UiNoticeTone.Warning,
                    ),
            )
    }

    fun clearReminderStatus() {
        _reminderUiState.value = ReminderUiState.Idle
    }

    fun handleReminderAction(
        route: DirectRoute,
        context: Context,
        action: String,
        minutesBefore: Int,
    ) {
        viewModelScope.launch {
            clearReminderStatus()

            if (action == "none") {
                return@launch
            }

            val depTime = route.timetable_items?.firstOrNull()?.DepartureTime ?: ""
            if (depTime.isBlank()) {
                _reminderUiState.value =
                    ReminderUiState.Failure(
                        UiNotice(
                            message = context.getString(R.string.toast_no_departure_time),
                            tone = UiNoticeTone.Error,
                        ),
                    )
                return@launch
            }

            val dateStr = selectedDate.value.ifBlank { return@launch }
            val depDateTime =
                DateTimeUtils.parseDateTime("$dateStr $depTime") ?: run {
                    _reminderUiState.value =
                        ReminderUiState.Failure(
                            UiNotice(
                                message = context.getString(R.string.toast_cant_parse_departure),
                                tone = UiNoticeTone.Error,
                            ),
                        )
                    return@launch
                }

            val depMillis = depDateTime.time
            val trainNum = route.TrainNumber ?: context.getString(R.string.unknown_label)
            val stationLanguage = stationLanguage()
            val stationName =
                route.timetable_items
                    ?.firstOrNull()
                    ?.routestop
                    ?.stop
                    ?.getNameForLanguage(stationLanguage)
                    ?: route.startStation
                    ?: context.getString(R.string.unknown_station)

            val pushResult =
                if (action == "push" || action == "both") {
                    ReminderUtils.schedulePushNotification(
                        context = context,
                        trainNumber = trainNum,
                        departureTimeMs = depMillis,
                        minutesBefore = minutesBefore,
                        stationName = stationName,
                    )
                } else {
                    null
                }

            val calendarOpened =
                if (action == "calendar" || action == "both") {
                    addEventToCalendar(context, route, depMillis)
                } else {
                    false
                }

            _reminderUiState.value =
                buildReminderState(
                    context = context,
                    action = action,
                    trainNumber = trainNum,
                    minutesBefore = minutesBefore,
                    pushResult = pushResult,
                    calendarOpened = calendarOpened,
                )
        }
    }

    private fun buildReminderState(
        context: Context,
        action: String,
        trainNumber: String,
        minutesBefore: Int,
        pushResult: PushReminderScheduleResult?,
        calendarOpened: Boolean,
    ): ReminderUiState =
        when (action) {
            "calendar" -> {
                if (calendarOpened) {
                    ReminderUiState.Success(
                        UiNotice(
                            message =
                                context.getString(
                                    R.string.reminder_status_calendar_opened,
                                    trainNumber,
                                ),
                            tone = UiNoticeTone.Success,
                        ),
                    )
                } else {
                    ReminderUiState.Failure(
                        UiNotice(
                            message =
                                context.getString(
                                    R.string.reminder_status_calendar_failed,
                                    trainNumber,
                                ),
                            tone = UiNoticeTone.Error,
                        ),
                    )
                }
            }

            "push" -> pushOnlyState(context, trainNumber, minutesBefore, pushResult)

            "both" ->
                pushAndCalendarState(
                    context = context,
                    trainNumber = trainNumber,
                    minutesBefore = minutesBefore,
                    pushResult = pushResult,
                    calendarOpened = calendarOpened,
                )

            else -> ReminderUiState.Idle
        }

    private fun pushOnlyState(
        context: Context,
        trainNumber: String,
        minutesBefore: Int,
        pushResult: PushReminderScheduleResult?,
    ): ReminderUiState =
        when (pushResult) {
            PushReminderScheduleResult.Scheduled ->
                ReminderUiState.Success(
                    UiNotice(
                        message =
                            context.getString(
                                R.string.reminder_status_push_created,
                                trainNumber,
                                minutesBefore,
                            ),
                        tone = UiNoticeTone.Success,
                    ),
                )

            PushReminderScheduleResult.NotificationPermissionMissing ->
                ReminderUiState.PermissionMissing(
                    permission = ReminderPermissionKind.Notification,
                    notice =
                        UiNotice(
                            message =
                                context.getString(
                                    R.string.reminder_status_notification_permission_missing,
                                ),
                            tone = UiNoticeTone.Warning,
                        ),
                )

            PushReminderScheduleResult.ExactAlarmPermissionMissing ->
                ReminderUiState.PermissionMissing(
                    permission = ReminderPermissionKind.ExactAlarm,
                    notice =
                        UiNotice(
                            message = context.getString(R.string.reminder_status_exact_alarm_missing),
                            tone = UiNoticeTone.Warning,
                        ),
                )

            PushReminderScheduleResult.TriggerTimeTooSoon ->
                ReminderUiState.Failure(
                    UiNotice(
                        message = context.getString(R.string.reminder_status_too_late),
                        tone = UiNoticeTone.Warning,
                    ),
                )

            is PushReminderScheduleResult.Failed ->
                ReminderUiState.Failure(
                    UiNotice(
                        message = context.getString(R.string.reminder_status_push_failed),
                        tone = UiNoticeTone.Error,
                    ),
                )

            null ->
                ReminderUiState.Failure(
                    UiNotice(
                        message = context.getString(R.string.reminder_status_push_failed),
                        tone = UiNoticeTone.Error,
                    ),
                )
        }

    private fun pushAndCalendarState(
        context: Context,
        trainNumber: String,
        minutesBefore: Int,
        pushResult: PushReminderScheduleResult?,
        calendarOpened: Boolean,
    ): ReminderUiState =
        when (pushResult) {
            PushReminderScheduleResult.Scheduled -> {
                if (calendarOpened) {
                    ReminderUiState.Success(
                        UiNotice(
                            message =
                                context.getString(
                                    R.string.reminder_status_push_and_calendar_created,
                                    trainNumber,
                                    minutesBefore,
                                ),
                            tone = UiNoticeTone.Success,
                        ),
                    )
                } else {
                    ReminderUiState.Failure(
                        UiNotice(
                            message =
                                context.getString(
                                    R.string.reminder_status_push_created_calendar_failed,
                                    trainNumber,
                                    minutesBefore,
                                ),
                            tone = UiNoticeTone.Warning,
                        ),
                    )
                }
            }

            PushReminderScheduleResult.NotificationPermissionMissing ->
                ReminderUiState.PermissionMissing(
                    permission = ReminderPermissionKind.Notification,
                    notice =
                        UiNotice(
                            message =
                                if (calendarOpened) {
                                    context.getString(
                                        R.string.reminder_status_calendar_opened_push_notification_missing,
                                        trainNumber,
                                    )
                                } else {
                                    context.getString(
                                        R.string.reminder_status_notification_permission_missing,
                                    )
                                },
                            tone = UiNoticeTone.Warning,
                        ),
                )

            PushReminderScheduleResult.ExactAlarmPermissionMissing ->
                ReminderUiState.PermissionMissing(
                    permission = ReminderPermissionKind.ExactAlarm,
                    notice =
                        UiNotice(
                            message =
                                if (calendarOpened) {
                                    context.getString(
                                        R.string.reminder_status_calendar_opened_push_exact_alarm_missing,
                                        trainNumber,
                                    )
                                } else {
                                    context.getString(R.string.reminder_status_exact_alarm_missing)
                                },
                            tone = UiNoticeTone.Warning,
                        ),
                )

            PushReminderScheduleResult.TriggerTimeTooSoon ->
                ReminderUiState.Failure(
                    UiNotice(
                        message =
                            if (calendarOpened) {
                                context.getString(
                                    R.string.reminder_status_calendar_opened_push_too_late,
                                    trainNumber,
                                )
                            } else {
                                context.getString(R.string.reminder_status_too_late)
                            },
                        tone = UiNoticeTone.Warning,
                    ),
                )

            is PushReminderScheduleResult.Failed,
            null,
            ->
                ReminderUiState.Failure(
                    UiNotice(
                        message =
                            if (calendarOpened) {
                                context.getString(
                                    R.string.reminder_status_calendar_opened_push_failed,
                                    trainNumber,
                                )
                            } else {
                                context.getString(R.string.reminder_status_push_failed)
                            },
                        tone = UiNoticeTone.Error,
                    ),
                )
        }

    private fun addEventToCalendar(
        context: Context,
        route: DirectRoute,
        departureTimeMs: Long,
    ): Boolean {
        val endTimeMs = departureTimeMs + 60L * 60_000
        val trainNum = route.TrainNumber ?: context.getString(R.string.unknown_label)
        val stationLanguage = stationLanguage()
        val fromSt =
            route.timetable_items
                ?.firstOrNull()
                ?.routestop
                ?.stop
                ?.getNameForLanguage(stationLanguage)
                ?: route.startStation
                ?: "From"
        val toSt =
            route.timetable_items
                ?.lastOrNull()
                ?.routestop
                ?.stop
                ?.getNameForLanguage(stationLanguage)
                ?: route.endStation
                ?: "To"

        val title = "Train $trainNum: $fromSt → $toSt"
        val desc = "Generated from reminder action"

        return ReminderUtils.scheduleCalendarEvent(
            context,
            title = title,
            description = desc,
            beginTimeMs = departureTimeMs,
            endTimeMs = endTimeMs,
            locationUri = null,
        )
    }

    fun refreshTimeToDeparture() {
        val current = _routeSearchState.value
        if (current is RouteSearchUiState.Results) {
            _routeSearchState.value = current.copy(response = current.response.copy())
        }
    }

    private fun selectedStopStillMatches(
        stopId: Int?,
        inputText: String,
    ): Boolean {
        if (inputText.isBlank()) return false
        val selectedStop = findStopById(stopId) ?: return true
        return stopMatchesText(selectedStop, inputText)
    }

    private fun resolveStopForUserInput(
        inputText: String,
        selectedStopId: Int?,
    ): StopEntity? = findStopById(selectedStopId) ?: findStopByAnyName(_stops.value, inputText)

    private fun findStopById(stopId: Int?): StopEntity? = _stops.value.firstOrNull { it.stopId == stopId }

    private fun readSavedRoutesFromPrefs(): List<SavedRoutePreference> =
        prefs
            .getStringSet(SAVED_ROUTES_KEY, emptySet())
            .orEmpty()
            .mapNotNull { raw ->
                runCatching {
                    gson.fromJson(raw, SavedRoutePreference::class.java)
                }.getOrNull()?.takeIf {
                    it.fromStopId > 0 && it.toStopId > 0
                }
            }

    private fun readRecentSearchesFromPrefs(): List<RecentSearchPreference> {
        val raw = prefs.getString(RECENT_SEARCHES_KEY, null) ?: return emptyList()
        return runCatching {
            gson
                .fromJson(raw, Array<RecentSearchPreference>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList()).filter {
            it.fromStopId > 0 && it.toStopId > 0
        }
    }

    private fun writeSavedRoutes(routes: Set<SavedRoutePreference>) {
        prefs.edit {
            putStringSet(
                SAVED_ROUTES_KEY,
                routes.map { gson.toJson(it) }.toSet(),
            )
        }
        loadSavedRoutes()
    }

    private fun writeRecentSearches(routes: List<RecentSearchPreference>) {
        prefs.edit {
            putString(RECENT_SEARCHES_KEY, gson.toJson(routes))
        }
        loadRecentSearches()
    }

    private fun migrateLegacySavedRoutesIfNeeded() {
        if (readSavedRoutesFromPrefs().isNotEmpty()) return
        if (_stops.value.isEmpty()) return

        val legacyRoutes = prefs.getStringSet(LEGACY_SAVED_ROUTES_KEY, emptySet()).orEmpty()
        if (legacyRoutes.isEmpty()) return

        val migrated = migrateLegacySavedRoutes(legacyRoutes, _stops.value)

        if (migrated.isNotEmpty()) {
            writeSavedRoutes(migrated)
        }
    }

    private fun stationLanguage(): String {
        val raw = prefs.getString("appLanguage", "en")
        return if (raw in setOf("en", "me", "meCyr")) raw!! else "en"
    }

    private fun applyRouteSelection(
        fromStopId: Int,
        toStopId: Int,
        fromFallbackName: String,
        toFallbackName: String,
        language: String,
    ) {
        fromStopIdState.value = fromStopId
        toStopIdState.value = toStopId
        _fromStation.value = findStopById(fromStopId)?.getNameForLanguage(language)
            ?: fromFallbackName
        _toStation.value = findStopById(toStopId)?.getNameForLanguage(language)
            ?: toFallbackName
    }

    private fun launchSearchForCurrentSelection(preferredDate: String) {
        val finalDate = preferredDate.ifBlank { DateTimeUtils.todayTrainDateString() }
        _selectedDate.value = finalDate
        loadRoutes(_fromStation.value, _toStation.value, finalDate)
    }

    private fun recordRecentSearch(
        fromStop: StopEntity,
        toStop: StopEntity,
    ) {
        writeRecentSearches(
            upsertRecentSearch(
                existing = readRecentSearchesFromPrefs(),
                fromStop = fromStop,
                toStop = toStop,
                searchedAtMs = System.currentTimeMillis(),
            ),
        )
    }
}
