package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the scroll-driven chip highlight in [dominantDay] / [rowForKey].
 *
 * This logic runs inside a scroll callback on every frame the list moves, and
 * its only visible symptom is a chip that highlights the wrong day — which
 * looks identical to the highlight simply not working. Keeping it as a pure
 * function over visible keys means the arithmetic is pinned here rather than
 * discovered by flicking the list on a device.
 *
 * The keys fed in are exactly what `LazyList` reports in
 * `layoutInfo.visibleItemsInfo`, i.e. the same literals the list body emits.
 */
class DayHighlightDominanceTest {

    private fun entry(day: String, courseCode: String) = WeeklyScheduleEntry(
        day = day,
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
            (0 until count).map { entry(day, "$day-$it") }
        }

    /** Visible keys for one day label followed by [cardCount] of its cards. */
    private fun dayKeys(day: String, cardCount: Int): List<Any> =
        buildList {
            add(dayLabelKey(day))
            repeat(cardCount) { add(entryKey(day, entry(day, "$day-$it"))) }
        }

    private val structural = listOf(
        ITEM_KEY_HEADING,
        ITEM_KEY_DAY_CHIPS,
        ITEM_KEY_DASHBOARD
    )

    // --- Fixture 1: first day ------------------------------------------------

    @Test
    fun `first day of the week resolves to itself when its group fills the viewport`() {
        val groups = groups("MON" to 2)
        val visible = structural + dayKeys("MON", cardCount = 2)

        assertEquals("MON", dominantDay(visible, groups, classDays = setOf("MON")))
    }

    @Test
    fun `the first day wins over a leading day label alone`() {
        // Mid-fling off the top of the list: MON's label is leaving the screen
        // while its first card is arriving. One visible row beats zero.
        val groups = groups("MON" to 3)
        val visible = structural + dayKeys("MON", cardCount = 1)

        assertEquals("MON", dominantDay(visible, groups, classDays = setOf("MON")))
    }

    // --- Fixture 2: middle day ----------------------------------------------

    @Test
    fun `middle day wins once its cards outnumber the previous day's remainder`() {
        // MON 3 cards(3), TUE 2 cards(2), WED 4 cards(4) -> WED dominates.
        val groups = groups("MON" to 3, "TUE" to 2, "WED" to 4)
        val classDays = setOf("MON", "TUE", "WED")

        val visible = buildList {
            repeat(3) { add(entryKey("MON", entry("MON", "MON-$it"))) }
            add(dayLabelKey("TUE"))
            repeat(2) { add(entryKey("TUE", entry("TUE", "TUE-$it"))) }
            add(dayLabelKey("WED"))
            repeat(4) { add(entryKey("WED", entry("WED", "WED-$it"))) }
        }

        assertEquals("WED", dominantDay(visible, groups, classDays))
    }

    @Test
    fun `a tie resolves to the earlier day so the highlight never oscillates`() {
        // Exactly balanced viewport. MON shows 2 cards; TUE shows its label
        // plus 1 card = 2 rows. If the tie-break were not stable the chip would
        // flicker between the two days on sub-pixel scroll jitter.
        val groups = groups("MON" to 2, "TUE" to 2)
        val classDays = setOf("MON", "TUE")

        val visible = buildList {
            repeat(2) { add(entryKey("MON", entry("MON", "MON-$it"))) }
            add(dayLabelKey("TUE"))
            add(entryKey("TUE", entry("TUE", "TUE-0")))
        }

        assertEquals("MON", dominantDay(visible, groups, classDays))
    }

    // --- Fixture 3: last day ------------------------------------------------

    @Test
    fun `last day of the week resolves to itself at the bottom of the list`() {
        val groups = groups("SAT" to 2, "SUN" to 3)
        val classDays = setOf("SAT", "SUN")

        val visible = buildList {
            add(entryKey("SAT", entry("SAT", "SAT-0")))
            add(dayLabelKey("SUN"))
            repeat(3) { add(entryKey("SUN", entry("SUN", "SUN-$it"))) }
        }

        assertEquals("SUN", dominantDay(visible, groups, classDays))
    }

