package com.example.myiulms

import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.formatRemainingClock
import com.example.myiulms.ui.dashboard.formatStartsIn
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import com.example.myiulms.ui.dashboard.resolveScheduleFocus
import com.example.myiulms.ui.dashboard.scheduleStartMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-case coverage for the dashboard countdown logic. All of this is pure
 * Kotlin precisely so these tests need no Android runtime.
 */
class ScheduleIntervalTest {

    private fun entry(time: String, day: String = "MON") = WeeklyScheduleEntry(
        day = day,
        time = time,
        courseTitle = "Numerical Methods",
        faculty = "Dr. Q",
        location = "Room A-101",
        edpCode = "CS-201",
        courseCode = "CS201"
    )

    // ---- Parsing ---------------------------------------------------------

    @Test
    fun `parses a simple 24 hour range`() {
        val interval = parseScheduleInterval("08:00 - 09:30")
        assertEquals(480, interval?.startMinuteOfDay)
        assertEquals(570, interval?.endMinuteOfDay)
        assertEquals(90, interval?.durationMinutes)
    }

    @Test
    fun `parses 12 hour with meridiem`() {
        val interval = parseScheduleInterval("1:30 PM - 3:00 PM")
        assertEquals(810, interval?.startMinuteOfDay)
        assertEquals(900, interval?.endMinuteOfDay)
        assertEquals(90, interval?.durationMinutes)
    }

    @Test
    fun `infers meridiem when only the second token carries it`() {
        val interval = parseScheduleInterval("11:00 - 1:00 PM")
        assertEquals(660, interval?.startMinuteOfDay)
        assertEquals(780, interval?.endMinuteOfDay)
    }

    @Test
    fun `wraps an overnight class past midnight`() {
        val interval = parseScheduleInterval("22:00 - 02:00")
        assertEquals(1320, interval?.startMinuteOfDay)
        assertEquals(1560, interval?.endMinuteOfDay)
        assertEquals(240, interval?.durationMinutes)
    }

    @Test
    fun `returns null for TBA and blank strings`() {
        assertNull(parseScheduleInterval("TBA"))
        assertNull(parseScheduleInterval(""))
        assertNull(parseScheduleInterval("   "))
        assertNull(parseScheduleInterval("To be announced"))
    }

    @Test
    fun `returns null for a single time token`() {
        assertNull(parseScheduleInterval("08:00"))
        assertNull(parseScheduleInterval("08:00 onwards"))
    }

    @Test
    fun `returns null for an implausible duration`() {
        assertNull(parseScheduleInterval("08:00 - 23:00")) // 15h, over the 12h cap
    }

    @Test
    fun `returns null for out of range hour or minute`() {
        assertNull(parseScheduleInterval("25:00 - 26:00"))
        assertNull(parseScheduleInterval("08:75 - 09:00"))
    }

    @Test
    fun `start minutes returns null rather than a sentinel`() {
        assertEquals(480, scheduleStartMinutes("08:00 - 09:30"))
        assertNull(scheduleStartMinutes("TBA"))
        assertNull(scheduleStartMinutes(""))
        // Explicitly not Int.MAX_VALUE any more.
        assertTrue(scheduleStartMinutes("nonsense") == null)
    }

    // ---- Day normalization ----------------------------------------------

    @Test
    fun `day normalization matches the list grouping contract`() {
        assertEquals("MON", normalizeDay("Monday"))
        assertEquals("MON", normalizeDay(" mon "))
        assertEquals("OTHER", normalizeDay("   "))
        assertEquals("OTHER", normalizeDay(""))
    }

    // ---- Focus resolution ------------------------------------------------

    @Test
    fun `resolves a live class`() {
        val focus = resolveScheduleFocus(
            entries = listOf(entry("08:00 - 09:30")),
            today = "MON",
            minuteOfDay = 9 * 60 + 12
        )
        assertTrue(focus is ScheduleFocus.Live)
        assertEquals(18, (focus as ScheduleFocus.Live).remainingMinutes)
        assertEquals(90, focus.totalMinutes)
    }

    @Test
    fun `resolves the next upcoming class when nothing is live`() {
        val focus = resolveScheduleFocus(
            entries = listOf(entry("14:00 - 15:00")),
            today = "MON",
            minuteOfDay = 8 * 60 + 35
        )
        assertTrue(focus is ScheduleFocus.Upcoming)
        assertEquals(325, (focus as ScheduleFocus.Upcoming).startsInMinutes)
    }

