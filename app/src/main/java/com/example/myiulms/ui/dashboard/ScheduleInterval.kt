package com.example.myiulms.ui.dashboard

import com.example.myiulms.WeeklyScheduleEntry
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure-Kotlin schedule interval logic for the upcoming/live class dashboard card.
 *
 * This file deliberately has ZERO Compose imports so the whole time-analysis
 * surface can be unit tested on the JVM without an Android runtime.
 *
 * Every function here is TOTAL: malformed input ("TBA", blank, single token,
 * absurd ranges) yields a null/None result instead of throwing or leaking
 * sentinel values into arithmetic. All durations are clamped so that manual
 * device clock manipulation degrades to a safe fallback rather than a crash or
 * a nonsensical negative countdown.
 */

/**
 * Same token semantics as the parser in MainActivity so the dashboard and the
 * existing schedule cards agree on what a time string means.
 */
private val timeTokenPattern = Regex(
    "(?i)(?<![\\d:])(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM|A\\.M\\.|P\\.M\\.))?(?!\\d)"
)

/** Upper bound mirroring the existing duration guard (12 hours). */
private const val MAX_DURATION_MINUTES = 12 * 60

/** Sentinel shown when no trustworthy interval can be derived. */
const val NO_COUNTDOWN = "—"

/**
 * A parsed class interval in minutes-of-day.
 *
 * [endMinuteOfDay] may exceed 1440 when a class crosses midnight, so that
 * duration arithmetic never has to special-case wrap-around at the call site.
 */
data class ScheduleInterval(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val durationMinutes: Int
)

/**
 * Current minute-of-day (0..1439) in the device's zone.
 *
 * @param epochMillis injectable so tests are deterministic.
 */
