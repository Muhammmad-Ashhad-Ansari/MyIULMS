package com.example.myiulms.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.graphics.Color

// internal: the home-screen widget reuses these below Android 12.
internal val LightColors = lightColorScheme(
    primary = MyIULMSBlue,
    onPrimary = Color.White,
    primaryContainer = MyIULMSBlueSoft,
    onPrimaryContainer = MyIULMSNavy,
    secondary = MyIULMSNavy,
    onSecondary = Color.White,
    // Selected SegmentedButton / FilledTonal / NavigationBar indicator yahin se rang lete
    // hain — define na hon to M3 baseline lavender (#E8DEF8). Luminance baseline ke qareeb.
    // onSecondaryContainer navy on #DDE7F2 ≈ 7:1.
    secondaryContainer = Color(0xFFDDE7F2),
    onSecondaryContainer = MyIULMSNavy,
    // Tertiary: slate-teal (baseline mauve #7D5260 ki jagah); on-colors ≈ 7:1+.
    tertiary = Color(0xFF3D6373),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E7EE),
    onTertiaryContainer = Color(0xFF173543),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    // Surface containers — define na hon to M3 baseline (lavender) aata hai: AlertDialog
    // (High), default Card (Highest), menus/NavigationBar (Container), sheets (Low).
    // Luminance baseline ke qareeb rakhi hai (sirf hue cool blue-grey) taake 38 default
    // Cards ka background se separation pehle jaisa rahe.
    surfaceDim = Color(0xFFDCE3EA),
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF5F8FB),
    surfaceContainer = Color(0xFFEFF3F7),
    surfaceContainerHigh = Color(0xFFE9EEF3),
    surfaceContainerHighest = Color(0xFFE3E9EF),
    // Snackbar inverse tokens — define na hon to M3 baseline purple aata hai.
    inverseSurface = LightText,
    inverseOnSurface = LightSurfaceAlt,
    inversePrimary = Color(0xFF82CFFF),
    error = Danger,
    errorContainer = DangerSoft,
    onErrorContainer = Color(0xFF410002)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF82CFFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = MyIULMSBlueSoftDark,
    onPrimaryContainer = Color(0xFFB9E4FF),
    secondary = Color(0xFFAAC7FF),
    onSecondary = Color(0xFF0A2F5D),
    // Slate-navy containers (baseline dark lavender #4A4458 ki jagah).
    secondaryContainer = Color(0xFF2B3D55),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF9FCADB),
    onTertiary = Color(0xFF00313F),
    tertiaryContainer = Color(0xFF224552),
    onTertiaryContainer = Color(0xFFC9E8F3),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    // Deep slate/navy ladder (DarkBackground #0C1116 → DarkSurfaceAlt #1B252E ke
    // aas-paas), luminance M3 dark baseline jaisi — Cards/dialogs background se alag dikhein.
    surfaceDim = DarkBackground,
    surfaceBright = Color(0xFF2E3A46),
    surfaceContainerLowest = Color(0xFF0A0F14),
    surfaceContainerLow = Color(0xFF151C24),
    surfaceContainer = Color(0xFF19222B),
    surfaceContainerHigh = Color(0xFF212B35),
    surfaceContainerHighest = Color(0xFF2A3541),
    inverseSurface = DarkText,
    inverseOnSurface = DarkSurface,
    inversePrimary = MyIULMSBlue,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * The in-app light/dark choice ("iulms_ui" → "dark_theme"), held once per
 * process. Every screen observes this one flow, so no activity instance can
 * keep a stale copy of the theme: a toggle anywhere updates all of them.
 * null = no choice yet, follow the phone.
 */
object ThemeSettings {
    private const val PREFS = "iulms_ui"
    private const val KEY_DARK = "dark_theme"

    @Volatile
    private var state: MutableStateFlow<Boolean?>? = null

    private fun flow(context: Context): MutableStateFlow<Boolean?> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
                    if (contains(KEY_DARK)) getBoolean(KEY_DARK, false) else null
                }
            ).also { state = it }
        }

    fun savedDark(context: Context): StateFlow<Boolean?> = flow(context.applicationContext).asStateFlow()

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_DARK, dark) }
        flow(context.applicationContext).value = dark
    }
}

fun isSystemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

// MainActivity aur EvaluationActivity dono yahi padhte hain taake theme match kare.
fun resolveSavedDarkTheme(context: Context): Boolean =
    ThemeSettings.savedDark(context).value ?: isSystemDark(context)

/**
 * Edge-to-edge with status and navigation bar icons matched to the app's own
 * theme choice, not the phone's dark mode: dark icons in light mode, light
 * icons in dark mode. Call again whenever the in-app theme changes.
 */
fun ComponentActivity.applySystemBars(dark: Boolean) {
    val transparent = android.graphics.Color.TRANSPARENT
    val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

@Composable
fun MyIULMSTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
