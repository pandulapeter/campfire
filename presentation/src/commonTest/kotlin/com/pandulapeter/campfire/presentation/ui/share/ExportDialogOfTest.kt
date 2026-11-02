/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExportDialogOfTest {

    @Test
    fun `a shared song opens the export screen on the library's song of that name, case and form folded`() {
        val song = song("Café.cho")
        assertEquals(
            DialogType.Export(song = song, format = PrintSettings.Format.LINK),
            exportDialogOf(share(SharedLinkKind.SONG, "café.cho"), songsByFileName = mapOf(song.fileName to song), setlists = emptyList()),
        )
        val setlist = setlist("Gig.setlist.json")
        assertEquals(
            DialogType.Export(setlist = setlist, format = PrintSettings.Format.LINK),
            exportDialogOf(share(SharedLinkKind.SETLIST, "gig.setlist.json"), songsByFileName = emptyMap(), setlists = listOf(setlist)),
        )
    }

    @Test
    fun `a share whose file is gone opens nothing`() {
        val song = song("other.cho")
        assertNull(exportDialogOf(share(SharedLinkKind.SONG, "song.cho"), songsByFileName = mapOf(song.fileName to song), setlists = emptyList()))
        assertNull(exportDialogOf(share(SharedLinkKind.SETLIST, "gig.setlist.json"), songsByFileName = emptyMap(), setlists = listOf(setlist("other.setlist.json"))))
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    private fun setlist(fileName: String) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0,
    )

    private fun share(kind: SharedLinkKind, fileName: String) = Share(
        shareId = fileName,
        kind = kind,
        fileName = fileName,
        title = fileName,
        link = assertNotNull(ShareLinks.create(kind, shareId = "aaaaaaaaaaaaaaaa", fileId = "file", rlkey = "key", title = fileName)),
        sharedAt = null,
        state = ShareState.OK,
    )
}
