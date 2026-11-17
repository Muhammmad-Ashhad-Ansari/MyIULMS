package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards [dayHeaderLabel], the sticky header's day text.
 *
 * The marker is the only on-screen cue telling a student which group is today,
 * and it must follow the "today" value rather than being written into a row, so
 * a different [today] (the next day after midnight) moves the marker with it.
 */
class DayHeaderLabelTest {

    @Test
    fun `todays header carries the marker`() {
        assertEquals("Today · MON", dayHeaderLabel("MON", today = "MON"))
    }

    @Test
    fun `other headers stay plain`() {
        assertEquals("WED", dayHeaderLabel("WED", today = "MON"))
        assertEquals("SUN", dayHeaderLabel("SUN", today = "MON"))
    }

    @Test
    fun `a different today moves the marker`() {
        // Same day value, next day as "today": the marker follows the argument.
        assertEquals("TUE", dayHeaderLabel("TUE", today = "MON"))
        assertEquals("Today · TUE", dayHeaderLabel("TUE", today = "TUE"))
    }
}
