package com.example.myiulms

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.myiulms.ui.theme.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val GradeStrong = Color(0xFF2E7D32)
private val GradeAverage = Color(0xFFF9A825)
private val GradeWeak = Color(0xFFEF6C00)
private val GradeCritical = Color(0xFFC62828)
private val GradeNeutral = Color(0xFF757575)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uiPrefs = getSharedPreferences("iulms_ui", Context.MODE_PRIVATE)
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
                            uiPrefs.edit().putBoolean("dark_theme", next).apply()
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
    if (vm.loggedIn) {
        HomeScreen(vm, darkTheme, onThemeToggle)
    } else {
        LoginScreen(vm, darkTheme, onThemeToggle)
    }
}

@Composable
private fun LoginScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
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
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
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
                    "Access your results, fee vouchers and academic record.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Registration number") },
                            leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            modifier = Modifier.fillMaxWidth(),
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
                            shape = RoundedCornerShape(16.dp),
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
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp)
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
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (vm.loading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Credentials are stored encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Independent student project • Not affiliated with or endorsed by Iqra University",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .78f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "by Not_Einstein",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> if (vm.examResult == null) vm.loadExamResult()
            1 -> if (vm.vouchers == null) vm.loadVouchers()
            2 -> if (vm.transcript == null) vm.loadTranscript()
        }
    }

    Scaffold(
        topBar = {
            PortalTopBar(
                darkTheme = darkTheme,
                onThemeToggle = onThemeToggle,
                onRefresh = { vm.refresh(tab) },
                onLogout = vm::logout,
                studentName = vm.studentName
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                NavItem(tab == 0, { tab = 0 }, Icons.Rounded.Assessment, "Result")
                NavItem(tab == 1, { tab = 1 }, Icons.Rounded.ReceiptLong, "Vouchers")
                NavItem(tab == 2, { tab = 2 }, Icons.Rounded.School, "Transcript")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> ResultScreen(vm.examResult, vm.studentName)
                1 -> VoucherScreen(vm.vouchers)
                else -> TranscriptScreen(vm.transcript, vm.studentName)
            }

            if (vm.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            vm.errorMsg?.let { ErrorBanner(it) }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        label = { Text(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalTopBar(
    studentName: String?,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.myiulms_app_icon),
                    contentDescription = "MyIULMS",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(10.dp))
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
            IconButton(onClick = onThemeToggle) {
                Icon(
                    if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Switch theme"
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, "Refresh")
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Rounded.ExitToApp, "Logout")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun ResultScreen(result: ExamResult?, studentName: String?) {
    if (result == null) {
        LoadingPlaceholder("Loading latest result…")
        return
    }

    val context = LocalContext.current
    val totals = result.rows.mapNotNull { it.total.toDoubleOrNull() }
    val average = if (totals.isNotEmpty()) totals.average() else null
    val highest = totals.maxOrNull()
    val aGrades = result.rows.count { it.grade.trim().uppercase().startsWith("A") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            ScreenHeading(
                title = "Academic result",
                subtitle = result.title
                    .replace("EXAM RESULT", "", ignoreCase = true)
                    .trim(' ', '(', ')', '-')
                    .ifBlank { "Latest examination" },
                action = {
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
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    row.course,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                GradeBadge(row.grade)
            }

            Spacer(Modifier.height(15.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MetricTile("Mid", row.midterm, Modifier.weight(1f))
                MetricTile("Quiz", row.quizzes, Modifier.weight(1f))
                MetricTile("Project", row.project, Modifier.weight(1f))
                MetricTile("Final", row.finalExam, Modifier.weight(1f))
            }

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

@Composable
private fun VoucherScreen(vouchers: List<Voucher>?) {
    if (vouchers == null) {
        LoadingPlaceholder("Loading vouchers…")
        return
    }

    val totalOutstanding = vouchers.sumOf { parseMoney(it.amount) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
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
            items(vouchers) { VoucherCard(it) }
        }
    }
}

@Composable
private fun VoucherCard(voucher: Voucher) {
    val status = dueStatus(voucher.dueDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(17.dp)) {
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
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Download voucher")
            }
            Text(
                "Download will activate after the LMS voucher file/link is mapped.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TranscriptScreen(transcript: Transcript?, studentName: String?) {
    if (transcript == null) {
        LoadingPlaceholder("Loading transcript…")
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeading(
                "Transcript",
                "Academic record and degree progress",
                action = {
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
                Triple("Credits", completed.toString(), Icons.Rounded.MenuBook),
                Triple("Entries", transcript.courses.size.toString(), Icons.Rounded.LibraryBooks),
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
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
        shape = RoundedCornerShape(19.dp),
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
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
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
    icon: ImageVector
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f)
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f)
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { (label, value, icon) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(value, style = MaterialTheme.typography.titleMedium)
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

private fun gradeColor(grade: String): Color {
    val g = grade.trim().uppercase()
    return when {
        g == "A" || g == "A-" -> GradeStrong
        g.startsWith("B") -> GradeAverage
        g.startsWith("C") -> GradeWeak
        g == "D" || g == "F" -> GradeCritical
        else -> GradeNeutral
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val color = gradeColor(grade)
    Surface(
        color = color.copy(alpha = .14f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            grade.ifBlank { "—" },
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DueBadge(status: DueInfo) {
    val bg = when (status.kind) {
        DueKind.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        DueKind.SOON -> WarningSoft
        DueKind.NORMAL -> MaterialTheme.colorScheme.primaryContainer
    }
    val fg = when (status.kind) {
        DueKind.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        DueKind.SOON -> Warning
        DueKind.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
        Text(
            status.label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg
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
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(14.dp),
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
        shape = RoundedCornerShape(21.dp),
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxScope.ErrorBanner(message: String) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp)
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
