package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PortalFormatTest {

    @Test
    fun `portal dates parse with ordinal suffixes`() {
        assertEquals(LocalDate.of(2026, 11, 30), parsePortalDate("Monday, 30th November, 2026"))
        assertEquals(LocalDate.of(2026, 12, 1), parsePortalDate("Tuesday, 1st December, 2026"))
        assertNull(parsePortalDate("30/11/2026"))
    }

    @Test
    fun `short date and fallback to the raw text`() {
        assertEquals("Mon, 30 Nov 2026", shortPortalDate("Monday, 30th November, 2026"))
        assertEquals("TBA", shortPortalDate("TBA"))
    }

    @Test
    fun `rupees are whole and grouped`() {
        assertEquals("Rs 1,000", formatRupees(parseRupees("1,000.00")))
        assertEquals("Rs 27,900", formatRupees(parseRupees("Rs 27900")))
        assertEquals(0.0, parseRupees("n/a"), 0.0)
    }

    @Test
    fun `generic portal subtitles give way to the fallback`() {
        assertEquals("Dates, times and rooms", distinctSubtitle("Exam Schedule", "Exam schedule", "Dates, times and rooms"))
        assertEquals("Your classes", distinctSubtitle("Semester Schedule", "Weekly class schedule", "Your classes"))
        assertEquals("Your classes", distinctSubtitle("  ", "Weekly class schedule", "Your classes"))
        assertEquals(
            "Final Exam Schedule Fall 2026",
            distinctSubtitle("Final Exam Schedule Fall 2026", "Exam schedule", "Dates, times and rooms")
        )
    }
}
