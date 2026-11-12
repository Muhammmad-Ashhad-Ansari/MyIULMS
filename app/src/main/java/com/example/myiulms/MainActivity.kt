package com.example.myiulms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.animation.doOnEnd
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myiulms.ui.theme.WarningForegroundLight
import com.example.myiulms.ui.theme.WarningForegroundDark
import com.example.myiulms.ui.theme.GradeCriticalLight
import com.example.myiulms.ui.theme.GradeCriticalDark
import com.example.myiulms.ui.theme.GradeStrongLight
import com.example.myiulms.ui.theme.GradeStrongDark
import com.example.myiulms.ui.policy.AcademicPolicy
import com.example.myiulms.domain.attendance.SEMESTER_WEEKS
import com.example.myiulms.domain.attendance.attendanceRiskLabel
import com.example.myiulms.domain.attendance.assessAttendance
import com.example.myiulms.domain.attendance.AttendanceRisk
import com.example.myiulms.domain.attendance.AttendanceLevel
import com.example.myiulms.domain.attendance.edpCodeOf
import com.example.myiulms.ui.components.InfoIconButton
import com.example.myiulms.ui.components.MyIulmsDropdownMenu
import com.example.myiulms.ui.components.MyIulmsPullToRefresh
import com.example.myiulms.ui.components.rememberAppHaptics
import com.example.myiulms.ui.login.LoginScreen
import com.example.myiulms.ui.login.PortalVerificationHost
import com.example.myiulms.ui.components.rememberRevealedProgress
import com.example.myiulms.ui.components.MyIulmsDropdownMenuDivider
import com.example.myiulms.ui.components.MyIulmsDropdownMenuHeader
import com.example.myiulms.ui.components.MyIulmsDropdownMenuItem
import com.example.myiulms.ui.dashboard.CampusZone
import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.ScheduleFocusCard
import com.example.myiulms.ui.dashboard.groupScheduleByDay
import com.example.myiulms.ui.dashboard.liveSecondsRemaining
import com.example.myiulms.ui.dashboard.minuteOfDay
import com.example.myiulms.ui.dashboard.nextFocusTickDelayMillis
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.resolveScheduleFocus
import com.example.myiulms.ui.dashboard.scheduleFreshnessNote
import com.example.myiulms.ui.dashboard.ScheduleTips
import com.example.myiulms.alarms.ReminderSettings
import com.example.myiulms.alarms.REMINDER_LEAD_OPTIONS
import com.example.myiulms.ui.widget.NextClassWidgetSession
import com.example.myiulms.ui.dashboard.todayAbbrev
import com.example.myiulms.ui.policy.AcademicPolicyHost
import com.example.myiulms.ui.policy.LocalAcademicPolicyState
import com.example.myiulms.ui.policy.PolicyInfoButton
import com.example.myiulms.ui.policy.PolicyOverflowItem
import com.example.myiulms.ui.widget.EXTRA_OPEN_TAB
import com.example.myiulms.ui.policy.rememberAcademicPolicyState
import com.example.myiulms.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/**
 * A request from outside the app (the home-screen widget) to show one tab.
 * Compared by identity, so tapping the widget twice is two requests even when
 * both ask for the same tab.
 */
class TabRequest(val tab: Int)

private const val SPLASH_EXIT_MILLIS = 300L
private const val SPLASH_ICON_END_SCALE = 0.85f

class MainActivity : ComponentActivity() {
    private var tabRequest by mutableStateOf<TabRequest?>(null)

    // Same instance App() gets from viewModel(): both use this activity's store.
    private val vm: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps Theme.MyIULMS.Starting for the app theme.
        // The system splash stays up until the remembered login has been read on
        // IO, so the first thing drawn is the right screen, not a blank frame.
        installSplashScreen().apply {
            setKeepOnScreenCondition { vm.restoringSession }
            setOnExitAnimationListener(::animateSplashExit)
        }
        super.onCreate(savedInstanceState)
        // Bars follow the in-app theme: set before the first frame, then on every toggle.
        applySystemBars(resolveSavedDarkTheme(this))
        // Cold start. Skipped on recreation (rotation, process restore), where
        // the launch intent still carries the extra but the student may since
        // have moved to another tab.
        if (savedInstanceState == null) consumeTabRequest(intent)
        // Launch: re-arm the schedule alarm in case it was lost (force-stop clears it).
        NextClassWidgetSession.rescheduleAlarms(this)


        setContent {
            // Observed, not copied: the theme lives in ThemeSettings, outside
            // this activity, so it can never be restored stale.
            val savedDark by ThemeSettings.savedDark(this).collectAsState()
            val darkTheme = savedDark ?: isSystemInDarkTheme()
            LaunchedEffect(darkTheme) { applySystemBars(darkTheme) }

            MyIULMSTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        darkTheme = darkTheme,
                        onThemeToggle = {
                            ThemeSettings.setDark(this, !darkTheme)
                        },
                        tabRequest = tabRequest,
                        onTabRequestHandled = { tabRequest = null }
                    )
                }
            }
        }
    }

    /**
     * Splash exit: the icon eases down to 85% while the whole splash fades out,
     * revealing the already-drawn schedule beneath. 300 ms, accelerating, so it
     * starts gently and gets out of the way. Animators follow the system
     * animation scale, so "Remove animations" makes this instant.
     */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        val splash = provider.view
        val animators = mutableListOf<Animator>(ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f))
        // Shell- or system-started launches can show an icon-less splash.
        runCatching { provider.iconView }.getOrNull()?.let { icon ->
            animators += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, SPLASH_ICON_END_SCALE)
            animators += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, SPLASH_ICON_END_SCALE)
        }
        AnimatorSet().apply {
            playTogether(animators)
            duration = SPLASH_EXIT_MILLIS
            interpolator = AccelerateInterpolator()
            doOnEnd { provider.remove() }
            start()
        }
    }

    // Warm start: the widget's CLEAR_TOP | SINGLE_TOP intent lands here when
    // this activity is already running.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTabRequest(intent)
    }

    private fun consumeTabRequest(intent: Intent) {
        val tab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        if (tab !in 0 until TAB_COUNT) return
        tabRequest = TabRequest(tab)
        intent.removeExtra(EXTRA_OPEN_TAB)
    }
}

