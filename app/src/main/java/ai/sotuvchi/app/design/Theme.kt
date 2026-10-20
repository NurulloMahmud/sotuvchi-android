package ai.sotuvchi.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. The system follows the Intercom DESIGN.md from
 * VoltAgent/awesome-design-md: a warm cream canvas, white cards lifted by a
 * hairline border instead of a shadow, charcoal as the system primary, and a
 * single chromatic accent reserved for AI moments. Intercom uses Fin Orange
 * for that role; Sotuvchi uses its own brand blue from the app icon.
 *
 * Intercom ships no dark theme, so the dark values are derived from the same
 * rules: a warm near-black canvas, one step lighter cards, cream ink.
 */
@Immutable
data class SotuvchiColors(
    // Surfaces
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    // Ink
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val inkTertiary: Color,
    // Primary (charcoal)
    val primary: Color,
    val onPrimary: Color,
    // AI accent (brand blue, the role Fin Orange plays at Intercom)
    val accent: Color,
    val accentSoft: Color,
    val accentLight: Color,
    // Semantic palette
    val error: Color,
    val errorSoft: Color,
    val success: Color,
    val successInk: Color,
    val successSoft: Color,
    val warning: Color,
    val warningInk: Color,
    val warningSoft: Color,
    val isDark: Boolean,
)

private fun hex(value: Long) = Color(0xFF000000 or value)

val LightColors = SotuvchiColors(
    canvas = hex(0xF5F1EC), surface = hex(0xFFFFFF), surfaceAlt = hex(0xEBE7E1),
    hairline = hex(0xD3CEC6), hairlineSoft = hex(0xEBE7E1),
    ink = hex(0x111111), inkMuted = hex(0x626260), inkSubtle = hex(0x7B7B78), inkTertiary = hex(0x9C9FA5),
    primary = hex(0x111111), onPrimary = hex(0xFFFFFF),
    accent = hex(0x2563EB), accentSoft = hex(0xE8EFFD), accentLight = hex(0xBFDBFE),
    error = hex(0xC41C1C), errorSoft = hex(0xFBE9E9),
    success = hex(0x0BDF50), successInk = hex(0x087A30), successSoft = hex(0xE3F8EA),
    warning = hex(0xFE4C02), warningInk = hex(0xB33600), warningSoft = hex(0xFFEDE4),
    isDark = false,
)

val DarkColors = SotuvchiColors(
    canvas = hex(0x141312), surface = hex(0x1E1D1B), surfaceAlt = hex(0x2A2927),
    hairline = hex(0x3A3835), hairlineSoft = hex(0x2A2927),
    ink = hex(0xF5F1EC), inkMuted = hex(0xB3AEA7), inkSubtle = hex(0x96928C), inkTertiary = hex(0x6F6C68),
    primary = hex(0xF5F1EC), onPrimary = hex(0x111111),
    accent = hex(0x5B8DF6), accentSoft = hex(0x1C2A47), accentLight = hex(0x2D4372),
    error = hex(0xF06A6A), errorSoft = hex(0x3A1D1D),
    success = hex(0x0BDF50), successInk = hex(0x4BE07F), successSoft = hex(0x16301F),
    warning = hex(0xFF7A3D), warningInk = hex(0xFF9461), warningSoft = hex(0x3A2116),
    isDark = true,
)

/** Spacing (8px base), radius (never pill-rounded CTAs, never square) and sizes. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
}

/** Minimum touch target height. */
val ControlHeight = 48.dp

/**
 * Saans is proprietary; the design doc allows a close geometric substitute.
 * The system font at weight medium carries display, regular carries body,
 * with negative tracking scaled to size as the doc prescribes.
 */
@Immutable
data class SotuvchiTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val cardTitle: TextStyle,
    val subhead: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
    val eyebrow: TextStyle,
    val mono: TextStyle,
)

private fun style(size: Int, weight: FontWeight, tracking: Double = 0.0, line: Double = 1.3) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeight = (size * line).sp,
)

val DefaultTypography = SotuvchiTypography(
    display = style(34, FontWeight.Medium, -0.8, 1.2),
    headline = style(28, FontWeight.Medium, -0.5, 1.2),
    cardTitle = style(22, FontWeight.Medium, -0.3, 1.25),
    subhead = style(20, FontWeight.Normal, -0.2, 1.3),
    bodyLarge = style(17, FontWeight.Normal, -0.1, 1.4),
    body = style(16, FontWeight.Normal, 0.0, 1.4),
    bodySmall = style(14, FontWeight.Normal, 0.0, 1.4),
    caption = style(12, FontWeight.Normal, 0.0, 1.35),
    button = style(14, FontWeight.Medium, 0.0, 1.3),
    eyebrow = style(14, FontWeight.Medium, 0.0, 1.3),
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
)

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalTypography = staticCompositionLocalOf { DefaultTypography }

/** Shorthand for the active tokens: `Theme.colors.ink`, `Theme.type.body`. */
object Theme {
    val colors: SotuvchiColors
        @Composable get() = LocalColors.current
    val type: SotuvchiTypography
        @Composable get() = LocalTypography.current
}

/** Material 3 is only the base; every color and shape comes from the tokens above. */
@Composable
fun SotuvchiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.inkMuted,
            outline = colors.hairline, error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.inkMuted,
            outline = colors.hairline, error = colors.error,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalTypography provides DefaultTypography) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
