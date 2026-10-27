package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the native faculty-evaluation parsers and payload builder.
 *
 * Fixtures are trimmed excerpts of a real gated/course page captured read-only
 * during an active evaluation window (evidence: docs-local/eval-gate — never
 * committed). They are fully sanitized per the project fixture rules: no
 * credentials, cookies, or student data; courseId / sesskey / ids are
 * placeholders, personal cells are [NAME]. Structural markers the parsers rely
 * on are kept exactly.
 *
 * Rule under test: radio answer values are OPAQUE database row ids (contiguous
 * like 1869..1918) that are parsed from the HTML at runtime — never hardcoded.
 */
class EvaluationParserTest {

    private val gateListHtml = """
        <!DOCTYPE html>
        <html lang="en">
        <head><title>IU Learning Management System</title></head>
        <body>
        <div class="logininfo">You are logged in as <a href="https://iulms.edu.pk/user/view.php?id=123&amp;course=1">Student Name</a> (<a href="https://iulms.edu.pk/login/logout.php?sesskey=abc">Logout</a>)</div>
        <h1>First Impression (FALL-26)</h1>
        <table class="tblAttendance">
        <tr class="tableHeaderStyle"><td>#</td><td>Course</td><td>Instructor</td><td>Status</td></tr>
        <tr onclick='window.location = "sic/CourseEvaluation.php?courseId=1867"' style="cursor: pointer;">
        <td><img src="images/close.png" style="width:16px;height:16px;"/></td>
        <td style="text-align: left; padding-left: 15px;">Data Structures and Algorithms</td>
        <td style="text-align: left; padding-left: 15px;">Dr. [NAME]</td>
        <td><span style="color:red;">Not Completed</span></td>
        </tr>
        <tr onclick='window.location = "sic/CourseEvaluation.php?courseId=1868"' style="cursor: pointer;">
        <td><img src="images/close.png" style="width:16px;height:16px;"/></td>
        <td style="text-align: left; padding-left: 15px;">Operating Systems</td>
        <td style="text-align: left; padding-left: 15px;">Prof. [NAME]</td>
        <td><span style="color:red;">Not Completed</span></td>
        </tr>
        <tr>
        <td><img src="images/close.png" style="width:16px;height:16px;"/></td>
        <td style="text-align: left; padding-left: 15px;">Database Systems</td>
        <td style="text-align: left; padding-left: 15px;">Dr. [NAME]</td>
        <td><span style="color:green;">Completed</span></td>
        </tr>
        </table>
        </body>
        </html>
    """.trimIndent()

    private fun courseFormHtml(): String = buildString {
        append("<!DOCTYPE html><html lang=\"en\"><head><title>Course Evaluation</title></head><body>")
        append("<form action=\"\" method=\"post\">")
        append("<input type=\"hidden\" name=\"totalCount\" value=\"10\"/>")
        append("<input type=\"hidden\" name=\"courseId\" value=\"1867\"/>")
        append("<input type=\"submit\" name=\"submitEval\" value=\"Submit\"/>")
        append("<img src=\"?_CAPTCHA&amp;t=captchaToken123\" alt=\"captcha\"/>")
        append("<table>")
        val scale = listOf("Strongly Agree", "Agree", "Neutral", "Disagree", "Strongly Disagree")
        for (i in 0 until 10) {
            val group = 586 + i
            val base = 1869 + (i * 5)
            val options = scale.mapIndexed { j, label -> "<label><input type=\"radio\" name=\"answer[$group]\" value=\"${base + j}\"/>$label</label>" }
            append("<tr><td>Question ${i + 1}</td><td>")
            append(options.joinToString(" "))
            append("</td></tr>")
        }
        append("</table>")
        append("<table>")
        for (i in 0 until 3) {
            append("<tr><td>Comments</td><td><textarea name=\"comments[${109 + i}]\" maxlength=\"1000\"></textarea></td></tr>")
        }
        append("</table>")
        append("</form></body></html>")
    }

    // ---- parseEvaluationList ----

    @Test
    fun evaluationListParsesCourseIdTitleInstructorAndStatus() {
        val list = parseEvaluationList(gateListHtml)

        assertEquals(3, list.size)
        assertEquals("1867", list[0].courseId)
        assertEquals("Data Structures and Algorithms", list[0].title)
        assertEquals("Dr. [NAME]", list[0].instructor)
        assertEquals(false, list[0].completed)

        assertEquals("1868", list[1].courseId)
        assertEquals("Operating Systems", list[1].title)
        assertEquals("Prof. [NAME]", list[1].instructor)
        assertEquals(false, list[1].completed)
    }

    @Test
    fun evaluationListMarksCompletedWhenStatusIsNotPending() {
        val list = parseEvaluationList(gateListHtml)

        // Completed row: no onclick (blank courseId) + non-pending status.
        assertEquals("", list[2].courseId)
        assertEquals("Database Systems", list[2].title)
        assertEquals(true, list[2].completed)
    }

    @Test
    fun evaluationListSkipsHeaderRow() {
        val list = parseEvaluationList(gateListHtml)

        // tableHeaderStyle row carries no courseId/status and must not leak into results.
        assertEquals("1867", list.first().courseId)
        assertEquals("Data Structures and Algorithms", list.first().title)
    }

