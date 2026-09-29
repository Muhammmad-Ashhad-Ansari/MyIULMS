package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAnalyticsTest {

    @Test
    fun theoryAndLabWithSameCodeAreBothCounted() {
        val courses = listOf(
            Course("CSC101", "Programming Fundamentals", "3", "A", "4.0"),
            Course("CSC101", "Programming Fundamentals Lab", "1", "A", "4.0")
        )
        assertEquals(4, completedHours(courses))
    }

    @Test
    fun repeatedCourseCountsOnceAfterPassing() {
        val courses = listOf(
            Course("CSC201", "Data Structures", "3", "F", "0.0"),
            Course("CSC201", "Data Structures", "3", "B", "3.0")
        )
        assertEquals(3, completedHours(courses))
    }

    @Test
    fun withdrawnAndIncompleteDoNotCount() {
        val courses = listOf(
            Course("A", "Course A", "3", "W", ""),
            Course("B", "Course B", "3", "I", "")
        )
        assertEquals(0, completedHours(courses))
    }

    @Test
    fun passGradeCountsTowardCompletion() {
        val courses = listOf(
            Course("GEN100", "Community Service", "2", "PASS", "")
        )
        assertEquals(2, completedHours(courses))
    }
}
