package com.example.myiulms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.myiulms.ui.theme.AppRadius

/*
 * The app's menu: a compact, rounded popup sized to its content, used for
 * every dropdown (overflow menu, transcript filter, reminder time).
 *
 * Built on Material's DropdownMenu for positioning, dismissal and focus, with
 * its own item row: Material's DropdownMenuItem fixes a 48dp minimum height and
 * 112-280dp width that this design deliberately overrides.
 */

private val MenuItemShape = RoundedCornerShape(10.dp)
private val MenuItemMinHeight = 40.dp
private val MenuIconSize = 20.dp

/**
 * Rounded (16dp) popup as wide as its widest row. Rows inside are inset by 6dp,
 * so a selected row's tint reads as a pill, not an edge-to-edge band.
 */
@Composable
fun MyIulmsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(AppRadius.Medium),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 160.dp, max = 300.dp)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

/**
 * One menu row: 40dp minimum height, content-sized width.
 *
 * @param checked null for an action row (shows [leadingIcon]); true/false for a
 *   choice in a single-select list: a primary check on the chosen row, an empty
 *   slot on the others so labels align, and a soft primary tint behind the
 *   chosen row.
 * @param trailing optional end content: a count, a switch.
 */
@Composable
fun MyIulmsDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val isChosen = checked == true
    val disabledAlpha = if (enabled) 1f else 0.38f
    val contentColor = (if (destructive) colors.error else colors.onSurface).copy(alpha = disabledAlpha)
    val iconColor = when {
        destructive -> colors.error.copy(alpha = disabledAlpha)
        isChosen -> colors.primary
        else -> colors.onSurfaceVariant.copy(alpha = disabledAlpha)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MenuItemMinHeight)
            .clip(MenuItemShape)
            .then(if (isChosen) Modifier.background(colors.primary.copy(alpha = 0.10f)) else Modifier)
            .clickable(
                enabled = enabled,
                role = if (checked != null) Role.RadioButton else Role.Button,
                onClick = onClick
            )
            .then(if (checked != null) Modifier.semantics { selected = isChosen } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (checked != null) Icons.Rounded.Check.takeIf { isChosen } else leadingIcon
        if (icon != null || checked != null) {
            Box(Modifier.size(MenuIconSize), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(MenuIconSize))
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isChosen) colors.primary else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = disabledAlpha),
                    maxLines = 1
                )
            }
        }
        if (trailing != null) {
            // Fixed gap: the popup is as wide as its widest row, so the end
            // content sits just past the longest label, never far across.
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** Small muted title above a group of choices, e.g. "Remind me before class". */
@Composable
fun MyIulmsDropdownMenuHeader(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Hairline between groups of rows. */
@Composable
fun MyIulmsDropdownMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}
