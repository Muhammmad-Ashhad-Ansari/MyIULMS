package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExamScheduleTest {

    @Test
    fun parseExamScheduleExtractsAllFieldsCorrectly() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
                <div class="label-head"><p align="center"><span class="label-head-text" style="font-weight: bold;">Tentative Schedule for  Final Exam</span></p></div>
                <p style="font-weight: bold; color: red;">This is a Tentative Examination Schedule and is subject to change.</p>
                <table width="100%">
                    <tbody>
                        <tr>
                            <td class="dateStyle">
                                <table width="100%">
                                    <tbody>
                                        <tr align="center">
                                            <td style="text-align:center"><span class="dayStyle">Tuesday<br>Sep 15th, 2026</span></td>
                                        </tr>
                                        <tr>
                                            <td style="text-align:center">12:00 - 14:30</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </td>
                            <td class="detailsStyle">
                                <table>
                                    <tbody>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Course Title : </span>HCI &amp; COMPUTER GRAPHICS</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Faculty : </span>DR. RIZWAN MUNIR</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Location : </span>503</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">EDP Code : </span>032607189</td></tr>
                                    </tbody>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td class="dateStyle">
                                <table width="100%">
                                    <tbody>
                                        <tr align="center">
                                            <td style="text-align:center"><span class="dayStyle">Thursday<br>Sep 17th, 2026</span></td>
                                        </tr>
                                        <tr>
                                            <td style="text-align:center">12:00 - 14:30</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </td>
                            <td class="detailsStyle">
                                <table>
                                    <tbody>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Course Title : </span>ADVANCE DATABASE MANAGEMENT SYSTEMS</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Faculty : </span>SHAHZAD AHMED</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">Location : </span>704</td></tr>
                                        <tr><td style="padding: 2px;"><span class="titleStyle">EDP Code : </span>032607036</td></tr>
                                    </tbody>
                                </table>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val schedule = parseExamSchedule(html)

        assertEquals("Tentative Schedule for Final Exam", schedule.title)
        assertNotNull(schedule.notice)
        assertEquals("This is a Tentative Examination Schedule and is subject to change.", schedule.notice)
        assertEquals(2, schedule.entries.size)

        val first = schedule.entries[0]
        assertEquals("Tuesday, Sep 15th, 2026", first.dayAndDate)
        assertEquals("12:00 - 14:30", first.time)
        assertEquals("HCI & COMPUTER GRAPHICS", first.courseTitle)
        assertEquals("DR. RIZWAN MUNIR", first.faculty)
        assertEquals("503", first.location)
        assertEquals("032607189", first.edpCode)

        val second = schedule.entries[1]
        assertEquals("Thursday, Sep 17th, 2026", second.dayAndDate)
        assertEquals("12:00 - 14:30", second.time)
        assertEquals("ADVANCE DATABASE MANAGEMENT SYSTEMS", second.courseTitle)
        assertEquals("SHAHZAD AHMED", second.faculty)
        assertEquals("704", second.location)
        assertEquals("032607036", second.edpCode)
    }

    @Test
    fun parseExamScheduleHandlesEmptyHtmlGracefully() {
        val schedule = parseExamSchedule("<html><body></body></html>")
        assertEquals("Exam Schedule", schedule.title)
        assertEquals(null, schedule.notice)
        assertEquals(0, schedule.entries.size)
    }
}
