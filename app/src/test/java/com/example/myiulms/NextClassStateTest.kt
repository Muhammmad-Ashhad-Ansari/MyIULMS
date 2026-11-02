package com.example.myiulms

import com.example.myiulms.ui.widget.NextClassUiState
import com.example.myiulms.ui.widget.TimelineClass
import com.example.myiulms.ui.widget.UpcomingDay
import com.example.myiulms.ui.widget.widgetStateFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextClassStateTest {

    private fun entry(day: String, time: String, title: String, room: String = "E-806") =
        WeeklyScheduleEntry(day, time, title, "Dr. X", room, "1", "CS-1")

    // Monday 2 Nov 2026 in Karachi, the campus zone the widget resolves in.
    private fun monday(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, 2, hour, minute, 0, 0, ZoneId.of("Asia/Karachi")).toInstant().toEpochMilli()

    private val week = listOf(
        entry("MON", "2:30 PM/3:50 PM", "ANN", "E-803"),
        entry("MON", "1:00 PM/2:20 PM", "AI Agents"),
        entry("MON", "4:00 PM/5:20 PM", "Compilers", " Room E-101 "),
        entry("WED", "9:00 AM/10:20 AM", "Databases", "E-610"),
        entry("WED", "TBA", "Seminar")
    )

    private val ai = TimelineClass("AI Agents", "E-806", "1:00 PM", "2:20 PM")
    private val ann = TimelineClass("ANN", "E-803", "2:30 PM", "3:50 PM")
    private val compilers = TimelineClass("Compilers", "E-101", "4:00 PM", "5:20 PM")
    private val wednesday = UpcomingDay(
        "Wednesday", "Wed", listOf(TimelineClass("Databases", "E-610", "9:00 AM", "10:20 AM"))
    )

    @Test
    fun `nothing cached asks the student to open the app`() {
        assertEquals(NextClassUiState.NoSchedule(timesUnreadable = false), widgetStateFor(null, monday(9, 0)))
    }

    @Test
    fun `before classes the hero is next and the rest of today follows in order`() {
        assertEquals(
            NextClassUiState.Today(ai, live = false, later = listOf(ann, compilers)),
            widgetStateFor(week, monday(12, 30))
        )
    }

    @Test
    fun `a live class leads and only later classes are listed`() {
        assertEquals(
            NextClassUiState.Today(ann, live = true, later = listOf(compilers)),
            widgetStateFor(week, monday(15, 0))
        )
    }

    @Test
    fun `after the last class previews the next day with classes, skipping unreadable times`() {
        assertEquals(
            NextClassUiState.NoClassesLeft(finishedToday = true, nextDay = wednesday),
            widgetStateFor(week, monday(18, 0))
        )
    }

    @Test
    fun `an off day previews tomorrow by name`() {
        val tuesdayOnly = listOf(entry("TUE", "9:00 AM/10:20 AM", "Databases", "E-610"))
        val state = widgetStateFor(tuesdayOnly, monday(9, 0)) as NextClassUiState.NoClassesLeft

        assertEquals(false, state.finishedToday)
        assertEquals("Tomorrow", state.nextDay?.name)
        assertEquals("Tomorrow", state.nextDay?.shortName)
    }

    @Test
    fun `a week with only today's classes previews next week's same day`() {
        val mondayOnly = listOf(entry("MON", "9:00 AM/10:20 AM", "Databases"))
        val state = widgetStateFor(mondayOnly, monday(18, 0)) as NextClassUiState.NoClassesLeft

        assertEquals("Next Monday", state.nextDay?.name)
    }

    @Test
    fun `no readable class anywhere leaves the preview empty`() {
        val state = widgetStateFor(listOf(entry("WED", "TBA", "Seminar")), monday(9, 0)) as NextClassUiState.NoClassesLeft

        assertNull(state.nextDay)
    }
}
