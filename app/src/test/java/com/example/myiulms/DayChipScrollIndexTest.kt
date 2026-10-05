package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the day-chip -> sticky-header scroll arithmetic in [dayHeaderItemIndex].
 *
 * This is the invariant most easily broken by a structural change: adding a
 * header item shifts every group index, and the resulting misroute compiles
 * silently and ships as a scroll to the wrong day. These tests pin the exact
 * indices the LazyList actually emits.
 */
class DayChipScrollIndexTest {

    private val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private fun entry(courseCode: String) = WeeklyScheduleEntry(
        day = "MON",
        time = "08:00 - 09:00",
        courseTitle = "Course $courseCode",
        faculty = "Dr. Q",
        location = "E-806",
        edpCode = "CS-201",
        courseCode = courseCode
    )

    /** Builds a groups map with `count` entries per named day. */
    private fun groups(vararg spec: Pair<String, Int>): Map<String, List<WeeklyScheduleEntry>> =
        spec.toMap().mapValues { (day, count) ->
            (0 until count).map { entry("$day-$it") }
        }

    @Test
    fun `first day with classes targets the first sticky header`() {
        // Heading(0), chips(1), countdown(2), MON header(3).
        val groups = groups("MON" to 2)
        assertEquals(3, dayHeaderItemIndex("MON", weekDays, setOf("MON"), groups))
    }

    @Test
    fun `second day skips the first day's header and every one of its entries`() {
        // MON header(3), MON a(4), MON b(5), TUE header(6).
        val groups = groups("MON" to 2, "TUE" to 1)
        assertEquals(6, dayHeaderItemIndex("TUE", weekDays, setOf("MON", "TUE"), groups))
    }

    @Test
    fun `days with no classes are skipped exactly as the list skips them`() {
        // Only WED and FRI have classes. WED header(3), FRI header(6): the two
        // empty days ahead of them must contribute nothing.
        val groups = groups("WED" to 2, "FRI" to 3)
        val classDays = setOf("WED", "FRI")

        assertEquals(3, dayHeaderItemIndex("WED", weekDays, classDays, groups))
        assertEquals(6, dayHeaderItemIndex("FRI", weekDays, classDays, groups))
    }

    @Test
    fun `index accumulates correctly across many days`() {
        val groups = groups("MON" to 1, "WED" to 3, "FRI" to 2)
        val classDays = setOf("MON", "WED", "FRI")

        assertEquals(3, dayHeaderItemIndex("MON", weekDays, classDays, groups))
        // MON header(3) + 1 entry(4) -> WED header(5)
        assertEquals(5, dayHeaderItemIndex("WED", weekDays, classDays, groups))
        // WED header(5) + 3 entries(6,7,8) -> FRI header(9)
        assertEquals(9, dayHeaderItemIndex("FRI", weekDays, classDays, groups))
    }

    @Test
    fun `a day listed in classDays but absent from groups does not break the count`() {
        // Portal edge case: the chip set and the grouped map can disagree after a
        // partial parse. A missing group must not throw or skew later indices.
        val groups = groups("MON" to 2)
        val classDays = setOf("MON", "TUE")

        assertEquals(3, dayHeaderItemIndex("MON", weekDays, classDays, groups))
        // MON contributes 1 header + 2 entries; TUE's own absent group adds 0.
        assertEquals(6, dayHeaderItemIndex("TUE", weekDays, classDays, groups))
    }

    @Test
    fun `every target index is strictly greater than the last structural item`() {
        // The countdown card sits at index 2. No day chip may ever scroll to an
        // index at or below it, or the tap would appear to do nothing.
        val groups = groups("MON" to 1, "TUE" to 1, "WED" to 1)
        val classDays = setOf("MON", "TUE", "WED")

        classDays.forEach { day ->
            assertEquals(
                "day $day resolved into the structural header",
                true,
                dayHeaderItemIndex(day, weekDays, classDays, groups) > 2
            )
        }
    }
}