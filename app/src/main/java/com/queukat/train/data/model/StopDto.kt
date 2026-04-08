package com.queukat.train.data.model

data class StopDto(
    val StopID: Int?,
    val Name_me: String?,
    val Name_en: String?,
    val Name_me_cyr: String?,
    val StopTypeID: Int?,
    var Latitude: Double?,    // <-  val,  var
    var Longitude: Double?,   // <-  val,  var
    val local: Int?,
    val stop_type: StopType?
)

data class StopType(
    val StopTypeID: Int?,
    val Name_me: String?,
    val Name_en: String?
)

fun StopDto.getNameForLanguage(lang: String): String {
    return when (lang) {
        "en" -> this.Name_en
        "me" -> this.Name_me
        "ru", "meCyr" -> this.Name_me_cyr ?: this.Name_me
        else -> this.Name_me ?: this.Name_en
    } ?: this.Name_en ?: this.Name_me ?: this.Name_me_cyr ?: "Unknown"
}
