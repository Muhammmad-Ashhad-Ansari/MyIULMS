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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = IulmsClient()
    private val store = SecureStore(app)

    var loggedIn by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var examResult by mutableStateOf<ExamResult?>(null)
        private set
    var vouchers by mutableStateOf<List<Voucher>?>(null)
        private set
    var downloadingVoucherNumber by mutableStateOf<String?>(null)
        private set
    var transcript by mutableStateOf<Transcript?>(null)
        private set

    var studentName by mutableStateOf<String?>(null)
        private set
    var savedUser: String? = null
        private set
    var savedPassword: String? = null
        private set

    init {
        val saved = store.load()
        if (saved != null) {
            savedUser = saved.first
            savedPassword = saved.second
            launchTask { doLogin(saved.first, saved.second, true) }
        }
    }

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                block()
            } catch (e: NotLoggedInException) {
                errorMsg = "Session expired. Please sign in again."
                loggedIn = false
            } catch (e: Exception) {
                errorMsg = "Something went wrong: ${e.message ?: "Unknown error"}"
            } finally {
                loading = false
            }
        }
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
        vouchers = null
        downloadingVoucherNumber = null
        transcript = null
        studentName = null
        errorMsg = null
        loggedIn = false
    }

    fun loadExamResult() = launchTask {
        examResult = withContext(Dispatchers.IO) {
            parseExamResult(client.getHtml("/sic/examresult.php"))
        }
    }

    fun loadVouchers() = launchTask {
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
            downloadingVoucherNumber = voucher.number
            errorMsg = null

            try {
                val document = withContext(Dispatchers.IO) {
                    client.getVoucherDocument(voucher)
                }

                if (document.isPdf()) {
                    val uri = saveVoucherAsPdf(context, voucher, document)
                    if (!openVoucherPdf(context, uri)) {
                        errorMsg = "Voucher saved, but no PDF viewer is installed."
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
                errorMsg = "Session expired. Please sign in again."
                loggedIn = false
            } catch (e: Exception) {
                errorMsg = "Voucher could not be opened: ${e.message ?: "Unknown error"}"
            } finally {
                downloadingVoucherNumber = null
            }
        }
    }

    fun loadTranscript() = launchTask {
        transcript = withContext(Dispatchers.IO) {
            val page = client.getHtml("/sic/Transcript.php")
            val degree = parseDegreeId(page)
                ?: throw IllegalStateException("Degree ID was not found")
            parseTranscript(client.getTranscriptJson(degree))
        }
    }

    fun refresh(tab: Int) {
        when (tab) {
            0 -> loadExamResult()
            1 -> loadVouchers()
            else -> loadTranscript()
        }
    }
}