@Composable
fun App(
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    tabRequest: TabRequest? = null,
    onTabRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    if (vm.restoringSession) {
        // A frame or two while the remembered login is read off the main
        // thread: the theme background, which matches the system splash.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else if (vm.showHome) {
        // A request made while signed out waits here until the home screen exists.
        HomeScreen(vm, darkTheme, onThemeToggle, tabRequest, onTabRequestHandled)
    } else {
        LoginScreen(vm, darkTheme, onThemeToggle, onCheckForUpdates = vm::checkForUpdates)
    }

    // Root-level so it also covers a challenge that arrives mid-session, when
    // the sign-in screen (its previous host) is not composed.
    PortalVerificationHost(vm)

    // Trigger automatic update check after user enters a real portal/session
    LaunchedEffect(key1 = vm.loggedIn) {
        if (vm.loggedIn) {
            vm.checkAutomaticUpdate()
        }
    }

    vm.updateCheckState?.let { state ->
        UpdateCheckDialog(
            state = state,
            onDismiss = vm::dismissUpdateCheck,
            onRetry = vm::checkForUpdates,
            onOpenRelease = { releaseUrl ->
                vm.dismissUpdateCheck()
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                }.onFailure {
                    Toast.makeText(context, "Couldn't open the release page", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// Number of top-level tabs: Schedule, Attendance, Result, Transcript, Vouchers.
private const val TAB_COUNT = 5
private const val SCHEDULE_PAGE = 0

// Results and Transcript lists: heading is item 0, the GPA card item 1.
private const val RESULTS_PAGE = 2
private const val TRANSCRIPT_PAGE = 3
private const val GPA_HERO_INDEX = 1

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    tabRequest: TabRequest?,
    onTabRequestHandled: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0) { TAB_COUNT }
    // The pager is the single source of truth. `settledPage` only changes after
    // a swipe or tap has settled, so loading, refresh and error state always
    // match the screen the user actually ended up on.
    val settledPage = pagerState.settledPage
    // Presentation only: the pill follows the page nearest the snap point while
    // an animation is running, but it never drives loading or actions.
    val selectedPage = pagerState.currentPage

    val useCompactNavLabels = LocalDensity.current.fontScale >= 1.3f
    val showNavLabels = LocalDensity.current.fontScale < 1.4f
    val screenState = vm.screenStateFor(settledPage)
    val hasData = when (settledPage) {
        // Either half counts: a saved weekly schedule is real data on screen.
        0 -> vm.examSchedule != null || vm.weeklySchedule != null
        1 -> vm.attendance != null
        2 -> vm.examResult != null
        3 -> vm.transcript != null
        else -> vm.vouchers != null
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val pageScope = rememberCoroutineScope()

    // One refresh indicator app-wide: the pull spinner, shown only for a
    // refresh the student asked for (a pull, or the toolbar button). Background
    // syncs stay quiet. One flag per tab, cleared when that tab stops loading.
    val manualPulls = remember { List(TAB_COUNT) { ManualPull() } }
    for (page in 0 until TAB_COUNT) {
        val loading = vm.screenStateFor(page).loading
        LaunchedEffect(page, loading) {
            if (!loading) manualPulls[page].pulled = false
        }
    }

    // Results and Transcript scroll positions, hoisted so the shared top bar
    // can see them. Once the GPA card (item 1) has scrolled completely off the
    // screen, the top bar swaps the student's name for the GPA. derivedStateOf:
    // the top bar recomposes only when that answer flips, not on every pixel.
    val resultsListState = rememberLazyListState()
    val transcriptListState = rememberLazyListState()
    val gpaTitle by remember {
        derivedStateOf {
            when (pagerState.settledPage) {
                RESULTS_PAGE -> vm.examResult?.gpa?.takeIf {
                    it.isNotBlank() && resultsListState.firstVisibleItemIndex > GPA_HERO_INDEX
                }?.let { "GPA: $it" }
                TRANSCRIPT_PAGE -> vm.transcript?.cgpa?.takeIf {
                    it.isNotBlank() && transcriptListState.firstVisibleItemIndex > GPA_HERO_INDEX
                }?.let { "CGPA: $it" }
                else -> null
            }
        }
    }

    // The one way to change tabs, shared by the nav capsule and Back. Adjacent
    // tabs keep the short one-page slide: at a distance of one the pager cannot
    // pre-jump, so the animation is a single clean leg that matches a swipe.
    // Non-adjacent moves snap instantly instead, because `animateScrollToPage`
    // pre-jumps to one viewport away whenever the target is three or more pages
    // off (foundation's `MaxPagesForAnimateScroll`), which briefly lands on, and
    // composes, an unintended intermediate tab. `scrollToPage` goes straight to
    // the target; the pill keeps animating on its own weight springs.
    fun goToPage(index: Int) {
        pageScope.launch {
            if (abs(index - pagerState.currentPage) <= 1) {
                pagerState.animateScrollToPage(index)
            } else {
                pagerState.scrollToPage(index)
            }
        }
    }

    // Back from any other tab returns to Schedule first; from Schedule the
    // system closes the app as usual.
    //
    // Predictive back (Android 14+): while the back swipe is held, the system
    // streams BackEventCompat.progress (0..1). It is stored in [backProgress]
    // and read only inside the pager's graphicsLayer, so the gesture never
    // recomposes the tabs. The current tab shrinks to 90%, rounds its corners
    // and drifts away from the swiping edge, with a live Schedule preview
    // composed behind it for the duration of the gesture only.
    //   - Swipe finished: the flow completes; the pager jumps to Schedule
    //     (which is what was already showing behind) and the transform resets.
    //   - Swipe cancelled: the flow throws CancellationException; progress
    //     springs back to 0 on a separate scope, then the preview is removed.
    // Below Android 14 (or for a plain back press) the flow completes with no
    // progress events, so this is an ordinary Back: goToPage slides one tab
    // or jumps straight home from distant tabs, never through the ones between.
    // A second press while the slide from Attendance is still running is
    // consumed, not re-run.
    val backProgress = remember { Animatable(0f) }
    var backPreviewVisible by remember { mutableStateOf(false) }
    var backFromLeftEdge by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = settledPage != SCHEDULE_PAGE) { events ->
        if (pagerState.isScrollInProgress && pagerState.targetPage == SCHEDULE_PAGE) {
            events.collect { }
            return@PredictiveBackHandler
        }
        var tracked = false
        try {
            events.collect { event ->
                if (!tracked) {
                    tracked = true
                    backPreviewVisible = true
                }
                backFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                backProgress.snapTo(event.progress)
            }
            if (tracked) {
                // Schedule is already on screen behind the shrunken tab.
                pagerState.scrollToPage(SCHEDULE_PAGE)
                backProgress.snapTo(0f)
                backPreviewVisible = false
            } else {
                goToPage(SCHEDULE_PAGE)
            }
        } catch (cancelled: CancellationException) {
            pageScope.launch {
                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                backPreviewVisible = false
            }
            throw cancelled
        }
    }
    val saveableStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(vm.lastUpdatedAt) {
        if (vm.lastUpdatedAt != null) {
            snackbarHostState.showSnackbar(
                message = "Updated just now",
                duration = SnackbarDuration.Short
            )
        }
    }

    // External tab request (widget tap). Instant rather than animated: the app is
    // just opening. The pager is the source of truth, so the nav pill, loading
    // and refresh all follow from this one scroll.
    LaunchedEffect(tabRequest) {
        val request = tabRequest ?: return@LaunchedEffect
        pagerState.scrollToPage(request.tab)
        onTabRequestHandled()
    }

    // Portal loads wait for a real sign-in; a cached session shows saved data only.
    LaunchedEffect(settledPage, vm.loggedIn) {
        if (!vm.loggedIn) return@LaunchedEffect
        when (settledPage) {
            0 -> if (vm.examSchedule == null || vm.weeklySchedule == null) vm.loadSchedules()
            1 -> if (vm.attendance == null) vm.loadAttendance()
            2 -> if (vm.examResult == null) vm.loadExamResult()
            3 -> if (vm.transcript == null) vm.loadTranscript()
            4 -> if (vm.vouchers == null) vm.loadVouchers()
        }
    }

    val context = LocalContext.current
    val policyState = rememberAcademicPolicyState()

    // Faculty evaluation gate: 100% native evaluation flow kholta hai —
    // EvaluationActivity khud apni OkHttp session se gate list / course form /
    // CAPTCHA fetch karta hai (WebView koi nahi). Return par EvaluationActivity
    // hamesha RESULT_OK bhejta hai aur system back/cancel case me bhi
    // onEvaluationReturned() re-probe karta hai — portal ka response hi gate
    // state ka single source of truth hai.
    val evaluationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.onEvaluationReturned(settledPage)
    }

    CompositionLocalProvider(
        LocalAcademicPolicyState provides policyState,
        LocalEvaluationGateAction provides {
            evaluationLauncher.launch(Intent(context, EvaluationActivity::class.java))
        }
    ) {
        Scaffold(
            topBar = {
                PortalTopBar(
                    darkTheme = darkTheme,
                    onThemeToggle = onThemeToggle,
                    onRefresh = {
                        manualPulls[settledPage].pulled = true
                        vm.refresh(settledPage)
                    },
                    // Progress shows as the tab's pull spinner; the button only
                    // rests while a load is already running.
                    refreshEnabled = !screenState.loading,
                    onCheckForUpdates = vm::checkForUpdates,
                    onLogout = vm::logout,
                    studentName = vm.studentName,
                    gpaTitle = gpaTitle
                )
            },
            bottomBar = {
                // Floating capsule rather than a full-width NavigationBar.
                // The slot accepts any composable, so Scaffold still reserves the
                // capsule's measured height and the `padding` consumed below stays
                // correct, which is what keeps the last list card clear of it.
                FloatingNavCapsule(
                    selectedIndex = selectedPage,
                    // Drive the pager directly. There is no `tab` state to feed
                    // back into an effect, so a tap can never cancel its own
                    // animation.
                    onSelect = ::goToPage,
                    useCompactLabels = useCompactNavLabels,
                    showLabels = showNavLabels,
                    darkTheme = darkTheme
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep the Scaffold's top and horizontal content padding (status
                    // bar, display cutouts), but do not consume the bottom-bar
                    // padding here: the pager viewport extends behind the floating
                    // capsule so list content can scroll beneath the fade instead of
                    // being clipped at the bar's top edge. Each list reserves the
                    // full bar band exactly once through its own bottom padding.
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = 0.dp
                    )
            ) {
                if (hasData && screenState.error != null) {
                    ErrorBanner(screenState.error, onRetry = { vm.refresh(settledPage) })
                }
                if (settledPage == 4 && vm.voucherActionError != null) {
                    ErrorBanner(
                        message = vm.voucherActionError!!,
                        onRetry = { vm.retryVoucherAction(context) }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    // Live Schedule preview, revealed behind the shrunken tab
                    // while a predictive back swipe is in progress. Fresh UI
                    // state (no saveable provider), so it can never clash with
                    // the real page 0.
                    //
                    // It is composed once and stays composed: the first back
                    // event used to mount a whole screen on the render thread,
                    // which is exactly the frame that dropped. Now that frame
                    // only flips a draw flag (read below in the draw phase) and
                    // the pager's layer. While hidden, drawWithContent skips
                    // every child draw, so idle frames pay no overdraw.
                    var previewWarming by remember { mutableStateOf(true) }
                    // Exactly one drawn frame per data change. While the flag is
                    // up the preview records its display lists behind the opaque
                    // pager; the flag drops again before the next draw, so idle
                    // frames never pay for it. Keyed on the Schedule data (and
                    // its refresh state) because a hidden screen that never draws
                    // would otherwise go stale and charge the first gesture for
                    // the re-record. Two frame awaits, not one: the flag is only
                    // cleared on the *second* frame callback, which guarantees a
                    // draw already happened with it up.
                    LaunchedEffect(
                        vm.examSchedule,
                        vm.weeklySchedule,
                        vm.studentName,
                        vm.screenStateFor(SCHEDULE_PAGE).loading
                    ) {
                        previewWarming = true
                        withFrameNanos { }
                        withFrameNanos { }
                        previewWarming = false
                    }
                    SchedulesScreen(
                        vm.examSchedule,
                        vm.weeklySchedule,
                        null,
                        vm.screenStateFor(SCHEDULE_PAGE),
                        onRetry = {},
                        onPullRefresh = {},
                        manualPull = remember { ManualPull() },
                        snackbarHostState = snackbarHostState,
                        studentName = vm.studentName,
                        preview = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                if (backPreviewVisible || previewWarming) drawContent()
                            }
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = backProgress.value
                                if (p > 0f) {
                                    val scale = 1f - 0.1f * p
                                    scaleX = scale
                                    scaleY = scale
                                    // Drift away from the edge the swipe started on.
                                    translationX = (if (backFromLeftEdge) 1f else -1f) * 16.dp.toPx() * p
                                    shape = RoundedCornerShape(28.dp)
                                    clip = true
                                }
                            }
                            // Opaque, so the preview only shows around the shrunken tab.
                            .background(MaterialTheme.colorScheme.background)
                    ) { page ->
                        val pageState = vm.screenStateFor(page)
                        // Keep each page's UI state (sub-tab, scroll, expanded rows,
                        // filter) without holding every page in composition.
                        saveableStateHolder.SaveableStateProvider(page) {
                            when (page) {
                                0 -> SchedulesScreen(
                                    vm.examSchedule,
                                    vm.weeklySchedule,
                                    scheduleFreshnessNote(
                                        fetchedAtMillis = vm.weeklyScheduleUpdatedAt,
                                        fromCache = vm.weeklyScheduleFromCache,
                                        syncing = pageState.loading,
                                        nowMillis = System.currentTimeMillis()
                                    ),
                                    pageState,
                                    modifier = Modifier.fillMaxSize(),
                                    onRetry = { vm.refresh(page) },
                                    onPullRefresh = { vm.refresh(page, showRefreshFeedback = false) },
                                    manualPull = manualPulls[page],
                                    snackbarHostState = snackbarHostState,
                                    studentName = vm.studentName
                                )
                                1 -> AttendanceScreen(
                                    vm.attendance,
                                    vm.weeklySchedule?.entries,
                                    pageState,
                                    onRetry = { vm.refresh(page) },
                                    onPullRefresh = { vm.refresh(page, showRefreshFeedback = false) },
                                    manualPull = manualPulls[page]
                                )
                                2 -> ResultScreen(
                                    vm.examResult,
                                    vm.studentName,
                                    pageState,
                                    onRetry = { vm.refresh(page) },
                                    onPullRefresh = { vm.refresh(page, showRefreshFeedback = false) },
                                    manualPull = manualPulls[page],
                                    listState = resultsListState
                                )
                                3 -> TranscriptScreen(
                                    vm.transcript,
                                    vm.studentName,
                                    pageState,
                                    onRetry = { vm.refresh(page) },
                                    onPullRefresh = { vm.refresh(page, showRefreshFeedback = false) },
                                    manualPull = manualPulls[page],
                                    listState = transcriptListState
                                )
                                else -> VoucherScreen(
                                    vouchers = vm.vouchers,
                                    downloadingVoucherNumber = vm.downloadingVoucherNumber,
                                    screenState = pageState,
                                    onRetry = { vm.refresh(page) },
                                    onPullRefresh = { vm.refresh(page, showRefreshFeedback = false) },
                                    manualPull = manualPulls[page],
                                    onDownload = { voucher -> vm.openVoucher(context, voucher) }
                                )
                            }
                        }
                    }
                }

            }
        }

        // Single sheet instance for the whole app. Every entry point in the
        // policy package funnels through the same hoisted state.
        AcademicPolicyHost()
    }
}

/**
 * Fixed height envelope for the capsule.
 *
 * Hard-bounded so a tab switch can never resize the Scaffold `bottomBar` slot.
 * The inner row is padded to fit inside it rather than growing past it.
 */
private val CapsuleHeight = 64.dp

/** Inset between the capsule and the left/right screen edges. */
private val CapsuleSideInset = 18.dp

/** Gap between the capsule and the bottom system edge. */
private val CapsuleBottomInset = 12.dp

/** Horizontal padding inside the capsule, either side of the row. */
private val CapsuleHorizontalPadding = 6.dp

/** Gap between capsule edge and the outermost pill. */
private val CapsuleItemSpacing = 2.dp

/** Size of the selection pill behind the active destination. */
private val NavPillHeight = 48.dp

/** Height available to the capsule icon. */
private val NavIconSize = 22.dp

/** Gap between the pill icon and its label. */
private val NavLabelGap = 6.dp

/** Row weight of a collapsed, icon-only destination. */
private const val NAV_WEIGHT_COLLAPSED = 1f

/** Row weight of the expanded, icon-plus-label destination. */
private const val NAV_WEIGHT_EXPANDED = 2.2f

/** Translucent fill of the capsule. High enough to keep text legible. */
private const val CAPSULE_FILL_ALPHA = 0.85f

/** Rim alpha at the capsule's top edge, dark scheme. */
private const val RIM_TOP_ALPHA_DARK = 0.35f

/** Rim alpha at the capsule's bottom edge, dark scheme. */
private const val RIM_BOTTOM_ALPHA_DARK = 0.05f

/** Rim alpha at the capsule's top edge, light scheme. */
private const val RIM_TOP_ALPHA_LIGHT = 0.18f

/** Rim alpha at the capsule's bottom edge, light scheme. */
private const val RIM_BOTTOM_ALPHA_LIGHT = 0.04f

/** Width of the capsule rim stroke. */
private val CapsuleRimWidth = 1.dp

/**
 * Height of the gradient fade drawn above the capsule.
 *
 * Softens the hard cut where list content meets the bar as it scrolls underneath.
 */
private val ScrollFadeHeight = 32.dp

/** Extra visual gap between the last list item and the fade. */
private val ListBottomGap = AppSpacing.Sm

/**
 * Bottom clearance for every authenticated list.
 *
 * The Scaffold no longer reserves the bottom bar for the pager viewport (the list
 * scrolls behind the floating capsule), so each list must reserve the full bar band
 * exactly once here: navigation-bar inset + capsule bottom spacing + capsule
 * height + fade height + a small visual gap. Reserving it only in the lists (and
 * not also via the Scaffold content padding) avoids the previous double-inset gap.
 */
@Composable
private fun listBottomClearance(): Dp {
    val navigationBar = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return navigationBar + CapsuleBottomInset + CapsuleHeight + ScrollFadeHeight + ListBottomGap
}

/**
 * Destinations, in tab order. [NavDestinations.size] is what the pager and the
 * `when (page)` dispatch below rely on, so the two must stay in lockstep.
 */
private val NavDestinations = listOf(
    NavDestination(Icons.Rounded.Event, "Schedule", "Sched.", "Schedule"),
    NavDestination(Icons.AutoMirrored.Rounded.FactCheck, "Attendance", "Attend", "Attendance"),
    NavDestination(Icons.Rounded.Assessment, "Results", "Results", "Results"),
    NavDestination(Icons.Rounded.School, "Transcript", "Transcr.", "Transcript"),
    NavDestination(Icons.AutoMirrored.Rounded.ReceiptLong, "Vouchers", "Voucher", "Vouchers")
)

private data class NavDestination(
    val icon: ImageVector,
    /** Full label, used at normal font scales. */
    val label: String,
    /** Abbreviated label, used once [useCompactLabels] kicks in. */
    val compactLabel: String,
    /**
     * Spoken name. Never abbreviated: TalkBack reads a `contentDescription`
     * verbatim, so "Transcr." would be announced literally.
     */
    val accessibilityLabel: String
)

/**
 * Floating liquid-glass navigation capsule.
 *
 * Frosted appearance without a backdrop blur: a translucent surface fill, a
 * hairline outline and a soft shadow. `Modifier.blur()` / `RenderEffect` are
 * deliberately NOT used — they sample the node's own render layer, not the
 * content behind it, so they would blur the icons rather than the list, and a
 * genuine backdrop blur needs a separate offscreen pass over a region roughly
 * 1080x288 px on every frame the list scrolls. On low-end GPUs that couples two
 * independent invalidation sources on the same frame and drops frames. This is a
 * single translucent fill plus a stroke: no per-frame allocation, no second
 * render target.
 *
 * The navigation-bar inset is consumed here rather than at a hardcoded offset so
 * the capsule clears the gesture handle on gesture-nav devices, where a literal
 * bottom padding would overlap it.
 */
@Composable
private fun FloatingNavCapsule(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    useCompactLabels: Boolean,
    showLabels: Boolean,
    darkTheme: Boolean
) {
    val haptics = rememberAppHaptics()
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CAPSULE_FILL_ALPHA)
    // Dark mode needs a light rim: `surface` is already near-black there, so a
    // dark stroke would be invisible. Light mode needs a dark rim for the same
    // reason in reverse.
    val isDark = darkTheme
    val rimBrush = Brush.verticalGradient(
        if (isDark) {
            listOf(
                Color.White.copy(alpha = RIM_TOP_ALPHA_DARK),
                Color.White.copy(alpha = RIM_BOTTOM_ALPHA_DARK)
            )
        } else {
            val onSurface = MaterialTheme.colorScheme.onSurface
            listOf(
                onSurface.copy(alpha = RIM_TOP_ALPHA_LIGHT),
                onSurface.copy(alpha = RIM_BOTTOM_ALPHA_LIGHT)
            )
        }
    )

    // Tap routing is anchored to the capsule, not to the item that happens to
    // sit under the finger when it lifts. The selected pill's row weight springs
    // on every selection change, so items slide sideways for a few hundred ms;
    // a stationary finger can fall outside the moving node and the item's
    // clickable then cancels its press — on slower frames this reads as "the
    // tab needs two taps". The item's own click consumes its up when it
    // succeeds; when it does not, the capsule resolves the destination from the
    // down coordinates. Exactly one path fires per tap.
    val rowCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val itemCoords = remember { arrayOfNulls<LayoutCoordinates>(NavDestinations.size) }
    val selectedNow = rememberUpdatedState(selectedIndex)
    val selectNow = rememberUpdatedState(onSelect)
    val select: (Int) -> Unit = remember(haptics) {
        { index ->
            // Buzz only when the visible destination changes. The tap itself
            // always reaches the pager so a tap during an animation can still
            // redirect it.
            if (index != selectedNow.value) haptics.tick()
            selectNow.value(index)
        }
    }

    // The root container deliberately has no background: the fade below must
    // composite over the scrolling pager content so its transparent top
    // reveals the list and its lower portion fades to the theme background.
    // The opaque background lives on the capsule band only (on the Surface
    // modifier chain below), where it blocks list content from showing
    // through the capsule's translucent fill, its side gutters, or the
    // system navigation area.
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrollFadeHeight)
                // Consume pointer events in the fade area so they cannot reach
                // list content scrolling underneath the bar band. This uses the
                // resolved PointerInputEventHandler overload of pointerInput: the
                // lambda runs in PointerInputScope, where awaitPointerEventScope
                // exposes AwaitPointerEventScope.awaitPointerEvent. Placed after
                // all layout modifiers so the handler's measured region covers
                // the full band width and height. pointerInput adds no semantics,
                // so no TalkBack action or focus target is introduced.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                change.consume()
                            }
                        }
                    }
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Non-semantic full-width pointer barrier: consume every event
                // in the capsule band so taps and drags cannot fall through to
                // the list scrolling underneath. Uses the resolved
                // PointerInputEventHandler overload of pointerInput — the lambda
                // runs in PointerInputScope, so awaitPointerEventScope exposes
                // AwaitPointerEventScope.awaitPointerEvent. Placed before the
                // padding/inset modifiers below so its measured region spans the
                // full band width including the side gutters (padding applied
                // after pointerInput is inside the handler's region). pointerInput
                // registers no semantics, so no inert TalkBack action is created;
                // NavItem children keep their Role.Tab, label, and selected state
                // and are dispatched before this ancestor in the Main pass.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                change.consume()
                            }
                        }
                    }
                }
                // Opaque theme background for the capsule band. Placed after
                // the pointerInput barrier (so the barrier's hit region still
                // spans the full band) and before the side/inset padding, so
                // the paint wraps the 18dp side gutters, the bottom inset, and
                // the system navigation area. This is what stops list content
                // scrolling behind the band from showing through the capsule's
                // translucent fill or the gutters beside it; the fade above
                // intentionally has no such layer so it reveals the list.
                .background(MaterialTheme.colorScheme.background)
                // Inset the capsule from the screen edges. The barrier above
                // still covers this 18dp strip because it wraps it.
                .padding(start = CapsuleSideInset, end = CapsuleSideInset)
                // Consume the gesture inset, then add it back as padding so the
                // capsule body itself never sits under the home indicator.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = CapsuleBottomInset)
                .height(CapsuleHeight)
                // Gradient rim. `Surface.border` only accepts a solid
                // BorderStroke, so this is the Brush overload of Modifier.border,
                // which takes the vertical gradient directly and handles the
                // rounded outline geometry for us.
                .border(CapsuleRimWidth, rimBrush, CircleShape),
            shape = CircleShape,
            color = containerColor,
            // Explicit elevation: the translucent fill has no tonal tint to
            // carry it, so the shadow is what separates it from the list.
            // Zeroed in dark mode, where a black shadow on a near-black surface
            // is invisible and only costs an extra shadow layer.
            shadowElevation = if (isDark) 0.dp else 8.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rowCoords[0] = it }
                    // Main pass: by the time the capsule sees an event, the
                    // tapped item's own clickable has already had its chance.
                    // If the press was cancelled mid-morph its up arrives
                    // unconsumed, and this handler switches from the down
                    // position so a single tap always lands.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Main
                            )
                            val target = navIndexAt(
                                down.position,
                                rowCoords[0],
                                itemCoords
                            )
                            var tapCancelled = false
                            var upConsumed = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUp()) {
                                    upConsumed = change.isConsumed
                                    break
                                }
                                if (!change.pressed) {
                                    tapCancelled = true
                                    break
                                }
                                if ((change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
                                    tapCancelled = true
                                }
                            }
                            if (target != null && !tapCancelled && !upConsumed) {
                                select(target)
                            }
                        }
                    }
                    .padding(horizontal = CapsuleHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(CapsuleItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestinations.forEachIndexed { index, destination ->
                    NavItem(
                        selected = index == selectedIndex,
                        onClick = { select(index) },
                        icon = destination.icon,
                        label = if (useCompactLabels) destination.compactLabel else destination.label,
                        // Accessibility always uses the full destination name.
                        // An abbreviated label like "Transcr." is announced
                        // verbatim by TalkBack, which is not what it means.
                        contentDescription = destination.accessibilityLabel,
                        showLabel = showLabels,
                        onPositioned = { itemCoords[index] = it }
                    )
                }
            }
        }
    }
}

