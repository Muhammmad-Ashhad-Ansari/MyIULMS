package com.example.myiulms.alarms

import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.ui.dashboard.CampusZone
import com.example.myiulms.ui.dashboard.formatRoomLabel
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure timing for the class alarm: every moment the app must wake at, from the
 * cached weekly schedule, and the text the class notifications show. No
 * Android types, so it is JVM-tested.
 *
 * With reminders on, each class has two notifying stages:
 * [ScheduleEventType.Reminder] → [ScheduleEventType.ClassStart]. A class end
 * never notifies; [ScheduleEventType.ClassEnd] and campus midnight exist only
 * to refresh the home-screen widget.
 */

/** Default minutes before a class that its reminder fires. */
const val DEFAULT_REMINDER_LEAD_MINUTES = 10L

/** The lead times a student can choose from (Settings: "Remind me"). */
val REMINDER_LEAD_OPTIONS: List<Long> = listOf(5L, 10L, 15L)
private const val MINUTE_MILLIS = 60_000L

/** The only stages that ever post a notification. */
private val NotifyingStages = setOf(ScheduleEventType.Reminder, ScheduleEventType.ClassStart)

/**
 * How late a start notification may still be posted. A phone that was
 * off through a class should not announce it hours later.
 */
private const val STAGE_GRACE_MILLIS = 10 * MINUTE_MILLIS

/** How late a reminder notification may still be posted after its intended time.
 * An inexact alarm may arrive late; this grace window allows it to still notify
 * the student before or at class start rather than silently dropping it.
 */
private const val REMINDER_GRACE_MILLIS = 15 * MINUTE_MILLIS

enum class ScheduleEventType {
    /** The chosen lead time before a class (default [DEFAULT_REMINDER_LEAD_MINUTES]). */
    Reminder,
    /** Exact class start: widget flips to NOW. */
    ClassStart,
    /** Exact class end: widget-only refresh. Never posts a notification. */
    ClassEnd,
    /** Campus midnight: the widget's "today" changes. Never notifies. */
    DayChange
}

/** One dated sitting of a weekly class, in epoch millis. */
data class ClassOccurrence(val entry: WeeklyScheduleEntry, val startMillis: Long, val endMillis: Long)

/** [occurrence] is null only for [ScheduleEventType.DayChange]. */
data class ScheduleEvent(val atMillis: Long, val type: ScheduleEventType, val occurrence: ClassOccurrence? = null)

/**
 * Concrete class sittings from yesterday (an overnight class may still be
 * running) through a week ahead, sorted by start. Rows without a readable time
 * are skipped, as everywhere else.
 */
fun classOccurrences(
    entries: List<WeeklyScheduleEntry>,
    fromMillis: Long,
    zone: ZoneId = CampusZone,
    daysAhead: Int = 8
): List<ClassOccurrence> {
    val firstDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate().minusDays(1)
    return (0..daysAhead).flatMap { offset ->
        val date = firstDay.plusDays(offset.toLong())
        val day = date.dayOfWeek.name.take(3)
        val midnight = date.atStartOfDay(zone)
        entries
            .filter { normalizeDay(it.day) == day }
            .mapNotNull { entry ->
                parseScheduleInterval(entry.time)?.let { interval ->
                    ClassOccurrence(
                        entry = entry,
                        startMillis = midnight.plusMinutes(interval.startMinuteOfDay.toLong()).toInstant().toEpochMilli(),
                        endMillis = midnight.plusMinutes(interval.endMinuteOfDay.toLong()).toInstant().toEpochMilli()
                    )
                }
            }
    }.sortedBy { it.startMillis }
}

/**
 * Every wake-up moment in the window, sorted. Reminders contribute each class's
 * reminder and start; the widget contributes start, end and midnight. A class
 * end is scheduled only when a widget needs it, so with no widget the alarm
 * goes straight from a class's start to the next class's reminder.
 */
