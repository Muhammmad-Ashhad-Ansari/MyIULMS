package com.example.myiulms

import com.example.myiulms.ui.dashboard.groupScheduleByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [groupScheduleByDay], the single ordering contract shared by the
 * weekly list and the schedule share image.
 *
 * Narrow on purpose. This file previously covered a plain-text compiler that has
 * since been replaced by the PNG renderer in ShareAcademic.kt; only the grouping
 * logic survived, and only the grouping logic still has two independent callers
 * that must agree.
 *
 * The invariants that matter:
 *  - days come out Monday-first regardless of portal row order;
 *  - a day the portal did not name sorts last rather than displacing a real
 *    weekday;
 *  - an entry with an unparseable time sorts LAST within its day, not first.
 */
class ScheduleShareTextTest {

    private fun entry(
        day: String,
        time: String,
        courseCode: String = "CSC410"
    ) = WeeklyScheduleEntry(
        day = day,
        time = time,
        courseTitle = "Mobile Application Development",
        faculty = "Ayesha Khan",
        location = "Lab 3",
        edpCode = "",
        courseCode = courseCode
    )

    @Test
    fun `keys come back in week order however the portal ordered its rows`() {
        val groups = groupScheduleByDay(
            listOf(
                entry("FRI", "09:00 AM - 10:00 AM"),
                entry("MON", "09:00 AM - 10:00 AM"),
                entry("SUN", "09:00 AM - 10:00 AM"),
                entry("WED", "09:00 AM - 10:00 AM")
            )
        )
        assertEquals(listOf("MON", "WED", "FRI", "SUN"), groups.keys.toList())
    }

    @Test
    fun `a day with no weekday sorts after every real day`() {
        // The portal can emit a blank date cell; normalizeDay maps it to OTHER.
        // It must not be dropped, and it must not displace a real weekday.
        val groups = groupScheduleByDay(
            listOf(
                entry("", "09:00 AM - 10:00 AM"),
                entry("THU", "09:00 AM - 10:00 AM"),
                entry("MON", "09:00 AM - 10:00 AM")
            )
        )
        assertEquals(listOf("MON", "THU", "OTHER"), groups.keys.toList())
    }

    @Test
    fun `an entry with an unparseable time sorts last within its day`() {
        // "TBA" yields a null start minute. Sorting it to the top would put an
        // unreadable row ahead of the 8 AM lecture.
        val monday = groupScheduleByDay(
            listOf(
                entry("MON", "TBA", courseCode = "LATE"),
                entry("MON", "09:00 AM - 10:00 AM", courseCode = "MORNING"),
                entry("MON", "01:00 PM - 02:00 PM", courseCode = "MIDDAY")
            )
        ).getValue("MON")

        assertEquals(
            listOf("MORNING", "MIDDAY", "LATE"),
            monday.map { it.courseCode }
        )
    }

    @Test
    fun `entries within a day are chronological`() {
        val monday = groupScheduleByDay(
            listOf(
                entry("MON", "02:00 PM - 03:00 PM", courseCode = "PM"),
                entry("MON", "09:00 AM - 10:00 AM", courseCode = "AM"),
                entry("MON", "11:00 AM - 12:00 PM", courseCode = "MID")
            )
        ).getValue("MON")

        assertEquals(listOf("AM", "MID", "PM"), monday.map { it.courseCode })
    }

    @Test
    fun `an overnight class is ordered by its start, not rejected`() {
        // A 22:00 start must stay inside its own day rather than wrapping to the
        // top of the week.
        val groups = groupScheduleByDay(
            listOf(
                entry("MON", "22:00 - 02:00", courseCode = "NIGHT"),
                entry("MON", "09:00 AM - 10:00 AM", courseCode = "MORNING")
            )
        )
        assertTrue(groups.containsKey("MON"))
        assertEquals(
            listOf("MORNING", "NIGHT"),
            groups.getValue("MON").map { it.courseCode }
        )
    }
}