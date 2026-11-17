package com.example.myiulms.domain.attendance

import com.example.myiulms.AttendanceCourse
import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.policy.AcademicPolicy

/**
 * How close a course is to the attendance F (below
 * [AcademicPolicy.ATTENDANCE_MIN_PERCENT], 75%: up to 25% of planned sessions may
 * be missed). Pure Kotlin, JVM-tested.
 *
 * The F limit is the allowed absence count itself: 25% of planned sessions,
 * rounded down (16 of 64, 8 of 32). The last safe absence is one less than that,
 * because the absence that reaches the limit earns F.
 *
 * Levels: Safe with 3 or more safe absences left, Caution with 1-2, Danger at 0
 * (the next absence earns F) or once the 75% minimum can no longer be reached.
 *
 * Planned sessions come from the course's own weekly timetable, verified on
 * real portal data:
 *   planned = weekly meetings × [SEMESTER_WEEKS] × attendance sessions per meeting
 * - Weekly meetings: the attendance record's own schedule ("MON 1:00 PM 2:20 PM
 *   E-806, WED …"); if that is empty, the weekly timetable entries with the same
 *   EDP code (the number in the course name, unique per theory/lab section).
 * - Sessions per meeting: the "Session N" columns of the course's session table
 *   (2 on the live portal), falling back to [DEFAULT_SESSIONS_PER_MEETING].
 *
 * The status counts absences against the allowance rather than the running
 * percentage, so a single early-semester absence never reads as danger.
 */

const val SEMESTER_WEEKS = 16
const val DEFAULT_SESSIONS_PER_MEETING = 2

enum class AttendanceLevel { Safe, Caution, Danger, Unknown }

data class AttendanceRisk(
    val level: AttendanceLevel,
    /** Null when the course's weekly meetings could not be determined. */
    val plannedSessions: Int? = null,
    /** Absences that earn F: 25% of planned sessions, rounded down. */
    val fLimit: Int? = null,
    /** Last absence that still clears 75%, i.e. fLimit - 1. */
    val maxSafe: Int? = null,
    /** maxSafe minus absent; 0 means the next absence earns F, negative means F is reached. */
    val safeLeft: Int? = null,
    /** Highest attendance % still reachable by attending every remaining session. */
    val bestPossiblePercent: Int? = null
)

private val EdpCodePattern = Regex("""\((\d{6,})\)\s*$""")
private val DayToken = Regex("""\b(MON|TUE|WED|THU|FRI|SAT|SUN)""", RegexOption.IGNORE_CASE)
private val SessionHeader = Regex("""^\s*session\b""", RegexOption.IGNORE_CASE)

/** "AI AGENTS (032610669)" → "032610669". */
fun edpCodeOf(courseName: String): String? = EdpCodePattern.find(courseName)?.groupValues?.get(1)

/** Weekly meetings in a schedule like "MON 1:00 PM 2:20 PM E-806, WED 1:00 PM 2:20 PM E-806". */
fun weeklyMeetingsFromSchedule(schedule: String): Int =
    schedule.split(",").count { DayToken.containsMatchIn(it) }

/** Distinct weekly slots in the timetable for one EDP code. */
fun weeklyMeetingsFromTimetable(edpCode: String, timetable: List<WeeklyScheduleEntry>): Int =
    timetable.filter { it.edpCode == edpCode }
        .map { normalizeDay(it.day) to it.time.trim() }
        .distinct()
        .size

fun sessionsPerMeeting(sessionHeaders: List<String>): Int =
    sessionHeaders.count { SessionHeader.containsMatchIn(it) }.takeIf { it > 0 } ?: DEFAULT_SESSIONS_PER_MEETING

fun assessAttendance(
    course: AttendanceCourse,
    timetable: List<WeeklyScheduleEntry>? = null,
    minPercent: Int = AcademicPolicy.ATTENDANCE_MIN_PERCENT,
    semesterWeeks: Int = SEMESTER_WEEKS
): AttendanceRisk {
    val meetings = weeklyMeetingsFromSchedule(course.schedule).takeIf { it > 0 }
        ?: edpCodeOf(course.name)?.let { edp -> timetable?.let { weeklyMeetingsFromTimetable(edp, it) } }
            ?.takeIf { it > 0 }
        ?: return AttendanceRisk(AttendanceLevel.Unknown)

    // Make-up classes can push the real count past the plan; never plan fewer
    // sessions than have already been held.
    val planned = maxOf(meetings * semesterWeeks * sessionsPerMeeting(course.sessionHeaders), course.totalSessions)
    val fLimit = planned * (100 - minPercent) / 100
    val maxSafe = (fLimit - 1).coerceAtLeast(0)
    val safeLeft = maxSafe - course.absent
    val remaining = planned - course.totalSessions
    val best = (course.present + remaining) * 100 / planned

    val level = when {
        course.absent >= fLimit || best < minPercent -> AttendanceLevel.Danger
        safeLeft == 0 -> AttendanceLevel.Danger
        safeLeft in 1..2 -> AttendanceLevel.Caution
        else -> AttendanceLevel.Safe
    }
    return AttendanceRisk(level, planned, fLimit, maxSafe, safeLeft, best)
}

/** True once the absence count earns F or 75% can no longer be reached. */
fun attendanceIsBarred(risk: AttendanceRisk): Boolean =
    risk.level == AttendanceLevel.Danger && (risk.safeLeft ?: 0) != 0

/**
 * Headline for the course card: "5 absences left", "1 absence left",
 * "0 left · Next absence = F", or "F · Not eligible for exam". Null when the
 * course's weekly meetings are unknown.
 */
fun attendanceRiskLabel(risk: AttendanceRisk): String? {
    val left = risk.safeLeft ?: return null
    return when (risk.level) {
        AttendanceLevel.Safe -> "$left absences left"
        AttendanceLevel.Caution -> if (left == 1) "1 absence left" else "$left absences left"
        AttendanceLevel.Danger -> if (left == 0) "0 left · Next absence = F" else "F · Not eligible for exam"
        AttendanceLevel.Unknown -> null
    }
}

/**
 * Supporting count, e.g. "12 / 16 (F at 16)". Once barred the raw count stays
 * unclamped, so 16 / 16 and 17 / 16 both show instead of hiding the overage.
 */
fun attendanceUsageLabel(risk: AttendanceRisk, absent: Int): String? {
    val fLimit = risk.fLimit ?: return null
    return if (attendanceIsBarred(risk)) "$absent / $fLimit" else "$absent / $fLimit (F at $fLimit)"
}
