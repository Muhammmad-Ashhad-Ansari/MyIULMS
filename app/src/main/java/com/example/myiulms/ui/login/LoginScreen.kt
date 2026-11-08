package com.example.myiulms.ui.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Update
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiulms.MainViewModel
import com.example.myiulms.R
import com.example.myiulms.ui.components.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * The public "Before you sign in" guide (GitHub Pages, output/myiulms-data-flow-figure):
 * an interactive walkthrough of the app → iulms.edu.pk sign-in flow.
 */
private const val SIGN_IN_GUIDE_URL = "https://muhammmad-ashhad-ansari.github.io/MyIULMS/"

/** Opened by the "Made by Not_Einstein" credit. */
private const val DEVELOPER_PROFILE_URL = "https://github.com/Muhammmad-Ashhad-Ansari"

/** One radius for every control on this screen: fields, button. */
private val ControlRadius = 16.dp
private val ControlShape = RoundedCornerShape(ControlRadius)
private val ControlHeight = 56.dp
private val Gutter = 24.dp

/** Entrance: each block fades and rises in, 40ms after the one before. */
private const val ENTER_MILLIS = 200
private const val ENTER_STAGGER_MILLIS = 40

/** Header grow/shrink as the keyboard closes/opens. */
private const val HEADER_MILLIS = 300

/** Resting header = compact header × this (32dp logo → 40dp, 22sp → 27.5sp). */
private const val HEADER_REST_SCALE = 1.25f

/**
 * Sign-in screen.
 *
 * Layout: the brand mark at the top, the form optically centred (a little
 * above the middle, via a 1 : 1.3 split of the free space), and the footer on
 * the bottom safe area. The whole column scrolls, and the root's imePadding
 * shrinks it when the keyboard opens, so nothing is ever covered.
 *
 * Trust: the screen states where the login goes in one small chip, and the
 * sheet behind it explains the flow. Wording follows README "Trust and
 * privacy" and FAQ §5.1: the login *is* transmitted, to iulms.edu.pk only.
 * Never "stored locally" or "never sent".
 *
 * Colour: brand blue is the only accent (button, chip, focus, switch, link).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    vm: MainViewModel,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    var user by remember(vm.savedUser) { mutableStateOf(vm.savedUser.orEmpty()) }
    var pass by remember(vm.savedPassword) { mutableStateOf(vm.savedPassword.orEmpty()) }
    var rememberPassword by remember { mutableStateOf(vm.savedPassword != null) }
    var showPassword by remember { mutableStateOf(false) }
    var showHowItWorks by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme

    val canSubmit = !vm.loading && user.isNotBlank() && pass.isNotBlank()
    val submit = {
        keyboard?.hide()
        vm.login(user.trim(), pass, rememberPassword)
    }

    // Plays once per visit to the screen.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // Read directly (not `.value`): in foundation-layout 1.10 isImeVisible is a
    // @Composable getter returning Boolean.
    val imeVisible = WindowInsets.isImeVisible
    val signInRequester = remember { BringIntoViewRequester() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // While the keyboard is up, keep the Sign in button (and the line
        // under it) in view. Keyed on maxHeight as well: imePadding shrinks
        // this box frame by frame as the keyboard slides in, each change
        // restarts the effect, and the last run scrolls to the final layout.
        // Focusing a field only brings that field into view, which left the
        // button under the keyboard.
        LaunchedEffect(imeVisible, maxHeight) {
            if (imeVisible) signInRequester.bringIntoView()
        }

        // A scrolling column at least as tall as the screen: the weighted
        // spacers share whatever is left over, and when nothing is left over
        // (keyboard up, large fonts) it simply scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = Gutter)
        ) {
            BrandHeader(
                expanded = !imeVisible,
                darkTheme = darkTheme,
                onThemeToggle = onThemeToggle,
                onCheckForUpdates = onCheckForUpdates
            )

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))

            Column(Modifier.entrance(0, entered)) {
                Text(
                    "Sign in to IULMS",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use your IULMS registration number and password.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            TrustChip(
                onClick = { showHowItWorks = true },
                modifier = Modifier.entrance(1, entered)
            )

            Spacer(Modifier.height(32.dp))

            LoginField(
                value = user,
                onValueChange = { user = it.filter(Char::isDigit) },
                label = "Registration number",
                leadingIcon = Icons.Outlined.Badge,
                modifier = Modifier.entrance(2, entered),
                textFieldModifier = Modifier.semantics { contentType = ContentType.Username },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(Modifier.height(16.dp))

            LoginField(
                value = pass,
                onValueChange = { pass = it },
                label = "IULMS password",
                leadingIcon = Icons.Outlined.Lock,
                modifier = Modifier.entrance(3, entered),
                textFieldModifier = Modifier.semantics { contentType = ContentType.Password },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() else keyboard?.hide() }),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
            )

            Spacer(Modifier.height(16.dp))

            RememberMeRow(
                checked = rememberPassword,
                onCheckedChange = { rememberPassword = it },
                modifier = Modifier.entrance(4, entered)
            )

            vm.errorMsg?.let { message ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    color = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                    shape = ControlShape
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // The button and the line under it are brought into view as one
            // block when the keyboard opens.
            Column(Modifier.bringIntoViewRequester(signInRequester)) {
                SignInButton(
                    enabled = canSubmit,
                    loading = vm.loading,
                    onClick = {
                        haptics.confirm()
                        submit()
                    },
                    modifier = Modifier.entrance(5, entered)
                )
                Spacer(Modifier.height(16.dp))
                FooterText(
                    "Unofficial, not affiliated with Iqra University.",
                    modifier = Modifier.entrance(5, entered)
                )
                Spacer(Modifier.height(8.dp))
            }

            // 1 : 1.3 puts the form a little above the true centre, which
            // reads as centred; mathematically centred reads as low.
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1.3f))

            DeveloperCredit(modifier = Modifier.padding(bottom = 4.dp))
        }
    }

    if (showHowItWorks) {
        HowItWorksSheet(onDismiss = { showHowItWorks = false })
    }
}

/**
 * Brand mark plus the update and theme actions. Large while the keyboard is
 * hidden, so it fills the open space above the form; it shrinks back to the
 * compact size over 300ms when the keyboard opens, giving the form room.
 */
