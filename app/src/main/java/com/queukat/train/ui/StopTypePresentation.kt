package com.queukat.train.ui

import com.queukat.train.R
import com.queukat.train.data.model.STOP_TYPE_CROSSING_NO_PASSENGERS

internal fun stopTypeLabelRes(stopTypeId: Int?): Int? =
    when (stopTypeId) {
        1 -> R.string.label_stop_type_station
        2 -> R.string.label_stop_type_stop
        STOP_TYPE_CROSSING_NO_PASSENGERS -> R.string.label_crossing_no_passengers
        4 -> R.string.label_stop_type_main_station
        else -> null
    }

internal fun isCrossingStopType(stopTypeId: Int?): Boolean =
    stopTypeId == STOP_TYPE_CROSSING_NO_PASSENGERS
