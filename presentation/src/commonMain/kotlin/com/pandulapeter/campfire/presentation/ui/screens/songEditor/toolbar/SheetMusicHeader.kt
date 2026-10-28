/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import com.pandulapeter.campfire.chordpro.ChordProTime

/**
 * The lines an empty Sheet music block starts with: `X:1`, which abc2svg draws no tune without, the song's meter where
 * its [time] is one `{time}` accepts, `L:1/8` (the most common unit, written so the field is there to change), and its
 * [key], so that someone who does not know ABC sees where each goes.
 *
 * The key is rebuilt rather than copied, in standard letters, as `:chordpro` reads a `{key}` (`SongParts`): a root
 * `A`–`G` in either case with its `#` or `b`, then `m` for minor or a mode's three letters, else nothing. abc2svg throws
 * on a German `K:H`, reads a key written in words (`K:A-moll`) or with a Central European suffix (`K:Fis`) as its
 * letter's major, and has no tune at all once it throws; [key] arrives in standard notation from the editor's summary,
 * but a key spelled out in words keeps its words there. A Central European `Fis` therefore starts on `F`, one key
 * signature wrong but never a tune that fails to draw. No key, or none read, is `K:C`. The key is the one the chords
 * are named in, so a capo chart whose `{key}` names the sounding key starts a capo's distance off, for the writer to
 * correct with the rest of the tune.
 */
internal fun sheetMusicHeader(key: String?, time: String?): List<String> = listOfNotNull(
    "X:1",
    meterOf(time)?.let { "M:$it" },
    "L:1/8",
    "K:${abcKeyOf(key)}",
)

private fun meterOf(time: String?): String? {
    val (beats, unit) = ChordProTime.parse(time) ?: return null
    return when (time?.trim()) {
        "C" -> "C"
        "C|", "¢" -> "C|"
        else -> "$beats/$unit"
    }
}

private fun abcKeyOf(key: String?): String {
    val trimmed = key?.trim().orEmpty()
    val letter = trimmed.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'G' } ?: return "C"
    val accidental = trimmed.getOrNull(1)?.let { accidentalLetters[it] }
    val root = letter + (accidental?.toString() ?: "")
    val word = trimmed.drop(if (accidental == null) 1 else 2).trimStart { it.isWhitespace() || it == '-' }.lowercase()
    val mode = modes.firstOrNull { word.startsWith(it) }
    return when {
        mode != null -> "$root $mode"
        word.startsWith('m') && !word.startsWith("ma") -> "${root}m"
        else -> root
    }
}

private val accidentalLetters = mapOf('#' to '#', '♯' to '#', 'b' to 'b', '♭' to 'b')

/** The modes abc2svg reads by their first three letters, as it writes them. */
private val modes = listOf("dor", "phr", "lyd", "mix", "aeo", "loc", "ion")
