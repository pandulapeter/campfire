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

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTextImportTest {

    private fun namesOf(texts: List<String>, subject: String?) = sharedTextsToImportedFiles(texts, subject).map { it.name }

    private fun nameOf(text: String, subject: String? = null) = namesOf(listOf(text), subject).single()

    @Test
    fun anEmptyShareIsOneEmptyUntitledFile() {
        val file = sharedTextsToImportedFiles(emptyList(), null).single()
        assertEquals("untitled${LibraryFiles.TEXT_EXTENSION}", file.name)
        assertTrue(file.bytes.isEmpty())
    }

    @Test
    fun aTextWithNoSubjectIsNamedAfterItsFirstPlainLine() {
        assertEquals("Amazing Grace${LibraryFiles.TEXT_EXTENSION}", nameOf("\n  \n{title: x}\n[Chorus]\n# c\n  Amazing Grace  \nHow sweet"))
    }

    @Test
    fun aTextOfOnlyDirectivesSectionsAndCommentsIsUntitled() {
        assertEquals("untitled${LibraryFiles.TEXT_EXTENSION}", nameOf("{title: x}\n[Chorus]\n# c"))
    }

    @Test
    fun aSubjectNamesTheFile() {
        assertEquals("Page title${LibraryFiles.TEXT_EXTENSION}", nameOf("Lyrics", subject = "Page title"))
    }

    @Test
    fun severalTextsUnderOneSubjectAreNumbered() {
        assertEquals(
            listOf("Notes 1${LibraryFiles.TEXT_EXTENSION}", "Notes 2${LibraryFiles.TEXT_EXTENSION}"),
            namesOf(listOf("a", "b"), subject = "Notes"),
        )
    }

    @Test
    fun aSubjectWithNothingUsableFallsBackToTheFirstPlainLine() {
        assertEquals("Lyric${LibraryFiles.TEXT_EXTENSION}", nameOf("Lyric", subject = " / // "))
    }

    @Test
    fun separatorsAndControlCharactersBecomeSpaces() {
        assertEquals("a b c d${LibraryFiles.TEXT_EXTENSION}", nameOf("a/b\\c\u0007d"))
    }

    @Test
    fun aLongLineIsCutToEightyCharactersAndTrimmed() {
        assertEquals("x".repeat(79) + LibraryFiles.TEXT_EXTENSION, nameOf("x".repeat(79) + " " + "y".repeat(120)))
        assertEquals("z".repeat(80) + LibraryFiles.TEXT_EXTENSION, nameOf("z".repeat(200)))
    }

    @Test
    fun aTextOfOnlyLinksIsPassedOnEmpty() {
        val file = sharedTextsToImportedFiles(listOf("https://example.com/a\n\n  HTTP://EXAMPLE.COM/b  \n"), null).single()
        assertTrue(file.bytes.isEmpty())
    }

    @Test
    fun aLinkWithALyricIsKept() {
        val text = "https://example.com\nA lyric line"
        assertContentEquals(text.encodeToByteArray(), sharedTextsToImportedFiles(listOf(text), null).single().bytes)
    }

    @Test
    fun theBytesAreTheTextInUtf8() {
        val text = "Ég a napmelegtől\nКатюша"
        val bytes = sharedTextsToImportedFiles(listOf(text), null).single().bytes
        assertEquals(text, bytes.decodeToString())
    }
}
