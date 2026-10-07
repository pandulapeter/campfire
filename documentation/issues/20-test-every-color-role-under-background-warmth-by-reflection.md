# Check every role of the color scheme under background warmth by reflection, so a role a Material update adds cannot escape the light half's uniform dimming unnoticed

**Challenged:** sound

**Kind:** test  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/theme/BackgroundWarmthRolesTest.kt` (new), `presentation/CLAUDE.md` (the `ui/theme/` entry, one clause)

## Problem

The light half's contrast guarantee is that every role's `Y + 0.05` is multiplied by one factor, which is only true for
the roles `mapColors` names by hand —
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/BackgroundWarmth.kt:73-122`:

```kotlin
private fun ColorScheme.mapColors(transform: (Color) -> Color) = copy(
    primary = transform(primary),
    onPrimary = transform(onPrimary),
    …
    onTertiaryFixedVariant = transform(onTertiaryFixedVariant),
)
```

`ColorScheme.copy` defaults every parameter to the current value, so a role that a future
`org.jetbrains.compose.material3` adds to the constructor compiles here and keeps its undimmed value, silently breaking
the ratio between it and every dimmed role. The tests cannot notice: `BackgroundWarmthTest` lists its roles by hand too
(`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/theme/BackgroundWarmthTest.kt:89-100`):

```kotlin
private fun ColorScheme.neutrals() = listOf(
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, inverseSurface, inverseOnSurface,
    …
)

private fun ColorScheme.withSecondAccent(secondAccent: Color) = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryContainer,
    …
) + neutrals()
```

and that list already leaves out `inversePrimary`, `surfaceTint`, `scrim` and the twelve `*Fixed*` roles, so even today
those are covered by nothing.

**Verified at 1b26dfb94: no role is missing yet.** The pinned version is `jetbrains-compose-material3 = "1.12.0-alpha03"`
(`gradle/libs.versions.toml:27`); its `commonMain/androidx/compose/material3/ColorScheme.kt` (sources jar in the Gradle
cache) has 48 `Color` constructor properties (lines 149-196) and a 48-parameter `copy` (lines 330-378), and `mapColors`
names exactly those 48. Reflection on the desktop class finds the same 48 getters. So this is a guard, not a bug. (The
library's own `ColorScheme.toString()` prints `onPrimaryFixed=$onPrimaryContainer`, so it is no reliable way to
enumerate the roles either.)

## Fix

No production change: `commonMain` has no reflection, so `mapColors` has to stay a hand list. Add a `desktopTest` that
enumerates the roles with Java reflection — on the JVM every `Color` property of `ColorScheme` is a public no-argument
getter returning `long` (the value class, mangled as `getPrimary-0d7_KjU`) — and asserts the two invariants for each:

- light half, every level: `Y + 0.05` of the warmed role is `scale × (Y + 0.05)` of the original, where
  `scale = 1 - (1 - 0.86) × level / MAX_BACKGROUND_WARMTH`, except that a role the product would take below black is
  held at black (`dimmed`'s `coerceAtLeast(0.0)`). At 1b26dfb94 that clamp applies to `scrim` (pure black) in all ten
  palettes and nothing else; the expectation below computes it rather than naming `scrim`, so the test needs no list.
- dark half: every role keeps its luminance (the sepia moves only hue and chroma; the accents are untouched).

New file `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/theme/BackgroundWarmthRolesTest.kt`
(with the MPL header copied from a sibling):

```kotlin
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every role of the scheme, found by reflection rather than listed, so that a role a Material update adds to
 * [ColorScheme] fails here until `mapColors` in `BackgroundWarmth.kt` dims it too: `copy` defaults every parameter, so a
 * forgotten role compiles and keeps its undimmed value, and the contrast of everything drawn on it moves.
 */
class BackgroundWarmthRolesTest {

    private val palettes = listOf(
        CampfireColorScheme,
        OrangeColorScheme,
        GrayColorScheme,
        MaterialColorSchemes.Red,
        MaterialColorSchemes.Yellow,
        MaterialColorSchemes.Green,
        MaterialColorSchemes.Teal,
        MaterialColorSchemes.Blue,
        MaterialColorSchemes.Purple,
        MaterialColorSchemes.Pink,
    )

    /** On the JVM a [Color] property is a getter returning the value class's `long`, under a mangled name. */
    private val roleGetters = ColorScheme::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }

    private fun ColorScheme.roles() = roleGetters.associate {
        it.name.removePrefix("get").substringBefore('-') to Color((it.invoke(this) as Long).toULong())
    }

    @Test
    fun theRolesAreFound() = assertTrue(roleGetters.size >= 48, "Only ${roleGetters.size} roles were found")

    @Test
    fun everyRoleOfTheLightHalfIsDimmedByTheSameFactor() = palettes.forEach { palette ->
        (1..UserPreferences.MAX_BACKGROUND_WARMTH).forEach { level ->
            val scale = 1 - 0.14f * level / UserPreferences.MAX_BACKGROUND_WARMTH
            val warmed = palette.withBackgroundWarmth(level).light.roles()
            palette.light.roles().forEach { (role, before) ->
                // A role the dimming would take below black is held at black, which only the scrim is.
                val expected = (scale * (before.luminance() + 0.05f) - 0.05f).coerceAtLeast(0f) + 0.05f
                assertEquals(expected, warmed.getValue(role).luminance() + 0.05f, 0.02f * expected, "$role at level $level")
            }
        }
    }

    @Test
    fun everyRoleOfTheDarkHalfKeepsItsLuminance() = palettes.forEach { palette ->
        val warmed = palette.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH).dark.roles()
        palette.dark.roles().forEach { (role, before) ->
            // Eight bits a channel is all a color is stored in, which is all the difference allowed.
            assertTrue(abs(before.luminance() - warmed.getValue(role).luminance()) < 0.004f, role)
        }
    }
}
```

`0.14f` is `1 - MIN_LIGHT_LUMINANCE_SCALE`, which is `private` in `BackgroundWarmth.kt`; either make that constant
`internal` and use it here, or keep the literal with a one-line `//` pointing at it — the implementer's choice, the
former preferred so the two cannot drift. The 2% tolerance is the one `everyLevelKeepsEveryContrastOfTheScheme` already
uses for eight-bit rounding of near-blacks.

`BackgroundWarmthTest`'s hand lists can stay: they serve its pairwise-contrast and dark-neutral tests, and the new test
covers the completeness they cannot.

In `presentation/CLAUDE.md`'s `ui/theme/` entry, after "(`BackgroundWarmth.kt`, tested)", note that the dimming is
checked for every role `ColorScheme` has, found by reflection in `desktopTest`, so a role a Material update adds fails
the build until `mapColors` names it.

## Tests

The test above. Verified with an untracked probe at 1b26dfb94 (then deleted): all three tests pass with
`./gradlew :presentation:desktopTest --tests '*BackgroundWarmthRolesTest*'`, finding 48 roles, with `Scrim` the only
clamped role in every palette. A fourth probe test that put the original `onTertiaryFixedVariant` back into the warmed
light scheme with `copy` (simulating a role `mapColors` forgot) confirmed that role then misses the expected
`Y + 0.05` by well over the 2% tolerance, i.e. the light-half test fails for a missed role.

## Manual check

None.
