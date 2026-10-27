package com.example.myiulms

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = IulmsSession.client
    private val store = SecureStore(app)
    private var updateCheckJob: Job? = null
    private val sharedPreferences: SharedPreferences
        get() = getApplication<Application>().getSharedPreferences("myilms_update_check", Context.MODE_PRIVATE)

    // 6-hour persistent cooldown for AUTOMATIC checks only.
    private val AUTOMATIC_CHECK_COOLDOWN_MS = 6L * 60 * 60 * 1000
    private val KEY_LAST_AUTOMATIC_CHECK = "last_automatic_check_ms"
    private var automaticCheckClaimedInWindow = false

    var loggedIn by mutableStateOf(false)
        private set
    // App-level faculty-evaluation gate flag. Sirf detection layer set karta hai;
    // clear sirf logout ya onEvaluationReturned() karta hai. Session expire nahi
    // hua hota is waqt — loggedIn alag rehta hai.
    var evaluationRequired by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set
    var lastUpdatedAt by mutableStateOf<Long?>(null)
        private set
    internal var updateCheckState by mutableStateOf<UpdateCheckState?>(null)
        private set
    var screenLoadStates by mutableStateOf<Map<Int, ScreenLoadState>>(emptyMap())
        private set

    var examResult by mutableStateOf<ExamResult?>(null)
        private set
    var examSchedule by mutableStateOf<ExamSchedule?>(null)
        private set
    var weeklySchedule by mutableStateOf<WeeklySchedule?>(null)
        private set
    var attendance by mutableStateOf<AttendanceSummary?>(null)
        private set
    var vouchers by mutableStateOf<List<Voucher>?>(null)
        private set
    var downloadingVoucherNumber by mutableStateOf<String?>(null)
        private set
    var voucherActionError by mutableStateOf<String?>(null)
        private set

    fun checkForUpdates() = runUpdateCheck(reportFailure = true)

    private fun runUpdateCheck(reportFailure: Boolean) {
        if (updateCheckState == UpdateCheckState.Checking) return

        updateCheckState = UpdateCheckState.Checking
        updateCheckJob = viewModelScope.launch {
            updateCheckState = try {
                val latest = withContext(Dispatchers.IO) { fetchLatestAppRelease() }
                val currentVersion = installedVersionName()
                if (isVersionNewer(latest.version, currentVersion)) {
                    UpdateCheckState.Available(currentVersion, latest)
                } else {
                    UpdateCheckState.UpToDate(currentVersion)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Automatic checks fail silently so the user is never interrupted.
                // Manual checks keep the existing visible error.
                if (reportFailure) {
                    UpdateCheckState.Failed(
                        "Couldn't check for updates. Check your internet connection and try again."
                    )
                } else {
                    null
                }
            }
        }
    }

    fun dismissUpdateCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = null
        updateCheckState = null
    }

    fun checkAutomaticUpdate() {
        // In-memory guard: stops repeated login-state changes / recomposition from
        // issuing more than one automatic request within the same 6-hour window.
        if (automaticCheckClaimedInWindow) return

        // Persistent 6-hour cooldown is checked BEFORE any network request.
        val now = System.currentTimeMillis()
        val lastCheck = sharedPreferences.getLong(KEY_LAST_AUTOMATIC_CHECK, 0L)
        if (lastCheck > 0L && now - lastCheck < AUTOMATIC_CHECK_COOLDOWN_MS) {
            automaticCheckClaimedInWindow = true
            return
        }

        // Cooldown expired: claim the window and persist the timestamp BEFORE the
        // request, so an in-flight request (or process death) cannot cause a second
        // automatic check on the next login.
        automaticCheckClaimedInWindow = true
        sharedPreferences.edit().putLong(KEY_LAST_AUTOMATIC_CHECK, now).commit()

        runUpdateCheck(reportFailure = false)
    }

    private fun installedVersionName(): String {
        @Suppress("DEPRECATION")
        return getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0)
            .versionName
            .orEmpty()
    }
    var transcript by mutableStateOf<Transcript?>(null)
        private set

    var studentName by mutableStateOf<String?>(null)
        private set
    var savedUser: String? = null
        private set
    var savedPassword: String? = null
        private set
    private var lastVoucherAction: Voucher? = null

    init {
        val saved = store.load()
        if (saved != null) {
            savedUser = saved.first
            savedPassword = saved.second
            launchTask { doLogin(saved.first, saved.second, true) }
        }
    }

    private fun launchTask(onSuccess: (() -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                block()
                onSuccess?.invoke()
            } catch (e: EvaluationRequiredException) {
                // Defensive net: doLogin gate khud handle karta hai (remember-me
                // save ke saath). Yahan bas flag ensure hota hai; loggedIn jo
                // hai wahi rehta hai — gate session-expiry nahi hai.
                evaluationRequired = true
            } catch (e: NotLoggedInException) {
                errorMsg = "Session expired. Please sign in again."
                loggedIn = false
            } catch (e: Exception) {
                errorMsg = e.toSignInErrorMessage()
            } finally {
                loading = false
            }
        }
    }

    private fun launchScreenTask(
        screen: Int,
        showRefreshFeedback: Boolean = false,
        block: suspend () -> Unit
    ) {
        if (screenLoadStates[screen]?.loading == true) return

        viewModelScope.launch {
            updateScreenState(screen) { screenStateLoading(it) }
            try {
                block()
                val now = System.currentTimeMillis()
                updateScreenState(screen) { screenStateSuccess() }
                if (showRefreshFeedback) lastUpdatedAt = now
            } catch (e: EvaluationRequiredException) {
                // Gate = actionable state, error NAHI: errorMsg (global banner) par
                // nahi chadhta, taake UI "Connection problem" na dikhaye.
                // Screen state ke through hi "Faculty evaluations required" card aata hai.
                evaluationRequired = true
                updateScreenState(screen) { screenStateGate() }
            } catch (e: NotLoggedInException) {
                val message = "Session expired. Please sign in again."
                errorMsg = message
                loggedIn = false
                updateScreenState(screen) { screenStateError(message) }
            } catch (_: Exception) {
                val name = when (screen) {
                    0 -> "schedule"
                    1 -> "attendance"
                    2 -> "result"
                    3 -> "transcript"
                    else -> "vouchers"
                }
                updateScreenState(screen) {
                    screenStateError("Couldn't load $name. Check your connection and try again.")
                }
            }
        }
    }

    private fun updateScreenState(screen: Int, update: (ScreenLoadState) -> ScreenLoadState) {
        val current = screenLoadStates[screen] ?: ScreenLoadState()
        screenLoadStates = screenLoadStates + (screen to update(current))
    }

    private suspend fun doLogin(user: String, pass: String, remember: Boolean) {
        var gatedDuringHomeFetch = false
        val loginResult = withContext(Dispatchers.IO) {
            val ok = client.login(user, pass)

            if (!ok) {
                Pair(false, null)
            } else {
                try {
                    val homeHtml = client.getHtml("/")
                    Pair(true, parseStudentName(homeHtml))
                } catch (e: EvaluationRequiredException) {
                    // Login kaamyab lekin home par gate redirect: yahan rokna zaroori
                    // hai, warna neeche ka remember-me save aur loggedIn=true chhut
                    // jate. Naam gate ke baad onEvaluationReturned() se wapas aata hai.
                    gatedDuringHomeFetch = true
                    Pair(true, null)
                }
            }
        }

        if (gatedDuringHomeFetch) evaluationRequired = true

        val ok = loginResult.first
        val name = loginResult.second

        if (ok) {
            studentName = name

            if (remember) {
                store.save(user, pass)
                savedUser = user
                savedPassword = pass
            } else {
                store.clear()
                savedUser = null
                savedPassword = null
            }

            loggedIn = true
        } else {
            errorMsg = "Sign in failed. Check your registration number and password."
        }
    }

    fun login(user: String, pass: String, remember: Boolean) = launchTask {
        doLogin(user, pass, remember)
    }

    // Signing out ends the network session, but remembered credentials remain
    // encrypted on the device. Unchecking Remember me on a successful login
    // removes them.
    fun logout() {
        client.logout()
        examResult = null
        examSchedule = null
        weeklySchedule = null
        attendance = null
        vouchers = null
        downloadingVoucherNumber = null
        voucherActionError = null
        screenLoadStates = emptyMap()
        transcript = null
        studentName = null
        errorMsg = null
        evaluationRequired = false
        loggedIn = false
    }

    fun loadExamResult(showRefreshFeedback: Boolean = false) = launchScreenTask(2, showRefreshFeedback) {
        examResult = withContext(Dispatchers.IO) {
            parseExamResult(client.getHtml("/sic/examresult.php"))
        }
    }

    fun loadSchedules(showRefreshFeedback: Boolean = false) = launchScreenTask(0, showRefreshFeedback) {
        val schedules = withContext(Dispatchers.IO) {
            val exam = parseExamSchedule(client.getHtml("/sic/examschedule.php"))
            val weekly = parseWeeklySchedule(client.getHtml("/sic/Schedule.php"))
            exam to weekly
        }
        examSchedule = schedules.first
        weeklySchedule = schedules.second
    }

    fun loadAttendance(showRefreshFeedback: Boolean = false) = launchScreenTask(1, showRefreshFeedback) {
        attendance = withContext(Dispatchers.IO) {
            parseAttendance(client.getHtml("/sic/StudentAttendance.php"))
        }
    }

    fun loadVouchers(showRefreshFeedback: Boolean = false) = launchScreenTask(4, showRefreshFeedback) {
        vouchers = withContext(Dispatchers.IO) {
            parseVouchers(client.getHtml("/sic/Vouchers.php"))
        }
    }

    fun openVoucher(
        context: android.content.Context,
        voucher: Voucher
    ) {
        if (downloadingVoucherNumber != null) return

        viewModelScope.launch {
            lastVoucherAction = voucher
            voucherActionError = null
            downloadingVoucherNumber = voucher.number

            try {
                val document = withContext(Dispatchers.IO) {
                    client.getVoucherDocument(voucher)
                }

                if (document.isPdf()) {
                    val uri = saveVoucherAsPdf(context, voucher, document)
                    if (!openVoucherPdf(context, uri)) {
                        voucherActionError = "Voucher saved, but no PDF viewer is installed."
                    }
                } else {
                    val html = document.asHtml()
                    if (!html.contains("<html", ignoreCase = true) &&
                        !html.contains("<!doctype", ignoreCase = true)
                    ) {
                        throw IllegalStateException("IULMS returned an unsupported voucher format")
                    }

                    val intent = android.content.Intent(
                        context,
                        VoucherPrintActivity::class.java
                    ).apply {
                        putExtra(VoucherPrintActivity.EXTRA_HTML, html)
                        putExtra(VoucherPrintActivity.EXTRA_VOUCHER_NUMBER, voucher.number)
                    }

                    context.startActivity(intent)
                }
            } catch (e: EvaluationRequiredException) {
                // Gate active: koi misleading voucher error nahi — screen-4 ka
                // gate card hi yahan sahi affordance hai.
                evaluationRequired = true
                updateScreenState(4) { screenStateGate() }
                voucherActionError = null
            } catch (e: NotLoggedInException) {
                voucherActionError = "Session expired. Please sign in again."
                errorMsg = "Session expired. Please sign in again."
                loggedIn = false
            } catch (e: Exception) {
                voucherActionError = "Voucher could not be opened. Check your connection and try again."
            } finally {
                downloadingVoucherNumber = null
            }
        }
    }

    fun retryVoucherAction(context: android.content.Context) {
        lastVoucherAction?.let { openVoucher(context, it) }
    }

    fun loadTranscript(showRefreshFeedback: Boolean = false) = launchScreenTask(3, showRefreshFeedback) {
        transcript = withContext(Dispatchers.IO) {
            val page = client.getHtml("/sic/Transcript.php")
            val degree = parseDegreeId(page)
                ?: throw IllegalStateException("Degree ID was not found")
            parseTranscript(client.getTranscriptJson(degree))
        }
    }

    // `showRefreshFeedback` gates only the success snackbar. The toolbar
    // button and the retry buttons confirm with it; the pull gesture passes
    // false because its own on-screen indicator is the success signal.
    fun refresh(tab: Int, showRefreshFeedback: Boolean = true) {
        when (tab) {
            0 -> loadSchedules(showRefreshFeedback)
            1 -> loadAttendance(showRefreshFeedback)
            2 -> loadExamResult(showRefreshFeedback)
            3 -> loadTranscript(showRefreshFeedback)
            else -> loadVouchers(showRefreshFeedback)
        }
    }

    // EvaluationActivity launch se pehle koi session-handover step nahi hai:
    // 100% native evaluation flow abhi bhi client ke apne OkHttp SessionCookieJar
    // se authenticated requests bhe deta hai (WebView bridge abhi bhi exist hi
    // nahi karta). EvaluationActivity khud gate list / course form / CAPTCHA le
    // lega. Return par onEvaluationReturned() re-probe karta hai.

    // EvaluationActivity se wapas aane par strict reset. Models null taake koi
    // stale empty screen (gate ke dauran parse hue khaali models) zinda na rahe;
    // screen states cleared; naam re-fetch (doLogin me gate par chhut jata hai);
    // aur active tab ka hard refresh = fresh portal re-probe. Agar evaluation
    // abhi bhi baaki hai (partial completion) to yehi load dobara
    // EvaluationRequiredException throw karke gate card wapas laga dega —
    // completion ka koi guess nahi, har return par asli re-probe.
    fun onEvaluationReturned(activeTab: Int) {
        evaluationRequired = false
        errorMsg = null
        examResult = null
        examSchedule = null
        weeklySchedule = null
        attendance = null
        vouchers = null
        transcript = null
        studentName = null
        screenLoadStates = emptyMap()
        refreshStudentName()
        refresh(activeTab, showRefreshFeedback = false)
    }

    // Gate ke dauran doLogin ka naam-fetch chhod jata hai — wapas aane par yahan
    // se wapas lao. Yahan har failure silent hai (naam cosmetic hai): gate ya
    // session error asli tab-load path se hi dikhte hain.
    private fun refreshStudentName() {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    parseStudentName(client.getHtml("/"))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            studentName = name
        }
    }
}

