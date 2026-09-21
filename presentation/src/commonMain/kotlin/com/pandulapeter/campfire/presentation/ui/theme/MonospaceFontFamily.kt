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

/**
 * The font tablature and the editor's source text are set in, since their columns only line up in one whose
 * characters are all the same width.
 *
 * Provided by [CampfireTheme] rather than asked for where it is used, so that a platform that has to fetch the font
 * starts doing so as the app starts instead of when the first tab is opened.
 */
internal val LocalMonospaceFontFamily = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * The system's own monospaced font wherever there is one to ask for. The web has none: Compose draws there with its
 * own copy of Skia, which cannot reach the fonts the browser has, and resolves [FontFamily.Monospace] to the same
 * proportional font it uses for everything else - so that build bundles a font of its own.
 */
@Composable
internal expect fun monospaceFontFamily(): FontFamily
