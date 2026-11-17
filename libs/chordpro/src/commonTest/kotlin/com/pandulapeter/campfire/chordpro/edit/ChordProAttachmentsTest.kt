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
import com.pandulapeter.campfire.chordpro.ChordProSerializer
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChordProAttachmentsTest {

    private val sheet = "3fa9c2e81b7d4f06.pdf"
    private val take = "0123456789abcdef.mp3"

    @Test
    fun `attachments are read from their meta directives, in order, each file once, keeping its first name`() {
        val metadata = ChordProParser.parseMetadata(
            "{title: T}\n{meta: attachment $sheet Sheet music.pdf}\n{meta: Attachment $take}\n{meta: attachment $sheet Other name}",
        )

        assertEquals(listOf(ChordProAttachment(sheet, "Sheet music.pdf"), ChordProAttachment(take)), metadata.attachments)
        assertEquals(emptyMap(), metadata.custom)
    }

    @Test
    fun `names keep their spaces and quotes, and a missing name reads as none`() {
        val metadata = ChordProParser.parseMetadata("{meta: attachment $sheet  \"Live\" at Joe's   place.pdf }\n{meta: attachment $take   }")

        assertEquals(listOf(ChordProAttachment(sheet, "\"Live\" at Joe's   place.pdf"), ChordProAttachment(take)), metadata.attachments)
    }

    @Test
    fun `an address or a path is never an attachment`() {
        val text = "{meta: attachment https://example.com/a.pdf Sheet}\n{meta: attachment ../a.pdf}\n{meta: attachment C:a.pdf}\n{meta: attachment}"

        assertEquals(emptyList(), ChordProParser.parseMetadata(text).attachments)
        assertEquals(emptySet(), ChordProAttachments.referencedSources(text))
        assertEquals(emptySet(), ChordProParser.summarize(text).attachmentSources)
    }

    @Test
    fun `the files a song keeps are read generously, and the summary reads the same set`() {
        val text = "{title: T}\n{meta: attachment $sheet Sheet}\n# {meta: attachment $take Take}\n{start_of_abc}\n" +
            "{meta: attachment abcdef0123456789.mid}\n{end_of_abc}\n{image: src=0011223344556677.png}\n[C]Line"

        val expected = setOf(sheet, take, "abcdef0123456789.mid")
        assertEquals(expected, ChordProAttachments.referencedSources(text))
        assertEquals(expected, ChordProParser.summarize(text).attachmentSources)
        assertEquals(expected + "0011223344556677.png", libraryFilesNamedBy(text))
        assertEquals(libraryFilesNamedBy(text), ChordProParser.summarize(text).librarySources)
    }

    @Test
    fun `setting attachments keeps every other byte and the lines of unchanged ones`() {
        val text = "{title: T}\r\n{meta: Attachment $sheet Sheet}\r\n{meta: link https://example.com}\r\n\r\n[C]Line\r\n"
        val wanted = listOf(ChordProAttachment(sheet, "Sheet"), ChordProAttachment(take, "  Take   one "))

        assertEquals(
            "{title: T}\r\n{meta: Attachment $sheet Sheet}\r\n{meta: attachment $take Take one}\r\n{meta: link https://example.com}\r\n\r\n[C]Line\r\n",
            ChordProAttachments.setAttachments(text, wanted),
        )
        assertSame(text, ChordProAttachments.setAttachments(text, ChordProParser.parseMetadata(text).attachments))
        assertEquals(
            "{title: T}\r\n{meta: link https://example.com}\r\n\r\n[C]Line\r\n",
            ChordProAttachments.setAttachments(text, emptyList()),
        )
    }

    @Test
    fun `setting attachments leaves out addresses and duplicates, and a name cannot write another directive`() {
        val text = ChordProAttachments.setAttachments(
            "{title: T}\n[C]Line",
            listOf(
                ChordProAttachment(sheet, "Sheet}\n{tag: Injected"),
                ChordProAttachment("https://example.com/a.pdf"),
                ChordProAttachment(sheet, "Again"),
            ),
        )

        assertEquals(emptyList(), ChordProParser.parseMetadata(text).tags)
        assertEquals(listOf(ChordProAttachment(sheet, "Sheet tag: Injected")), ChordProParser.parseMetadata(text).attachments)
    }

    @Test
    fun `rewriting changes only the file, on every line that names one`() {
        val text = "{title: T}\n{meta:  attachment   $sheet  Sheet music}\n#{meta: attachment $take}\n{meta: link https://x.com}\n"

        val rewritten = ChordProAttachments.withAttachmentSourcesRewritten(text) { if (it == sheet) "${sheet.dropLast(4)}0.pdf" else "${it}0" }

        assertEquals(
            "{title: T}\n{meta:  attachment   ${sheet.dropLast(4)}0.pdf  Sheet music}\n#{meta: attachment ${take}0}\n{meta: link https://x.com}\n",
            rewritten,
        )
        assertSame(text, ChordProAttachments.withAttachmentSourcesRewritten(text) { null })
        assertSame(text, ChordProAttachments.withAttachmentSourcesRewritten(text) { "https://example.com/x.pdf" })
    }

    @Test
    fun `a file declared twice is kept once when the attachments are written back`() {
        val text = "{title: T}\n{meta: attachment $sheet Sheet}\n{meta: attachment $take Take}\n[C]Line\n{meta: attachment $sheet Sheet}"

        val written = ChordProAttachments.setAttachments(text, ChordProParser.parseMetadata(text).attachments)

        assertEquals("{title: T}\n{meta: attachment $sheet Sheet}\n{meta: attachment $take Take}\n[C]Line", written)
    }

    @Test
    fun `an attachment left out loses every line naming it, commented out or not, and nothing else`() {
        val text = "{title: T}\r\n{meta: attachment $sheet Sheet}\r\n# {meta: attachment $sheet Old}\r\n{meta: attachment $take Take}\r\n[C]Line"

        assertEquals(
            "{title: T}\r\n{meta: attachment $take Take}\r\n[C]Line",
            ChordProAttachments.withoutAttachments(text, setOf(sheet)),
        )
        assertSame(text, ChordProAttachments.withoutAttachments(text, setOf("ffffffffffffffff.pdf")))
    }

    @Test
    fun `attachments survive a round trip through the serializer`() {
        val text = "{title: T}\n{meta: attachment $sheet Sheet music.pdf}\n{meta: attachment $take}\n[C]Line"
        val expected = listOf(ChordProAttachment(sheet, "Sheet music.pdf"), ChordProAttachment(take))

        assertEquals(expected, ChordProParser.parseMetadata(ChordProSerializer.serialize(ChordProParser.parse(text))).attachments)
    }
}
