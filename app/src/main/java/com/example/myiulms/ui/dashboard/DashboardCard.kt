package com.example.myiulms.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing

// ---------------------------------------------------------------------------
// Geometry. Every value here is a hard constant, shared by every state, so the
// card cannot reflow when the focus tier changes.
// ---------------------------------------------------------------------------

/**
 * Height envelope for the whole card, identical in all five states.
 *
 * Deliberately 92.dp and not 84.dp: the four text slots measure 68.dp, so 92.dp
 * is what keeps the block inside the frame up to a 1.35x font scale. At 84.dp
 * the same stack clips below ~1.23x. The extra 8.dp buys accessibility headroom
 * that a tighter frame would spend on nothing.
 */
private val CardHeight = 92.dp

/**
 * Start and end inset for card content.
 *
 * Must equal the inset [com.example.myiulms.ui.theme.AppSpacing] list cards use
 * on their own content (16.dp). This is what puts the countdown card's text on
 * the same vertical axis as every schedule card below it. The accent indicator
 * is drawn OVER this inset, not inside it, so it costs no layout width.
 */
private val ContentInset = AppSpacing.Lg

/** Gap between the text column and the right-hand anchor slot. */
private val AnchorGap = AppSpacing.Md

/** Diameter of the progress ring, and the height of the right-hand slot. */
private val RingSize = 56.dp

/**
 * Fixed frame for the ticker so a changing string cannot reflow its neighbours.
 *
 * [Modifier.requiredWidth] rather than [Modifier.width]: a plain width() is
 * coerced down by any smaller parent constraint and would silently collapse this
 * frame. The slot is sized to match so the constraint cannot bite in the first
 * place, and requiredWidth keeps it honest if that ever changes.
 */
private val TickerWidth = 72.dp

/** Width of the full-height flush accent indicator on the leading edge. */
private val AccentBarWidth = 4.dp

/** Diameter of the terminal-tier badge anchored in the right-hand slot. */
private val BadgeSize = 40.dp

/** Glyph size inside the terminal-tier badge. */
private val StampSize = 18.dp

/**
 * Height floor for every text slot.
 *
 * Four slots are ALWAYS composed, whatever the state, so the left column's
 * height is a constant and [Arrangement.Center] resolves to one fixed offset.
 * This is the whole anti-wobble mechanism. The floor is a minimum rather than a
 * fixed height so a large accessibility font scale grows the block instead of
 * clipping it.
 */
private val LineSlotMinHeight = 16.dp

/** Width of the progress arc stroke. */
private val ArcStrokeWidth = 3.dp

/**
 * Font scale above which the LIVE NOW / NEXT UP header is dropped.
 *
 * Four slots at [LineSlotMinHeight] do not fit the fixed [CardHeight] once text
 * is scaled past roughly 1.3x. Rather than let the shell clip, the header
 * reserves an empty slot and the countdown keeps the space it needs.
 */
private const val MAX_LABEL_FONT_SCALE = 1.3f

private const val FALLBACK_TITLE = "Class"
private const val OFF_DAY_TITLE = "Off Day"
private const val OFF_DAY_SUBTITLE = "No classes scheduled"
private const val DONE_TITLE = "Day Completed"
private const val DONE_SUBTITLE = "All classes finished"
private const val UNAVAILABLE_TITLE = "Times unavailable"
private const val UNAVAILABLE_SUBTITLE = "Check schedule for details"

/**
 * Upcoming/live class countdown card.
 *
 * Design invariants (deliberate, do not "simplify"):
 *
 *  - Fixed [CardHeight] in every state; never `heightIn`.
 *  - One content inset, [ContentInset], matching the list cards. There is
 *    exactly one padding source in the content path so the leading edge cannot
 *    drift out of alignment with the cards below.
 *  - The full-height accent indicator is painted in the same Box as the content
 *    and therefore consumes no layout width, which is what allows the text to sit
 *    flush at [ContentInset] rather than being pushed in by the indicator.
 *  - Four text slots in every state, so left-column height is constant and
 *    [Arrangement.Center] lands on the same offset in all five states.
 *  - The right-hand anchor slot is present in every state at a fixed size, so
 *    the text column's width never changes and nothing can reflow horizontally.
 *  - No AnimatedVisibility and no animators anywhere; content swaps in place.
 */
