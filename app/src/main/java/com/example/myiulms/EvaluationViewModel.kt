package com.example.myiulms

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// One-shot UI events. Activity inhe main thread par collect karta hai — finish()
// aur setResult() kabhi background thread se nahi chalte.
sealed interface EvaluationEvent {
    data class Message(val text: String) : EvaluationEvent
    data object Finish : EvaluationEvent

    // Snackbar "Undo" ke liye. token se purane snackbar ka Undo naye fill ko
    // revert nahi kar sakta.
    data class QuickFilled(val message: String, val undoToken: Int?) : EvaluationEvent

    // Undo ab valid nahi (manual edit / course change) — Indefinite snackbar hatao.
    data object QuickFillUndoExpired : EvaluationEvent
}

// Evaluation flow ki saari state. viewModelScope Main par chalta hai: har state
// write main thread par hoti hai, network/decode withContext(IO) mein.
// Selected course + answers + comments SavedStateHandle mein — process death ke
// baad student ke bhare hue jawab wapas aate hain. CAPTCHA input persist nahi
// hota (single-use, session-bound).
class EvaluationViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

    private val client = IulmsSession.client
    private val secureStore = SecureStore(app)
    private val sessionMutex = Mutex()

    var courses by mutableStateOf<List<EvaluationCourse>?>(null)
        private set
    var listLoading by mutableStateOf(true)
        private set
    var listError by mutableStateOf<String?>(null)
        private set

    var selectedCourse by mutableStateOf(restoreSelectedCourse())
        private set
    var form by mutableStateOf<EvaluationForm?>(null)
        private set
    var formLoading by mutableStateOf(false)
        private set
    var formError by mutableStateOf<String?>(null)
        private set
    var submitError by mutableStateOf<String?>(null)
        private set
    var answers by mutableStateOf(savedState.get<HashMap<String, String>>(KEY_ANSWERS)?.toMap().orEmpty())
        private set
    var comments by mutableStateOf(savedState.get<HashMap<String, String>>(KEY_COMMENTS)?.toMap().orEmpty())
        private set
    var captchaBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var captchaLoading by mutableStateOf(false)
        private set
    var captchaError by mutableStateOf(false)
        private set
    var captchaInput by mutableStateOf("")
        private set
    var submitting by mutableStateOf(false)
        private set

    private val quickFillUndo = QuickFillUndo()

    private val events = Channel<EvaluationEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EvaluationEvent> = events.receiveAsFlow()

    private var listJob: Job? = null
    private var formJob: Job? = null
    private var captchaJob: Job? = null

    val hasUnsavedInput: Boolean
        get() = hasUnsavedEvaluationInput(answers, comments)

    init {
        loadGateList()
        // Process death ke baad: wahi course dobara kholo, bhare jawab rakho.
        selectedCourse?.let { openForm(it, keepInput = true) }
    }

    fun loadGateList() {
        listJob?.cancel()
        listLoading = true
        listError = null
        listJob = viewModelScope.launch {
            val parsed = fetchGateList()
            if (parsed == null) {
                listError = "Couldn't load evaluations. Check your connection and try again."
            } else {
                courses = parsed
            }
            listLoading = false
        }
    }

    fun openCourse(course: EvaluationCourse) {
        // Completed rows ka onclick link nahi hota — tap par kuch load nahi karna.
        if (course.completed || course.courseId.isBlank()) return
        openForm(course, keepInput = false)
    }

    fun retryForm() {
        selectedCourse?.let { openForm(it, keepInput = true) }
    }

    fun backToList() {
        if (submitting) return
        formJob?.cancel()
        clearFormState()
    }

    fun setAnswer(name: String, value: String) {
        // Fill ke baad manual edit — ab Undo us edit ko mita deta, isliye invalidate.
        expireQuickFillUndo()
        replaceAnswers(answers + (name to value))
    }

    // Ek hi rule, koi toggle nahi: sirf unanswered questions bharo. Kuch answered
    // na ho to iska matlab "sab" hai; student ke manual answers kabhi overwrite nahi
    // hote. Ek bulk update (ek state + ek SavedStateHandle write), Undo ke liye snapshot.
    fun quickFill(label: String) {
        val current = form ?: return
        val result = quickFillAnswers(current.questions, answers, label)
        val token = if (result.filled > 0) quickFillUndo.record(answers) else null
        if (result.filled > 0) replaceAnswers(result.answers)
        events.trySend(EvaluationEvent.QuickFilled(quickFillMessage(label, result), token))
    }

    fun undoQuickFill(token: Int) {
        quickFillUndo.restore(token)?.let(::replaceAnswers)
    }

    private fun expireQuickFillUndo() {
        if (quickFillUndo.invalidate()) events.trySend(EvaluationEvent.QuickFillUndoExpired)
    }

    private fun replaceAnswers(updated: Map<String, String>) {
        answers = updated
        savedState[KEY_ANSWERS] = HashMap(updated)
    }

    fun setComment(name: String, value: String) {
        comments = comments + (name to value)
        savedState[KEY_COMMENTS] = HashMap(comments)
    }

    fun updateCaptchaInput(value: String) {
        captchaInput = value
    }

    // Portal simple-php-captcha use karta hai: code FORM PAGE render par session
    // mein banta hai, aur ?_CAPTCHA endpoint use ek hi baar draw karke session se
    // hata deta hai — wahi URL dobara (naye t= ke saath bhi) khaali body deta hai.
    // Isliye naya code = naya form GET (naya captchaUrl). Answers/comments VM mein
    // rehte hain; question names stable hain.
    fun refreshCaptcha() {
        val course = selectedCourse ?: return
        if (form == null || submitting || captchaLoading) return
        captchaJob?.cancel()
        captchaInput = ""
        captchaJob = viewModelScope.launch { reloadFormCaptcha(course) }
    }

    fun submit() {
        val current = form ?: return
        val course = selectedCourse ?: return
        if (submitting) return
        submitting = true
        submitError = null
        val payload = buildEvaluationPayload(current, answers, comments, captchaInput)
        viewModelScope.launch {
            val responseHtml = withContext(Dispatchers.IO) {
                try {
                    client.submitEvaluation(current.submitUrl, payload)
                } catch (e: Exception) {
                    Log.w(TAG, "Evaluation submit failed", e)
                    null
                }
            }
            if (responseHtml == null) {
                failSubmit("Submission failed. Check your connection and try again.")
                return@launch
            }
            parseEvaluationSubmitError(responseHtml)?.let { portalError ->
                failSubmit(portalError)
                return@launch
            }

            // POST response par trust nahi — gate list re-fetch hi success ka proof hai.
            val refreshed = fetchGateList()
            submitting = false
            when {
                refreshed == null -> {
                    clearFormState()
                    listError = "Your evaluation was sent, but the list couldn't refresh. Tap Retry."
                }
                !isSubmissionConfirmed(refreshed, course) -> {
                    submitError = "The portal didn't accept this submission. Check the code and try again."
                    reloadFormKeepingInput(course)
                }
                else -> {
                    clearFormState()
                    courses = refreshed
                    listError = null
                    if (refreshed.none { !it.completed }) {
                        events.send(EvaluationEvent.Finish)
                    } else {
                        events.send(EvaluationEvent.Message("Submitted ✓ ${course.title}"))
                    }
                }
            }
        }
    }

    private suspend fun failSubmit(message: String) {
        submitting = false
        submitError = message
        // CAPTCHA single-use hai — har attempt ke baad fresh form + naya code.
        selectedCourse?.let { reloadFormKeepingInput(it) }
    }

    private fun openForm(course: EvaluationCourse, keepInput: Boolean) {
        formJob?.cancel()
        captchaJob?.cancel()
        captchaLoading = false
        captchaError = false
        expireQuickFillUndo()
        selectCourse(course)
        form = null
        formError = null
        formLoading = true
        submitError = null
        captchaBitmap = null
        captchaInput = ""
        if (!keepInput) {
            answers = emptyMap()
            comments = emptyMap()
            savedState.remove<HashMap<String, String>>(KEY_ANSWERS)
            savedState.remove<HashMap<String, String>>(KEY_COMMENTS)
        }
        formJob = viewModelScope.launch {
            val parsed = fetchForm(course.courseId)
            formLoading = false
            if (parsed == null) {
                // Fresh form nahi mila (gate/redirect/login interstitial) — error dikhao,
                // silently list par wapas mat bhejo.
                formError = "Couldn't open this evaluation. Please try again."
                return@launch
            }
            form = parsed
            // Image endpoint one-shot hai — pehli fetch fail ho to ek baar naya form
            // GET (naya code) try karo, warna error state + refresh button.
            if (!loadCaptcha(parsed.captchaUrl)) reloadFormCaptcha(course)
        }
    }

    // Failed submit ke baad: wahi screen, wahi jawab, naya form token + CAPTCHA.
    private suspend fun reloadFormKeepingInput(course: EvaluationCourse) {
        captchaInput = ""
        reloadFormCaptcha(course)
    }

    // Naya form GET → session mein naya CAPTCHA code + naya captchaUrl → image.
    private suspend fun reloadFormCaptcha(course: EvaluationCourse) {
        captchaLoading = true
        captchaError = false
        captchaBitmap = null
        val parsed = fetchForm(course.courseId)
        if (selectedCourse != course) return
        if (parsed == null) {
            captchaLoading = false
            captchaError = true
            return
        }
        form = parsed
        loadCaptcha(parsed.captchaUrl)
    }

    // Process death ke baad yeh activity MainActivity ke bagair restore hoti hai —
    // shared client khaali hota hai. Remembered creds (SecureStore) se ek hi baar
    // login; list + form parallel fetch karte hain, isliye Mutex.
    private suspend fun ensureSession() = sessionMutex.withLock {
        if (client.hasCredentials) return@withLock
        val saved = secureStore.load() ?: throw NotLoggedInException()
        if (!client.login(saved.first, saved.second)) throw NotLoggedInException()
    }

    private suspend fun fetchGateList(): List<EvaluationCourse>? = withContext(Dispatchers.IO) {
        try {
            ensureSession()
            parseEvaluationList(client.getEvaluationGateHtml())
        } catch (e: Exception) {
            Log.w(TAG, "Evaluation gate list fetch failed", e)
            null
        }
    }

    private suspend fun fetchForm(courseId: String): EvaluationForm? = withContext(Dispatchers.IO) {
        try {
            ensureSession()
            parseEvaluationForm(client.getEvaluationFormHtml(courseId), evaluationFormUrl(courseId))
        } catch (e: Exception) {
            Log.w(TAG, "Evaluation form fetch failed", e)
            null
        }
    }

    // true = image dikh rahi hai. Network IO par, state writes main par.
    private suspend fun loadCaptcha(url: String): Boolean {
        captchaLoading = true
        captchaError = false
        captchaBitmap = null
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val bytes = client.getEvaluationCaptcha(url)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also {
                    // decode null = image nahi aayi (e.g. HTML) — exception nahi hoti,
                    // isliye alag se log. Body log nahi karte (student data ho sakta hai).
                    if (it == null) Log.w(TAG, "Evaluation captcha was not an image (${bytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Evaluation captcha fetch failed", e)
                null
            }
        }
        captchaBitmap = bitmap
        captchaError = bitmap == null
        captchaLoading = false
        return bitmap != null
    }

    private fun clearFormState() {
        captchaJob?.cancel()
        expireQuickFillUndo()
        captchaLoading = false
        captchaError = false
        selectCourse(null)
        form = null
        formError = null
        formLoading = false
        submitError = null
        answers = emptyMap()
        comments = emptyMap()
        captchaBitmap = null
        captchaInput = ""
        savedState.remove<HashMap<String, String>>(KEY_ANSWERS)
        savedState.remove<HashMap<String, String>>(KEY_COMMENTS)
    }

    private fun selectCourse(course: EvaluationCourse?) {
        selectedCourse = course
        savedState[KEY_COURSE_ID] = course?.courseId
        savedState[KEY_COURSE_TITLE] = course?.title
        savedState[KEY_COURSE_INSTRUCTOR] = course?.instructor
    }

    private fun restoreSelectedCourse(): EvaluationCourse? {
        val id = savedState.get<String>(KEY_COURSE_ID)?.takeIf { it.isNotBlank() } ?: return null
        return EvaluationCourse(
            courseId = id,
            title = savedState.get<String>(KEY_COURSE_TITLE).orEmpty(),
            instructor = savedState.get<String>(KEY_COURSE_INSTRUCTOR).orEmpty(),
            completed = false
        )
    }

    private companion object {
        const val TAG = "EvaluationViewModel"
        const val KEY_COURSE_ID = "eval_course_id"
        const val KEY_COURSE_TITLE = "eval_course_title"
        const val KEY_COURSE_INSTRUCTOR = "eval_course_instructor"
        const val KEY_ANSWERS = "eval_answers"
        const val KEY_COMMENTS = "eval_comments"
    }
}

