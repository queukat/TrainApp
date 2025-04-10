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
