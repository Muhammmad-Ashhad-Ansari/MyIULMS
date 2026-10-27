package com.example.myiulms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing
import com.example.myiulms.ui.theme.MyIULMSBlue
import com.example.myiulms.ui.theme.MyIULMSNavy
import com.example.myiulms.ui.theme.MyIULMSTheme

// "Update available" jaan-boojh kar "up to date" se bilkul alag dikhta hai: brand
// gradient hero (dono themes mein wahi deep blue — white text ≈7:1+), glowing
// rocket, bada version number, release notes preview aur full-width elevated CTA.
// Up-to-date / failed / checking states MainActivity ke calm AlertDialog mein hain.
@Composable
internal fun UpdateAvailableDialog(
    currentVersion: String,
    latest: LatestAppRelease,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val preview = remember(latest.notes) { releaseNotesPreview(latest.notes) }
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(AppRadius.Hero),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .padding(horizontal = AppSpacing.Xl)
                .widthIn(max = 420.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                UpdateHero(currentVersion = currentVersion, latestVersion = latest.version)
                Column(
                    modifier = Modifier.padding(AppSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
                ) {
                    ReleaseNotesList(preview = preview)
                    Button(
                        onClick = onUpdate,
                        shape = RoundedCornerShape(AppRadius.Medium),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                        Text(
                            text = "Get ${displayVersion(latest.version)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = AppSpacing.Sm)
                        )
                    }
                    // CTA asal mein kya karta hai — koi silent install nahi (AI_HANDOFF §8).
                    Text(
                        text = "Opens the GitHub release page to download the APK. Android asks you to confirm the install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(text = "Later")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateHero(currentVersion: String, latestVersion: String) {
    // Deep brand gradient dono themes mein same — dark mode ka light `primary`
    // (#82CFFF) gradient par white text kamzor hota; hero ka kaam hi contrast hai.
    val gradient = Brush.linearGradient(listOf(MyIULMSBlue, MyIULMSNavy))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(horizontal = AppSpacing.Xl, vertical = AppSpacing.Xl)
    ) {
        // Glow: radial white halo + translucent ring ke andar rocket.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = "New update available",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = displayVersion(latestVersion),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.semantics { contentDescription = "Version ${latestVersion.trimStart('v', 'V')}" }
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White
        ) {
            Text(
                text = "You have ${displayVersion(currentVersion)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs)
            )
        }
    }
}

@Composable
private fun ReleaseNotesList(preview: ReleaseNotesPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
        Text(
            text = "What's new",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        if (preview.items.isEmpty()) {
            Text(
                text = "Improvements and fixes. Full details are on the release page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        preview.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp)
                )
                Text(text = item, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (preview.remaining > 0) {
            Text(
                text = "+ ${preview.remaining} more on the release page",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun displayVersion(version: String): String = "v" + version.trim().trimStart('v', 'V')

// ---------------- Previews (android preview render) ----------------

private val previewRelease = LatestAppRelease(
    version = "v1.0.8",
    releaseUrl = "https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/tag/v1.0.8",
    notes = """
        # MyIULMS v1.0.8

        ## What's new

        - **Native course evaluations** — fill and submit faculty evaluations without leaving the app.
        - **Quick fill** — one tap fills the remaining questions, with Undo.
        - **Clearer CAPTCHA** — bigger code, one-tap refresh.
        - **Comment suggestions** — tap a phrase, then edit it.
        - **Theme fixes** — evaluation screens follow the in-app theme.
        - **Faster loading** — shared portal session.

        ## Notes

        - APK SHA-256: `abc123`
    """.trimIndent()
)

@Preview(name = "Update available — light", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun UpdateAvailableLightPreview() {
    MyIULMSTheme(darkTheme = false) {
        UpdateAvailableDialog(currentVersion = "1.0.7", latest = previewRelease, onUpdate = {}, onLater = {})
    }
}

@Preview(name = "Update available — dark", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = 0xFF0C1116)
@Composable
private fun UpdateAvailableDarkPreview() {
    MyIULMSTheme(darkTheme = true) {
        UpdateAvailableDialog(currentVersion = "1.0.7", latest = previewRelease, onUpdate = {}, onLater = {})
    }
}
