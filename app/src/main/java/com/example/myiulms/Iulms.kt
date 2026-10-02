package com.example.myiulms

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val BASE = "https://iulms.edu.pk"

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class NotLoggedInException : Exception("Session khatam ya login fail")

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

// ---------------- Client ----------------

class IulmsClient {
    private val jar = SessionCookieJar()
    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(headerInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var creds: Pair<String, String>? = null

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

    // Session expire ho jaye to khud dobara login karke retry karta hai
    fun getHtml(path: String): String {
        repeat(2) {
            val html = http.newCall(Request.Builder().url(BASE + path).build()).execute()
                .use { it.body?.string().orEmpty() }
            if (isLoggedIn(html)) return html
            relogin()
        }
        throw NotLoggedInException()
    }

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
            val text = http.newCall(req).execute().use { it.body?.string().orEmpty() }
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
            val bytes = response.use { it.body?.bytes() ?: ByteArray(0) }

            if (bytes.isEmpty()) {
                throw IllegalStateException("IULMS returned an empty voucher")
            }

            val looksLikeHtml = contentType.contains("text/html", ignoreCase = true) ||
                bytes.take(256).toByteArray().toString(Charsets.UTF_8)
                    .contains("<html", ignoreCase = true)

            if (looksLikeHtml) {
                val html = bytes.toString(Charsets.UTF_8)
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