fun scheduleEvents(
    entries: List<WeeklyScheduleEntry>,
    nowMillis: Long,
    widgetEvents: Boolean,
    reminders: Boolean,
    leadMinutes: Long = DEFAULT_REMINDER_LEAD_MINUTES,
    zone: ZoneId = CampusZone
): List<ScheduleEvent> = buildList {
    if (!widgetEvents && !reminders) return@buildList
    for (occurrence in classOccurrences(entries, nowMillis, zone)) {
        if (reminders) {
            add(ScheduleEvent(occurrence.startMillis - leadMinutes * MINUTE_MILLIS, ScheduleEventType.Reminder, occurrence))
        }
        add(ScheduleEvent(occurrence.startMillis, ScheduleEventType.ClassStart, occurrence))
        if (widgetEvents) {
            add(ScheduleEvent(occurrence.endMillis, ScheduleEventType.ClassEnd, occurrence))
        }
    }
    if (widgetEvents) {
        val nextMidnight = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        add(ScheduleEvent(nextMidnight, ScheduleEventType.DayChange))
    }
}.sortedBy { it.atMillis }

/** The next moment after [nowMillis] to wake at, or null when nothing needs it. */
fun nextScheduleEvent(
    entries: List<WeeklyScheduleEntry>,
    nowMillis: Long,
    widgetEvents: Boolean,
    reminders: Boolean,
    leadMinutes: Long = DEFAULT_REMINDER_LEAD_MINUTES,
    zone: ZoneId = CampusZone
): ScheduleEvent? =
    scheduleEvents(entries, nowMillis, widgetEvents, reminders, leadMinutes, zone).firstOrNull { it.atMillis > nowMillis }

/**
 * Class notifications to post now: reminder and start stages that happened after
 * [handledUntilMillis] (the last time the receiver ran) and up to [nowMillis].
 * A class end is never included. A reminder is only posted before its class
 * starts, a start only within [STAGE_GRACE_MILLIS], so a late or delayed alarm
 * never announces something long past.
 */
fun dueNotifications(
    entries: List<WeeklyScheduleEntry>,
    nowMillis: Long,
    handledUntilMillis: Long,
    leadMinutes: Long = DEFAULT_REMINDER_LEAD_MINUTES,
    zone: ZoneId = CampusZone
): List<ScheduleEvent> =
    scheduleEvents(entries, nowMillis, widgetEvents = false, reminders = true, leadMinutes = leadMinutes, zone = zone).filter { event ->
        val occurrence = event.occurrence ?: return@filter false
        event.type in NotifyingStages &&
            event.atMillis in (handledUntilMillis + 1)..nowMillis && when (event.type) {
            ScheduleEventType.Reminder -> nowMillis < occurrence.startMillis + REMINDER_GRACE_MILLIS
            else -> nowMillis - event.atMillis <= STAGE_GRACE_MILLIS
        }
    }

private val ClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "1:00 PM" in campus time. */
fun formatClassClock(epochMillis: Long, zone: ZoneId = CampusZone): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(ClockFormatter)

/** "10 mins", "1 min", "1 hr", "2 hrs 5 mins". Rounded up to the minute. */
fun formatGap(millis: Long): String {
    val totalMinutes = ((millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val hourPart = when (hours) {
        0L -> null
        1L -> "1 hr"
        else -> "$hours hrs"
    }
    val minutePart = when {
        minutes == 0L && hourPart != null -> null
        minutes == 1L -> "1 min"
        else -> "$minutes mins"
    }
    return listOfNotNull(hourPart, minutePart).joinToString(" ")
}

/**
 * Text for a class-end notification: the next class the same campus day, or
 * the day being over. "Next: AI AGENTS in 1 hr 10 mins at 1:00 PM · Room E-806".
 */
fun nextClassMessage(
    entries: List<WeeklyScheduleEntry>,
    ended: ClassOccurrence,
    zone: ZoneId = CampusZone
): String {
    val endedDay = Instant.ofEpochMilli(ended.startMillis).atZone(zone).toLocalDate()
    val next = classOccurrences(entries, ended.endMillis, zone).firstOrNull {
        it.startMillis >= ended.endMillis &&
            Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() == endedDay
    } ?: return "All classes for today are over."

    val course = next.entry.courseTitle.ifBlank { next.entry.courseCode }.ifBlank { "Class" }
    val gap = next.startMillis - ended.endMillis
    val timing = if (gap <= 0) "starting now" else "in ${formatGap(gap)}"
    val room = formatRoomLabel(next.entry.location)?.let { " · $it" }.orEmpty()
    return "Next: $course $timing at ${formatClassClock(next.startMillis, zone)}$room"
}
