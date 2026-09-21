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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.jetbrains_mono_bold
import com.pandulapeter.campfire.presentation.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/**
 * JetBrains Mono, in the variant without programming ligatures, which would draw a tab's `--` and `|-` as joined
 * shapes. The files are subset to the Latin, Greek and Cyrillic scripts and the symbols a song uses (the
 * accidentals, box drawing, punctuation), which halves them; a character outside that falls back to the default font.
 *
 * Bold is bundled because the editor sets the chords and directives in it; italic is left to Skia to slant.
 * Until both weights have arrived the family is the default one, which is what the first frames of a cold start show
 * rather than a blank where the text should be.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun monospaceFontFamily(): FontFamily {
    val regular = preloadFont(Res.font.jetbrains_mono_regular, FontWeight.Normal).value
    val bold = preloadFont(Res.font.jetbrains_mono_bold, FontWeight.Bold).value
    return remember(regular, bold) {
        if (regular == null || bold == null) FontFamily.Monospace else FontFamily(regular, bold)
    }
}
