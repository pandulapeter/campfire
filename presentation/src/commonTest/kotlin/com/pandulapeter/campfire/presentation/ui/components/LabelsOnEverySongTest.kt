/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelsOnEverySongTest {

    private fun song(tags: List<String> = emptyList(), languages: List<String> = emptyList()) = Song(
        fileName = "a.cho", title = "A", artist = "", key = null, transpose = 0, tags = tags, languages = languages,
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    @Test
    fun `an empty library has no label on every song`() = assertEquals(LabelsOnEverySong(), labelsOnEverySongOf(emptyList()))

    @Test
    fun `tags are compared without their case and given in lowercase`() = assertEquals(
        LabelsOnEverySong(tags = setOf("folk"), languages = setOf("en")),
        labelsOnEverySongOf(
            listOf(
                song(tags = listOf("Folk", "Demo"), languages = listOf("en")),
                song(tags = listOf("folk"), languages = listOf("en", "hu")),
            ),
        ),
    )

    @Test
    fun `a first song with no tags still lets the languages be counted`() = assertEquals(
        LabelsOnEverySong(tags = emptySet(), languages = setOf("hu")),
        labelsOnEverySongOf(
            listOf(
                song(languages = listOf("hu")),
                song(tags = listOf("folk"), languages = listOf("hu")),
                song(tags = listOf("folk"), languages = listOf("hu", "en")),
            ),
        ),
    )
}
