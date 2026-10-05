package com.example.myiulms

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myiulms.ui.dashboard.ScheduleFocus
import com.example.myiulms.ui.dashboard.ScheduleFocusCard
import com.example.myiulms.ui.dashboard.liveSecondsRemaining
import com.example.myiulms.ui.dashboard.minuteOfDay
import com.example.myiulms.ui.dashboard.normalizeDay
import com.example.myiulms.ui.dashboard.resolveScheduleFocus
import com.example.myiulms.ui.dashboard.todayAbbrev
import com.example.myiulms.ui.policy.AcademicPolicyHost
import com.example.myiulms.ui.policy.LocalAcademicPolicyState
import com.example.myiulms.ui.policy.PolicyInfoButton
import com.example.myiulms.ui.policy.PolicyOverflowItem
import com.example.myiulms.ui.policy.rememberAcademicPolicyState
import com.example.myiulms.ui.theme.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uiPrefs = getSharedPreferences("iulms_ui", MODE_PRIVATE)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        setContent {
            var darkTheme by remember {
                mutableStateOf(
                    if (uiPrefs.contains("dark_theme")) {
                        uiPrefs.getBoolean("dark_theme", systemDark)
                    } else {
                        systemDark
                    }
                )
            }

            MyIULMSTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        darkTheme = darkTheme,
                        onThemeToggle = {
                            val next = !darkTheme
                            darkTheme = next
                            uiPrefs.edit { putBoolean("dark_theme", next) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun App(
    vm: MainViewModel = viewModel(),
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    if (vm.loggedIn) {
        HomeScreen(vm, darkTheme, onThemeToggle)
    } else {
        LoginScreen(vm, darkTheme, onThemeToggle, onCheckForUpdates = vm::checkForUpdates)
    }

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

@Composable
private fun LoginScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    var user by remember(vm.savedUser) { mutableStateOf(vm.savedUser.orEmpty()) }
    var pass by remember(vm.savedPassword) { mutableStateOf(vm.savedPassword.orEmpty()) }
    var rememberPassword by remember { mutableStateOf(vm.savedPassword != null) }
    var showPassword by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Text(
                    text = "MyIULMS",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Unofficial student client for IULMS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    "Welcome back",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Your student portal, made simple.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppRadius.Hero),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it.filter(Char::isDigit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.Username },
                            label = { Text("Registration number") },
                            leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(AppRadius.Medium),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.Password },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Rounded.VisibilityOff
                                        else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(AppRadius.Medium),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboard?.hide()
                                    if (!vm.loading && user.isNotBlank() && pass.isNotBlank()) {
                                        vm.login(user.trim(), pass, rememberPassword)
                                    }
                                }
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberPassword,
                                onCheckedChange = { rememberPassword = it }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Remember me", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Keep my login encrypted on this device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        vm.errorMsg?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                },
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(AppRadius.Medium)
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.ErrorOutline, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(message, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                keyboard?.hide()
                                vm.login(user.trim(), pass, rememberPassword)
                            },
                            enabled = !vm.loading && user.isNotBlank() && pass.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(AppRadius.Medium)
                        ) {
                            if (vm.loading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Unofficial student app · Not affiliated with Iqra University",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "by Not_Einstein",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.Md),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                IconButton(onClick = onCheckForUpdates) {
                    Icon(
                        Icons.Rounded.Update,
                        contentDescription = "Check for updates",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Number of top-level tabs: Schedule, Attendance, Result, Transcript, Vouchers.
private const val TAB_COUNT = 5

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { TAB_COUNT }

    // Pager -> tab. Only the settled page updates `tab`, so data loading and the
    // existing `when (tab)` content keep working off a single source of truth.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tab = page
        }
    }

    // tab -> pager. Keeps NavigationBar taps in sync with a settled swipe. The
    // guard prevents a feedback loop with the collector above.
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) {
            pagerState.animateScrollToPage(tab)
        }
    }
    val useCompactNavLabels = LocalDensity.current.fontScale >= 1.3f
    val showNavLabels = LocalDensity.current.fontScale < 1.4f
    val screenState = vm.screenLoadStates[tab] ?: ScreenLoadState()
    val hasData = when (tab) {
        0 -> vm.examSchedule != null && vm.weeklySchedule != null
        1 -> vm.attendance != null
        2 -> vm.examResult != null
        3 -> vm.transcript != null
        else -> vm.vouchers != null
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.lastUpdatedAt) {
        if (vm.lastUpdatedAt != null) {
            snackbarHostState.showSnackbar(
                message = "Updated just now",
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> if (vm.examSchedule == null || vm.weeklySchedule == null) vm.loadSchedules()
            1 -> if (vm.attendance == null) vm.loadAttendance()
            2 -> if (vm.examResult == null) vm.loadExamResult()
            3 -> if (vm.transcript == null) vm.loadTranscript()
            4 -> if (vm.vouchers == null) vm.loadVouchers()
        }
    }

    val context = LocalContext.current
    val policyState = rememberAcademicPolicyState()

    CompositionLocalProvider(LocalAcademicPolicyState provides policyState) {
        Scaffold(
            topBar = {
                PortalTopBar(
                    darkTheme = darkTheme,
                    onThemeToggle = onThemeToggle,
                    onRefresh = { vm.refresh(tab) },
                    refreshing = screenState.loading,
                    onCheckForUpdates = vm::checkForUpdates,
                    onLogout = vm::logout,
                    studentName = vm.studentName
                )
            },
            bottomBar = {
                // Floating capsule rather than a full-width NavigationBar.
                // The slot accepts any composable, so Scaffold still reserves the
                // capsule's measured height and the `padding` consumed below stays
                // correct, which is what keeps the last list card clear of it.
                FloatingNavCapsule(
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    useCompactLabels = useCompactNavLabels,
                    showLabels = showNavLabels
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (screenState.loading && hasData) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Refreshing current screen" }
                    )
                }
                if (hasData && screenState.error != null) {
                    ErrorBanner(screenState.error, onRetry = { vm.refresh(tab) })
                }
                if (tab == 4 && vm.voucherActionError != null) {
                    ErrorBanner(
                        message = vm.voucherActionError!!,
                        onRetry = { vm.retryVoucherAction(context) }
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> SchedulesScreen(
                            vm.examSchedule,
                            vm.weeklySchedule,
                            screenState,
                            onRetry = { vm.refresh(tab) },
                            snackbarHostState = snackbarHostState
                        )
                        1 -> AttendanceScreen(vm.attendance, screenState, onRetry = { vm.refresh(tab) })
                        2 -> ResultScreen(vm.examResult, vm.studentName, screenState, onRetry = { vm.refresh(tab) })
                        3 -> TranscriptScreen(vm.transcript, vm.studentName, screenState, onRetry = { vm.refresh(tab) })
                        else -> VoucherScreen(
                            vouchers = vm.vouchers,
                            downloadingVoucherNumber = vm.downloadingVoucherNumber,
                            screenState = screenState,
                            onRetry = { vm.refresh(tab) },
                            onDownload = { voucher -> vm.openVoucher(context, voucher) }
                        )
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

/**
 * Height of the gradient fade drawn above the capsule.
 *
 * Softens the hard cut where list content meets the bar.
 */
private val ScrollFadeHeight = 32.dp

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
 * Bottom inset shared by every authenticated list.
 *
 * A tab switch must not change how much scrollable room a list has, so this is
 * a plain function of the capsule geometry rather than a per-screen literal.
 * The system navigation inset is read at composition time: `enableEdgeToEdge`
 * is on, so the gesture bar is not already accounted for by the Scaffold.
 */
@Composable
private fun listBottomInset(): androidx.compose.ui.unit.Dp {
    val navigationBar = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return AppSpacing.Md + CapsuleHeight + ScrollFadeHeight + navigationBar
}

/**
 * Destinations, in tab order. [NavDestinations.size] is what the pager and the
 * `when (tab)` dispatch below rely on, so the two must stay in lockstep.
 */
private val NavDestinations = listOf(
    NavDestination(Icons.Rounded.Event, "Schedule", "Sched.", "Schedule"),
    NavDestination(Icons.AutoMirrored.Rounded.FactCheck, "Attend.", "Attend", "Attendance"),
    NavDestination(Icons.Rounded.Assessment, "Result", "Result", "Results"),
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
    showLabels: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CAPSULE_FILL_ALPHA)
    // Dark mode needs a light rim: `surface` is already near-black there, so a
    // dark stroke would be invisible. Light mode needs a dark rim for the same
    // reason in reverse.
    val isDark = isSystemInDarkTheme()
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

    Column(modifier = Modifier.fillMaxWidth()) {
        // Scroll fade above the capsule: opaque theme background at the capsule's
        // top edge fading to fully transparent upward, so list cards dissolve
        // into the bar instead of being sliced by it. Drawn first so the capsule
        // composites over it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrollFadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
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
                    .padding(horizontal = CapsuleHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(CapsuleItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestinations.forEachIndexed { index, destination ->
                    NavItem(
                        selected = index == selectedIndex,
                        onClick = {
                            // Tick only on a real change; re-tapping the active
                            // tab is a no-op and must not buzz.
                            if (index != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                        },
                        icon = destination.icon,
                        label = if (useCompactLabels) destination.compactLabel else destination.label,
                        // Accessibility always uses the full destination name.
                        // An abbreviated label like "Transcr." is announced
                        // verbatim by TalkBack, which is not what it means.
                        contentDescription = destination.accessibilityLabel,
                        showLabel = showLabels
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
    showLabel: Boolean
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
            .height(NavPillHeight)
            .clip(CircleShape)
            .background(pillColor)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalTopBar(
    studentName: String?,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onCheckForUpdates: () -> Unit,
    onLogout: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Refreshing current screen"
                            },
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh current screen")
                }
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Check for updates") },
                        leadingIcon = { Icon(Icons.Rounded.Update, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCheckForUpdates()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        }
                    )

                    PolicyOverflowItem(onDismissMenu = { menuExpanded = false })
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun UpdateCheckDialog(
    state: UpdateCheckState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                when (state) {
                    is UpdateCheckState.Available -> Icons.Rounded.SystemUpdate
                    is UpdateCheckState.UpToDate -> Icons.Rounded.CheckCircle
                    is UpdateCheckState.Failed -> Icons.Rounded.CloudOff
                    UpdateCheckState.Checking -> Icons.Rounded.SystemUpdate
                },
                contentDescription = null
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
                is UpdateCheckState.UpToDate -> Text("You're using the latest version (${state.currentVersion}).")
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
    onRetry: () -> Unit
) {
    if (result == null) {
        LoadStateContent("Loading latest result…", screenState, onRetry)
        return
    }

    val context = LocalContext.current
    val totals = result.rows.mapNotNull { it.total.toDoubleOrNull() }
    val average = if (totals.isNotEmpty()) totals.average() else null
    val highest = totals.maxOrNull()
    val aGrades = result.rows.count { it.grade.trim().uppercase().startsWith("A") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListGutter,
            top = AppSpacing.Md,
            end = ListGutter,
            bottom = listBottomInset()
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
    ) {
        item {
            ScreenHeading(
                title = "Academic result",
                subtitle = result.title
                    .replace("EXAM RESULT", "", ignoreCase = true)
                    .trim(' ', '(', ')', '-')
                    .ifBlank { "Latest examination" },
                action = {
                    Row {
                        PolicyInfoButton()
                        IconButton(
                            onClick = {
                                runCatching { shareResultAsPng(context, studentName, result) }
                                    .onFailure {
                                        Toast.makeText(context, "Could not share result.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        ) {
                            Icon(Icons.Rounded.Share, "Share result")
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
                InsightRow(
                    Triple("Average", formatOneDecimal(average), Icons.Rounded.QueryStats),
                    Triple("Highest", formatOneDecimal(highest ?: average), Icons.Rounded.EmojiEvents),
                    Triple("A grades", aGrades.toString(), Icons.Rounded.Grade)
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
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(10.dp))
                GradeBadge(row.grade)
            }

            Spacer(Modifier.height(15.dp))

            ResultMetricGrid(row)

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
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
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

        Box(modifier = Modifier.weight(1f)) {
            if (selectedSubTab == 0) {
                WeeklyScheduleContent(
                    schedule = weeklySchedule,
                    screenState = screenState,
                    onRetry = onRetry,
                    snackbarHostState = snackbarHostState,
                    enabled = selectedSubTab == 0
                )
            } else {
                ExamScheduleContent(examSchedule, screenState, onRetry)
            }
        }
    }
}

@Composable
private fun ExamScheduleContent(
    schedule: ExamSchedule?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit
) {
    if (schedule == null) {
        LoadStateContent("Loading exam schedule…", screenState, onRetry)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListGutter,
            top = AppSpacing.Md,
            end = ListGutter,
            bottom = listBottomInset()
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
    ) {
        item {
            ScreenHeading(
                title = "Exam schedule",
                subtitle = schedule.title.ifBlank { "Examination schedule" }
            )
        }

        if (!schedule.notice.isNullOrBlank()) {
            item {
                NoticeCard(message = schedule.notice)
            }
        }

        item {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyScheduleContent(
    schedule: WeeklySchedule?,
    screenState: ScreenLoadState,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState,
    enabled: Boolean
) {
    if (schedule == null) {
        LoadStateContent("Loading weekly schedule…", screenState, onRetry)
        return
    }

    val dayOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val groups = schedule.entries
        .sortedBy { scheduleStartMinutes(it.time) }
        .groupBy { normalizeDay(it.day) }
        .toSortedMap(
            compareBy(
                { day -> dayOrder.indexOf(day).let { if (it < 0) dayOrder.size else it } },
                { day -> day }
            )
        )

    val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val classDays = groups.keys
    var selectedDay by remember(schedule) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        val focusState = rememberScheduleFocusState(
            schedule = schedule,
            enabled = enabled
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ListGutter,
                top = AppSpacing.Md,
                end = ListGutter,
                bottom = listBottomInset()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            // The screen heading and the day chips now scroll WITH the list
            // instead of sitting in static chrome above it, which is what hands
            // their ~145.dp back to the entries on a swipe up.
            item(key = ITEM_KEY_HEADING) {
                ScreenHeading(
                    title = "Weekly class schedule",
                    subtitle = schedule.title.ifBlank { "Your semester classes" }
                )
            }

            item(key = ITEM_KEY_DAY_CHIPS) {
                WeeklyDayChipsRow(
                    weekDays = weekDays,
                    classDays = classDays,
                    selectedDay = selectedDay,
                    onSelectDay = { day ->
                        selectedDay = day
                        scope.launch {
                            listState.animateScrollToItem(
                                dayHeaderItemIndex(
                                    day = day,
                                    weekDays = weekDays,
                                    classDays = classDays,
                                    groups = groups
                                )
                            )
                        }
                    },
                    onEmptyDay = { day ->
                        scope.launch {
                            snackbarHostState.showSnackbar("No classes scheduled on ${day.lowercase().replaceFirstChar(Char::uppercase)}")
                        }
                    }
                )
            }

            item(key = ITEM_KEY_DASHBOARD) {
                ScheduleFocusCard(
                    focus = focusState.focus,
                    secondsRemaining = focusState.secondsRemaining
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
                    stickyHeader(key = "day-$day") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Text(
                                text = day,
                                modifier = Modifier.padding(vertical = AppSpacing.Sm),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(entries, key = { entry -> "$day-${entry.courseCode}-${entry.time}" }) { entry ->
                        WeeklyScheduleCard(entry)
                    }
                }
            }
        }
    }
}

/**
 * Gutter for the weekly schedule list.
 *
 * Named because the heading, the day chips and the schedule cards below them
 * must all resolve to the same horizontal axis. Three separate `18.dp` literals
 * is exactly the kind of duplication that drifts.
 */
private val ListGutter = 18.dp

/** Keys of the structural items that precede the day groups. */
private const val ITEM_KEY_HEADING = "weekly-heading"
private const val ITEM_KEY_DAY_CHIPS = "weekly-day-chips"
private const val ITEM_KEY_DASHBOARD = "class-dashboard"

/**
 * Number of structural items the list emits before the first day group:
 * heading, day chips, countdown card.
 *
 * [dayHeaderItemIndex] adds this to every target, so adding or removing a
 * structural header item is a single-line change here rather than a silent
 * off-by-N in the scroll arithmetic.
 */
private const val ITEMS_BEFORE_DAY_GROUPS = 3

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
 * LazyList index of [day]'s sticky header.
 *
 * Walks the day groups in the same order the list emits them and counts what has
 * already been placed:
 *
 *  - [ITEMS_BEFORE_DAY_GROUPS] structural items (heading, day chips, countdown
 *    card) always precede the groups.
 *  - For each earlier day that has classes, one sticky header plus that day's
 *    entry cards.
 *
 * The earlier days are taken from [weekDays] rather than from [groups] so the
 * weekday ordering cannot drift from the chip order the student is tapping. Days
 * with no classes are skipped, matching the list, which emits no group for them.
 */
internal fun dayHeaderItemIndex(
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
 * Dashboard tick state.
 *
 * [focus] is recomputed on each tick; the ticker only runs while [enabled] and
 * while a class is actually live, so there is no off-screen or idle work.
 */
private data class ScheduleFocusUiState(
    val focus: ScheduleFocus,
    val secondsRemaining: Int
)

@Composable
private fun rememberScheduleFocusState(
    schedule: WeeklySchedule?,
    enabled: Boolean
): ScheduleFocusUiState {
    val entries = remember(schedule) { schedule?.entries.orEmpty() }

    // 1 Hz heartbeat. Only advances while the weekly sub-tab is visible AND a
    // class is live; otherwise the coroutine is not running at all.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isLive by remember { mutableStateOf(false) }

    LaunchedEffect(entries, enabled) {
        if (!enabled) return@LaunchedEffect
        val initial = resolveScheduleFocus(
            entries = entries,
            today = todayAbbrev(),
            minuteOfDay = minuteOfDay()
        )
        isLive = initial is ScheduleFocus.Live
        nowMillis = System.currentTimeMillis()
    }

    LaunchedEffect(isLive, enabled) {
        if (!enabled || !isLive) return@LaunchedEffect
        while (enabled && isLive) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val focus = remember(entries, nowMillis) {
        resolveScheduleFocus(
            entries = entries,
            today = todayAbbrev(),
            minuteOfDay = minuteOfDay(nowMillis)
        )
    }

    val seconds = remember(focus, nowMillis) {
        when (focus) {
            is ScheduleFocus.Live -> liveSecondsRemaining(focus.entry, nowMillis)
            else -> 0
        }
    }

    return ScheduleFocusUiState(focus = focus, secondsRemaining = seconds)
}

@Composable
private fun WeeklyScheduleCard(entry: WeeklyScheduleEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (entry.location.isNotBlank()) InfoLine(Icons.Rounded.LocationOn, "Room", entry.location)
            if (entry.faculty.isNotBlank()) InfoLine(Icons.Rounded.Person, "Faculty", entry.faculty)
            if (entry.courseCode.isNotBlank() || entry.edpCode.isNotBlank()) {
                Text(
                    text = listOf(entry.courseCode, entry.edpCode).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    screenState: ScreenLoadState,
    onRetry: () -> Unit
) {
    if (attendance == null) {
        LoadStateContent("Loading attendance…", screenState, onRetry)
        return
    }

    val totalSessions = attendance.courses.sumOf { it.totalSessions }
    val totalPresent = attendance.courses.sumOf { it.present }
    val percent = if (totalSessions > 0) (totalPresent * 100f / totalSessions).toInt() else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListGutter,
            top = AppSpacing.Md,
            end = ListGutter,
            bottom = listBottomInset()
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
    ) {
        item {
            ScreenHeading(
                title = "Attendance",
                subtitle = "Course-wise sessions and class records"
            )
        }
        item {
            HeroMetricCard(
                label = "Overall attendance",
                value = if (totalSessions > 0) "$percent%" else "—",
                helper = if (totalSessions > 0) "$totalPresent of $totalSessions sessions attended"
                else "No attendance sessions recorded yet",
                icon = Icons.AutoMirrored.Rounded.FactCheck
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
            items(attendance.courses) { course -> AttendanceCourseCard(course) }
        }
    }
}

@Composable
private fun AttendanceCourseCard(course: AttendanceCourse) {
    var expanded by remember(course.name) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Medium),
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
                Text(
                    text = course.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    AttendanceMetric("Present", course.present.toString(), Modifier.weight(1f))
                    AttendanceMetric("Absent", course.absent.toString(), Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    AttendanceMetric("Sessions", course.totalSessions.toString(), Modifier.weight(1f))
                    AttendanceMetric(
                        "Attend. %",
                        if (course.totalSessions > 0) "${course.attendancePercent}%" else "—",
                        Modifier.weight(1f)
                    )
                }
            }
            if (expanded) {
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

@Composable
private fun AttendanceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.Small),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
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
    onDownload: (Voucher) -> Unit
) {
    if (vouchers == null) {
        LoadStateContent("Loading vouchers…", screenState, onRetry)
        return
    }

    val totalOutstanding = vouchers.sumOf { parseMoney(it.amount) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListGutter,
            top = AppSpacing.Md,
            end = ListGutter,
            bottom = listBottomInset()
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
    ) {
        item {
            ScreenHeading(
                "Fee vouchers",
                if (vouchers.isEmpty()) "No outstanding balance"
                else "${vouchers.size} outstanding voucher${if (vouchers.size == 1) "" else "s"}"
            )
        }

        if (vouchers.isNotEmpty()) {
            item {
                HeroMetricCard(
                    label = "Total outstanding",
                    value = "Rs ${formatMoney(totalOutstanding)}",
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
                    Text(voucher.description, style = MaterialTheme.typography.titleMedium)
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
                "Rs ${voucher.amount}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(13.dp))
            InfoLine(Icons.Rounded.Event, "Due date", voucher.dueDate)
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
                    Icon(Icons.Rounded.Download, contentDescription = null)
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
    onRetry: () -> Unit
) {
    if (transcript == null) {
        LoadStateContent("Loading transcript…", screenState, onRetry)
        return
    }

    val context = LocalContext.current
    val completed = remember(transcript) { completedHours(transcript.courses) }
    val remaining = remember(transcript) { remainingHours(transcript.courses) }
    val aGrades = transcript.courses.count { it.grade.trim().uppercase().startsWith("A") }
    var weakOnly by remember(transcript) { mutableStateOf(false) }

    // IMPORTANT: Display the raw transcript rows exactly as returned by IULMS.
    // Do not deduplicate here; theory/lab rows can share similar course codes.
    val visibleCourses = remember(transcript, weakOnly) {
        if (weakOnly) {
            transcript.courses.filter { isWeakCourse(it.grade) }
        } else {
            transcript.courses
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListGutter,
            top = AppSpacing.Md,
            end = ListGutter,
            bottom = listBottomInset()
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
                                runCatching { shareTranscriptAsPng(context, studentName, transcript) }
                                    .onFailure {
                                        Toast.makeText(context, "Could not share transcript.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        ) {
                            Icon(Icons.Rounded.Share, "Share transcript")
                        }
                    }
                }
            )
        }

        item {
            HeroMetricCard(
                "Cumulative GPA",
                transcript.cgpa.ifBlank { "—" },
                "${transcript.courses.size} transcript entries",
                Icons.Rounded.School
            )
        }

        item {
            CreditProgressCard(completed = completed, remaining = remaining)
        }

        item {
            InsightRow(
                Triple("Credits", completed.toString(), Icons.AutoMirrored.Rounded.MenuBook),
                Triple("Entries", transcript.courses.size.toString(), Icons.AutoMirrored.Rounded.LibraryBooks),
                Triple("A grades", aGrades.toString(), Icons.Rounded.Grade)
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
                        if (weakOnly) "${visibleCourses.size} weak/critical entries"
                        else "All transcript entries returned by IULMS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = weakOnly,
                    onClick = { weakOnly = !weakOnly },
                    label = { Text("Weak only") },
                    leadingIcon = if (weakOnly) {
                        { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        if (visibleCourses.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.CheckCircle,
                    if (weakOnly) "No weak courses" else "No transcript entries",
                    if (weakOnly) "No C-grade or lower entries were found."
                    else "Transcript entries will appear here when returned by IULMS."
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

@Composable
private fun CreditProgressCard(completed: Int, remaining: Int) {
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

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
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
                            style = MaterialTheme.typography.titleMedium
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
    icon: ImageVector
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Hero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

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

@Composable
private fun InsightRow(vararg values: Triple<String, String, ImageVector>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                values.forEach { item ->
                    InsightTile(
                        item = item,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                values.forEach { item ->
                    InsightTile(
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightTile(
    item: Triple<String, String, ImageVector>,
    modifier: Modifier = Modifier
) {
    val (label, value, icon) = item

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.Medium),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(AppSpacing.Md))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        g == "A" || g == "A-" -> if (dark) GradeStrongDark else GradeStrongLight
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
    Row(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ResultMetricGrid(row: ExamRow) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    MetricTile("Mid", row.midterm, Modifier.weight(1f))
                    MetricTile("Quiz", row.quizzes, Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    MetricTile("Project", row.project, Modifier.weight(1f))
                    MetricTile("Final", row.finalExam, Modifier.weight(1f))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                MetricTile("Mid", row.midterm, Modifier.weight(1f))
                MetricTile("Quiz", row.quizzes, Modifier.weight(1f))
                MetricTile("Project", row.project, Modifier.weight(1f))
                MetricTile("Final", row.finalExam, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(AppRadius.Medium),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    val error = state.error
    if (error == null) {
        LoadingPlaceholder(loadingText)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

private fun parseMoney(value: String): Double =
    value.replace(",", "").replace("Rs", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0

private fun formatMoney(value: Double): String =
    NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(value)

private fun formatOneDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

private enum class DueKind { OVERDUE, SOON, NORMAL }
private data class DueInfo(val label: String, val kind: DueKind)

private fun dueStatus(raw: String): DueInfo {
    return try {
        val clean = raw.replace(Regex("(?<=\\d)(st|nd|rd|th)"), "")
        val date = LocalDate.parse(
            clean,
            DateTimeFormatter.ofPattern("EEEE, d MMMM, yyyy", Locale.ENGLISH)
        )
        val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
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

