package com.example.myiulms

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Cloudflare challenge WebView se clear karne wala solver + cookie bridge.
 *
 * Do raste:
 *  1. **Silent** — off-screen WebView [IULMS_LOGIN_URL], max
 *     [SILENT_SOLVE_TIMEOUT_MS] (3 s); agar challenge page load hone ke
 *     [INTERACTIVE_DETECT_MS] baad bhi pada rahe (Turnstile checkbox) to
 *     turant chhod kar interactive sheet. Zyada tar non-interactive challenge
 *     is window mein khud clear ho jata hai.
 *  2. **Interactive** — agar student ki zaroorat ho (Turnstile checkbox) to
 *     LoginScreen ek Material sheet mein wahi WebView dikhata hai; naya
 *     cf_clearance aate hi sheet khud band hoti hai.
 *
 * **Cookies:** WebView ka `CookieManager` (`cf_clearance`, `MoodleSession`,
 * `MoodleSessionTest`) → `SessionCookieJar`. Sign-out par dono taraf saaf.
 *
 * **User-Agent:** WebView [PortalUserAgent.shared()] par set hoti hai — wahi
 * string OkHttp bhejta hai, warna cf_clearance (us par bound) reject ho jati hai.
 *
 * Success ka contract ([isSolveComplete]): **naya** cf_clearance (snapshot se
 * badal gaya) YA clearance maujood aur page challenge nahi — stale clearance
 * jiske paas nahi, sirf wahi fail hoti, auto-loop nahi banta.
 *
 * Yeh object Android par depend karta hai; unit tests sirf PortalChallenge.kt
 * ki pure logic test karti hain.
 */
object PortalChallengeSolver {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    /** AppContainer ek baar lagata hai: solver hook + UA resolution. */
    fun attach(client: IulmsClient, context: Context) {
        appContext = context.applicationContext
        client.challengeSolver = ChallengeSolver { timeoutMs -> solveSilently(timeoutMs) }
        PortalUserAgent.ensureResolved(context)
        // Cold start: WebView ne pehle jo cf_clearance earn ki thi woh disk par
        // persisted hai — jar mein le lo, taake pehla GET / dobara challenge par
        // na jaye. Fail ho to login() ka silent solver phir bhi sambhal leta hai.
        runCatching { client.importPersistedClearance() }
    }

