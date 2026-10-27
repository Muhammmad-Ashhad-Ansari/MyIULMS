package com.example.myiulms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the screen-load state machine introduced with the
 * faculty-evaluation gate. Every transition MainViewModel performs goes
 * through these helpers, so these tests pin the three invariants the UI
 * depends on:
 *
 *  1. `portalActionRequired` and `error` are mutually exclusive — a gate
 *     screen must never render the "Connection problem" overlay.
 *  2. A gate flag never survives into an error/success/loading state (no
 *     stale gate card after data recovers, and vice versa).
 *  3. Re-probing (loading) starts from a clean slate, so an old gate flag
 *     cannot hide a fresh spinner.
 */
class GateStateTransitionTest {

    @Test
    fun defaultStateIsNeitherGateNorError() {
        val s = ScreenLoadState()
        assertFalse(s.portalActionRequired)
        assertNull(s.error)
        assertFalse(s.loading)
    }

    @Test
    fun gateStateIsActionableNotAnError() {
        val s = screenStateGate()
        assertTrue(s.portalActionRequired)
        assertFalse("gate must not look like a load in flight", s.loading)
        assertNull("gate must never render the Connection problem overlay", s.error)
    }

    @Test
    fun errorStateClearsStaleGateFlag() {
        val s = screenStateError("Couldn't load schedule. Check your connection and try again.")
        assertFalse(s.portalActionRequired)
        assertFalse(s.loading)
        assertNotNull(s.error)
    }

    @Test
    fun sessionExpiredStyleErrorClearsGateFlag() {
        val s = screenStateError("Session expired. Please sign in again.")
        assertFalse("session expiry replaces any gate card", s.portalActionRequired)
        assertNotNull(s.error)
    }

    @Test
    fun loadingTransitionClearsGateAndErrorForFreshProbe() {
        // Pull/toolbar refresh after a gate: the spinner must be visible and the
        // old gate card must not linger while the portal is re-probed.
        val previouslyGated = screenStateGate()
        val s = screenStateLoading(previouslyGated)
        assertTrue(s.loading)
        assertFalse(s.portalActionRequired)
        assertNull(s.error)

        val previouslyErrored = screenStateError("boom")
        val s2 = screenStateLoading(previouslyErrored)
        assertTrue(s2.loading)
        assertNull(s2.error)
        assertFalse(s2.portalActionRequired)
    }

    @Test
    fun successTransitionClearsGateAndError() {
        // Data recovered (evaluations completed, return-flow re-probe succeeded):
        // the gate flag must be gone so the gate card cannot outlive the gate.
        val afterGate = screenStateSuccess()
        assertFalse(afterGate.portalActionRequired)
        assertNull(afterGate.error)
        assertFalse(afterGate.loading)
    }

    @Test
    fun noTransitionEverCombinesGateWithErrorOrLoading() {
        val transitions = listOf(
            ScreenLoadState(),
            screenStateGate(),
            screenStateSuccess(),
            screenStateError("Couldn't load transcript. Check your connection and try again."),
            screenStateLoading(screenStateGate()),
            screenStateLoading(screenStateError("boom")),
            screenStateLoading(ScreenLoadState())
        )
        for (s in transitions) {
            assertFalse(
                "gate and error must be mutually exclusive: $s",
                s.portalActionRequired && s.error != null
            )
            assertFalse(
                "gate and loading must be mutually exclusive: $s",
                s.portalActionRequired && s.loading
            )
            assertFalse(
                "loading and error must be mutually exclusive: $s",
                s.loading && s.error != null
            )
        }
    }
}
