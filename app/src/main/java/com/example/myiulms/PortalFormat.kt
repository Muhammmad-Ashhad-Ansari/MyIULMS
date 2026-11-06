package com.example.myiulms

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Display formats for values the portal returns as free text. Pure Kotlin,
 * JVM-tested. Anything that does not parse is shown exactly as the portal sent it.
 */

private val PortalLongDate = DateTimeFormatter.ofPattern("EEEE, d MMMM, yyyy", Locale.ENGLISH)
private val ShortDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val OrdinalSuffix = Regex("(?<=\\d)(st|nd|rd|th)")

/** "Monday, 30th November, 2026" → 2026-11-30; null if it does not parse. */
fun parsePortalDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw.trim().replace(OrdinalSuffix, ""), PortalLongDate) }.getOrNull()

/** "Monday, 30th November, 2026" → "Mon, 30 Nov 2026"; unchanged if it does not parse. */
fun shortPortalDate(raw: String): String = parsePortalDate(raw)?.format(ShortDate) ?: raw

/** "1,000.00" / "Rs 27900" → 1000.0 / 27900.0; 0.0 if it does not parse. */
fun parseRupees(raw: String): Double =
    raw.replace(",", "").replace("Rs", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0

/** 1000.0 → "Rs 1,000": whole rupees, grouped, no decimals. */
fun formatRupees(value: Double): String =
    "Rs " + NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(value)

/**
 * A screen subtitle that does not just repeat the heading. The portal's own
 * labels ("Exam Schedule", "Semester Schedule") are dropped in favour of
 * [fallback]; a specific one ("Final Exam Schedule Fall 2026") is kept.
 */
fun distinctSubtitle(portalTitle: String, heading: String, fallback: String): String {
    val title = portalTitle.trim()
    val generic = title.isBlank() ||
        title.equals(heading.trim(), ignoreCase = true) ||
        (title.none { it.isDigit() } && title.endsWith("schedule", ignoreCase = true))
    return if (generic) fallback else title
}
