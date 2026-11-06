package com.zynergylabs.forager.app.ui.availability

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.domain.ComputeSundownCountdownUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.isShown
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.sundownLineFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * How the sundown line writes a clock time (dispatch 2026-09-28-592, verify item 8). [full] is the
 * first time on the line, "6:42 PM"; [short] is a later one, "5:32", as the owner's confirmed lines
 * write them ("Sunset 6:42 PM · start back by 5:32", "dark 7:13"). Both follow the phone's 12/24-hour
 * setting, as the sundown alerts do.
 */
internal interface SundownClock {
    fun full(epochMillis: Long): String
    fun short(epochMillis: Long): String
}

/**
 * The phone's own clock format: [DateFormat.getTimeFormat], the format the sundown alerts use
 * (`AndroidAlertDelivery.sundownNotificationText`), for [SundownClock.full]. For [SundownClock.short]
 * the same in 24-hour; in 12-hour, hour and minute without the AM/PM marker.
 */
internal fun androidSundownClock(context: Context): SundownClock {
    val full = DateFormat.getTimeFormat(context)
    val short = if (DateFormat.is24HourFormat(context)) full else SimpleDateFormat("h:mm", Locale.getDefault())
    return object : SundownClock {
        override fun full(epochMillis: Long): String = full.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = short.format(Date(epochMillis))
    }
}

/** The clock the line is written in. Production reads the phone's ([androidSundownClock]); a test may provide a fixed one. */
internal val LocalSundownClock = staticCompositionLocalOf<SundownClock?> { null }

@Composable
internal fun rememberSundownClock(): SundownClock {
    LocalSundownClock.current?.let { return it }
    val context = LocalContext.current
    return remember(context) { androidSundownClock(context) }
}

/**
 * The line's words, or `null` when it is hidden ([isShown], Amendment 2, RECORD -595). The words are the owner's (RECORD -592; Amendment 1, RECORD -593, for "at least" walks, which
 * read "start back by" like a measured one). Nothing here says there is enough time. Polar night,
 * which the owner's path does not name, is worded by this dispatch and reported for the owner to rule on.
 */
internal fun sundownLineText(line: SundownLine, clock: SundownClock): String? = if (!line.isShown()) null else when (line) {
    // Hidden by isShown (Amendment 2): no position has no line at all (the owner, RECORD -596: "Yes hide
    // it before a position is known"), and polar day has no sunset to approach.
    SundownLine.FindingPosition, SundownLine.SunDoesNotSet -> null
    is SundownLine.BeforeSunset -> {
        val sunset = "Sunset ${clock.full(line.sunsetAtEpochMillis)}"
        val startBack = line.startBackAtEpochMillis
        when {
            startBack == null -> buildString {
                append(sunset)
                append(" · in ")
                append(formatSundownDuration(line.sunsetAtEpochMillis - line.nowEpochMillis))
                line.civilDuskAtEpochMillis?.let { append(" · dark ").append(clock.short(it)) }
            }
            line.nowEpochMillis < startBack -> "$sunset · start back by ${clock.short(startBack)}"
            else -> "$sunset · start back was ${clock.short(startBack)}"
        }
    }
    is SundownLine.AfterSunset -> buildString {
        append("Sun set ").append(clock.full(line.sunsetAtEpochMillis))
        line.civilDuskAtEpochMillis?.let { append(" · dark in ").append(formatSundownDuration(it - line.nowEpochMillis)) }
    }
    is SundownLine.DarkSince -> "Dark since ${clock.full(line.civilDuskAtEpochMillis)}"
    SundownLine.SunStaysDown -> "Sun stays down today"
}

/**
 * A time to go, as the owner wrote it ("in 1 h 12 min", "dark in 21 min"), **rounded down** to the
 * minute: a countdown that rounds down never says there is more time than there is.
 */
internal fun formatSundownDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes == 0L -> "less than 1 min"
        hours == 0L -> "$minutes min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}

/**
 * The line for the navigation display while navigating to a waypoint **without a recording**: sunset
 * and dark only, computed on screen from the best position there is, with no start-back time (the
 * owner, Amendment 1: "Sunset and dark only (Recommended)"). There is no walk back without a track,
 * so this is the sunset computation alone, never a second computation of the alerts' start-back
 * time. Recomputed every 15 s, the watch's own tick, in this leaf.
 */
@Composable
internal fun rememberScreenSundownLine(position: LatLng?, currentTime: CurrentTimeProvider): SundownLine {
    val compute = remember { ComputeSundownCountdownUseCase() }
    val line by produceState(initialValue = screenSundownLine(compute, position, currentTime.nowEpochMillis()), position, currentTime) {
        while (true) {
            value = screenSundownLine(compute, position, currentTime.nowEpochMillis())
            delay(SCREEN_SUNDOWN_TICK_MILLIS)
        }
    }
    return line
}

internal fun screenSundownLine(compute: ComputeSundownCountdownUseCase, position: LatLng?, now: Long): SundownLine =
    sundownLineFor(compute(now, position, now, 0L), position, startBackAtEpochMillis = null)

private const val SCREEN_SUNDOWN_TICK_MILLIS = 15_000L

/** The sundown line in the map's top strip. */
internal const val STRIP_SUNDOWN_LINE_TAG = "strip-sundown-line"

/** The sundown line in the navigation display. */
internal const val NAVIGATION_HUD_SUNDOWN_LINE_TAG = "navigation-hud-sundown-line"
