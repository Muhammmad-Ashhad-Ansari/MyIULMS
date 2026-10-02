package com.example.myiulms

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = IulmsClient()
    private val store = SecureStore(app)

    var loggedIn by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set
    var lastUpdatedAt by mutableStateOf<Long?>(null)
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
            updateScreenState(screen) { it.copy(loading = true, error = null) }
            try {
                block()
                val now = System.currentTimeMillis()
                updateScreenState(screen) { it.copy(loading = false, error = null) }
                if (showRefreshFeedback) lastUpdatedAt = now
            } catch (e: NotLoggedInException) {
                val message = "Session expired. Please sign in again."
                errorMsg = message
                loggedIn = false
                updateScreenState(screen) { it.copy(loading = false, error = message) }
            } catch (_: Exception) {
                val name = when (screen) {
                    0 -> "schedule"
                    1 -> "attendance"
                    2 -> "result"
                    3 -> "transcript"
                    else -> "vouchers"
                }
                updateScreenState(screen) {
                    it.copy(
                        loading = false,
                        error = "Couldn't load $name. Check your connection and try again."
                    )
                }
            }
        }
    }

    private fun updateScreenState(screen: Int, update: (ScreenLoadState) -> ScreenLoadState) {
        val current = screenLoadStates[screen] ?: ScreenLoadState()
        screenLoadStates = screenLoadStates + (screen to update(current))
    }

    private suspend fun doLogin(user: String, pass: String, remember: Boolean) {
        val loginResult = withContext(Dispatchers.IO) {
            val ok = client.login(user, pass)

            if (!ok) {
                Pair(false, null)
            } else {
                val homeHtml = client.getHtml("/")
                Pair(true, parseStudentName(homeHtml))
            }
        }

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

    fun refresh(tab: Int) {
        when (tab) {
            0 -> loadSchedules(showRefreshFeedback = true)
            1 -> loadAttendance(showRefreshFeedback = true)
            2 -> loadExamResult(showRefreshFeedback = true)
            3 -> loadTranscript(showRefreshFeedback = true)
            else -> loadVouchers(showRefreshFeedback = true)
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
    val error: String? = null
)
