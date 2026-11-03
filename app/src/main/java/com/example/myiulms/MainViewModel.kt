package com.example.myiulms

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiulms.data.PortalRepository
import com.example.myiulms.ui.widget.NextClassWidgetSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// Photo sirf memory mein rehti hai (README: "nothing is cached offline").
sealed interface PhotoState {
    data object Idle : PhotoState
    data object Loading : PhotoState
    data class Loaded(val photo: ImageBitmap) : PhotoState
    data object Unavailable : PhotoState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = IulmsSession.client
    private val store = SecureStore(app)
    private val portal = PortalRepository(app)
    private var updateCheckJob: Job? = null
    private val sharedPreferences: SharedPreferences
        get() = getApplication<Application>().getSharedPreferences("myilms_update_check", Context.MODE_PRIVATE)

    // 6-hour persistent cooldown for AUTOMATIC checks only.
    private val AUTOMATIC_CHECK_COOLDOWN_MS = 6L * 60 * 60 * 1000
    private val KEY_LAST_AUTOMATIC_CHECK = "last_automatic_check_ms"
    private var automaticCheckClaimedInWindow = false

    // Har sign-in/sign-out (logout, session expiry, naya account) yahin se
    // guzarta hai, isliye widget ko yahin batate hain.
    private var loggedInState by mutableStateOf(false)
    var loggedIn: Boolean
        get() = loggedInState
        private set(value) {
            loggedInState = value
            // Either outcome ends a cached session: signed in for real, or out.
            offlineSession = false
            NextClassWidgetSession.onAuthChanged(getApplication(), value)
        }

    // Cached session: remembered credentials plus a saved schedule for the same
    // account, shown before (or without) a successful network sign-in. Only the
    // Schedule tab has data; every portal call waits for loggedIn.
    var offlineSession by mutableStateOf(false)
        private set
    val showHome: Boolean get() = loggedIn || offlineSession
    /** Cached session currently trying to reach the portal. */
    val signingInFromCache: Boolean get() = offlineSession && loading
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
    /** When [weeklySchedule] was fetched; with [weeklyScheduleFromCache], drives the "last updated" note. */
    var weeklyScheduleUpdatedAt by mutableStateOf<Long?>(null)
        private set
    var weeklyScheduleFromCache by mutableStateOf(false)
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
    private val _studentIdentity = MutableStateFlow<StudentIdentity?>(null)
    val studentIdentity: StateFlow<StudentIdentity?> = _studentIdentity.asStateFlow()
    private val _photoState = MutableStateFlow<PhotoState>(PhotoState.Idle)
    val photoState: StateFlow<PhotoState> = _photoState.asStateFlow()
    private var photoJob: Job? = null

    // savedUser "Remember me" off par null hota hai; Reg ID ko yeh chahiye.
    private var loggedInUser: String? = null

    var savedUser: String? = null
        private set
    var savedPassword: String? = null
        private set
    private var lastVoucherAction: Voucher? = null

    /**
     * True until the remembered login (and, with it, the saved schedule) has been
     * read. The UI shows a plain background meanwhile, so neither the login
     * screen nor the home screen flashes up before the right one is known.
     */
    var restoringSession by mutableStateOf(true)
        private set

