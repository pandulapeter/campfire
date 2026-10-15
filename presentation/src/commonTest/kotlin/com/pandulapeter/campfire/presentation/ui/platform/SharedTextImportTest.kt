/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedTextImportTest {

    private fun namesOf(texts: List<String>, subject: String?) = sharedTextsToImportedFiles(texts, subject).map { it.name }

    private fun nameOf(text: String, subject: String? = null) = namesOf(listOf(text), subject).single()

    @Test
    fun `an empty share is one empty untitled file`() {
        val file = sharedTextsToImportedFiles(emptyList(), null).single()
        assertEquals("untitled${LibraryFiles.TEXT_EXTENSION}", file.name)
        assertTrue(file.bytes.isEmpty())
    }

    @Test
    fun `a text with no subject is named after its first plain line`() {
        assertEquals("Amazing Grace${LibraryFiles.TEXT_EXTENSION}", nameOf("\n  \n{title: x}\n[Chorus]\n# c\n  Amazing Grace  \nHow sweet"))
    }

    @Test
    fun `a text of only directives sections and comments is untitled`() {
        assertEquals("untitled${LibraryFiles.TEXT_EXTENSION}", nameOf("{title: x}\n[Chorus]\n# c"))
    }

    @Test
    fun `a subject names the file`() {
        assertEquals("Page title${LibraryFiles.TEXT_EXTENSION}", nameOf("Lyrics", subject = "Page title"))
    }

    @Test
    fun `several texts under one subject are numbered`() {
        assertEquals(
            listOf("Notes 1${LibraryFiles.TEXT_EXTENSION}", "Notes 2${LibraryFiles.TEXT_EXTENSION}"),
            namesOf(listOf("a", "b"), subject = "Notes"),
        )
    }

    @Test
    fun `a subject with nothing usable falls back to the first plain line`() {
        assertEquals("Lyric${LibraryFiles.TEXT_EXTENSION}", nameOf("Lyric", subject = " / // "))
    }

    @Test
    fun `separators and control characters become spaces`() {
        assertEquals("a b c d${LibraryFiles.TEXT_EXTENSION}", nameOf("a/b\\c\u0007d"))
    }

    @Test
    fun `a long line is cut to eighty characters and trimmed`() {
        assertEquals("x".repeat(79) + LibraryFiles.TEXT_EXTENSION, nameOf("x".repeat(79) + " " + "y".repeat(120)))
        assertEquals("z".repeat(80) + LibraryFiles.TEXT_EXTENSION, nameOf("z".repeat(200)))
    }

    @Test
    fun `a text of only links is passed on empty`() {
        val file = sharedTextsToImportedFiles(listOf("https://example.com/a\n\n  HTTP://EXAMPLE.COM/b  \n"), null).single()
        assertTrue(file.bytes.isEmpty())
    }

    @Test
    fun `a link with a lyric is kept`() {
        val text = "https://example.com\nA lyric line"
        assertContentEquals(text.encodeToByteArray(), sharedTextsToImportedFiles(listOf(text), null).single().bytes)
    }

    @Test
    fun `the bytes are the text in UTF-8`() {
        val text = "Ég a napmelegtől\nКатюша"
        val bytes = sharedTextsToImportedFiles(listOf(text), null).single().bytes
        assertEquals(text, bytes.decodeToString())
    }

    @Test
    fun `only links are told apart from a text with something else in it`() {
        assertTrue("https://example.com/a\n\n  HTTP://EXAMPLE.COM/b  ".isOnlyLinks())
        assertFalse("https://example.com\nA lyric line".isOnlyLinks())
        assertFalse("   \n".isOnlyLinks())
    }

    @Test
    fun `a text fits the import by its size in UTF-8`() {
        val limit = ImportLimits.MAX_TEXT_FILE_SIZE.toInt()
        assertTrue("a".repeat(limit).fitsImport())
        assertFalse("a".repeat(limit + 1).fitsImport())
        // Two bytes each in UTF-8, so half as many characters already reach the limit.
        assertFalse("é".repeat(limit / 2 + 1).fitsImport())
    }
}
