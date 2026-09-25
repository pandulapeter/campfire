/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The preferences are the one document the app overwrites as a whole, so whatever is not read from it is lost by the
 * next save: one value of the wrong shape has to cost that value and nothing else.
 */
internal class UserPreferencesDocumentFormatTest {

    @Test
    fun readsWhatItWrote() {
        val document = UserPreferencesDocument(
            isPerformanceModeEnabled = true,
            shouldShowArchivedSetlists = true,
            isLyricsOnlyModeEnabled = true,
            isHorizontalSectionFlowEnabled = false,
            fontScale = 1.25f,
            sortingMode = "by_title",
            setlistSortingMode = "by_title",
            uiMode = "dark",
            themeColor = "forest",
            isAppIconThemed = false,
            language = "hu",
            accidentals = "flats",
            isGermanNotationEnabled = true,
            transpositions = mapOf("a.cho" to 2, "b.cho" to -3),
            foldedSections = mapOf("a.cho" to listOf("chorus#1", "intro#1/tab#1")),
            tagMatchMode = "all",
            languageMatchMode = "all",
        )

        val decoded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(document))

        assertEquals(document, decoded.document)
        assertTrue(decoded.isIntact)
    }

    @Test
    fun readsADocumentWithMissingAndUnknownFields() {
        val decoded = UserPreferencesDocumentFormat.decode("""{"fontScale": 1.5, "somethingNew": [1, 2]}""")

        assertEquals(UserPreferencesDocument(fontScale = 1.5f), decoded.document)
        assertTrue(decoded.isIntact)
    }

    @Test
    fun treatsANullAsAMissingField() {
        val decoded = UserPreferencesDocumentFormat.decode("""{"fontScale": null, "transpositions": null, "uiMode": "dark"}""")

        assertEquals(UserPreferencesDocument(uiMode = "dark"), decoded.document)
        assertTrue(decoded.isIntact)
    }

    @Test
    fun givesUpOnlyTheFieldOfTheWrongShape() {
        val decoded = UserPreferencesDocumentFormat.decode(
            """{"fontScale": "big", "isLyricsOnlyModeEnabled": true, "language": "hu", "transpositions": {"a.cho": 2}}""",
        )

        assertEquals(
            expected = UserPreferencesDocument(isLyricsOnlyModeEnabled = true, language = "hu", transpositions = mapOf("a.cho" to 2)),
            actual = decoded.document,
        )
        assertFalse(decoded.isIntact)
    }

    @Test
    fun givesUpOnlyTheTranspositionThatIsNotANumber() {
        val decoded = UserPreferencesDocumentFormat.decode(
            """{"transpositions": {"a.cho": 2, "b.cho": "2x", "c.cho": -3, "d.cho": 1.5, "e.cho": [1]}}""",
        )

        assertEquals(mapOf("a.cho" to 2, "c.cho" to -3), decoded.document.transpositions)
        assertFalse(decoded.isIntact)
    }

    @Test
    fun givesUpOnlyTheFoldedSectionsThatAreNotText() {
        val decoded = UserPreferencesDocumentFormat.decode(
            """{"foldedSections": {"a.cho": ["chorus#1", 2, "verse#1"], "b.cho": "chorus#1", "c.cho": ["bridge#1"]}}""",
        )

        assertEquals(mapOf("a.cho" to listOf("chorus#1", "verse#1"), "c.cho" to listOf("bridge#1")), decoded.document.foldedSections)
        assertFalse(decoded.isIntact)
    }

    @Test
    fun givesUpTranspositionsThatAreNotAnObject() {
        val decoded = UserPreferencesDocumentFormat.decode("""{"transpositions": [1, 2], "themeColor": "forest"}""")

        assertEquals(UserPreferencesDocument(themeColor = "forest"), decoded.document)
        assertFalse(decoded.isIntact)
    }

    @Test
    fun readsTextThatIsNotAnObjectAsTheDefaults() {
        listOf("", "   ", """{"fontScale": 1.""", "[1, 2]", "hello").forEach { text ->
            val decoded = UserPreferencesDocumentFormat.decode(text)

            assertEquals(UserPreferencesDocument(), decoded.document, text)
            assertFalse(decoded.isIntact, text)
        }
    }
}
