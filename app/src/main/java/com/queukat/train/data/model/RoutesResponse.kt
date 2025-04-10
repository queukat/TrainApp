package com.queukat.train.data.model

data class RoutesResponse(
    val price: PriceInfo?,
    val direct: List<DirectRoute>?,
    val connected: List<DirectRoute>?
)

data class PriceInfo(
    val PricelistID: Int?,
    val StopFromID: Int?,
    val StopToID: Int?,
    val Class1Price: Double?,
    val Class2Price: Double?
)

/**
 *   (  ),  .
 */
data class DirectRoute(
    val TimetableID: Int?,
    val RouteID: Int?,
    val TrainNumber: String?,
    val TrainTypeID: Int?,
    val International: Int?,
    val timetable_items: List<TimetableItem>?,
    val route: RouteInfo? = null,

    //   (    JSON!)
    @Transient var startStation: String? = null,
    @Transient var endStation: String? = null,
    @Transient var validFrom: String? = null,
    @Transient var validTo: String? = null
)

data class TimetableItem(
    val TimetableItemID: Int?,
    val TimetableID: Int?,
    val RouteStopID: Int?,
    val ArrivalTime: String?,
    val DepartureTime: String?,
    val routestop: RouteStop? = null
)

data class RouteStop(
    val RouteStopID: Int?,
    val Order: Int?,
    val StopID: Int?,
    val stop: StopDto? = null
)

data class RouteInfo(
    val RouteID: Int?,
    val ValidFrom: String?,
    val ValidTo: String?
)
