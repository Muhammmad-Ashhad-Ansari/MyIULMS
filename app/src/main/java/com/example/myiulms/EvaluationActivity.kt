package com.example.myiulms

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing
import com.example.myiulms.ui.theme.GradeNeutralLight
import com.example.myiulms.ui.theme.GradeStrongDark
import com.example.myiulms.ui.theme.GradeStrongLight
import com.example.myiulms.ui.theme.MyIULMSTheme
import com.example.myiulms.ui.theme.WarningContainerDark
import com.example.myiulms.ui.theme.WarningContainerLight
import com.example.myiulms.ui.theme.WarningForegroundDark
import com.example.myiulms.ui.theme.WarningForegroundLight
import com.example.myiulms.ui.theme.resolveSavedDarkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- Native faculty evaluation flow ----------------
// 100% native (no WebView): gate list → course form → CAPTCHA → native POST.
// Exit contract: every exit path sets RESULT_OK + finish() — MainActivity's
// onEvaluationReturned() re-probes the portal, which stays the single source of
// truth for whether the gate is still open.

class EvaluationActivity : ComponentActivity() {

    private val vm: EvaluationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App ka in-app theme choice (system nahi) — bar icons bhi usi se match.
        val darkTheme = resolveSavedDarkTheme(this)
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContent {
            MyIULMSTheme(darkTheme = darkTheme) {
                val snackbarHostState = remember { SnackbarHostState() }
                // Composition ka scope main thread par hai — finish()/setResult() yahin.
                LaunchedEffect(Unit) {
                    // Quick-fill snackbar Indefinite hai — iska job cancel karna hi
                    // use hatata hai (showSnackbar cancel par snackbar remove karta hai).
                    var quickFillSnackbar: Job? = null
                    vm.eventFlow.collect { event ->
                        when (event) {
                            is EvaluationEvent.Message -> launch { snackbarHostState.showSnackbar(event.text) }
                            EvaluationEvent.Finish -> finishWithResult()
                            is EvaluationEvent.QuickFilled -> {
                                quickFillSnackbar?.cancel()
                                // Naya fill purana snackbar hata de — queue mein stale Undo na rahe.
                                snackbarHostState.currentSnackbarData?.dismiss()
                                quickFillSnackbar = launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = event.message,
                                        actionLabel = if (event.undoToken != null) "Undo" else null,
                                        withDismissAction = true,
                                        // Undo scroll ke baad bhi milna chahiye — Undo, ✕, manual
                                        // rating edit ya course change tak dikhta rehta hai.
                                        duration = if (event.undoToken != null) {
                                            SnackbarDuration.Indefinite
                                        } else {
                                            SnackbarDuration.Short
                                        }
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        event.undoToken?.let(vm::undoQuickFill)
                                    }
                                }
                            }
                            EvaluationEvent.QuickFillUndoExpired -> {
                                quickFillSnackbar?.cancel()
                                quickFillSnackbar = null
                            }
                        }
                    }
                }
                EvaluationFlow(
                    vm = vm,
                    snackbarHostState = snackbarHostState,
                    onClose = { finishWithResult() }
                )
            }
        }
    }

    private fun finishWithResult() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}

// ---------------- UI ----------------

@Composable
private fun EvaluationFlow(
    vm: EvaluationViewModel,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit
) {
    val course = vm.selectedCourse
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBackToList = {
        if (vm.hasUnsavedInput) confirmDiscard = true else vm.backToList()
    }

    // Submit ke dauran back block — POST beech mein chhod kar form state na bigde.
    BackHandler(enabled = !vm.submitting) {
        if (course == null) onClose() else requestBackToList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            course == null -> EvaluationListScreen(
                courses = vm.courses,
                loading = vm.listLoading,
                error = vm.listError,
                onRetry = vm::loadGateList,
                onRefreshAppData = onClose,
                onCourseClick = vm::openCourse,
                snackbarHostState = snackbarHostState
            )
            vm.formLoading -> EvaluationFormStatusScaffold(course = course, onBack = requestBackToList) {
                EvaluationLoadingContent(message = "Opening ${course.title}...")
            }
            vm.formError != null -> EvaluationFormStatusScaffold(course = course, onBack = requestBackToList) {
                EvaluationErrorContent(
                    message = vm.formError.orEmpty(),
                    onRetry = vm::retryForm,
                    secondaryLabel = "Back to list",
                    onSecondary = requestBackToList
                )
            }
            vm.form != null -> CourseEvaluationFormScreen(
                course = course,
                form = vm.form!!,
                answers = vm.answers,
                onAnswer = vm::setAnswer,
                comments = vm.comments,
                onComment = vm::setComment,
                captchaBitmap = vm.captchaBitmap,
                captchaLoading = vm.captchaLoading,
                captchaError = vm.captchaError,
                captchaInput = vm.captchaInput,
                onCaptchaChange = vm::updateCaptchaInput,
                onRefreshCaptcha = vm::refreshCaptcha,
                submitting = vm.submitting,
                submitError = vm.submitError,
                onSubmit = vm::submit,
                onBack = requestBackToList,
                onQuickFill = vm::quickFill,
                snackbarHostState = snackbarHostState
            )
        }
        // SnackbarHost ab har Scaffold ke apne slot mein — form par sticky Submit bar
        // ke upar aata hai, usse dhakta nahi.
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(text = "Discard answers?") },
            text = { Text(text = "Your ratings and comments for this course haven't been submitted and will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.backToList()
                }) { Text(text = "Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(text = "Keep editing") }
            }
        )
    }
}