private fun Throwable.toSignInErrorMessage(): String {
    val cause = generateSequence(this) { it.cause }
        .firstOrNull {
            it is UnknownHostException ||
                it is ConnectException ||
                it is SocketTimeoutException ||
                it is IOException
        }

    return when (cause) {
        is SocketTimeoutException -> "IULMS took too long to respond. Please try again."
        is UnknownHostException,
        is ConnectException,
        is IOException -> "Couldn't reach IULMS. Check your internet connection and try again."
        else -> "Couldn't sign in right now. Please try again."
    }
}

data class ScreenLoadState(
    val loading: Boolean = false,
    val error: String? = null,
    // "Faculty evaluations required" — loading/error se mutually exclusive.
    // Gate ke waqt error NULL rehta hai, taake UI Connection problem na dikhaye.
    val portalActionRequired: Boolean = false
)

// ---------------- Screen-load state transitions (pure, unit-tested) ----------------
// Har transition inhi helpers se guzarta hai (GateStateTransitionTest) — is tarah
// loading / error / gate kabhi ek saath active nahi ho sakte: ek screen par ek
// waqt par in teeno mein se exactly ek hota hai.

internal fun screenStateLoading(previous: ScreenLoadState): ScreenLoadState =
    previous.copy(loading = true, error = null, portalActionRequired = false)

internal fun screenStateSuccess(): ScreenLoadState =
    ScreenLoadState(loading = false, error = null, portalActionRequired = false)

internal fun screenStateError(message: String): ScreenLoadState =
    ScreenLoadState(loading = false, error = message, portalActionRequired = false)

internal fun screenStateGate(): ScreenLoadState =
    ScreenLoadState(loading = false, error = null, portalActionRequired = true)
