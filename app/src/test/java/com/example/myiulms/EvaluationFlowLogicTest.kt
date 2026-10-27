package com.example.myiulms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationFlowLogicTest {

    private val submitted = EvaluationCourse("1867", "Data Structures", "Dr. [NAME]", completed = false)

    @Test
    fun submissionConfirmedWhenCourseNowCompleted() {
        val refreshed = listOf(
            EvaluationCourse("", "Data Structures", "Dr. [NAME]", completed = true),
            EvaluationCourse("1868", "Operating Systems", "Prof. [NAME]", completed = false)
        )
        assertTrue(isSubmissionConfirmed(refreshed, submitted))
    }

    @Test
    fun submissionNotConfirmedWhenSameCourseIdStillPending() {
        val refreshed = listOf(EvaluationCourse("1867", "Data Structures", "Dr. [NAME]", completed = false))
        assertFalse(isSubmissionConfirmed(refreshed, submitted))
    }

    @Test
    fun submissionNotConfirmedWhenTokenRotatedButCourseStillPending() {
        // Opaque courseId token may change between page loads — title + instructor still match.
        val refreshed = listOf(EvaluationCourse("9999", "Data Structures", "Dr. [NAME]", completed = false))
        assertFalse(isSubmissionConfirmed(refreshed, submitted))
    }

    @Test
    fun unsavedInputDetectsAnswersOrNonBlankComments() {
        assertFalse(hasUnsavedEvaluationInput(emptyMap(), emptyMap()))
        assertFalse(hasUnsavedEvaluationInput(emptyMap(), mapOf("comments[109]" to "   ")))
        assertTrue(hasUnsavedEvaluationInput(mapOf("answer[586]" to "1872"), emptyMap()))
        assertTrue(hasUnsavedEvaluationInput(emptyMap(), mapOf("comments[109]" to "Great labs")))
    }
}
