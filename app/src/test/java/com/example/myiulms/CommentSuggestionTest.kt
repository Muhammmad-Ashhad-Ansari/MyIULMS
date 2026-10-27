package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentSuggestionTest {

    // Live portal prompts (docs-local/eval-gate/FORM_CONTRACT.md §E).
    private val bestFeatures = commentSuggestions("The best features of the course are:")
    private val challenges = commentSuggestions("What challenges are you facing in this course?")
    private val additional = commentSuggestions("Any additional comments regarding the course or instructor:")

    @Test
    fun eachLivePromptGetsItsOwnPhraseSet() {
        assertTrue("Clear explanations." in bestFeatures)
        assertTrue("The pace is a bit fast." in challenges)
        assertTrue("Overall a good learning experience." in additional)
        assertNotEquals(bestFeatures, challenges)
        assertNotEquals(challenges, additional)
    }

    @Test
    fun challengeSetIsNotOnlyPraise() {
        // Constructive phrases must exist — praise-only chips would skew feedback.
        assertTrue(challenges.any { "help" in it || "hard" in it || "fast" in it || "heavy" in it })
    }

    @Test
    fun improvePromptMapsToConstructiveSet() {
        assertEquals(challenges, commentSuggestions("What could the instructor improve?"))
    }

    @Test
    fun appendIntoEmptyFieldUsesPhraseAsIs() {
        assertEquals("Clear explanations.", appendCommentPhrase("", "Clear explanations.", 1000))
        assertEquals("Clear explanations.", appendCommentPhrase("   ", "Clear explanations.", 1000))
    }

    @Test
    fun appendAfterSentenceAddsSpace() {
        assertEquals(
            "Loved the labs. Clear explanations.",
            appendCommentPhrase("Loved the labs.", "Clear explanations.", 1000)
        )
    }

    @Test
    fun appendAfterUnpunctuatedTextAddsFullStop() {
        assertEquals(
            "Loved the labs. Clear explanations.",
            appendCommentPhrase("Loved the labs  ", "Clear explanations.", 1000)
        )
    }

    @Test
    fun appendThatWouldExceedMaxLengthLeavesStudentTextUntouched() {
        val current = "x".repeat(990)
        assertEquals(current, appendCommentPhrase(current, "Clear explanations.", 1000))
    }
}