// Form ke loading/error states bhi top bar ke saath — back affordance hamesha visible.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationFormStatusScaffold(
    course: EvaluationCourse,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = { EvaluationCourseTopBar(course = course, onBack = onBack) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationCourseTopBar(course: EvaluationCourse, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = course.instructor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to course list")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationListScreen(
    courses: List<EvaluationCourse>?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onRefreshAppData: () -> Unit,
    onCourseClick: (EvaluationCourse) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Course evaluations") },
                navigationIcon = {
                    IconButton(onClick = onRefreshAppData) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close evaluations")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> EvaluationLoadingContent()
                error != null -> EvaluationErrorContent(message = error, onRetry = onRetry)
                else -> {
                    val list = courses ?: emptyList()
                    val pending = list.filter { !it.completed }
                    val completedCount = list.count { it.completed }
                    if (pending.isEmpty()) {
                        EvaluationAllCompleteContent(onRefreshAppData = onRefreshAppData)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                        ) {
                            // Plain progress header — pehle AssistChip tha jo TalkBack par
                            // "button" bolta tha magar kuch karta nahi tha.
                            EvaluationProgressHeader(
                                label = "$completedCount of ${list.size} completed",
                                done = completedCount,
                                total = list.size,
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.Xs,
                                    vertical = AppSpacing.Xs
                                )
                            )
                            list.forEach { course ->
                                EvaluationCourseCard(course = course, onClick = { onCourseClick(course) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationLoadingContent(message: String = "Loading evaluations...") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Xxl))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppSpacing.Xxl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EvaluationErrorContent(
    message: String,
    onRetry: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
            modifier = Modifier.padding(AppSpacing.Xxl)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) { Text(text = "Retry") }
            secondaryLabel?.let { label ->
                TextButton(onClick = onSecondary) { Text(text = label) }
            }
        }
    }
}

@Composable
private fun EvaluationAllCompleteContent(onRefreshAppData: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            modifier = Modifier.padding(AppSpacing.Xxl)
        ) {
            Text(
                text = "All evaluations complete",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Every pending course evaluation is done. Nice work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRefreshAppData, modifier = Modifier.padding(top = AppSpacing.Sm)) {
                Text(text = "Refresh app data")
            }
        }
    }
}

@Composable
private fun EvaluationProgressHeader(label: String, done: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun EvaluationCourseCard(course: EvaluationCourse, onClick: (EvaluationCourse) -> Unit) {
    // Completed course portal par dobara nahi khulta — card disabled + muted, ripple bhi nahi.
    val surface = MaterialTheme.colorScheme.surface
    Card(
        onClick = { onClick(course) },
        enabled = !course.completed,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (course.completed) 0.6f else 1f),
        shape = RoundedCornerShape(AppRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = surface,
            disabledContainerColor = surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = course.instructor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EvaluationStatusChip(completed = course.completed)
        }
    }
}

