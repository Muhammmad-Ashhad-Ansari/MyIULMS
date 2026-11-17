package com.example.myiulms

import com.example.myiulms.domain.attendance.AttendanceLevel
import com.example.myiulms.domain.attendance.assessAttendance
import com.example.myiulms.domain.attendance.attendanceIsBarred
import com.example.myiulms.domain.attendance.attendanceRiskLabel
import com.example.myiulms.domain.attendance.attendanceUsageLabel
import com.example.myiulms.domain.attendance.edpCodeOf
import com.example.myiulms.domain.attendance.sessionsPerMeeting
import com.example.myiulms.domain.attendance.weeklyMeetingsFromSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceRiskTest {

    private val twoSessionHeaders = listOf("Session 1", "Session 2")

    private fun course(
        name: String = "AI AGENTS (032610669)",
        schedule: String = "MON 1:00 PM 2:20 PM E-806, WED 1:00 PM 2:20 PM E-806",
        total: Int,
        absent: Int,
        headers: List<String> = twoSessionHeaders
    ) = AttendanceCourse(name, total, total - absent, absent, schedule, "", headers, emptyList())

    // ---- inputs, shapes taken from the live portal ----

    @Test
    fun `edp code is the trailing number in the course name`() {
        assertEquals("032610669", edpCodeOf("AI AGENTS (032610669)"))
        assertEquals("032610080", edpCodeOf("ARTIFICIAL INTELLIGENCE (LAB) (032610080)"))
        assertNull(edpCodeOf("Mobile Application Development"))
    }

    @Test
    fun `weekly meetings come from the record's own schedule`() {
        assertEquals(2, weeklyMeetingsFromSchedule("MON 1:00 PM 2:20 PM E-806, WED 1:00 PM 2:20 PM E-806"))
        assertEquals(1, weeklyMeetingsFromSchedule("SAT 8:30 AM 11:20 AM E-604"))
        assertEquals(0, weeklyMeetingsFromSchedule(""))
    }

    @Test
    fun `sessions per meeting are read from the session columns`() {
        assertEquals(2, sessionsPerMeeting(twoSessionHeaders))
        assertEquals(3, sessionsPerMeeting(listOf("Session 1", "Session 2", "Session 3")))
        assertEquals(2, sessionsPerMeeting(listOf("Date", "Status"))) // fallback
    }

    // ---- the F limit and the last safe absence ----

    private fun theory(absent: Int, total: Int = 64) = course(total = total, absent = absent)

    private fun lab(absent: Int, total: Int = 32) =
        course(name = "COMPUTER ARCHITECTURE (LAB) (032610612)", schedule = "SAT 8:30 AM 11:20 AM E-604", total = total, absent = absent)

    @Test
    fun `twice-weekly theory reaches F at 16 absences`() {
        val risk = assessAttendance(theory(absent = 0))
        assertEquals(64, risk.plannedSessions)
        assertEquals(16, risk.fLimit)
        assertEquals(15, risk.maxSafe)
        assertEquals(15, risk.safeLeft)
        assertEquals(AttendanceLevel.Safe, risk.level)
        assertFalse(attendanceIsBarred(risk))
    }

    @Test
    fun `once-weekly lab reaches F at 8 absences`() {
        val risk = assessAttendance(lab(absent = 0))
        assertEquals(32, risk.plannedSessions)
        assertEquals(8, risk.fLimit)
        assertEquals(7, risk.maxSafe)
        assertEquals(7, risk.safeLeft)
    }

    @Test
    fun `timetable fills in when the record has no schedule`() {
        val noSchedule = course(schedule = "", total = 4, absent = 0)
        val timetable = listOf(
            WeeklyScheduleEntry("MON", "1:00 PM/2:20 PM", "AI AGENTS", "", "E-806", "032610669", "AIE331"),
            WeeklyScheduleEntry("WED", "1:00 PM/2:20 PM", "AI AGENTS", "", "E-806", "032610669", "AIE331"),
            WeeklyScheduleEntry("SAT", "8:30 AM/11:20 AM", "AI LAB", "", "E-604", "032610080", "CMC381")
        )
        val risk = assessAttendance(noSchedule, timetable)
        assertEquals(64, risk.plannedSessions)
        assertEquals(16, risk.fLimit)
        assertEquals(AttendanceLevel.Unknown, assessAttendance(noSchedule, null).level)
    }

    // ---- four tiers on a 16-absence limit ----

    @Test
    fun `theory is safe down to 5 absences left and amber for 4 down to 2`() {
        val fiveLeft = assessAttendance(theory(absent = 10))
        assertEquals(AttendanceLevel.Safe, fiveLeft.level)
        assertEquals("5 absences left", attendanceRiskLabel(fiveLeft))
        assertEquals("10 / 16", attendanceUsageLabel(fiveLeft, 10))

        val fourLeft = assessAttendance(theory(absent = 11))
        assertEquals(AttendanceLevel.Caution, fourLeft.level)
        assertEquals("4 absences left", attendanceRiskLabel(fourLeft))

        val twoLeft = assessAttendance(theory(absent = 13))
        assertEquals(AttendanceLevel.Caution, twoLeft.level)
        assertEquals("2 absences left", attendanceRiskLabel(twoLeft))
    }

    @Test
    fun `theory is red from 1 absence left through the limit`() {
        val oneLeft = assessAttendance(theory(absent = 14))
        assertEquals(AttendanceLevel.Danger, oneLeft.level)
        assertEquals("1 absence left", attendanceRiskLabel(oneLeft))
        assertFalse(attendanceIsBarred(oneLeft))
        assertEquals("14 / 16", attendanceUsageLabel(oneLeft, 14))

        val lastSafe = assessAttendance(theory(absent = 15))
        assertEquals(AttendanceLevel.Danger, lastSafe.level)
        assertEquals("0 left · Next = F", attendanceRiskLabel(lastSafe))
        assertFalse(attendanceIsBarred(lastSafe))
        assertEquals("15 / 16", attendanceUsageLabel(lastSafe, 15))
    }

    @Test
    fun `theory is barred at and beyond 16 absences`() {
        val atLimit = assessAttendance(theory(absent = 16))
        assertEquals(AttendanceLevel.Danger, atLimit.level)
        assertEquals("F · Not eligible", attendanceRiskLabel(atLimit))
        assertTrue(attendanceIsBarred(atLimit))
        assertEquals("16 / 16", attendanceUsageLabel(atLimit, 16))

        val over = assessAttendance(theory(absent = 17))
        assertEquals(AttendanceLevel.Danger, over.level)
        assertEquals("F · Not eligible", attendanceRiskLabel(over))
        assertTrue(attendanceIsBarred(over))
        assertEquals("17 / 16", attendanceUsageLabel(over, 17))
    }

    // ---- four tiers on an 8-absence limit ----

    @Test
    fun `lab is safe down to 5 absences left and amber for 4 down to 2`() {
        val fiveLeft = assessAttendance(lab(absent = 2))
        assertEquals(AttendanceLevel.Safe, fiveLeft.level)
        assertEquals("5 absences left", attendanceRiskLabel(fiveLeft))
        assertEquals("2 / 8", attendanceUsageLabel(fiveLeft, 2))

        val fourLeft = assessAttendance(lab(absent = 3))
        assertEquals(AttendanceLevel.Caution, fourLeft.level)
        assertEquals("4 absences left", attendanceRiskLabel(fourLeft))

        val twoLeft = assessAttendance(lab(absent = 5))
        assertEquals(AttendanceLevel.Caution, twoLeft.level)
        assertEquals("2 absences left", attendanceRiskLabel(twoLeft))
    }

    @Test
    fun `lab is red from 1 absence left through the limit`() {
        val oneLeft = assessAttendance(lab(absent = 6))
        assertEquals(AttendanceLevel.Danger, oneLeft.level)
        assertEquals("1 absence left", attendanceRiskLabel(oneLeft))
        assertFalse(attendanceIsBarred(oneLeft))
        assertEquals("6 / 8", attendanceUsageLabel(oneLeft, 6))

        val lastSafe = assessAttendance(lab(absent = 7))
        assertEquals(AttendanceLevel.Danger, lastSafe.level)
        assertEquals("0 left · Next = F", attendanceRiskLabel(lastSafe))
        assertFalse(attendanceIsBarred(lastSafe))
        assertEquals("7 / 8", attendanceUsageLabel(lastSafe, 7))
    }

    @Test
    fun `lab is barred at and beyond 8 absences`() {
        val atLimit = assessAttendance(lab(absent = 8))
        assertEquals(AttendanceLevel.Danger, atLimit.level)
        assertEquals("F · Not eligible", attendanceRiskLabel(atLimit))
        assertTrue(attendanceIsBarred(atLimit))
        assertEquals("8 / 8", attendanceUsageLabel(atLimit, 8))

        val over = assessAttendance(lab(absent = 9))
        assertEquals("F · Not eligible", attendanceRiskLabel(over))
        assertTrue(attendanceIsBarred(over))
        assertEquals("9 / 8", attendanceUsageLabel(over, 9))
    }

    // ---- buffer and edge cases ----

    @Test
    fun `an early absence stays safe even though the percentage looks low`() {
        // 2 sessions held, both absent: 0% so far, but only 2 of the 16 limit used.
        val risk = assessAttendance(course(total = 2, absent = 2))
        assertEquals(AttendanceLevel.Safe, risk.level)
        assertEquals(13, risk.safeLeft)
    }

    @Test
    fun `danger when 75 percent can no longer be reached`() {
        // Late in the semester with few sessions left: best possible drops under 75%.
        val risk = assessAttendance(lab(total = 32, absent = 9))
        assertEquals(AttendanceLevel.Danger, risk.level)
        assertEquals(71, risk.bestPossiblePercent)
    }

    @Test
    fun `make-up sessions beyond the plan raise the plan`() {
        val risk = assessAttendance(course(total = 70, absent = 0))
        assertEquals(70, risk.plannedSessions)
        assertEquals(17, risk.fLimit)
        assertEquals(100, risk.bestPossiblePercent)
    }
}
