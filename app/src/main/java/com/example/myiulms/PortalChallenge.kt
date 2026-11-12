package com.example.myiulms

import android.content.Context
import android.os.Looper
import android.webkit.WebSettings
import okhttp3.Cookie
import okhttp3.Headers

/*
 * iulms.edu.pk ke login page ab Cloudflare managed challenge ke peeche hain:
 * 403 + `Cf-Mitigated: challenge` aur "Just a moment..." wala page. Ek browser
 * JS chala kar cf_clearance cookie leta hai; OkHttp yeh nahi kar sakta, isliye
 * ek asli WebView solve karti hai aur uski cookies OkHttp ke jar mein jaati hain.
 *
 * Yeh file PURE logic hai (koi WebView nahi) — unit tests isi ko chalate hain:
 *   - challenge pehchanna (galat-credentials mein kabhi na badalna),
 *   - CookieManager ka header → OkHttp cookies,
 *   - session ka EK hi User-Agent (cf_clearance us par bound hoti hai).
 * WebView wala hissa PortalChallengeSolver.kt mein hai.
 */

/** Challenge response ka header; 403 par iski value `challenge` hoti hai. */
const val CF_CHALLENGE_HEADER = "Cf-Mitigated"
const val CF_CHALLENGE_HEADER_VALUE = "challenge"

/** Challenge page ke body markers (page title + script host). */
const val CF_CHALLENGE_MARKER_TITLE = "Just a moment..."
const val CF_CHALLENGE_MARKER_HOST = "challenges.cloudflare.com"

/** Clearance cookie: WebView ko milti hai, OkHttp ko bhejni hai. */
const val CF_CLEARANCE_COOKIE = "cf_clearance"

/**
 * Silent off-screen solve ki hard limit. Uske baad seedha interactive
 * PortalVerificationSheet — requirement: sign-in mein 3 second se zyada
 * intezaar nahi.
 */
const val SILENT_SOLVE_TIMEOUT_MS = 3_000L

/**
 * Challenge page ke load hone ke itni der baad bhi "Just a moment..." pada ho
 * to maana jata hai ke user ki zaroorat hai (Turnstile checkbox) — silent
 * attempt turant chhod kar sheet kholo, 3-second cap ka intezaar kiye bina.
 */
const val INTERACTIVE_DETECT_MS = 1_500L

/** Portal ka login endpoint — app isi par POST karta hai, WebView isi par solve karti hai. */
val IULMS_LOGIN_URL: String get() = "$BASE/login/index.php"

/**
 * Off-screen solve ka hook (AppContainer `PortalChallengeSolver.attach` se
 * lagata hai). `timeoutMs` par portal-challenge wali ek request call karti hai
 * aur WebView ke cookies ka header lautati hai (`null` = solve nahi hui).
 * Background thread par chalti hai — main thread block kabhi nahi.
 */
fun interface ChallengeSolver {
    fun solve(timeoutMs: Long): String?
}

/**
 * Portal ne Cloudflare challenge diya aur WebView solve na kar payi.
 *
 * Yeh GALAT CREDENTIALS nahi hai: UI ko yeh alag se pehchanna chahiye taake
 * student ko "check your registration number and password" na dikha jaye.
 */
class CloudflareChallengeException :
    Exception("iulms.edu.pk requires browser verification.")

/**
 * Ek waqt par ek hi solver/login chalaye rakhta hai.
 *
 * Do tabs ek saath 403 kha sakte hain (Attendance + Results), ya session expiry
 * par dono `relogin()` par pahunch sakte hain. Bina is gate ke har caller apni
 * WebView solve aur apni login POST chalata, jo parallel dialogs aur ek doosre
 * ka fresh Moodle session clear karne wali "login storm" ban jati hai.
 *
 * Contract: har call apni request se pehle [completedCount] snapshot leti hai.
 *  - [reuseOrRun]: snapshot ke baad kisi doosre caller ne attempt kiya to block
 *    DOBARA nahi chalta — success par `true` (naye cookies ke saath retry),
 *    failure par `false` (fail fast; 3s ka doosra WebView nahi).
 *  - [run]: block ko lock ke andar chalata hai (reentrant).
 *
 * Attempt (success ya failure) hamesha record hota hai, taake ek hi challenge
 * wave mein sirf ek solver/login chale. Naya request naya snapshot leta hai,
 * isliye agla attempt phir allowed hai.
 *
 * Ek hi file mein rakhne ki wajah: yeh pure logic hai, WebView ke bagair
 * unit-testable.
 */
internal class SingleFlightGate {
    private val lock = Any()

    private var completed = 0L
    private var lastFailed = false

    fun completedCount(): Long = synchronized(lock) { completed }

    /** [block] ko serialize karta hai aur attempt record karta hai. */
    fun run(block: () -> Boolean): Boolean = synchronized(lock) {
        val ok = block()
        completed++
        lastFailed = !ok
        ok
    }

    /** Snapshot ke baad hui attempt ka outcome reuse karo, warna khud chalao. */
    fun reuseOrRun(observed: Long, block: () -> Boolean): Boolean = synchronized(lock) {
        if (completed != observed) return !lastFailed
        val ok = block()
        completed++
        lastFailed = !ok
        ok
    }
}

