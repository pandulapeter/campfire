/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorSongTest {

    @Test
    fun `the caret starts on the line after the first section starts`() {
        val text = "{title: Song}\n{start_of_verse}\n\n{end_of_verse}\n{start_of_chorus}\n"
        assertEquals(text.indexOf("{start_of_verse}") + "{start_of_verse}\n".length, text.caretInsideFirstSection())
    }

    @Test
    fun `without a section or a line after it the caret goes to the end`() {
        assertEquals(13, "{title: Song}".caretInsideFirstSection())
        assertEquals(30, "{title: Song}\n{start_of_verse}".caretInsideFirstSection())
    }

    @Test
    fun `the song is what the text says`() = assertEquals(
        Song(
            fileName = "file_name.cho",
            title = "Hello (Live)",
            artist = "Me",
            key = "G",
            transpose = 0,
            tags = listOf("Rock"),
            languages = listOf("en"),
            coverArtUrl = "https://example.com/cover.jpg",
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0,
            size = 0,
        ),
        ChordProParser.summarize(
            """
            {title: Hello}
            {subtitle: Live}
            {artist: Me}
            {key: G}
            {tag: Rock}
            {tag: rock}
            {meta: language en}
            {meta: cover https://example.com/cover.jpg}
            [G]Hello
            """.trimIndent(),
        ).toEditorSong("file_name.cho"),
    )

    @Test
    fun `a text without a title is named by its file`() = assertEquals(
        "file_name",
        ChordProParser.summarize("[G]Hello").toEditorSong("file_name.cho").title,
    )
}