/**
 * One capsule destination.
 *
 * [Modifier.selectable] with [Role.Tab] replaces what `NavigationBarItem`
 * provided: selection semantics, role, ripple, and keyboard focus. [stateDescription]
 * and the selected-pill are both surfaced to the accessibility tree so TalkBack
 * announces state identically to the M3 component it replaces.
 */
@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    showLabel: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit
) {
    // Row weight animates between collapsed and expanded. Weight is a
    // parent-measured value, not a layout state, so this drives a re-measure of
    // the row each frame rather than a GPU-only transform. That is acceptable
    // here precisely because the capsule is a fixed 64.dp: five icon nodes, no
    // content beneath it re-laying out, and the whole thing is above the
    // Scaffold's content column.
    val weight by animateFloatAsState(
        targetValue = if (selected) NAV_WEIGHT_EXPANDED else NAV_WEIGHT_COLLAPSED,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navItemWeight"
    )

    val pillColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val pillContentColor = MaterialTheme.colorScheme.onPrimary
    val iconTint = if (selected) pillContentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            // Weight is applied here, inside RowScope, because it is a
            // parent-measured value: `Row` reads it during measure and hands back
            // the width budget. Animating it therefore re-measures the row each
            // frame rather than moving a GPU layer.
            .weight(weight)
            // The target is the whole capsule column, not just the 48dp pill:
            // taps on the 8dp band above or below a thumb-height icon used to
            // be dropped. The pill is now a visual layer inside, so its morph
            // cannot change the hit area's height either.
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .onGloballyPositioned(onPositioned)
            .semantics {
                // The visible label is decorative here: it is inside an
                // AnimatedVisibility whose node would otherwise be announced for
                // the duration of the fade, and clearing it here guarantees
                // TalkBack reads the description exactly once.
                this.contentDescription = contentDescription
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center
    ) {
        // Selection pill: fixed 48dp and full item width, drawn behind the
        // icon row. Purely visual — no gesture handling lives here.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavPillHeight)
                .clip(CircleShape)
                .background(pillColor)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NavLabelGap)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(NavIconSize)
            )

            // Inside the pill, not beneath it: the expanded destination carries
            // its icon and label together on the primary fill.
            AnimatedVisibility(
                visible = selected && showLabel,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90))
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = pillContentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The destination whose measured slot contains [position], in the capsule
 * row's local coordinates. Reads live layout coordinates, so it reflects what
 * was under the finger at pointer-down even while the pill is mid-spring.
 */
