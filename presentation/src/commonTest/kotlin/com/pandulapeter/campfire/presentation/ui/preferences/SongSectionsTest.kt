/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class SongSectionsTest {

    @Test
    fun `nothing read yet shows the scales but no diagrams`() = assertEquals(
        SongSections(areDiagramsShown = false, areDegreesShown = false, areScaleSuggestionsShown = true),
        SongSections.of(null),
    )

    @Test
    fun `the defaults show the diagrams and the scales without degrees`() = assertEquals(
        SongSections(areDiagramsShown = true, areDegreesShown = false, areScaleSuggestionsShown = true),
        SongSections.of(testPreferences()),
    )

    @Test
    fun `the degrees switch labels the diagrams`() = assertEquals(
        SongSections(areDiagramsShown = true, areDegreesShown = true, areScaleSuggestionsShown = true),
        SongSections.of(testPreferences().copy(areChordDegreesEnabled = true)),
    )

    @Test
    fun `the degrees are not shown without a diagram to label`() = assertEquals(
        SongSections(areDiagramsShown = false, areDegreesShown = false, areScaleSuggestionsShown = true),
        SongSections.of(testPreferences().copy(areChordDiagramsEnabled = false, areChordDegreesEnabled = true)),
    )

    @Test
    fun `chords off take the diagrams and the degrees and the scales`() = assertEquals(
        SongSections(areDiagramsShown = false, areDegreesShown = false, areScaleSuggestionsShown = false),
        SongSections.of(testPreferences().copy(areChordsEnabled = false, areChordDegreesEnabled = true)),
    )

    @Test
    fun `the scales switch takes the suggestions`() = assertEquals(
        SongSections(areDiagramsShown = true, areDegreesShown = false, areScaleSuggestionsShown = false),
        SongSections.of(testPreferences().copy(areScalesEnabled = false)),
    )

    @Test
    fun `the suggestions switch takes only the suggestions`() = assertEquals(
        SongSections(areDiagramsShown = true, areDegreesShown = false, areScaleSuggestionsShown = false),
        SongSections.of(testPreferences().copy(areScaleSuggestionsEnabled = false)),
    )
}
