package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the day-chip -> day-label scroll arithmetic in [dayGroupLabelItemIndex].
 *
 * This is the invariant most easily broken by a structural change: adding a
 * header item shifts every group index, and the resulting misroute compiles
 * silently and ships as a scroll to the wrong day. These tests pin the exact
 * indices the LazyList actually emits.
 *
 * Every expected value shifted down by exactly 1 when the day-chip bar was
 * hoisted OUT of the LazyColumn into a static sibling above it. That is the one
 * structural change that removes a slot: `item {}` and `stickyHeader {}` each
 * consume one index, but a composable that is not a list row consumes none.
 *
 * The arithmetic itself is unchanged -- only the constant
 * [ITEMS_BEFORE_DAY_GROUPS] moved, which is exactly what that constant exists
 * to absorb.
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
    fun `first day with classes targets the first day label`() {
        // Heading(0), countdown(1), MON label(2). The chips are not a row.
        val groups = groups("MON" to 2)
        assertEquals(2, dayGroupLabelItemIndex("MON", weekDays, setOf("MON"), groups))
    }

    @Test
    fun `second day skips the first day's header and every one of its entries`() {
        // MON label(2), MON a(3), MON b(4), TUE label(5).
        val groups = groups("MON" to 2, "TUE" to 1)
        assertEquals(5, dayGroupLabelItemIndex("TUE", weekDays, setOf("MON", "TUE"), groups))
    }

    @Test
    fun `days with no classes are skipped exactly as the list skips them`() {
        // Only WED and FRI have classes. WED label(2), FRI label(5): the two
        // empty days ahead of them must contribute nothing.
        val groups = groups("WED" to 2, "FRI" to 3)
        val classDays = setOf("WED", "FRI")

        assertEquals(2, dayGroupLabelItemIndex("WED", weekDays, classDays, groups))
        assertEquals(5, dayGroupLabelItemIndex("FRI", weekDays, classDays, groups))
    }

    @Test
    fun `index accumulates correctly across many days`() {
        val groups = groups("MON" to 1, "WED" to 3, "FRI" to 2)
        val classDays = setOf("MON", "WED", "FRI")

        assertEquals(2, dayGroupLabelItemIndex("MON", weekDays, classDays, groups))
        // MON label(2) + 1 entry(3) -> WED label(4)
        assertEquals(4, dayGroupLabelItemIndex("WED", weekDays, classDays, groups))
        // WED label(4) + 3 entries(5,6,7) -> FRI label(8)
        assertEquals(8, dayGroupLabelItemIndex("FRI", weekDays, classDays, groups))
    }

    @Test
    fun `a day listed in classDays but absent from groups does not break the count`() {
        // Portal edge case: the chip set and the grouped map can disagree after a
        // partial parse. A missing group must not throw or skew later indices.
        val groups = groups("MON" to 2)
        val classDays = setOf("MON", "TUE")

        assertEquals(2, dayGroupLabelItemIndex("MON", weekDays, classDays, groups))
        // MON contributes 1 label + 2 entries; TUE's own absent group adds 0.
        assertEquals(5, dayGroupLabelItemIndex("TUE", weekDays, classDays, groups))
    }

    @Test
    fun `the first day label sits at index 2 under the static chip bar`() {
        // Layout: heading(0), countdown(1), first day label(2). The chip bar is
        // a static sibling ABOVE the list, not a row in it, so it occupies no
        // index. The chips' scrollOffset is 0 for the same reason: the list's
        // own content edge already sits beneath the bar.
        val groups = groups("MON" to 2, "TUE" to 1)
        val classDays = setOf("MON", "TUE")

        assertEquals(2, dayGroupLabelItemIndex("MON", weekDays, classDays, groups))
        assertEquals(ITEMS_BEFORE_DAY_GROUPS, dayGroupLabelItemIndex("MON", weekDays, classDays, groups))
    }

    @Test
    fun `every target index is strictly greater than the last structural item`() {
        // The countdown card sits at index 1. No day chip may ever scroll to an
        // index at or below it, or the tap would appear to do nothing.
        val groups = groups("MON" to 1, "TUE" to 1, "WED" to 1)
        val classDays = setOf("MON", "TUE", "WED")

        classDays.forEach { day ->
            assertEquals(
                "day $day resolved into the structural header",
                true,
                dayGroupLabelItemIndex(day, weekDays, classDays, groups) > 1
            )
        }
    }
}