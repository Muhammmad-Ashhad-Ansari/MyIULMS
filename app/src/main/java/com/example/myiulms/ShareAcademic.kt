package com.example.myiulms

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.example.myiulms.ui.dashboard.formatWallClock
import com.example.myiulms.ui.dashboard.groupScheduleByDay
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.parseScheduleInterval
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHARE_WIDTH = 1400

/**
 * Footer date pattern, pinned to [Locale.ENGLISH].
 *
 * Replaces a per-call `SimpleDateFormat(...)`: constructing a formatter is not
 * free, and this one runs on every share.
 */
private val ShareFooterDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private const val MARGIN = 84f
private const val ROW_HEIGHT = 86f

suspend fun shareResultAsPng(context: Context, studentName: String?, result: ExamResult) {
    val subtitle = result.title
        .replace("EXAM RESULT", "", ignoreCase = true)
        .trim(' ', '(', ')', '-')
        .ifBlank { "Latest examination" }

    val rows = result.rows.map {
        listOf(
            it.course,
            it.total.ifBlank { "—" },
            it.grade.ifBlank { "—" },
            it.points.ifBlank { "—" }
        )
    }

    shareAcademicTable(
        context = context,
        filePrefix = "MyIULMS_Result",
        title = "Academic Result",
        subtitle = subtitle,
        studentName = studentName,
        metricLabel = "Semester GPA",
        metricValue = result.gpa ?: "—",
        headers = listOf("Course", "Total", "Grade", "Points"),
        rows = rows,
        columnWidths = listOf(.58f, .14f, .14f, .14f)
    )
}

suspend fun shareTranscriptAsPng(context: Context, studentName: String?, transcript: Transcript) {
    val completed = completedHours(transcript.courses)
    val rows = transcript.courses.map {
        listOf(
            "${it.code}  ${it.title}".trim(),
            it.hours.ifBlank { "—" },
            it.grade.ifBlank { "—" },
            it.gpa.ifBlank { "—" }
        )
    }

    shareAcademicTable(
        context = context,
        filePrefix = "MyIULMS_Transcript",
        title = "Academic Transcript",
        subtitle = "$completed / $DEGREE_TOTAL_CREDIT_HOURS credit hours complete",
        studentName = studentName,
        metricLabel = "CGPA",
        metricValue = transcript.cgpa.ifBlank { "—" },
        headers = listOf("Course", "CH", "Grade", "GPA"),
        rows = rows,
        columnWidths = listOf(.62f, .10f, .14f, .14f)
    )
}

/**
 * Weekly class schedule as a shareable image.
 *
 * Mirrors [shareResultAsPng] / [shareTranscriptAsPng] exactly: the same 1400px
 * canvas pipeline, the same brand palette, the same footer, the same
 * `FileProvider` + `ACTION_SEND` handoff. Only the row model differs.
 *
 * The rows are grouped by [groupScheduleByDay] rather than re-sorted here, so
 * the exported image and the on-screen list always present the week in the same
 * order.
 *
 * @param now injected so the footer date is deterministic in tests.
 */
suspend fun shareScheduleAsPng(
    context: Context,
    studentName: String?,
    schedule: WeeklySchedule?,
    now: LocalDate = LocalDate.now()
) {
    val groups = groupScheduleByDay(schedule?.entries.orEmpty())
    val classCount = groups.values.sumOf { it.size }

    val rows = groups.flatMap { (day, entries) ->
        entries.map { entry -> scheduleRow(day, entry) }
    }

    shareAcademicTable(
        context = context,
        filePrefix = "MyIULMS_Schedule",
        title = "Weekly Class Schedule",
        subtitle = if (groups.isEmpty()) {
            "No classes reported for this week"
        } else {
            "$classCount class${if (classCount == 1) "" else "es"} across " +
                "${groups.size} day${if (groups.size == 1) "" else "s"}"
        },
        studentName = studentName,
        metricLabel = "This week",
        metricValue = if (classCount == 0) "—" else "$classCount",
        headers = listOf("Day", "Time", "Course", "Room"),
        rows = rows,
        columnWidths = listOf(.12f, .17f, .45f, .13f),
        generatedOn = now
    )
}

/**
 * One schedule row as the four display strings the table renderer draws.
 *
 * A row is three visual bands rather than four columns: the time and the course
 * title each get their own line, so the course column carries the code and the
 * faculty beneath the title.
 */
private fun scheduleRow(day: String, entry: WeeklyScheduleEntry): List<String> {
    val title = entry.courseTitle.trim().ifBlank { "Class" }
    val interval = parseScheduleInterval(entry.time)
    val time = if (interval == null) {
        // "TBA" or a single token. The raw portal text still tells the student
        // something; an em dash would tell them nothing.
        entry.time.trim().ifBlank { "TBA" }
    } else {
        "${formatWallClock(interval.startMinuteOfDay)} - " +
            formatWallClock(interval.endMinuteOfDay)
    }

    val course = buildString {
        append(title)
        // Code and faculty share the course cell; the code is the identifier a
        // classmate would search on, the faculty is the tiebreaker when two
        // sections of the same course meet at once.
        val code = entry.courseCode.trim()
        val faculty = entry.faculty.trim()
        if (code.isNotBlank()) {
            append("  ·  ")
            append(code)
        }
        if (faculty.isNotBlank()) {
            append("  ·  ")
            append(formatShareName(faculty))
        }
    }

    return listOf(day, time, course, entry.location.trim())
}

