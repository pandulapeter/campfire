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
import kotlin.test.assertNull

class ChordProCoverArtTest {

    private val url = "https://coverartarchive.org/release-group/abc/front-250"

    @Test
    fun `a cover is read from its meta directive`() {
        val metadata = ChordProParser.parseMetadata("{title: T}\n{meta: cover $url}")

        assertEquals(url, metadata.coverArt)
    }

    @Test
    fun `the key of a cover is matched ignoring case`() {
        assertEquals(url, ChordProParser.parseMetadata("{meta: Cover $url}").coverArt)
    }

    @Test
    fun `the first usable cover wins`() {
        val metadata = ChordProParser.parseMetadata("{meta: cover ftp://example.com/a.jpg}\n{meta: cover $url}\n{meta: cover http://example.com/b.jpg}")

        assertEquals(url, metadata.coverArt)
    }

    @Test
    fun `a value that is not an http address is not a cover`() {
        assertNull(ChordProParser.parseMetadata("{meta: cover file:///etc/passwd}").coverArt)
        assertNull(ChordProParser.parseMetadata("{meta: cover https://}").coverArt)
        assertNull(ChordProParser.parseMetadata("{meta: cover https://example.com/a b.jpg}").coverArt)
        assertNull(ChordProParser.parseMetadata("{meta: cover}").coverArt)
    }

    @Test
    fun `a cover is not carried as custom metadata as well`() {
        assertEquals(emptyMap(), ChordProParser.parseMetadata("{meta: cover $url}").custom)
    }

    @Test
    fun `the summary carries the cover`() {
        assertEquals(url, ChordProParser.summarize("{meta: cover $url}\n[G]Line").metadata.coverArt)
    }

    @Test
    fun `a cover is written into the header after the album`() {
        val text = "{title: T}\n{artist: A}\n{album: B}\n{year: 1999}\n\n[G]Line\n"

        assertEquals(
            "{title: T}\n{artist: A}\n{album: B}\n{meta: cover $url}\n{year: 1999}\n\n[G]Line\n",
            ChordProCoverArt.set(text, url),
        )
    }

    @Test
    fun `a cover replaces the one the song has on its own line`() {
        val text = "{title: T}\r\n{meta: language en}\r\n{meta: cover https://example.com/old.jpg}\r\n\r\nLine\r\n"

        assertEquals(
            "{title: T}\r\n{meta: language en}\r\n{meta: cover $url}\r\n\r\nLine\r\n",
            ChordProCoverArt.set(text, url),
        )
    }

    @Test
    fun `setting a cover drops every cover line after the first`() {
        val text = "{title: T}\n{meta: cover https://example.com/a.jpg}\n{meta: cover https://example.com/b.jpg}\nLine"

        assertEquals("{title: T}\n{meta: cover $url}\nLine", ChordProCoverArt.set(text, url))
    }

    @Test
    fun `a null cover removes every cover line`() {
        val text = "{title: T}\n{meta: cover $url}\n{meta: cover nonsense}\nLine\n"

        assertEquals("{title: T}\nLine\n", ChordProCoverArt.set(text, null))
    }

    @Test
    fun `a value that is not a cover removes it as well`() {
        assertEquals("{title: T}", ChordProCoverArt.set("{title: T}\n{meta: cover $url}", "not a url"))
    }

    @Test
    fun `a text that already names the cover is returned unchanged`() {
        val text = "{title: T}\n{meta:   cover   $url }\nLine"

        assertEquals(text, ChordProCoverArt.set(text, url))
        assertEquals("{title: T}\nLine", ChordProCoverArt.set("{title: T}\nLine", null))
    }

    @Test
    fun `the cover survives a round trip through the serializer`() {
        val song = ChordProParser.parse("{title: T}\n{album: B}\n{meta: cover $url}\n\n[G]Line")

        assertEquals(song, ChordProParser.parse(ChordProSerializer.serialize(song)))
        assertEquals(url, ChordProParser.parse(ChordProSerializer.serialize(song)).metadata.coverArt)
    }

    @Test
    fun `a cover counts as declared metadata`() {
        assertEquals(setOf("title", "cover"), ChordProHeader.declaredMetadata("{title: T}\n{meta: cover $url}"))
    }
}
