package com.pandulapeter.campfire.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Tonal palettes generated from the Campfire orange (#F57C00) seed color.
 */
internal object CampfireColorSchemes {

    val light = lightColorScheme(
        primary = Color(0xFF8B5000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFB068),
        onPrimaryContainer = Color(0xFF4F2C00),
        inversePrimary = Color(0xFFFFB77C),
        secondary = Color(0xFF725A42),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDDBE),
        onSecondaryContainer = Color(0xFF5B442D),
        tertiary = Color(0xFF596339),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDDE8B3),
        onTertiaryContainer = Color(0xFF424B24),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF211A15),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF211A15),
        surfaceVariant = Color(0xFFF4DFD1),
        onSurfaceVariant = Color(0xFF52443B),
        surfaceTint = Color(0xFF8B5000),
        inverseSurface = Color(0xFF362F2A),
        inverseOnSurface = Color(0xFFFCEEE6),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF857469),
        outlineVariant = Color(0xFFD7C3B7),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceDim = Color(0xFFE5D8CF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF1E8),
        surfaceContainer = Color(0xFFFAEBE1),
        surfaceContainerHigh = Color(0xFFF4E5DB),
        surfaceContainerHighest = Color(0xFFEEE0D6)
    )

    val dark = darkColorScheme(
        primary = Color(0xFFFFB77C),
        onPrimary = Color(0xFF4A2800),
        primaryContainer = Color(0xFFDB8A2E),
        onPrimaryContainer = Color(0xFF2E1600),
        inversePrimary = Color(0xFF8B5000),
        secondary = Color(0xFFE1C1A4),
        onSecondary = Color(0xFF402C17),
        secondaryContainer = Color(0xFF52392B),
        onSecondaryContainer = Color(0xFFEFCEB0),
        tertiary = Color(0xFFC1CC99),
        onTertiary = Color(0xFF2C3410),
        tertiaryContainer = Color(0xFF424B24),
        onTertiaryContainer = Color(0xFFDDE8B3),
        background = Color(0xFF19120D),
        onBackground = Color(0xFFEFE0D7),
        surface = Color(0xFF19120D),
        onSurface = Color(0xFFEFE0D7),
        surfaceVariant = Color(0xFF52443B),
        onSurfaceVariant = Color(0xFFD7C3B7),
        surfaceTint = Color(0xFFFFB77C),
        inverseSurface = Color(0xFFEFE0D7),
        inverseOnSurface = Color(0xFF382F29),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFFA08D82),
        outlineVariant = Color(0xFF52443B),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF413732),
        surfaceDim = Color(0xFF19120D),
        surfaceContainerLowest = Color(0xFF130D08),
        surfaceContainerLow = Color(0xFF211A15),
        surfaceContainer = Color(0xFF261E19),
        surfaceContainerHigh = Color(0xFF312823),
        surfaceContainerHighest = Color(0xFF3C332D)
    )
}
