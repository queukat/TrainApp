package com.queukat.train.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.queukat.train.data.api.RetrofitClient
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.RouteInfoEntity
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.connectedRouteSegments
import com.queukat.train.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "TrainRepository"
private const val ERROR_BODY_PREVIEW_LENGTH = 200

// сколько держим cumulative в памяти прежде чем освежить (без дорогого парса всего JSON)
private const val CUMULATIVE_TTL_MS = 12L * 60 * 60 * 1000 // 12 часов

open class TrainRepository(
    private val db: AppDatabase,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.IO,
) {
    @Volatile
    private var cachedCumulative: String? = null

    @Volatile
    private var cachedCumulativeAtMs: Long = 0L

    @Volatile
    private var stopsMapCache: Map<Int, StopEntity>? = null

    suspend fun ensureStopsUpToDate(force: Boolean = false): StopsSyncResult =
        withContext(ioDispatcher) {
            val prefs = context.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong("stops_last_update", 0L)
            val now = System.currentTimeMillis()
            val countLocal = db.appDao().countStops()

            if (shouldRefreshStops(force, countLocal, lastUpdate, now)) {
                refreshStops(prefs, now)
            } else {
                Log.d(TAG, "Not updating stops: already have data & <24h since last update.")
                StopsSyncResult.UpToDate
            }
        }

    private fun shouldRefreshStops(
        force: Boolean,
        localCount: Int,
        lastUpdate: Long,
        now: Long,
    ): Boolean {
        val oneDayMillis = 24L * 60 * 60 * 1000
        return force || localCount == 0 || now - lastUpdate > oneDayMillis
    }

    private suspend fun refreshStops(
        prefs: SharedPreferences,
        now: Long,
    ): StopsSyncResult =
        try {
            val response = RetrofitClient.api.getStops().execute()
            if (response.isSuccessful) {
                val entities = response.body().orEmpty().mapNotNull(::stopEntityFromDto)
                db.appDao().insertAllStops(entities)
                prefs.edit { putLong("stops_last_update", now) }
                stopsMapCache = entities.associateBy { it.stopId }
                StopsSyncResult.Refreshed(entities.size)
            } else {
                Log.e(TAG, "ensureStopsUpToDate failed: ${response.code()} ${response.message()}")
                StopsSyncResult.Failed("HTTP ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stops: ${e.message}", e)
            StopsSyncResult.Failed(e.localizedMessage)
        }

    private fun stopEntityFromDto(dto: com.queukat.train.data.model.StopDto): StopEntity? {
        val stopId = dto.stopId ?: return null
        val nameMe = dto.nameMe ?: return null
        return StopEntity(
            stopId = stopId,
            nameEn = dto.nameEn ?: "",
            nameMe = nameMe,
            nameMeCyr = dto.nameMeCyr,
            stopTypeId = dto.stopTypeId,
            latitude = dto.latitude,
            longitude = dto.longitude,
            local = dto.local,
        )
    }

    open suspend fun getAllStopsFromDb(): List<StopEntity> = withContext(ioDispatcher) { db.appDao().getAllStops() }

    /**
     * ВАЖНО: тут НЕ трогаем cumulative (Android 9 будет лагать).
     * start/end берем из timetable (или fallback на UI).
     */
    open suspend fun getRoutes(
        start: String,
        finish: String,
        date: String,
    ): RouteLookupResult {
        return withContext(ioDispatcher) {
            try {
                val response = RetrofitClient.api.getRoutes(start, finish, date).execute()
                if (response.isSuccessful) {
                    val routes =
                        response.body()
                            ?: return@withContext RouteLookupResult.InvalidResponse("Empty response body")

                    routes.let {
                        fixCoordinates(routes)
                        fillStartEndStationFromTimetable(routes)
                    }
                    RouteLookupResult.Success(routes)
                } else {
                    Log.e(TAG, "getRoutes failed: ${response.code()} ${response.message()}")
                    RouteLookupResult.HttpError(
                        code = response.code(),
                        message = response.message(),
                        responsePreview = response.errorBody()?.string()?.take(ERROR_BODY_PREVIEW_LENGTH),
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error in getRoutes: ${e.message}", e)
                RouteLookupResult.NetworkError(e.localizedMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error in getRoutes: ${e.message}", e)
                RouteLookupResult.InvalidResponse(e.localizedMessage)
            }
        }
    }

    /**
     * Cumulative держим в памяти с TTL, без тяжёлого parseMinValidTo().
     * Это ключевой фикс для Android 9.
     */
    suspend fun ensureCumulativeCached(force: Boolean = false) {
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val hasFreshCache = cachedCumulative != null && (now - cachedCumulativeAtMs) < CUMULATIVE_TTL_MS
            if (!force && hasFreshCache) return@withContext

            try {
                val resp = RetrofitClient.api.getCumulativeRoutes().execute()
                if (resp.isSuccessful) {
                    cachedCumulative = resp.body()?.string()
                    cachedCumulativeAtMs = now
                    Log.d(TAG, "Cumulative routes refreshed in memory.")
                } else {
                    Log.e(TAG, "Failed to fetch cumulative: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching cumulative routes: ${e.message}", e)
            }
        }
    }

    /**
     * Full route — вот тут cumulative реально нужен.
     * Фикс: ищем и в "direct", и в "connected" (на всякий случай).
     */
    open suspend fun getFullRouteFromCumulative(timetableId: Int): DirectRoute? {
        ensureCumulativeCached()

        val bigJson = cachedCumulative ?: return null
        return withContext(ioDispatcher) {
            try {
                val gson = Gson()
                val rootObj = gson.fromJson(bigJson, JsonObject::class.java)
                findRouteInCumulative(rootObj, timetableId, gson)?.also {
                    fixCoordinatesForDirectRoute(it)
                    fillStartEndStation(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cumulative routes: ${e.message}", e)
                null
            }
        }
    }

    private fun findRouteInCumulative(
        rootObj: JsonObject,
        timetableId: Int,
        gson: Gson,
    ): DirectRoute? {
        for ((_, secondLevel) in rootObj.entrySet()) {
            val secondObj = secondLevel.asJsonObject
            for ((_, pairValue) in secondObj.entrySet()) {
                val found = findRouteInPair(pairValue.asJsonObject, timetableId, gson)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findRouteInPair(
        pairObj: JsonObject,
        timetableId: Int,
        gson: Gson,
    ): DirectRoute? {
        findRouteInArray(pairObj.get("direct"), timetableId, gson)?.let { return it }

        val connected = pairObj.get("connected")
        if (connected != null && connected.isJsonObject) {
            for ((_, viaValue) in connected.asJsonObject.entrySet()) {
                val viaObj = viaValue.asJsonObject
                findRouteInArray(viaObj.get("start"), timetableId, gson)?.let { return it }
                findRouteInArray(viaObj.get("finish"), timetableId, gson)?.let { return it }
            }
        }
        return null
    }

    private fun findRouteInArray(
        routesElement: JsonElement?,
        timetableId: Int,
        gson: Gson,
    ): DirectRoute? {
        if (routesElement == null || !routesElement.isJsonArray) return null
        routesElement.asJsonArray.forEach { elem ->
            val route = gson.fromJson(elem, DirectRoute::class.java)
            if (route.timetableId == timetableId) return route
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Coordinates fix: используем кэш stopsMap
    // -------------------------------------------------------------------------
    private suspend fun getStopsMap(): Map<Int, StopEntity> {
        val cached = stopsMapCache
        if (!cached.isNullOrEmpty()) return cached

        val list = db.appDao().getAllStops()
        val map = list.associateBy { it.stopId }
        stopsMapCache = map
        return map
    }

    private suspend fun fixCoordinates(routesResponse: RoutesResponse) {
        val stopsMap = getStopsMap()

        routesResponse.direct.orEmpty().forEach { directRoute ->
            fixCoordinatesForDirectRoute(directRoute, stopsMap)
        }
        routesResponse.connectedRouteSegments().forEach { connectedSegment ->
            fixCoordinatesForDirectRoute(connectedSegment, stopsMap)
        }
    }

    private suspend fun fixCoordinatesForDirectRoute(directRoute: DirectRoute) {
        val stopsMap = getStopsMap()
        fixCoordinatesForDirectRoute(directRoute, stopsMap)
    }

    private fun fixCoordinatesForDirectRoute(
        directRoute: DirectRoute,
        stopsMap: Map<Int, StopEntity>,
    ) {
        directRoute.timetableItems?.forEach { item ->
            val stopId = item.routestop?.stopId ?: return@forEach
            val st = item.routestop.stop ?: return@forEach
            val stopEntity = stopsMap[stopId] ?: return@forEach
            st.latitude = st.latitude ?: stopEntity.latitude
            st.longitude = st.longitude ?: stopEntity.longitude
        }
    }

    // -------------------------------------------------------------------------
    // Лёгкое заполнение start/end без cumulative
    // -------------------------------------------------------------------------
    private fun fillStartEndStationFromTimetable(routesResponse: RoutesResponse) {
        routesResponse.direct?.forEach { fillStartEndStation(it) }
        routesResponse.connectedRouteSegments().forEach { fillStartEndStation(it) }
    }

    private fun fillStartEndStation(
        dr: DirectRoute,
        full: DirectRoute? = null,
    ) {
        val actual = full ?: dr
        val firstStop =
            actual.timetableItems
                ?.firstOrNull()
                ?.routestop
                ?.stop
        val lastStop =
            actual.timetableItems
                ?.lastOrNull()
                ?.routestop
                ?.stop

        if (dr.startStation == null) {
            dr.startStation = firstStop?.nameEn ?: "Unknown start"
        }
        if (dr.endStation == null) {
            dr.endStation = lastStop?.nameEn ?: "Unknown end"
        }

        dr.validFrom = actual.route?.validFrom
        dr.validTo = actual.route?.validTo
    }

    // ------------------------------------------------------------------------
    // route_info (оставил как у тебя)
    // ------------------------------------------------------------------------
    suspend fun updateRouteInfoFromCumulative(force: Boolean = false) {
        withContext(ioDispatcher) {
            val routeInfoDao = db.routeInfoDao()
            val minValidTo = routeInfoDao.getMinValidTo()
            val isExpired = isDateExpired(minValidTo)
            if (!force && !isExpired) {
                Log.d(TAG, "No need to update route_info, not expired yet.")
                return@withContext
            }

            try {
                val resp = RetrofitClient.api.getCumulativeRoutes().execute()
                if (resp.isSuccessful) {
                    val json = resp.body()?.string().orEmpty()
                    val listOfEntities = parseCumulativeRouteInfo(json)

                    routeInfoDao.clearAll()
                    routeInfoDao.insertAll(listOfEntities)
                    Log.d(TAG, "updateRouteInfoFromCumulative: loaded ${listOfEntities.size} routes.")
                } else {
                    Log.e(TAG, "updateRouteInfoFromCumulative failed: code=${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateRouteInfoFromCumulative: ${e.message}", e)
            }
        }
    }

    private fun parseCumulativeRouteInfo(bigJson: String): List<RouteInfoEntity> {
        val gson = Gson()
        val rootObj = gson.fromJson(bigJson, JsonObject::class.java)
        val result = mutableListOf<RouteInfoEntity>()

        for ((_, secondLevel) in rootObj.entrySet()) {
            val secondObj = secondLevel.asJsonObject
            for ((_, pairValue) in secondObj.entrySet()) {
                result += routeInfoFromPair(pairValue.asJsonObject, gson)
            }
        }
        return result
    }

    private fun routeInfoFromPair(
        pairObj: JsonObject,
        gson: Gson,
    ): List<RouteInfoEntity> =
        buildList {
            addAll(routeInfoFromRouteArray(pairObj.get("direct"), gson))

            val connected = pairObj.get("connected")
            if (connected != null && connected.isJsonObject) {
                for ((_, viaValue) in connected.asJsonObject.entrySet()) {
                    val viaObj = viaValue.asJsonObject
                    addAll(routeInfoFromRouteArray(viaObj.get("start"), gson))
                    addAll(routeInfoFromRouteArray(viaObj.get("finish"), gson))
                }
            }
        }

    private fun routeInfoFromRouteArray(
        routesElement: JsonElement?,
        gson: Gson,
    ): List<RouteInfoEntity> {
        if (routesElement == null || !routesElement.isJsonArray) return emptyList()
        return routesElement.asJsonArray.mapNotNull { elem -> routeInfoEntityFromElement(elem, gson) }
    }

    private fun routeInfoEntityFromElement(
        elem: JsonElement,
        gson: Gson,
    ): RouteInfoEntity? {
        val route = gson.fromJson(elem, DirectRoute::class.java)
        val routeId = route.routeId ?: return null
        val firstStop = route.timetableItems?.firstOrNull()?.routestop?.stop ?: return null
        val lastStop = route.timetableItems.lastOrNull()?.routestop?.stop ?: return null

        return RouteInfoEntity(
            routeId = routeId,
            startNameEn = firstStop.nameEn ?: "",
            startNameMe = firstStop.nameMe ?: "",
            startNameMeCyr = firstStop.nameMeCyr,
            endNameEn = lastStop.nameEn ?: "",
            endNameMe = lastStop.nameMe ?: "",
            endNameMeCyr = lastStop.nameMeCyr,
            validFrom = route.route?.validFrom,
            validTo = route.route?.validTo,
        )
    }

    /**
     * validTo = yyyy-MM-dd считаем “включительно до конца дня”.
     */
    private fun isDateExpired(dateStr: String?): Boolean {
        if (dateStr.isNullOrEmpty()) return true
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr) ?: return true
            val cal =
                Calendar.getInstance().apply {
                    time = date
                    add(Calendar.DATE, 1)
                }
            System.currentTimeMillis() >= cal.timeInMillis
        } catch (_: Exception) {
            true
        }
    }
}
