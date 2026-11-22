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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordProDirectiveValuesTest {

    private val untidy = "  Live at\t{the}  Hall \n"

    @Test
    fun `a typed value loses its braces and line breaks and keeps everything else`() {
        assertEquals("Live\tat the Hall  x", ChordProDirectiveValues.typed("Live\tat {the}\r\n Hall  x"))
    }

    @Test
    fun `a name folds every run of whitespace into one space and drops braces`() {
        assertEquals("Live at the Hall", ChordProDirectiveValues.name("  Live\tat  {the}  Hall \n"))
        assertEquals("Live at the Hall", ChordProDirectiveValues.name(untidy))
    }

    @Test
    fun `a blank or all-brace name is none`() {
        assertNull(ChordProDirectiveValues.name(null))
        assertNull(ChordProDirectiveValues.name(" \t "))
        assertNull(ChordProDirectiveValues.name("{ }}"))
    }

    @Test
    fun `cleaning a name twice changes nothing more`() {
        listOf(untidy, "Plain", " a  b ", "{x}", " wide space").forEach { name ->
            val once = ChordProDirectiveValues.name(name)
            assertEquals(once, ChordProDirectiveValues.name(once), name)
        }
    }

    @Test
    fun `a link's name reads back as cleaned and is written again unchanged`() {
        val video = "https://www.youtube.com/watch?v=abc"
        val text = ChordProLinks.setLinks("{title: T}\n[C]Line", listOf(ChordProLink(video, untidy)))
        val links = ChordProParser.parseMetadata(text).links

        assertEquals(listOf(ChordProLink(video, ChordProDirectiveValues.name(untidy))), links)
        assertEquals(text, ChordProLinks.setLinks(text, links))
    }

    @Test
    fun `an attachment's name reads back as cleaned and is written again unchanged`() {
        val sheet = "3fa9c2e81b7d4f06.pdf"
        val text = ChordProAttachments.setAttachments("{title: T}\n[C]Line", listOf(ChordProAttachment(sheet, untidy)))
        val attachments = ChordProParser.parseMetadata(text).attachments

        assertEquals(listOf(ChordProAttachment(sheet, ChordProDirectiveValues.name(untidy))), attachments)
        assertEquals(text, ChordProAttachments.setAttachments(text, attachments))
    }
}
