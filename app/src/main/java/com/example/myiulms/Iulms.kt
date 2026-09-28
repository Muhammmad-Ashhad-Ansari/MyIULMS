package com.example.myiulms

import android.content.Context
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

data class Voucher(
    val number: String,
    val semester: String,
    val dueDate: String,
    val description: String,
    val amount: String
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
}

// ---------------- Parsers ----------------

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

fun parseVouchers(html: String): List<Voucher> {
    val doc = Jsoup.parse(html)
    val table = doc.getElementById("voucherTable") ?: return emptyList()
    val list = mutableListOf<Voucher>()
    for (tr in table.select("tr")) {
        val td = tr.select("td")
        // Data rows ka pehla cell 1. 2. 3. jaisa hota hai, header ka khali
        if (td.size >= 7 && td[0].text().endsWith(".")) {
            list.add(
                Voucher(
                    number = td[1].text(),
                    semester = td[2].text(),
                    dueDate = td[3].text(),
                    description = td[5].text(),
                    amount = td[6].text()
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
            prefs.edit().putString("u", user).putString("p", pass).apply()
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
            prefs.edit().clear().apply()
        } catch (e: Exception) {
        }
    }
}