/**
 * Cloudflare ka managed-challenge interstitial pehchano.
 *
 * Contract: SIRF 403 par, aur ya to `Cf-Mitigated: challenge` header ya body
 * ke marker. Origin ka apna 403 ("Access Denied: Invalid Browser Headers")
 * yahan NAHI aata — woh alag (header) failure hai aur challenge solve ki
 * zaroorat nahi.
 */
fun isCloudflareChallenge(statusCode: Int, cfMitigated: String?, body: String): Boolean {
    if (statusCode != 403) return false
    if (cfMitigated?.trim()?.equals(CF_CHALLENGE_HEADER_VALUE, ignoreCase = true) == true) return true
    return body.contains(CF_CHALLENGE_MARKER_HOST, ignoreCase = true) ||
        body.contains(CF_CHALLENGE_MARKER_TITLE, ignoreCase = true)
}

/** OkHttp response headers wala overload. */
fun isCloudflareChallenge(statusCode: Int, headers: Headers?, body: String): Boolean =
    isCloudflareChallenge(statusCode, headers?.get(CF_CHALLENGE_HEADER), body)

/**
 * `CookieManager.getCookie(url)` ka "a=1; b=2" header → OkHttp cookies.
 *
 * Values '=' rakh sakti hain (cf_clearance base64 jaisa), isliye pehla '=' hi
 * separator hai. Blank/invalid segments chhod diye jate hain.
 */
fun parseCookieHeader(header: String, host: String): List<Cookie> =
    header.split(';').mapNotNull { part ->
        val token = part.trim()
        if ('=' !in token) return@mapNotNull null
        val name = token.substringBefore('=').trim()
        val value = token.substringAfter('=').trim()
        if (name.isEmpty()) return@mapNotNull null
        runCatching {
            Cookie.Builder().name(name).value(value).domain(host).path("/").build()
        }.getOrNull()
    }

private const val MOODLE_SESSION = "MoodleSession"
private const val MOODLE_SESSION_TEST = "MoodleSessionTest"

/**
 * Moodle ka session-pair ATOMIC hai: `MoodleSession` aur `MoodleSessionTest`
 * ek hi session se aate hain (testcookies iska hi check hai).
 *
 * Agar WebView header sirf ek half laaye aur jar mein doosra half pehle se ho,
 * to half pair KABHI mat lagao — mismatch par Moodle "cookies not enabled"
 * error deta hai, jo galat-credentials jaisa dikhta hai. Dono ho to dono ek
 * saath replace (coherent pair), aur jar mein kuch na ho to aane do (behtar
 * kuch hai hi nahi).
 */
fun withCoherentSessionPair(incoming: List<Cookie>, existingNames: Set<String>): List<Cookie> {
    val incomingNames = incoming.mapTo(HashSet()) { it.name }
    val hasSession = MOODLE_SESSION in incomingNames
    val hasTest = MOODLE_SESSION_TEST in incomingNames
    if (hasSession == hasTest) return incoming
    val lone = if (hasSession) MOODLE_SESSION else MOODLE_SESSION_TEST
    val other = if (hasSession) MOODLE_SESSION_TEST else MOODLE_SESSION
    return if (other in existingNames) incoming.filterNot { it.name == lone } else incoming
}

/**
 * Poori session ka EK User-Agent.
 *
 * `cf_clearance` User-Agent par bound hoti hai: WebView jo string bhejti hai
 * aur OkHttp jo bhejta hai EXACT same honi chahiye, warna clearance reject ho
 * jati hai aur portal dobara challenge deta hai. Isliye dono taraf yahi
 * `shared()` value use hoti hai — headerInterceptor (OkHttp) aur
 * `WebSettings.userAgentString` (WebView).
 */
object PortalUserAgent {

    /**
     * WebView ka default resolve hone tak ka fallback (desktop Chrome).
     * Yeh bhi dono taraf lagta hai, isliye consistency kharab nahi hoti.
     */
    const val FALLBACK =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @Volatile
    private var current = FALLBACK

    @Volatile
    private var resolved = false

    /** OkHttp aur WebView dono ko yahi string milti hai. */
    fun shared(): String = current

    /**
     * WebView ka asli default user agent. Sirf main thread par (WebView ka
     * provider wahan hi initialized hota hai); off-main par chup-chaap retry
     * hota hai — pehle sign-in attempt (main par) se pehle resolve ho jata hai.
     * Fail ho jaye to fallback rehta hai (consistency phir bhi barqarar).
     */
    fun ensureResolved(context: Context) {
        if (resolved) return
        val default = runCatching {
            if (Looper.myLooper() != Looper.getMainLooper()) return
            WebSettings.getDefaultUserAgent(context)
        }.getOrNull()
        resolved = true
        if (!default.isNullOrBlank()) adopt(default)
    }

    /** Dono transports par ek hi diya hua UA lagao (ensureResolved + tests). */
    fun adopt(userAgent: String) {
        if (userAgent.isNotBlank()) current = userAgent
    }
}
