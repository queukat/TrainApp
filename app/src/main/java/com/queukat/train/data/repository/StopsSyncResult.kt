package com.queukat.train.data.repository

sealed interface StopsSyncResult {
    data object UpToDate : StopsSyncResult

    data class Refreshed(
        val count: Int,
    ) : StopsSyncResult

    data class Failed(
        val message: String? = null,
    ) : StopsSyncResult
}
