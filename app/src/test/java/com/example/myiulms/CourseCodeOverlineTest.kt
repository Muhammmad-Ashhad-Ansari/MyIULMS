package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards [displayCourseCode], the overline above a weekly class title.
 *
 * The parser normally stores a bare token, but the UI must not print a portal
 * label if one ever reaches it, and it must not reserve a line when there is
 * nothing to show.
 */
class CourseCodeOverlineTest {

    @Test
    fun `a bare code is returned unchanged`() {
        assertEquals("CSC334", displayCourseCode("CSC334"))
    }

    @Test
    fun `a leading course code label is stripped`() {
        assertEquals("CSC334", displayCourseCode("Course Code : CSC334"))
        assertEquals("CSC334", displayCourseCode("course code:CSC334"))
        assertEquals("CSC334", displayCourseCode("COURSE CODE:   CSC334"))
        assertEquals("CSC334", displayCourseCode("Course   Code  :  CSC334"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("CSC334", displayCourseCode("  CSC334  "))
    }

    @Test
    fun `blank inputs produce no overline`() {
        assertNull(displayCourseCode(""))
        assertNull(displayCourseCode("   "))
        assertNull(displayCourseCode(null))
    }

    @Test
    fun `a label with no value produces no overline`() {
        assertNull(displayCourseCode("Course Code :"))
        assertNull(displayCourseCode("course code:"))
        assertNull(displayCourseCode("  Course Code  "))
    }
}
