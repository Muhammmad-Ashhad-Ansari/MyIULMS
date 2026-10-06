package com.example.myiulms.ui.dashboard

import com.example.myiulms.WeeklyScheduleEntry

/**
 * Day grouping for the weekly schedule, shared by the on-screen list and the
 * schedule share image.
 *
 * Like [ScheduleInterval] beside it, this file has ZERO Compose and ZERO Android
 * imports so the ordering contract is unit-testable on the JVM.
 *
 * This lives here rather than in the share layer because it is NOT a share
 * concern: `WeeklyScheduleContent` groups its day groups through this function
 * so the list and the exported image cannot disagree about what order a week is
 * in. Removing it as "share-only" code breaks the list.
 */
private val DayOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/**
 * Groups a schedule into day buckets in display order.
 *
 * Contract:
 *  - days ordered Monday-first by [DayOrder];
 *  - a day outside [DayOrder] (including [normalizeDay]'s "OTHER" fallback)
 *    sorts last, then alphabetically, so a malformed row never silently
 *    displaces a real weekday;
 *  - entries within a day ordered by start minute;
 *  - an entry whose time cannot be parsed sorts LAST within its day, rather than
 *    first. [scheduleStartMinutes] returns null for such rows, and sorting a
 *    null to the top would put an unreadable class ahead of the 8 AM lecture.
 */
fun groupScheduleByDay(
    entries: List<WeeklyScheduleEntry>
): Map<String, List<WeeklyScheduleEntry>> =
    entries
        .sortedWith(
            compareBy<WeeklyScheduleEntry> { scheduleStartMinutes(it.time) ?: Int.MAX_VALUE }
        )
        .groupBy { normalizeDay(it.day) }
        .toSortedMap(
            compareBy(
                { day -> DayOrder.indexOf(day).let { if (it < 0) DayOrder.size else it } },
                { day -> day }
            )
        )