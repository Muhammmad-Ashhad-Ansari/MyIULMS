package com.example.myiulms

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHARE_WIDTH = 1400
private const val MARGIN = 84f
private const val ROW_HEIGHT = 86f

fun shareResultAsPng(context: Context, studentName: String?, result: ExamResult) {
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

fun shareTranscriptAsPng(context: Context, studentName: String?, transcript: Transcript) {
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

private fun shareAcademicTable(
    context: Context,
    filePrefix: String,
    title: String,
    subtitle: String,
    studentName: String?,
    metricLabel: String,
    metricValue: String,
    headers: List<String>,
    rows: List<List<String>>,
    columnWidths: List<Float>
) {
    val headerHeight = 470
    val footerHeight = 170
    val tableHeaderHeight = 88
    val height = (headerHeight + tableHeaderHeight + rows.size * ROW_HEIGHT + footerHeight)
        .toInt()
        .coerceAtLeast(900)

    val bitmap = Bitmap.createBitmap(SHARE_WIDTH, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(248, 250, 253))

    val primary = Color.rgb(0, 91, 149)
    val navy = Color.rgb(22, 48, 91)
    val text = Color.rgb(28, 38, 49)
    val muted = Color.rgb(93, 108, 122)
    val border = Color.rgb(220, 228, 236)
    val surface = Color.WHITE

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
    drawText(
        "Generated by MyIULMS • Unofficial • ${SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())}",
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

    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(shareDir, "${filePrefix}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()

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