@Composable
private fun EvaluationStatusChip(completed: Boolean) {
    // AcademicPolicySheet ke SUCCESS/CAUTION badge recipe jaisa — existing theme
    // tokens se, koi naya colour literal nahi.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (container, content) = if (completed) {
        val accent = if (dark) GradeStrongDark else GradeStrongLight
        accent.copy(alpha = if (dark) 0.20f else 0.14f) to accent
    } else {
        if (dark) WarningContainerDark to WarningForegroundDark else WarningContainerLight to WarningForegroundLight
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text = if (completed) "Completed" else "Pending",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseEvaluationFormScreen(
    course: EvaluationCourse,
    form: EvaluationForm,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit,
    comments: Map<String, String>,
    onComment: (String, String) -> Unit,
    captchaBitmap: Bitmap?,
    captchaLoading: Boolean,
    captchaError: Boolean,
    captchaInput: String,
    onCaptchaChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    submitting: Boolean,
    submitError: String?,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onQuickFill: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val allAnswered = form.questions.isNotEmpty() &&
        form.questions.all { answers[it.name]?.isNotBlank() == true }
    val captchaReady = captchaInput.isNotBlank()
    val canSubmit = allAnswered && captchaReady && !submitting
    val answeredCount = form.questions.count { answers[it.name]?.isNotBlank() == true }

    // Aakhri question answer hote hi (quick fill ya manual) agla step dikhao —
    // sirf false→true transition par; restore/recomposition par already-complete
    // form ko scroll nahi karte.
    val commentsRequester = remember { BringIntoViewRequester() }
    val captchaRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var wasAllAnswered by remember { mutableStateOf(allAnswered) }
    LaunchedEffect(allAnswered) {
        if (allAnswered && !wasAllAnswered && form.comments.isNotEmpty()) {
            commentsRequester.bringIntoView()
        }
        wasAllAnswered = allAnswered
    }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        // Keyboard khule to bottom bar (error + Submit) keyboard ke upar rahe.
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Sticky: course naam top bar mein, progress uske neeche — content mein
            // course card ka duplicate header hata diya.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    EvaluationCourseTopBar(course = course, onBack = onBack)
                    EvaluationProgressHeader(
                        label = "$answeredCount of ${form.questions.size} answered",
                        done = answeredCount,
                        total = form.questions.size,
                        modifier = Modifier.padding(
                            start = AppSpacing.Lg,
                            end = AppSpacing.Lg,
                            bottom = AppSpacing.Md
                        )
                    )
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    // Error Submit button ke paas — scroll position kuch bhi ho, dikhta hai.
                    submitError?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(AppRadius.Small),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Assertive }
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = 10.dp)
                            )
                        }
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(text = if (submitting) "Submitting..." else "Submit evaluation")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            // Portal ka apna document order (Strongly Disagree → Strongly Agree) —
            // alphabetical sort scale ko tod deta tha.
            val quickFillOptions = remember(form.questions) {
                form.questions.flatMap { q -> q.options.map { it.label } }.distinct()
            }
            val unansweredCount = form.questions.size - answeredCount

            // Quick fill = ek halki strip, card/badge/toggle nahi. Rule automatic hai
            // (VM): sirf baaki questions bharta hai — empty form par sab. Sab answered
            // ho jayein to strip chhup jati hai (bharne ko kuch nahi); badalna ho to
            // Undo snackbar ya question ka apna pill.
            if (unansweredCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (answeredCount == 0) {
                                "One tap fills all ${form.questions.size} questions"
                            } else {
                                "One tap fills the $unansweredCount remaining"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickFillOptions.forEach { label ->
                            RatingPill(
                                label = label,
                                selected = false,
                                onClick = { onQuickFill(label) },
                                role = Role.Button,
                                accent = true,
                                description = if (answeredCount == 0) {
                                    "Set all questions to $label"
                                } else {
                                    "Set remaining questions to $label"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Text(
                text = quickFillOptions.joinToString("  ·  ") { "${ratingShortLabel(it)} = $it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Questions",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = AppSpacing.Xs).semantics { heading() }
            )

            form.questions.forEachIndexed { index, question ->
                EvaluationSectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                    ) {
                        Text(
                            text = "${index + 1}. ${question.prompt.ifBlank { "Question ${index + 1}" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            question.options.forEach { option ->
                                RatingPill(
                                    label = option.label,
                                    selected = answers[question.name] == option.value,
                                    onClick = { onAnswer(question.name, option.value) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Poora comments group bring-into-view — sirf pehli field target karne
            // par woh viewport ke bilkul neeche aati thi aur Undo snackbar ke peeche chhup jati.
            Column(
                modifier = Modifier.bringIntoViewRequester(commentsRequester),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg)
            ) {
                Text(
                    text = "Your comments",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                form.comments.forEach { comment ->
                    val value = comments[comment.name] ?: ""
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updated -> onComment(comment.name, updated.take(comment.maxLength)) },
                            label = { Text(text = if (comment.prompt.isBlank()) "Comments" else comment.prompt) },
                            maxLines = 4,
                            supportingText = { Text(text = "${value.length} / ${comment.maxLength}") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        CommentSuggestionChips(
                            suggestions = remember(comment.prompt) { commentSuggestions(comment.prompt) },
                            currentText = value,
                            onAdd = { phrase ->
                                onComment(comment.name, appendCommentPhrase(value, phrase, comment.maxLength))
                            }
                        )
                    }
                }
            }

            // CAPTCHA image field ke UPAR — keyboard khulne par field keyboard ke
            // theek upar scroll hoti hai, image uske upar dikhti rehti hai.
            // Field focus par poora card (image + field) view mein laao — keyboard
            // animation ke baad, warna sirf field dikhti hai aur image upar kat sakti hai.
            EvaluationSectionCard(modifier = Modifier.bringIntoViewRequester(captchaRequester)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AppSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
                ) {
                    Text(
                        text = "Verification code",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(AppRadius.Small))
                                // Portal image ka apna safed background — dono themes mein readable frame.
                                .background(Color.White)
                        ) {
                            val bitmap = captchaBitmap
                            when {
                                bitmap != null -> Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Verification code image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(AppSpacing.Xs)
                                )
                                // Fail ko spinner ke peeche mat chhupao — user ko refresh ka pata chale.
                                captchaError && !captchaLoading -> Text(
                                    text = "Couldn't load the code. Tap ↻ to try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GradeNeutralLight, // frame hamesha white — light token dono themes mein
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = AppSpacing.Md)
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                )
                                else -> CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .semantics { contentDescription = "Loading verification code" },
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        // Bada tonal button (56dp) — chhota icon miss hota tha. Row ki height
                        // image box (64dp, fixed) se tay hoti hai: loading/error/image mein shift nahi.
                        FilledTonalIconButton(
                            onClick = onRefreshCaptcha,
                            enabled = !captchaLoading && !submitting,
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Get a new code")
                        }
                    }
                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = onCaptchaChange,
                        singleLine = true,
                        label = { Text(text = "Type the code shown above") },
                        // Monospace + spacing: 0/O, 1/l/I jaise characters alag dikhein.
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    scope.launch {
                                        delay(350) // IME resize settle hone do
                                        captchaRequester.bringIntoView()
                                    }
                                }
                            },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            if (canSubmit) onSubmit()
                        })
                    )
                }
            }
            // Submit + submitError sirf bottomBar mein (sticky) — content mein duplicate nahi.
            // Indefinite quick-fill snackbar content ke upar overlay hota hai — utni
            // jagah chhodo taake scroll ke end par CAPTCHA field uske peeche na phanse.
            if (snackbarHostState.currentSnackbarData != null) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

