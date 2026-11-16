package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the 5-column exam share row emitted by [examScheduleRow]: the same
 * normalisation the on-screen card uses, so the exported image cannot disagree
 * with the list.
 */
class ExamScheduleShareTest {

    @Test
    fun `row carries date, time, course, faculty and room`() {
        val row = examScheduleRow(
            ExamScheduleEntry(
                dayAndDate = "Sunday, Nov 22nd, 2026",
                time = "9:00 AM - 10:30 AM",
                courseTitle = "Artificial Intelligence",
                faculty = "DR. LUBN A AZIZ",
                location = "202",
                edpCode = "032610404"
            )
        )

        assertEquals(
            listOf(
                "Sun, 22 Nov 2026",
                "9:00 AM - 10:30 AM",
                "Artificial Intelligence",
                "Dr. Lubn A Aziz",
                "202"
            ),
            row
        )
    }

    @Test
    fun `compact time is normalised`() {
        val row = examScheduleRow(
            ExamScheduleEntry("Monday, Nov 23rd, 2026", "09:00-10:30", "AI Agents", "X", "609", "032610669")
        )

        assertEquals("Mon, 23 Nov 2026", row[0])
        assertEquals("9:00 AM - 10:30 AM", row[1])
    }

    @Test
    fun `row falls back for tba time and blank optional fields`() {
        val row = examScheduleRow(ExamScheduleEntry("TBA", "TBA", "", "", "", ""))

        assertEquals(listOf("TBA", "TBA", "Exam", "—", "—"), row)
    }
}
