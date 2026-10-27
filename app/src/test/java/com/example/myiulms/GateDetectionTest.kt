package com.example.myiulms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate-detection contract tests for the faculty-evaluation gate.
 *
 * The gate fixture below is a trimmed excerpt of a real gated page captured
 * read-only during an active evaluation window (evidence: REPORT.md, local
 * docs-local/eval-gate — never committed). It is fully sanitized per the
 * project fixture rules: no credentials, cookies, or student data — courseId,
 * sesskey and id values are placeholders ([TOKEN]/[ID]), personal cells are
 * [CELL], and the student link text is [NAME]. Structural markers (title,
 * heading, marker sentences, row onclick pattern, status span, logout link)
 * are kept exactly because the detector relies on them.
 */
class GateDetectionTest {

    private val gateFixture = """
        <!DOCTYPE html>
        <html lang="en">
        <head><title>IU Learning Management System</title></head>
        <body>
        <div class="logininfo">You are logged in as <a href="https://iulms.edu.pk/user/view.php?id=[ID]&amp;course=1">[NAME]</a> (<a href="https://iulms.edu.pk/login/logout.php?sesskey=[TOKEN]">Logout</a>)</div>
        <div style="float:left"><h1>First Impression (FALL-26)</h1>
        <p>Welcome to Online Faculty Evaluation System! The objective of this evaluation is to help faculty members improve their teaching methodology and courses. You have to complete the Online Faculty Evaluation form in order to proceed further with SIC website.</p>
        <table>
        <tr onclick='window.location = "sic/CourseEvaluation.php?courseId=[TOKEN]"' style="cursor: pointer;">
        <td><img src="images/close.png" style="width:16px;height:16px;"/></td>
        <td style="text-align: left; padding-left: 15px;">[CELL]</td>
        <td style="text-align: left; padding-left: 15px;">[CELL]</td>
        <td><span style="color:red;">Not Completed</span></td>
        </tr>
        </table>
        </div>
        </body>
        </html>
    """.trimIndent()

    private val normalScheduleHtml = """
        <html><head><title>IU Learning Management System</title></head>
        <body>
        <div class="label-head-text">Fall 2026 Weekly Schedule</div>
        <table><tr><td class="dateStyle">Monday 09:00 AM</td>
        <td class="detailsStyle">Course Title : Mobile Application Development</td></tr></table>
        <div class="logininfo">Logout</div>
        </body></html>
    """.trimIndent()

    @Test
    fun finalUrlOnGatePathIsDetectedEvenWithPlainBody() {
        // Primary signal: the portal303-redirects everything to the gate.
        assertTrue(
            isEvaluationGate("https://iulms.edu.pk/StudentEvaluation.php", "<html></html>")
        )
        // Redirects to the gate with a query/fragment still counts.
        assertTrue(
            isEvaluationGate("https://iulms.edu.pk/StudentEvaluation.php?redirect=1", "<html></html>")
        )
    }

    @Test
    fun gateBodyMarkerPairDetectedWithoutFinalUrlSignal() {
        // Fallback: a portal build that serves the gate inline without redirecting.
        assertTrue(isEvaluationGate(null, gateFixture))
        assertTrue(isEvaluationGate("https://iulms.edu.pk/sic/Schedule.php", gateFixture))
    }

    @Test
    fun gateBodyWithLogoutLinkStillCountsAsGate() {
        // The gate page carries login/logout.php, so a plain isLoggedIn check
        // would accept it as a normal page. Detection must run first.
        assertTrue(gateFixture.contains("login/logout.php"))
        assertTrue(isEvaluationGate(null, gateFixture))
    }

    @Test
    fun sanitizedFixtureKeepsDetectorMarkers() {
        // Guard against the fixture drifting away from what the detector reads.
        assertTrue(gateFixture.contains("Online Faculty Evaluation"))
        assertTrue(gateFixture.contains("Not Completed"))
        assertTrue(gateFixture.contains("StudentEvaluation.php").not()) // URL signal lives in finalUrl only
        assertTrue(gateFixture.contains("courseId=[TOKEN]"))
        assertTrue(gateFixture.contains("sesskey=[TOKEN]"))
    }

    @Test
    fun normalSchedulePageIsNotGate() {
        assertFalse(isEvaluationGate("https://iulms.edu.pk/sic/Schedule.php", normalScheduleHtml))
        assertFalse(isEvaluationGate(null, normalScheduleHtml))
    }

    @Test
    fun genuinelyEmptyPageIsNotGate() {
        // Genuine empty academic data must never be mistaken for the gate.
        assertFalse(
            isEvaluationGate(
                "https://iulms.edu.pk/sic/examresult.php",
                "<html><body><table></table></body></html>"
            )
        )
        assertFalse(isEvaluationGate(null, ""))
    }

    @Test
    fun singleBodyMarkerIsNotGate() {
        // Body fallback requires BOTH markers together.
        assertFalse(isEvaluationGate(null, "<html>Not Completed</html>"))
        assertFalse(isEvaluationGate(null, "<html>Online Faculty Evaluation System</html>"))
    }

    @Test
    fun loginFormPageIsNotGate() {
        val loginPage = """
            <html><body>
            <form action="/login/index.php" method="post">
            <input name="username" type="text"/>
            <input name="password" type="password"/>
            </form></body></html>
        """.trimIndent()
        assertFalse(isEvaluationGate("https://iulms.edu.pk/login/index.php", loginPage))
        assertFalse(isEvaluationGate(null, loginPage))
    }
}
