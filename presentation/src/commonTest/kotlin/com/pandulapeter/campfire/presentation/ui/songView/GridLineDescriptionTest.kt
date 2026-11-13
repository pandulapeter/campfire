/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songLayout.gridBarLinesOf
import kotlin.test.Test
import kotlin.test.assertEquals

class GridLineDescriptionTest {

    private val words = GridLineWords(held = "held", repeatBar = "repeat bar", repeatTwoBars = "repeat two bars") { "$it beats" }

    private fun described(line: String) = gridLineDescription(
        gridBarLinesOf(
            ChordProParser.parse("{start_of_grid}\n$line\n{end_of_grid}").blocks.filterIsInstance<ChordProBlock.Section>()
                .flatMap { it.lines }.filterIsInstance<ChordProLine.Grid>().map { it.tokens },
        ).single(),
        words,
    )

    @Test
    fun `a grid line is read as chords and beats, not bar characters`() {
        assertEquals(
            "Intro; C, 4 beats; Dm, 2 beats, Am, 2 beats; held, 4 beats; repeat bar; x2",
            described("Intro |: C . . . | Dm . Am . | | % :| x2"),
        )
    }
}