fun minuteOfDay(
    epochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Int = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
    .let { it.hour * 60 + it.minute }

/**
 * Second-resolution seconds-of-day (0..86399), used to drive the 1 Hz ticker.
 * Injectable so tests stay deterministic.
 */
fun secondsOfDay(
    epochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Int = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
    .let { it.hour * 3600 + it.minute * 60 + it.second }

/**
 * Clamped seconds remaining for a live class.
 *
 * Clamping into [0, durationSeconds] is what makes a manual device-clock jump
 * degrade to "00:00" instead of rendering a negative or absurd countdown.
 */
fun liveSecondsRemaining(
    entry: WeeklyScheduleEntry,
    epochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Int {
    val interval = parseScheduleInterval(entry.time) ?: return 0
    val durationSeconds = interval.durationMinutes * 60
    return (interval.endMinuteOfDay * 60 - secondsOfDay(epochMillis, zone))
        .coerceIn(0, durationSeconds)
}

/**
 * Three-letter day abbreviation matching the normalization used when the weekly
 * list groups entries (MON/TUE/.../SUN, plus OTHER).
 */
fun todayAbbrev(
    epochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
    .dayOfWeek
    .abbrev()

private fun DayOfWeek.abbrev(): String = when (this) {
    DayOfWeek.MONDAY -> "MON"
    DayOfWeek.TUESDAY -> "TUE"
    DayOfWeek.WEDNESDAY -> "WED"
    DayOfWeek.THURSDAY -> "THU"
    DayOfWeek.FRIDAY -> "FRI"
    DayOfWeek.SATURDAY -> "SAT"
    DayOfWeek.SUNDAY -> "SUN"
}

/** Normalizes an entry's day string to the same 3-letter form used for grouping. */
fun normalizeDay(day: String): String =
    day.trim().uppercase().take(3).ifBlank { "OTHER" }

/**
 * Parses the start token of a time string.
 *
 * Returns null instead of the historical Int.MAX_VALUE sentinel, so a
 * malformed token can never participate in a comparison or subtraction.
 */
fun scheduleStartMinutes(time: String): Int? {
    val match = timeTokenPattern.find(time) ?: return null
    return tokenToMinutes(match, inferredSuffix = "")
}

/**
 * Parses a full "start - end" range into a validated [ScheduleInterval].
 *
 * Returns null for blank/TBA strings, single-token strings, out-of-range
 * hours/minutes, and implausible durations (not 1..720 minutes).
 */
fun parseScheduleInterval(time: String): ScheduleInterval? {
    val tokens = timeTokenPattern.findAll(time).take(2).toList()
    if (tokens.size < 2) return null

    val first = tokens[0]
    val second = tokens[1]
    val firstHour = first.groupValues[1].toIntOrNull() ?: return null
    val secondHour = second.groupValues[1].toIntOrNull() ?: return null
    val firstSuffix = first.groupValues[3].replace(".", "").uppercase()
    val secondSuffix = second.groupValues[3].replace(".", "").uppercase()

    // Same suffix-inference rules already proven in MainActivity.
    val inferredFirstSuffix = when {
        firstSuffix == "AM" || firstSuffix == "PM" -> firstSuffix
        firstHour > 12 -> ""
        secondSuffix != "AM" && secondSuffix != "PM" -> ""
        firstHour % 12 > secondHour % 12 -> if (secondSuffix == "AM") "PM" else "AM"
        else -> secondSuffix
    }
    val inferredSecondSuffix = when {
        secondSuffix == "AM" || secondSuffix == "PM" -> secondSuffix
        secondHour > 12 -> ""
        firstSuffix != "AM" && firstSuffix != "PM" -> ""
        secondHour % 12 < firstHour % 12 -> if (firstSuffix == "AM") "PM" else "AM"
        else -> firstSuffix
    }

    val start = tokenToMinutes(first, inferredFirstSuffix) ?: return null
    var end = tokenToMinutes(second, inferredSecondSuffix) ?: return null

    // Overnight class: 22:00 - 02:00 becomes 1320 -> 1560.
    if (end < start) end += 24 * 60

    val duration = end - start
    if (duration !in 1..MAX_DURATION_MINUTES) return null

    return ScheduleInterval(
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        durationMinutes = duration
    )
}

private fun tokenToMinutes(match: MatchResult, inferredSuffix: String): Int? {
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null

    val suffix = match.groupValues[3].replace(".", "").uppercase().ifBlank { inferredSuffix }
    val hour24 = when (suffix) {
        "AM" -> hour % 12
        "PM" -> (hour % 12) + 12
        else -> hour % 24
    }
    return hour24 * 60 + minute
}

/**
 * What the dashboard card should currently display.
 *
 * These are deliberately distinct rather than one catch-all "None", because the
 * states mean genuinely different things to a student:
 *  - [Live] / [Upcoming]: real classes, real countdowns.
 *  - [OffDay]: the portal has no rows at all for today.
 *  - [Done]: today had classes and they have all finished.
 *  - [Unavailable]: rows exist but none of their times could be parsed.
 *
 * Collapsing the last three into a single "no classes today" would print a
 * false statement every evening after the final class of the day.
 */
sealed interface ScheduleFocus {
    data class Live(
        val entry: WeeklyScheduleEntry,
        val remainingMinutes: Int,
        val totalMinutes: Int
    ) : ScheduleFocus

    data class Upcoming(
        val entry: WeeklyScheduleEntry,
        val startsInMinutes: Int
    ) : ScheduleFocus

    /** No entries exist for the active day. */
    data object OffDay : ScheduleFocus

    /** Entries existed for today and every class has already finished. */
    data object Done : ScheduleFocus

    /** Entries existed but none carried a parseable time range. */
    data object Unavailable : ScheduleFocus
}

/**
 * Resolves today's live class, else the next upcoming one, else a precise
 * terminal state.
 *
 * [minuteOfDay] is a parameter (rather than read internally) so this stays
 * deterministic and unit-testable. Entries without a parseable interval are
 * never allowed to reach arithmetic.
 */
fun resolveScheduleFocus(
    entries: List<WeeklyScheduleEntry>,
    today: String,
    minuteOfDay: Int
): ScheduleFocus {
    // Day-scoped, not week-scoped: "no rows for today" is an off-day fact.
    val todayEntries = entries.filter { normalizeDay(it.day) == today }
    if (todayEntries.isEmpty()) return ScheduleFocus.OffDay

    val parsed = todayEntries
        .map { it to parseScheduleInterval(it.time) }
        .filter { (_, interval) -> interval != null }
        .sortedBy { (_, interval) -> interval!!.startMinuteOfDay }

    // Rows exist but not one time could be read (portal "TBA"/structural error).
    if (parsed.isEmpty()) return ScheduleFocus.Unavailable

    // Live class wins if one is currently running.
    parsed.firstOrNull { (_, interval) ->
        val i = interval!!
        minuteOfDay in i.startMinuteOfDay until i.endMinuteOfDay
    }?.let { (entry, interval) ->
        val i = interval!!
        val remaining = (i.endMinuteOfDay - minuteOfDay).coerceIn(0, i.durationMinutes)
        return ScheduleFocus.Live(entry, remaining, i.durationMinutes)
    }

    // Otherwise the next class that has not started yet.
    parsed.firstOrNull { (_, interval) ->
        interval!!.startMinuteOfDay > minuteOfDay
    }?.let { (entry, interval) ->
        val i = interval!!
        val lead = (i.startMinuteOfDay - minuteOfDay).coerceAtLeast(0)
        return ScheduleFocus.Upcoming(entry, lead)
    }

    // Today had real classes and the last one has ended.
    return ScheduleFocus.Done
}

/**
 * Formats a live countdown as MM:SS, clamped to [totalMinutes].
 *
 * [secondsRemaining] is the second-resolution value so the string can tick at
 * 1 Hz; it is clamped against the interval length, which makes a device clock
 * jump produce "00:00" rather than a negative or absurd string.
 */
fun formatRemainingClock(secondsRemaining: Int, totalMinutes: Int): String {
    val maxSeconds = (totalMinutes.coerceAtLeast(0)) * 60
    val safe = secondsRemaining.coerceIn(0, maxSeconds)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Formats lead time as "In 25 mins" / "In 1 min" / "Starting now". */
fun formatStartsIn(startsInMinutes: Int): String {
    val safe = startsInMinutes.coerceAtLeast(0)
    return when (safe) {
        0 -> "Starting now"
        1 -> "In 1 min"
        else -> "In $safe mins"
    }
}
