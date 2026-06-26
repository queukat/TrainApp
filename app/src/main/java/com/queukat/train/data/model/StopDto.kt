package com.queukat.train.data.model

import com.google.gson.annotations.SerializedName

data class StopDto(
    @SerializedName("StopID") val stopId: Int?,
    @SerializedName("Name_me") val nameMe: String?,
    @SerializedName("Name_en") val nameEn: String?,
    @SerializedName("Name_me_cyr") val nameMeCyr: String?,
    @SerializedName("StopTypeID") val stopTypeId: Int?,
    @SerializedName("Latitude") var latitude: Double?,
    @SerializedName("Longitude") var longitude: Double?,
    val local: Int?,
    @SerializedName("stop_type") val stopType: StopType?,
)

data class StopType(
    @SerializedName("StopTypeID") val stopTypeId: Int?,
    @SerializedName("Name_me") val nameMe: String?,
    @SerializedName("Name_en") val nameEn: String?,
)

fun StopDto.getNameForLanguage(lang: String): String =
    when (lang) {
        "en" -> nameEn
        "me" -> nameMe
        "ru", "meCyr" -> nameMeCyr ?: nameMe
        else -> nameMe ?: nameEn
    } ?: nameEn ?: nameMe ?: nameMeCyr ?: "Unknown"
