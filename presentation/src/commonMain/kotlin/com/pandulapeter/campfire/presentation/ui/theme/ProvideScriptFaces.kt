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
import kotlinx.coroutines.flow.Flow

/**
 * Registers the faces of [scriptFaces] as fallbacks of the text engine [content] lays its text out with, so that a
 * Hebrew title is drawn in any language of the interface, Inter and JetBrains Mono alike falling back to the face of
 * its script (the editor's Hebrew is therefore proportional, see the package's `CLAUDE.md`).
 *
 * Skia asks the platform's own fonts first and the registered faces only after them, so the bundled face is what the web
 * (which has no fonts of its own) and a Linux desktop without one for the script draw, and Apple's and Windows' own
 * faces are drawn elsewhere. Android's text engine is not Skia: its fallback is the system's, whose Hebrew is this same
 * Noto Sans Hebrew, and it has no way to add a face to that chain below API 29.
 *
 * @param neededScriptFaces The rows the interface's language and the library's text need, read only on the web, which
 *   fetches a face when it is first needed rather than on every launch; every other platform registers every row from
 *   its own files before the first frame.
 * @param content Told whether every face needed so far is in, which holds the launch screen as the interface's own
 *   typography does: text laid out before its face arrives is laid out again, once, when it does.
 */
@Composable
internal expect fun ProvideScriptFaces(
    neededScriptFaces: Flow<Set<ScriptFace>>,
    content: @Composable (areScriptFacesIn: Boolean) -> Unit,
)
