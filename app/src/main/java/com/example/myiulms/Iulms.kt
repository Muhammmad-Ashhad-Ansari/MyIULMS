package com.example.myiulms

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val BASE = "https://iulms.edu.pk"

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class NotLoggedInException : Exception("Your session has expired. Please sign in again.")

// Faculty evaluation gate active hai: portal har authenticated page ko
// StudentEvaluation.php par redirect karta hai. Session expire NAHI hua —
// UI "Evaluations required" dikhata hai, sign-in screen nahi.
class EvaluationRequiredException : Exception("Faculty evaluations required")

// ---------------- Models ----------------

data class ExamRow(
    val course: String,
    val midterm: String,
    val quizzes: String,
    val project: String,
    val finalExam: String,
    val total: String,
    val grade: String,
    val points: String
)

data class ExamResult(val title: String, val rows: List<ExamRow>, val gpa: String?)

data class ExamScheduleEntry(
    val dayAndDate: String,
    val time: String,
    val courseTitle: String,
    val faculty: String,
    val location: String,
    val edpCode: String
)

data class ExamSchedule(
    val title: String,
    val notice: String?,
    val entries: List<ExamScheduleEntry>
)

data class WeeklyScheduleEntry(
    val day: String,
    val time: String,
    val courseTitle: String,
    val faculty: String,
    val location: String,
    val edpCode: String,
    val courseCode: String
)

data class WeeklySchedule(
    val title: String,
    val entries: List<WeeklyScheduleEntry>
)

data class AttendanceSession(
    val lectureNumber: String,
    val values: List<String>
)

data class AttendanceCourse(
    val name: String,
    val totalSessions: Int,
    val present: Int,
    val absent: Int,
    val schedule: String,
    val faculty: String,
    val sessionHeaders: List<String>,
    val sessions: List<AttendanceSession>
) {
    val attendancePercent: Int
        get() = if (totalSessions > 0) ((present * 100f) / totalSessions).toInt() else 0
}

data class AttendanceSummary(val courses: List<AttendanceCourse>)

data class Voucher(
    val number: String,
    val semester: String,
    val dueDate: String,
    val description: String,
    val amount: String,
    val printVoucherNumber: String = "",
    val studentId: String = ""
)

data class Course(
    val code: String,
    val title: String,
    val hours: String,
    val grade: String,
    val gpa: String
)

data class Transcript(val cgpa: String, val courses: List<Course>)

// ---------------- Evaluation models ----------------
// Portal contract (docs-local/eval-gate/FORM_CONTRACT.md, v2 capture):
// gate page = tblAttendance rows with onclick courseId links; form page =
// answer[NNN] radio groups whose values are OPAQUE DB ROW IDS (e.g.
// 1869..1918) — parsed at runtime, kabhi hardcode nahi.

data class EvaluationCourse(
    // Blank = completed row jis mein form ka onclick link hi nahi (non-tappable).
    val courseId: String,
    val title: String,
    val instructor: String,
    val completed: Boolean
)

data class EvaluationOption(val value: String, val label: String)

data class EvaluationQuestion(
    val name: String, // portal radio group name, e.g. "answer[586]"
    val prompt: String,
    val options: List<EvaluationOption>
)

data class EvaluationCommentField(
    val name: String, // portal textarea name, e.g. "comments[109]"
    val prompt: String,
    val maxLength: Int
)

data class EvaluationForm(
    val courseId: String,
    val submitUrl: String, // form action="" → POST back to the page URL
    val questions: List<EvaluationQuestion>,
    val comments: List<EvaluationCommentField>,
    val captchaUrl: String, // absolute, resolved against submitUrl
    val captchaInputName: String,
    val submitName: String,
    val submitValue: String,
    val totalCount: Int
)

// ---------------- Cookies + headers ----------------

class SessionCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        synchronized(list) {
            val now = System.currentTimeMillis()
            list.removeAll { it.expiresAt < now }
            return list.toList()
        }
    }

    fun clear() {
        store.clear()
    }
}

