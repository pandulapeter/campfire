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
import kotlin.test.Test
import kotlin.test.assertEquals

class AbcMessagesTest {

    @Test
    fun `a message is placed on the tune's line past the renderer's format lines`() {
        assertEquals(
            listOf(AbcMessage(text = "Bad character", line = 2, column = 4)),
            abcMessagesOf(records = record("5", "4", "Bad character"), headerLines = 3, tune = TUNE),
        )
    }

    @Test
    fun `a message about one of the renderer's format lines is left out`() {
        assertEquals(emptyList(), abcMessagesOf(records = record("1", "0", "Unknown format"), headerLines = 3, tune = TUNE))
    }

    @Test
    fun `a message without a position is kept without one and a record without a text is dropped`() {
        assertEquals(
            listOf(AbcMessage(text = "No tune", line = null, column = null)),
            abcMessagesOf(records = record("", "", "No tune") + "${RECORD_SEPARATOR}7\u00024", headerLines = 0, tune = TUNE),
        )
    }

    @Test
    fun `a message given twice is reported once`() {
        assertEquals(
            listOf(AbcMessage(text = "Bad bar", line = 1, column = 2)),
            abcMessagesOf(records = record("1", "2", "Bad bar") + record("1", "2", "Bad bar"), headerLines = 0, tune = TUNE),
        )
    }

    @Test
    fun `an unknown decoration is placed on every place it is written in either spelling`() {
        val tune = "X:1\nK:C\n!wobble!C D +wobble+E !wobble!F|\n!trill!G|\n"
        val text = "Unknown decoration 'wobble'"
        assertEquals(
            listOf(2 to 0, 2 to 22, 2 to 12).map { (line, column) -> AbcMessage(text = text, line = line, column = column) },
            abcMessagesOf(records = record("", "", text), headerLines = 0, tune = tune),
        )
    }

    @Test
    fun `an unknown decoration the tune does not write is reported without a position`() {
        val text = "Unknown decoration 'wobble'"
        assertEquals(
            listOf(AbcMessage(text = text, line = null, column = null)),
            abcMessagesOf(records = record("", "", text), headerLines = 0, tune = TUNE),
        )
    }

    /** One message as `campfire-abc-host.js` writes it after the systems. */
    private fun record(line: String, column: String, text: String) = "$RECORD_SEPARATOR$line\u0002$column\u0002$text"

    private companion object {
        const val TUNE = "X:1\nK:C\nCDEF|\n"
    }
}
