package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards [reconcileSelectedDay], the rule that keeps the day-chip selection
 * honest after a refresh.
 *
 * The failure it prevents is silent: a selected day that vanished from the new
 * schedule leaves a lit chip with no group behind it, and nothing in the UI
 * explains why tapping it does nothing.
 */
class SelectedDayReconciliationTest {

    @Test
    fun `a selection whose day still exists is kept`() {
        assertEquals(
            "WED",
            reconcileSelectedDay("WED", setOf("MON", "WED"), fallback = null)
        )
    }

    @Test
    fun `a selection whose day disappeared falls back`() {
        assertNull(reconcileSelectedDay("TUE", setOf("MON", "WED"), fallback = null))
        assertEquals(
            "FRI",
            reconcileSelectedDay("TUE", setOf("MON", "FRI"), fallback = "FRI")
        )
    }

    @Test
    fun `no stored selection stays at the fallback`() {
        assertNull(reconcileSelectedDay(null, setOf("MON", "WED"), fallback = null))
        assertEquals("MON", reconcileSelectedDay(null, setOf("MON"), fallback = "MON"))
    }

    @Test
    fun `OTHER is discarded when it is not a class day`() {
        assertNull(reconcileSelectedDay("OTHER", setOf("MON", "WED"), fallback = null))
    }
}
