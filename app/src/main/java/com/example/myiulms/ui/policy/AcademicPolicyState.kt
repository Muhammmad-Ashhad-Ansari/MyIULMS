package com.example.myiulms.ui.policy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Single hoisted visibility flag for the academic policy sheet.
 *
 * This is deliberately NOT a ViewModel. The policy content is static reference
 * material with no portal, network, or session dependency, so pushing it
 * through [com.example.myiulms.MainViewModel] would couple a read-only document
 * to the authentication and screen-loading lifecycle for no benefit.
 *
 * Provide once from the common root ancestor with
 * `CompositionLocalProvider(LocalAcademicPolicyState provides rememberAcademicPolicyState())`
 * and read it anywhere below with `LocalAcademicPolicyState.current`.
 */
@Stable
class AcademicPolicyState internal constructor() {

    var visible by mutableStateOf(false)
        private set

    fun open() {
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

@Composable
fun rememberAcademicPolicyState(): AcademicPolicyState =
    remember { AcademicPolicyState() }

val LocalAcademicPolicyState = staticCompositionLocalOf<AcademicPolicyState> {
    error(
        "AcademicPolicyState not provided. Wrap the call site in " +
            "CompositionLocalProvider(LocalAcademicPolicyState provides ...)."
    )
}