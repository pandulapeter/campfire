/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

/**
 * What a directive's value may hold, decided here once for every writer of one and every form that edits one, so that
 * what a form compares a draft by is what the file will hold.
 */
public object ChordProDirectiveValues {

    /**
     * [value] as it may be typed into one directive: without braces, which would end it early or open another one, or
     * line breaks, which would leave the rest of the value in the song as lyrics. `ChordProMetadataFields.set` does not
     * apply it (it only turns line breaks into spaces), so a caller writing a typed value runs it first.
     */
    public fun typed(value: String): String = value.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }

    /**
     * A link's or an attachment's name as its `{meta: …}` line holds it: no braces, every run of whitespace
     * (`Char.isWhitespace`, the same on every platform) one plain space, trimmed; null where nothing is left. Read back,
     * the name is what this gives, which it leaves as it is.
     */
    public fun name(name: String?): String? {
        if (name == null) return null
        val cleaned = StringBuilder(name.length)
        var isSpacePending = false
        for (char in name) {
            when {
                char == '{' || char == '}' -> Unit
                char.isWhitespace() -> isSpacePending = cleaned.isNotEmpty()
                else -> {
                    if (isSpacePending) cleaned.append(' ')
                    isSpacePending = false
                    cleaned.append(char)
                }
            }
        }
        return cleaned.toString().takeIf { it.isNotEmpty() }
    }
}