    init {
        // The encrypted store (EncryptedSharedPreferences + Android Keystore) is
        // opened here for the first time, which blocked the main thread for
        // ~85 ms of the first frame. It now runs on IO; the first frame draws at
        // once and the session is restored a moment later.
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrNull() }
            if (saved != null) {
                savedUser = saved.first
                savedPassword = saved.second
                // Instant launch: read the saved schedule before revealing any
                // screen, so a cached session opens straight onto it.
                restoreSavedSchedule(saved.first)
                launchTask { doLogin(saved.first, saved.second, true) }
            }
            restoringSession = false
        }
    }

    private suspend fun restoreSavedSchedule(username: String) {
        val cached = runCatching { portal.cachedWeeklyScheduleFor(username) }.getOrNull() ?: return
        if (weeklySchedule != null || loggedIn) {
            // Sign-in already won; the Schedule tab's own load shows the cache.
            return
        }
        weeklySchedule = cached.payload
        weeklyScheduleUpdatedAt = cached.fetchedAt
        weeklyScheduleFromCache = true
        offlineSession = true
    }

    /** Pull-to-refresh in a cached session: retry the sign-in, which then loads the tab. */
    private fun retryCachedSignIn() {
        val user = savedUser ?: return
        val pass = savedPassword ?: return
        if (!offlineSession || loading) return
        launchTask { doLogin(user, pass, true) }
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
                    Pair(true, parseStudentIdentity(homeHtml))
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
        val identity = loginResult.second

        if (ok) {
            loggedInUser = user
            applyStudentIdentity(identity)

            if (remember) {
                withContext(Dispatchers.IO) { store.save(user, pass) }
                savedUser = user
                savedPassword = pass
            } else {
                withContext(Dispatchers.IO) { store.clear() }
                savedUser = null
                savedPassword = null
            }

            // Before loggedIn: the widget must never render another account's cache.
            withContext(Dispatchers.IO) { portal.setActiveOwner(user, persist = remember) }
            loggedIn = true
        } else {
            errorMsg = "Sign in failed. Check your registration number and password."
            // Credentials rejected: the saved copy must not stay on screen.
            offlineSession = false
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
        portal.clear()
        examResult = null
        examSchedule = null
        clearWeeklySchedule()
        attendance = null
        vouchers = null
        downloadingVoucherNumber = null
        voucherActionError = null
        screenLoadStates = emptyMap()
        transcript = null
        studentName = null
        loggedInUser = null
        _studentIdentity.value = null
        resetStudentPhoto()
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
        try {
            coroutineScope {
                val exam = async(Dispatchers.IO) { parseExamSchedule(client.getHtml("/sic/examschedule.php")) }
                portal.weeklyScheduleUpdates(loggedInUser).collect { update ->
                    // The saved copy only fills an empty screen; it never replaces
                    // data already on it.
                    if (update.fromCache && weeklySchedule != null) return@collect
                    weeklySchedule = update.value
                    weeklyScheduleUpdatedAt = update.fetchedAt
                    weeklyScheduleFromCache = update.fromCache
                }
                examSchedule = exam.await()
            }
        } catch (e: EvaluationRequiredException) {
            // The gate card only shows on an empty screen; a saved schedule must
            // never hide it.
            clearWeeklySchedule()
            throw e
        }
        NextClassWidgetSession.onDataChanged(getApplication())
    }

    private fun clearWeeklySchedule() {
        weeklySchedule = null
        weeklyScheduleUpdatedAt = null
        weeklyScheduleFromCache = false
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
        if (!loggedIn) {
            retryCachedSignIn()
            return
        }
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
        clearWeeklySchedule()
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
            val identity = withContext(Dispatchers.IO) {
                try {
                    parseStudentIdentity(client.getHtml("/"))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            applyStudentIdentity(identity)
        }
    }

    private fun applyStudentIdentity(identity: StudentIdentity?) {
        studentName = identity?.studentName?.takeIf { it.isNotBlank() }
        // Session expire ke baad doosra student login kar sakta hai (logout() nahi
        // chalta) — purani photo us ke card par nahi rehni chahiye.
        if (identity?.moodleUserId != _studentIdentity.value?.moodleUserId) resetStudentPhoto()
        _studentIdentity.value = identity?.copy(universityRegId = loggedInUser)
    }

    fun loadStudentPhoto() {
        val state = _photoState.value
        if (state is PhotoState.Loading || state is PhotoState.Loaded) return
        val moodleUserId = _studentIdentity.value?.moodleUserId
        if (moodleUserId == null) {
            _photoState.value = PhotoState.Unavailable
            return
        }

        _photoState.value = PhotoState.Loading
        photoJob = viewModelScope.launch {
            val bitmap = client.getProfilePhoto(moodleUserId)?.let { bytes ->
                withContext(Dispatchers.Default) { decodeStudentPhoto(bytes) }
            }
            _photoState.value = bitmap
                ?.let { PhotoState.Loaded(it.asImageBitmap()) }
                ?: PhotoState.Unavailable
        }
    }

    // Cancel zaroori hai: warna in-flight fetch logout ke baad photo wapas likh deta.
    private fun resetStudentPhoto() {
        photoJob?.cancel()
        photoJob = null
        _photoState.value = PhotoState.Idle
    }
}

private const val STUDENT_PHOTO_MAX_DIMENSION_PX = 512

// Portal ki photo ka size hamare haath mein nahi, isliye pehle bounds, phir downsample.
private fun decodeStudentPhoto(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= STUDENT_PHOTO_MAX_DIMENSION_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
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

/**
 * A tab's state as the screen should show it. In a cached session the portal
 * has not been reached: a sign-in attempt reads as a refresh, otherwise as an
 * error with retry, so no tab spins forever.
 */
fun MainViewModel.screenStateFor(tab: Int): ScreenLoadState {
    val state = screenLoadStates[tab] ?: ScreenLoadState()
    return when {
        loggedIn -> state
        signingInFromCache -> state.copy(loading = true, error = null)
        offlineSession -> state.copy(loading = false, error = state.error ?: OFFLINE_MESSAGE)
        else -> state
    }
}

const val OFFLINE_MESSAGE = "Couldn't reach IULMS. Pull down to try again."

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
