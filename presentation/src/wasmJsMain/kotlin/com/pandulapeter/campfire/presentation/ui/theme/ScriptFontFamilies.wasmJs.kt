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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/**
 * Preloaded rather than asked for with Compose's own `Font`, which answers with Skia's Roboto until the file is in, so
 * that nothing is laid out before its face is. A face [ProvideScriptFaces] already fetched answers at once.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun scriptFontFamilies(): List<Pair<ScriptFace, FontFamily>>? {
    val fonts = scriptFaces.map { face -> key(face) { preloadFont(face.font).value } }
    val hasWaitedLongEnough by produceState(initialValue = false) {
        delay(SCRIPT_FONT_DEADLINE_MILLIS)
        value = true
    }
    return remember(fonts, hasWaitedLongEnough) {
        if (fonts.any { it == null } && !hasWaitedLongEnough) {
            null
        } else {
            scriptFaces.zip(fonts).mapNotNull { (face, font) -> font?.let { face to FontFamily(it) } }
        }
    }
}

/** As long as [ProvideScriptFaces] waits for a face. */
private const val SCRIPT_FONT_DEADLINE_MILLIS = 5_000L
