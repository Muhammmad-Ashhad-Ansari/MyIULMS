package com.example.myiulms

import com.example.myiulms.ui.dashboard.formatWallClock
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Display formats for values the portal returns as free text. Pure Kotlin,
 * JVM-tested. Anything that does not parse is shown exactly as the portal sent it.
 */

private val PortalLongDate = DateTimeFormatter.ofPattern("EEEE, d MMMM, yyyy", Locale.ENGLISH)
// The exam schedule abbreviates the month: "Sunday, Nov 22nd, 2026".
private val PortalExamDate = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.ENGLISH)
private val ShortDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val OrdinalSuffix = Regex("(?<=\\d)(st|nd|rd|th)")

/**
 * "Monday, 30th November, 2026" or "Sunday, Nov 22nd, 2026" → that date;
 * null if neither form parses.
 */
fun parsePortalDate(raw: String): LocalDate? {
    val cleaned = raw.trim().replace(OrdinalSuffix, "")
    return runCatching { LocalDate.parse(cleaned, PortalLongDate) }.getOrNull()
        ?: runCatching { LocalDate.parse(cleaned, PortalExamDate) }.getOrNull()
}

/** "Sunday, Nov 22nd, 2026" → "Sun, 22 Nov 2026"; unchanged if it does not parse. */
fun shortPortalDate(raw: String): String = parsePortalDate(raw)?.format(ShortDate) ?: raw

/**
 * "9:00 AM" + "10:30 AM" → "9:00 – 10:30 AM"; a range that crosses noon keeps
 * both meridiems: "11:30 AM – 1:00 PM".
 */
fun formatExamTimeRange(startMinuteOfDay: Int, endMinuteOfDay: Int): String {
    val start = formatWallClock(startMinuteOfDay)
    val end = formatWallClock(endMinuteOfDay)
    val startMeridiem = start.takeLast(2)
    val endMeridiem = end.takeLast(2)
    return if (startMeridiem == endMeridiem) {
        "${start.dropLast(3)} – ${end.dropLast(3)} $endMeridiem"
    } else {
        "$start – $end"
    }
}

/** Exam duration: 90 → "1h 30m", 120 → "2h", 45 → "45m"; blank for no interval. */
fun formatExamDuration(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/** Whole days until an exam: "6 days left", or "Today"/"Tomorrow"; "Passed" once behind us. */
fun examCountdown(daysUntil: Long): String = when {
    daysUntil < 0 -> "Passed"
    daysUntil == 0L -> "Today"
    daysUntil == 1L -> "Tomorrow"
    else -> "$daysUntil days left"
}

/**
 * Index of the nearest exam that has not passed: earliest date at or after
 * [today], earliest start time on a shared date. [nowMinuteOfDay] lets a
 * same-day exam that has already finished step aside. Entries whose date does
 * not parse are never candidates; null means no exam remains.
 */
fun nextExamIndex(
    entries: List<ExamScheduleEntry>,
    today: LocalDate,
    nowMinuteOfDay: Int? = null
): Int? =
    entries.withIndex()
        .mapNotNull { (index, entry) ->
            val date = parsePortalDate(entry.dayAndDate) ?: return@mapNotNull null
            if (date.isBefore(today)) return@mapNotNull null
            val interval = parseScheduleInterval(entry.time)
            if (date == today && nowMinuteOfDay != null && interval != null &&
                interval.endMinuteOfDay <= nowMinuteOfDay
            ) {
                return@mapNotNull null
            }
            index to (date.toEpochDay() * 1440L + (interval?.startMinuteOfDay ?: 0))
        }
        .minByOrNull { it.second }
        ?.first

/** "1,000.00" / "Rs 27900" → 1000.0 / 27900.0; 0.0 if it does not parse. */
fun parseRupees(raw: String): Double =
    raw.replace(",", "").replace("Rs", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0

/** 1000.0 → "Rs 1,000": whole rupees, grouped, no decimals. */
fun formatRupees(value: Double): String =
    "Rs " + NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(value)

/**
 * A screen subtitle that does not just repeat the heading. The portal's own
 * labels ("Exam Schedule", "Semester Schedule") are dropped in favour of
 * [fallback]; a specific one ("Final Exam Schedule Fall 2026") is kept.
 */
fun distinctSubtitle(portalTitle: String, heading: String, fallback: String): String {
    val title = portalTitle.trim()
    val generic = title.isBlank() ||
        title.equals(heading.trim(), ignoreCase = true) ||
        (title.none { it.isDigit() } && title.endsWith("schedule", ignoreCase = true))
    return if (generic) fallback else title
}
