package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---- clean CGPA ----

    @Test
    fun cleanCgpaDropsFailedWithdrawnAndIncompleteAttempts() {
        val courses = listOf(
            Course("A1", "Algebra", "3", "A", "4.0"),
            Course("B1", "Biology", "3", "C", "2.0"),
            Course("C1", "Chemistry", "3", "F", "0"),
            Course("D1", "Drawing", "3", "W", ""),
            Course("E1", "Economics", "2", "I", ""),
            Course("X1", "Xenology", "3", "XF", "0")
        )
        val clean = cleanCgpa(courses)!!
        assertEquals(3.0, clean.gpa, 1e-9) // (3*4 + 3*2) / 6
        assertEquals(6.0, clean.creditHours, 1e-9)
        assertEquals(2, clean.attempts)
    }

    @Test
    fun cleanCgpaIgnoresUngradedRowsAndCountsEveryPassedAttempt() {
        val courses = listOf(
            Course("GEN100", "Community Service", "2", "PASS", ""),
            Course("CSC201", "Data Structures", "3", "C", "2.0"),
            Course("CSC201", "Data Structures", "3", "B", "3.0"), // retake: both count, no replacement
            Course("CSC202", "Data Structures Lab", "1", "A", "4.0")
        )
        val clean = cleanCgpa(courses)!!
        assertEquals((6.0 + 9.0 + 4.0) / 7.0, clean.gpa, 1e-9)
        assertEquals(3, clean.attempts)
    }

    @Test
    fun cleanCgpaEqualsThePortalFormulaWhenNothingIsExcluded() {
        val courses = listOf(
            Course("A1", "Algebra", "3", "B+", "3.5"),
            Course("B1", "Biology", "1", "C+", "2.5")
        )
        assertEquals((10.5 + 2.5) / 4.0, cleanCgpa(courses)!!.gpa, 1e-9)
        assertEquals(false, hasExcludedAttempts(courses))
    }

    @Test
    fun cleanCgpaIsNullWhenNoAttemptQualifies() {
        assertNull(cleanCgpa(listOf(Course("C1", "Chemistry", "3", "F", "0"), Course("D1", "Drawing", "3", "W", ""))))
        assertEquals(true, hasExcludedAttempts(listOf(Course("C1", "Chemistry", "3", "F", "0"))))
    }
}
