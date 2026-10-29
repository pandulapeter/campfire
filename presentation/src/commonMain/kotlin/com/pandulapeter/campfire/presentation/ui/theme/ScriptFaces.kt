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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.noto_sans_hebrew_regular

/**
 * The faces bundled for the scripts Inter and JetBrains Mono lack, one row per script. No code asks for a script by
 * name: a face is used because its row covers a character, and `tools/fonts/script_faces.py` reads its ranges from
 * here, so another script is a row, a run of that script, and the face's license credited. The layout of each row
 * (one `ScriptFace(` per row, its ranges as hexadecimal `..` ranges, one `Res.font`) is what that script parses.
 */
internal val scriptFaces = listOf(
    ScriptFace(
        script = "Hebrew",
        ranges = listOf(0x0590..0x05FF, 0xFB1D..0xFB4F),
        languages = setOf("he", "yi"),
        font = Res.font.noto_sans_hebrew_regular,
    ),
)

/**
 * The rows of [scriptFaces] needed to draw a library and an interface: those of the scripts [languages] are written in
 * (the interface's, and those the songs declare), and those of any script one of [texts] holds a character of.
 */
internal fun scriptFacesNeededBy(languages: Collection<String>, texts: Sequence<String>): Set<ScriptFace> {
    val needed = scriptFaces.filterTo(mutableSetOf()) { face -> languages.any { it in face.languages } }
    val rest = scriptFaces - needed
    if (rest.isNotEmpty()) texts.forEach { text -> rest.filterTo(needed) { it.isNeededFor(text) } }
    return needed
}