@Composable
private fun BrandHeader(
    expanded: Boolean,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    // One scale drives the whole lockup (logo, corner, gap, wordmark), so the
    // resting state is the compact header made larger, not a bigger icon next
    // to the same text. The logo stays ~1.45x the wordmark's font size in
    // both states, which keeps the icon from overpowering the name.
    val scale by animateFloatAsState(
        if (expanded) HEADER_REST_SCALE else 1f,
        tween(HEADER_MILLIS, easing = FastOutSlowInEasing),
        label = "brandScale"
    )
    val logoSize = 32.dp * scale
    val logoCorner = 9.dp * scale
    val gap = 10.dp * scale
    val wordmarkSp = 22f * scale
    val rowHeight = 64.dp + 8.dp * ((scale - 1f) / (HEADER_REST_SCALE - 1f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The app's own mark, as in the Home top bar. Deliberately not the
            // university's crest: MyIULMS is unofficial, and an IU logo on a
            // screen that asks for IU credentials would imply an affiliation
            // that does not exist.
            Image(
                painter = painterResource(R.drawable.myiulms_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(logoCorner)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(gap))
            Text(
                text = "MyIULMS",
                // The text box hugs the glyphs (line height = font size, top
                // and bottom trimmed, no font padding), so centring the row
                // centres the letters on the logo rather than a padded box.
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = wordmarkSp.sp,
                    lineHeight = wordmarkSp.sp,
                    letterSpacing = (-0.2).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        // Pulled 12dp right so the glyphs, not the 48dp touch targets, line
        // up with the right edge of the fields. Fixed size: these are
        // controls, not part of the brand lockup.
        Row(
            modifier = Modifier.offset(x = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCheckForUpdates) {
                Icon(
                    Icons.Outlined.Update,
                    contentDescription = "Check for updates",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The single trust marker. Taps open [HowItWorksSheet]. */
@Composable
private fun TrustChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        // A solid (opaque) tonal tint of the brand blue over the page, edged
        // with a full-strength 1.5dp primary border: a defined, high-contrast
        // pill rather than a faint wash.
        color = colors.primary.copy(alpha = if (dark) 0.22f else 0.16f).compositeOver(colors.background),
        contentColor = colors.primary,
        border = BorderStroke(1.5.dp, colors.primary)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Direct to iulms.edu.pk, no middle server",
                color = colors.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 56dp field, 16dp corners, no grey fill. Light: a white field lifted by a
 * soft shadow over the off-white page. Dark: a raised surface tone. Focus:
 * a 2dp brand border and a faint blue glow.
 */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(150), label = "fieldBorder")
    val borderColor by animateColorAsState(
        if (focused) colors.primary else colors.outline,
        tween(150),
        label = "fieldBorderColor"
    )
    val elevation by animateDpAsState(
        when {
            focused -> 8.dp
            dark -> 0.dp
            else -> 1.dp
        },
        tween(150),
        label = "fieldElevation"
    )
    val shadowColor = if (focused) colors.primary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.10f)
    val container = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .shadow(elevation, ControlShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .background(container, ControlShape)
            .border(borderWidth, borderColor, ControlShape)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = textFieldModifier.fillMaxSize(),
            label = { Text(label) },
            leadingIcon = { Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = trailingIcon,
            singleLine = true,
            shape = ControlShape,
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant,
                focusedLeadingIconColor = colors.primary,
                unfocusedLeadingIconColor = colors.onSurfaceVariant,
                focusedTrailingIconColor = colors.onSurfaceVariant,
                unfocusedTrailingIconColor = colors.onSurfaceVariant,
                cursorColor = colors.primary
            )
        )
    }
}

/** The whole row toggles; the switch is the visual. */
@Composable
private fun RememberMeRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Remember me",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Encrypted on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        // The theme's outline colour is a hairline tone (#2A3742 in dark), so
        // the default "off" thumb and border vanish into the track. Off uses
        // the muted text colour instead; on stays the brand accent.
        val colors = MaterialTheme.colorScheme
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.onSurfaceVariant
            )
        )
    }
}