    @Test
    fun `a trailing day with a single card still takes the highlight`() {
        // SUN has one class only: its label plus one card is 2 rows, beating
        // SAT's single trailing card. Weighting by rows, not by index, keeps a
        // one-class day reachable by scroll instead of being skipped over.
        val groups = groups("SAT" to 5, "SUN" to 1)
        val classDays = setOf("SAT", "SUN")

        val visible = buildList {
            add(entryKey("SAT", entry("SAT", "SAT-4")))
            add(dayLabelKey("SUN"))
            add(entryKey("SUN", entry("SUN", "SUN-0")))
        }

        assertEquals("SUN", dominantDay(visible, groups, classDays))
    }

    // --- Fixture 4: gap day -------------------------------------------------

    @Test
    fun `a week with missing days highlights across the gap`() {
        // Portal returned only WED and FRI. TUE and THU emit no rows at all, and
        // the jump must not reset or stall the highlight. WED's label has
        // already scrolled off, leaving 2 rows; FRI shows its label plus 2.
        val groups = groups("WED" to 2, "FRI" to 3)
        val classDays = setOf("WED", "FRI")

        val visible = buildList {
            repeat(2) { add(entryKey("WED", entry("WED", "WED-$it"))) }
            add(dayLabelKey("FRI"))
            repeat(2) { add(entryKey("FRI", entry("FRI", "FRI-$it"))) }
        }

        assertEquals("FRI", dominantDay(visible, groups, classDays))
    }

    @Test
    fun `a day present in classDays but absent from groups is ignored not counted`() {
        // Partial-parse edge case: classDays advertises TUE but no TUE rows
        // exist. A stray unmatched key must not be attributed to TUE.
        val groups = groups("WED" to 2)
        val classDays = setOf("WED", "TUE")

        val visible = dayKeys("WED", cardCount = 2) + "tue-UNKNOWN-CS-101 10:00 - 11:00"

        assertEquals("WED", dominantDay(visible, groups, classDays))
    }

    @Test
    fun `a dominating day missing from classDays is discarded`() {
        val groups = groups("MON" to 3, "TUE" to 1)
        val visible = dayKeys("MON", cardCount = 3) + dayKeys("TUE", cardCount = 1)

        // classDays forgot TUE; the chip may only light up for an advertised day.
        assertEquals("MON", dominantDay(visible, groups, classDays = setOf("MON")))
    }

    // --- Structural-only viewport ------------------------------------------

    @Test
    fun `a viewport showing only structural rows resolves to no day`() {
        val groups = groups("MON" to 2, "TUE" to 1)

        // Nothing should be highlighted while the heading, the pinned chips and
        // the countdown card still own the screen; the observer skips null.
        assertNull(dominantDay(structural, groups, classDays = setOf("MON", "TUE")))
        assertNull(dominantDay(emptyList(), groups, classDays = setOf("MON", "TUE")))
    }

    // --- key mapping --------------------------------------------------------

    @Test
    fun `structural keys map to their own row types`() {
        val groups = groups("MON" to 1)

        assertEquals(ScheduleRow.Heading, rowForKey(ITEM_KEY_HEADING, groups))
        assertEquals(ScheduleRow.DayChips, rowForKey(ITEM_KEY_DAY_CHIPS, groups))
        assertEquals(ScheduleRow.FocusCard, rowForKey(ITEM_KEY_DASHBOARD, groups))
    }

    @Test
    fun `day label keys round-trip through the shared prefix`() {
        val groups = groups("SUN" to 1)

        assertEquals(ScheduleRow.DayLabel("SUN"), rowForKey(dayLabelKey("SUN"), groups))
    }

    @Test
    fun `entry keys reverse-resolve back to their day`() {
        val groups = groups("THU" to 2)
        val key = entryKey("THU", entry("THU", "THU-1"))

        assertEquals(ScheduleRow.EntryCard("THU"), rowForKey(key, groups))
    }

    @Test
    fun `an unknown key degrades to a structural row instead of throwing`() {
        // A future row type must not crash the scroll callback.
        val groups = groups("MON" to 1)

        assertEquals(ScheduleRow.Heading, rowForKey("some-future-row", groups))
        assertEquals(ScheduleRow.Heading, rowForKey(42, groups))
    }
}