    // ---- parseEvaluationForm ----

    @Test
    fun evaluationFormParsesRuntimeRadioIdsAndOptions() {
        val form = parseEvaluationForm(courseFormHtml(), "https://iulms.edu.pk/sic/CourseEvaluation.php?courseId=1867")!!

        assertEquals("1867", form.courseId)
        assertEquals(10, form.questions.size)
        assertEquals("answer[586]", form.questions[0].name)
        assertEquals("answer[595]", form.questions[9].name)
        // Runtime-parsed opaque DB row ids — never hardcoded.
        assertEquals("1869", form.questions[0].options[0].value)
        assertEquals("Strongly Agree", form.questions[0].options[0].label)
        assertEquals("1918", form.questions[9].options[4].value)
        assertEquals("Strongly Disagree", form.questions[9].options[4].label)
        assertEquals(5, form.questions[0].options.size)
    }

    @Test
    fun evaluationFormReadsPromptFromPrecedingRowOnLivePortalLayout() {
        // Live portal (docs-local/eval-gate/form.redacted.html): number + question
        // text in one row, radios in the next row whose first td is empty.
        val radios = listOf("Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree")
            .mapIndexed { j, label ->
                "<span><input name=\"answer[586]\" type=\"radio\" value=\"${1869 + j}\"/>$label</span>"
            }.joinToString("")
        val html = """
            <html><body><form action="" method="post">
            <table class="tblAttendance">
            <tr class="tableHeaderStyle"><td colspan="2">First Impression</td></tr>
            <tr><td>1</td><td>Course outline has been shared and explained.   </td></tr>
            <tr><td></td><td>$radios</td></tr>
            </table>
            <img src="?_CAPTCHA&amp;t=abc"/>
            </form></body></html>
        """.trimIndent()

        val form = parseEvaluationForm(html, "https://iulms.edu.pk/sic/CourseEvaluation.php?courseId=1867")!!

        assertEquals("Course outline has been shared and explained.", form.questions[0].prompt)
        assertEquals("Strongly Disagree", form.questions[0].options[0].label)
    }

    @Test
    fun evaluationFormParsesCommentsWithMaxLength() {
        val form = parseEvaluationForm(courseFormHtml(), "https://iulms.edu.pk/sic/CourseEvaluation.php?courseId=1867")!!

        assertEquals(3, form.comments.size)
        assertEquals("comments[109]", form.comments[0].name)
        assertEquals(1000, form.comments[0].maxLength)
        assertEquals("comments[111]", form.comments[2].name)
        assertEquals(1000, form.comments[2].maxLength)
    }

    @Test
    fun evaluationFormResolvesCaptchaUrlFromRelativePath() {
        val form = parseEvaluationForm(courseFormHtml(), "https://iulms.edu.pk/sic/CourseEvaluation.php?courseId=1867")!!

        // Relative ?_CAPTCHA?t=... resolves against the form page URL.
        assertEquals("https://iulms.edu.pk/sic/CourseEvaluation.php?_CAPTCHA&t=captchaToken123", form.captchaUrl)
        assertEquals("captcha_input", form.captchaInputName)
        assertEquals("submitEval", form.submitName)
        assertEquals("Submit", form.submitValue)
        assertEquals(10, form.totalCount)
    }

    @Test
    fun evaluationFormReturnsNullWhenNoFormPresent() {
        assertNull(parseEvaluationForm("<html><body>No form here</body></html>", "https://iulms.edu.pk/sic/CourseEvaluation.php"))
    }

    // ---- parseEvaluationSubmitError ----

    @Test
    fun evaluationSubmitErrorParsesRedErrorDiv() {
        val error = parseEvaluationSubmitError(
            "<html><body><div style=\"color: red;\">Please complete all questions before submitting.</div></body></html>"
        )

        assertEquals("Please complete all questions before submitting.", error)
    }

    @Test
    fun evaluationSubmitErrorIsNullWithoutRedErrorDiv() {
        assertNull(parseEvaluationSubmitError("<html><body><div>Thanks, your evaluation was submitted.</div></body></html>"))
    }

    // ---- buildEvaluationPayload ----

    @Test
    fun evaluationPayloadUsesRuntimeIdsAndExactFieldOrder() {
        val form = parseEvaluationForm(courseFormHtml(), "https://iulms.edu.pk/sic/CourseEvaluation.php?courseId=1867")!!

        val payload = buildEvaluationPayload(
            form = form,
            answers = mapOf("answer[586]" to "1869", "answer[587]" to "1874"),
            comments = mapOf(
                "comments[109]" to "Clear teaching methods.",
                "comments[110]" to "",
                "comments[111]" to ""
            ),
            captchaAnswer = "  tok123  "
        )

        assertEquals(
            listOf(
                "answer[586]" to "1869",
                "answer[587]" to "1874",
                "comments[109]" to "Clear teaching methods.",
                "comments[110]" to "",
                "comments[111]" to "",
                "captcha_input" to "tok123",
                "totalCount" to "10",
                "courseId" to "1867",
                "submitEval" to "Submit"
            ),
            payload
        )
    }
}
