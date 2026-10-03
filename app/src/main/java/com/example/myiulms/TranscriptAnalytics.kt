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

fun remainingHours(courses: List<Course>): Int =
    (DEGREE_TOTAL_CREDIT_HOURS - completedHours(courses)).coerceAtLeast(0)

fun isWeakCourse(grade: String): Boolean {
    val g = grade.trim().uppercase()
    return g.startsWith("C") || g == "D" || g == "F"
}

fun gradeMeaning(grade: String): String {
    val g = grade.trim().uppercase()
    return when {
        g == "A" || g == "A-" -> "Strong"
        g.startsWith("B") -> "Average"
        g.startsWith("C") -> "Weak"
        // D is a minimum-pass grade under the Main Campus scheme, so it must not
        // share the "Critical" label with a failing F grade.
        g == "D" -> "Minimum Pass"
        g == "F" -> "Critical"
        else -> "Not counted"
    }
}
