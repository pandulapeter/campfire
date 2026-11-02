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
 * A face bundled for a script the app's own fonts lack, a row of [scriptFaces]. It is never asked for by name, except by
 * the print, which sets the script's characters in it ([runsIn]) so that a PDF is the same on every platform: it is
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
        forEachCodePoint(text) { _, _, codePoint -> if (ranges.any { codePoint in it }) return true }
        return false
    }

    /** The runs of [text]'s characters of the script, as ranges of its indices, a surrogate pair always whole. */
    fun runsIn(text: String): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var runStart = -1
        forEachCodePoint(text) { start, end, codePoint ->
            val isInScript = ranges.any { codePoint in it }
            if (isInScript && runStart < 0) runStart = start
            if (!isInScript && runStart >= 0) {
                runs.add(runStart until start)
                runStart = -1
            }
            if (isInScript && end == text.length) runs.add(runStart until end)
        }
        return runs
    }

    override fun toString() = script
}

/** Calls [action] with every code point of [text] and the indices it starts at and ends before. */
private inline fun forEachCodePoint(text: String, action: (start: Int, end: Int, codePoint: Int) -> Unit) {
    var index = 0
    while (index < text.length) {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
            action(index, index + 2, 0x10000 + ((high.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00))
            index += 2
        } else {
            action(index, index + 1, high.code)
            index++
        }
    }
}
