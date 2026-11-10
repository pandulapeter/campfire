/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import com.pandulapeter.campfire.abc.api.AbcMessage

/**
 * Where [tune] gives a note, a rest, a chord, or a grace note a length divided by zero (`C/0`, `z//0`, `[CE]/00`): abc2svg
 * takes the zero for a divisor, and the infinite length it then lays out never ends, which on the web (the page's only
 * thread) and on iOS (whose engine nothing can interrupt) would hang the app. A tune with one is not rendered, and each
 * is reported where it stands.
 *
 * Only the music is read: a field line (`L:1/0` is refused by abc2svg itself), a comment, a `%%begin…` block, and what
 * stands inside quotes, decorations, and inline fields are not durations.
 */
internal fun zeroDurationsIn(tune: String): List<AbcMessage> = buildList {
    var block: String? = null
    tune.split('\n').forEachIndexed { lineIndex, line ->
        val openBlock = block
        if (openBlock != null) {
            if (line.trimStart().startsWith("%%end$openBlock")) block = null
            return@forEachIndexed
        }
        val trimmed = line.trimStart()
        if (trimmed.startsWith("%%begin")) {
            block = trimmed.removePrefix("%%begin").takeWhile { it.isLetterOrDigit() }
            return@forEachIndexed
        }
        if (trimmed.startsWith('%') || FIELD_LINE.matches(trimmed)) return@forEachIndexed
        zeroDivisorColumns(line).forEach { column -> add(AbcMessage(text = ZERO_DURATION_MESSAGE, line = lineIndex, column = column)) }
    }
}

/** The columns of the slashes of [line] that start a length divided by zero, outside what [zeroDurationsIn] skips. */
private fun zeroDivisorColumns(line: String): List<Int> = buildList {
    var index = 0
    while (index < line.length) {
        when (val character = line[index]) {
            '%' -> return@buildList
            '"', '!' -> index = line.indexOf(character, index + 1).let { if (it < 0) line.length else it + 1 }
            '[' -> index = if (index + 2 < line.length && line[index + 1].isLetter() && line[index + 2] == ':') {
                line.indexOf(']', index + 1).let { if (it < 0) line.length else it + 1 }
            } else {
                index + 1
            }
            '/' -> {
                val start = index
                while (index < line.length && line[index] == '/') index++
                val digitsStart = index
                while (index < line.length && line[index].isDigit()) index++
                if (index > digitsStart && (digitsStart until index).all { line[it] == '0' }) add(start)
            }
            else -> index++
        }
    }
}

private val FIELD_LINE = Regex("""^[A-Za-z+]:.*""")
internal const val ZERO_DURATION_MESSAGE = "A note length divided by zero"
