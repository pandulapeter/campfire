/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.filledForm
import com.pandulapeter.campfire.data.model.song.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class DialogStackTest {

    @Test
    fun `a dialog stacks over the one it is opened from`() {
        assertEquals(listOf(info, cover), nextUnderlyingDialogs(openDialogs = listOf(info, cover), next = remove))
        assertEquals(listOf(info, form), nextUnderlyingDialogs(openDialogs = listOf(info, form), next = lookup))
    }

    @Test
    fun `a dialog in the place of an open one goes back to it, closing everything above it`() {
        val filledForm = form.copy(draft = emptyMap())

        assertEquals(listOf(info), nextUnderlyingDialogs(openDialogs = listOf(info, form, lookup), next = filledForm))
        assertEquals(emptyList(), nextUnderlyingDialogs(openDialogs = listOf(info, form, lookup), next = info))
    }

    @Test
    fun `an editor of the song info sheet replaces another one rather than stacking over it`() {
        assertEquals(listOf(info), nextUnderlyingDialogs(openDialogs = listOf(info, form), next = DialogType.SongTags(song = song, target = file)))
    }

    @Test
    fun `a dialog about another text, or about nothing that is open, replaces the stack`() {
        val draftLookup = lookup.copy(target = SongEditTarget.EditorDraft(song.fileName))

        assertEquals(emptyList(), nextUnderlyingDialogs(openDialogs = listOf(info, form), next = draftLookup))
        assertEquals(emptyList(), nextUnderlyingDialogs(openDialogs = listOf(info, form), next = DialogType.ConfirmExit))
        assertEquals(emptyList(), nextUnderlyingDialogs(openDialogs = listOf(info, form), next = null))
    }

    private val song = Song(
        fileName = "a.cho",
        title = "A",
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )
    private val file = SongEditTarget.File(song.fileName)
    private val info = DialogType.SongInfo(song = song, target = file)
    private val form = DialogType.SongMetadata(song = song, values = emptyMap(), target = file)
    private val lookup = DialogType.SongDetailsLookup(song = song, target = file, values = emptyMap(), draft = emptyMap(), formFillCount = 0)
    private val cover = DialogType.CoverArtSearch(song = song, target = file)
    private val remove = DialogType.RemoveSongCoverArt(song = song, target = file)
}
