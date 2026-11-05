package com.example.myiulms.domain.attendance

import com.example.myiulms.AttendanceCourse
import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.policy.AcademicPolicy

/**
 * How close a course is to the attendance lock (XF below
 * [AcademicPolicy.ATTENDANCE_MIN_PERCENT], 75%: up to 25% of planned sessions may
 * be missed). Pure Kotlin, JVM-tested.
 *
 * Levels: Danger at 0 absences left or when the minimum can no longer be
 * reached; Caution once more than half the allowance is used or exactly one
 * absence is left; otherwise Safe.
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
    val allowedAbsences: Int? = null,
    /** Allowed minus absent; negative means over the limit. */
    val absencesLeft: Int? = null,
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
    val allowed = planned * (100 - minPercent) / 100
    val left = allowed - course.absent
    val remaining = planned - course.totalSessions
    val best = (course.present + remaining) * 100 / planned

    val level = when {
        left <= 0 || best < minPercent -> AttendanceLevel.Danger
        left == 1 || course.absent * 2 > allowed -> AttendanceLevel.Caution
        else -> AttendanceLevel.Safe
    }
    return AttendanceRisk(level, planned, allowed, left, best)
}

/** One line for the course card, e.g. "2 absences left". Null when unknown. */
fun attendanceRiskLabel(risk: AttendanceRisk): String? {
    val left = risk.absencesLeft ?: return null
    return when {
        left < 0 -> "Over the absence limit"
        left == 0 -> "No absences left"
        left == 1 -> "Last absence left"
        else -> "$left absences left"
    }
}
