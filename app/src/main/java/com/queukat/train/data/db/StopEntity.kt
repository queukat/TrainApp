package com.queukat.train.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val stopId: Int,
    val nameEn: String,
    val nameMe: String,
    val nameMeCyr: String?,
    val stopTypeId: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val local: Int?
)

//       
fun StopEntity.getNameForLanguage(lang: String): String {
    return when (lang) {
        "en" -> this.nameEn
        "me" -> this.nameMe
        "ru", "meCyr" -> this.nameMeCyr ?: this.nameMe
        else -> if (this.nameMe.isNotEmpty()) this.nameMe else this.nameEn
    }
}
