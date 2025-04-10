package com.queukat.train.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteInfoEntity>)

    @Query("DELETE FROM route_info")
    suspend fun clearAll()

    //   
    @Query("SELECT * FROM route_info WHERE routeId = :routeId LIMIT 1")
    suspend fun findByRouteId(routeId: Int): RouteInfoEntity?

    // ,   validTo
    @Query("SELECT MIN(validTo) FROM route_info")
    suspend fun getMinValidTo(): String?
}