@Composable
fun ScheduleFocusCard(
    focus: ScheduleFocus,
    secondsRemaining: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight),
        shape = RoundedCornerShape(AppRadius.Medium),
        // Filled tonal surface, no outline. This mirrors the list cards exactly:
        // they are `containerColor = surface` with no border, and the previous
        // 1.dp outline here was the only thing making this card look different.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Flush full-height indicator, drawn in the leading inset. Because
            // it is a sibling of the content rather than a Row child, it takes
            // no width and cannot offset the text axis.
            AccentIndicator(
                focus = focus,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(AccentBarWidth)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = ContentInset, end = ContentInset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailColumn(
                    focus = focus,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(AnchorGap))
                AnchorSlot(
                    focus = focus,
                    secondsRemaining = secondsRemaining
                )
            }
        }
    }
}

/**
 * Full-height leading indicator.
 *
 * Corner-rounded on the leading side only, so it follows the card's own curve
 * instead of painting square corners over it. [MaterialTheme.colorScheme.primary]
 * rather than `primaryContainer`: the container token is a pale tint in light and
 * a near-surface navy in dark, which is exactly where a 4.dp indicator
 * disappears. `primary` is the high-contrast token in both schemes.
 *
 * Purely decorative, so it carries no semantics of its own.
 */
@Composable
private fun AccentIndicator(
    focus: ScheduleFocus,
    modifier: Modifier = Modifier
) {
    val color = if (focus is ScheduleFocus.Live) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = AppRadius.Medium,
                    bottomStart = AppRadius.Medium
                )
            )
            .background(color)
            .clearAndSetSemantics { }
    )
}

/**
 * Left text column.
 *
 * One structure serves all five states: four always-present slots, of which any
 * may be empty. A terminal state is simply a live state with the header and the
 * metadata row left blank, which is why there is no branching on tier height and
 * no state can pull the block off the optical centre.
 *
 * Slot order:
 *  1. status header  (LIVE NOW / NEXT UP; blank when terminal)
 *  2. title          (course title, or terminal title)
 *  3. subtitle       (faculty, or terminal subtitle)
 *  4. metadata       ("Room E-806 • Ends at 2:20 PM"; blank when terminal)
 */
