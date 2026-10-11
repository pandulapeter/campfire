/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pandulapeter.campfire.data.model.domain.tagKey

/**
 * The colors a tag can be given, painted inside its icon ([TagIcon]). They are fixed hues rather than roles of the
 * theme, since a tag's color is how it is found again on every device and in every palette, and the point of giving one
 * is that it reads as that color whatever the theme is. Each has a half for either brightness: on a light background a
 * saturated middle tone that the dark outline of the icon frames, and on a dark one a lighter tone of the same hue, so
 * that the color stays a color rather than sinking into the background, while the light outline still sets it apart.
 *
 * The order is around the hue circle, the neutrals last, and the colors the theme also has are named as it names them.
 * Black is a charcoal on a dark scheme rather than black itself: the inside of a mark with no color is the dark
 * background showing through, and a black fill would be the very same thing, so a black tag would read as no color.
 *
 * @param id What `UserPreferences.tagColors` stores, and what every other device reads; never changed once shipped.
 */
internal enum class TagColor(
    val id: String,
    internal val light: Color,
    internal val dark: Color,
) {
    RED("red", light = Color(0xFFE53935), dark = Color(0xFFEF7A75)),
    ORANGE("orange", light = Color(0xFFF57C00), dark = Color(0xFFFFB74D)),
    YELLOW("yellow", light = Color(0xFFFBC02D), dark = Color(0xFFFFE066)),
    LIME("lime", light = Color(0xFFAFB42B), dark = Color(0xFFDCE775)),
    GREEN("green", light = Color(0xFF43A047), dark = Color(0xFF81C995)),
    TEAL("teal", light = Color(0xFF00897B), dark = Color(0xFF4DB6AC)),
    CYAN("cyan", light = Color(0xFF00ACC1), dark = Color(0xFF4DD0E1)),
    BLUE("blue", light = Color(0xFF1E88E5), dark = Color(0xFF7BB8F5)),
    INDIGO("indigo", light = Color(0xFF3F51B5), dark = Color(0xFF9FA8DA)),
    PURPLE("purple", light = Color(0xFF8E24AA), dark = Color(0xFFCE93D8)),
    PINK("pink", light = Color(0xFFD81B60), dark = Color(0xFFF48FB1)),
    BROWN("brown", light = Color(0xFF795548), dark = Color(0xFFBCAAA4)),
    GRAY("gray", light = Color(0xFF9E9E9E), dark = Color(0xFF8E8E8E)),
    BLACK("black", light = Color(0xFF212121), dark = Color(0xFF424242)),
    ;

    /**
     * The half for the scheme on screen, told by its surface rather than by the preference, since the scheme cross fades
     * and every palette, warmed to sepia or not, keeps its light surfaces light and its dark ones dark.
     */
    @Composable
    fun color() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light
}

/**
 * The color [tag] was given in [tagColors] (`UserPreferences.tagColors`), or null for a tag left at the default and one
 * whose stored color this version does not know, which is drawn as none and kept as it is.
 */
internal fun tagColorOf(tagColors: Map<String, String>, tag: String): TagColor? =
    tagColors[tagKey(tag)]?.let { id -> TagColor.entries.firstOrNull { it.id == id } }
