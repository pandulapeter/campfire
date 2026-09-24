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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.inter_bold
import com.pandulapeter.campfire.presentation.resources.inter_medium
import com.pandulapeter.campfire.presentation.resources.inter_regular
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/**
 * Inter, the closest to the desktop systems' own fonts that may be shipped (SF Pro's license keeps it on Apple's
 * platforms), in the three weights Material's type scale and the app use: Regular, Medium and Bold. The files are
 * subset to the Latin, Greek and Cyrillic scripts, punctuation, arrows and the symbols a song uses, and keep only the
 * default layout features, which takes them to a sixth of their size; a character outside that falls back to the
 * Roboto Skia carries. Italic is left to Skia to slant.
 *
 * The wait is bounded, see [FONT_DEADLINE_MILLIS]: Compose resources never reports a fetch that failed, and a font
 * that is not coming must not keep the app covered - it opens in Material's own typography instead.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun interfaceTypography(): Typography? {
    val regular = preloadFont(Res.font.inter_regular, FontWeight.Normal).value
    val medium = preloadFont(Res.font.inter_medium, FontWeight.Medium).value
    val bold = preloadFont(Res.font.inter_bold, FontWeight.Bold).value
    val hasWaitedLongEnough by produceState(initialValue = false) {
        delay(FONT_DEADLINE_MILLIS)
        value = true
    }
    return remember(regular, medium, bold, hasWaitedLongEnough) {
        when {
            regular != null && medium != null && bold != null -> Typography(fontFamily = FontFamily(regular, medium, bold))
            hasWaitedLongEnough -> Typography()
            else -> null
        }
    }
}

/**
 * Counted from the first composition, by which time the preloads the page started next to the binaries have usually
 * answered, so this is only ever reached by a request that is not coming back.
 */
private const val FONT_DEADLINE_MILLIS = 5_000L
