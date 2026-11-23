/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/**
 * Just enough JSON for the trainer's `golden.json` (objects, arrays, strings, numbers, booleans, null), so the module's
 * tests need no dependency the module itself does not have.
 */
internal class GoldenJson private constructor(
    private val text: String,
) {
    private var position = 0

    private fun value(): Any? {
        skipSpaces()
        return when (text[position]) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> numberValue()
        }
    }

    private fun objectValue(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        position++
        skipSpaces()
        if (text[position] == '}') return result.also { position++ }
        while (true) {
            skipSpaces()
            val key = stringValue()
            skipSpaces()
            check(text[position++] == ':')
            result[key] = value()
            skipSpaces()
            if (text[position++] == '}') return result
        }
    }

    private fun arrayValue(): List<Any?> {
        val result = mutableListOf<Any?>()
        position++
        skipSpaces()
        if (text[position] == ']') return result.also { position++ }
        while (true) {
            result += value()
            skipSpaces()
            if (text[position++] == ']') return result
        }
    }

    private fun stringValue(): String {
        check(text[position++] == '"')
        val result = StringBuilder()
        while (true) {
            val character = text[position++]
            when (character) {
                '"' -> return result.toString()
                '\\' -> when (val escaped = text[position++]) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    'r' -> result.append('\r')
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    'u' -> result.append(text.substring(position, position + 4).toInt(16).toChar()).also { position += 4 }
                    else -> result.append(escaped)
                }
                else -> result.append(character)
            }
        }
    }

    private fun numberValue(): Double {
        val start = position
        while (position < text.length && text[position] in "+-0123456789.eE") position++
        return text.substring(start, position).toDouble()
    }

    private fun literal(word: String, value: Any?): Any? {
        check(text.startsWith(word, position))
        position += word.length
        return value
    }

    private fun skipSpaces() {
        while (text[position].isWhitespace()) position++
    }

    companion object {
        fun parse(text: String) = GoldenJson(text).value()
    }
}