@Composable
private fun DetailColumn(
    focus: ScheduleFocus,
    modifier: Modifier = Modifier
) {
    val entry = when (focus) {
        is ScheduleFocus.Live -> focus.entry
        is ScheduleFocus.Upcoming -> focus.entry
        ScheduleFocus.OffDay -> null
        ScheduleFocus.Done -> null
        ScheduleFocus.Unavailable -> null
    }
    val isTerminal = focus is ScheduleFocus.OffDay ||
        focus is ScheduleFocus.Done ||
        focus is ScheduleFocus.Unavailable
    val isLive = focus is ScheduleFocus.Live

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        TextLineSlot(
            text = if (isTerminal) "" else statusLabel(focus),
            style = MaterialTheme.typography.labelMedium,
            color = if (isLive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.Bold
        )

        TextLineSlot(
            text = if (isTerminal) {
                terminalTitle(focus)
            } else {
                entry?.courseTitle?.takeIf { it.isNotBlank() }
                    ?: entry?.courseCode?.takeIf { it.isNotBlank() }
                    ?: FALLBACK_TITLE
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        TextLineSlot(
            text = if (isTerminal) {
                terminalSubtitle(focus)
            } else {
                // The room moved to the metadata row below; faculty takes this
                // slot for BOTH live and standby.
                entry?.faculty?.takeIf { it.isNotBlank() }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TextLineSlot(
            text = when (focus) {
                is ScheduleFocus.Live ->
                    formatLiveMetadata(entry?.location, focus.endMinuteOfDay)
                is ScheduleFocus.Upcoming ->
                    formatUpcomingMetadata(entry?.location, focus.startMinuteOfDay)
                else -> null
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One fixed-floor line of the left column.
 *
 * A null or blank [text] renders a reserved empty box of the same minimum
 * height instead of nothing at all, so a value appearing or disappearing
 * mid-session cannot re-centre the stack. The empty box is stripped from the
 * accessibility tree so TalkBack does not announce a phantom row.
 */
@Composable
private fun TextLineSlot(
    text: String?,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight? = null
) {
    val content = text?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LineSlotMinHeight)
            .then(
                if (content == null) Modifier.clearAndSetSemantics { } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (content != null) {
            Text(
                text = content,
                style = style,
                fontWeight = fontWeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

/**
 * Right-hand anchor: the countdown ring while a class is live or pending, a
 * solid status badge once the day is over.
 *
 * Always composed at exactly [TickerWidth] x [RingSize], in every state, which
 * is what guarantees the left column's width never changes and nothing reflows
 * horizontally on a tier change.
 *
 * The arcs are plain Canvas draws with no animator, so the slot does no work
 * between the 1 Hz text ticks. No CircularProgressIndicator: it animates
 * continuously and would tick at a different rhythm from the countdown beside it.
 */
@Composable
private fun AnchorSlot(
    focus: ScheduleFocus,
    secondsRemaining: Int
) {
    val isTerminal = focus is ScheduleFocus.OffDay ||
        focus is ScheduleFocus.Done ||
        focus is ScheduleFocus.Unavailable

    // Read theme colors OUTSIDE the Canvas draw lambda: the draw block is a
    // DrawScope, not a @Composable context, so a MaterialTheme read inside it
    // will not compile.
    val trackColor = MaterialTheme.colorScheme.outline
    val progressColor = if (focus is ScheduleFocus.Live) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .requiredWidth(TickerWidth)
            .height(RingSize),
        contentAlignment = Alignment.Center
    ) {
        if (isTerminal) {
            // Terminal badge: a filled tonal disc rather than a bare glyph, so
            // the right end carries real visual weight opposite the text block
            // instead of trailing off into empty space.
            Box(
                modifier = Modifier
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = terminalStampIcon(focus),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(StampSize)
                )
            }
        } else {
            Box(
                modifier = Modifier.size(RingSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = ArcStrokeWidth.toPx()
                    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        // `outline`, not `outlineVariant`: this scheme never
                        // defines outlineVariant, so it was falling back to the
                        // Material default purple-grey against a blue palette.
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * anchorProgress(focus, secondsRemaining),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }

                Text(
                    text = tickerText(focus, secondsRemaining),
                    modifier = Modifier.requiredWidth(TickerWidth),
                    style = MaterialTheme.typography.labelMedium.copy(
                        // Tabular figures: every digit occupies the same advance
                        // width, so the per-second tick cannot shift the glyphs
                        // horizontally inside the frame.
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    }
}

/** Fraction of the class already elapsed, or 0 when there is nothing to show. */
private fun anchorProgress(focus: ScheduleFocus, secondsRemaining: Int): Float = when (focus) {
    is ScheduleFocus.Live -> {
        val total = focus.totalMinutes.coerceAtLeast(1) * 60
        (secondsRemaining.coerceIn(0, total).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    // Standby shows an empty ring; elapsed progress is meaningless before the
    // class starts.
    else -> 0f
}

private fun terminalTitle(focus: ScheduleFocus): String = when (focus) {
    ScheduleFocus.OffDay -> OFF_DAY_TITLE
    ScheduleFocus.Done -> DONE_TITLE
    ScheduleFocus.Unavailable -> UNAVAILABLE_TITLE
    else -> ""
}

/** Decorative glyph for the terminal-tier badge. */
private fun terminalStampIcon(focus: ScheduleFocus): ImageVector = when (focus) {
    ScheduleFocus.Done -> Icons.Rounded.DoneAll
    ScheduleFocus.Unavailable -> Icons.Rounded.EventBusy
    else -> Icons.Rounded.CalendarToday
}

private fun terminalSubtitle(focus: ScheduleFocus): String = when (focus) {
    ScheduleFocus.OffDay -> OFF_DAY_SUBTITLE
    ScheduleFocus.Done -> DONE_SUBTITLE
    ScheduleFocus.Unavailable -> UNAVAILABLE_SUBTITLE
    else -> ""
}

/**
 * Live/standby tier label.
 *
 * Backs off above [MAX_LABEL_FONT_SCALE], where the fixed [CardHeight] can no
 * longer hold four slots. Returns empty so the slot is reserved rather than the
 * card growing.
 */
@Composable
private fun statusLabel(focus: ScheduleFocus): String {
    val label = when (focus) {
        is ScheduleFocus.Live -> "LIVE NOW"
        is ScheduleFocus.Upcoming -> "NEXT UP"
        ScheduleFocus.OffDay -> ""
        ScheduleFocus.Done -> ""
        ScheduleFocus.Unavailable -> ""
    }
    return if (label.isEmpty() || LocalDensity.current.fontScale <= MAX_LABEL_FONT_SCALE) {
        label
    } else {
        ""
    }
}

private fun tickerText(focus: ScheduleFocus, secondsRemaining: Int): String = when (focus) {
    is ScheduleFocus.Live -> formatRemainingClock(secondsRemaining, focus.totalMinutes)
    is ScheduleFocus.Upcoming -> formatStartsIn(focus.startsInMinutes)
    ScheduleFocus.OffDay -> NO_COUNTDOWN
    ScheduleFocus.Done -> NO_COUNTDOWN
    ScheduleFocus.Unavailable -> NO_COUNTDOWN
}