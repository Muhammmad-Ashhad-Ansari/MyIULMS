package com.example.myiulms.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.myiulms.MainActivity
import com.example.myiulms.R
import com.example.myiulms.appContainer
import com.example.myiulms.ui.theme.DarkColors
import com.example.myiulms.ui.theme.LightColors
import kotlinx.coroutines.flow.MutableStateFlow

/** Intent extra MainActivity can read to open a specific tab; Schedule is tab 0. */
const val EXTRA_OPEN_TAB = "com.example.myiulms.extra.OPEN_TAB"
const val SCHEDULE_TAB = 0

// SizeMode.Exact: one structure that reacts to the real size. Below this height
// the widget is the hero alone; at or above it the timeline appears under it.
private val TimelineMinHeight = 130.dp

// The hero's accent bar matches its two text lines; a fixed height is reliable
// in RemoteViews, where a child filling a wrap-content row is not.
private val HeroBarHeight = 40.dp

/**
 * In-memory refresh signal. A running Glance session does not re-run
 * provideGlance on update(), so the content reads this tick and recomputes the
 * state whenever the sign-in state or the cached schedule changes.
 */
internal object NextClassRefresh {
    val ticks = MutableStateFlow(0)
}

/**
 * "Up Next" timeline: the current or next class as a hero, then the rest of the
 * day as a scrollable list. With nothing left today, the list previews the next
 * day that has classes. No background refresh: it updates on system widget
 * updates and whenever the app saves the schedule.
 */
class NextClassWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val session = NextClassWidgetSession.signedIn(context)
        // Reads the encrypted cache only: no login, no network, no ViewModel.
        val repository = context.appContainer.portal
        val initialSchedule = repository.cachedWeeklySchedule()
        provideContent {
            val signedIn by session.collectAsState()
            val tick by NextClassRefresh.ticks.collectAsState()
            val nowMillis = remember(tick) { System.currentTimeMillis() }
            // Re-read on every refresh tick and sign-in change, so a new save or
            // a wipe shows up without restarting the session.
            val cached by produceState(initialSchedule, tick, signedIn) {
                value = repository.cachedWeeklySchedule()
            }
            val state = if (signedIn) {
                widgetStateFor(cached?.payload?.entries, nowMillis)
            } else {
                NextClassUiState.SignedOut
            }
            GlanceTheme(colors = widgetColors()) {
                NextClassContent(state)
            }
        }
    }
}

// Wallpaper colours on Android 12+; below that the app's own palette instead of
// Glance's purple baseline.
@Composable
private fun widgetColors(): androidx.glance.color.ColorProviders =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        ColorProviders(light = LightColors, dark = DarkColors)
    }

// Translucent wallpaper-neutral scrim (res/color*/widget_scrim.xml, ~60%), so
// the widget blends with the wallpaper but text keeps a readable base. Below
// Android 12 cornerRadius has no effect, so a tinted drawable gives the shape.
private fun GlanceModifier.translucentSurface(): GlanceModifier {
    val scrim = ColorProvider(R.color.widget_scrim)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(scrim).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        background(ImageProvider(R.drawable.widget_background_fallback), colorFilter = ColorFilter.tint(scrim))
    }
}

private fun GlanceModifier.pill(color: ColorProvider): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(color).cornerRadius(2.dp)
    } else {
        background(ImageProvider(R.drawable.widget_badge_fallback), colorFilter = ColorFilter.tint(color))
    }

@Composable
private fun NextClassContent(state: NextClassUiState) {
    // CLEAR_TOP | SINGLE_TOP: a running MainActivity receives onNewIntent instead
    // of a second copy stacking up; anything open above it (an evaluation, a
    // voucher) is closed so the schedule is actually what the student sees.
    val openSchedule = actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, SCHEDULE_TAB)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    )
    val showTimeline = LocalSize.current.height >= TimelineMinHeight
    val timeline = timelineFor(state)
    val hasTimeline = showTimeline && timeline.isNotEmpty()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .translucentSurface()
            .clickable(openSchedule)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        // A lone hero centres; with a timeline it anchors to the top.
        verticalAlignment = if (hasTimeline) Alignment.Top else Alignment.CenterVertically
    ) {
        Hero(heroFor(state, compact = !showTimeline), titleSize = if (showTimeline) 18.sp else 16.sp)
        if (hasTimeline) {
            Spacer(GlanceModifier.height(12.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline),
                content = {}
            )
            Spacer(GlanceModifier.height(4.dp))
            // Scrolls natively when the day outgrows the widget. Rows carry their
            // own click: taps inside a list never reach the parent.
            LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(timeline) { item -> TimelineRow(item, openSchedule) }
            }
        }
    }
}

