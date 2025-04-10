package com.queukat.train.data.repository

import android.content.Context
import android.util.Log
import com.queukat.train.data.api.RetrofitClient
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.RouteInfoEntity
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "TrainRepository"

open class TrainRepository(
    private val db: AppDatabase,
    private val context: Context
) {

    private var cachedCumulative: String? = null
    private var lastCumulativeFetchMillis: Long = 0

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
        return withContext(Dispatchers.IO) {
            db.appDao().getAllStops()
        }
    }

    /**
     *   /api/routes –   (direct, connected)   .
     *   – «»  (fixCoordinates),  «» startStation / endStation  cumulative.
     */
    open suspend fun getRoutes(start: String, finish: String, date: String): RoutesResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.getRoutes(start, finish, date).execute()
                if (response.isSuccessful) {
                    val routes = response.body()
                    routes?.let {
                        fixCoordinates(it) // <--  
                        fillStartEndStationFromCumulative(it)
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

    suspend fun ensureCumulativeCached(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            //   -   cachedCumulative,  minValidTo
            if (!force && cachedCumulative != null) {
                val minValidToStr = parseMinValidTo(cachedCumulative!!)
                //      ё   —  :
                if (minValidToStr != null && !isDateExpired(minValidToStr)) {
                    Log.d(TAG, "ensureCumulativeCached: cached data still valid (validTo=$minValidToStr)")
                    return@withContext
                }
            }

            //    →  force=true,  cachedCumulative=null,
            //  minValidTo  —    
            try {
                val resp = RetrofitClient.api.getCumulativeRoutes().execute()
                if (resp.isSuccessful) {
                    cachedCumulative = resp.body()?.string()
                    lastCumulativeFetchMillis = now
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
     *   JSON     ValidTo.
     *    "yyyy-MM-dd",  null.
     */
    private fun parseMinValidTo(bigJson: String): String? {
        return try {
            val rootObj = Gson().fromJson(bigJson, JsonObject::class.java)
            var minDate: String? = null
            //    direct/connected,  route.ValidTo, ё 
            for ((_, secondLevel) in rootObj.entrySet()) {
                val secondObj = secondLevel.asJsonObject
                for ((_, pairValue) in secondObj.entrySet()) {
                    val pairObj = pairValue.asJsonObject

                    if (pairObj.has("direct")) {
                        val directArr = pairObj.getAsJsonArray("direct")
                        directArr.forEach { elem ->
                            val dr = Gson().fromJson(elem, DirectRoute::class.java)
                            val vt = dr.route?.ValidTo
                            if (vt != null) {
                                minDate = minOfDates(minDate, vt)
                            }
                        }
                    }

                    if (pairObj.has("connected")) {
                        val connArr = pairObj.getAsJsonArray("connected")
                        connArr.forEach { elem ->
                            val dr = Gson().fromJson(elem, DirectRoute::class.java)
                            val vt = dr.route?.ValidTo
                            if (vt != null) {
                                minDate = minOfDates(minDate, vt)
                            }
                        }
                    }
                }
            }
            minDate
        } catch (e: Exception) {
            null
        }
    }

    private fun minOfDates(current: String?, candidate: String): String {
        if (current == null) return candidate
        return if (candidate < current) candidate else current
    }

    /**
     * « »  routeId  cachedCumulative.
     *   —   .
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
                        if (!pairObj.has("direct")) continue
                        val directArr = pairObj.getAsJsonArray("direct")
                        directArr.forEach { elem ->
                            val dr = gson.fromJson(elem, DirectRoute::class.java)
                            if (dr.RouteID == routeId) {
                                fixCoordinatesForDirectRoute(dr) // <--  
                                fillStartEndStation(dr)
                                return@withContext dr
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
    //   "" coords   ,      = null
    // -------------------------------------------------------------------------
    private suspend fun fixCoordinates(routesResponse: RoutesResponse) {
        val stopsList = db.appDao().getAllStops()
        val stopsMap = stopsList.associateBy { it.stopId }

        routesResponse.direct?.forEach { directRoute ->
            directRoute.timetable_items?.forEach { item ->
                val stopId = item.routestop?.StopID ?: return@forEach
                val st = item.routestop.stop ?: return@forEach
                val stopEntity = stopsMap[stopId] ?: return@forEach

                //    null ->    
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
        val stopsList = db.appDao().getAllStops()
        val stopsMap = stopsList.associateBy { it.stopId }

        directRoute.timetable_items?.forEach { item ->
            val stopId = item.routestop?.StopID ?: return@forEach
            val st = item.routestop.stop ?: return@forEach
            val stopEntity = stopsMap[stopId] ?: return@forEach

            st.Latitude = st.Latitude ?: stopEntity.latitude
            st.Longitude = st.Longitude ?: stopEntity.longitude
        }
    }

    // -------------------------------------------------------------------------
    // "" startStation / endStation  cumulative
    // -------------------------------------------------------------------------
    private suspend fun fillStartEndStationFromCumulative(routesResponse: RoutesResponse) {
        ensureCumulativeCached()
        val bigJson = cachedCumulative ?: return
        if (bigJson.isEmpty()) return

        val gson = Gson()
        val rootObj = gson.fromJson(bigJson, JsonObject::class.java)

        //  direct
        routesResponse.direct?.forEach { dr ->
            dr.RouteID?.let { rid ->
                val maybeFull = findRouteInCumulativeJson(rootObj, gson, rid)
                if (maybeFull != null) {
                    fillStartEndStation(dr, maybeFull)
                }
            }
        }
        // connected
        routesResponse.connected?.forEach { dr ->
            dr.RouteID?.let { rid ->
                val maybeFull = findRouteInCumulativeJson(rootObj, gson, rid)
                if (maybeFull != null) {
                    fillStartEndStation(dr, maybeFull)
                }
            }
        }
    }

    private fun fillStartEndStation(
        dr: DirectRoute,
        full: DirectRoute? = null
    ) {
        val actual = full ?: dr
        val firstStop = actual.timetable_items?.firstOrNull()?.routestop?.stop
        val lastStop = actual.timetable_items?.lastOrNull()?.routestop?.stop

        firstStop?.let {
            dr.startStation = it.Name_en ?: "Unknown start"
        }
        lastStop?.let {
            dr.endStation = it.Name_en ?: "Unknown end"
        }

        //    validFrom/validTo
        dr.validFrom = actual.route?.ValidFrom
        dr.validTo = actual.route?.ValidTo
    }

    private fun findRouteInCumulativeJson(
        rootObj: JsonObject,
        gson: Gson,
        routeId: Int
    ): DirectRoute? {
        for ((_, secondLevel) in rootObj.entrySet()) {
            val secondObj = secondLevel.asJsonObject
            for ((_, pairValue) in secondObj.entrySet()) {
                val pairObj = pairValue.asJsonObject
                if (!pairObj.has("direct")) continue
                val directArr = pairObj.getAsJsonArray("direct")
                directArr.forEach { elem ->
                    val dr = gson.fromJson(elem, DirectRoute::class.java)
                    if (dr.RouteID == routeId) {
                        return dr
                    }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------------
    //    route_info    ( )
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

                        val entity = RouteInfoEntity(
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
                        result.add(entity)
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

                        val entity = RouteInfoEntity(
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
                        result.add(entity)
                    }
                }
            }
        }
        return result
    }

    private fun isDateExpired(dateStr: String?): Boolean {
        if (dateStr.isNullOrEmpty()) return true
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr)
            date?.time ?: 0 < System.currentTimeMillis()
        } catch (e: Exception) {
            true
        }
    }
}