/**
 * Solid brand blue when ready; a faded tint of the same blue (never grey)
 * while the form is incomplete. Presses scale to 98%. While signing in the
 * button keeps its full colour and shows only a spinner.
 */
@Composable
private fun SignInButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "buttonPress")

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ControlShape,
        interactionSource = interaction,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        // The exact primary role in every state, as on the voucher buttons,
        // and the label at full onPrimary whether or not the fields are
        // filled, so it never drops to Material's muted disabled tint.
        // onPrimary is pure white in the light theme; in dark it is the deep
        // navy that the light-blue dark primary needs (white on #82CFFF is
        // about 1.7:1).
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary,
            disabledContentColor = colors.onPrimary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = "Signing in to iulms.edu.pk" },
                strokeWidth = 2.5.dp,
                color = colors.onPrimary
            )
        } else {
            // Colour set on the Text itself as well, so no inherited
            // LocalContentColor can dim it.
            Text(
                "Sign in",
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 12sp, normal tracking, centred. Full onSurfaceVariant: it is about 5:1 on
 * the light surface, so any visible fade would take 12sp text below WCAG AA's
 * 4.5:1.
 */
@Composable
private fun FooterText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * "Made by Not_Einstein", quietly tappable: it opens the developer's GitHub
 * profile. Same type and muted colour as the line above, no link styling.
 * The ripple is a soft pill around the words only (not the full width), and
 * minimumInteractiveComponentSize keeps the touch target at 48dp.
 */
@Composable
private fun DeveloperCredit(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Made by Not_Einstein",
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClickLabel = "Open the developer's GitHub profile") {
                    // No browser installed must not crash the sign-in screen.
                    runCatching { uriHandler.openUri(DEVELOPER_PROFILE_URL) }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** What happens to the login, in four plain steps, plus the full guide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowItWorksSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Gutter, end = Gutter, bottom = 16.dp)
        ) {
            Text(
                "How sign-in works",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(20.dp))
            FlowStep(1, "You enter your IULMS registration number and password.")
            FlowStep(2, "MyIULMS sends them over HTTPS straight to iulms.edu.pk, exactly as a browser would.")
            FlowStep(3, "There is no MyIULMS server or database in between. No analytics, no tracking.")
            FlowStep(
                4,
                "With Remember me on, your login is kept encrypted on this phone only. With it off, your login isn't saved.",
                last = true
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = close,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ControlHeight),
                shape = ControlShape
            ) {
                Text("Got it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { runCatching { uriHandler.openUri(SIGN_IN_GUIDE_URL) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("See the step-by-step guide")
            }
        }
    }
}

@Composable
private fun FlowStep(number: Int, text: String, last: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(bottom = if (last) 0.dp else 16.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(colors.primary.copy(alpha = if (isDarkSurface()) 0.18f else 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = colors.onSurface
        )
    }
}

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Staggered fade-and-rise on first composition. */
@Composable
private fun Modifier.entrance(index: Int, entered: Boolean, rise: Dp = 12.dp): Modifier {
    val delay = index * ENTER_STAGGER_MILLIS
    val alpha by animateFloatAsState(
        if (entered) 1f else 0f,
        tween(ENTER_MILLIS, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "enterAlpha"
    )
    val offset by animateDpAsState(
        if (entered) 0.dp else rise,
        tween(ENTER_MILLIS, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "enterOffset"
    )
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    return graphicsLayer {
        this.alpha = alpha
        translationY = offsetPx
    }
}
