package com.example.myiulms

import com.example.myiulms.ui.dashboard.CampusZone
import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.ScheduleRowState
import com.example.myiulms.ui.dashboard.classProgressFraction
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import com.example.myiulms.ui.dashboard.resolveScheduleFocus
import com.example.myiulms.ui.dashboard.scheduleRowState
import com.example.myiulms.ui.dashboard.todayAbbrev
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduleRowStateTest {

    private fun entry(
        day: String = "MON",
        time: String = "08:00 - 09:00",
        code: String = "CS101"
    ) = WeeklyScheduleEntry(
        day = day,
        time = time,
        courseTitle = "Course",
        faculty = "Faculty",
        location = "Room A",
        edpCode = "EDP",
        courseCode = code
    )

    private fun state(
        isToday: Boolean = true,
        key: String = "MON-CS101-08:00 - 09:00",
        time: String? = "08:00 - 09:00",
        nowMinute: Int = 8 * 60 + 30,
        liveKey: String? = null
    ) = scheduleRowState(
        isToday = isToday,
        rowKey = key,
        interval = time?.let(::parseScheduleInterval),
        nowMinuteOfDay = nowMinute,
        liveKey = liveKey
    )

    @Test
    fun `another day never carries a temporal state`() {
        assertEquals(
            ScheduleRowState.NotToday,
            state(isToday = false, nowMinute = 20 * 60, liveKey = "MON-CS101-08:00 - 09:00")
        )
    }

    @Test
    fun `an unreadable time is Unknown rather than a guess`() {
        assertEquals(ScheduleRowState.Unknown, state(time = "TBA"))
        assertEquals(ScheduleRowState.Unknown, state(time = null))
    }

    @Test
    fun `an ended class is past at its exact end minute`() {
        assertEquals(ScheduleRowState.Past, state(nowMinute = 9 * 60))
        assertEquals(ScheduleRowState.Past, state(nowMinute = 15 * 60))
    }

    @Test
    fun `a class is live at its exact start minute`() {
        val key = "MON-CS101-08:00 - 09:00"
        val focus = resolveScheduleFocus(listOf(entry()), "MON", 8 * 60)
        val liveKey = entryKey("MON", (focus as ScheduleFocus.Live).entry)
        assertEquals(key, liveKey)
        assertEquals(ScheduleRowState.Live, state(nowMinute = 8 * 60, liveKey = liveKey))
    }

    @Test
    fun `before the start minute the row is upcoming`() {
        assertEquals(ScheduleRowState.Upcoming, state(nowMinute = 7 * 60))
    }

    @Test
    fun `an overnight class is never past during its own day`() {
        assertEquals(
            ScheduleRowState.Upcoming,
            state(time = "22:00 - 02:00", nowMinute = 23 * 60)
        )
        assertEquals(
            ScheduleRowState.Upcoming,
            state(time = "22:00 - 02:00", nowMinute = 30)
        )
    }

    @Test
    fun `the row marked live is the entry the focus resolver reports`() {
        val entries = listOf(
            entry(time = "08:00 - 09:00", code = "A"),
            entry(time = "10:00 - 11:00", code = "B")
        )
        val nowMinute = 10 * 60 + 30
        val focus = resolveScheduleFocus(entries, "MON", nowMinute)
        val liveEntry = (focus as ScheduleFocus.Live).entry
        val liveKey = entryKey("MON", liveEntry)

        val liveRow = scheduleRowState(
            isToday = true,
            rowKey = entryKey("MON", entries[1]),
            interval = parseScheduleInterval(entries[1].time),
            nowMinuteOfDay = nowMinute,
            liveKey = liveKey
        )
        val otherRow = scheduleRowState(
            isToday = true,
            rowKey = entryKey("MON", entries[0]),
            interval = parseScheduleInterval(entries[0].time),
            nowMinuteOfDay = nowMinute,
            liveKey = liveKey
        )

        assertEquals(ScheduleRowState.Live, liveRow)
        assertEquals(ScheduleRowState.Past, otherRow)
    }

    @Test
    fun `at the end minute the resolver no longer calls the row live`() {
        val focus = resolveScheduleFocus(listOf(entry()), "MON", 9 * 60)
        assertEquals(ScheduleFocus.Done, focus)
        assertEquals(ScheduleRowState.Past, state(nowMinute = 9 * 60))
    }

    @Test
    fun `midnight moves today to the next day`() {
        val lateEvening = ZonedDateTime.of(2026, 11, 17, 23, 59, 0, 0, CampusZone)
        val midnight = ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, CampusZone)
        val lateMillis = lateEvening.toInstant().toEpochMilli()
        val midnightMillis = midnight.toInstant().toEpochMilli()

        val expectedLate = LocalDate.of(2026, 11, 17).dayOfWeek.name.take(3)
        val expectedNext = LocalDate.of(2026, 11, 18).dayOfWeek.name.take(3)

        val lastMinute = todayAbbrev(lateMillis)
        val firstMinute = todayAbbrev(midnightMillis)
        assertEquals(expectedLate, lastMinute)
        assertEquals(expectedNext, firstMinute)
        assertNotEquals(lastMinute, firstMinute)

        val interval = parseScheduleInterval("08:00 - 09:00")!!
        val rowKey = "MON-CS101-08:00 - 09:00"

        // The same finished row is past at 23:59 and has no temporal state one
        // minute later, because its day is no longer today.
        assertEquals(
            ScheduleRowState.Past,
            scheduleRowState(true, rowKey, interval, 23 * 60 + 59, liveKey = null)
        )
        assertEquals(
            ScheduleRowState.NotToday,
            scheduleRowState(lastMinute == firstMinute, rowKey, interval, 0, liveKey = null)
        )
    }

    @Test
    fun `progress runs from zero to one and clamps outside the class`() {
        val interval = parseScheduleInterval("08:00 - 09:00")!!
        assertEquals(0f, classProgressFraction(interval, 8 * 60), 0.0001f)
        assertEquals(0.5f, classProgressFraction(interval, 8 * 60 + 30), 0.0001f)
        assertEquals(1f, classProgressFraction(interval, 9 * 60), 0.0001f)
        assertEquals(0f, classProgressFraction(interval, 7 * 60), 0.0001f)
        assertEquals(1f, classProgressFraction(interval, 10 * 60), 0.0001f)
    }
}