// One-tap phrases — tap par field mein append (replace nahi); jo phrase text mein
// pehle se hai uska chip ✓ dikhata hai aur dobara nahi jodta.
@Composable
private fun CommentSuggestionChips(
    suggestions: List<String>,
    currentText: String,
    onAdd: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
        Text(
            text = "Tap to add, then edit in your own words",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Ek line, sideways scroll — FlowRow mein bade font par har chip apni line
        // le leta tha (3 fields x 6 chips = bohot lambi screen).
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            suggestions.forEach { phrase ->
                val added = currentText.contains(phrase, ignoreCase = true)
                FilterChip(
                    selected = added,
                    onClick = { if (!added) onAdd(phrase) },
                    label = { Text(text = phrase.removeSuffix(".")) },
                    // secondaryContainer theme mein define nahi (baseline lavender) — brand tones.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        iconColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (added) "$phrase Added" else "Add: $phrase"
                    }
                )
            }
        }
    }
}

// "Strongly Disagree" → "SD", "Neutral" → "N".
private fun ratingShortLabel(label: String): String =
    label.split(' ').filter { it.isNotBlank() }.joinToString("") { it.first().uppercase() }
        .ifBlank { label }

// Question/CAPTCHA cards: brand surface + 1dp border. Default Card container
// (M3 baseline surfaceContainerHighest) theme mein define nahi — lavender tint aata tha.
@Composable
private fun EvaluationSectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) { content() }
}

@Composable
private fun RatingPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // RadioButton = question ka jawab; Button = Quick fill action (select state nahi).
    role: Role = Role.RadioButton,
    description: String = label,
    // Quick-fill action pills: brand border/text taake answer pills se alag dikhein.
    accent: Boolean = false
) {
    // Unselected border: onSurfaceVariant @ 75% — light theme mein white par ~3.1:1
    // (WCAG 1.4.11 non-text contrast). `outline` token (#DCE4EC) sirf ~1.3:1 deta.
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            accent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = when {
            selected -> null
            accent -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (role == Role.RadioButton) {
                    Modifier.selectable(selected = selected, role = role, onClick = onClick)
                } else {
                    Modifier.clickable(role = role, onClick = onClick)
                }
            )
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = AppSpacing.Md)) {
            Text(
                text = ratingShortLabel(label),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}
