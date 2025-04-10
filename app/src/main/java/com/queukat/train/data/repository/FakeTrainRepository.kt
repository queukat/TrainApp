package com.queukat.train.data.repository

import android.content.Context
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.model.DirectRoute
import com.queukat.train.data.model.RoutesResponse

/**
 * ё ,    ,
 *   ё   .
 */
class FakeTrainRepository(
    private val context: Context
) : TrainRepository(
    db = AppDatabase.getInstance(context),
    context = context
) {
    override suspend fun getAllStopsFromDb(): List<StopEntity> {
        //  2-3  
        return listOf(
            StopEntity(
                stopId = 1,
                nameEn = "Podgorica",
                nameMe = "Podgorica",
                nameMeCyr = "",
                stopTypeId = 1,
                latitude = 42.0,
                longitude = 19.0,
                local = 1
            ),
            StopEntity(
                stopId = 2,
                nameEn = "Bar",
                nameMe = "Bar",
                nameMeCyr = "",
                stopTypeId = 1,
                latitude = 42.1,
                longitude = 19.1,
                local = 1
            )
        )
    }

    override suspend fun getRoutes(start: String, finish: String, date: String): RoutesResponse? {
        //   
        return null
    }

    override suspend fun getFullRouteFromCumulative(routeId: Int): DirectRoute? {
        return null
    }

    //        .
}
