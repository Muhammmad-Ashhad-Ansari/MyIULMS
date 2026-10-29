package com.example.myiulms

// WeeklyScheduleEntry is in the same package (com.example.myiulms), so no import is needed.
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies the 5-column schedule share row emitted by [scheduleRow]
 * (Day, Time, Course, Faculty, Room) after the canvas layout change.
 */
class ScheduleShareTest {

    @Test
    fun `schedule row emits exactly five columns with correct formatting`() {
        val entry = WeeklyScheduleEntry(
            day = "MON",
            time = "1:00 PM - 2:20 PM",
            courseTitle = "Mobile Application Development",
            faculty = "dr. muhammad ahmed",
            location = "Lab 3",
            edpCode = "",
            courseCode = "CS-402"
        )

        val row = scheduleRow("MON", entry)

        assertEquals(5, row.size)
        assertEquals("MON", row[0])
        assertEquals("1:00 PM - 2:20 PM", row[1])
        assertEquals("Mobile Application Development", row[2])
        assertEquals("Dr. Muhammad Ahmed", row[3])
        assertEquals("Lab 3", row[4])
    }

    @Test
    fun `schedule row contains pure course title without code`() {
        val entry = WeeklyScheduleEntry(
            day = "TUE",
            time = "9:00 AM - 10:00 AM",
            courseTitle = "Data Structures",
            faculty = "dr. sarah khan",
            location = "Room 101",
            edpCode = "",
            courseCode = "CS-101"
        )

        val row = scheduleRow("TUE", entry)

        assertEquals("Data Structures", row[2])
        assertFalse(row[2].contains("CS-101"))
        assertFalse(row[2].contains("Sarah"))
        assertFalse(row[2].contains("Khan"))
        assertEquals("Dr. Sarah Khan", row[3])
    }

    @Test
    fun `schedule row handles missing optional fields gracefully`() {
        val entry = WeeklyScheduleEntry(
            day = "WED",
            time = "TBA",
            courseTitle = "Independent Study",
            faculty = "",
            location = "",
            edpCode = "",
            courseCode = ""
        )

        val row = scheduleRow("WED", entry)

        assertEquals(5, row.size)
        assertEquals("WED", row[0])
        assertEquals("TBA", row[1])
        assertEquals("Independent Study", row[2])
        assertEquals("—", row[3])
        assertEquals("—", row[4])
    }
}
