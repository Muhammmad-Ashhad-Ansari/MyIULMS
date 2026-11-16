package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies the 4-column exam share row emitted by [examScheduleRow]: the same
 * date and time normalisation the on-screen card uses, with the course title
 * untouched so the renderer can wrap it instead of truncating it.
 */
class ExamScheduleShareTest {

    private fun entry(
        day: String = "Sunday, Nov 22nd, 2026",
        time: String = "9:00 AM - 10:30 AM",
        title: String = "Artificial Intelligence",
        faculty: String = "DR. LUBN A AZIZ",
        location: String = "202"
    ) = ExamScheduleEntry(
        dayAndDate = day,
        time = time,
        courseTitle = title,
        faculty = faculty,
        location = location,
        edpCode = "032610404"
    )

    @Test
    fun `row carries date, time, course and location`() {
        val row = examScheduleRow(entry())

        assertEquals(
            listOf(
                "Sun, 22 Nov 2026",
                "9:00 AM - 10:30 AM",
                "Artificial Intelligence",
                "202"
            ),
            row
        )
    }

    @Test
    fun `faculty never reaches the shared image`() {
        val row = examScheduleRow(entry(faculty = "DR. LUBN A AZIZ"))

        assertEquals(4, row.size)
        assertFalse(row.contains("Dr. Lubn A Aziz"))
    }

    @Test
    fun `compact time is normalised`() {
        val row = examScheduleRow(entry(day = "Monday, Nov 23rd, 2026", time = "09:00-10:30"))

        assertEquals("Mon, 23 Nov 2026", row[0])
        assertEquals("9:00 AM - 10:30 AM", row[1])
    }

    @Test
    fun `long course titles are passed through whole`() {
        val row = examScheduleRow(entry(title = "PARALLEL & DISTRIBUTED COMPUTING"))

        assertEquals("PARALLEL & DISTRIBUTED COMPUTING", row[2])
        assertFalse(row[2].contains("…"))
    }

    @Test
    fun `row falls back for tba time and blank optional fields`() {
        val row = examScheduleRow(
            ExamScheduleEntry("TBA", "TBA", "", "", "", "")
        )

        assertEquals(listOf("TBA", "TBA", "Exam", "—"), row)
    }
}
