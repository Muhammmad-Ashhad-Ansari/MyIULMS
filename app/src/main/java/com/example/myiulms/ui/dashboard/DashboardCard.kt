package com.example.myiulms.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing

/**
 * Fixed geometry. These are hard constants so the card cannot reflow between
 * states, which is what prevents the "wobble" on state change.
 */
private val CardHeight = 92.dp
private val RingSize = 56.dp

/** Fixed frame for the ticker so a changing string cannot reflow its neighbours. */
private val TickerWidth = 72.dp

/**
 * Height of the reserved third line, kept in one place so it cannot drift.
 */
private val ReservedLineHeight = 16.dp

/** Decorative accent bar for the terminal (off-day / done) tier. */
private val AccentBarWidth = 4.dp
private val AccentBarHeight = 36.dp
private val IconSize = 18.dp

/** Ultra-soft decorative stamp shown in the ring slot for terminal states. */
private val StampSize = 26.dp
private const val StampAlpha = 0.15f

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
 * Layout invariants (deliberate, do not "simplify"):
 *  - Fixed [CardHeight]; never heightIn.
 *  - The ring slot stays physically present in every state, so horizontal
 *    geometry never changes. Terminal states keep the same transparent arcs and
 *    swap the ticker for a low-contrast decorative stamp.
 *  - Content is vertically centred; terminal states add no leading spacer.
 *  - No AnimatedVisibility anywhere; content swaps in place.
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailColumn(
                focus = focus,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppSpacing.Md))
            TimerRing(
                focus = focus,
                secondsRemaining = secondsRemaining,
                modifier = Modifier.size(RingSize)
            )
        }
    }
}

/**
 * Left area.
 *
 * Two tiers, vertically centred with [Arrangement.Center]:
 *  - Live/standby: a status header line plus a two-line class summary.
 *  - Terminal (off-day / done / times unavailable): a left-aligned accent-bar,
 *    off-state icon and two-line typography stack, with NO leading spacer, so
 *    the visible text sits on the true vertical centre of the 92.dp card.
 *
 * The card frame is hard-bounded at 92.dp, so the differing content heights
 * cannot move the card or shift the 56.dp ring slot; only the text's position
 * within the frame changes, which is the intended visual fix.
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
    val isLive = focus is ScheduleFocus.Live
    val isTerminal = focus is ScheduleFocus.OffDay ||
        focus is ScheduleFocus.Done ||
        focus is ScheduleFocus.Unavailable

    Column(
        modifier = modifier.padding(start = AppSpacing.Lg, end = AppSpacing.Md),
        verticalArrangement = Arrangement.Center
    ) {
        // Live/standby show a status header line. Terminal states deliberately
        // emit NOTHING here: a reserved spacer would bias the visible text away
        // from the true vertical centre of the card. With Arrangement.Center and
        // no leading offset, the terminal block splits the leftover space 50/50
        // above and below itself.
        if (!isTerminal) {
            Text(
                text = statusLabel(focus),
                style = MaterialTheme.typography.labelMedium,
                color = if (isLive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isTerminal) {
            TerminalFocusBlock(
                title = terminalTitle(focus),
                subtitle = terminalSubtitle(focus)
            )
        } else {
            LiveFocusBlock(
                title = entry?.courseTitle?.takeIf { it.isNotBlank() }
                    ?: entry?.courseCode?.takeIf { it.isNotBlank() }
                    ?: FALLBACK_TITLE,
                subtitle = entry?.location?.takeIf { it.isNotBlank() }.takeIf { isLive }
            )
        }
    }
}

/**
 * Premium terminal tier: thin accent bar, off-state icon, and a two-line
 * typography stack. Height-matched to [LiveFocusBlock].
 */
@Composable
private fun TerminalFocusBlock(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decorative accent bar.
        Box(
            modifier = Modifier
                .width(AccentBarWidth)
                .height(AccentBarHeight)
                .clip(RoundedCornerShape(AccentBarWidth / 2))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { }
        )
        Spacer(Modifier.width(AppSpacing.Md))
        Icon(
            imageVector = Icons.Rounded.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize)
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Live/standby tier: two left-aligned text slots. */
@Composable
private fun LiveFocusBlock(
    title: String,
    subtitle: String?
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Spacer(
            modifier = Modifier
                .height(ReservedLineHeight)
                .clearAndSetSemantics { }
        )
    }
}

private fun terminalTitle(focus: ScheduleFocus): String = when (focus) {
    ScheduleFocus.OffDay -> OFF_DAY_TITLE
    ScheduleFocus.Done -> DONE_TITLE
    ScheduleFocus.Unavailable -> UNAVAILABLE_TITLE
    else -> ""
}

/** Decorative glyph stamped into the otherwise-blank ring slot. */
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

private fun statusLabel(focus: ScheduleFocus): String = when (focus) {
    is ScheduleFocus.Live -> "LIVE NOW"
    is ScheduleFocus.Upcoming -> "NEXT UP"
    ScheduleFocus.OffDay -> ""
    ScheduleFocus.Done -> ""
    ScheduleFocus.Unavailable -> ""
}

/**
 * Right area: static dual-arc ring plus a fixed-width ticker.
 *
 * The 56dp Box and its preceding spacer are ALWAYS composed, in every state,
 * including terminal ones. For off-day/done/unavailable the arcs are drawn
 * fully transparent and the ticker becomes an invisible placeholder. Keeping
 * the node in the tree is what guarantees zero horizontal shift — the left
 * column never changes width, so there is nothing to reflow.
 *
 * The arcs are plain Canvas draws with no animator, so the widget performs no
 * work between the 1 Hz text ticks. No CircularProgressIndicator is used
 * deliberately: it animates continuously and would run at a different rhythm
 * from the once-per-second countdown beside it.
 */
@Composable
private fun TimerRing(
    focus: ScheduleFocus,
    secondsRemaining: Int,
    modifier: Modifier = Modifier
) {
    val isTerminal = focus is ScheduleFocus.OffDay ||
        focus is ScheduleFocus.Done ||
        focus is ScheduleFocus.Unavailable

    val trackColor = if (isTerminal) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val progressColor = when {
        isTerminal -> Color.Transparent
        focus is ScheduleFocus.Live -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val progress = when {
        isTerminal -> 0f
        focus is ScheduleFocus.Live -> {
            val total = focus.totalMinutes.coerceAtLeast(1) * 60
            (secondsRemaining.coerceIn(0, total).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        // Standby shows an empty ring; elapsed progress is meaningless
        // before the class starts.
        else -> 0f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawArc(
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
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }

        // Terminal states keep the exact same 56.dp slot but swap the ticker for
        // an ultra-soft decorative stamp, so the right-hand corner carries a
        // visual mark instead of dead space. Geometry is untouched, so the left
        // column's width never changes.
        if (isTerminal) {
            Icon(
                imageVector = terminalStampIcon(focus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = StampAlpha),
                modifier = Modifier.size(StampSize)
            )
        } else {
            Text(
                text = tickerText(focus, secondsRemaining),
                modifier = Modifier.width(TickerWidth),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun tickerText(focus: ScheduleFocus, secondsRemaining: Int): String = when (focus) {
    is ScheduleFocus.Live -> formatRemainingClock(secondsRemaining, focus.totalMinutes)
    is ScheduleFocus.Upcoming -> formatStartsIn(focus.startsInMinutes)
    ScheduleFocus.OffDay -> NO_COUNTDOWN
    ScheduleFocus.Done -> NO_COUNTDOWN
    ScheduleFocus.Unavailable -> NO_COUNTDOWN
}
