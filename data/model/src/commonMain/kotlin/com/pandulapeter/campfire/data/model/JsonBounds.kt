/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model

/**
 * The one check of a JSON text from outside the app, made in a single pass before the text is parsed.
 *
 * The tree parser bounds neither the depth it recurses to nor the tree it builds, and both fail with an `Error`
 * (`StackOverflowError`, `OutOfMemoryError`) that no `catch (Exception)` stops. So the depth of brackets and the number
 * of structural tokens are counted outside strings first, and a text past either bound is never handed to the parser.
 */
object JsonBounds {

    /**
     * Whether [text] nests no deeper than [maxNesting] and holds at most [maxTokens] of `[`, `{`, `,`, and `:` outside
     * strings. Every value except the root, and every object key, follows one of those four characters, so the count
     * bounds the number of nodes the parsed tree can have, whatever its shape.
     */
    fun isWithin(text: String, maxTokens: Int, maxNesting: Int = MAX_NESTING): Boolean {
        var depth = 0
        var tokens = 0
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
                character == '[' || character == '{' -> if (++depth > maxNesting || ++tokens > maxTokens) return false
                character == ']' || character == '}' -> depth--
                character == ',' || character == ':' -> if (++tokens > maxTokens) return false
            }
        }
        return true
    }

    /**
     * The tree parser recurses once per level with no limit of its own, so a few kilobytes of `[[[[` overflow the stack.
     * Twice the 32 levels the import's readers walk, so that every document they could read whole still parses.
     */
    const val MAX_NESTING = 64

    /**
     * The tree costs 40 to 150 bytes per value, so a document small enough to pass every size limit could still build a
     * tree of hundreds of megabytes; the budget is fixed rather than scaled to the text's size because memory is what has
     * to be bounded. At this budget the worst shape measured (`{"file":"a"}` entries, 3 tokens per 2 values) is about
     * 65 MB of tree. The largest library the import's size budget admits, about 6,000 SongbookPro songs of roughly 25
     * members (50 tokens) each, is about 300,000 tokens. A refusal costs the whole document, and SongbookPro's format is
     * unpublished: members nobody has measured (its drawings, should a version write them as arrays rather than as text)
     * count too.
     */
    const val MAX_TOKENS = 600_000
}
