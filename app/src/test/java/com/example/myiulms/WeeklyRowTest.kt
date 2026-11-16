package com.example.myiulms

import com.example.myiulms.ui.dashboard.groupScheduleByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the data-driven list structure: [weeklyRows] is the single description
 * of what the list emits, and [dayGroupLabelItemIndex] resolves against it.
 *
 * A mismatch here compiles fine and ships as a chip that scrolls to the wrong
 * day, so the row order and the first/last flags are pinned explicitly.
 */
class WeeklyRowTest {

    private val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private fun entry(
        day: String,
        courseCode: String,
        time: String = "08:00 - 09:00"
    ) = WeeklyScheduleEntry(
        day = day,
        time = time,
        courseTitle = "Course $courseCode",
        faculty = "Dr. Q",
        location = "E-806",
        edpCode = "CS-201",
        courseCode = courseCode
    )

    private fun groups(vararg spec: Pair<String, Int>): Map<String, List<WeeklyScheduleEntry>> =
        spec.toMap().mapValues { (day, count) ->
            (0 until count).map { entry(day, "$day-$it") }
        }

    @Test
    fun `only structural rows precede the first day group`() {
        val rows = weeklyRows(emptyMap())

        assertEquals(listOf<WeeklyRow>(WeeklyRow.Heading, WeeklyRow.Focus), rows)
        assertEquals(rows.size, ITEMS_BEFORE_DAY_GROUPS)
    }

    @Test
    fun `rows follow group order with one header and one entry per class`() {
        val groups = groupScheduleByDay(
            listOf(
                entry("TUE", "TUE-0", "09:00 - 10:00"),
                entry("MON", "MON-0", "08:00 - 09:00")
            )
        )

        val rows = weeklyRows(groups)

        assertEquals(WeeklyRow.Heading, rows[0])
        assertEquals(WeeklyRow.Focus, rows[1])
        assertEquals(WeeklyRow.DayHeader("MON", 1), rows[2])
        assertTrue(rows[3] is WeeklyRow.Entry)
        assertEquals("MON", (rows[3] as WeeklyRow.Entry).day)
        assertEquals("MON-0", (rows[3] as WeeklyRow.Entry).entry.courseCode)
        assertEquals(WeeklyRow.DayHeader("TUE", 1), rows[4])
        assertEquals("TUE", (rows[5] as WeeklyRow.Entry).day)
    }

    @Test
    fun `first and last flags bracket each day's entries`() {
        val rows = weeklyRows(groups("MON" to 3, "TUE" to 1))
        val entries = rows.filterIsInstance<WeeklyRow.Entry>()

        assertEquals(listOf(true, false, false, true), entries.map { it.isFirst })
        assertEquals(listOf(false, false, true, true), entries.map { it.isLast })
    }

    @Test
    fun `chip index resolves to the header row for a present day`() {
        val groups = groups("MON" to 2, "TUE" to 1)
        val rows = weeklyRows(groups)

        listOf("MON", "TUE").forEach { day ->
            val index = dayGroupLabelItemIndex(day, weekDays, groups.keys, groups)
            assertEquals(
                WeeklyRow.DayHeader(day, groups.getValue(day).size),
                rows[index]
            )
        }
    }

    @Test
    fun `chip index for an absent advertised day keeps the previous fallback`() {
        val groups = groups("MON" to 2)

        assertEquals(
            5,
            dayGroupLabelItemIndex("TUE", weekDays, setOf("MON", "TUE"), groups)
        )
    }

    @Test
    fun `metadata keeps room and faculty as separate facts`() {
        assertEquals(
            WeeklyClassMeta(room = "Room E-806", faculty = "Dr. Lubna Aziz"),
            weeklyClassMeta("E-806", " Dr. Lubna Aziz ")
        )
    }

    @Test
    fun `metadata drops a blank part instead of leaving an empty slot`() {
        assertEquals(
            WeeklyClassMeta(room = "Room E-806", faculty = null),
            weeklyClassMeta("E-806", " ")
        )
        assertEquals(
            WeeklyClassMeta(room = null, faculty = "Dr. Lubna Aziz"),
            weeklyClassMeta("", "Dr. Lubna Aziz")
        )
        assertEquals(WeeklyClassMeta(room = null, faculty = null), weeklyClassMeta("", ""))
        assertEquals(WeeklyClassMeta(room = null, faculty = null), weeklyClassMeta(null, null))
    }

    @Test
    fun `spoken duration spells out hours and minutes for the pill label`() {
        assertEquals("1 minute", spokenDuration(1))
        assertEquals("50 minutes", spokenDuration(50))
        assertEquals("1 hour", spokenDuration(60))
        assertEquals("1 hour 20 minutes", spokenDuration(80))
        assertEquals("2 hours", spokenDuration(120))
    }

    @Test
    fun `a location that is only the word Room normalises to no room`() {
        assertEquals(
            WeeklyClassMeta(room = null, faculty = "Dr. Lubna Aziz"),
            weeklyClassMeta("Room", "Dr. Lubna Aziz")
        )
    }
}
