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

class ChordProHeaderTest {

    @Test
    fun `a directive is written into the header rather than at the caret`() {
        val text = "{title: T}\n\n{start_of_verse}\nThe first line\n{end_of_verse}"

        assertEquals("{title: T}\n{artist: }\n\n{start_of_verse}\nThe first line\n{end_of_verse}", text.insert("artist"))
    }

    @Test
    fun `a header written in the app's order stays in it`() {
        val text = "{title: T}\n{artist: A}\n{capo: 2}\n\nThe first line"

        assertEquals("{title: T}\n{artist: A}\n{key: }\n{capo: 2}\n\nThe first line", text.insert("key"))
    }

    @Test
    fun `a header the user has arranged their own way is left as it is`() {
        val text = "{artist: A}\n{title: T}\n\nThe first line"

        assertEquals("{artist: A}\n{title: T}\n{album: }\n\nThe first line", text.insert("album"))
    }

    @Test
    fun `a directive that comes before everything the song declares opens the header`() {
        val text = "{artist: A}\n\nThe first line"

        assertEquals("{title: }\n{artist: A}\n\nThe first line", text.insert("title"))
    }

    @Test
    fun `the short spelling of a directive is what it is ordered by`() {
        val text = "{t: T}\n{capo: 2}\n\nThe first line"

        assertEquals("{t: T}\n{year: }\n{capo: 2}\n\nThe first line", text.insert("year"))
    }

    @Test
    fun `a repeatable directive is written after the last one of its kind`() {
        val text = "{title: T}\n{tag: slow}\n{tag: campfire}\n\nThe first line"

        assertEquals("{title: T}\n{tag: slow}\n{tag: campfire}\n{tag: }\n\nThe first line", text.insert("tag"))
    }

    @Test
    fun `a language is written after the ones the song already declares, whichever spelling they use`() {
        val text = "{title: T}\n{meta: lang hu}\n{capo: 2}\n\nThe first line"

        assertEquals(
            "{title: T}\n{meta: lang hu}\n{meta: language }\n{capo: 2}\n\nThe first line",
            text.insert("language", prefix = "{meta: language "),
        )
    }

    @Test
    fun `a song that opens with its lyrics gets the directive above them`() {
        assertEquals("{title: }\nThe first line", "The first line".insert("title"))
    }

    @Test
    fun `a file that ends with its header takes the line break with the directive`() {
        assertEquals("{title: T}\n{artist: }", "{title: T}".insert("artist"))
    }

    @Test
    fun `a file that ends with a line break does not grow another one`() {
        assertEquals("{title: T}\n{artist: }\n", "{title: T}\n".insert("artist"))
    }

    @Test
    fun `an empty file is a header of one directive`() {
        assertEquals("{title: }\n", "".insert("title"))
    }

    @Test
    fun `the caret lands between the two halves of the directive`() {
        val insertion = ChordProHeader.insert("{title: T}\n\nThe first line", name = "artist", prefix = "{artist: ", suffix = "}")

        assertEquals("{title: T}\n{artist: ".length, insertion.caretOffset)
    }

    @Test
    fun `every spelling of a directive is reported as declared under one name`() {
        val text = "{t: T}\n{st: S}\n{meta: tag slow}\n{lang: hu}\n{capo: 2}"

        assertEquals(setOf("title", "subtitle", "tag", "language", "capo"), ChordProHeader.declaredMetadata(text))
    }

    @Test
    fun `a directive waiting to be typed into counts as declared`() {
        assertEquals(setOf("title"), ChordProHeader.declaredMetadata("{title: }\n\nThe first line"))
    }

    @Test
    fun `what makes up the song is not metadata`() {
        val text = "{start_of_verse}\nThe first line\n{end_of_verse}\n{comment: Repeat}\n{meta: tuning DADGAD}"

        assertEquals(emptySet(), ChordProHeader.declaredMetadata(text))
    }

    @Test
    fun `the tags and the languages of a song are the repeatable directives`() {
        assertEquals(setOf("tag", "language"), ChordProHeader.repeatableMetadata)
    }

    private fun String.insert(name: String, prefix: String = "{$name: ", suffix: String = "}"): String {
        val insertion = ChordProHeader.insert(this, name = name, prefix = prefix, suffix = suffix)
        return replaceRange(insertion.offset, insertion.offset, insertion.text)
    }
}