private suspend fun shareAcademicTable(
    context: Context,
    filePrefix: String,
    title: String,
    subtitle: String,
    studentName: String?,
    metricLabel: String,
    metricValue: String,
    headers: List<String>,
    rows: List<List<String>>,
    columnWidths: List<Float>,
    generatedOn: LocalDate? = null
) {
    val headerHeight = 470
    val footerHeight = 170
    val tableHeaderHeight = 88
    val height = (headerHeight + tableHeaderHeight + rows.size * ROW_HEIGHT + footerHeight)
        .toInt()
        .coerceAtLeast(900)

    val primary = Color.rgb(0, 91, 149)
    val navy = Color.rgb(22, 48, 91)
    val text = Color.rgb(28, 38, 49)
    val muted = Color.rgb(93, 108, 122)
    val border = Color.rgb(220, 228, 236)
    val surface = Color.WHITE

    // Allocation and every draw call happen off the main thread. A 1400 x ~1900
    // ARGB_8888 bitmap is ~10MB, and PNG compression at quality 100 is
    // CPU-bound: doing this inline drops frames on a mid-range device and risks
    // an ANR on a long week. Only the two calls that must touch the main thread
    // -- cache-dir file IO and startActivity -- are hoisted back out below.
    val bitmap = withContext(Dispatchers.Default) {
        val created = Bitmap.createBitmap(SHARE_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(created)
        canvas.drawColor(Color.rgb(248, 250, 253))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun drawText(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
            paint.textSize = size
            paint.color = color
            paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }

    drawText("MyIULMS", MARGIN, 112f, 54f, primary, true)
    drawText("Unofficial student client for IULMS", MARGIN, 158f, 26f, muted)

    paint.color = primary
    canvas.drawRoundRect(MARGIN, 194f, SHARE_WIDTH - MARGIN, 202f, 4f, 4f, paint)

    drawText(title, MARGIN, 276f, 50f, text, true)
    drawText(subtitle, MARGIN, 320f, 27f, muted)

    studentName?.takeIf { it.isNotBlank() }?.let {
        drawText("Student: ${formatShareName(it)}", MARGIN, 366f, 29f, text, true)
    }

    val metricLeft = SHARE_WIDTH - 420f
    paint.color = Color.rgb(229, 242, 250)
    canvas.drawRoundRect(metricLeft, 245f, SHARE_WIDTH - MARGIN, 390f, 28f, 28f, paint)
    drawText(metricLabel, metricLeft + 30f, 295f, 24f, navy)
    drawText(metricValue, metricLeft + 30f, 358f, 50f, navy, true)

    val tableTop = headerHeight.toFloat()
    paint.color = surface
    canvas.drawRoundRect(
        MARGIN,
        tableTop,
        SHARE_WIDTH - MARGIN,
        height - footerHeight.toFloat(),
        24f,
        24f,
        paint
    )

    val tableWidth = SHARE_WIDTH - MARGIN * 2
    val colStarts = mutableListOf(MARGIN)
    columnWidths.dropLast(1).forEach { fraction ->
        colStarts += colStarts.last() + tableWidth * fraction
    }

    paint.color = Color.rgb(240, 244, 248)
    canvas.drawRect(MARGIN, tableTop, SHARE_WIDTH - MARGIN, tableTop + tableHeaderHeight, paint)

    headers.forEachIndexed { i, header ->
        drawText(header, colStarts[i] + 18f, tableTop + 57f, 25f, navy, true)
    }

    rows.forEachIndexed { rowIndex, row ->
        val yTop = tableTop + tableHeaderHeight + rowIndex * ROW_HEIGHT
        if (rowIndex % 2 == 1) {
            paint.color = Color.rgb(250, 252, 254)
            canvas.drawRect(MARGIN, yTop, SHARE_WIDTH - MARGIN, yTop + ROW_HEIGHT, paint)
        }

        paint.color = border
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN, yTop, SHARE_WIDTH - MARGIN, yTop, paint)

        row.forEachIndexed { colIndex, value ->
            val maxWidth = if (colIndex < columnWidths.lastIndex) {
                tableWidth * columnWidths[colIndex] - 32f
            } else {
                SHARE_WIDTH - MARGIN - colStarts[colIndex] - 18f
            }

            drawEllipsizedText(
                canvas = canvas,
                paint = paint,
                value = value,
                x = colStarts[colIndex] + 18f,
                baseline = yTop + 55f,
                maxWidth = maxWidth,
                size = if (colIndex == 0) 24f else 25f,
                color = text,
                bold = colIndex == 2
            )
        }
    }

    val footerY = height - 90f
        val footerDate = generatedOn ?: LocalDate.now()
        drawText(
            "Generated by MyIULMS • Unofficial • ${footerDate.format(ShareFooterDateFormatter)}",
            MARGIN,
            footerY,
            23f,
            muted
        )
        drawText(
            "Academic data is read from the student's authenticated IULMS session.",
            MARGIN,
            footerY + 38f,
            21f,
            muted
        )

        created
    }

    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(shareDir, "${filePrefix}_${System.currentTimeMillis()}.png")
    // Compression is the expensive half, so it goes back to the background
    // dispatcher before touching the cache directory.
    withContext(Dispatchers.Default) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share with"))
}

private fun drawEllipsizedText(
    canvas: Canvas,
    paint: Paint,
    value: String,
    x: Float,
    baseline: Float,
    maxWidth: Float,
    size: Float,
    color: Int,
    bold: Boolean
) {
    paint.textSize = size
    paint.color = color
    paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
    else android.graphics.Typeface.DEFAULT

    val safe = value.replace("\n", " ").trim()
    val text = if (paint.measureText(safe) <= maxWidth) {
        safe
    } else {
        var cut = safe
        while (cut.length > 1 && paint.measureText("$cut…") > maxWidth) {
            cut = cut.dropLast(1)
        }
        "$cut…"
    }
    canvas.drawText(text, x, baseline, paint)
}

private fun formatShareName(name: String): String =
    name.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
