package com.example.myiulms

const val DEGREE_TOTAL_CREDIT_HOURS = 136

private val nonCompletionGrades = setOf("I", "W", "F", "XF")

/**
 * Course identity deliberately uses BOTH code and title.
 *
 * IULMS may use the same/base course code for theory and lab components.
 * Grouping only by code can therefore hide labs and under-count credits.
 */
private fun courseIdentity(course: Course): String {
    val code = course.code.trim().uppercase()
    val title = course.title
        .trim()
        .uppercase()
        .replace(Regex("\\s+"), " ")
    return "$code|$title"
}

private fun isCompletedAttempt(course: Course): Boolean {
    val grade = course.grade.trim().uppercase()
    val hours = course.hours.toDoubleOrNull() ?: 0.0
    return grade.isNotBlank() &&
        grade !in nonCompletionGrades &&
        hours > 0.0
}

/**
 * Count each logical course/lab once. If a course was repeated, any completed
 * attempt earns the credit hours. Theory and lab remain separate because the
 * title is part of the identity.
 */
fun completedHours(courses: List<Course>): Int =
    courses
        .groupBy(::courseIdentity)
        .values
        .sumOf { attempts ->
            val completed = attempts.firstOrNull(::isCompletedAttempt)
                ?: return@sumOf 0
            completed.hours.toDoubleOrNull()?.toInt() ?: 0
        }

/** A weighted grade-point average and what it was computed over. */
data class GpaSummary(val gpa: Double, val creditHours: Double, val attempts: Int)

/**
 * "Clean CGPA": the CGPA without failed or unfinished attempts. Unofficial.
 *
 * The portal's own CGPA (verified against live data) is
 *   Σ(points × hours) / Σ hours over EVERY attempt with numeric grade points:
 * an F counts as 0 points with its full hours and keeps counting after the
 * course is passed; W and I carry blank points and are left out.
 *
 * This applies the same formula and the same "every attempt, no replacement"
 * rule, but drops F, W, I and XF attempts. Rows without numeric grade points
 * (e.g. PASS) or without positive hours are ignored, as the portal does.
 * Repeated courses are deliberately NOT de-duplicated.
 *
 * Returns null when no attempt qualifies.
 */
fun cleanCgpa(courses: List<Course>): GpaSummary? {
    val counted = courses.mapNotNull { course ->
        val grade = course.grade.trim().uppercase()
        val hours = course.hours.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
        val points = course.gpa.trim().toDoubleOrNull()
        if (grade.isBlank() || grade in nonCompletionGrades || hours == null || points == null) null
        else hours to points
    }
    val totalHours = counted.sumOf { it.first }
    if (totalHours == 0.0) return null
    return GpaSummary(
        gpa = counted.sumOf { (hours, points) -> hours * points } / totalHours,
        creditHours = totalHours,
        attempts = counted.size
    )
}

/** True when the transcript has any F, W, I or XF attempt, i.e. Clean CGPA would differ. */
fun hasExcludedAttempts(courses: List<Course>): Boolean =
    courses.any { it.grade.trim().uppercase() in nonCompletionGrades }

fun remainingHours(courses: List<Course>): Int =
    (DEGREE_TOTAL_CREDIT_HOURS - completedHours(courses)).coerceAtLeast(0)

/** Transcript list filters: everything, weak entries, or one exact grade. */
const val TRANSCRIPT_FILTER_ALL = "All"
const val TRANSCRIPT_FILTER_WEAK = "Weak"

/** Best to worst, covering both grading schemes; unknown grades sort after these. */
private val GradeOrder = listOf("A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "F", "XF", "I", "W")

private fun normalizedGrade(grade: String) = grade.trim().uppercase()

/**
 * Filter choices for the transcript: All, every grade that actually appears
 * (best first), then Weak. Only grades present are offered, so no choice ever
 * leads to an empty list by itself.
 */
fun transcriptFilterOptions(courses: List<Course>): List<String> {
    val grades = courses.map { normalizedGrade(it.grade) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedWith(compareBy({ GradeOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
    return listOf(TRANSCRIPT_FILTER_ALL) + grades + TRANSCRIPT_FILTER_WEAK
}

fun filterTranscript(courses: List<Course>, filter: String): List<Course> = when (filter) {
    TRANSCRIPT_FILTER_ALL -> courses
    TRANSCRIPT_FILTER_WEAK -> courses.filter { isWeakCourse(it.grade) }
    else -> courses.filter { normalizedGrade(it.grade) == filter }
}

fun isWeakCourse(grade: String): Boolean {
    val g = grade.trim().uppercase()
    return g.startsWith("C") || g == "D" || g == "F"
}

fun gradeMeaning(grade: String): String {
    val g = grade.trim().uppercase()
    return when {
        // B+ keeps the "Strong" label so the word on the card never
        // contradicts the green colour the same grade is drawn in.
        g == "A" || g == "A-" || g == "B+" -> "Strong"
        g.startsWith("B") -> "Average"
        g.startsWith("C") -> "Weak"
        // D is a minimum-pass grade under the Main Campus scheme, so it must not
        // share the "Critical" label with a failing F grade.
        g == "D" -> "Minimum Pass"
        g == "F" -> "Critical"
        else -> "Not counted"
    }
}
