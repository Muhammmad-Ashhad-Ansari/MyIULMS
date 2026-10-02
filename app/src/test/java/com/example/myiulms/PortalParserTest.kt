package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalParserTest {

    @Test
    fun parseWeeklyScheduleExtractsClassDetailsAndCodes() {
        val html = """
            <div class="label-head-text">Fall 2026 Weekly Schedule</div>
            <table>
              <tr>
                <td class="dateStyle">
                  <span class="dayStyle">Monday</span>
                  <table><tr><td>ignored</td></tr><tr><td>09:00 AM - 10:30 AM</td></tr></table>
                </td>
                <td class="detailsStyle"><table>
                  <tr><td>Course Title : Mobile Application Development</td></tr>
                  <tr><td>Faculty : Ayesha Khan</td></tr>
                  <tr><td>Location : Lab 3</td></tr>
                  <tr><td>Course Code : CSC410 EDP Code : 12345</td></tr>
                </table></td>
              </tr>
            </table>
        """.trimIndent()

        val schedule = parseWeeklySchedule(html)

        assertEquals("Fall 2026 Weekly Schedule", schedule.title)
        assertEquals(1, schedule.entries.size)
        assertEquals(
            WeeklyScheduleEntry(
                day = "Monday",
                time = "09:00 AM - 10:30 AM",
                courseTitle = "Mobile Application Development",
                faculty = "Ayesha Khan",
                location = "Lab 3",
                edpCode = "12345",
                courseCode = "CSC410"
            ),
            schedule.entries.single()
        )
    }

    @Test
    fun parseAttendanceExtractsSummaryAndSessionDetails() {
        val html = """
            <table class="attendance-table">
              <tr class="attendanceRow">
                <td class="attendanceRowCourse">Mobile Application Development</td>
                <td class="attendanceRowStat">5</td>
                <td class="attendanceRowStat">4</td>
                <td class="attendanceRowStat">1</td>
                <td><a onclick="viewattendance(7)">Details</a></td>
              </tr>
            </table>
            <div id="myModal_7">
              <div id="schedule">Mon 09:00 AM</div>
              <div id="facultyName">Ayesha Khan</div>
              <table class="attendance-table">
                <tr><th>Lecture</th><th>Date</th><th>Status</th></tr>
                <tr><td>1</td><td>Sep 1</td><td>Present</td></tr>
                <tr class="attendance-table-summary">
                  <td>Total Sessions</td><td>8</td>
                  <td>Total Present</td><td>6</td>
                  <td>Total Absent</td><td>2</td>
                </tr>
              </table>
            </div>
        """.trimIndent()

        val course = parseAttendance(html).courses.single()

        assertEquals("Mobile Application Development", course.name)
        assertEquals(8, course.totalSessions)
        assertEquals(6, course.present)
        assertEquals(2, course.absent)
        assertEquals(75, course.attendancePercent)
        assertEquals("Mon 09:00 AM", course.schedule)
        assertEquals("Ayesha Khan", course.faculty)
        assertEquals(listOf("Date", "Status"), course.sessionHeaders)
        assertEquals(AttendanceSession("1", listOf("Sep 1", "Present")), course.sessions.single())
    }

    @Test
    fun parseVouchersExtractsPaymentAndPrintFields() {
        val html = """
            <table id="voucherTable">
              <tr><th>#</th><th>Number</th><th>Semester</th><th>Due date</th><th>Other</th><th>Description</th><th>Amount</th></tr>
              <tr>
                <td>1.</td><td>V-102</td><td>Fall 2026</td><td>Oct 10, 2026</td><td>-</td>
                <td>Tuition fee</td><td>45,000</td>
                <td><form action="PrintVoucher"><input name="VoucherNumber" value="PV-102"><input name="studentId" value="68669"></form></td>
              </tr>
            </table>
        """.trimIndent()

        val voucher = parseVouchers(html).single()

        assertEquals("V-102", voucher.number)
        assertEquals("Fall 2026", voucher.semester)
        assertEquals("Oct 10, 2026", voucher.dueDate)
        assertEquals("Tuition fee", voucher.description)
        assertEquals("45,000", voucher.amount)
        assertEquals("PV-102", voucher.printVoucherNumber)
        assertEquals("68669", voucher.studentId)
    }

    @Test
    fun parseTranscriptExtractsCgpaAndAttemptedCourses() {
        val transcript = parseTranscript(
            """{"cgpa":"3.45","attemptedCourses":[{"crsCode":"CSC410","crsTitle":"Mobile Application Development","crsHours":"3","crsGrade":"A","gpa":"4.0"}]}"""
        )

        assertEquals("3.45", transcript.cgpa)
        assertEquals(1, transcript.courses.size)
        assertEquals(Course("CSC410", "Mobile Application Development", "3", "A", "4.0"), transcript.courses.single())
    }

    @Test
    fun emptyVoucherPageReturnsNoItems() {
        assertTrue(parseVouchers("<html><body>No vouchers</body></html>").isEmpty())
    }
}
