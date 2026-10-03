package com.example.myiulms.ui.policy

/**
 * Read-only academic policy reference for Iqra University, Main Campus.
 *
 * DISPLAY ONLY. Nothing here may be used to compute pass/fail, degree
 * eligibility, or scholarship entitlement. Iqra University is the sole authority
 * on these rules and the portal result is the source of truth for a student's
 * record.
 *
 * Every figure carries a [PolicyConfidence] so the UI stays explicit about what
 * is confirmed and what the student must confirm with the Office of the
 * Registrar. This type has no Compose imports so it stays unit-testable.
 */

enum class PolicyConfidence { VERIFIED, CONTESTED, UNVERIFIED }

enum class GradeTone { STRONG, AVERAGE, WEAK, CRITICAL, NEUTRAL }

enum class PolicyBadgeTone { SUCCESS, CAUTION }

data class GradeBand(
    val letter: String,
    val range: String,
    val points: String,
    val tone: GradeTone,
    val confidence: PolicyConfidence = PolicyConfidence.VERIFIED
)

data class PolicySection(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val badgeTone: PolicyBadgeTone = PolicyBadgeTone.CAUTION,
    val bands: List<GradeBand> = emptyList(),
    val notes: List<String> = emptyList()
)

data class ScholarshipTier(
    val bracket: String,
    val waiver: String,
    val condition: String
)

data class PolicySource(
    val label: String,
    val url: String,
    val isPrimary: Boolean = false
)

object AcademicPolicy {

    const val CAMPUS = "Main Campus"
    const val CAMPUS_LABEL = "Iqra University, $CAMPUS"

    const val OFFICIAL_POLICIES_URL = "https://iqra.edu.pk/iu-policies/"
    const val OFFICIAL_SCHOLARSHIP_URL = "https://www.iqrauni.edu.pk/scholarships"
    const val REGISTRAR_EMAIL = "verify@iqra.edu.pk"

    // ---- Passing marks ----------------------------------------------------

    const val PASS_THRESHOLD_PERCENT = 50
    const val PASSING_GRADE_LABEL = "D Grade"
    const val PREVIOUS_PASS_THRESHOLD_PERCENT = 60
    const val PREVIOUS_PASSING_GRADE_LABEL = "C Grade"

    const val SCHOLARSHIP_MIN_CGPA = "3.50"
    const val SCHOLARSHIP_CREDIT_HOURS_BACHELOR = 15
    const val SCHOLARSHIP_CREDIT_HOURS_MASTERS = 12

    val sources = listOf(
        PolicySource(
            label = "IU Policies",
            url = OFFICIAL_POLICIES_URL,
            isPrimary = true
        ),
        PolicySource(
            label = "IU Scholarship Policy",
            url = OFFICIAL_SCHOLARSHIP_URL
        )
    )

    // ---- New scheme -------------------------------------------------------

    val newScheme = PolicySection(
        id = "new",
        title = "New Grading Scheme (Batch Fall 2024 & Onwards)",
        subtitle = "Passing Marks: $PASS_THRESHOLD_PERCENT% " +
            "($PASSING_GRADE_LABEL) \u2022 Applies to Freshmen/Sophomores",
        badge = "Freshmen/Sophomores",
        badgeTone = PolicyBadgeTone.SUCCESS,
        bands = listOf(
            GradeBand("A", "90% - 100%", "4.0", GradeTone.STRONG),
            GradeBand("A-", "85% - 89%", "3.67", GradeTone.STRONG),
            GradeBand("B+", "80% - 84%", "3.33", GradeTone.AVERAGE),
            GradeBand("B", "75% - 79%", "3.0", GradeTone.AVERAGE),
            GradeBand("B-", "70% - 74%", "2.67", GradeTone.AVERAGE),
            GradeBand("C+", "65% - 69%", "2.33", GradeTone.WEAK),
            GradeBand("C", "60% - 64%", "2.0", GradeTone.WEAK),
            GradeBand(
                letter = "D",
                range = "$PASS_THRESHOLD_PERCENT% - 59%",
                points = "1.0",
                tone = GradeTone.CRITICAL
            ),
            GradeBand("F", "Below $PASS_THRESHOLD_PERCENT%", "0.0", GradeTone.CRITICAL),
            GradeBand(
                letter = "XF",
                range = "Attendance below 80%",
                points = "0.0",
                tone = GradeTone.CRITICAL
            )
        ),
        notes = listOf(
            "D is the minimum passing grade and carries 1.0 grade point. " +
                "XF indicates an attendance lock, not academic effort."
        )
    )

    // ---- Previous scheme --------------------------------------------------

    val oldScheme = PolicySection(
        id = "old",
        title = "Old Grading Scheme (Till Fall 2024)",
        subtitle = "Passing Marks: $PREVIOUS_PASS_THRESHOLD_PERCENT% " +
            "($PREVIOUS_PASSING_GRADE_LABEL) \u2022 Applies to Seniors/Juniors",
        badge = "Seniors/Juniors",
        badgeTone = PolicyBadgeTone.CAUTION,
        bands = listOf(
            GradeBand("A", "88% - 100%", "4.0", GradeTone.STRONG),
            GradeBand("B+", "81% - 87%", "3.5", GradeTone.STRONG),
            GradeBand("B", "74% - 80%", "3.0", GradeTone.AVERAGE),
            GradeBand("C+", "67% - 73%", "2.5", GradeTone.AVERAGE),
            GradeBand("C", "60% - 66%", "2.0", GradeTone.WEAK),
            GradeBand("F", "Below 60%", "0.0", GradeTone.CRITICAL)
        ),
        notes = listOf(
            "Under this scheme there is no D grade. C at $PREVIOUS_PASS_THRESHOLD_PERCENT% " +
                "is the minimum passing grade.",
            "Ask the Registrar which scheme applies to you. Do not judge a pass " +
                "or fail from this table alone."
        )
    )

    // ---- Scholarship -------------------------------------------------------

    val scholarship = PolicySection(
        id = "scholarship",
        title = "Scholarship Requirements",
        subtitle = "Tuition-fee waiver by semester GPA. Every tier requires a " +
            "cumulative CGPA of $SCHOLARSHIP_MIN_CGPA or above.",
        badge = "CGPA $SCHOLARSHIP_MIN_CGPA+"
    )

    val scholarshipTiers = listOf(
        ScholarshipTier("4.00 GPA", "60%", "CGPA $SCHOLARSHIP_MIN_CGPA or above"),
        ScholarshipTier("3.75 - 3.99 GPA", "40%", "CGPA $SCHOLARSHIP_MIN_CGPA or above"),
        ScholarshipTier("3.50 - 3.74 GPA", "20%", "CGPA $SCHOLARSHIP_MIN_CGPA or above")
    )

    val scholarshipConditionChips = listOf(
        "CGPA $SCHOLARSHIP_MIN_CGPA+ required",
        "Load: $SCHOLARSHIP_CREDIT_HOURS_BACHELOR Cr. Hrs (BS) / " +
            "$SCHOLARSHIP_CREDIT_HOURS_MASTERS Cr. Hrs (MS)",
        "Projects and theses excluded",
        "No F, W, or I grade",
        "Capped at 10% of cohort",
        "No pending disciplinary case",
        "Reviewed each semester"
    )

    val scholarshipNote = "Confirm the credit-hour load for your programme with " +
        "the Registrar."
}