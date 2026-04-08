package com.queukat.train.ui

import com.queukat.train.data.model.RoutesResponse

enum class UiNoticeTone {
    Info,
    Success,
    Warning,
    Error
}

data class UiNotice(
    val message: String,
    val tone: UiNoticeTone
)

enum class RouteErrorKind {
    StationSelection,
    Network,
    Server,
    InvalidResponse
}

sealed interface RouteSearchUiState {
    data object Idle : RouteSearchUiState
    data object Loading : RouteSearchUiState
    data class Results(val response: RoutesResponse) : RouteSearchUiState
    data object Empty : RouteSearchUiState
    data class Error(
        val kind: RouteErrorKind,
        val notice: UiNotice
    ) : RouteSearchUiState
}

enum class ReminderPermissionKind {
    Notification,
    ExactAlarm
}

sealed interface ReminderUiState {
    data object Idle : ReminderUiState
    data class Success(val notice: UiNotice) : ReminderUiState
    data class PermissionMissing(
        val permission: ReminderPermissionKind,
        val notice: UiNotice
    ) : ReminderUiState
    data class Failure(val notice: UiNotice) : ReminderUiState
}
