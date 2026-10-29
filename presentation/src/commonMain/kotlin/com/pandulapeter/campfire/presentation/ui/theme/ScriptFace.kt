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

import org.jetbrains.compose.resources.FontResource

/**
 * A face bundled for a script the app's own fonts lack, a row of [scriptFaces]. It is never asked for by name: it is
 * registered as a fallback of the text engine (see [ProvideScriptFaces]), so a character of [ranges] in any text, set in
 * Inter or in JetBrains Mono, in any language of the interface, is drawn from it wherever the platform has no face of
 * its own for that script.
 *
 * Only the Regular weight is bundled. Skia, which draws the text everywhere but on Android, takes a fallback for a
 * character by asking every registered face in turn whether it has the character, without regard to the weight the
 * text asked for, so the first registered face answers for a script whatever its weight, and a bolder one would never
 * be drawn.
 *
 * @param script What the row is for, as the Unicode script is named, so that a log or a test names it.
 * @param ranges The code points the face holds, the only ones `tools/fonts/script_faces.py` keeps when it subsets the
 *   upstream file, so a range is added here and the script run again.
 * @param languages The languages written in the script, by the lowercase codes `LanguageCodes` folds to: an interface
 *   in one of them, or a song declaring one, needs the face before any of its text has been read.
 * @param font The subset file, under `composeResources/font`.
 */
internal class ScriptFace(
    val script: String,
    val ranges: List<IntRange>,
    val languages: Set<String>,
    val font: FontResource,
) {

    /** Whether [text] holds a character of the script, which is what makes the face needed on the web. */
    fun isNeededFor(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val high = text[index]
            val codePoint = if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                index++
                0x10000 + ((high.code - 0xD800) shl 10) + (text[index].code - 0xDC00)
            } else {
                high.code
            }
            if (ranges.any { codePoint in it }) return true
            index++
        }
        return false
    }

    override fun toString() = script
}