// Server browser jaisi headers na mile to Access Denied deta hai, isliye har request pe lagti hain
private val headerInterceptor = Interceptor { chain ->
    val original = chain.request()
    val builder = original.newBuilder()
        .header("User-Agent", UA)
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Connection", "keep-alive")
    if (original.header("Accept") == null) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }
    chain.proceed(builder.build())
}

// ---------------- Evaluation gate detection ----------------
// Live capture evidence (evaluation window active): portal 303-redirects every
// authenticated page — home, all /sic/ data pages, and even the login POST —
// to /StudentEvaluation.php, whose body carries both marker strings below.
// Contract: a final URL on the gate path alone is enough; the body markers are
// the fallback for a portal that might serve the gate inline without a redirect,
// and they must BOTH appear so an ordinary page can never masquerade as the gate.
private const val GATE_FINAL_PATH = "/StudentEvaluation.php"
private const val GATE_MARKER_TEXT = "Online Faculty Evaluation"
private const val GATE_MARKER_STATUS = "Not Completed"

fun isEvaluationGate(finalUrl: String?, html: String): Boolean {
    if (finalUrl != null && finalUrl.contains(GATE_FINAL_PATH)) return true
    return html.contains(GATE_MARKER_TEXT) && html.contains(GATE_MARKER_STATUS)
}

// Final URL (redirects ke baad) kabhi discard nahi — gate detection ka primary signal.
private data class PortalPage(val finalUrl: String, val html: String)

// ---------------- Client ----------------

// Process-wide logged-in client. MainViewModel aur EvaluationActivity dono isi
// ko use karte hain — alag instance ka cookie jar/creds khaali hota hai, to
// evaluation requests logged-out land karti theen.
object IulmsSession {
    val client = IulmsClient()
}

class IulmsClient {
    private val jar = SessionCookieJar()
    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(headerInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var creds: Pair<String, String>? = null

    // Process death ke baad shared client khaali hota hai (koi login nahi hua) —
    // EvaluationActivity is se pehchanta hai ke remembered creds se login karna hai.
    val hasCredentials: Boolean get() = creds != null

    // Logged in page mein hamesha Logout link hota hai
    private fun isLoggedIn(html: String) = html.contains("login/logout.php")

    fun login(username: String, password: String): Boolean {
        jar.clear()
        // Pehle homepage se session cookie lo
        http.newCall(Request.Builder().url("$BASE/").build()).execute().close()

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("testcookies", "1")
            .build()
        val req = Request.Builder().url("$BASE/login/index.php").post(form).build()
        val html = http.newCall(req).execute().use { it.body?.string().orEmpty() }

        val ok = isLoggedIn(html)
        creds = if (ok) username to password else null
        return ok
    }

    fun logout() {
        jar.clear()
        creds = null
    }

    private fun relogin() {
        val c = creds ?: throw NotLoggedInException()
        if (!login(c.first, c.second)) throw NotLoggedInException()
    }

    // Session expire ho jaye to khud dobara login karke retry karta hai.
    // Gate page mein logout link bhi hota hai, isliye evaluation-gate check
    // isLoggedIn check SE PEHLE chalta hai — warna gate khaali data samajh kar
    // chala jata (silent empty screens).
    private fun getPortalPage(path: String): PortalPage {
        repeat(2) {
            val response = http.newCall(Request.Builder().url(BASE + path).build()).execute()
            val page = response.use { r ->
                PortalPage(finalUrl = r.request.url.toString(), html = r.body?.string().orEmpty())
            }
            if (isEvaluationGate(page.finalUrl, page.html)) throw EvaluationRequiredException()
            if (isLoggedIn(page.html)) return page
            relogin()
        }
        throw NotLoggedInException()
    }

    fun getHtml(path: String): String = getPortalPage(path).html

    fun getTranscriptJson(degreeId: String): String {
        repeat(2) {
            val form = FormBody.Builder()
                .add("action", "GetTranscript")
                .add("degreeId", degreeId)
                .build()
            val req = Request.Builder()
                .url("$BASE/sic/SICDataService.php")
                .post(form)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val text = http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                // Gate active ho to JSON kabhi nahi aayega — beech ka relogin
                // loop skip karke seedha gate state par jao.
                if (isEvaluationGate(r.request.url.toString(), body)) {
                    throw EvaluationRequiredException()
                }
                body
            }
            val clean = text.trimStart('\uFEFF').trim()
            if (clean.startsWith("{")) return clean
            relogin()
        }
        throw NotLoggedInException()
    }

