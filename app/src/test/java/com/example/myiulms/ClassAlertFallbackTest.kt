package com.example.myiulms

import com.example.myiulms.alarms.ClassAlertBlocker
import com.example.myiulms.alarms.ClassAlertGuidance
import com.example.myiulms.alarms.ScheduleEventType
import com.example.myiulms.alarms.dueNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClassAlertFallbackTest {

    private fun blocker(
        reminders: Boolean = true,
        notifications: Boolean = true,
        exact: Boolean = true,
        battery: Boolean = true
    ) = ClassAlertGuidance.blocker(reminders, notifications, exact, battery)

    // ---- which system tip the Schedule tab offers ----

    @Test
    fun `nothing to offer while reminders are off`() {
        assertNull(blocker(reminders = false, notifications = false, exact = false, battery = false))
    }

    @Test
    fun `nothing to offer when every prerequisite is met`() {
        assertNull(blocker())
    }

    @Test
    fun `notifications come before exact alarms and battery`() {
        assertEquals(
            ClassAlertBlocker.Notifications,
            blocker(notifications = false, exact = false, battery = false)
        )
        assertEquals(ClassAlertBlocker.Notifications, blocker(notifications = false))
    }

    @Test
    fun `exact alarms come before the battery exemption`() {
        assertEquals(ClassAlertBlocker.ExactAlarms, blocker(exact = false, battery = false))
    }

    @Test
    fun `the battery exemption is the last thing offered`() {
        assertEquals(ClassAlertBlocker.BatteryOptimizations, blocker(battery = false))
    }

    // ---- when the WorkManager safety net is kept and allowed to run ----

    @Test
    fun `the fallback is kept only for a signed-in student with reminders on`() {
        assertTrue(ClassAlertGuidance.shouldKeepFallback(remindersEnabled = true, signedIn = true))
        assertFalse(ClassAlertGuidance.shouldKeepFallback(remindersEnabled = false, signedIn = true))
        assertFalse(ClassAlertGuidance.shouldKeepFallback(remindersEnabled = true, signedIn = false))
        assertFalse(ClassAlertGuidance.shouldKeepFallback(remindersEnabled = false, signedIn = false))
    }

    @Test
    fun `a fallback pass needs a timetable cached on disk`() {
        // "Remember me" off leaves no cache, so a pass would post and arm nothing.
        assertFalse(ClassAlertGuidance.canRunPass(remindersEnabled = true, signedIn = true, scheduleCached = false))
        assertFalse(ClassAlertGuidance.canRunPass(remindersEnabled = false, signedIn = true, scheduleCached = true))
        assertFalse(ClassAlertGuidance.canRunPass(remindersEnabled = true, signedIn = false, scheduleCached = true))
        assertTrue(ClassAlertGuidance.canRunPass(remindersEnabled = true, signedIn = true, scheduleCached = true))
    }

    // ---- what a fallback pass posts after the alarm chain was killed ----

    private val karachi = ZoneId.of("Asia/Karachi")

    // Monday 2 Nov 2026, campus time.
    private fun monday(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, 2, hour, minute, 0, 0, karachi).toInstant().toEpochMilli()

    private fun entry(day: String, time: String, title: String) =
        WeeklyScheduleEntry(day, time, title, "Dr. X", "E-806", "1", "CS-1")

    private val week = listOf(
        entry("MON", "11:35 AM/12:50 PM", "DATABASE SYSTEMS"),
        entry("MON", "2:00 PM/3:20 PM", "AI AGENTS")
    )

    @Test
    fun `the fallback posts the reminder the killed alarm never delivered`() {
        // Reminder was due at 11:25; no pass ran until the worker woke at 11:30.
        val due = dueNotifications(week, monday(11, 30), handledUntilMillis = monday(11, 0))
        assertEquals(listOf(ScheduleEventType.Reminder), due.map { it.type })
    }

    @Test
    fun `the fallback still posts a class start the killed chain missed`() {
        // The reminder was handled at 11:25; the 11:35 start never fired.
        val due = dueNotifications(week, monday(11, 40), handledUntilMillis = monday(11, 25))
        assertEquals(listOf(ScheduleEventType.ClassStart), due.map { it.type })
    }

    @Test
    fun `the fallback never repeats what was already posted`() {
        assertTrue(dueNotifications(week, monday(11, 45), handledUntilMillis = monday(11, 40)).isEmpty())
    }

    @Test
    fun `a long silence never announces an old class`() {
        // Chain dead through the whole class; the worker wakes two hours later.
        assertTrue(dueNotifications(week, monday(13, 40), handledUntilMillis = monday(11, 0)).isEmpty())
    }

    @Test
    fun `with no cached timetable nothing is posted`() {
        // Signed in without "Remember me": the non-persistent session has no
        // entries, and the pass must come back empty rather than fail.
        assertTrue(dueNotifications(emptyList(), monday(11, 30), handledUntilMillis = monday(0, 0)).isEmpty())
    }
}
