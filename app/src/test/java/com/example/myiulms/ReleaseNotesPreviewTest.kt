package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesPreviewTest {

    // Shape of the real v1.0.7 GitHub release body (abridged).
    private val v107 = """
        # MyIULMS v1.0.7

        Local, offline-friendly improvements to navigation, refreshing, and the weekly schedule.

        ## What's new

        - **Pull-to-refresh** — pull down on Schedule, Attendance, Results, Transcript, and Vouchers.
        - **Faster bottom navigation** — tapping a distant tab now jumps straight to it.
        - **Smoother layout** — list content scrolls softly behind the floating navigation bar.
        - **State kept while you browse** — sub-tabs, scroll positions and filters are preserved.
        - **Accurate class countdown** — the schedule card recalculates on return.
        - **Theme consistency** — the bottom navigation bar follows the app's theme.

        ## Notes

        - No new permissions, no analytics.
        - APK SHA-256: `62d236d6`
    """.trimIndent()

    @Test
    fun takesBoldTitlesFromWhatsNewSectionOnly() {
        val preview = releaseNotesPreview(v107)

        assertEquals(
            listOf(
                "Pull-to-refresh",
                "Faster bottom navigation",
                "Smoother layout",
                "State kept while you browse",
                "Accurate class countdown"
            ),
            preview.items
        )
        assertEquals(1, preview.remaining)
        // "Notes" bullets (permissions, checksum) never leak into the preview.
        assertTrue(preview.items.none { "SHA" in it || "permissions" in it })
    }

    @Test
    fun withoutWhatsNewHeadingUsesBulletsButSkipsNotesSection() {
        val body = """
            - Fixed login on slow networks
            - Better [attendance](https://example.com) colours

            ## Notes
            - APK SHA-256: `abc`
        """.trimIndent()

        val preview = releaseNotesPreview(body)

        assertEquals(listOf("Fixed login on slow networks", "Better attendance colours"), preview.items)
        assertEquals(0, preview.remaining)
    }

    @Test
    fun numberedListsAndInlineMarkdownAreCleaned() {
        val body = "## Changes\n1. Use `OkHttp` timeouts\n2) **Bold** start — detail"

        assertEquals(listOf("Use OkHttp timeouts", "Bold"), releaseNotesPreview(body).items)
    }

    @Test
    fun longPlainItemsAreTruncated() {
        val item = releaseNotesPreview("## What's new\n- " + "a".repeat(120)).items.single()
        assertEquals(78, item.length)
        assertTrue(item.endsWith("…"))
    }

    @Test
    fun emptyOrProseOnlyBodyGivesEmptyPreview() {
        assertTrue(releaseNotesPreview("").items.isEmpty())
        assertTrue(releaseNotesPreview("# v1.0.8\n\nJust fixes.").items.isEmpty())
    }

    @Test
    fun displayVersionAlwaysHasSingleVPrefix() {
        assertEquals("v1.0.8", displayVersion("v1.0.8"))
        assertEquals("v1.0.7", displayVersion("1.0.7"))
        assertEquals("v2.0", displayVersion(" V2.0 "))
    }
}
