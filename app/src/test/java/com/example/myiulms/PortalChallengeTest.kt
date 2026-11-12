package com.example.myiulms

import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Contract tests for the Cloudflare-challenge layer:
 *
 *  1. challenge detection — a 403 challenge must never be reported as invalid
 *     credentials (and the portal's own 403 must not be mistaken for one),
 *  2. User-Agent consistency — OkHttp and the WebView send the exact same
 *     string, because `cf_clearance` is bound to it,
 *  3. cookie sync — CookieManager's header → SessionCookieJar, with the
 *     Moodle session pair kept atomic.
 *
 * Fixtures are synthetic (headers/markers only, no student data).
 */
class PortalChallengeTest {

    @After
    fun restoreFallbackUserAgent() {
        // adopt() tests ke dauran shared() badal dete hain — process ke doosre
        // test files ko wahi na mile.
        PortalUserAgent.adopt(PortalUserAgent.FALLBACK)
    }

    // ---------------- 1. Challenge detection ----------------

    @Test
    fun challengeHeaderOn403IsDetected() {
        val headers = Headers.headersOf(CF_CHALLENGE_HEADER, CF_CHALLENGE_HEADER_VALUE)
        assertTrue(isCloudflareChallenge(403, headers, "<html><body>loading</body></html>"))
    }

    @Test
    fun challengeHeaderIsCaseInsensitive() {
        val headers = Headers.headersOf(CF_CHALLENGE_HEADER, "Challenge")
        assertTrue(isCloudflareChallenge(403, headers, ""))
    }

    @Test
    fun challengeBodyMarkersOn403AreDetected() {
        assertTrue(
            isCloudflareChallenge(
                403,
                null as Headers?,
                """<script src="https://challenges.cloudflare.com/turnstile/v0/api.js">"""
            )
        )
        assertTrue(isCloudflareChallenge(403, null as Headers?, "<title>Just a moment...</title>"))
    }

    @Test
    fun challengeHeaderIgnoredOnNon403() {
        // 200/302 par header ka hona challenge nahi (CF aisa kabhi nahi deta,
        // par contract sirf 403 par).
        val headers = Headers.headersOf(CF_CHALLENGE_HEADER, CF_CHALLENGE_HEADER_VALUE)
        assertFalse(isCloudflareChallenge(200, headers, "<title>Just a moment...</title>"))
        assertFalse(isCloudflareChallenge(302, headers, ""))
    }

    @Test
    fun plainLoginForm403IsNotACloudflareChallenge() {
        // Origin ka apna 403 ("Invalid Browser Headers") — alag failure hai,
        // solve ki zaroorat nahi aur yeh credentials error bhi nahi.
        val body = "<html><head><title>Access Denied</title></head>" +
            "<body>Access Denied: Invalid Browser Headers</body></html>"
        assertFalse(isCloudflareChallenge(403, null as Headers?, body))
        assertFalse(isCloudflareChallenge(403, Headers.headersOf("Server", "cloudflare"), body))
    }

    @Test
    fun ordinaryLoginHtmlIsNotACloudflareChallenge() {
        val loginForm = """<html><title>IU Learning Management System</title>
            <form><input name="username"><input name="password"></form></html>"""
        assertFalse(isCloudflareChallenge(200, null as Headers?, loginForm))
        assertFalse(isCloudflareChallenge(403, null as Headers?, loginForm))
    }

    @Test
    fun silentSolveGivesUpWithinThreeSeconds() {
        // Requirement: sign-in mein 3 second se zyada intezaar nahi — silent
        // attempt usi cap par khatam hota hai, aur interactive detection
        // (Turnstile) usse PEHLE chal kar sheet khol deta hai.
        assertEquals(3_000L, SILENT_SOLVE_TIMEOUT_MS)
        assertTrue(INTERACTIVE_DETECT_MS in 1L..SILENT_SOLVE_TIMEOUT_MS)
    }

    // ---------------- 2. User-Agent consistency ----------------

    @Test
    fun httpAndWebViewShareTheExactSameUserAgent() {
        PortalUserAgent.adopt("UnifiedUA/2.0")
        val request = Request.Builder().url("$BASE/").build()
        val withHeaders = withPortalHeaders(request)
        // wahi string dono taraf — cf_clearance isi par bound hoti hai.
        assertEquals("UnifiedUA/2.0", withHeaders.header("User-Agent"))
        assertEquals(PortalUserAgent.shared(), withHeaders.header("User-Agent"))
    }

    @Test
    fun portalHeadersAlwaysCarryBrowserLikeValues() {
        PortalUserAgent.adopt(PortalUserAgent.FALLBACK)
        val request = Request.Builder().url("$BASE/").build()
        val withHeaders = withPortalHeaders(request)
        assertEquals("en-US,en;q=0.9", withHeaders.header("Accept-Language"))
        assertTrue(withHeaders.header("Accept")!!.contains("text/html"))
    }

    @Test
    fun portalHeadersKeepAnExplicitAcceptHeader() {
        PortalUserAgent.adopt(PortalUserAgent.FALLBACK)
        val request = Request.Builder()
            .url("$BASE/sic/SICDataService.php")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()
        val withHeaders = withPortalHeaders(request)
        assertEquals("application/json, text/javascript, */*; q=0.01", withHeaders.header("Accept"))
    }

    // ---------------- 3. Cookie sync ----------------

    @Test
    fun cookieHeaderParsesNamesAndValues() {
        val cookies = parseCookieHeader("MoodleSession=abc123; MoodleSessionTest=xyz789", "iulms.edu.pk")
        assertEquals(listOf("MoodleSession", "MoodleSessionTest"), cookies.map { it.name })
        assertEquals(listOf("abc123", "xyz789"), cookies.map { it.value })
    }

    @Test
    fun cookieValueMayContainEqualsSign() {
        // cf_clearance base64 jaisi hoti hai — pehla '=' hi separator hai.
        val cookies = parseCookieHeader("cf_clearance=abc=def==; theme=dark", "iulms.edu.pk")
        assertEquals(2, cookies.size)
        assertEquals("abc=def==", cookies[0].value)
        assertEquals("dark", cookies[1].value)
    }

    @Test
    fun junkCookieSegmentsAreSkipped() {
        val cookies = parseCookieHeader("a=1; ; junk; =x; b=2", "iulms.edu.pk")
        assertEquals(listOf("a", "b"), cookies.map { it.name })
    }

    @Test
    fun jarImportReplacesCookieByName() {
        val jar = SessionCookieJar()
        val url = BASE.toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("cf_clearance", "old-value")))
        jar.importHeader("cf_clearance=new-value", "iulms.edu.pk")
        val loaded = jar.loadForRequest(url)
        assertEquals(listOf("new-value"), loaded.map { it.value })
    }

    @Test
    fun completeMoodlePairImportReplacesBothHalves() {
        val jar = SessionCookieJar()
        val url = BASE.toHttpUrl()
        jar.saveFromResponse(
            url,
            listOf(cookie("MoodleSession", "old-session"), cookie("MoodleSessionTest", "old-test"))
        )
        jar.importHeader("MoodleSession=new-session; MoodleSessionTest=new-test", "iulms.edu.pk")
        val byName = jar.loadForRequest(url).associateBy { it.name }
        assertEquals("new-session", byName["MoodleSession"]?.value)
        assertEquals("new-test", byName["MoodleSessionTest"]?.value)
    }

    @Test
    fun loneMoodleHalfIsDroppedWhenJarHasTheOtherHalf() {
        // Aadha pair → Moodle "cookies not enabled" error, jo galat-credentials
        // jaisa dikhta hai. Isliye sirf tab jab doosra half NA ho.
        val jar = SessionCookieJar()
        val url = BASE.toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("MoodleSessionTest", "existing-test")))
        jar.importHeader("cf_clearance=clr; MoodleSession=lone-session", "iulms.edu.pk")
        val names = jar.loadForRequest(url).map { it.name }.toSet()
        assertTrue("cf_clearance" in names)
        assertTrue("MoodleSessionTest" in names)
        assertFalse("MoodleSession" in names)
    }

    @Test
    fun loneMoodleHalfIsKeptWhenJarIsEmpty() {
        val jar = SessionCookieJar()
        jar.importHeader("MoodleSession=only-session", "iulms.edu.pk")
        val loaded = jar.loadForRequest(BASE.toHttpUrl())
        assertEquals(listOf("MoodleSession"), loaded.map { it.name })
    }

    @Test
    fun clearanceImportOnColdStartSkipsStaleMoodleSession() {
        // Cold start sirf cf_clearance laata hai; Moodle pair login() naya banata hai.
        val jar = SessionCookieJar()
        jar.importClearance(
            "cf_clearance=clr; MoodleSession=stale; MoodleSessionTest=stale-test",
            "iulms.edu.pk"
        )
        val loaded = jar.loadForRequest(BASE.toHttpUrl())
        assertEquals(listOf("cf_clearance"), loaded.map { it.name })
        assertEquals("clr", loaded.single().value)
    }

    @Test
    fun coherenceRulePassesNonMoodleCookiesThrough() {
        // cf_clearance jaisi cookies par rule laagu nahi.
        val incoming = listOf(cookie("cf_clearance", "clr"))
        assertEquals(incoming, withCoherentSessionPair(incoming, emptySet()))
        assertEquals(incoming, withCoherentSessionPair(incoming, setOf("MoodleSession")))
    }

    // ---------------- 4. Single-flight (concurrent challenge / re-login) ----------------
    // Do tabs ek saath 403 kha sakte hain ya session expiry par pahunch sakte
    // hain. Gate guarantee karta hai: ek hi WebView solve / ek hi login POST.

    @Test
    fun concurrentCallersShareOneSolve() {
        val gate = SingleFlightGate()
        val observed = gate.completedCount()
        val solverEntered = CountDownLatch(1)
        val releaseSolver = CountDownLatch(1)
        val solverCalls = AtomicInteger(0)
        val secondResult = AtomicBoolean(false)

        val first = thread {
            gate.reuseOrRun(observed) {
                solverCalls.incrementAndGet()
                solverEntered.countDown()
                releaseSolver.await()
                true
            }
        }
        assertTrue(solverEntered.await(2, TimeUnit.SECONDS))
        val second = thread {
            secondResult.set(gate.reuseOrRun(observed) {
                solverCalls.incrementAndGet()
                true
            })
        }
        releaseSolver.countDown()
        first.join(2_000)
        second.join(2_000)

        assertEquals("solver ek hi baar chala", 1, solverCalls.get())
        assertTrue("doosra caller retry kar sakta hai", secondResult.get())
        assertEquals(1L, gate.completedCount())
    }

    @Test
    fun reuseOrRunSkipsWorkWhenAnotherSolveAlreadyCompleted() {
        val gate = SingleFlightGate()
        assertTrue(gate.run { true })
        val blockRan = AtomicBoolean(false)
        assertTrue(gate.reuseOrRun(0L) { blockRan.set(true); true })
        assertFalse(blockRan.get())
    }

    @Test
    fun failedSolveFailsFastForWaitingCallersButNewRequestsTryAgain() {
        val gate = SingleFlightGate()
        val observed = gate.completedCount()
        assertFalse(gate.reuseOrRun(observed) { false })
        // Isi wave ka doosra tab 3s ka naya WebView nahi chalata — fail fast.
        val blockRan = AtomicBoolean(false)
        assertFalse(gate.reuseOrRun(observed) { blockRan.set(true); true })
        assertFalse(blockRan.get())
        // Naya request naya snapshot leta hai, isliye agla attempt allowed hai.
        val fresh = gate.completedCount()
        assertTrue(gate.reuseOrRun(fresh) { true })
    }

    @Test
    fun loginGateSerializesOverlappingLogins() {
        val gate = SingleFlightGate()
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val threads = (1..4).map {
            thread {
                gate.run {
                    val now = active.incrementAndGet()
                    maxActive.updateAndGet { current -> maxOf(current, now) }
                    Thread.sleep(30)
                    active.decrementAndGet()
                    true
                }
            }
        }
        threads.forEach { it.join(2_000) }
        assertEquals("ek waqt par ek hi login", 1, maxActive.get())
        assertEquals(4L, gate.completedCount())
    }

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain("iulms.edu.pk").path("/").build()
}