    fun getVoucherDocument(voucher: Voucher): VoucherDocument {
        if (voucher.printVoucherNumber.isBlank() || voucher.studentId.isBlank()) {
            throw IllegalStateException("Voucher print information is unavailable")
        }

        repeat(2) {
            val form = FormBody.Builder()
                .add("VoucherNumber", voucher.printVoucherNumber)
                .add("studentId", voucher.studentId)
                .add("voucherBtn", "Print Voucher")
                .build()

            val req = Request.Builder()
                .url("$BASE/sic/PrintVoucher.php")
                .post(form)
                .header("Accept", "application/pdf,text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Referer", "$BASE/sic/Vouchers.php")
                .build()

            val response = http.newCall(req).execute()
            val contentType = response.body?.contentType()?.toString().orEmpty()
            val finalUrl = response.request.url.toString()
            val bytes = response.use { it.body?.bytes() ?: ByteArray(0) }

            if (bytes.isEmpty()) {
                throw IllegalStateException("IULMS returned an empty voucher")
            }

            val looksLikeHtml = contentType.contains("text/html", ignoreCase = true) ||
                bytes.take(256).toByteArray().toString(Charsets.UTF_8)
                    .contains("<html", ignoreCase = true)

            if (looksLikeHtml) {
                val html = bytes.toString(Charsets.UTF_8)
                if (isEvaluationGate(finalUrl, html)) throw EvaluationRequiredException()
                val looksLoggedOut =
                    html.contains("name=\"username\"", ignoreCase = true) &&
                        html.contains("name=\"password\"", ignoreCase = true)

                if (looksLoggedOut) {
                    relogin()
                    return@repeat
                }
            }

            return VoucherDocument(bytes = bytes, contentType = contentType)
        }

        throw NotLoggedInException()
    }

    // ---------------- Native evaluation flow (OkHttp only — no WebView) ----------------
    // Yeh methods gate page KHUD fetch karte hain (getPortalPage gate par throw
    // karta hai) — gate yahan expected state hai. Session expiry par wahi
    // relogin-retry discipline. Auth sab jagah client ke SessionCookieJar ke
    // cookies se chalta hai (FORM_CONTRACT.md: form mein sesskey nahi).

    // Gate page ka raw HTML (StudentEvaluation.php). isLoggedIn gate page par
    // bhi true hota hai (logout link body mein) — relogin sirf session-expiry par.
    // url = absolute URL (form page) ya BASE-relative path (gate page).
    private fun getEvaluationPage(url: String): String {
        val absolute = if (url.startsWith("http", ignoreCase = true)) url else BASE + url
        repeat(2) {
            val response = http.newCall(Request.Builder().url(absolute).build()).execute()
            val page = response.use { r ->
                PortalPage(finalUrl = r.request.url.toString(), html = r.body?.string().orEmpty())
            }
            if (isLoggedIn(page.html)) return page.html
            relogin()
        }
        throw NotLoggedInException()
    }

    // Course form GET. courseId portal ka opaque token hai — HttpUrl
    // addQueryParameter use karta hai taake '+' aur base64 chars safe encode hon.
    fun getEvaluationFormHtml(courseId: String): String =
        getEvaluationPage(evaluationFormUrl(courseId))

    fun getEvaluationGateHtml(): String = getEvaluationPage("/StudentEvaluation.php")

