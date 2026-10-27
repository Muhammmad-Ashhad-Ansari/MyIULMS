package com.example.myiulms

// ---------------- Comment suggestion chips (pure, unit-tested) ----------------
// Student khud chip tap karta hai — kuch bhi auto-fill nahi hota, phrase field
// mein APPEND hota hai aur student use edit kar sakta hai. Har field ka apna set:
// strengths wale field mein strengths, challenges wale mein constructive points —
// sirf tareef wale chips dena feedback ko ek taraf jhuka deta.

private val STRENGTH_PHRASES = listOf(
    "Clear explanations.",
    "Well-organized lectures.",
    "Practical, real-world examples.",
    "Helpful lab sessions.",
    "Approachable and supportive instructor.",
    "Useful course material."
)

private val CHALLENGE_PHRASES = listOf(
    "The pace is a bit fast.",
    "The workload is heavy.",
    "More practice examples would help.",
    "Some topics are hard to follow.",
    "More lab time would help.",
    "No major challenges so far."
)

private val GENERAL_PHRASES = listOf(
    "Thank you for the effort.",
    "More real-world examples would help.",
    "Clearer feedback on assessments would help.",
    "Overall a good learning experience."
)

// Portal prompt text se field ki qism pehchano (live form: "The best features of
// the course are:", "What challenges are you facing in this course?", "Any
// additional comments regarding the course or instructor:"). Challenges pehle
// check — "improve" wale prompts strengths mein na jayein.
internal fun commentSuggestions(prompt: String): List<String> {
    val p = prompt.lowercase()
    return when {
        listOf("challenge", "difficult", "problem", "improve").any { it in p } -> CHALLENGE_PHRASES
        listOf("best", "feature", "strength", "like").any { it in p } -> STRENGTH_PHRASES
        else -> GENERAL_PHRASES
    }
}

// Phrase ko existing text ke baad jodo. Pichla text punctuation par khatam na ho
// to ". " lagao. maxLength cross ho to text waisa hi rehta hai (phrase beech se
// kaatna student ke text ko bigaad deta).
internal fun appendCommentPhrase(current: String, phrase: String, maxLength: Int): String {
    val base = current.trimEnd()
    val joined = when {
        base.isEmpty() -> phrase
        base.last() in ".!?" -> "$base $phrase"
        else -> "$base. $phrase"
    }
    return if (joined.length <= maxLength) joined else current
}
