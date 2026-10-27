package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickFillTest {

    private val scale = listOf("Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree")

    // Opaque DB-row-id style values, like the live portal (answer[586] → 1869..1873).
    private fun question(group: Int, labels: List<String> = scale) = EvaluationQuestion(
        name = "answer[$group]",
        prompt = "Question $group",
        options = labels.mapIndexed { j, label -> EvaluationOption(value = "${group * 10 + j}", label = label) }
    )

    private val questions = (1..4).map { question(it) }

    @Test
    fun emptyFormFillsEveryQuestionWithMatchingOptionValue() {
        val result = quickFillAnswers(questions, emptyMap(), "Agree")

        assertEquals(4, result.filled)
        assertEquals(0, result.kept)
        assertTrue(result.unmatched.isEmpty())
        // "Agree" is index 3 — value comes from each question's own options, never hardcoded.
        assertEquals(mapOf("answer[1]" to "13", "answer[2]" to "23", "answer[3]" to "33", "answer[4]" to "43"), result.answers)
    }

    @Test
    fun partlyAnsweredFormFillsOnlyRemainingAndNeverOverwritesManualAnswers() {
        val current = mapOf("answer[2]" to "20") // student picked Strongly Disagree on Q2

        val result = quickFillAnswers(questions, current, "Strongly Agree")

        assertEquals(3, result.filled)
        assertEquals(1, result.kept)
        assertEquals("20", result.answers["answer[2]"])
        assertEquals("14", result.answers["answer[1]"])
        assertEquals("44", result.answers["answer[4]"])
    }

    @Test
    fun secondQuickFillOnlyTouchesWhatIsStillEmpty() {
        // No toggle exists any more — a later tap can never replace earlier answers.
        val first = quickFillAnswers(questions.take(2), emptyMap(), "Agree").answers
        val second = quickFillAnswers(questions, first, "Neutral")

        assertEquals("13", second.answers["answer[1]"])
        assertEquals("23", second.answers["answer[2]"])
        assertEquals("32", second.answers["answer[3]"])
        assertEquals(2, second.filled)
        assertEquals(2, second.kept)
    }

    @Test
    fun labelMatchIsCaseInsensitive() {
        val result = quickFillAnswers(questions, emptyMap(), "strongly agree")
        assertEquals("14", result.answers["answer[1]"])
    }

    @Test
    fun questionWithoutTheLabelIsReportedNotSilentlySkipped() {
        val mixed = listOf(question(1), question(2, listOf("Yes", "No")), question(3))

        val result = quickFillAnswers(mixed, emptyMap(), "Agree")

        assertEquals(listOf(2), result.unmatched)
        assertEquals(2, result.filled)
        assertNull(result.answers["answer[2]"])
        assertTrue(quickFillMessage("Agree", result).contains("Q2 need a manual answer"))
    }

    @Test
    fun fullyAnsweredFormChangesNothing() {
        val full = questions.associate { it.name to it.options[0].value }

        val result = quickFillAnswers(questions, full, "Agree")

        assertEquals(0, result.filled)
        assertEquals(4, result.kept)
        assertEquals(full, result.answers)
        assertEquals("All questions are already answered.", quickFillMessage("Agree", result))
    }

    @Test
    fun messagesDescribeWhatHappened() {
        val partial = quickFillAnswers(questions, mapOf("answer[1]" to "10"), "Agree")
        assertEquals("Filled 3 remaining with Agree", quickFillMessage("Agree", partial))

        val all = quickFillAnswers(questions, emptyMap(), "Neutral")
        assertEquals("Set all 4 to Neutral", quickFillMessage("Neutral", all))
    }

    // ---- QuickFillUndo ----

    @Test
    fun undoRestoresExactPreviousAnswers() {
        val undo = QuickFillUndo()
        val before = mapOf("answer[2]" to "20")
        val token = undo.record(before)

        assertEquals(before, undo.restore(token))
        // One-shot: a second Undo tap does nothing.
        assertNull(undo.restore(token))
    }

    @Test
    fun undoFromOlderFillCannotRevertNewerFill() {
        val undo = QuickFillUndo()
        val first = undo.record(emptyMap())
        val second = undo.record(mapOf("answer[1]" to "13"))

        assertNull(undo.restore(first))
        assertEquals(mapOf("answer[1]" to "13"), undo.restore(second))
    }

    @Test
    fun manualEditAfterFillInvalidatesUndo() {
        val undo = QuickFillUndo()
        val token = undo.record(emptyMap())

        undo.invalidate() // student tapped an individual pill

        assertNull(undo.restore(token))
    }

    @Test
    fun invalidateReportsWhetherAnUndoWasPending() {
        // true tells the UI to remove the Indefinite quick-fill snackbar.
        val undo = QuickFillUndo()
        assertFalse(undo.invalidate())

        undo.record(emptyMap())
        assertTrue(undo.invalidate())
        assertFalse(undo.invalidate())
    }
}
