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

import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChordProLinksTest {

    private val video = "https://www.youtube.com/watch?v=abc"
    private val tab = "https://example.com/tabs/song"

    @Test
    fun `links are read from their meta directives, in order and each once`() {
        val metadata = ChordProParser.parseMetadata("{title: T}\n{meta: link $video}\n{meta: Link $tab}\n{meta: link $video}")

        assertEquals(listOf(ChordProLink(video), ChordProLink(tab)), metadata.links)
        assertEquals(emptyMap(), metadata.custom)
    }

    @Test
    fun `a value that is not an http address is not a link`() {
        val metadata = ChordProParser.parseMetadata("{meta: link javascript:alert(1)}\n{meta: link https://}\n{meta: link}")

        assertEquals(emptyList(), metadata.links)
    }

    @Test
    fun `a link is added after the header, and next to the other links`() {
        val text = "{title: T}\n{tag: Rock}\n\n[C]Line"

        val withOne = ChordProLinks.addLink(text, video)
        val withTwo = ChordProLinks.addLink(withOne, tab)

        assertEquals("{title: T}\n{tag: Rock}\n{meta: link $video}\n\n[C]Line", withOne)
        assertEquals("{title: T}\n{tag: Rock}\n{meta: link $video}\n{meta: link $tab}\n\n[C]Line", withTwo)
    }

    @Test
    fun `adding a link the song already has, or an unusable one, changes nothing`() {
        val text = "{title: T}\n{meta: link $video}\n"

        assertSame(text, ChordProLinks.addLink(text, " $video "))
        assertSame(text, ChordProLinks.addLink(text, "not a link"))
    }

    @Test
    fun `removing a link drops every line naming it and nothing else`() {
        val text = "{title: T}\n{meta: link $video}\n{meta: link $tab}\n{meta:   link   $video}\n\n[C]Line"

        assertEquals("{title: T}\n{meta: link $tab}\n\n[C]Line", ChordProLinks.removeLink(text, video))
        assertSame(text, ChordProLinks.removeLink(text, "https://example.com/other"))
    }

    @Test
    fun `an address typed without its scheme is taken as https`() {
        assertEquals("https://youtu.be/abc", ChordProLinks.usableUrl(" youtu.be/abc "))
        assertEquals("http://example.com", ChordProLinks.usableUrl("http://example.com"))
        assertEquals(null, ChordProLinks.usableUrl("hello"))
        assertEquals(null, ChordProLinks.usableUrl("ftp://example.com"))
        assertEquals(null, ChordProLinks.usableUrl("example.com/a b"))
        assertEquals("https://example.com:8080/x", ChordProLinks.usableUrl("example.com:8080/x"))
        assertEquals("https://www.example.com", ChordProLinks.usableUrl("www.example.com"))
        assertEquals("https://hu.wikipedia.org/wiki/Tükör", ChordProLinks.usableUrl("hu.wikipedia.org/wiki/Tükör"))
    }

    @Test
    fun `text that does not start with a host is not taken as an https address`() {
        assertEquals(null, ChordProLinks.usableUrl("https:/example.com"))
        assertEquals(null, ChordProLinks.usableUrl("https//example.com"))
        assertEquals(null, ChordProLinks.usableUrl("mailto:me@example.com"))
        assertEquals(null, ChordProLinks.usableUrl("me@example.com"))
        assertEquals(null, ChordProLinks.usableUrl("javascript:alert(document.title)"))
        assertEquals(null, ChordProLinks.usableUrl(".example.com"))
        assertEquals(null, ChordProLinks.usableUrl("example.com."))
        assertEquals(null, ChordProLinks.usableUrl("example.com:/x"))
    }

    @Test
    fun `links survive a round trip through the serializer`() {
        val text = ChordProSerializer.serialize(ChordProParser.parse("{title: T}\n{meta: link $video}\n{meta: link $tab}\n[C]Line"))

        assertEquals(listOf(ChordProLink(video), ChordProLink(tab)), ChordProParser.parseMetadata(text).links)
    }

    @Test
    fun `named links keep their first name and survive serialization`() {
        val text = "{meta: link $video Live recording}\n{meta: link $video Another name}\n{meta: link $tab}"
        val links = listOf(ChordProLink(video, "Live recording"), ChordProLink(tab))

        assertEquals(links, ChordProParser.parseMetadata(text).links)
        assertEquals(links, ChordProParser.parseMetadata(ChordProSerializer.serialize(ChordProParser.parse(text))).links)
    }

    @Test
    fun `editing links preserves other text and the spelling of unchanged directives`() {
        val text = "{title: T}\r\n{meta: Link $video}\r\n{meta: link $tab Old name}\r\n\r\n[C]Line\r\n"
        val wanted = listOf(ChordProLink(video), ChordProLink(tab, "New name"), ChordProLink("youtu.be/new"))

        assertEquals(
            "{title: T}\r\n{meta: Link $video}\r\n{meta: link $tab New name}\r\n{meta: link https://youtu.be/new}\r\n\r\n[C]Line\r\n",
            ChordProLinks.setLinks(text, wanted),
        )
        assertSame(text, ChordProLinks.setLinks(text, ChordProParser.parseMetadata(text).links))
    }

    @Test
    fun `editing removes duplicates and unwanted links and trims optional names`() {
        val text = "{title: T}\n{meta: link $video First}\n{meta: link $video Second}\n{meta: link $tab}\n[C]Line"

        assertEquals(
            "{title: T}\n{meta: link $video}\n[C]Line",
            ChordProLinks.setLinks(text, listOf(ChordProLink(video, "  "))),
        )
        assertEquals("{title: T}\n[C]Line", ChordProLinks.setLinks(text, emptyList()))
    }

    @Test
    fun `a name cannot inject another directive or a line of lyrics`() {
        val text = ChordProLinks.setLinks("{title: T}\n[C]Line", listOf(ChordProLink(video, "Live}\n{tag: Injected")))

        assertEquals(emptyList(), ChordProParser.parseMetadata(text).tags)
        assertEquals(listOf(ChordProLink(video, "Live tag: Injected")), ChordProParser.parseMetadata(text).links)
    }

    @Test
    fun `metadata defaults to no links`() {
        assertEquals(emptyList(), ChordProMetadata().links)
    }
}
