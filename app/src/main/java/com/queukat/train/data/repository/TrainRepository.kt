package com.queukat.train.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.queukat.train.data.api.RetrofitClient
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.RouteInfoEntity
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "TrainRepository"

// сколько держим cumulative в памяти прежде чем освежить (без дорогого парса всего JSON)
private const val CUMULATIVE_TTL_MS = 12L * 60 * 60 * 1000 // 12 часов

open class TrainRepository(
    private val db: AppDatabase,
    private val context: Context
) {

    @Volatile
    private var cachedCumulative: String? = null

    @Volatile
    private var cachedCumulativeAtMs: Long = 0L

    @Volatile
    private var stopsMapCache: Map<Int, StopEntity>? = null

    suspend fun ensureStopsUpToDate(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong("stops_last_update", 0L)
            val now = System.currentTimeMillis()
            val oneDayMillis = 24L * 60 * 60 * 1000

            val countLocal = db.appDao().countStops()
            val isDbEmpty = (countLocal == 0)

            val needUpdate = force || isDbEmpty || (now - lastUpdate > oneDayMillis)
            if (needUpdate) {
                try {
                    val response = RetrofitClient.api.getStops().execute()
                    if (response.isSuccessful) {
                        val stopsDto = response.body().orEmpty()
                        val entities = stopsDto.mapNotNull { dto ->
                            if (dto.StopID == null || dto.Name_me == null) null
                            else {
                                StopEntity(
                                    stopId = dto.StopID,
                                    nameEn = dto.Name_en ?: "",
                                    nameMe = dto.Name_me,
                                    nameMeCyr = dto.Name_me_cyr,
                                    stopTypeId = dto.StopTypeID,
                                    latitude = dto.Latitude,
                                    longitude = dto.Longitude,
                                    local = dto.local
                                )
                            }
                        }

                        db.appDao().insertAllStops(entities)
                        prefs.edit().putLong("stops_last_update", now).apply()

                        // обновим кэш, чтобы fixCoordinates не читала БД заново
                        stopsMapCache = entities.associateBy { it.stopId }
                    } else {
                        Log.e(TAG, "ensureStopsUpToDate failed: ${response.code()} ${response.message()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching stops: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Not updating stops: already have data & <24h since last update.")
            }
        }
    }

    open suspend fun getAllStopsFromDb(): List<StopEntity> {
        return withContext(Dispatchers.IO) { db.appDao().getAllStops() }
    }

    /**
     * ВАЖНО: тут НЕ трогаем cumulative (Android 9 будет лагать).
     * start/end берем из timetable (или fallback на UI).
     */
    open suspend fun getRoutes(start: String, finish: String, date: String): RoutesResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.getRoutes(start, finish, date).execute()
                if (response.isSuccessful) {
                    val routes = response.body()
                    routes?.let {
                        fixCoordinates(it)
                        fillStartEndStationFromTimetable(it)
                    }
                    routes
                } else {
                    Log.e(TAG, "getRoutes failed: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in getRoutes: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Cumulative держим в памяти с TTL, без тяжёлого parseMinValidTo().
     * Это ключевой фикс для Android 9.
     */
    suspend fun ensureCumulativeCached(force: Boolean = false) {
        withContext(Dispatchers.IO) {
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
    open suspend fun getFullRouteFromCumulative(routeId: Int): DirectRoute? {
        ensureCumulativeCached()

        val bigJson = cachedCumulative ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val rootObj = gson.fromJson(bigJson, JsonObject::class.java)

                for ((_, secondLevel) in rootObj.entrySet()) {
                    val secondObj = secondLevel.asJsonObject
                    for ((_, pairValue) in secondObj.entrySet()) {
                        val pairObj = pairValue.asJsonObject

                        // direct
                        if (pairObj.has("direct")) {
                            val directArr = pairObj.getAsJsonArray("direct")
                            directArr.forEach { elem ->
                                val dr = gson.fromJson(elem, DirectRoute::class.java)
                                if (dr.RouteID == routeId) {
                                    fixCoordinatesForDirectRoute(dr)
                                    fillStartEndStation(dr)
                                    return@withContext dr
                                }
                            }
                        }

                        // connected (фоллбек)
                        if (pairObj.has("connected")) {
                            val connArr = pairObj.getAsJsonArray("connected")
                            connArr.forEach { elem ->
                                val dr = gson.fromJson(elem, DirectRoute::class.java)
                                if (dr.RouteID == routeId) {
                                    fixCoordinatesForDirectRoute(dr)
                                    fillStartEndStation(dr)
                                    return@withContext dr
                                }
                            }
                        }
                    }
                }

                null
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cumulative routes: ${e.message}", e)
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Coordinates fix: используем кэш stopsMap
    // -------------------------------------------------------------------------
    private suspend fun getStopsMap(): Map<Int, StopEntity> {
        val cached = stopsMapCache
        if (cached != null && cached.isNotEmpty()) return cached

        val list = db.appDao().getAllStops()
        val map = list.associateBy { it.stopId }
        stopsMapCache = map
        return map
    }

    private suspend fun fixCoordinates(routesResponse: RoutesResponse) {
        val stopsMap = getStopsMap()

        routesResponse.direct?.forEach { directRoute ->
            directRoute.timetable_items?.forEach { item ->
                val stopId = item.routestop?.StopID ?: return@forEach
                val st = item.routestop.stop ?: return@forEach
                val stopEntity = stopsMap[stopId] ?: return@forEach
                st.Latitude = st.Latitude ?: stopEntity.latitude
                st.Longitude = st.Longitude ?: stopEntity.longitude
            }
        }

        routesResponse.connected?.forEach { cRoute ->
            cRoute.timetable_items?.forEach { item ->
                val stopId = item.routestop?.StopID ?: return@forEach
                val st = item.routestop.stop ?: return@forEach
                val stopEntity = stopsMap[stopId] ?: return@forEach
                st.Latitude = st.Latitude ?: stopEntity.latitude
                st.Longitude = st.Longitude ?: stopEntity.longitude
            }
        }
    }

    private suspend fun fixCoordinatesForDirectRoute(directRoute: DirectRoute) {
        val stopsMap = getStopsMap()

        directRoute.timetable_items?.forEach { item ->
            val stopId = item.routestop?.StopID ?: return@forEach
            val st = item.routestop.stop ?: return@forEach
            val stopEntity = stopsMap[stopId] ?: return@forEach
            st.Latitude = st.Latitude ?: stopEntity.latitude
            st.Longitude = st.Longitude ?: stopEntity.longitude
        }
    }

    // -------------------------------------------------------------------------
    // Лёгкое заполнение start/end без cumulative
    // -------------------------------------------------------------------------
    private fun fillStartEndStationFromTimetable(routesResponse: RoutesResponse) {
        routesResponse.direct?.forEach { fillStartEndStation(it) }
        routesResponse.connected?.forEach { fillStartEndStation(it) }
    }

    private fun fillStartEndStation(dr: DirectRoute, full: DirectRoute? = null) {
        val actual = full ?: dr
        val firstStop = actual.timetable_items?.firstOrNull()?.routestop?.stop
        val lastStop = actual.timetable_items?.lastOrNull()?.routestop?.stop

        if (dr.startStation == null) {
            dr.startStation = firstStop?.Name_en ?: "Unknown start"
        }
        if (dr.endStation == null) {
            dr.endStation = lastStop?.Name_en ?: "Unknown end"
        }

        dr.validFrom = actual.route?.ValidFrom
        dr.validTo = actual.route?.ValidTo
    }

    // ------------------------------------------------------------------------
    // route_info (оставил как у тебя)
    // ------------------------------------------------------------------------
    suspend fun updateRouteInfoFromCumulative(force: Boolean = false) {
        withContext(Dispatchers.IO) {
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
        val result = mutableListOf<RouteInfoEntity>()
        val gson = Gson()
        val rootObj = gson.fromJson(bigJson, JsonObject::class.java)

        for ((_, secondLevel) in rootObj.entrySet()) {
            val secondObj = secondLevel.asJsonObject
            for ((_, pairValue) in secondObj.entrySet()) {
                val pairObj = pairValue.asJsonObject

                if (pairObj.has("direct")) {
                    val directArr = pairObj.getAsJsonArray("direct")
                    directArr.forEach { elem ->
                        val dr = gson.fromJson(elem, DirectRoute::class.java)
                        val routeId = dr.RouteID ?: return@forEach
                        val firstStop = dr.timetable_items?.firstOrNull()?.routestop?.stop
                        val lastStop = dr.timetable_items?.lastOrNull()?.routestop?.stop
                        if (firstStop == null || lastStop == null) return@forEach

                        result.add(
                            RouteInfoEntity(
                                routeId = routeId,
                                startNameEn = firstStop.Name_en ?: "",
                                startNameMe = firstStop.Name_me ?: "",
                                startNameMeCyr = firstStop.Name_me_cyr,
                                endNameEn = lastStop.Name_en ?: "",
                                endNameMe = lastStop.Name_me ?: "",
                                endNameMeCyr = lastStop.Name_me_cyr,
                                validFrom = dr.route?.ValidFrom,
                                validTo = dr.route?.ValidTo
                            )
                        )
                    }
                }

                if (pairObj.has("connected")) {
                    val connArr = pairObj.getAsJsonArray("connected")
                    connArr.forEach { elem ->
                        val dr = gson.fromJson(elem, DirectRoute::class.java)
                        val routeId = dr.RouteID ?: return@forEach
                        val firstStop = dr.timetable_items?.firstOrNull()?.routestop?.stop
                        val lastStop = dr.timetable_items?.lastOrNull()?.routestop?.stop
                        if (firstStop == null || lastStop == null) return@forEach

                        result.add(
                            RouteInfoEntity(
                                routeId = routeId,
                                startNameEn = firstStop.Name_en ?: "",
                                startNameMe = firstStop.Name_me ?: "",
                                startNameMeCyr = firstStop.Name_me_cyr,
                                endNameEn = lastStop.Name_en ?: "",
                                endNameMe = lastStop.Name_me ?: "",
                                endNameMeCyr = lastStop.Name_me_cyr,
                                validFrom = dr.route?.ValidFrom,
                                validTo = dr.route?.ValidTo
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * validTo = yyyy-MM-dd считаем “включительно до конца дня”.
     */
    private fun isDateExpired(dateStr: String?): Boolean {
        if (dateStr.isNullOrEmpty()) return true
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr) ?: return true
            val cal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DATE, 1)
            }
            System.currentTimeMillis() >= cal.timeInMillis
        } catch (_: Exception) {
            true
        }
    }
}
