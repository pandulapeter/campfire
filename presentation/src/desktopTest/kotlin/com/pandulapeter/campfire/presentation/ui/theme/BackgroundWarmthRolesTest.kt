/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
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
            val scale = 1 - (1 - MIN_LIGHT_LUMINANCE_SCALE.toFloat()) * level / UserPreferences.MAX_BACKGROUND_WARMTH
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
