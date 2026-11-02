package com.example.myiulms

import com.example.myiulms.ui.dashboard.formatLastUpdated
import com.example.myiulms.ui.dashboard.scheduleFreshnessNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LastUpdatedTest {

    private val karachi = ZoneId.of("Asia/Karachi")

    private fun at(month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, karachi).toInstant().toEpochMilli()

    // Tuesday 3 Nov 2026, 10:00 campus time.
    private val now = at(11, 3, 10, 0)

    @Test
    fun `same campus day shows today and the clock`() {
        assertEquals("today 9:12 AM", formatLastUpdated(at(11, 3, 9, 12), now))
    }

    @Test
    fun `previous campus day shows yesterday, even just after midnight`() {
        assertEquals("yesterday 11:50 PM", formatLastUpdated(at(11, 2, 23, 50), at(11, 3, 0, 5)))
    }

    @Test
    fun `within a week shows the weekday`() {
        assertEquals("Sat 4:30 PM", formatLastUpdated(at(10, 31, 16, 30), now))
    }

    @Test
    fun `a week or older shows the date`() {
        assertEquals("27 Oct", formatLastUpdated(at(10, 27, 9, 0), now))
    }

    @Test
    fun `fresh portal data at rest needs no note`() {
        assertNull(scheduleFreshnessNote(at(11, 3, 9, 12), fromCache = false, syncing = false, nowMillis = now))
        assertNull(scheduleFreshnessNote(null, fromCache = true, syncing = false, nowMillis = now))
    }

    @Test
    fun `a manual refresh of fresh data still says it is syncing`() {
        assertEquals(
            "Syncing… · last updated today 9:12 AM",
            scheduleFreshnessNote(at(11, 3, 9, 12), fromCache = false, syncing = true, nowMillis = now)
        )
        assertEquals("Syncing…", scheduleFreshnessNote(null, fromCache = false, syncing = true, nowMillis = now))
    }

    @Test
    fun `saved copy says whether it is syncing`() {
        assertEquals(
            "Syncing… · last updated today 9:12 AM",
            scheduleFreshnessNote(at(11, 3, 9, 12), fromCache = true, syncing = true, nowMillis = now)
        )
        assertEquals(
            "Saved copy · last updated today 9:12 AM",
            scheduleFreshnessNote(at(11, 3, 9, 12), fromCache = true, syncing = false, nowMillis = now)
        )
    }
}
