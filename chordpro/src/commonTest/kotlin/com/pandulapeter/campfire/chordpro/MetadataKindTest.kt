/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataKindTest {

    @Test
    fun `the header order is the order of the kinds`() {
        assertEquals(
            listOf(
                "title", "subtitle", "artist", "composer", "lyricist", "album", "cover", "year", "key", "capo", "tempo", "time",
                "duration", "tag", "language", "link",
            ),
            ChordProHeaderLayout.metadataOrder,
        )
    }

    @Test
    fun `the short spellings are the ones ChordPro defines`() {
        assertEquals(mapOf("t" to "title", "st" to "subtitle", "lang" to "language"), ChordProHeaderLayout.metadataAliases)
    }

    @Test
    fun `the meta items that stand for a standalone directive are the twelve the model has a field for`() {
        assertEquals(
            setOf("title", "subtitle", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo", "time", "duration"),
            MetadataKind.entries.filter { it.isStandardMeta }.map { it.longName }.toSet(),
        )
    }

    @Test
    fun `the repeatable and the changeable kinds`() {
        assertEquals(setOf("tag", "language", "link"), MetadataKind.entries.filter { it.isRepeatable }.map { it.longName }.toSet())
        assertEquals(setOf("tempo", "time"), MetadataKind.entries.filter { it.isTimingChange }.map { it.longName }.toSet())
        assertEquals(setOf("key", "tempo", "time"), MetadataKind.entries.filter { it.isKeptInBody }.map { it.longName }.toSet())
    }
}