    // CAPTCHA image bytes. URL parser page se absolute resolve karke deta hai
    // (relative "?_CAPTCHA&t=..." → CourseEvaluation.php?_CAPTCHA&t=...;
    // courseId yahan drop hota hai — session cookies auth carry karte hain).
    // CAPTCHA session-bound + ONE-SHOT hai (simple-php-captcha): code form page
    // GET par session mein banta hai; yeh endpoint use ek baar draw karke session
    // se hata deta hai. Wahi URL dobara (naye 't' ke saath bhi) khaali body deta
    // hai — device par verified. Naya code chahiye to naya form GET karo.
    fun getEvaluationCaptcha(url: String): ByteArray {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "image/*,*/*;q=0.8")
            .build()
        val response = http.newCall(req).execute()
        val bytes = response.use { it.body?.bytes() ?: ByteArray(0) }
        if (bytes.isEmpty()) throw IllegalStateException("IULMS returned an empty CAPTCHA image")
        return bytes
    }

    // Form POST (FORM_CONTRACT.md §5): application/x-www-form-urlencoded,
    // action="" → current page URL, session cookies authenticate. OkHttp
    // FormBody '[', ']' ko %5B/%5D encode karta hai — browser bilkul aisa hi
    // bhejta hai, PHP decode kar leta hai. Response HTML return; caller
    // kabhi POST par trust nahi karta — gate list re-fetch hi success ka
    // proof hai (error div parseEvaluationSubmitError se).
    fun submitEvaluation(
        formUrl: String,
        fields: List<Pair<String, String>>
    ): String {
        repeat(2) {
            val body = FormBody.Builder().apply {
                for ((name, value) in fields) add(name, value)
            }.build()
            val req = Request.Builder()
                .url(formUrl)
                .post(body)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Referer", formUrl)
                .build()
            val html = http.newCall(req).execute().use { it.body?.string().orEmpty() }
            // Session expire ho to POST login page par land karega.
            val looksLoggedOut =
                html.contains("name=\"username\"", ignoreCase = true) &&
                    html.contains("name=\"password\"", ignoreCase = true)
            if (looksLoggedOut) {
                relogin()
                return@repeat
            }
            return html
        }
        throw NotLoggedInException()
    }
}

