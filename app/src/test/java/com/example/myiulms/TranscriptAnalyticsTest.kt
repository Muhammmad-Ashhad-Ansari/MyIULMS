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

    // ---- grade filter ----

    private val mixed = listOf(
        Course("CSC101", "Programming", "3", "B", "3.0"),
        Course("CSC102", "Programming Lab", "1", "A", "4.0"),
        Course("MTH101", "Calculus", "3", "C+", "2.33"),
        Course("ENG101", "English", "3", "b+", "3.33"),
        Course("GEN100", "Community Service", "2", "PASS", ""),
        Course("PHY101", "Physics", "3", "A", "4.0")
    )

    @Test
    fun filterOptionsListPresentGradesBestFirstBetweenAllAndWeak() {
        assertEquals(listOf("All", "A", "B+", "B", "C+", "PASS", "Weak"), transcriptFilterOptions(mixed))
    }

    @Test
    fun filterByExactGradeIgnoresCaseAndSpacing() {
        assertEquals(listOf("Programming Lab", "Physics"), filterTranscript(mixed, "A").map { it.title })
        assertEquals(listOf("English"), filterTranscript(mixed, "B+").map { it.title })
    }

    @Test
    fun allAndWeakFilters() {
        assertEquals(6, filterTranscript(mixed, TRANSCRIPT_FILTER_ALL).size)
        assertEquals(listOf("Calculus"), filterTranscript(mixed, TRANSCRIPT_FILTER_WEAK).map { it.title })
    }
}
