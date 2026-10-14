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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.jetbrains_mono_bold
import com.pandulapeter.campfire.presentation.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * The font tablature and the editor's source text are set in, since their columns only line up in one whose
 * characters are all the same width.
 *
 * Provided by [CampfireTheme] rather than asked for where it is used, so that the web, which has to fetch the font,
 * starts doing so as the app starts instead of when the first tab is opened.
 */
internal val LocalMonospaceFontFamily = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * [jetBrainsMonoFontFamily] on every platform, as soon as it can be had: at once from the app's own files, or on the
 * web once it has been fetched.
 */
@Composable
internal expect fun monospaceFontFamily(): FontFamily

/**
 * JetBrains Mono, bundled so that a tab's columns are the same width everywhere, in the variant without programming
 * ligatures, which would draw a tab's `--` and `|-` as joined shapes. The files are subset to the Latin, Greek and
 * Cyrillic scripts and the symbols a song uses (the accidentals, box drawing, punctuation), which halves them; a
 * character outside that falls back to the default font.
 *
 * Bold is bundled because the editor sets the chords and directives in it; italic is left to the text engine to slant.
 */
@Composable
internal fun jetBrainsMonoFontFamily() = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)
