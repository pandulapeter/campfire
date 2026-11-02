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
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font

/**
 * Every row of [scriptFaces] with a family of its face alone, for the print, which sets each script's characters in it
 * ([ScriptFace.runsIn]), as soon as every face can be had: at once from the app's own files, or on the web once each
 * one has been fetched, null until then. The web stops waiting after the five seconds [ProvideScriptFaces] waits, since
 * Compose resources never reports a fetch that failed, and answers the faces that did arrive.
 */
@Composable
internal expect fun scriptFontFamilies(): List<Pair<ScriptFace, FontFamily>>?

/** [scriptFontFamilies] where Compose's own `Font` reads a face from the app's files at once: everywhere but the web. */
@Composable
internal fun bundledScriptFontFamilies() = scriptFaces.map { face -> face to FontFamily(Font(face.font)) }
