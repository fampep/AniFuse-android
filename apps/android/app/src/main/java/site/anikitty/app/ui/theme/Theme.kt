// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AniFuse canvas — strictly monochrome. Artwork supplies the colour;
 * chrome stays black, white, and grey.
 */
val KittyInk = Color(0xFF080808)
val KittyPage = Color(0xFF080808)
val KittySurface = Color(0xFF151515)
val KittySurfaceElevated = Color(0xFF1C1C1C)
val KittyGlass = Color(0xFF101010)
val KittyGlassStrong = Color(0xFF1C1C1C)
val KittyText = Color(0xFFFFFFFF)
val KittyMuted = Color(0xFFA1A1A1)
val KittyFaint = Color(0xFF707070)
val KittyDisabled = Color(0xFF505050)
val KittyOutline = Color(0xFF292929)
val KittyOutlineSoft = Color(0xFF292929)

/** Selected controls are solid white with ink text. No hue accent. */
val KittyAccent = Color(0xFFFFFFFF)
val KittyAccentGlow = Color(0xFFFFFFFF)
val KittyOnAccent = Color(0xFF080808)

val KittyNavPillSurface = Color(0xFF101010)
val KittyNavPillBorder = Color(0xFF292929)
val KittyNavPillSelected = KittyAccent
val KittyNavPillSelectedContent = KittyOnAccent
val KittyNavPillContent = Color(0xFF707070)

val KittyAmbientWash = Color.Transparent

/** Flat #080808 page — AMOLED / true-black mode. */
val KittyPageFlatBrush: Brush
    get() = Brush.verticalGradient(
        colors = listOf(KittyPage, KittyPage),
    )

/**
 * Soft-black atmosphere. A one-step lift at the top, still monochrome.
 * Artwork stays the only source of colour.
 */
val KittyPageGlassBrush: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF141414),
            Color(0xFF0C0C0C),
            Color(0xFF080808),
            Color(0xFF060606),
        ),
    )

fun kittyPageBrush(glass: Boolean): Brush =
    if (glass) KittyPageGlassBrush else KittyPageFlatBrush

object PlayerChrome {
    val Paper = Color(0xFFF5F7FA)
    val Ink = Color(0xFF000000)
    val Graphite = Paper.copy(alpha = 0.72f)
    val Faint = Paper.copy(alpha = 0.42f)
    val Smoke = Color(0xCC000000)
    val Sheet = Color(0xF2151515)
    val SheetStroke = Color(0xFF292929)
    val RowSelected = Color.White.copy(alpha = 0.12f)
    val Disc = Color(0xFF1A1A1A)
    val DiscStroke = Color.White.copy(alpha = 0.16f)
    val DiscLarge = Color(0xFF222222)
    val Accent = KittyAccent
    val Progress = KittyAccent
    val ProgressGlow = Color.White.copy(alpha = 0.35f)
    val TrackGlass = Color(0xFF2C2C2C)
    val MarkerIntro = Color.White.copy(alpha = 0.55f)
    val MarkerCredits = Paper.copy(alpha = 0.35f)
    val MarkerRecap = Paper.copy(alpha = 0.45f)
}

@Immutable
data class KittyPalette(
    val page: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val surface: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val dark: Boolean,
)

val KittyDarkPalette = KittyPalette(
    page = KittyPage,
    text = KittyText,
    muted = KittyMuted,
    faint = KittyFaint,
    surface = Color(0xFF151515),
    hairline = KittyOutlineSoft,
    accent = KittyAccent,
    onAccent = KittyOnAccent,
    dark = true,
)

val KittyLightPalette = KittyPalette(
    page = Color(0xFFF3F3F1),
    text = Color(0xFF141414),
    muted = Color(0xFF5E5E5E),
    faint = Color(0xFF8A8A8A),
    surface = Color(0xFFFFFFFF),
    hairline = Color(0x14141414),
    accent = Color(0xFF141414),
    onAccent = Color(0xFFFFFFFF),
    dark = false,
)

val LocalKittyPalette = staticCompositionLocalOf { KittyDarkPalette }

private val DarkColors = darkColorScheme(
    primary = KittyAccent,
    onPrimary = KittyOnAccent,
    secondary = KittySurfaceElevated,
    onSecondary = KittyText,
    background = KittyPage,
    onBackground = KittyText,
    surface = KittySurface,
    onSurface = KittyText,
    onSurfaceVariant = KittyMuted,
    outline = KittyOutline,
    error = Color(0xFFE8E8E8),
)

private val KittyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun AniKittyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LookState.ensure(context)
    // AniFuse is a black-and-white product. System light mode does not
    // introduce a second palette or a coloured accent.
    val glassLook = LookState.look != "amoled"
    val palette = KittyDarkPalette.copy(
        accent = KittyAccent,
        onAccent = KittyOnAccent,
        surface = if (glassLook) KittySurface else Color(0xFF101010),
    )
    CompositionLocalProvider(LocalKittyPalette provides palette) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = KittyTypography,
            content = content,
        )
    }
}
