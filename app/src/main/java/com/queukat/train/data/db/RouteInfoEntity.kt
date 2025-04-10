package com.queukat.train.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_info")
data class RouteInfoEntity(
    @PrimaryKey val routeId: Int,

    //   (4  – )
    val startNameEn: String,
    val startNameMe: String,
    val startNameMeCyr: String?,

    //  
    val endNameEn: String,
    val endNameMe: String,
    val endNameMeCyr: String?,

    val validFrom: String?,
    val validTo: String?
)