private fun navIndexAt(
    position: Offset,
    row: LayoutCoordinates?,
    items: Array<LayoutCoordinates?>
): Int? {
    if (row == null || !row.isAttached) return null
    for (index in items.indices) {
        val item = items[index]?.takeIf { it.isAttached } ?: continue
        if (row.localBoundingBoxOf(item).contains(position)) return index
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalTopBar(
    studentName: String?,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
    onCheckForUpdates: () -> Unit,
    onLogout: () -> Unit,
    /** Shown in place of the name while a GPA card is scrolled away; null = name. */
    gpaTitle: String? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // A second menu at the same anchor, opened from the overflow menu's
    // "Remind me" row once that menu has closed.
    var showReminderTime by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.myiulms_app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppRadius.Small)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(AppSpacing.Md))
                // Name and subtitle normally; the GPA once the tab's GPA card
                // has scrolled away. A plain cross-fade in the same spot: no
                // movement, and the bar's height never changes.
                AnimatedContent(
                    targetState = gpaTitle,
                    transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                    contentAlignment = Alignment.CenterStart,
                    label = "topBarTitle"
                ) { title ->
                    if (title == null) {
                        Column {
                            Text(
                                text = studentName?.let { formatStudentName(it) } ?: "MyIULMS",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "MyIULMS • Unofficial",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh current screen")
            }

            IconButton(onClick = onThemeToggle) {
                Icon(
                    if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (darkTheme) {
                        "Switch to light theme"
                    } else {
                        "Switch to dark theme"
                    }
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                ReminderTimeMenu(expanded = showReminderTime, onDismiss = { showReminderTime = false })
                MyIulmsDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    MyIulmsDropdownMenuItem(
                        text = "Check for updates",
                        leadingIcon = Icons.Rounded.Update,
                        onClick = {
                            menuExpanded = false
                            onCheckForUpdates()
                        }
                    )
                    PolicyOverflowItem(onDismissMenu = { menuExpanded = false })
                    ClassRemindersMenuItem()
                    ReminderTimeMenuItem(onClick = {
                        menuExpanded = false
                        showReminderTime = true
                    })

                    // Last on purpose. Sign out is the only destructive action in
                    // this menu, so it sits below the two informational entries
                    // rather than between them — harder to hit by accident and it
                    // reads as the menu's terminal action.
                    //
                    // `destructive` tints both the label and the exit glyph red.
                    MyIulmsDropdownMenuDivider()
                    MyIulmsDropdownMenuItem(
                        text = "Sign out",
                        leadingIcon = Icons.AutoMirrored.Rounded.ExitToApp,
                        destructive = true,
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * "Class reminders" on/off, in the overflow menu with the other app-wide
 * settings. Off stops the notifications at once: the next alarm is re-armed as
 * a silent widget-only one (start, end, midnight). The menu stays open so the
 * switch visibly flips.
 */
@Composable
private fun ClassRemindersMenuItem() {
    val context = LocalContext.current
    val enabled by ReminderSettings.enabled(context).collectAsState()
    MyIulmsDropdownMenuItem(
        text = "Class reminders",
        leadingIcon = if (enabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
        trailing = {
            // Display only: the whole row is the toggle target. Off-state colours
            // are explicit: the M3 defaults put a near-invisible thumb on this
            // theme's dark menu surface.
            Switch(
                checked = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        onClick = {
            ReminderSettings.setEnabled(context, !enabled)
            NextClassWidgetSession.rescheduleAlarms(context)
        },
        modifier = Modifier.semantics {
            stateDescription = if (enabled) "On" else "Off"
        }
    )
}

/** "Remind me · 10 min before". Disabled while reminders are off. */
@Composable
private fun ReminderTimeMenuItem(onClick: () -> Unit) {
    val context = LocalContext.current
    val enabled by ReminderSettings.enabled(context).collectAsState()
    val lead by ReminderSettings.leadMinutesFlow(context).collectAsState()
    MyIulmsDropdownMenuItem(
        text = "Remind me",
        supportingText = "$lead min before class",
        leadingIcon = Icons.Rounded.Schedule,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Lead-time choice: 5, 10 or 15 minutes, as a small menu at the overflow
 * button. Applies at once: the alarm is re-armed for the new reminder time.
 */
@Composable
private fun ReminderTimeMenu(expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by ReminderSettings.leadMinutesFlow(context).collectAsState()
    MyIulmsDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MyIulmsDropdownMenuHeader("Remind me before class")
        REMINDER_LEAD_OPTIONS.forEach { minutes ->
            MyIulmsDropdownMenuItem(
                text = "$minutes minutes",
                checked = minutes == current,
                onClick = {
                    ReminderSettings.setLeadMinutes(context, minutes)
                    NextClassWidgetSession.rescheduleAlarms(context)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun UpdateCheckDialog(
    state: UpdateCheckState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    // Update available = alag, high-contrast celebratory dialog (UpdateAvailableDialog.kt).
    // Baaki states (up to date / failed / checking) yahin ka calm AlertDialog.
    if (state is UpdateCheckState.Available) {
        UpdateAvailableDialog(
            currentVersion = state.currentVersion,
            latest = state.latest,
            onUpdate = { onOpenRelease(state.latest.releaseUrl) },
            onLater = onDismiss
        )
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                when (state) {
                    is UpdateCheckState.UpToDate -> Icons.Rounded.CheckCircle
                    is UpdateCheckState.Failed -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.SystemUpdate
                },
                contentDescription = null,
                // Up to date: halka green tick — calm, celebratory nahi.
                tint = if (state is UpdateCheckState.UpToDate) {
                    if (dark) GradeStrongDark else GradeStrongLight
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )
        },
        title = {
            Text(
                when (state) {
                    is UpdateCheckState.Available -> "Update available"
                    is UpdateCheckState.UpToDate -> "You're up to date"
                    is UpdateCheckState.Failed -> "Couldn't check for updates"
                    UpdateCheckState.Checking -> "Checking for updates"
                }
            )
        },
        text = {
            when (state) {
                is UpdateCheckState.Available -> Text(
                    "Version ${state.latest.version} is available. You're using version ${state.currentVersion}."
                )
                is UpdateCheckState.UpToDate -> Text(
                    "You're running the latest version (${displayVersion(state.currentVersion)}).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is UpdateCheckState.Failed -> Text(state.message)
                UpdateCheckState.Checking -> Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Checking the latest GitHub release…")
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateCheckState.Available -> TextButton(
                    onClick = { onOpenRelease(state.latest.releaseUrl) }
                ) { Text("View update") }
                is UpdateCheckState.Failed -> TextButton(onClick = onRetry) { Text("Try again") }
                is UpdateCheckState.UpToDate -> TextButton(onClick = onDismiss) { Text("Done") }
                UpdateCheckState.Checking -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateCheckState.Available,
                is UpdateCheckState.Failed -> TextButton(onClick = onDismiss) { Text("Later") }
                else -> Unit
            }
        }
    )
}

@Composable
private fun ResultScreen(
    result: ExamResult?,
    studentName: String?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull,
    listState: LazyListState
) {
    if (result == null) {
        LoadStateContent("Loading latest result…", screenState, onRetry)
        return
    }

    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val totals = result.rows.mapNotNull { it.total.toDoubleOrNull() }
    val average = if (totals.isNotEmpty()) totals.average() else null
    val highest = totals.maxOrNull()
    val aGrades = result.rows.count { it.grade.trim().uppercase().startsWith("A") }

    // Data-present branch only: `loading` here always means a refresh, never the first load.
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(screenState.loading),
        onRefresh = { manualPull.pulled = true; onPullRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomClearance()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            item {
                ScreenHeading(
                    title = "Results",
                    subtitle = result.title
                        .replace("EXAM RESULT", "", ignoreCase = true)
                        .trim(' ', '(', ')', '-')
                        .ifBlank { "Latest examination" },
                    action = {
                        Row {
                            PolicyInfoButton()
                            IconButton(
                                onClick = {
                                    shareScope.launch {
                                        runCatching { shareResultAsPng(context, studentName, result) }
                                            .onFailure {
                                                Toast.makeText(context, "Could not share result.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            ) {
                                // Same muted tint as InfoIconButton: the ⓘ and
                                // the Share glyph sit in one row and must read
                                // as the same weight (onSurfaceVariant =
                                // #627180 light / #A9B6C0 dark).
                                Icon(
                                    Icons.Rounded.Share,
                                    "Share result",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            item {
                HeroMetricCard(
                    label = "Semester GPA",
                    value = result.gpa ?: "—",
                    helper = "${result.rows.size} courses",
                    icon = Icons.Rounded.AutoGraph
                )
            }

            if (average != null) {
                item {
                    MetricStrip(
                        "Average" to formatOneDecimal(average),
                        "Highest" to formatOneDecimal(highest ?: average),
                        "A grades" to aGrades.toString()
                    )
                }
            }

            if (result.rows.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Assessment,
                        "No result available",
                        "Your result will appear here when it is published."
                    )
                }
            } else {
                items(result.rows) { ResultCourseCard(it) }
            }
        }
    }
}

@Composable
private fun ResultCourseCard(row: ExamRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(AppSpacing.Lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    row.course,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                GradeBadge(row.grade)
            }

            Spacer(Modifier.height(15.dp))

            ResultMarksRow(row)

            HorizontalDivider(
                Modifier.padding(vertical = 13.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = .7f)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryValue("Total", row.total)
                SummaryValue("Grade", row.grade)
                SummaryValue("Points", row.points)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulesScreen(
    examSchedule: ExamSchedule?,
    weeklySchedule: WeeklySchedule?,
    freshnessNote: String?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull,
    snackbarHostState: SnackbarHostState,
    studentName: String?,
    modifier: Modifier = Modifier,
    // True for the predictive-back mirror behind the pager: content only, no
    // second set of side effects (see ScheduleTips.interactive).
    preview: Boolean = false
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    val subTabStateHolder = rememberSaveableStateHolder()

    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Weekly Classes")
            }
            SegmentedButton(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Exam Schedule")
            }
        }

        ScheduleTips(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = AppSpacing.Xs),
            interactive = !preview
        )

        Box(modifier = Modifier.weight(1f)) {
            if (selectedSubTab == 0) {
                subTabStateHolder.SaveableStateProvider("weekly") {
                    WeeklyScheduleContent(
                        schedule = weeklySchedule,
                        freshnessNote = freshnessNote,
                        screenState = screenState,
                        onRetry = onRetry,
                        onPullRefresh = onPullRefresh,
                        manualPull = manualPull,
                        snackbarHostState = snackbarHostState,
                        enabled = selectedSubTab == 0,
                        studentName = studentName
                    )
                }
            } else {
                subTabStateHolder.SaveableStateProvider("exam") {
                    Column {
                        // No day chips here, so the line sits under the sub-tab row.
                        FreshnessLine(freshnessNote)
                        ExamScheduleContent(examSchedule, screenState, onRetry, onPullRefresh, manualPull)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamScheduleContent(
    schedule: ExamSchedule?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull
) {
    if (schedule == null) {
        LoadStateContent("Loading exam schedule…", screenState, onRetry)
        return
    }

    // Data-present branch only: `loading` here always means a refresh, never the first load.
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(screenState.loading),
        onRefresh = { manualPull.pulled = true; onPullRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomClearance()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            item {
                ScreenHeading(
                    title = "Exam schedule",
                    subtitle = distinctSubtitle(schedule.title, "Exam schedule", "Dates, times and rooms")
                )
            }

            if (!schedule.notice.isNullOrBlank()) {
                item {
                    NoticeCard(message = schedule.notice)
                }
            }

            // With no exams the empty state below says it once; no "0" card.
            if (schedule.entries.isNotEmpty()) item {
                HeroMetricCard(
                    label = "Scheduled exams",
                    value = "${schedule.entries.size}",
                    helper = if (schedule.entries.isEmpty()) "No scheduled exams"
                    else "${schedule.entries.size} course examination${if (schedule.entries.size == 1) "" else "s"}",
                    icon = Icons.Rounded.Event
                )
            }

            if (schedule.entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "No exam schedule",
                        body = "Your examination schedule is not available at this time or no exams are scheduled."
                    )
                }
            } else {
                items(schedule.entries) { entry ->
                    ExamScheduleCard(entry = entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * "Syncing… · last updated …" / "Saved copy · last updated …". Announced
 * politely, so TalkBack hears a sync start and finish without a spinner.
 */
@Composable
private fun FreshnessLine(note: String?) {
    if (note == null) return
    Text(
        text = note,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ListGutter, vertical = AppSpacing.Xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Pull indicator that spins only for a refresh the student asked for (a pull or
 * the toolbar button): a background sync never sets [pulled]. Held per tab by
 * HomeScreen and reset when that tab's load ends.
 */
private class ManualPull {
    var pulled by mutableStateOf(false)
    fun isRefreshing(loading: Boolean) = pulled && loading
}

@Composable
private fun WeeklyScheduleContent(
    schedule: WeeklySchedule?,
    freshnessNote: String?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull,
    snackbarHostState: SnackbarHostState,
    enabled: Boolean,
    studentName: String?
) {
    if (schedule == null) {
        LoadStateContent("Loading weekly schedule…", screenState, onRetry)
        return
    }

    // Shared with the share compiler via groupScheduleByDay so the text a
    // student shares cannot disagree with the list they are looking at.
    val groups = groupScheduleByDay(schedule.entries)

    val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val classDays = groups.keys
    var selectedDay by rememberSaveable(schedule) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /** Suppresses the scroll-driven highlight while a chip tap is animating. */
    var suppressHighlight by remember { mutableStateOf(false) }

    // Composition-scoped Activity context for the share intent. Never hoisted
    // into the ViewModel: that would outlive the Activity and leak it.
    val shareContext = LocalContext.current

    // Ties the share job to this screen's lifecycle, so it is cancelled if the
    // student navigates away before the bitmap is finished.
    val shareScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // derivedStateOf + read only inside ScheduleFocusHost: scrolling must not
        // recompose this screen just to learn the card's visibility.
        val focusVisible by remember(listState) {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.any { it.key == ITEM_KEY_DASHBOARD }
            }
        }

        /**
         * Static day-chip bar. A SIBLING of the list, not an item in it.
         *
         * This is what lets both the chips and the day labels be permanently
         * visible. A `LazyColumn` sticky header is unpushed only by the next
         * sticky header, so two pinned rows inside one list is a structural
         * conflict: whichever comes second evicts the first. Lifting the chips
         * out of the list removes them from the pin hierarchy entirely — they
         * never scroll, never evict, never need an offset.
         *
         * Consequence: the list lost one item, so [ITEMS_BEFORE_DAY_GROUPS] is
         * 2, not 3. The chips bar is no longer addressable as an index.
         */
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListGutter),
            color = MaterialTheme.colorScheme.background
        ) {
            // Bottom cushion equals the list's `spacedBy(AppSpacing.Md)` gap.
            // `Arrangement` space is layout space that this bar does NOT cover,
            // so anything smaller would let the first card slide under the seam.
            Column(modifier = Modifier.padding(bottom = AppSpacing.Md)) {
                WeeklyDayChipsRow(
                    weekDays = weekDays,
                    classDays = classDays,
                    selectedDay = selectedDay,
                    onSelectDay = { day ->
                        selectedDay = day
                        suppressHighlight = true
                        scope.launch {
                            try {
                                listState.animateScrollToItem(
                                    index = dayGroupLabelItemIndex(
                                        day = day,
                                        weekDays = weekDays,
                                        classDays = classDays,
                                        groups = groups
                                    ),
                                    // 0, not a negative offset. The chips are no
                                    // longer a list item, so the list's own
                                    // content edge now sits flush beneath the
                                    // static bar. Offsetting again would push the
                                    // target day label a full band too low and
                                    // leave a gap under the chips.
                                    scrollOffset = 0
                                )
                            } finally {
                                suppressHighlight = false
                            }
                        }
                    },
                    onEmptyDay = { day ->
                        scope.launch {
                            snackbarHostState.showSnackbar("No classes scheduled on ${day.lowercase().replaceFirstChar(Char::uppercase)}")
                        }
                    }
                )
            }
        }

        // The Schedule tab's only sync indicator. Static, under the chips, so it
        // stays visible while scrolling.
        FreshnessLine(freshnessNote)

        // Data-present branch only: `loading` here always means a refresh, never the first load.
        MyIulmsPullToRefresh(
            isRefreshing = manualPull.isRefreshing(screenState.loading),
            onRefresh = { manualPull.pulled = true; onPullRefresh() },
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = ListGutter,
                    // 0.dp: this list begins directly under the static chip bar, so
                    // any top padding would be a visible dead band between the bar
                    // and the first row. The 12.dp the screen wants below the status
                    // bar is carried by the heading item's own top padding.
                    top = 0.dp,
                    end = ListGutter,
                    bottom = listBottomClearance()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                // Heading and countdown are ordinary items and scroll away; the day
                // labels below are the list's sticky rows and pin to this list's
                // content edge, which is directly under the static chip bar.
                item(key = ITEM_KEY_HEADING) {
                    // Carries what `contentPadding.top` used to hold, so the screen
                    // still starts 12.dp below the status bar without leaving a
                    // gap the day labels would scroll through.
                    Box(modifier = Modifier.padding(top = AppSpacing.Md)) {
                        ScreenHeading(
                            title = "Weekly class schedule",
                            subtitle = distinctSubtitle(schedule.title, "Weekly class schedule", "Your semester classes"),
                            // Same slot the result and transcript screens use for
                            // their share buttons, so the affordance reads
                            // identically across the app. It lives here rather than
                            // in the top app bar because this block only composes on
                            // the "Weekly Classes" sub-tab, which is what scopes the
                            // button to that view without threading the sub-tab
                            // selection up to PortalTopBar.
                            action = {
                                IconButton(
                                    onClick = {
                                        // `shareContext` is read at composition time,
                                        // not inside this lambda: an onClick body is
                                        // not a composable scope, so
                                        // `LocalContext.current` cannot be called
                                        // here. Holding it only for the life of this
                                        // composition is what keeps the Activity
                                        // from leaking into the ViewModel.
                                        //
                                        // `shareScope.launch` is required because the
                                        // renderer is a suspend function: it allocates
                                        // a ~10MB bitmap and PNG-compresses it on
                                        // Dispatchers.Default. Launching on the
                                        // composition scope also ties the job to this
                                        // screen, so it is cancelled if the student
                                        // leaves before the chooser appears.
                                        shareScope.launch {
                                            runCatching {
                                                shareScheduleAsPng(shareContext, studentName, schedule)
                                            }.onFailure {
                                                Toast.makeText(
                                                    shareContext,
                                                    "Could not share schedule.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                ) {
                                    // Heading action tint matches InfoIconButton
                                    // so every heading action reads identically.
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = "Share schedule",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                item(key = ITEM_KEY_DASHBOARD) {
                    ScheduleFocusHost(
                        schedule = schedule,
                        enabled = enabled,
                        isVisible = { focusVisible }
                    )
                }

                if (groups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.EventAvailable,
                            title = "No weekly schedule",
                            body = "Your class schedule is not available on the IULMS portal right now."
                        )
                    }
                } else {
                    groups.forEach { (day, entries) ->
                        // Sticky, and now genuinely permanent-looking: the chip bar that
                        // used to evict these labels has been hoisted OUT of the
                        // list, so this is the only sticky layer and nothing can
                        // push it off. It pins to the list's content edge, which
                        // sits directly beneath the static chip bar -- no modifier
                        // offset needed, and none would work anyway, since a pin
                        // position is decided by the LazyList layout rather than by
                        // any modifier inside the slot.
                        //
                        // The dead full-width `background` shield that used to sit
                        // here is gone: the pill is the only visible shape, and the
                        // 20.dp of empty padding it contributed per day header went
                        // with it.
                        //
                        // `CircleShape`: the pill is roughly 56x32dp, so its
                        // half-height is ~16dp and a circular outline is a true
                        // capsule end on both axes. A fixed `RoundedCornerShape`
                        // would need hand-matching to that height and would drift
                        // when the font scale changed.
                        stickyHeader(key = dayLabelKey(day)) {
                            // Invisible full-width shield. A pinned sticky header is
                            // composed in a separate overlay layout drawn ON TOP of
                            // the main item layer, so entry cards travel underneath
                            // the day label. The pill alone covers only its own
                            // ~56dp and cannot mask a 360dp scroll, so card titles
                            // ghost straight through the day name without this.
                            //
                            // Fill is `background`, matching the list, so the mask is
                            // invisible and only the pill reads as a shape.
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Surface(
                                    modifier = Modifier.padding(
                                        // ListGutter keeps the capsule's inset on
                                        // the same 18.dp axis as every other row in
                                        // this list.
                                        horizontal = ListGutter,
                                        // AppSpacing.Xs. NOTE: the list's
                                        // `Arrangement.spacedBy(AppSpacing.Md)`
                                        // gap is 12.dp, and `Arrangement` space is
                                        // layout space this shield does NOT cover,
                                        // so an 8.dp strip directly beneath the
                                        // shield is still exposed while cards slide
                                        // under it. AppSpacing.Md (12.dp) would close
                                        // that seam completely at the cost of 16.dp
                                        // more height per day header.
                                        vertical = AppSpacing.Xs
                                    ),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = day,
                                        // 16.dp / 6.dp track the pill's type size.
                                        // Neither is a spacing token; AppSpacing
                                        // stops at 4.dp.
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 6.dp
                                        ),
                                        // titleSmall, not labelMedium: the day is the
                                        // only orientation cue in a list of otherwise
                                        // identical cards, so it is set at the size a
                                        // reader scans for rather than the size a
                                        // caption uses.
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(entries, key = { entry -> entryKey(day, entry) }) { entry ->
                            WeeklyScheduleCard(entry)
                        }
                    }
                }
            }
        }

        /**
         * Scroll-driven chip highlight.
         *
         * Reads the visible keys, so the day that owns the most visible rows
         * wins; a partially visible next day cannot steal the chip early the way
         * `firstVisibleItemIndex` alone would. Structural rows contribute
         * nothing, which is why a viewport still showing the heading/chips/focus
         * card resolves to `null` and leaves the selection alone.
         *
         * This observer only ever writes `selectedDay` — it never scrolls, or it
         * would fight the finger that is currently driving the list.
         */
        val dominantDay by remember(groups, classDays) {
            derivedStateOf {
                dominantDay(
                    visibleKeys = listState.layoutInfo.visibleItemsInfo.map { it.key },
                    groups = groups,
                    classDays = classDays
                )
            }
        }
        LaunchedEffect(dominantDay, listState.isScrollInProgress) {
            if (!listState.isScrollInProgress || suppressHighlight) return@LaunchedEffect
            if (dominantDay != null && dominantDay != selectedDay) {
                selectedDay = dominantDay
            }
        }
    }
}

/** Key prefix of the per-day label rows. */
internal const val DAY_LABEL_KEY_PREFIX = "day-"

/** LazyList key of [day]'s label row. */
internal fun dayLabelKey(day: String): String = "$DAY_LABEL_KEY_PREFIX$day"

/** LazyList key of a single entry card, matching the `items(...)` key lambda. */
internal fun entryKey(day: String, entry: WeeklyScheduleEntry): String =
    "$day-${entry.courseCode}-${entry.time}"

/**
 * Gutter for the weekly schedule list.
 *
 * Named because the heading, the day chips and the schedule cards below them
 * must all resolve to the same horizontal axis. Three separate `18.dp` literals
 * is exactly the kind of duplication that drifts.
 */
private val ListGutter = 18.dp

/**
 * Keys of the structural items that precede the day groups.
 *
 * `internal` rather than `private` because [rowForKey] has to recognise them
 * and the highlight tests assert against the very same literals.
 */
internal const val ITEM_KEY_HEADING = "weekly-heading"
internal const val ITEM_KEY_DAY_CHIPS = "weekly-day-chips"
internal const val ITEM_KEY_DASHBOARD = "class-dashboard"

/**
 * Number of structural items the list emits before the first day group:
 * heading, countdown card.
 *
 * TWO, not three. The day-chip bar was hoisted out of the `LazyColumn` into a
 * static sibling, so it no longer occupies an index at all. When it was an
 * `item` this was 3; when it was a `stickyHeader` it was still 3, because
 * `item` and `stickyHeader` each consume exactly one index. Lifting it out of
 * the list is the one change that removes a slot.
 *
 * [dayGroupLabelItemIndex] adds this to every target, so adding or removing a
 * structural item is a single-line change here rather than a silent off-by-N in
 * the scroll arithmetic.
 */
internal const val ITEMS_BEFORE_DAY_GROUPS = 2

/**
 * What a single LazyList row represents, independent of Compose.
 *
 * The scroll-driven chip highlight has to answer "which day is the student
 * looking at" from the list's visible keys alone. That is pure bookkeeping, so
 * it is modelled as data and unit-tested without a Robolectric runner.
 */
internal sealed interface ScheduleRow {
    data object Heading : ScheduleRow
    data object DayChips : ScheduleRow
    data object FocusCard : ScheduleRow
    data class DayLabel(val day: String) : ScheduleRow
    data class EntryCard(val day: String) : ScheduleRow
}

/**
 * Maps a LazyList item key to the [ScheduleRow] it stands for.
 *
 * The key literals live in exactly one place each — [ITEM_KEY_HEADING],
 * [ITEM_KEY_DAY_CHIPS], [ITEM_KEY_DASHBOARD], [dayLabelKey] and [entryKey] —
 * so this cannot drift away from the list body. Entry keys are reverse-looked
 * because they encode day, course code and time, which is the only place the
 * day of an arbitrary card is recoverable from its key.
 *
 * An unknown key is reported as [ScheduleRow.Heading], i.e. as a structural row
 * that contributes nothing to the dominance count. That keeps the function
 * total: a key from a future row type degrades to "ignored" rather than
 * throwing inside a scroll callback.
 */
internal fun rowForKey(
    key: Any,
    groups: Map<String, List<WeeklyScheduleEntry>>
): ScheduleRow = when {
    key == ITEM_KEY_HEADING -> ScheduleRow.Heading
    key == ITEM_KEY_DAY_CHIPS -> ScheduleRow.DayChips
    key == ITEM_KEY_DASHBOARD -> ScheduleRow.FocusCard
    key is String && key.startsWith(DAY_LABEL_KEY_PREFIX) ->
        ScheduleRow.DayLabel(key.removePrefix(DAY_LABEL_KEY_PREFIX))
    else -> {
        val day = groups.entries
            .firstOrNull { (groupDay, entries) ->
                entries.any { entry -> entryKey(groupDay, entry) == key }
            }
            ?.key
        if (day != null) ScheduleRow.EntryCard(day) else ScheduleRow.Heading
    }
}

/**
 * Day owning the most visible rows, or `null` when only structural rows show.
 *
 * Counting rows rather than picking `firstVisibleItemIndex` is deliberate: the
 * index alone flips as soon as the previous day's last card leaves, and it
 * needs a reverse map back to a day name that varies per week. Weighting by
 * visible rows instead means the chip only moves when the viewport has actually
 * committed to the next day. Ties resolve to the earliest day in [visibleRows]
 * because [LinkedHashMap] preserves insertion order and `maxByOrNull` keeps the
 * first maximum.
 */
internal fun dominantDay(
    visibleKeys: List<Any>,
    groups: Map<String, List<WeeklyScheduleEntry>>,
    classDays: Set<String>
): String? {
    val weightByDay = LinkedHashMap<String, Int>()
    visibleKeys.forEach { key ->
        when (val row = rowForKey(key, groups)) {
            is ScheduleRow.DayLabel -> weightByDay.merge(row.day, 1, Int::plus)
            is ScheduleRow.EntryCard -> weightByDay.merge(row.day, 1, Int::plus)
            else -> Unit // structural rows own no day
        }
    }
    return weightByDay
        .filterKeys { it in classDays }
        .maxByOrNull { it.value }
        ?.key
}

/**
 * Day-of-week chips for the weekly schedule.
 *
 * Extracted from the list body so the `item {}` block stays declarative and the
 * tap behaviour is unit-testable independently of layout.
 */
@Composable
private fun WeeklyDayChipsRow(
    weekDays: List<String>,
    classDays: Set<String>,
    selectedDay: String?,
    onSelectDay: (String) -> Unit,
    onEmptyDay: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDays.forEach { day ->
            val hasClasses = day in classDays
            val isSelected = selectedDay == day
            val background = when {
                isSelected -> MaterialTheme.colorScheme.primary
                hasClasses -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val foreground = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                hasClasses -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "$day, ${if (hasClasses) "classes scheduled" else "no classes scheduled"}"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .clickable(role = Role.Button) {
                        if (hasClasses) onSelectDay(day) else onEmptyDay(day)
                    },
                shape = RoundedCornerShape(AppRadius.Small),
                color = background,
                tonalElevation = if (hasClasses) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        color = foreground,
                        fontWeight = if (hasClasses) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * LazyList index of [day]'s day-label row.
 *
 * Walks the day groups in the same order the list emits them and counts what has
 * already been placed:
 *
 *  - [ITEMS_BEFORE_DAY_GROUPS] structural items (heading, countdown card)
 *    always precede the groups. The day-chip bar is not among them: it is a
 *    static sibling of the list, not a row in it.
 *  - For each earlier day that has classes, one label row plus that day's entry
 *    cards.
 *
 * The earlier days are taken from [weekDays] rather than from [groups] so the
 * weekday ordering cannot drift from the chip order the student is tapping. Days
 * with no classes are skipped, matching the list, which emits no group for them.
 *
 * `item {}` and `stickyHeader {}` each occupy exactly one index, so swapping
 * which of the chips and the labels is pinned leaves this arithmetic
 * bit-for-bit identical.
 */
internal fun dayGroupLabelItemIndex(
    day: String,
    weekDays: List<String>,
    classDays: Set<String>,
    groups: Map<String, List<WeeklyScheduleEntry>>
): Int {
    val priorGroups = weekDays
        .takeWhile { it != day }
        .filter { it in classDays }
    return ITEMS_BEFORE_DAY_GROUPS + priorGroups.sumOf { priorDay ->
        1 + groups[priorDay].orEmpty().size
    }
}

/**
 * Owns the dashboard card's clock, so a tick recomposes this host and the card
 * only, never [WeeklyScheduleContent] or its LazyColumn.
 *
 * [nowMillis] is the single source of truth; [ScheduleFocus] is always derived
 * from it, so the card and the tick rate cannot disagree. The loop runs only
 * while STARTED and re-syncs immediately on return from the background.
 * [nextFocusTickDelayMillis] picks 1 Hz for a visible live class, otherwise the
 * next minute boundary, which is where every state change and midnight fall.
 */
@Composable
private fun ScheduleFocusHost(
    schedule: WeeklySchedule,
    enabled: Boolean,
    isVisible: () -> Boolean
) {
    val entries = schedule.entries
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // derivedStateOf, not remember(nowMillis): the loop below reads this straight
    // after writing nowMillis and must see the state for THIS tick, or the first
    // tick of a class would schedule a full-minute sleep off the previous state.
    val focus by remember(entries) {
        derivedStateOf {
            resolveScheduleFocus(
                entries = entries,
                today = todayAbbrev(nowMillis),
                minuteOfDay = minuteOfDay(nowMillis)
            )
        }
    }

    val visible = isVisible()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(entries, enabled, visible, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(nextFocusTickDelayMillis(focus, nowMillis, visible))
            }
        }
    }

    val current = focus
    ScheduleFocusCard(
        focus = current,
        secondsRemaining = when (current) {
            is ScheduleFocus.Live -> liveSecondsRemaining(current.entry, nowMillis)
            else -> 0
        }
    )
}

@Composable
private fun WeeklyScheduleCard(entry: WeeklyScheduleEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            // AppSpacing.Md, not the bare 8.dp this was: the time row, the
            // title and the Room/Faculty row are three dense text blocks, and at
            // 8.dp they read as one undivided mass. 12.dp separates them without
            // altering any block's own leading. The token also replaces a raw
            // literal that no other spacing in this card used.
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            if (entry.time.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.Sm))
                        Text(
                            text = formatTime12Hour(entry.time),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    scheduleDurationLabel(entry.time)?.let { duration ->
                        Spacer(Modifier.width(AppSpacing.Sm))
                        DurationBadge(duration)
                    }
                }
            }
            Text(
                text = entry.courseTitle.ifBlank { "Class" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Room and Faculty share one line below the title instead of
            // stacking as two full-width rows.
            //
            // The split is asymmetric on purpose. Room is a short, predictable
            // token ("E-806", "Lab 3") so it is measured at its intrinsic width
            // and takes only what it needs. Faculty is the variable-length field
            // -- "Dr. Muhammad Rizwan Munir" is three times the width of a room
            // number -- so it is the weighted child and absorbs ALL of the
            // leftover space. Splitting the row 50/50 instead would cap faculty
            // at half the card and truncate names that would otherwise fit.
            //
            // Both blocks are still width-bounded: Room by the row's own max
            // constraint (clamped by wrapContentWidth), Faculty by its weight.
            // Either way the value Text ellipsizes instead of wrapping, so a
            // long name cannot grow the card.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)
            ) {
                if (entry.location.isNotBlank()) {
                    InfoLineCompact(
                        icon = Icons.Rounded.LocationOn,
                        label = "Room",
                        value = entry.location,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
                if (entry.faculty.isNotBlank()) {
                    InfoLineCompact(
                        icon = Icons.Rounded.Person,
                        label = "Faculty",
                        value = entry.faculty,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // The course code / EDP code line used to sit here. Removed: both
            // fields are still parsed into WeeklyScheduleEntry and still feed
            // entryKey, so nothing downstream loses them -- they are simply no
            // longer printed on the collapsed card, which is where the vertical
            // saving comes from.
        }
    }
}

private val timeTokenPattern = Regex(
    "(?i)(?<![\\d:])(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM|A\\.M\\.|P\\.M\\.))?(?!\\d)"
)

private fun formatTime12Hour(time: String): String = timeTokenPattern.replace(time) { match ->
    val hour = match.groupValues[1].toIntOrNull() ?: return@replace match.value
    val minute = match.groupValues[2].toIntOrNull() ?: return@replace match.value
    if (hour !in 0..23 || minute !in 0..59) return@replace match.value

    val suffix = match.groupValues[3].replace(".", "").uppercase()
    val hour12 = when (hour % 12) {
        0 -> 12
        else -> hour % 12
    }
    val period = if (suffix == "AM" || suffix == "PM") {
        suffix
    } else if (hour < 12) {
        "AM"
    } else {
        "PM"
    }
    "$hour12:${minute.toString().padStart(2, '0')} $period"
}

private fun scheduleStartMinutes(time: String): Int {
    val match = timeTokenPattern.find(time) ?: return Int.MAX_VALUE
    val hour = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
    val minute = match.groupValues[2].toIntOrNull() ?: return Int.MAX_VALUE
    if (hour !in 0..23 || minute !in 0..59) return Int.MAX_VALUE

    val suffix = match.groupValues[3].replace(".", "").uppercase()
    val hour24 = when (suffix) {
        "AM" -> hour % 12
        "PM" -> (hour % 12) + 12
        else -> hour % 24
    }
    return hour24 * 60 + minute
}

private fun scheduleDurationLabel(time: String): String? {
    val tokens = timeTokenPattern.findAll(time).take(2).toList()
    if (tokens.size < 2) return null

    val first = tokens[0]
    val second = tokens[1]
    val firstHour = first.groupValues[1].toIntOrNull() ?: return null
    val secondHour = second.groupValues[1].toIntOrNull() ?: return null
    val firstSuffix = first.groupValues[3].replace(".", "").uppercase()
    val secondSuffix = second.groupValues[3].replace(".", "").uppercase()

    val inferredFirstSuffix = when {
        firstSuffix == "AM" || firstSuffix == "PM" -> firstSuffix
        firstHour > 12 -> ""
        secondSuffix != "AM" && secondSuffix != "PM" -> ""
        firstHour % 12 > secondHour % 12 -> if (secondSuffix == "AM") "PM" else "AM"
        else -> secondSuffix
    }
    val inferredSecondSuffix = when {
        secondSuffix == "AM" || secondSuffix == "PM" -> secondSuffix
        secondHour > 12 -> ""
        firstSuffix != "AM" && firstSuffix != "PM" -> ""
        secondHour % 12 < firstHour % 12 -> if (firstSuffix == "AM") "PM" else "AM"
        else -> firstSuffix
    }

    fun minutes(token: MatchResult, inferredSuffix: String): Int? {
        val hour = token.groupValues[1].toIntOrNull() ?: return null
        val minute = token.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val suffix = token.groupValues[3].replace(".", "").uppercase().ifBlank { inferredSuffix }
        val hour24 = when (suffix) {
            "AM" -> hour % 12
            "PM" -> (hour % 12) + 12
            else -> hour % 24
        }
        return hour24 * 60 + minute
    }

    val start = minutes(first, inferredFirstSuffix) ?: return null
    var end = minutes(second, inferredSecondSuffix) ?: return null
    if (end < start) end += 24 * 60
    val durationMinutes = end - start
    if (durationMinutes !in 1..(12 * 60)) return null

    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours hr${if (hours == 1) "" else "s"}"
        else -> "$hours hr${if (hours == 1) "" else "s"} $minutes min"
    }
}

@Composable
private fun AttendanceScreen(
    attendance: AttendanceSummary?,
    timetable: List<WeeklyScheduleEntry>?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull
) {
    if (attendance == null) {
        LoadStateContent("Loading attendance…", screenState, onRetry)
        return
    }

    var showLimitsInfo by rememberSaveable { mutableStateOf(false) }
    if (showLimitsInfo) AbsenceLimitsInfoDialog(onDismiss = { showLimitsInfo = false })

    // Data-present branch only: `loading` here always means a refresh, never the first load.
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(screenState.loading),
        onRefresh = { manualPull.pulled = true; onPullRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomClearance()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            item {
                ScreenHeading(
                    title = "Attendance",
                    subtitle = "Course-wise sessions and class records",
                    action = {
                        InfoIconButton(
                            onClick = { showLimitsInfo = true },
                            contentDescription = "How absence limits are estimated"
                        )
                    }
                )
            }
            if (attendance.courses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.FactCheck,
                        title = "No attendance data",
                        body = "Course attendance is not available on the IULMS portal right now."
                    )
                }
            } else {
                items(attendance.courses) { course ->
                    AttendanceCourseCard(course, remember(course, timetable) { assessAttendance(course, timetable) })
                }
            }
        }
    }
}

@Composable
private fun AttendanceCourseCard(course: AttendanceCourse, risk: AttendanceRisk) {
    var expanded by rememberSaveable(course.name) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = if (expanded) "Hide session details" else "Show session details"
                    ) { expanded = !expanded }
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "Session details expanded" else "Session details collapsed"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val edp = edpCodeOf(course.name)
                Column(Modifier.weight(1f)) {
                    // The EDP code gets its own quieter line, so long names wrap less.
                    Text(
                        text = edp?.let { course.name.removeSuffix("($it)").trim() } ?: course.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (edp != null) {
                        Text(
                            text = edp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // One chevron, turned 180° on a spring instead of swapping
                // icons. Read in graphicsLayer: the turn only redraws.
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "attendanceArrow"
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                )
            }
            AttendanceRiskChip(risk)
            AttendanceStatsRow(course, risk)
            // Session details grow and fade in from the stats row, and shrink
            // back on collapse, instead of popping in.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(180, delayMillis = 60)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    if (course.faculty.isNotBlank()) InfoLine(Icons.Rounded.Person, "Faculty", course.faculty)
                    if (course.schedule.isNotBlank()) {
                        InfoLine(Icons.Rounded.Schedule, "Schedule", formatTime12Hour(course.schedule))
                    }
                    if (course.sessions.isEmpty()) {
                        Text(
                            text = "No individual session records are available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        course.sessions.forEach { session ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Lecture ${session.lectureNumber.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = session.values.mapIndexed { index, value ->
                                        val label = course.sessionHeaders.getOrNull(index)
                                            ?: "Session ${index + 1}"
                                        "$label: ${value.ifBlank { "—" }}"
                                    }.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Absence status for one course: tinted pill with icon and words, so colour is
 * never the only signal. Safe = green, Caution = amber, Danger = red. Hidden
 * when the course's weekly classes can't be determined.
 */
@Composable
private fun AttendanceRiskChip(risk: AttendanceRisk) {
    val label = attendanceRiskLabel(risk) ?: return
    val fg = attendanceRiskColor(risk.level) ?: return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (icon, levelName) = when (risk.level) {
        AttendanceLevel.Safe -> Icons.Rounded.CheckCircle to "Safe"
        AttendanceLevel.Caution -> Icons.Rounded.Warning to "Caution"
        else -> Icons.Rounded.Error to "At risk"
    }
    val allowed = risk.allowedAbsences ?: 0
    val used = (allowed - (risk.absencesLeft ?: 0)).coerceIn(0, allowed.coerceAtLeast(1))
    // One-line pill: icon shape + verdict carry the level; the count stays quieter.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Small))
            .background(fg.copy(alpha = if (dark) 0.16f else 0.10f))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm)
            .semantics(mergeDescendants = true) {
                contentDescription = "Attendance $levelName. $label. $used of $allowed allowed absences used."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(AppSpacing.Sm))
        Text("$used of $allowed used", color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}

/** Safe / Caution / Danger foreground for the current theme; null when unknown. */
@Composable
private fun attendanceRiskColor(level: AttendanceLevel): Color? {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (level) {
        AttendanceLevel.Safe -> if (dark) GradeStrongDark else GradeStrongLight
        AttendanceLevel.Caution -> if (dark) WarningForegroundDark else WarningForegroundLight
        AttendanceLevel.Danger -> if (dark) GradeCriticalDark else GradeCriticalLight
        AttendanceLevel.Unknown -> null
    }
}

/**
 * Present · Absent · Held · % so far as one row of value-over-label pairs. No
 * boxes, so the risk chip stays the card's only filled shape. Wraps to two
 * rows at large font sizes; screen readers hear it as one sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceStatsRow(course: AttendanceCourse, risk: AttendanceRisk) {
    val absentTint = when (risk.level) {
        AttendanceLevel.Caution, AttendanceLevel.Danger -> attendanceRiskColor(risk.level)
        else -> null
    } ?: MaterialTheme.colorScheme.onSurface
    val percent = if (course.totalSessions > 0) "${course.attendancePercent}%" else "—"
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${course.present} present, ${course.absent} absent, " +
                    "${course.totalSessions} held, $percent so far"
            },
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        maxItemsInEachRow = 4
    ) {
        val cell = Modifier.weight(1f).widthIn(min = 64.dp)
        CompactStat(course.present.toString(), "Present", cell)
        CompactStat(course.absent.toString(), "Absent", cell, valueColor = absentTint)
        CompactStat(course.totalSessions.toString(), "Held", cell)
        CompactStat(percent, "so far", cell)
    }
}

@Composable
private fun CompactStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // Soft tinted micro-card: each number gets its own boundary, read at a glance.
    Column(
        modifier
            .clip(RoundedCornerShape(AppRadius.Small))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = AppSpacing.Sm, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Opened from the info icon by the Attendance heading: how the limits are derived. */
@Composable
private fun AbsenceLimitsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
        title = { Text("About absence limits") },
        text = {
            Text(
                "Absence limits are estimates. Each course needs ${AcademicPolicy.ATTENDANCE_MIN_PERCENT}% " +
                    "attendance, so you can miss up to ${100 - AcademicPolicy.ATTENDANCE_MIN_PERCENT}% of its " +
                    "planned sessions: its weekly classes over a $SEMESTER_WEEKS-week semester. The portal does " +
                    "not publish these limits, so confirm with your department before relying on them."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
private fun NoticeCard(message: String) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (dark) WarningContainerDark else WarningContainerLight
    val fg = if (dark) WarningForegroundDark else WarningForegroundLight

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(AppRadius.Medium)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = fg
            )
        }
    }
}

@Composable
private fun ExamScheduleCard(entry: ExamScheduleEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(AppSpacing.Lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = entry.courseTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.edpCode.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(AppRadius.Small)
                    ) {
                        Text(
                            text = "EDP: ${entry.edpCode}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(AppRadius.Medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.dayAndDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (entry.time.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = formatTime12Hour(entry.time),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (entry.location.isNotBlank()) {
                InfoLine(Icons.Rounded.MeetingRoom, "Location / Room", entry.location)
                Spacer(Modifier.height(8.dp))
            }

            if (entry.faculty.isNotBlank()) {
                InfoLine(Icons.Rounded.Person, "Faculty", entry.faculty)
            }
        }
    }
}

@Composable
private fun VoucherScreen(
    vouchers: List<Voucher>?,
    downloadingVoucherNumber: String?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull,
    onDownload: (Voucher) -> Unit
) {
    if (vouchers == null) {
        LoadStateContent("Loading vouchers…", screenState, onRetry)
        return
    }

    val totalOutstanding = vouchers.sumOf { parseRupees(it.amount) }

    // Data-present branch only: `loading` here always means a refresh, never the first load.
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(screenState.loading),
        onRefresh = { manualPull.pulled = true; onPullRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomClearance()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            item {
                // The count lives in the total card below; the subtitle only says
                // what the screen is.
                ScreenHeading(
                    "Fee vouchers",
                    if (vouchers.isEmpty()) "No outstanding balance" else "Outstanding fees and payment slips"
                )
            }

            if (vouchers.isNotEmpty()) {
                item {
                    HeroMetricCard(
                        label = "Total outstanding",
                        value = formatRupees(totalOutstanding),
                        helper = nearestDueText(vouchers),
                        icon = Icons.Rounded.AccountBalanceWallet
                    )
                }
            }

            if (vouchers.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.CheckCircle,
                        "You're all clear",
                        "There are no outstanding vouchers on your account."
                    )
                }
            } else {
                items(vouchers) { voucher ->
                    VoucherCard(
                        voucher = voucher,
                        downloading = downloadingVoucherNumber == voucher.number,
                        downloadBusy = downloadingVoucherNumber != null,
                        onDownload = { onDownload(voucher) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoucherCard(
    voucher: Voucher,
    downloading: Boolean,
    downloadBusy: Boolean,
    onDownload: () -> Unit
) {
    val status = dueStatus(voucher.dueDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(AppSpacing.Lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        voucher.description,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        voucher.semester,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                DueBadge(status)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                formatRupees(parseRupees(voucher.amount)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(13.dp))
            InfoLine(Icons.Rounded.Event, "Due date", shortPortalDate(voucher.dueDate))
            Spacer(Modifier.height(8.dp))
            InfoLine(Icons.Rounded.ConfirmationNumber, "Voucher no.", voucher.number)

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDownload,
                enabled = !downloadBusy &&
                    voucher.printVoucherNumber.isNotBlank() &&
                    voucher.studentId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(AppRadius.Medium)
            ) {
                if (downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text("Opening voucher…")
                } else {
                    // Same receipt glyph as the Vouchers tab, so the action reads
                    // as "open this fee slip".
                    Icon(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text(
                        if (voucher.printVoucherNumber.isNotBlank() &&
                            voucher.studentId.isNotBlank()
                        ) {
                            "Open voucher"
                        } else {
                            "Voucher unavailable"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptScreen(
    transcript: Transcript?,
    studentName: String?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    manualPull: ManualPull,
    listState: LazyListState
) {
    if (transcript == null) {
        LoadStateContent("Loading transcript…", screenState, onRetry)
        return
    }

    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val completed = remember(transcript) { completedHours(transcript.courses) }
    val remaining = remember(transcript) { remainingHours(transcript.courses) }

    // Clean CGPA (unofficial): the portal formula without F/W/I/XF attempts.
    // Offered only when the transcript has such attempts; otherwise it would
    // simply equal the official figure.
    val clean = remember(transcript) { cleanCgpa(transcript.courses) }
    val cleanAvailable = remember(transcript) { clean != null && hasExcludedAttempts(transcript.courses) }
    var cleanCgpaEnabled by rememberSaveable { mutableStateOf(false) }
    val showClean = cleanCgpaEnabled && cleanAvailable
    // One-time hint until the toggle is first used (remembered on the device).
    val cleanDiscovered by OnboardingHints.cleanCgpaDiscovered(context).collectAsState()

    // Degree bar reveal, held at screen level (not inside the LazyColumn item)
    // so scrolling the card away and back never replays it. It replays when
    // the tab is opened again (the screen is recreated) and after every
    // finished refresh (the generation below bumps).
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var wasLoading by remember { mutableStateOf(screenState.loading) }
    LaunchedEffect(screenState.loading) {
        if (wasLoading && !screenState.loading) refreshGeneration++
        wasLoading = screenState.loading
    }
    val degreeProgress = rememberRevealedProgress(
        target = (completed.toFloat() / DEGREE_TOTAL_CREDIT_HOURS).coerceIn(0f, 1f),
        replayKey = refreshGeneration
    )
    val aGrades = transcript.courses.count { it.grade.trim().uppercase().startsWith("A") }
    val filterOptions = remember(transcript) { transcriptFilterOptions(transcript.courses) }
    var gradeFilter by rememberSaveable(transcript) { mutableStateOf(TRANSCRIPT_FILTER_ALL) }
    val activeFilter = gradeFilter.takeIf { it in filterOptions } ?: TRANSCRIPT_FILTER_ALL

    // IMPORTANT: Display the raw transcript rows exactly as returned by IULMS.
    // Do not deduplicate here; theory/lab rows can share similar course codes.
    val visibleCourses = remember(transcript, activeFilter) { filterTranscript(transcript.courses, activeFilter) }

    // Data-present branch only: `loading` here always means a refresh, never the first load.
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(screenState.loading),
        onRefresh = { manualPull.pulled = true; onPullRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomClearance()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeading(
                    "Transcript",
                    "Academic record and degree progress",
                    action = {
                        Row {
                            PolicyInfoButton()
                            IconButton(
                                onClick = {
                                    shareScope.launch {
                                        runCatching { shareTranscriptAsPng(context, studentName, transcript) }
                                            .onFailure {
                                                Toast.makeText(context, "Could not share transcript.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            ) {
                                // Same muted tint as InfoIconButton for visual
                                // harmony with the ⓘ beside it.
                                Icon(
                                    Icons.Rounded.Share,
                                    "Share transcript",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            item {
                HeroMetricCard(
                    label = "Cumulative GPA",
                    value = clean?.takeIf { showClean }?.let { String.format(Locale.US, "%.2f", it.gpa) }
                        ?: transcript.cgpa.ifBlank { "—" },
                    helper = when {
                        showClean -> "Unofficial · Excluding F/W/I"
                        cleanAvailable && !cleanDiscovered -> "✨ Tap icon to hide F/W grades"
                        else -> "${transcript.courses.size} transcript entries"
                    },
                    icon = Icons.Rounded.School,
                    trailing = if (cleanAvailable) {
                        {
                            CleanCgpaToggle(
                                checked = cleanCgpaEnabled,
                                pulse = !cleanDiscovered,
                                onCheckedChange = {
                                    OnboardingHints.markCleanCgpaDiscovered(context)
                                    cleanCgpaEnabled = it
                                }
                            )
                        }
                    } else null
                )
            }

            item {
                CreditProgressCard(completed = completed, remaining = remaining, animatedProgress = degreeProgress)
            }

            item {
                MetricStrip(
                    "Credits" to completed.toString(),
                    "Entries" to transcript.courses.size.toString(),
                    "A grades" to aGrades.toString()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Courses & labs", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when (activeFilter) {
                                TRANSCRIPT_FILTER_ALL -> "All transcript entries returned by IULMS"
                                TRANSCRIPT_FILTER_WEAK -> "${visibleCourses.size} weak entries (C+ or lower)"
                                else -> "${visibleCourses.size} entries graded $activeFilter"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(AppSpacing.Sm))
                    TranscriptGradeFilter(
                        options = filterOptions,
                        selected = activeFilter,
                        countOf = { filterTranscript(transcript.courses, it).size },
                        onSelect = { gradeFilter = it }
                    )
                }
            }

            if (visibleCourses.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.CheckCircle,
                        when (activeFilter) {
                            TRANSCRIPT_FILTER_ALL -> "No transcript entries"
                            TRANSCRIPT_FILTER_WEAK -> "No weak courses"
                            else -> "No $activeFilter entries"
                        },
                        when (activeFilter) {
                            TRANSCRIPT_FILTER_ALL -> "Transcript entries will appear here when returned by IULMS."
                            TRANSCRIPT_FILTER_WEAK -> "No C-grade or lower entries were found."
                            else -> "No entries with this grade were found."
                        }
                    )
                }
            } else {
                items(visibleCourses) { TranscriptCourseCard(it) }
            }

            item {
                Text(
                    "MyIULMS is an unofficial student-made client and is not affiliated with or endorsed by Iqra University.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Excluding-F/W/I toggle in the GPA card's trailing slot, where the round
 * icon normally sits: same 52dp circle, so the card does not grow. Off: a
 * soft tinted circle; on: filled primary. The card's figure and helper line
 * say what changed ("Unofficial · Excluding F/W/I").
 */
@Composable
private fun CleanCgpaToggle(checked: Boolean, pulse: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberAppHaptics()
    // Discoverability: until first used, the circle breathes 1.0 → 1.15 → 1.0.
    // The infinite transition exists only while [pulse] is true, so the first
    // tap removes it and the button is instantly back at rest. The scale is
    // read in graphicsLayer, so the loop only redraws, never recomposes.
    val pulseScale = if (pulse) {
        rememberInfiniteTransition(label = "cleanTogglePulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cleanTogglePulseScale"
        )
    } else {
        null
    }
    val container by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primary.copy(alpha = .12f),
        label = "cleanToggleContainer"
    )
    val content by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "cleanToggleContent"
    )
    IconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptics.tick()
            onCheckedChange(it)
        },
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                val scale = pulseScale?.value ?: 1f
                scaleX = scale
                scaleY = scale
            },
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = container,
            contentColor = content,
            checkedContainerColor = container,
            checkedContentColor = content
        )
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = if (checked) "Show official CGPA" else "Show CGPA without F, W and I grades (unofficial)"
        )
    }
}

@Composable
private fun CreditProgressCard(completed: Int, remaining: Int, animatedProgress: () -> Float) {
    val progress = (completed.toFloat() / DEGREE_TOTAL_CREDIT_HOURS).coerceIn(0f, 1f)
    val percent = (progress * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(AppSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Timeline,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Degree progress", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$completed / $DEGREE_TOTAL_CREDIT_HOURS credit hours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            // 4dp hairline with a flush track: gapSize = 0 removes the
            // Material gap between fill and track, and an empty
            // drawStopIndicator removes the stop dot the library draws at the
            // far right. StrokeCap.Round (library default) keeps the ends soft
            // without an outer clip, so the bar stays a clean straight rail.
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(Modifier.height(9.dp))

            Text(
                if (remaining > 0) "$remaining credit hours remaining"
                else "Degree credit-hour target reached",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TranscriptCourseCard(course: Course) {
    val gradeColor = gradeColor(course.grade)
    val meaning = gradeMeaning(course.grade)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = gradeColor.copy(alpha = .09f)
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(gradeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            course.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${course.code} • ${course.hours} credit hours",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GradeBadge(course.grade)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Grade points",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(course.gpa.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        meaning,
                        style = MaterialTheme.typography.labelLarge,
                        color = gradeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricCard(
    label: String,
    value: String,
    helper: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /**
     * Optional control in place of the round icon at the card's trailing edge
     * (e.g. a toggle), so it costs no extra height.
     */
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Hero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(5.dp))
                    // The figure slides when it changes (e.g. Clean CGPA on/off):
                    // the new value rises in as the old one leaves upward.
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
                                (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
                        },
                        label = "heroValue"
                    ) { shown ->
                        Text(
                            shown,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Cross-fades when the helper line changes meaning.
                    AnimatedContent(
                        targetState = helper,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                        label = "heroHelper"
                    ) { shown ->
                        Text(
                            shown,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (trailing != null) {
                    trailing()
                } else {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

/**
 * Two or three headline numbers in one card, split by thin dividers: one
 * shape for one group of related values. Never stacks; at large font sizes a
 * label wraps inside its own segment instead.
 */
@Composable
private fun MetricStrip(vararg metrics: Pair<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(vertical = AppSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            metrics.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.Sm)
                        .clearAndSetSemantics { contentDescription = "$label: $value" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * One compact button that opens a menu of grade filters with counts: All,
 * each grade present (best first), then Weak. The button shows the current
 * choice and is highlighted while anything other than All is applied.
 */
@Composable
private fun TranscriptGradeFilter(
    options: List<String>,
    selected: String,
    countOf: (String) -> Int,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = selected != TRANSCRIPT_FILTER_ALL
    Box {
        FilterChip(
            selected = filtered,
            onClick = { expanded = true },
            label = { Text(if (filtered) selected else "Filter") },
            leadingIcon = { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.semantics {
                contentDescription = "Filter by grade, showing ${if (filtered) selected else "all"}"
                role = Role.DropdownList
            }
        )
        MyIulmsDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                if (option == TRANSCRIPT_FILTER_WEAK) MyIulmsDropdownMenuDivider()
                MyIulmsDropdownMenuItem(
                    text = if (option == TRANSCRIPT_FILTER_WEAK) "Weak (C+ or lower)" else option,
                    checked = option == selected,
                    trailing = {
                        Text(
                            countOf(option).toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun gradeColor(grade: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val g = grade.trim().uppercase()

    return when {
        // B+ is STRONG (green) under the old scheme this cohort is graded by
        // (81% - 87%, 3.5 points) — see AcademicPolicy.oldScheme. It is
        // coloured strong everywhere instead of falling into the amber "B"
        // bucket, so the badge, the card tint and the policy table agree.
        g == "A" || g == "A-" || g == "B+" -> if (dark) GradeStrongDark else GradeStrongLight
        g.startsWith("B") -> if (dark) GradeAverageDark else GradeAverageLight
        g.startsWith("C") -> if (dark) GradeWeakDark else GradeWeakLight
        g == "D" || g == "F" -> if (dark) GradeCriticalDark else GradeCriticalLight
        else -> if (dark) GradeNeutralDark else GradeNeutralLight
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val color = gradeColor(grade)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        color = color.copy(alpha = if (dark) .18f else .12f),
        shape = RoundedCornerShape(AppRadius.Small)
    ) {
        Text(
            grade.ifBlank { "—" },
            Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DueBadge(status: DueInfo) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = when (status.kind) {
        DueKind.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        DueKind.SOON -> if (dark) WarningContainerDark else WarningContainerLight
        DueKind.NORMAL -> MaterialTheme.colorScheme.primaryContainer
    }
    val fg = when (status.kind) {
        DueKind.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        DueKind.SOON -> if (dark) WarningForegroundDark else WarningForegroundLight
        DueKind.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(color = bg, shape = RoundedCornerShape(AppRadius.Small)) {
        Text(
            status.label,
            Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun DurationBadge(duration: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape
    ) {
        Text(
            text = duration,
            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ScreenHeading(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null
) {
    // 48dp minimum, the height of an action icon: headings with and without
    // icons sit at the same height, so titles never jump between tabs.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        action?.invoke()
    }
}

/** Mid · Quiz · Project · Final in one row; wraps at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultMarksRow(row: ExamRow) {
    val marks = listOf("Mid" to row.midterm, "Quiz" to row.quizzes, "Project" to row.project, "Final" to row.finalExam)
        .map { (label, value) -> label to value.ifBlank { "—" } }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = marks.joinToString(", ") { (label, value) -> "$label $value" }
            },
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        maxItemsInEachRow = 4
    ) {
        val cell = Modifier.weight(1f).widthIn(min = 64.dp)
        marks.forEach { (label, value) -> CompactStat(value, label, cell) }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Single-line sibling of [InfoLine] for a constrained-width slot.
 *
 * Deliberately a SEPARATE composable rather than a defaulted parameter on
 * [InfoLine]: the shared one is the layout of record for the attendance,
 * transcript and voucher cards, and its unbounded vertical growth is correct
 * there. Changing it in place would restyle four other screens.
 *
 * Two differences make this safe to place side-by-side:
 *  - The value [Text] is capped at one line with an ellipsis. An unbounded
 *    `Text` wraps, which silently grows the card instead of truncating a long
 *    faculty name.
 *  - Neither the [Row] nor the inner [Column] forces its own width. Both take
 *    the incoming constraint as-is, so the caller decides the allocation
 *    entirely: `Modifier.weight(1f)` to absorb the leftover space, or
 *    `Modifier.wrapContentWidth()` to shrink to intrinsic content.
 *
 * Why neither forces a width: an internal `fillMaxWidth()` would override a
 * caller's `wrapContentWidth()` and make the block swallow the whole row, and an
 * internal `Column(weight(1f))` would do the same, because a weighted child with
 * no weighted sibling receives all remaining space. Truncation does not depend
 * on either: a `Text` capped at one line ellipsizes against whatever `maxWidth`
 * the Row's constraint imposes, which the caller's weight already bounds.
 */
@Composable
private fun InfoLineCompact(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // AppSpacing.Md, not the raw 10.dp this was. At 10.dp the glyph sat
        // closer to its own label than the two blocks sit to each other, so the
        // icons read as crowded even when the inter-block gap was correct.
        // Tokenised so it no longer drifts from the spacing scale.
        Spacer(Modifier.width(AppSpacing.Md))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Faculty-evaluation gate card ka action — HomeScreen isse provide karta hai
// (EvaluationActivity launcher). CompositionLocal isliye taake paancho screens
// ke signatures me onGateAction na jhoolna pade (LoadStateContent ek hi jagah
// se sab cover karta hai).
val LocalEvaluationGateAction = staticCompositionLocalOf<() -> Unit> { {} }

// "Evaluations required" — actionable state, error nahi. Portal har authenticated
// page ko gate par redirect karta hai jab tak student saare pending course
// evaluations complete nahi karta. Is card ke neeche ka data genuinely
// inaccessible hai — yeh dikhana hi sahi affordance hai, khaali screen ya
// "Connection problem" nahi.
@Composable
private fun FacultyEvaluationGateState(onGateAction: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(AppRadius.Large),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                Icon(
                    Icons.Rounded.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Evaluations required",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Complete your pending course evaluations to unlock your academic records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGateAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(AppRadius.Medium)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text("Start evaluations")
                }
            }
        }
    }
}

// Headless render target for the gate card (native-flow preview, `android
// preview`). Surface theme wrapper app ke dark/light default par chalta hai.
@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun FacultyEvaluationGateStatePreview() {
    MyIULMSTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FacultyEvaluationGateState(onGateAction = {})
        }
    }
}

@Composable
private fun LoadingPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadStateContent(
    loadingText: String,
    state: ScreenLoadState,
    onRetry: () -> Unit
) {
    // Priority order: gate card SABSE PEHLE. Gate ke waqt error null hota hai
    // (mutual exclusivity, GateStateTransitionTest) aur data bhi null hota hai
    // (onEvaluationReturned ne models null kiye) — warna UI "Connection problem"
    // ya infinite loading dikhata, jo misleading hota. Yeh ek hi jagah se paancho
    // screens (schedule/attendance/result/transcript/vouchers) cover ho jate hain.
    if (state.portalActionRequired) {
        FacultyEvaluationGateState(onGateAction = LocalEvaluationGateAction.current)
        return
    }
    val error = state.error
    if (error == null) {
        LoadingPlaceholder(loadingText)
        return
    }

    // The message says "Pull down to try again", so the error state must take a
    // pull too, on every tab. Same rule as the lists: the spinner shows only
    // for the pull itself. Pulling retries exactly like the button.
    val manualPull = remember { ManualPull() }
    LaunchedEffect(state.loading) {
        if (!state.loading) manualPull.pulled = false
    }
    MyIulmsPullToRefresh(
        isRefreshing = manualPull.isRefreshing(state.loading),
        onRefresh = { manualPull.pulled = true; onRetry() },
        modifier = Modifier.fillMaxSize()
    ) {
        // A pull needs scrollable content to drag. The column scrolls (it never
        // overflows) and is at least screen-tall, so the card stays centred.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            ErrorStateCard(
                error = error,
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(24.dp)
            )
        }
    }
}

@Composable
private fun ErrorStateCard(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                Icon(
                    Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Connection problem",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = AppSpacing.Sm)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
            },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(AppRadius.Medium)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                Spacer(Modifier.width(AppSpacing.Sm))
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

private enum class DueKind { OVERDUE, SOON, NORMAL }
private data class DueInfo(val label: String, val kind: DueKind)

private fun dueStatus(raw: String): DueInfo {
    return try {
        val date = parsePortalDate(raw) ?: return DueInfo("Due", DueKind.NORMAL)
        val days = ChronoUnit.DAYS.between(LocalDate.now(CampusZone), date)
        when {
            days < 0 -> DueInfo("Overdue", DueKind.OVERDUE)
            days == 0L -> DueInfo("Due today", DueKind.SOON)
            days <= 3 -> DueInfo("Due in $days d", DueKind.SOON)
            else -> DueInfo("Upcoming", DueKind.NORMAL)
        }
    } catch (_: Exception) {
        DueInfo("Due", DueKind.NORMAL)
    }
}

private fun nearestDueText(vouchers: List<Voucher>): String {
    val info = vouchers.map { dueStatus(it.dueDate) }
    return when {
        info.any { it.kind == DueKind.OVERDUE } -> "One or more vouchers are overdue"
        info.any { it.label == "Due today" } -> "Payment due today"
        else -> "${vouchers.size} payment item${if (vouchers.size == 1) "" else "s"}"
    }
}

private fun formatStudentName(name: String): String =
    name.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }

