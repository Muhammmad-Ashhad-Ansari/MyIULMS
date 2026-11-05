package com.example.myiulms

import com.example.myiulms.domain.attendance.AttendanceLevel
import com.example.myiulms.domain.attendance.assessAttendance
import com.example.myiulms.domain.attendance.attendanceRiskLabel
import com.example.myiulms.domain.attendance.edpCodeOf
import com.example.myiulms.domain.attendance.sessionsPerMeeting
import com.example.myiulms.domain.attendance.weeklyMeetingsFromSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---- planned sessions and the 25% allowance (75% minimum) ----

    private fun lab(total: Int, absent: Int) =
        course(name = "COMPUTER ARCHITECTURE (LAB) (032610612)", schedule = "SAT 8:30 AM 11:20 AM E-604", total = total, absent = absent)

    @Test
    fun `twice-weekly theory plans 64 sessions and allows 16 absences`() {
        val risk = assessAttendance(course(total = 18, absent = 0))
        assertEquals(64, risk.plannedSessions)
        assertEquals(16, risk.allowedAbsences)
        assertEquals(16, risk.absencesLeft)
        assertEquals(AttendanceLevel.Safe, risk.level)
    }

    @Test
    fun `once-weekly lab plans 32 sessions and allows 8 absences`() {
        val risk = assessAttendance(lab(total = 6, absent = 0))
        assertEquals(32, risk.plannedSessions)
        assertEquals(8, risk.allowedAbsences)
    }

    @Test
    fun `timetable fills in when the record has no schedule`() {
        val noSchedule = course(schedule = "", total = 4, absent = 0)
        val timetable = listOf(
            WeeklyScheduleEntry("MON", "1:00 PM/2:20 PM", "AI AGENTS", "", "E-806", "032610669", "AIE331"),
            WeeklyScheduleEntry("WED", "1:00 PM/2:20 PM", "AI AGENTS", "", "E-806", "032610669", "AIE331"),
            WeeklyScheduleEntry("SAT", "8:30 AM/11:20 AM", "AI LAB", "", "E-604", "032610080", "CMC381")
        )
        assertEquals(64, assessAttendance(noSchedule, timetable).plannedSessions)
        assertEquals(AttendanceLevel.Unknown, assessAttendance(noSchedule, null).level)
    }

    // ---- buffer levels ----

    @Test
    fun `an early absence stays safe even though the percentage looks low`() {
        // 2 sessions held, both absent: 0% so far, but only 2 of 16 used.
        assertEquals(AttendanceLevel.Safe, assessAttendance(course(total = 2, absent = 2)).level)
    }

    @Test
    fun `theory stays out of danger up to 15 absences`() {
        assertEquals(AttendanceLevel.Safe, assessAttendance(course(total = 40, absent = 8)).level)      // half used
        assertEquals(AttendanceLevel.Caution, assessAttendance(course(total = 40, absent = 9)).level)   // over half
        val fifteen = assessAttendance(course(total = 40, absent = 15))
        assertEquals(AttendanceLevel.Caution, fifteen.level)
        assertEquals("Last absence left", attendanceRiskLabel(fifteen))
        val sixteen = assessAttendance(course(total = 40, absent = 16))
        assertEquals(AttendanceLevel.Danger, sixteen.level)
        assertEquals("No absences left", attendanceRiskLabel(sixteen))
    }

    @Test
    fun `lab stays out of danger up to 7 absences`() {
        assertEquals(AttendanceLevel.Safe, assessAttendance(lab(total = 16, absent = 4)).level)
        assertEquals(AttendanceLevel.Caution, assessAttendance(lab(total = 16, absent = 5)).level)
        assertEquals(AttendanceLevel.Caution, assessAttendance(lab(total = 16, absent = 7)).level)
        assertEquals(AttendanceLevel.Danger, assessAttendance(lab(total = 16, absent = 8)).level)
        assertEquals("Over the absence limit", attendanceRiskLabel(assessAttendance(lab(total = 16, absent = 9))))
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
        assertEquals(100, risk.bestPossiblePercent)
    }
}
