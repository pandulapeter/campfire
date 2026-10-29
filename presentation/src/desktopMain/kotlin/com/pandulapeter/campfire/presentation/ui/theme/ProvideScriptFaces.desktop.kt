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
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.Font

@Composable
internal actual fun ProvideScriptFaces(
    neededScriptFaces: Flow<Set<ScriptFace>>,
    content: @Composable (areScriptFacesIn: Boolean) -> Unit,
) {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val fonts = scriptFaces.map { Font(it.font) }
    // Resolving a font is what registers it with the resolver's Skia font collection, whose fallback is every face
    // registered with it. Done while composing rather than in an effect, so it is in before the first text is measured.
    remember(fontFamilyResolver, fonts) { fonts.forEach { fontFamilyResolver.resolve(FontFamily(it)) } }
    content(true)
}
