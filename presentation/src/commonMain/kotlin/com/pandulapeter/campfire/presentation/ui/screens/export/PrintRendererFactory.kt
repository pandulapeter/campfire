/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.LocalPlatformContext
import com.pandulapeter.campfire.presentation.ui.pictures.LocalSongImageLoader
import com.pandulapeter.campfire.presentation.ui.print.PrintPictures
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.ui.theme.ScriptFace

/**
 * What [ExportScreen] makes its [PrintRenderer]s with, a new one for each layout and file, in the faces the viewer sets a
 * song in and [scriptFontFamilies], with the songs' pictures from the app's library.
 */
@Composable
internal fun rememberPrintRendererFactory(scriptFontFamilies: List<Pair<ScriptFace, FontFamily>>): () -> PrintRenderer {
    val fontResolver = LocalFontFamilyResolver.current
    val monospaceFontFamily = LocalMonospaceFontFamily.current
    // The face the viewer sets lyrics in, Inter rather than FontFamily.Default.
    val textFontFamily = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    // One for every renderer, so that what the preview decoded is drawn by whichever of them draws the preview.
    val imageLoader = LocalSongImageLoader.current
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val pictures = remember(imageLoader, context, scope) { PrintPictures(imageLoader, context, scope) }
    return remember(fontResolver, monospaceFontFamily, textFontFamily, scriptFontFamilies, pictures) {
        {
            PrintRenderer(
                measurer = TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256),
                monospaceFontFamily = monospaceFontFamily,
                textFontFamily = textFontFamily,
                scriptFontFamilies = scriptFontFamilies,
                pictures = pictures,
            )
        }
    }
}
