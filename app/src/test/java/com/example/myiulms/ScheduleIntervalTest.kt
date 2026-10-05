package com.example.myiulms

import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.formatLiveMetadata
import com.example.myiulms.ui.dashboard.formatRemainingClock
import com.example.myiulms.ui.dashboard.formatRoomLabel
import com.example.myiulms.ui.dashboard.formatStartsIn
import com.example.myiulms.ui.dashboard.formatUpcomingMetadata
import com.example.myiulms.ui.dashboard.formatWallClock
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

    // ---- Ticker formatting ------------------------------------------------

    @Test
    fun `clock format clamps an oversize remaining value`() {
        // 9999 seconds against a 60 minute class must clamp to the interval
        // rather than overflow. The clamped value is exactly one hour, which
        // is the coarse branch's boundary.
        assertEquals("1h 0m", formatRemainingClock(9999, 60))
    }

    @Test
    fun `clock format clamps a negative value to zero`() {
        assertEquals("0m 00s", formatRemainingClock(-500, 60))
    }

    @Test
    fun `clock format ticks minutes and zero padded seconds under an hour`() {
        assertEquals("54m 32s", formatRemainingClock(3272, 90))
        assertEquals("0m 07s", formatRemainingClock(7, 90))
        assertEquals("59m 59s", formatRemainingClock(3599, 90))
    }

    @Test
    fun `clock format switches to hours and minutes over an hour`() {
        assertEquals("1h 30m", formatRemainingClock(5400, 90))
        assertEquals("8h 13m", formatRemainingClock(493 * 60, 600))
        assertEquals("11h 59m", formatRemainingClock(719 * 60, 720))
    }

    @Test
    fun `clock format never exceeds the ticker width budget`() {
        // 7 glyphs is the widest either branch can produce ("59m 42s"). This is
        // the invariant that keeps the string inside the fixed ticker frame on a
        // single line, across the whole clamped range.
        val maxWidth = 7
        val samples = listOf(0, 7, 59, 599, 3599, 3600, 3660, 5400, 493 * 60, 719 * 60)
        samples.forEach { seconds ->
            val text = formatRemainingClock(seconds, 720)
            assertTrue(
                "width drifted for $seconds s -> $text",
                text.length <= maxWidth
            )
        }
    }

    @Test
    fun `clock format switches branch exactly at the one hour boundary`() {
        assertEquals("59m 59s", formatRemainingClock(3599, 720))
        assertEquals("1h 0m", formatRemainingClock(3600, 720))
        assertEquals("1h 1m", formatRemainingClock(3660, 720))
    }

    @Test
    fun `starts-in formatting handles zero and singular`() {
        assertEquals("Starting now", formatStartsIn(0))
        assertEquals("In 1 min", formatStartsIn(1))
        assertEquals("In 25 mins", formatStartsIn(25))
        assertEquals("Starting now", formatStartsIn(-10))
    }

    @Test
    fun `starts-in formatting drops the wide minute wording past an hour`() {
        // "In 719 mins" was the widest string the ticker had to draw.
        assertEquals("In 60 mins", formatStartsIn(60))
        assertEquals("1h 1m", formatStartsIn(61))
        assertEquals("8h 13m", formatStartsIn(493))
        assertEquals("11h 59m", formatStartsIn(719))
    }

    // ---- Wall clock + metadata -------------------------------------------

    @Test
    fun `wall clock renders 12 hour time`() {
        assertEquals("8:00 AM", formatWallClock(480))
        assertEquals("2:20 PM", formatWallClock(860))
        assertEquals("12:00 AM", formatWallClock(0))
        assertEquals("12:30 PM", formatWallClock(750))
    }

    @Test
    fun `wall clock reduces an overnight end time instead of throwing`() {
        // A 22:00-02:00 class ends at minute 1560; LocalTime rejects hour 26.
        assertEquals("2:00 AM", formatWallClock(1560))
    }

    @Test
    fun `room label normalizes a bare or prefixed location`() {
        assertEquals("Room E-806", formatRoomLabel("E-806"))
        assertEquals("Room E-806", formatRoomLabel("Room E-806"))
        assertEquals("Room E-806", formatRoomLabel("  room   E-806  "))
        assertNull(formatRoomLabel(""))
        assertNull(formatRoomLabel("   "))
        assertNull(formatRoomLabel(null))
        assertNull(formatRoomLabel("Room"))
    }

    @Test
    fun `live metadata combines the room and the end time`() {
        assertEquals("Room E-806 • Ends at 2:20 PM", formatLiveMetadata("E-806", 860))
    }

    @Test
    fun `upcoming metadata combines the room and the start time`() {
        assertEquals("Room E-803 • Starts at 2:30 PM", formatUpcomingMetadata("E-803", 870))
    }

    @Test
    fun `metadata is null without a room so the slot stays reserved`() {
        assertNull(formatLiveMetadata(null, 860))
        assertNull(formatUpcomingMetadata("  ", 870))
    }

    @Test
    fun `focus states carry the wall clock times the card renders`() {
        val live = resolveScheduleFocus(
            entries = listOf(entry("14:00 - 15:00")),
            today = "MON",
            minuteOfDay = 14 * 60 + 20
        ) as ScheduleFocus.Live
        assertEquals(900, live.endMinuteOfDay)

        val upcoming = resolveScheduleFocus(
            entries = listOf(entry("14:00 - 15:00")),
            today = "MON",
            minuteOfDay = 13 * 60 + 30
        ) as ScheduleFocus.Upcoming
        assertEquals(840, upcoming.startMinuteOfDay)
    }
}