// ---------------- Parsers ----------------
fun parseStudentName(html: String): String? {
    val doc = org.jsoup.Jsoup.parse(html)
    val loginInfo = doc.selectFirst(".logininfo") ?: return null

    return loginInfo
        .select("a")
        .firstOrNull {
            !it.attr("href").contains("logout.php", ignoreCase = true)
        }
        ?.text()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
fun parseExamResult(html: String): ExamResult {
    val doc = Jsoup.parse(html)
    val title = doc.select("h1").firstOrNull { it.text().startsWith("EXAM RESULT") }?.text()
        ?: "Exam Result"
    val rows = mutableListOf<ExamRow>()
    var gpa: String? = null

    val table = doc.selectFirst("table.tblAttendance")
    if (table != null) {
        for (tr in table.select("tr")) {
            if (tr.hasClass("tableHeaderStyle")) continue
            val td = tr.select("td")
            if (td.size == 8) {
                rows.add(
                    ExamRow(
                        course = td[0].text(),
                        midterm = td[1].text(),
                        quizzes = td[2].text(),
                        project = td[3].text(),
                        finalExam = td[4].text(),
                        total = td[5].text(),
                        grade = td[6].text(),
                        points = td[7].text()
                    )
                )
            } else if (td.size == 1 && td[0].text().startsWith("GPA")) {
                gpa = td[0].text().substringAfter(":").trim()
            }
        }
    }
    return ExamResult(title, rows, gpa)
}

fun parseExamSchedule(html: String): ExamSchedule {
    val doc = Jsoup.parse(html)
    val title = doc.select(".label-head-text").text().replace("\\s+".toRegex(), " ").trim()
        .ifBlank { "Exam Schedule" }

    val notice = doc.select("p").firstOrNull { p ->
        p.parents().none { it.hasClass("label-head") } &&
            (p.text().contains("subject to change", ignoreCase = true) ||
                p.text().contains("advised to review", ignoreCase = true))
    }?.text()?.replace("\\s+".toRegex(), " ")?.trim()

    val entries = mutableListOf<ExamScheduleEntry>()
    val dateCells = doc.select("td.dateStyle")
    val detailCells = doc.select("td.detailsStyle")

    val count = minOf(dateCells.size, detailCells.size)
    for (i in 0 until count) {
        val dateTd = dateCells[i]
        val detailTd = detailCells[i]

        val daySpan = dateTd.selectFirst(".dayStyle")
        val dayAndDate = daySpan?.let {
            val htmlWithSeparator = it.html().replace("(?i)<br\\s*/?>".toRegex(), ", ")
            Jsoup.parse(htmlWithSeparator).text()
        }?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()

        val time = dateTd.select("tr").getOrNull(1)?.text()?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()

        var courseTitle = ""
        var faculty = ""
        var location = ""
        var edpCode = ""

        for (tr in detailTd.select("tr")) {
            val fullText = tr.text().replace("\\s+".toRegex(), " ").trim()
            when {
                fullText.contains("Course Title", ignoreCase = true) ->
                    courseTitle = fullText.substringAfter(":").trim()
                fullText.contains("Faculty", ignoreCase = true) ->
                    faculty = fullText.substringAfter(":").trim()
                fullText.contains("Location", ignoreCase = true) ->
                    location = fullText.substringAfter(":").trim()
                fullText.contains("EDP Code", ignoreCase = true) ->
                    edpCode = fullText.substringAfter(":").trim()
            }
        }

        if (courseTitle.isNotBlank() || dayAndDate.isNotBlank()) {
            entries.add(
                ExamScheduleEntry(
                    dayAndDate = dayAndDate,
                    time = time,
                    courseTitle = courseTitle,
                    faculty = faculty,
                    location = location,
                    edpCode = edpCode
                )
            )
        }
    }

    return ExamSchedule(title = title, notice = notice, entries = entries)
}

fun parseWeeklySchedule(html: String): WeeklySchedule {
    val doc = Jsoup.parse(html)
    val title = doc.select(".label-head-text").text()
        .replace("\\s+".toRegex(), " ")
        .trim()
        .ifBlank { "Weekly class schedule" }
    val dateCells = doc.select("td.dateStyle")
    val detailCells = doc.select("td.detailsStyle")
    val entries = mutableListOf<WeeklyScheduleEntry>()

    for (index in 0 until minOf(dateCells.size, detailCells.size)) {
        val dateCell = dateCells[index]
        val detailCell = detailCells[index]
        val day = dateCell.selectFirst(".dayStyle")?.text()
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            .orEmpty()
        val time = dateCell.select("tr").getOrNull(1)?.text()
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            .orEmpty()

        var courseTitle = ""
        var faculty = ""
        var location = ""
        var edpCode = ""
        var courseCode = ""
        for (row in detailCell.select("tr")) {
            val text = row.text().replace("\\s+".toRegex(), " ").trim()
            when {
                text.contains("Course Title", ignoreCase = true) -> courseTitle = text.substringAfter(":").trim()
                text.contains("Faculty", ignoreCase = true) -> faculty = text.substringAfter(":").trim()
                text.contains("Location", ignoreCase = true) -> location = text.substringAfter(":").trim()
                text.contains("EDP Code", ignoreCase = true) -> {
                    edpCode = Regex("EDP Code\\s*:\\s*([^\\s]+)", RegexOption.IGNORE_CASE)
                        .find(text)?.groupValues?.get(1).orEmpty()
                    courseCode = Regex("Course Code\\s*:\\s*([^\\s]+)", RegexOption.IGNORE_CASE)
                        .find(text)?.groupValues?.get(1).orEmpty()
                }
                text.contains("Course Code", ignoreCase = true) -> courseCode = text.substringAfter(":").trim()
            }
        }

        if (day.isNotBlank() || courseTitle.isNotBlank()) {
            entries += WeeklyScheduleEntry(
                day = day,
                time = time,
                courseTitle = courseTitle,
                faculty = faculty,
                location = location,
                edpCode = edpCode,
                courseCode = courseCode
            )
        }
    }

    return WeeklySchedule(title, entries)
}

fun parseAttendance(html: String): AttendanceSummary {
    val doc = Jsoup.parse(html)
    val courseRows = doc.selectFirst("table.attendance-table")?.select("tr.attendanceRow").orEmpty()
    val courses = courseRows.mapNotNull { row ->
        val name = row.selectFirst(".attendanceRowCourse")?.text()?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null

        val statCells = row.select("td.attendanceRowStat")
        var total = statCells.getOrNull(0)?.text()?.trim()?.toIntOrNull() ?: 0
        var present = statCells.getOrNull(1)?.text()?.trim()?.toIntOrNull() ?: 0
        var absent = statCells.getOrNull(2)?.text()?.trim()?.toIntOrNull() ?: 0

        val key = row.selectFirst("a[onclick]")?.attr("onclick")
            ?.let { Regex("viewattendance\\((\\d+)\\)").find(it)?.groupValues?.get(1) }
        val modal = key?.let { doc.getElementById("myModal_$it") }
        val sessionTable = modal?.selectFirst("table.attendance-table")
        val sessionHeaders = sessionTable?.selectFirst("tr")?.select("th")
            ?.map { it.text().trim() }
            .orEmpty()
        val sessions = sessionTable?.select("tr")
            ?.mapNotNull { sessionRow ->
                val cells = sessionRow.select("td")
                if (cells.size < 2 || sessionRow.hasClass("attendance-table-summary")) {
                    null
                } else {
                    AttendanceSession(
                        lectureNumber = cells.first()?.text()?.trim().orEmpty(),
                        values = cells.drop(1).map { it.text().trim() }
                    )
                }
            }
            .orEmpty()

        modal?.select("tr.attendance-table-summary")?.forEach { summaryRow ->
            val cells = summaryRow.select("td")
            for (index in 0 until cells.lastIndex) {
                val label = cells[index].text().lowercase()
                val value = cells[index + 1].text().trim().toIntOrNull() ?: continue
                when {
                    "total sessions" in label -> total = value
                    "total present" in label -> present = value
                    "total absent" in label -> absent = value
                }
            }
        }

        AttendanceCourse(
            name = name,
            totalSessions = total,
            present = present,
            absent = absent,
            schedule = modal?.getElementById("schedule")?.text()?.trim().orEmpty(),
            faculty = modal?.getElementById("facultyName")?.text()?.trim().orEmpty(),
            sessionHeaders = sessionHeaders.drop(1),
            sessions = sessions
        )
    }
    return AttendanceSummary(courses)
}

fun parseVouchers(html: String): List<Voucher> {
    val doc = Jsoup.parse(html)
    val table = doc.getElementById("voucherTable") ?: return emptyList()
    val list = mutableListOf<Voucher>()

    for (tr in table.select("tr")) {
        val td = tr.select("td")

        // Data rows ka pehla cell 1. 2. 3. jaisa hota hai, header ka khali
        if (td.size >= 7 && td[0].text().endsWith(".")) {
            val printForm = tr.selectFirst("form[action*=PrintVoucher]")

            list.add(
                Voucher(
                    number = td[1].text(),
                    semester = td[2].text(),
                    dueDate = td[3].text(),
                    description = td[5].text(),
                    amount = td[6].text(),
                    printVoucherNumber = printForm
                        ?.selectFirst("input[name=VoucherNumber]")
                        ?.attr("value")
                        .orEmpty(),
                    studentId = printForm
                        ?.selectFirst("input[name=studentId]")
                        ?.attr("value")
                        .orEmpty()
                )
            )
        }
    }

    return list
}

fun parseDegreeId(html: String): String? =
    Jsoup.parse(html).selectFirst("#cmbDegree option")?.attr("value")

// ---------------- Evaluation parsers + payload (pure, unit-tested) ----------------

// Form URL banata hai (query param properly encoded). Parser pageUrl isi se
// milata hai — captcha ka relative src isi par resolve hota hai.
internal fun evaluationFormUrl(courseId: String): String =
    "$BASE/sic/CourseEvaluation.php".toHttpUrl().newBuilder()
        .addQueryParameter("courseId", courseId)
        .build()
        .toString()

// Gate page (StudentEvaluation.php) → course rows. Row contract (v2 capture):
// <tr onclick='window.location = "sic/CourseEvaluation.php?courseId=..."'>
// ke andar 4 td: icon | course title | instructor | status span. courseId
// onclick attribute ME hai (koi <a> tag nahi). Completed row ka status text
// "Not Completed" nahi hota; onclick bhi missing ho sakta hai (non-tappable).
fun parseEvaluationList(html: String): List<EvaluationCourse> {
    val doc = Jsoup.parse(html)
    val list = mutableListOf<EvaluationCourse>()
    for (tr in doc.select("table.tblAttendance tr")) {
        if (tr.hasClass("tableHeaderStyle")) continue
        val td = tr.select("td")
        if (td.size < 4) continue
        val courseId = Regex("CourseEvaluation\\.php\\?courseId=([^\"']+)")
            .find(tr.attr("onclick"))?.groupValues?.get(1).orEmpty()
        val status = td[3].text()
        // Na courseId na status → yeh row data row hi nahi (spacer/empty).
        if (courseId.isBlank() && status.isBlank()) continue
        list.add(
            EvaluationCourse(
                courseId = courseId,
                title = td[1].text().trim(),
                instructor = td[2].text().trim(),
                completed = !status.contains("Not Completed", ignoreCase = true)
            )
        )
    }
    return list
}

// Course form page (CourseEvaluation.php?courseId=...) → native form model.
// RETURN NULL tab jab page form hai hi nahi (gate/redirect/login interstitial)
// — caller ko tab list screen par wapas bhejna chahiye.
//
// CRITICAL (FORM_CONTRACT.md §3): radio values portal ke OPAQUE DB ROW IDS
// hain (1869..1918 jaise) — HTML se hi padhi jati hain, kabhi hardcode nahi.
// Label = parent span ka text (input element text contribute nahi karta).
fun parseEvaluationForm(html: String, pageUrl: String): EvaluationForm? {
    val doc = Jsoup.parse(html)
    val form = doc.select("form")
        .firstOrNull { it.attr("method").equals("post", ignoreCase = true) }
        ?: return null

    // Radio groups: document order preserve (groupBy LinkedHashMap).
    val questions = form.select("input[type=radio]")
        .filter { it.attr("name").isNotBlank() && it.attr("value").isNotBlank() }
        .groupBy { it.attr("name") }
        .map { (name, inputs) ->
            EvaluationQuestion(
                name = name,
                prompt = evaluationQuestionPrompt(inputs.first()),
                options = inputs.map { input ->
                    EvaluationOption(
                        value = input.attr("value"),
                        label = input.parent()?.text()?.trim()
                            ?.ifBlank { input.attr("value") }
                            ?: input.attr("value")
                    )
                }
            )
        }
        .filter { it.options.size >= 2 }

    if (questions.isEmpty()) return null

    val comments = form.select("textarea").mapNotNull { ta ->
        val name = ta.attr("name")
        if (name.isBlank()) return@mapNotNull null
        val cells = ta.closest("tr")?.select("td").orEmpty()
        EvaluationCommentField(
            name = name,
            prompt = cells.firstOrNull()?.text()?.trim().orEmpty(),
            maxLength = ta.attr("maxlength").toIntOrNull() ?: 1000
        )
    }

    // Captcha: <img src="?_CAPTCHA&t=..."> — relative '?'-URL page par
    // resolve hota hai (path rehta hai, query replace). Page mein captcha img
    // hi nahi to yeh form page nahi.
    val captchaSrc = form.select("img")
        .firstOrNull { it.attr("src").contains("_CAPTCHA", ignoreCase = true) }
        ?.attr("src")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val captchaUrl = pageUrl.toHttpUrl().resolve(captchaSrc)?.toString() ?: return null

    val submit = form.select("input[type=submit]").firstOrNull()
    return EvaluationForm(
        courseId = form.selectFirst("input[name=courseId]")?.attr("value").orEmpty(),
        submitUrl = pageUrl, // action="" → POST back to current URL
        questions = questions,
        comments = comments,
        captchaUrl = captchaUrl,
        captchaInputName = form.selectFirst("input[name=captcha_input]")?.attr("name")
            ?.takeIf { it.isNotBlank() } ?: "captcha_input",
        submitName = submit?.attr("name")?.takeIf { it.isNotBlank() } ?: "submitEval",
        submitValue = submit?.attr("value")?.takeIf { it.isNotBlank() } ?: "Submit",
        totalCount = form.selectFirst("input[name=totalCount]")?.attr("value")?.toIntOrNull()
            ?: questions.size
    )
}

// Live portal (form.redacted.html): question text radio row ke PEHLE wali row
// mein hai — <td>1</td><td>Question text</td> — aur radio row ka pehla td
// khali hota hai. Pehle radio row ka apna first td try karo (purana/simple
// layout), khali ho to previous row ka last td.
private fun evaluationQuestionPrompt(input: Element): String {
    val row = input.closest("tr") ?: return ""
    val own = row.select("td").firstOrNull()?.text()?.trim().orEmpty()
    if (own.isNotEmpty()) return own
    return row.previousElementSibling()
        ?.select("td")
        ?.lastOrNull()
        ?.text()
        ?.trim()
        .orEmpty()
}

// POST response mein portal ka error div (<div style="color: red…">). GET par
// yeh div khali hota hai; failed submit par text aata hai. Null = koi error
// text nahi (abhi bhi POST par trust mat karo — gate list re-fetch hi proof hai).
fun parseEvaluationSubmitError(html: String): String? {
    val div = Jsoup.parse(html).select("div").firstOrNull { el ->
        val style = el.attr("style").lowercase()
        "color" in style && "red" in style
    } ?: return null
    return div.text().trim().takeIf { it.isNotEmpty() }
}

// Exact x-www-form-urlencoded field order (FORM_CONTRACT.md §5): sab
// answer[...] (question order), sab comments[...] (field order, empty allowed
// → server-side requirement unknown hai; portal ka HTML5 'required' client-side
// hi hai — device verification par confirm hoga), captcha_input, totalCount,
// courseId, submit. Values runtime-parsed form se — yehi guarantee hai ke
// kabhi purane/galat row IDs submit na hon.
internal fun buildEvaluationPayload(
    form: EvaluationForm,
    answers: Map<String, String>,
    comments: Map<String, String>,
    captchaAnswer: String
): List<Pair<String, String>> {
    val fields = mutableListOf<Pair<String, String>>()
    for (question in form.questions) {
        answers[question.name]?.let { fields += question.name to it }
    }
    for (comment in form.comments) {
        fields += comment.name to (comments[comment.name] ?: "")
    }
    fields += form.captchaInputName to captchaAnswer.trim()
    fields += "totalCount" to form.totalCount.toString()
    fields += "courseId" to form.courseId
    fields += form.submitName to form.submitValue
    return fields
}

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

fun parseTranscript(json: String): Transcript {
    val root = JSONObject(json)
    val arr = root.getJSONArray("attemptedCourses")
    val courses = (0 until arr.length()).map { i ->
        val c = arr.getJSONObject(i)
        Course(
            code = c.str("crsCode"),
            title = c.str("crsTitle"),
            hours = c.str("crsHours"),
            grade = c.str("crsGrade"),
            gpa = c.str("gpa")
        )
    }
    return Transcript(root.str("cgpa"), courses)
}

// ---------------- Encrypted credential store ----------------

class SecureStore(private val context: Context) {
    private val prefs by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "iulms_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(user: String, pass: String) {
        try {
            prefs.edit {
                putString("u", user)
                putString("p", pass)
            }
        } catch (e: Exception) {
            // save na ho paye to app phir bhi chalti rahe
        }
    }

    fun load(): Pair<String, String>? {
        return try {
            val u = prefs.getString("u", null)
            val p = prefs.getString("p", null)
            if (u != null && p != null) u to p else null
        } catch (e: Exception) {
            null
        }
    }


    fun clear() {
        try {
            prefs.edit { clear() }
        } catch (e: Exception) {
        }
    }
}
