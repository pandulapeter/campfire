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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/**
 * Fetches a face the first time [neededScriptFaces] names it, and keeps it for the rest of the session: a launch whose
 * interface and library need no other script fetches nothing beyond Inter and JetBrains Mono.
 *
 * A face that arrives is registered with a new resolver rather than with the one already in use: Compose keeps the
 * layout of every text it has measured and knows nothing of a fallback added behind it, so a title measured while its
 * face was still on its way would keep its empty boxes. A new resolver is a new cache, and every text is measured
 * again in it, once per face that arrives - under the launch screen when the interface or the library needs it from
 * the start, and otherwise when the first song in a new script lands in the library.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun ProvideScriptFaces(
    neededScriptFaces: Flow<Set<ScriptFace>>,
    content: @Composable (areScriptFacesIn: Boolean) -> Unit,
) {
    val needed by produceState<Set<ScriptFace>?>(initialValue = null, neededScriptFaces) {
        neededScriptFaces.collect { value = value.orEmpty() + it }
    }
    val fonts = needed.orEmpty().map { face -> key(face) { preloadFont(face.font).value } }
    val hasWaitedLongEnough by produceState(initialValue = false) {
        delay(SCRIPT_FACE_DEADLINE_MILLIS)
        value = true
    }
    val arrived = fonts.filterNotNull()
    val platformResolver = LocalFontFamilyResolver.current
    val fontFamilyResolver = remember(platformResolver, arrived) {
        if (arrived.isEmpty()) {
            platformResolver
        } else {
            createFontFamilyResolver().also { resolver -> arrived.forEach { resolver.resolve(FontFamily(it)) } }
        }
    }
    CompositionLocalProvider(LocalFontFamilyResolver provides fontFamilyResolver) {
        content(needed != null && (arrived.size == fonts.size || hasWaitedLongEnough))
    }
}

/**
 * How long the launch screen waits for a face, as it does for Inter: Compose resources never reports a fetch that
 * failed, and the app opens without the face rather than stay covered.
 */
private const val SCRIPT_FACE_DEADLINE_MILLIS = 5_000L