    /**
     * Off-screen, silent solve. Background (IO) thread par call hoti hai —
     * WebView main par chalti hai, isliye main thread yahan kabhi block nahi
     * hota. Main thread par lauti to null → interactive sheet ka raasta.
     */
    private fun solveSilently(timeoutMs: Long): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val context = appContext ?: return null
        val result = AtomicReference<String?>()
        val done = CountDownLatch(1)
        mainHandler.post {
            runSilentSolve(context, timeoutMs) { cookies ->
                result.set(cookies)
                done.countDown()
            }
        }
        // runSilentSolve khud timeout par finish hoti hai; margin sirf is liye
        // ke login thread hamesha wapas aaye — yahan kabhi infinite wait nahi.
        if (!done.await(timeoutMs + 2_000, TimeUnit.MILLISECONDS)) return null
        return result.get()
    }

    /** Main thread par: WebView load + poll. [onDone] hamesha exactly ek baar. */
    private fun runSilentSolve(context: Context, timeoutMs: Long, onDone: (String?) -> Unit) {
        // Har page finish par reset hota hai — interactive window usi page se
        // shuru hoti hai jo abhi load hua (main thread par likha/padha jata hai).
        var pageFinishedAt: Long? = null
        val webView = try {
            createWebView(context) { pageFinishedAt = SystemClock.uptimeMillis() }
        } catch (_: Exception) {
            onDone(null)
            return
        }

        var finished = false
        val startedAt = SystemClock.uptimeMillis()
        val seenBefore = clearanceValue()
        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                when {
                    // Naya cf_clearance = challenge pass; ya clearance pehle se
                    // valid (app restart) aur page challenge nahi — dono me.
                    isSolveComplete(webView, seenBefore) -> {
                        finished = true
                        webView.stopLoading()
                        webView.destroy()
                        persistCookies()
                        onDone(cookieHeader())
                    }
                    // Interactive (Turnstile checkbox): page load ke itni der
                    // baad bhi "Just a moment..." — student ka tap chahiye,
                    // isliye 3s cap ka intezaar kiye bina sheet par jao.
                    isInteractiveChallenge(webView, pageFinishedAt) -> {
                        finished = true
                        webView.stopLoading()
                        webView.destroy()
                        onDone(null)
                    }
                    SystemClock.uptimeMillis() - startedAt >= timeoutMs -> {
                        finished = true
                        webView.stopLoading()
                        webView.destroy()
                        onDone(null)
                    }
                    else -> mainHandler.postDelayed(this, 250)
                }
            }
        }

        try {
            webView.loadUrl(IULMS_LOGIN_URL)
        } catch (_: Exception) {
            webView.destroy()
            onDone(null)
            return
        }
        mainHandler.postDelayed(poll, 250)
    }

    /**
     * WebView (CookieManager) ke portal cookies ka header — "a=1; b=2".
     * Yehi header `SessionCookieJar` mein import hota hai.
     */
    fun cookieHeader(): String? = runCatching {
        CookieManager.getInstance().getCookie(BASE)
    }.getOrNull()

    /**
     * Abhi ka cf_clearance value (null = nahi). Silent solve aur interactive
     * sheet dono isi ko apna snapshot maan kar "naya clearance aaya" dekhte hain.
     */
    fun clearanceValue(): String? {
        val header = cookieHeader() ?: return null
        return header.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$CF_CLEARANCE_COOKIE=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Solve complete? Ya to **naya** cf_clearance (snapshot se badal gaya), ya
     * clearance maujood aur page ab challenge nahi (title "Just a moment..."
     * nahi). Doosra case tab aata hai jab clearance pehle se valid ho (app
     * restart, interactive resume) aur CF usi ko accept kar le — value na
     * badle, isliye sirf snapshot-diff hota to wahan false timeout hota.
     *
     * Main thread par call hoti hai (WebView.title).
     */
    fun isSolveComplete(webView: WebView, seenClearance: String?): Boolean {
        val now = clearanceValue() ?: return false
        if (now != seenClearance) return true
        val title = webView.title?.trim().orEmpty()
        if (title.isEmpty()) return false
        return !title.contains("just a moment", ignoreCase = true)
    }

    /**
     * Interactive challenge? Page load hone ke [INTERACTIVE_DETECT_MS] baad bhi
     * title "Just a moment..." (Turnstile checkbox) — auto-solve aksar isi
     * window mein complete ho jati hai, jo na ho usko 3s cap par chhodna hi
     * padta; dono hi turant interactive sheet khol dete hain, bina poore
     * timeout ka intezaar kiye. Main thread par call hoti hai.
     */
    private fun isInteractiveChallenge(webView: WebView, pageFinishedAt: Long?): Boolean {
        val finishedAt = pageFinishedAt ?: return false
        if (SystemClock.uptimeMillis() - finishedAt < INTERACTIVE_DETECT_MS) return false
        return webView.title?.contains("just a moment", ignoreCase = true) == true
    }

    /**
     * Solved challenge ke cookies disk par likho — WebView khud bhi persist
     * karta hai, par explicit flush se cold start hamesha wahi clearance paata
     * hai (silent solve dobara karne ki zaroorat nahi).
     */
    fun persistCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }

    /**
     * Sign-out par: WebView ke cookies bhi saaf — cf_clearance aur Moodle
     * session dono sirf isi sign-in se aaye the (session hygiene).
     */
    fun clearCookies() {
        val manager = CookieManager.getInstance()
        runCatching { manager.removeAllCookies { manager.flush() } }
    }

    /**
     * Configure ki hua WebView: JavaScript on aur wahi User-Agent jo OkHttp
     * bhejta hai. Top-level navigation sirf iulms.edu.pk par — challenge
     * student ko portal ke bahar le na jaye (credentials kahin aur nahi jate).
     * [onPageFinished] har page par ek baar (silent solve interactive window
     * yahin se naap-ti hai); sheet ko iski zaroorat nahi.
     */
    fun createWebView(context: Context, onPageFinished: ((WebView) -> Unit)? = null): WebView {
        PortalUserAgent.ensureResolved(context)
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = PortalUserAgent.shared()
        }
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host ?: return true
                return !isPortalHost(host)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view != null) onPageFinished?.invoke(view)
            }
        }
        return webView
    }

    private val portalHost: String by lazy { BASE.toHttpUrl().host }

    private fun isPortalHost(host: String): Boolean =
        host == portalHost || host.endsWith(".$portalHost")
}
