/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Parses the JSON of a foreign document, or returns null where it is not JSON or nests deeper than [MAX_NESTING].
 *
 * The tree parser recurses once per array level with no limit of its own, so a few kilobytes of `[[[[` overflow the
 * stack, and a `StackOverflowError` is an `Error` that no `catch (Exception)` on the import path stops. The brackets
 * are counted before parsing, outside strings, so no document that reaches the parser can be deep enough for that.
 */
internal object ShallowJson {

    fun parse(text: String): JsonElement? = if (nestsTooDeep(text)) null else try {
        Json.parseToJsonElement(text)
    } catch (_: SerializationException) {
        null
    }

    private fun nestsTooDeep(text: String): Boolean {
        var depth = 0
        var isInString = false
        var isEscaped = false
        for (character in text) {
            when {
                isEscaped -> isEscaped = false
                isInString -> when (character) {
                    '\\' -> isEscaped = true
                    '"' -> isInString = false
                }
                character == '"' -> isInString = true
                character == '[' || character == '{' -> if (++depth > MAX_NESTING) return true
                character == ']' || character == '}' -> depth--
            }
        }
        return false
    }

    /** Twice the levels the readers walk, so that every document they could read whole still parses. */
    private const val MAX_NESTING = 64
}
