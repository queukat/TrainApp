// AppDatabase.kt
package com.queukat.train.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [
        StopEntity::class,
        RouteInfoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun routeInfoDao(): RouteInfoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zpcg.db"
                ).fallbackToDestructiveMigration()
                 .build()
                 .also { instance = it }
            }
        }
    }
}
