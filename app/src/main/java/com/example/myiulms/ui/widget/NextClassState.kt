package com.example.myiulms.ui.widget

import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.ScheduleInterval
import com.example.myiulms.ui.dashboard.formatRoomLabel
import com.example.myiulms.ui.dashboard.formatWallClock
import com.example.myiulms.ui.dashboard.minuteOfDay
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import com.example.myiulms.ui.dashboard.resolveScheduleFocus
import com.example.myiulms.ui.dashboard.todayAbbrev

/** One class as the timeline prints it; room without the "Room" prefix. */
data class TimelineClass(
    val course: String,
    val room: String?,
    val start: String,
    val end: String
)

/** The next day that has classes, for the "no classes left today" preview. */
data class UpcomingDay(
    /** "Tomorrow", "Wednesday", or "Next Tuesday" a full week out. */
    val name: String,
    /** "Tomorrow" or "Wed": for the one-line compact layout. */
    val shortName: String,
    val classes: List<TimelineClass>
)

/** What the widget shows. Kept free of Glance types so it is JVM-testable. */
sealed interface NextClassUiState {
    /** A class is live or still to come today; [later] is the rest of today. */
    data class Today(val hero: TimelineClass, val live: Boolean, val later: List<TimelineClass>) : NextClassUiState

    /**
     * Nothing left today, either an off day or after the last class. [nextDay]
     * previews the next day with classes; null only when the whole week is empty.
     */
    data class NoClassesLeft(val finishedToday: Boolean, val nextDay: UpcomingDay?) : NextClassUiState

    /** Nothing cached yet, or today's rows exist but no time could be read. */
    data class NoSchedule(val timesUnreadable: Boolean) : NextClassUiState

    /** Shown instead of any class data whenever the app is signed out. */
    data object SignedOut : NextClassUiState
}

private val WeekDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private val DayNames = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday",
    "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday"
)

/**
 * Maps the cached weekly schedule to a widget state. Today's hero uses the same
 * rules as the in-app Focus Card ([resolveScheduleFocus], campus time zone).
 */
internal fun widgetStateFor(entries: List<WeeklyScheduleEntry>?, nowMillis: Long): NextClassUiState {
    if (entries == null) return NextClassUiState.NoSchedule(timesUnreadable = false)
    val today = todayAbbrev(nowMillis)
    return when (val focus = resolveScheduleFocus(entries, today, minuteOfDay(nowMillis))) {
        is ScheduleFocus.Live -> todayState(entries, today, focus.entry, live = true)
        is ScheduleFocus.Upcoming -> todayState(entries, today, focus.entry, live = false)
        ScheduleFocus.OffDay -> NextClassUiState.NoClassesLeft(false, nextClassDay(entries, today))
        ScheduleFocus.Done -> NextClassUiState.NoClassesLeft(true, nextClassDay(entries, today))
        ScheduleFocus.Unavailable -> NextClassUiState.NoSchedule(timesUnreadable = true)
    }
}

private fun classesOn(entries: List<WeeklyScheduleEntry>, day: String): List<Pair<WeeklyScheduleEntry, ScheduleInterval>> =
    entries
        .filter { normalizeDay(it.day) == day }
        .mapNotNull { entry -> parseScheduleInterval(entry.time)?.let { entry to it } }
        .sortedBy { (_, interval) -> interval.startMinuteOfDay }

private fun todayState(
    entries: List<WeeklyScheduleEntry>,
    today: String,
    hero: WeeklyScheduleEntry,
    live: Boolean
): NextClassUiState {
    // The focus resolver only returns entries whose interval parsed.
    val heroInterval = parseScheduleInterval(hero.time) ?: return NextClassUiState.NoSchedule(timesUnreadable = true)
    val later = classesOn(entries, today)
        .filter { (entry, interval) -> entry !== hero && interval.startMinuteOfDay > heroInterval.startMinuteOfDay }
        .map { (entry, interval) -> entry.toTimeline(interval) }
    return NextClassUiState.Today(hero.toTimeline(heroInterval), live, later)
}

private fun nextClassDay(entries: List<WeeklyScheduleEntry>, today: String): UpcomingDay? {
    val todayIndex = WeekDays.indexOf(today)
    if (todayIndex < 0) return null
    // Offset 7 is today's weekday next week: the only day left in a one-day week.
    for (offset in 1..7) {
        val day = WeekDays[(todayIndex + offset) % WeekDays.size]
        val classes = classesOn(entries, day)
        if (classes.isEmpty()) continue
        val fullName = DayNames.getValue(day)
        return UpcomingDay(
            name = when (offset) {
                1 -> "Tomorrow"
                7 -> "Next $fullName"
                else -> fullName
            },
            shortName = if (offset == 1) "Tomorrow" else fullName.take(3),
            classes = classes.map { (entry, interval) -> entry.toTimeline(interval) }
        )
    }
    return null
}

private fun WeeklyScheduleEntry.toTimeline(interval: ScheduleInterval) = TimelineClass(
    course = courseTitle.ifBlank { courseCode }.ifBlank { "Class" },
    room = formatRoomLabel(location)?.removePrefix("Room "),
    start = formatWallClock(interval.startMinuteOfDay),
    end = formatWallClock(interval.endMinuteOfDay)
)
