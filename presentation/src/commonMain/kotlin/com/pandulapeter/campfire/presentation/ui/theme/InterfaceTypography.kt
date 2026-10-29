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

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.inter_bold
import com.pandulapeter.campfire.presentation.resources.inter_medium
import com.pandulapeter.campfire.presentation.resources.inter_regular
import org.jetbrains.compose.resources.Font

/**
 * The typography the interface is set in, Material's type scale in [interFontFamily], or null for as long as the font
 * is still on its way.
 *
 * Every platform but the web reads the font from the app's own files before the first frame; the web fetches it, and
 * [CampfireTheme] does not report itself settled until it has arrived, which holds the launch screen over the app
 * rather than letting every line of text in it be laid out again once the font lands.
 */
@Composable
internal expect fun interfaceTypography(): Typography?

/**
 * Inter, which the whole app is set in on every platform, so that it looks the same everywhere and a song breaks its
 * lines and pages in the same places on a phone, a computer and paper. Bundled in the three weights Material's type
 * scale and the app use: Regular, Medium and Bold. The files are subset to the Latin, Greek and Cyrillic scripts,
 * punctuation and arrows, and keep only the default layout features, which takes them to a sixth of their size; a
 * character outside that falls back to the system's fonts and to the faces of [scriptFaces] (on the web, to those and
 * the Roboto Skia carries). The ♭ ♮ ♯ signs,
 * which Inter lacks, are drawn into the bundled files by `tools/fonts`, so a scale's degrees and a title's accidentals
 * are set in the same face as the rest. Italic is left to the text engine to slant.
 */
@Composable
internal fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_bold, FontWeight.Bold),
)
