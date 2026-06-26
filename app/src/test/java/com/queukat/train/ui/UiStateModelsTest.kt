package com.queukat.train.ui

import com.queukat.train.data.model.RoutesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiStateModelsTest {
    @Test
    fun routeSearchStatesCarryExpectedPayloads() {
        val response =
            RoutesResponse(
                price = null,
                direct = emptyList(),
                connected = emptyMap(),
            )
        val notice = UiNotice("Pick stations", UiNoticeTone.Warning)

        val results = RouteSearchUiState.Results(response)
        val error = RouteSearchUiState.Error(RouteErrorKind.StationSelection, notice)

        assertSame(RouteSearchUiState.Idle, RouteSearchUiState.Idle)
        assertSame(RouteSearchUiState.Loading, RouteSearchUiState.Loading)
        assertSame(RouteSearchUiState.Empty, RouteSearchUiState.Empty)
        assertEquals(response, results.response)
        assertEquals(RouteErrorKind.StationSelection, error.kind)
        assertEquals(notice, error.notice)
    }

    @Test
    fun reminderStatesCarryExpectedPayloads() {
        val successNotice = UiNotice("Saved", UiNoticeTone.Success)
        val permissionNotice = UiNotice("Permission needed", UiNoticeTone.Info)
        val failureNotice = UiNotice("Failed", UiNoticeTone.Error)

        val success = ReminderUiState.Success(successNotice)
        val permissionMissing =
            ReminderUiState.PermissionMissing(
                permission = ReminderPermissionKind.Notification,
                notice = permissionNotice,
            )
        val failure = ReminderUiState.Failure(failureNotice)

        assertSame(ReminderUiState.Idle, ReminderUiState.Idle)
        assertEquals(successNotice, success.notice)
        assertEquals(ReminderPermissionKind.Notification, permissionMissing.permission)
        assertEquals(permissionNotice, permissionMissing.notice)
        assertEquals(failureNotice, failure.notice)
    }
}
