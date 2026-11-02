package com.example.myiulms.ui.dashboard

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val WeekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH)
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Human age of a saved copy, in campus time: "today 9:12 AM", "yesterday
 * 9:12 AM", "Mon 9:12 AM" within a week, else "2 Nov".
 */
fun formatLastUpdated(fetchedAtMillis: Long, nowMillis: Long, zone: ZoneId = CampusZone): String {
    val fetched = Instant.ofEpochMilli(fetchedAtMillis).atZone(zone)
    val days = ChronoUnit.DAYS.between(fetched.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
    return when {
        days <= 0 -> "today ${fetched.format(ClockFormatter)}"
        days == 1L -> "yesterday ${fetched.format(ClockFormatter)}"
        days < 7 -> fetched.format(WeekdayFormatter)
        else -> fetched.format(DateFormatter)
    }
}

/**
 * The Schedule tab's one-line sync note, its only loading indicator once data is
 * on screen: "Syncing…" during any fetch, "Saved copy" while a cached schedule
 * has not yet been replaced, and nothing for fresh data at rest.
 */
fun scheduleFreshnessNote(
    fetchedAtMillis: Long?,
    fromCache: Boolean,
    syncing: Boolean,
    nowMillis: Long,
    zone: ZoneId = CampusZone
): String? {
    val age = fetchedAtMillis?.let { formatLastUpdated(it, nowMillis, zone) }
    return when {
        syncing -> if (age != null) "Syncing… · last updated $age" else "Syncing…"
        fromCache && age != null -> "Saved copy · last updated $age"
        else -> null
    }
}
