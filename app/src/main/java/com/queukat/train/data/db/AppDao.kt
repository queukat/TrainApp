package com.queukat.train.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStops(stops: List<StopEntity>)

    @Query("SELECT * FROM stops ORDER BY nameEn ASC")
    suspend fun getAllStops(): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun countStops(): Int
}
