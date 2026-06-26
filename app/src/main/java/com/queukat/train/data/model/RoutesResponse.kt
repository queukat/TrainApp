package com.queukat.train.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

const val STOP_TYPE_CROSSING_NO_PASSENGERS = 3

data class RoutesResponse(
    val price: PriceInfo?,
    val direct: List<DirectRoute>?,
    @JsonAdapter(ConnectedRoutesDeserializer::class)
    val connected: Map<String, ConnectedRouteGroup>?,
)

data class ConnectedRouteGroup(
    @SerializedName("via_stop") val viaStop: StopDto?,
    val start: List<DirectRoute>?,
    val finish: List<DirectRoute>?,
)

class ConnectedRoutesDeserializer : JsonDeserializer<Map<String, ConnectedRouteGroup>?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext,
    ): Map<String, ConnectedRouteGroup>? {
        if (json == null || json.isJsonNull) return null
        if (json.isJsonArray) return emptyMap()
        if (!json.isJsonObject) return emptyMap()

        return json.asJsonObject.entrySet().associate { (viaStopId, value) ->
            viaStopId to context.deserialize(value, ConnectedRouteGroup::class.java)
        }
    }
}

fun RoutesResponse.hasRouteResults(): Boolean =
    !direct.isNullOrEmpty() || connectedRouteSegments().isNotEmpty()

fun RoutesResponse.connectedRouteSegments(): List<DirectRoute> =
    connected
        .orEmpty()
        .values
        .flatMap { group -> group.routeSegments() }

fun ConnectedRouteGroup.routeSegments(): List<DirectRoute> =
    start.orEmpty() + finish.orEmpty()

data class PriceInfo(
    @SerializedName("PricelistID") val pricelistId: Int?,
    @SerializedName("StopFromID") val stopFromId: Int?,
    @SerializedName("StopToID") val stopToId: Int?,
    @SerializedName("Class1Price") val class1Price: Double?,
    @SerializedName("Class2Price") val class2Price: Double?,
)

/**
 *   (  ),  .
 */
data class DirectRoute(
    @SerializedName("TimetableID") val timetableId: Int?,
    @SerializedName("RouteID") val routeId: Int?,
    @SerializedName("TrainNumber") val trainNumber: String?,
    @SerializedName("TrainTypeID") val trainTypeId: Int?,
    @SerializedName("International") val international: Int?,
    @SerializedName("timetable_items") val timetableItems: List<TimetableItem>?,
    val route: RouteInfo? = null,
    //   (    JSON!)
    @Transient var startStation: String? = null,
    @Transient var endStation: String? = null,
    @Transient var validFrom: String? = null,
    @Transient var validTo: String? = null,
)

data class TimetableItem(
    @SerializedName("TimetableItemID") val timetableItemId: Int?,
    @SerializedName("TimetableID") val timetableId: Int?,
    @SerializedName("RouteStopID") val routeStopId: Int?,
    @SerializedName("ArrivalTime") val arrivalTime: String?,
    @SerializedName("DepartureTime") val departureTime: String?,
    val routestop: RouteStop? = null,
)

data class RouteStop(
    @SerializedName("RouteStopID") val routeStopId: Int?,
    @SerializedName("Order") val order: Int?,
    @SerializedName("StopID") val stopId: Int?,
    val stop: StopDto? = null,
)

data class RouteInfo(
    @SerializedName("RouteID") val routeId: Int?,
    @SerializedName("ValidFrom") val validFrom: String?,
    @SerializedName("ValidTo") val validTo: String?,
)
