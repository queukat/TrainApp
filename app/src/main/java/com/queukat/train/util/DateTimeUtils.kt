package com.queukat.train.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 *        .
 */
object DateTimeUtils {

    //   "yyyy-MM-dd HH:mm:ss"
    private val dateTimeParser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     *    "yyyy-MM-dd HH:mm:ss"  Date.
     *  null,   .
     */
    fun parseDateTime(dateTimeString: String): Date? {
        return try {
            dateTimeParser.parse(dateTimeString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     *   " X  Y "  " Z " —    [departureTimeMs].
     *  [departureTimeMs]   , ё  .
     *
     * @param departureTimeMs —    
     * @param nowMs —   (  System.currentTimeMillis())
     * @param formatHourMin —  ,    ( R.string.time_in_h_and_m)
     * @param formatMin —  ,    ( R.string.time_in_m)
     */
    fun getTimeUntilDepartureString(
        departureTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        formatHourMin: String,
        formatMin: String
    ): String {
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(departureTimeMs - nowMs)
        if (diffMinutes < 0) {
            //   ё
            return ""
        }
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), formatHourMin, hours, minutes)
        } else {
            String.format(Locale.getDefault(), formatMin, minutes)
        }
    }

    /**
     *   "HH:MM - HH:MM (Xh Ym)"   [startMs, endMs].
     * @param timeFormat —  ,  "HH:mm"
     * @param durationFormatHourMin — ,    (R.string.time_in_h_and_m)
     * @param durationFormatMin — ,    (R.string.time_in_m)
     */
    fun getTimeRangeWithDuration(
        startMs: Long,
        endMs: Long,
        timeFormat: String,
        durationFormatHourMin: String,
        durationFormatMin: String
    ): String {
        val dateFormat = SimpleDateFormat(timeFormat, Locale.getDefault())
        val startTimeStr = dateFormat.format(Date(startMs))
        val endTimeStr = dateFormat.format(Date(endMs))

        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(endMs - startMs)
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        val durationStr = if (hours > 0) {
            String.format(Locale.getDefault(), durationFormatHourMin, hours, minutes)
        } else {
            String.format(Locale.getDefault(), durationFormatMin, minutes)
        }

        return "$startTimeStr - $endTimeStr ($durationStr)"
    }
}
