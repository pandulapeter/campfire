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

import com.pandulapeter.campfire.abc.api.AbcMessage
import com.pandulapeter.campfire.chordpro.edit.ChordProAbcTunes
import kotlin.test.Test
import kotlin.test.assertEquals

class AbcProblemsTest {

    @Test
    fun `each message is marked on the token it names in the song, one without a line nowhere`() {
        val problems = problemsOf(SONG, AbcMessage("Bad character 'n'", line = 2, column = 11), AbcMessage("Unknown", line = null, column = null))
        assertEquals(listOf("{end_of_abc}"), problems.rangesIn(SONG).map { SONG.substring(it) })
    }

    @Test
    fun `problems are kept above an edit made since the check, moved below it, and dropped inside it`() {
        val problems = problemsOf(SONG, AbcMessage("Bad", line = 0, column = 0), AbcMessage("Bad", line = 2, column = 11), AbcMessage("Bad", line = 2, column = 2))
        val edited = SONG.replace("C D E", "C Xy E")
        assertEquals(listOf("X:1", "{end_of_abc}"), problems.rangesIn(edited).map { edited.substring(it) })
    }

    private fun problemsOf(text: String, vararg messages: AbcMessage) = AbcProblems.of(text, ChordProAbcTunes.of(text)) { messages.toList() }

    private companion object {
        const val SONG = "{title: T}\n{start_of_abc}\nX:1\nK:C\nC D E F|]{end_of_abc}\n"
    }
}
