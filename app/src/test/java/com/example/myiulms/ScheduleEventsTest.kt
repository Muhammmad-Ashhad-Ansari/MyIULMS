package com.example.myiulms

import com.example.myiulms.alarms.ScheduleEventType
import com.example.myiulms.alarms.classOccurrences
import com.example.myiulms.alarms.dueNotifications
import com.example.myiulms.alarms.formatGap
import com.example.myiulms.alarms.nextClassMessage
import com.example.myiulms.alarms.nextScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEventsTest {

    private val karachi = ZoneId.of("Asia/Karachi")

    // Monday 2 Nov 2026, campus time.
    private fun monday(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, 2, hour, minute, 0, 0, karachi).toInstant().toEpochMilli()

    private fun entry(day: String, time: String, title: String, room: String = "E-806") =
        WeeklyScheduleEntry(day, time, title, "Dr. X", room, "1", "CS-1")

    private val week = listOf(
        entry("MON", "11:35 AM/12:50 PM", "DATABASE SYSTEMS", "E-610"),
        entry("MON", "2:00 PM/3:20 PM", "AI AGENTS"),
        entry("WED", "9:00 AM/10:20 AM", "ANN", "E-803")
    )

    private fun next(at: Long, reminders: Boolean = true, widget: Boolean = true) =
        nextScheduleEvent(week, at, widgetEvents = widget, reminders = reminders)

    // ---- the three-stage hop ----

    @Test
    fun `alarm hops reminder, start, end for each class`() {
        val reminder = next(monday(11, 0))!!
        assertEquals(monday(11, 25) to ScheduleEventType.Reminder, reminder.atMillis to reminder.type)
        val start = next(reminder.atMillis)!!
        assertEquals(monday(11, 35) to ScheduleEventType.ClassStart, start.atMillis to start.type)
        val end = next(start.atMillis)!!
        assertEquals(monday(12, 50) to ScheduleEventType.ClassEnd, end.atMillis to end.type)
        val nextReminder = next(end.atMillis)!!
        assertEquals(monday(13, 50) to ScheduleEventType.Reminder, nextReminder.atMillis to nextReminder.type)
    }

    @Test
    fun `without reminders the widget only needs start and end`() {
        val event = next(monday(11, 0), reminders = false)!!
        assertEquals(monday(11, 35) to ScheduleEventType.ClassStart, event.atMillis to event.type)
    }

    @Test
    fun `after the last class the widget waits for midnight`() {
        val event = next(monday(18, 0))!!
        assertEquals(monday(0, 0) + 86_400_000L to ScheduleEventType.DayChange, event.atMillis to event.type)
    }

    @Test
    fun `reminders alone skip midnight and reach the next school day`() {
        val event = next(monday(18, 0), widget = false)!!
        val wednesday = ZonedDateTime.of(2026, 11, 4, 8, 50, 0, 0, karachi).toInstant().toEpochMilli()
        assertEquals(wednesday to ScheduleEventType.Reminder, event.atMillis to event.type)
    }

    @Test
    fun `nothing to wake for when both are off`() {
        assertNull(next(monday(11, 0), reminders = false, widget = false))
    }

    // ---- which notifications a firing posts ----

    @Test
    fun `each firing posts exactly its own stage once`() {
        val reminder = dueNotifications(week, monday(11, 25), handledUntilMillis = monday(11, 0))
        assertEquals(listOf(ScheduleEventType.Reminder), reminder.map { it.type })
        assertTrue(dueNotifications(week, monday(11, 26), handledUntilMillis = monday(11, 25)).isEmpty())
        val start = dueNotifications(week, monday(11, 35), handledUntilMillis = monday(11, 25))
        assertEquals(listOf(ScheduleEventType.ClassStart), start.map { it.type })
    }

    @Test
    fun `a slightly late alarm still posts, a long-missed one does not`() {
        assertEquals(1, dueNotifications(week, monday(12, 55), handledUntilMillis = monday(12, 0)).size)
        assertTrue(dueNotifications(week, monday(13, 30), handledUntilMillis = monday(12, 0)).isEmpty())
    }

    @Test
    fun `a reminder is never posted once its class has started`() {
        val due = dueNotifications(week, monday(11, 40), handledUntilMillis = monday(11, 0))
        assertEquals(listOf(ScheduleEventType.ClassStart), due.map { it.type })
    }

    // ---- end-of-class text ----

    private fun monday(index: Int) = classOccurrences(week, monday(0, 0)).filter {
        it.startMillis in monday(0, 0)..monday(23, 59)
    }[index]

    @Test
    fun `class end names the next class with the gap, time and room`() {
        assertEquals(
            "Next: AI AGENTS in 1 hr 10 mins at 2:00 PM · Room E-806",
            nextClassMessage(week, monday(0))
        )
    }

    @Test
    fun `the last class of the day says the day is over`() {
        assertEquals("All classes for today are over.", nextClassMessage(week, monday(1)))
    }

    @Test
    fun `gap wording`() {
        assertEquals("10 mins", formatGap(10 * 60_000L))
        assertEquals("1 min", formatGap(60_000L))
        assertEquals("1 hr", formatGap(60 * 60_000L))
        assertEquals("2 hrs 5 mins", formatGap(125 * 60_000L))
    }
}