private fun timelineFor(state: NextClassUiState): List<TimelineClass> = when (state) {
    is NextClassUiState.Today -> state.later
    is NextClassUiState.NoClassesLeft -> state.nextDay?.classes.orEmpty()
    else -> emptyList()
}

/** Everything the hero row prints; one shape for every state. */
private class HeroContent(
    val title: String,
    val tag: String?,
    val tagColor: ColorProvider?,
    val meta: String,
    val bar: ColorProvider
)

@Composable
private fun heroFor(state: NextClassUiState, compact: Boolean): HeroContent {
    val colors = GlanceTheme.colors
    return when (state) {
        is NextClassUiState.Today -> {
            val hero = state.hero
            val room = hero.room?.let { " · $it" }.orEmpty()
            if (state.live) {
                HeroContent(hero.course, "NOW", colors.tertiary, "until ${hero.end}$room", colors.tertiary)
            } else {
                HeroContent(hero.course, "NEXT", colors.primary, "${hero.start} – ${hero.end}$room", colors.primary)
            }
        }
        is NextClassUiState.NoClassesLeft -> {
            val title = if (state.finishedToday) "Done for today" else "No classes today"
            val next = state.nextDay
            val meta = when {
                next == null -> "No classes this week"
                // Hero only: fold the first class of the next day into one line.
                compact -> next.classes.first().let { "Next: ${next.shortName} · ${it.start} ${it.course}" }
                // The list below shows that day's classes.
                else -> "Next: ${next.name}"
            }
            HeroContent(title, null, null, meta, colors.outline)
        }
        is NextClassUiState.NoSchedule -> if (state.timesUnreadable) {
            HeroContent("Class times unavailable", null, null, "Open MyIULMS to check today", colors.outline)
        } else {
            HeroContent("No schedule yet", null, null, "Open MyIULMS to load it", colors.outline)
        }
        // The tap still opens the app, which lands on the sign-in screen.
        NextClassUiState.SignedOut ->
            HeroContent("Signed out", null, null, "Open MyIULMS to sign in", colors.outline)
    }
}

@Composable
private fun Hero(content: HeroContent, titleSize: TextUnit) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = GlanceModifier.width(4.dp).height(HeroBarHeight).pill(content.bar), content = {})
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = content.title,
                style = TextStyle(color = colors.onSurface, fontSize = titleSize, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content.tag != null && content.tagColor != null) {
                    Text(
                        text = content.tag,
                        style = TextStyle(color = content.tagColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    text = content.meta,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 1
                )
            }
        }
    }
}

// Quieter than the hero: time in the accent, course and room in onSurfaceVariant.
// The 16dp start inset lines the text up with the hero's text, not its bar.
@Composable
private fun TimelineRow(item: TimelineClass, openSchedule: Action) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openSchedule)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.start,
            style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.width(66.dp),
            maxLines = 1
        )
        Text(
            text = item.course,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1
        )
        if (item.room != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = item.room,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1
            )
        }
    }
}

private val PreviewDatabases = TimelineClass("DATABASE SYSTEMS", "E-610", "11:35 AM", "12:50 PM")
private val PreviewLater = listOf(
    TimelineClass("ARTIFICIAL INTELLIGENCE", "E-705", "1:00 PM", "2:20 PM"),
    TimelineClass("SOFTWARE ENGINEERING", "E-803", "2:30 PM", "3:50 PM")
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 190)
@Composable
private fun TimelinePreview() {
    GlanceTheme {
        NextClassContent(NextClassUiState.Today(PreviewDatabases, live = false, later = PreviewLater))
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 190)
@Composable
private fun LivePreview() {
    GlanceTheme {
        NextClassContent(NextClassUiState.Today(PreviewDatabases, live = true, later = PreviewLater))
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 190)
@Composable
private fun NoClassesPreview() {
    GlanceTheme {
        NextClassContent(
            NextClassUiState.NoClassesLeft(
                finishedToday = false,
                nextDay = UpcomingDay("Tomorrow", "Tomorrow", listOf(PreviewDatabases) + PreviewLater)
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 80)
@Composable
private fun CompactPreview() {
    GlanceTheme {
        NextClassContent(NextClassUiState.Today(PreviewDatabases, live = false, later = PreviewLater))
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 80)
@Composable
private fun CompactNoClassesPreview() {
    GlanceTheme {
        NextClassContent(
            NextClassUiState.NoClassesLeft(
                finishedToday = true,
                nextDay = UpcomingDay("Wednesday", "Wed", listOf(PreviewDatabases))
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 190)
@Composable
private fun SignedOutPreview() {
    GlanceTheme {
        NextClassContent(NextClassUiState.SignedOut)
    }
}
