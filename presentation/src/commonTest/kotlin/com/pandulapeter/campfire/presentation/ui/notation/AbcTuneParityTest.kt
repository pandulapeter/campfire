/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProAbcTunes
import kotlin.test.Test
import kotlin.test.assertEquals

/** The editor marks a renderer's report on the tunes it finds ([ChordProAbcTunes]), which must be the ones the song draws. */
class AbcTuneParityTest {

    private fun assertSameTunes(text: String) = assertEquals(
        abcTunesOf(ChordProParser.parse(text).blocks).map { it.text }.toSet(),
        ChordProAbcTunes.of(text).map { it.text }.toSet(),
        text,
    )

    @Test
    fun `a plain tune is found alike`() = assertSameTunes(
        """
        {title: A}
        {start_of_abc}
        X:1
        K:G
        GAB|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a blank line splits a block into tunes alike`() = assertSameTunes(
        """
        {start_of_abc}
        X:1
        K:G
        GAB|

        X:2
        K:D
        DEF|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a directive inside a block splits it alike`() = assertSameTunes(
        """
        {start_of_abc}
        X:1
        K:G
        GAB|
        {comment: Bridge}
        X:2
        K:D
        DEF|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a colon directive inside a block splits it alike`() = assertSameTunes(
        """
        {start_of_abc}
        X:1
        K:G
        GAB|
        {c: Bridge}
        DEF|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a block with a label is found alike`() = assertSameTunes(
        """
        {start_of_abc: Melody}
        X:1
        K:G
        GAB|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a block for one instrument is found alike`() = assertSameTunes(
        """
        {start_of_abc-piano}
        X:1
        K:G
        GAB|
        {end_of_abc-piano}
        {start_of_abc}
        X:2
        K:D
        DEF|
        {end_of_abc}
        """.trimIndent(),
    )

    @Test
    fun `a block under a verse and a chorus recall is found alike`() = assertSameTunes(
        """
        {start_of_chorus}
        [G]La la
        {end_of_chorus}
        {start_of_abc}
        X:1
        K:G
        GAB|
        {end_of_abc}
        {chorus}
        """.trimIndent(),
    )
}
