package com.queukat.train.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class DateTimeUtilsTest {
    @Test
    fun parseDateTimeUsesTrainTimezone() {
        val parsed = DateTimeUtils.parseDateTime("2026-01-02 03:04:05")

        assertNotNull(parsed)
        assertEquals(
            "2026-01-02 03:04:05",
            DateTimeUtils.formatTimeInTrainTz(
                timeMs = parsed!!.time,
                pattern = "yyyy-MM-dd HH:mm:ss",
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun parseDateTimeReturnsNullForInvalidInput() {
        assertNull(DateTimeUtils.parseDateTime("not-a-date"))
    }

    @Test
    fun trainTimezoneIsResolved() {
        assertNotNull(DateTimeUtils.TRAIN_TIME_ZONE)
    }

    @Test
    fun departureCountdownFormatsMinutesHoursAndDays() {
        val now = TimeUnit.HOURS.toMillis(12)

        assertEquals(
            "in 30m",
            DateTimeUtils.getTimeUntilDepartureString(
                departureTimeMs = now + TimeUnit.MINUTES.toMillis(30),
                nowMs = now,
                formatHourMin = "%dh %dm",
                formatMin = "%dm",
                formatDayHour = "%dd %dh",
                prefixFormat = "in %s",
            ),
        )
        assertEquals(
            "in 2h 5m",
            DateTimeUtils.getTimeUntilDepartureString(
                departureTimeMs = now + TimeUnit.MINUTES.toMillis(125),
                nowMs = now,
                formatHourMin = "%dh %dm",
                formatMin = "%dm",
                formatDayHour = "%dd %dh",
                prefixFormat = "in %s",
            ),
        )
        assertEquals(
            "in 1d 3h",
            DateTimeUtils.getTimeUntilDepartureString(
                departureTimeMs = now + TimeUnit.HOURS.toMillis(27),
                nowMs = now,
                formatHourMin = "%dh %dm",
                formatMin = "%dm",
                formatDayHour = "%dd %dh",
                prefixFormat = "in %s",
            ),
        )
    }

    @Test
    fun departureCountdownReturnsBlankForPastDeparture() {
        assertEquals(
            "",
            DateTimeUtils.getTimeUntilDepartureString(
                departureTimeMs = 0,
                nowMs = TimeUnit.MINUTES.toMillis(2),
                formatHourMin = "%dh %dm",
                formatMin = "%dm",
                formatDayHour = "%dd %dh",
                prefixFormat = "in %s",
            ),
        )
    }

    @Test
    fun timeRangeIncludesDuration() {
        val start = DateTimeUtils.parseDateTime("2026-01-02 03:00:00")!!.time
        val end = start + TimeUnit.MINUTES.toMillis(90)

        assertEquals(
            "03:00 - 04:30 (1h 30m)",
            DateTimeUtils.getTimeRangeWithDuration(
                startMs = start,
                endMs = end,
                timeFormat = "HH:mm",
                durationFormatHourMin = "%dh %dm",
                durationFormatMin = "%dm",
            ),
        )
    }

    @Test
    fun shortTimeRangeUsesMinuteDuration() {
        val start = DateTimeUtils.parseDateTime("2026-01-02 03:00:00")!!.time
        val end = start + TimeUnit.MINUTES.toMillis(45)

        assertEquals(
            "03:00 - 03:45 (45m)",
            DateTimeUtils.getTimeRangeWithDuration(
                startMs = start,
                endMs = end,
                timeFormat = "HH:mm",
                durationFormatHourMin = "%dh %dm",
                durationFormatMin = "%dm",
            ),
        )
    }
}
