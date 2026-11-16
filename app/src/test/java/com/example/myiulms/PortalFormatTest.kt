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
    fun `exam schedule dates parse with abbreviated months`() {
        assertEquals(LocalDate.of(2026, 11, 22), parsePortalDate("Sunday, Nov 22nd, 2026"))
        assertEquals("Sun, 22 Nov 2026", shortPortalDate("Sunday, Nov 22nd, 2026"))
        assertNull(parsePortalDate("Sunday, Nov 22nd"))
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

    @Test
    fun `exam time ranges share one meridiem when they can`() {
        assertEquals("9:00 – 10:30 AM", formatExamTimeRange(9 * 60, 10 * 60 + 30))
        assertEquals("12:00 – 1:00 PM", formatExamTimeRange(12 * 60, 13 * 60))
        assertEquals("11:30 AM – 1:00 PM", formatExamTimeRange(11 * 60 + 30, 13 * 60))
    }

    @Test
    fun `exam durations read as hours and minutes`() {
        assertEquals("1h 30m", formatExamDuration(90))
        assertEquals("2h", formatExamDuration(120))
        assertEquals("45m", formatExamDuration(45))
        assertEquals("", formatExamDuration(0))
    }

    @Test
    fun `countdown covers days left, today, tomorrow and passed`() {
        assertEquals("6 days left", examCountdown(6))
        assertEquals("Tomorrow", examCountdown(1))
        assertEquals("Today", examCountdown(0))
        assertEquals("Passed", examCountdown(-1))
    }

    @Test
    fun `next exam skips past dates and finished same-day exams`() {
        val entries = listOf(
            examEntry("Sunday, Nov 15th, 2026", "9:00 AM - 10:30 AM", "Past"),
            examEntry("Sunday, Nov 22nd, 2026", "9:00 AM - 10:30 AM", "First on exam day"),
            examEntry("Sunday, Nov 22nd, 2026", "11:30 AM - 1:00 PM", "Second on exam day"),
            examEntry("Sunday, Nov 29th, 2026", "9:00 AM - 10:30 AM", "Last")
        )

        assertEquals(1, nextExamIndex(entries, LocalDate.of(2026, 11, 16)))
        // On exam day, once the morning exam has ended, the afternoon one is next.
        assertEquals(
            2,
            nextExamIndex(entries, LocalDate.of(2026, 11, 22), nowMinuteOfDay = 10 * 60 + 45)
        )
        assertNull(nextExamIndex(entries, LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `exam phase follows the clock on exam day`() {
        val entry = examEntry("Sunday, Nov 22nd, 2026", "9:00 AM - 10:30 AM", "AI")
        val examDay = LocalDate.of(2026, 11, 22)

        assertEquals(ExamPhase.Upcoming, examPhase(entry, examDay, nowMinuteOfDay = 8 * 60))
        assertEquals(ExamPhase.InProgress, examPhase(entry, examDay, nowMinuteOfDay = 9 * 60 + 30))
        assertEquals(ExamPhase.Passed, examPhase(entry, examDay, nowMinuteOfDay = 10 * 60 + 30))
        assertEquals(ExamPhase.Passed, examPhase(entry, LocalDate.of(2026, 11, 23)))
        assertEquals(ExamPhase.Upcoming, examPhase(entry, LocalDate.of(2026, 11, 16)))
        // An unusable date is never hidden behind a "Passed" label.
        assertEquals(ExamPhase.Upcoming, examPhase(examEntry("TBA", "TBA", "X"), examDay))
    }

    @Test
    fun `passed exams sink below upcoming ones`() {
        val entries = listOf(
            examEntry("Tuesday, Nov 10th, 2026", "9:00 AM - 10:30 AM", "Passed older"),
            examEntry("Sunday, Nov 29th, 2026", "9:00 AM - 10:30 AM", "Later"),
            examEntry("Sunday, Nov 15th, 2026", "9:00 AM - 10:30 AM", "Passed recent"),
            examEntry("Sunday, Nov 22nd, 2026", "9:00 AM - 10:30 AM", "Next")
        )

        val ordered = orderExamsUpcomingFirst(entries, LocalDate.of(2026, 11, 16))
        assertEquals(
            listOf("Next", "Later", "Passed recent", "Passed older"),
            ordered.map { it.courseTitle }
        )
    }

    private fun examEntry(day: String, time: String, title: String) = ExamScheduleEntry(
        dayAndDate = day,
        time = time,
        courseTitle = title,
        faculty = "",
        location = "",
        edpCode = ""
    )
}
