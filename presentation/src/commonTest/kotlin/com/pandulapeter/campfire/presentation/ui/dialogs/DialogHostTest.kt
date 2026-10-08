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

import com.pandulapeter.campfire.data.model.domain.Song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DialogHostTest {

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

    @Test
    fun `listeners run in order around the change`() = runTest {
        val host = DialogHost(backgroundScope)
        val calls = mutableListOf<String>()
        host.addBeforeDialogChange { previous, next -> calls += "before 1 $previous $next ${host.visibleDialog.value}" }
        host.addBeforeDialogChange { _, _ -> calls += "before 2" }
        host.addAfterDialogChange { _, next -> calls += "after $next ${host.visibleDialog.value}" }
        host.showDialog(DialogType.ConfirmExit)
        assertEquals(
            listOf("before 1 null ConfirmExit null", "before 2", "after ConfirmExit ConfirmExit"),
            calls,
        )
    }

    @Test
    fun `a sheet dismisses only itself`() = runTest {
        val host = DialogHost(backgroundScope)
        host.showDialog(DialogType.UnsavedChanges)
        host.dismissSheet(DialogType.ConfirmExit)
        assertEquals(DialogType.UnsavedChanges, host.visibleDialog.value)
        host.dismissSheet(DialogType.UnsavedChanges)
        assertNull(host.visibleDialog.value)
    }

    @Test
    fun `the song info sheet stays under an edit of its own song and comes back after it`() = runTest {
        val host = DialogHost(backgroundScope)
        val info = DialogType.SongInfo(song)
        host.showDialog(info)
        host.showDialog(DialogType.SongTags(song = song, target = SongEditTarget.File(song.fileName)))
        assertEquals(info, host.underlyingSongInfo.value)
        host.dismissDialog()
        assertEquals(info, host.visibleDialog.value)
    }

    @Test
    fun `the song info sheet does not stay under an edit of the editor's text`() = runTest {
        val host = DialogHost(backgroundScope)
        host.showDialog(DialogType.SongInfo(song))
        host.showDialog(DialogType.SongTags(song = song, target = SongEditTarget.EditorDraft(song.fileName)))
        assertNull(host.underlyingSongInfo.value)
    }
}
