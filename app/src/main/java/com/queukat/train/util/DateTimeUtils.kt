package com.queukat.train.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DateTimeUtils {
    // Таймзона расписания (Черногория). IANA TZ, с DST.
    private const val TRAIN_TZ_ID = "Europe/Podgorica"

    // В некоторых средах "Europe/Podgorica" может резолвиться странно -> fallback.
    val TRAIN_TIME_ZONE: TimeZone by lazy {
        val tz = TimeZone.getTimeZone(TRAIN_TZ_ID)
        if (tz.id == "GMT" && TRAIN_TZ_ID != "GMT") {
            TimeZone.getTimeZone("Europe/Belgrade")
        } else {
            tz
        }
    }

    // Парсер даты/времени расписания в TZ Черногории
    private val dateTimeParser: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TRAIN_TIME_ZONE
            isLenient = false
        }
    }

    fun parseDateTime(dateTimeString: String): Date? =
        try {
            dateTimeParser.parse(dateTimeString)
        } catch (_: Exception) {
            null
        }

    /** "Сегодня" в TZ расписания (Черногория) */
    fun todayTrainDateString(): String {
        val df =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TRAIN_TIME_ZONE
            }
        return df.format(Date(System.currentTimeMillis()))
    }

    /** Форматировать миллисекунды в строку по TZ расписания */
    fun formatTimeInTrainTz(
        timeMs: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val df =
            SimpleDateFormat(pattern, locale).apply {
                timeZone = TRAIN_TIME_ZONE
            }
        return df.format(Date(timeMs))
    }

    /**
     * Возвращает строку "Через X" / "In X".
     * Если осталось >= 24 часов -> показываем дни+часы (без минут).
     */
    fun getTimeUntilDepartureString(
        departureTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        formatHourMin: String,
        formatMin: String,
        formatDayHour: String,
        prefixFormat: String,
    ): String {
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(departureTimeMs - nowMs)
        if (diffMinutes < 0) return ""

        val locale = Locale.getDefault()

        val base =
            if (diffMinutes >= 24L * 60L) {
                val days = diffMinutes / (24L * 60L)
                val hours = (diffMinutes % (24L * 60L)) / 60L
                String.format(locale, formatDayHour, days, hours)
            } else {
                val hours = diffMinutes / 60L
                val minutes = diffMinutes % 60L
                if (hours > 0) {
                    String.format(locale, formatHourMin, hours, minutes)
                } else {
                    String.format(locale, formatMin, diffMinutes)
                }
            }

        return String.format(locale, prefixFormat, base)
    }

    fun getTimeRangeWithDuration(
        startMs: Long,
        endMs: Long,
        timeFormat: String,
        durationFormatHourMin: String,
        durationFormatMin: String,
    ): String {
        val dateFormat =
            SimpleDateFormat(timeFormat, Locale.getDefault()).apply {
                timeZone = TRAIN_TIME_ZONE
            }
        val startTimeStr = dateFormat.format(Date(startMs))
        val endTimeStr = dateFormat.format(Date(endMs))

        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(endMs - startMs)
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        val durationStr =
            if (hours > 0) {
                String.format(Locale.getDefault(), durationFormatHourMin, hours, minutes)
            } else {
                String.format(Locale.getDefault(), durationFormatMin, minutes)
            }

        return "$startTimeStr - $endTimeStr ($durationStr)"
    }
}