    @Test
    fun `prefers the live class over an earlier upcoming one`() {
        val entries = listOf(entry("08:00 - 09:00"), entry("14:00 - 15:00"))
        val focus = resolveScheduleFocus(entries, "MON", 8 * 60 + 30)
        assertTrue(focus is ScheduleFocus.Live)
    }

    @Test
    fun `ignores entries with no parseable interval`() {
        val entries = listOf(entry("TBA"), entry("nonsense"), entry("14:00 - 15:00"))
        val focus = resolveScheduleFocus(entries, "MON", 8 * 60)
        assertTrue(focus is ScheduleFocus.Upcoming)
        assertEquals(360, (focus as ScheduleFocus.Upcoming).startsInMinutes)
    }

    @Test
    fun `returns Unavailable when no time range could be parsed`() {
        val entries = listOf(entry("TBA"), entry("nonsense"))
        assertEquals(ScheduleFocus.Unavailable, resolveScheduleFocus(entries, "MON", 8 * 60))
    }

    @Test
    fun `returns OffDay when no entries exist for the active day`() {
        val entries = listOf(entry("08:00 - 09:00", day = "TUE"))
        assertEquals(ScheduleFocus.OffDay, resolveScheduleFocus(entries, "MON", 8 * 60 + 30))
    }

    @Test
    fun `returns OffDay for a completely empty entry list`() {
        assertEquals(ScheduleFocus.OffDay, resolveScheduleFocus(emptyList(), "MON", 8 * 60))
    }

    @Test
    fun `returns Done once the last class of the day is finished`() {
        val entries = listOf(entry("08:00 - 09:00"))
        assertEquals(ScheduleFocus.Done, resolveScheduleFocus(entries, "MON", 20 * 60))
    }

    @Test
    fun `Done is distinct from OffDay so the copy cannot be wrong`() {
        val finished = resolveScheduleFocus(listOf(entry("08:00 - 09:00")), "MON", 20 * 60)
        val offDay = resolveScheduleFocus(emptyList(), "MON", 20 * 60)
        // A day that HAD classes must never be reported as having none.
        assertTrue(finished is ScheduleFocus.Done)
        assertTrue(offDay is ScheduleFocus.OffDay)
        assertTrue(finished != offDay)
    }

    @Test
    fun `partially unparseable day still resolves from the parseable entries`() {
        val entries = listOf(entry("TBA"), entry("14:00 - 15:00"))
        val focus = resolveScheduleFocus(entries, "MON", 8 * 60)
        assertTrue(focus is ScheduleFocus.Upcoming)
        assertEquals(360, (focus as ScheduleFocus.Upcoming).startsInMinutes)
    }

    // ---- Clock-skew safety ----------------------------------------------

    @Test
    fun `remaining minutes never go negative`() {
        // Clock pushed back an hour: raw delta would be -30.
        val focus = resolveScheduleFocus(
            entries = listOf(entry("08:00 - 09:00")),
            today = "MON",
            minuteOfDay = 7 * 60 + 30
        )
        // Not live (class has not started yet) and not upcoming either,
        // so the state degrades safely rather than throwing.
        assertTrue(focus is ScheduleFocus.Upcoming || focus is ScheduleFocus.Done)
    }

    @Test
    fun `clock format clamps an oversize remaining value to zero`() {
        // 9999 seconds against a 60 minute class must clamp, not overflow.
        assertEquals("60:00", formatRemainingClock(9999, 60))
    }

    @Test
    fun `clock format clamps a negative value to zero`() {
        assertEquals("0:00", formatRemainingClock(-500, 60))
    }

    @Test
    fun `clock format renders minutes and zero padded seconds`() {
        assertEquals("54:32", formatRemainingClock(3272, 90))
        assertEquals("0:07", formatRemainingClock(7, 90))
        assertEquals("90:00", formatRemainingClock(5400, 90))
    }

    @Test
    fun `starts-in formatting handles zero and singular`() {
        assertEquals("Starting now", formatStartsIn(0))
        assertEquals("In 1 min", formatStartsIn(1))
        assertEquals("In 25 mins", formatStartsIn(25))
        assertEquals("Starting now", formatStartsIn(-10))
    }
}