// Discard prompt ka trigger: koi rating chuni ho ya koi comment likha ho.
internal fun hasUnsavedEvaluationInput(answers: Map<String, String>, comments: Map<String, String>): Boolean =
    answers.isNotEmpty() || comments.values.any { it.isNotBlank() }

// Refreshed gate list mein course ab bhi pending hai to submit confirm nahi hua.
// courseId ke saath title+instructor bhi match karte hain — agar portal ka
// opaque token har page load par badalta ho to bhi pending row pakdi jaye.
internal fun isSubmissionConfirmed(refreshed: List<EvaluationCourse>, course: EvaluationCourse): Boolean =
    refreshed.none { row ->
        !row.completed && (
            row.courseId == course.courseId ||
                (row.title == course.title && row.instructor == course.instructor)
            )
    }

// ---------------- Quick fill (pure, unit-tested) ----------------

internal data class QuickFillResult(
    val answers: Map<String, String>,
    val filled: Int, // jitne answers is fill ne set/badle
    val kept: Int, // student ke apne answers jo rakhe gaye (kabhi overwrite nahi)
    val unmatched: List<Int> // 1-based question numbers jin mein yeh label hi nahi
)

// Sirf unanswered questions bharta hai — empty form par yeh "sab" ban jata hai.
// Label match (case-insensitive) har question ke apne options se — value opaque
// DB id hai, isliye label se dhoondte hain. Jis question mein label na mile woh
// chhoda jata hai aur `unmatched` mein report hota hai (silent partial fill nahi).
internal fun quickFillAnswers(
    questions: List<EvaluationQuestion>,
    current: Map<String, String>,
    label: String
): QuickFillResult {
    val updated = current.toMutableMap()
    var filled = 0
    var kept = 0
    val unmatched = mutableListOf<Int>()
    questions.forEachIndexed { index, question ->
        val existing = current[question.name]?.takeIf { it.isNotBlank() }
        if (existing != null) {
            kept++
            return@forEachIndexed
        }
        val option = question.options.firstOrNull { it.label.equals(label, ignoreCase = true) }
        if (option == null) {
            unmatched += index + 1
            return@forEachIndexed
        }
        updated[question.name] = option.value
        filled++
    }
    return QuickFillResult(updated, filled, kept, unmatched)
}

internal fun quickFillMessage(label: String, result: QuickFillResult): String {
    val main = when {
        result.filled == 0 && result.kept > 0 -> "All questions are already answered."
        result.filled == 0 -> "Nothing changed."
        result.kept > 0 -> "Filled ${result.filled} remaining with $label"
        else -> "Set all ${result.filled} to $label"
    }
    if (result.unmatched.isEmpty()) return main
    val numbers = result.unmatched.joinToString(", ") { "Q$it" }
    return "$main · $numbers need a manual answer"
}

// Ek-level undo. Token har fill par badalta hai; manual edit / course change par
// invalidate — purane snackbar ka Undo kabhi naya data overwrite nahi karta.
internal class QuickFillUndo {
    private var snapshot: Map<String, String>? = null
    private var token = 0

    fun record(before: Map<String, String>): Int {
        snapshot = before
        return ++token
    }

    fun restore(requestedToken: Int): Map<String, String>? {
        if (requestedToken != token) return null
        return snapshot.also { snapshot = null }
    }

    // true = koi pending undo tha (UI ko snackbar hatana chahiye).
    fun invalidate(): Boolean {
        val hadPending = snapshot != null
        snapshot = null
        token++
        return hadPending
    }
}
