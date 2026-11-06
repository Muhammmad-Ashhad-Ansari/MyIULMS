package com.example.myiulms.ui.policy

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myiulms.ui.components.InfoIconButton
import com.example.myiulms.ui.components.MyIulmsDropdownMenuItem

/**
 * Reusable entry points into the single hoisted policy sheet.
 *
 * Every trigger reads [LocalAcademicPolicyState] directly, so callers need no
 * callback plumbing and no duplicated state. Add as many entry points as needed;
 * there is still only one sheet instance, hosted by [AcademicPolicyHost].
 */

/** Header-slot trigger. Sits beside an existing ScreenHeading action icon. */
@Composable
fun PolicyInfoButton(modifier: Modifier = Modifier) {
    val policy = LocalAcademicPolicyState.current
    InfoIconButton(
        onClick = policy::open,
        contentDescription = "Grading and scholarship policy",
        modifier = modifier
    )
}

/** Overflow-menu entry. Matches the existing app-bar utility pattern. */
@Composable
fun PolicyOverflowItem(onDismissMenu: () -> Unit) {
    val policy = LocalAcademicPolicyState.current
    MyIulmsDropdownMenuItem(
        text = "Grading & scholarship policy",
        leadingIcon = Icons.Rounded.Info,
        onClick = {
            onDismissMenu()
            policy.open()
        }
    )
}

/** Low-emphasis inline link for contextual surfaces such as the fee screen. */
@Composable
fun PolicyTextLink(
    text: String = "How the merit scholarship works",
    modifier: Modifier = Modifier
) {
    val policy = LocalAcademicPolicyState.current
    TextButton(
        onClick = policy::open,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Convenience wrapper for placing the info trigger and a sibling action icon in
 * the same ScreenHeading action slot.
 */
@Composable
fun PolicyHeadingActions(content: @Composable () -> Unit) {
    Row {
        PolicyInfoButton()
        content()
    }
}

/**
 * Sole host for the sheet. Place once inside the CompositionLocalProvider in
 * the common root ancestor so a single instance serves the whole app.
 */
@Composable
fun AcademicPolicyHost() {
    val policy = LocalAcademicPolicyState.current
    if (policy.visible) {
        AcademicPolicySheet(onDismissRequest = policy::dismiss)
    }
}