package com.example.myiulms.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing
import com.example.myiulms.ui.theme.GradeAverageDark
import com.example.myiulms.ui.theme.GradeAverageLight
import com.example.myiulms.ui.theme.GradeCriticalDark
import com.example.myiulms.ui.theme.GradeCriticalLight
import com.example.myiulms.ui.theme.GradeNeutralDark
import com.example.myiulms.ui.theme.GradeNeutralLight
import com.example.myiulms.ui.theme.GradeStrongDark
import com.example.myiulms.ui.theme.GradeStrongLight
import com.example.myiulms.ui.theme.GradeWeakDark
import com.example.myiulms.ui.theme.GradeWeakLight
import com.example.myiulms.ui.theme.WarningContainerDark
import com.example.myiulms.ui.theme.WarningContainerLight
import com.example.myiulms.ui.theme.WarningForegroundDark
import com.example.myiulms.ui.theme.WarningForegroundLight

/**
 * Academic policy reference for [AcademicPolicy.CAMPUS].
 *
 * Section order is intentional and should be preserved:
 * new scheme, previous scheme, scholarship requirements, official sources.
 *
 * The sheet caps at 92% of screen height and scrolls internally so content is
 * never clipped at large font scales. Meaning is never carried by colour alone;
 * every grade state and every unverified figure is stated in text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicPolicySheet(onDismissRequest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolve the sheet's height cap against the screen, not against the sheet's
    // own incoming constraints. A fractional fillMaxHeight inside a bottom sheet
    // is a feedback path: the sheet settles its height, which changes the
    // constraint, which re-measures the lazy viewport, which re-clamps the scroll
    // position. That is what produced the bounce at the bottom edge. A fixed dp
    // value derived from configuration is stable across that whole cycle.
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(
            topStart = AppRadius.Hero,
            topEnd = AppRadius.Hero
        ),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            SheetHeader(onDismissRequest = onDismissRequest)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = AppSpacing.Lg,
                    end = AppSpacing.Lg,
                    top = AppSpacing.Md,
                    bottom = AppSpacing.Sm
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                item { NewSchemeSection(AcademicPolicy.newScheme) }
                item { PreviousSchemeSection(AcademicPolicy.oldScheme) }
                item { ScholarshipSection(AcademicPolicy.scholarship) }
                item { VerifyOfficialPoliciesCard() }

                // Solid trailing spacer. Guarantees the last content item is never
                // flush against the sheet's bottom edge, so the final row cannot
                // oscillate between measured and clipped states during over-scroll.
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Academic Policy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = AcademicPolicy.CAMPUS_LABEL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close policy reference"
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
}

/**
 * Section title, cohort badge, and the passing-marks descriptor line.
 *
 * The descriptor is the highest-value line in the sheet: it states the pass
 * threshold and who it applies to, so a student can confirm their own situation
 * without reading the band table.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    badge: String?,
    badgeTone: PolicyBadgeTone
) {
    Column(modifier = Modifier.padding(bottom = AppSpacing.Md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(AppSpacing.Xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (badge != null) {
            Spacer(Modifier.height(AppSpacing.Sm))
            PolicyBadge(text = badge, tone = badgeTone)
        }
    }
}

@Composable
private fun PolicyBadge(text: String, tone: PolicyBadgeTone) {
    val (container, content) = badgeColors(tone)
    Surface(
        color = container,
        shape = RoundedCornerShape(AppRadius.Small)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AppSpacing.Md,
                vertical = AppSpacing.Xs
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

@Composable
private fun badgeColors(tone: PolicyBadgeTone): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (tone) {
        // Soft green, derived from the existing grade-strong token so no new
        // colour literals are introduced.
        PolicyBadgeTone.SUCCESS -> {
            val accent = if (dark) GradeStrongDark else GradeStrongLight
            accent.copy(alpha = if (dark) 0.20f else 0.14f) to accent
        }
        PolicyBadgeTone.CAUTION -> {
            if (dark) WarningContainerDark to WarningForegroundDark
            else WarningContainerLight to WarningForegroundLight
        }
    }
}

// ---- New scheme ------------------------------------------------------------

@Composable
private fun NewSchemeSection(section: PolicySection) {
    GradeBandTableSection(section = section)
}

@Composable
private fun GradeTableHeader(compact: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Grade",
            modifier = Modifier.weight(if (compact) 0.28f else 0.22f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Marks",
            modifier = Modifier.weight(if (compact) 0.72f else 0.48f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!compact) {
            Text(
                text = "Points",
                modifier = Modifier.weight(0.30f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GradeBandRow(band: GradeBand, compact: Boolean) {
    val tone = toneColor(band.tone)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = buildString {
                    append("Grade ${band.letter}, ${band.range}, ${band.points} grade points")
                    if (band.confidence == PolicyConfidence.UNVERIFIED) {
                        append(", not confirmed in a published source")
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(if (compact) 0.28f else 0.22f)
                .clearAndSetSemantics { }
        ) {
            Surface(
                color = tone.copy(alpha = 0.12f),
                shape = RoundedCornerShape(AppRadius.Small)
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Md,
                        vertical = AppSpacing.Xs
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = band.letter,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tone
                    )
                    if (band.confidence == PolicyConfidence.UNVERIFIED) {
                        Spacer(Modifier.width(AppSpacing.Xs))
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = band.range,
            modifier = Modifier.weight(if (compact) 0.72f else 0.48f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!compact) {
            Text(
                text = band.points,
                modifier = Modifier.weight(0.30f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ---- Previous scheme -------------------------------------------------------

@Composable
private fun PreviousSchemeSection(section: PolicySection) {
    GradeBandTableSection(section = section)
}

/** Shared three-column band table used by both grading schemes. */
@Composable
private fun GradeBandTableSection(section: PolicySection) {
    val compact = LocalDensity.current.fontScale >= 1.3f

    Column {
        SectionHeader(
            title = section.title,
            subtitle = section.subtitle,
            badge = section.badge,
            badgeTone = section.badgeTone
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(AppSpacing.Lg)) {
                GradeTableHeader(compact = compact)

                section.bands.forEachIndexed { index, band ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = AppSpacing.Sm),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                    GradeBandRow(band = band, compact = compact)
                }

                section.notes.forEach { note ->
                    Text(
                        text = note,
                        modifier = Modifier.padding(top = AppSpacing.Md),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---- Scholarship ------------------------------------------------------------

@Composable
private fun ScholarshipSection(section: PolicySection) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val waiverForeground = if (dark) WarningForegroundDark else WarningForegroundLight
    val waiverContainer = if (dark) WarningContainerDark else WarningContainerLight

    Column {
        SectionHeader(
            title = section.title,
            subtitle = section.subtitle,
            badge = section.badge,
            badgeTone = PolicyBadgeTone.SUCCESS
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                AcademicPolicy.scholarshipTiers.forEach { tier ->
                    ScholarshipTierRow(
                        tier = tier,
                        waiverContainer = waiverContainer,
                        waiverForeground = waiverForeground
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )

                ConditionChips()

                Text(
                    text = AcademicPolicy.scholarshipNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScholarshipTierRow(
    tier: ScholarshipTier,
    waiverContainer: Color,
    waiverForeground: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "${tier.bracket}, ${tier.waiver} tuition fee waiver, ${tier.condition}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(AppRadius.Small)
        ) {
            Text(
                text = tier.bracket,
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Md,
                    vertical = AppSpacing.Sm
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = AppSpacing.Sm)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            color = waiverContainer,
            shape = RoundedCornerShape(AppRadius.Small)
        ) {
            Text(
                text = tier.waiver,
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Md,
                    vertical = AppSpacing.Sm
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = waiverForeground
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionChips() {
    // maxItemsInEachRow pins the wrap point for the current chip set so the row
    // height is settled rather than renegotiated on every remeasure. Chips keep a
    // bounded max width so a long label cannot widen the row and change the
    // measured height of the enclosing lazy item.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        maxItemsInEachRow = 2
    ) {
        AcademicPolicy.scholarshipConditionChips.forEach { chip ->
            Surface(
                modifier = Modifier.heightIn(min = 40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(AppRadius.Small)
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(
                            horizontal = AppSpacing.Md,
                            vertical = AppSpacing.Sm
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ---- Official sources ------------------------------------------------------

/**
 * The verification call to action and the source link list. Intentionally the
 * only footer: the highest-contrast surface in the sheet, so students leave
 * with the official page rather than trusting an unofficial summary.
 */
@Composable
private fun VerifyOfficialPoliciesCard() {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    text = "Verify Official Policies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            AcademicPolicy.sources.forEach { source ->
                val openLabel = "Open ${source.label}"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(AppRadius.Medium)
                        )
                        .clickable {
                            runCatching { uriHandler.openUri(source.url) }
                        }
                        .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm)
                        .semantics { contentDescription = openLabel },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = source.url,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Text(
                text = "Registrar verification: ${AcademicPolicy.REGISTRAR_EMAIL}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun toneColor(tone: GradeTone): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (tone) {
        GradeTone.STRONG -> if (dark) GradeStrongDark else GradeStrongLight
        GradeTone.AVERAGE -> if (dark) GradeAverageDark else GradeAverageLight
        GradeTone.WEAK -> if (dark) GradeWeakDark else GradeWeakLight
        GradeTone.CRITICAL -> if (dark) GradeCriticalDark else GradeCriticalLight
        GradeTone.NEUTRAL -> if (dark) GradeNeutralDark else GradeNeutralLight
    }
}