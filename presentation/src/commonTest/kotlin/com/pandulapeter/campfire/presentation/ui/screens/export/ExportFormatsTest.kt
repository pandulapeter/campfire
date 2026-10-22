/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportFormatsTest {

    private val songExport = DialogType.Export(song = song("a.cho"))
    private val setlistExport = DialogType.Export(setlist = setlist("set.setlist.json"))
    private val songsExport = DialogType.Export(songs = listOf(song("a.cho"), song("b.cho")))

    @Test
    fun `a song or a setlist is offered as a link where sync is available, the link first and the pdf last`() {
        val all = listOf(PrintSettings.Format.LINK, PrintSettings.Format.FILES, PrintSettings.Format.PDF)
        assertEquals(all, exportFormatsOf(songExport, isSyncAvailable = true))
        assertEquals(all, exportFormatsOf(setlistExport, isSyncAvailable = true))
    }

    @Test
    fun `several songs, or a build without sync, are not offered as a link`() {
        val files = listOf(PrintSettings.Format.FILES, PrintSettings.Format.PDF)
        assertEquals(files, exportFormatsOf(songsExport, isSyncAvailable = true))
        assertEquals(files, exportFormatsOf(songExport, isSyncAvailable = false))
    }

    @Test
    fun `a link left as the format opens an export that does not offer it on the pdf`() {
        val link = PrintSettings(format = PrintSettings.Format.LINK, fontSize = 14)
        assertEquals(PrintSettings(format = PrintSettings.Format.PDF, fontSize = 14), link.withFormatIn(exportFormatsOf(songsExport, isSyncAvailable = true)))
        assertEquals(link, link.withFormatIn(exportFormatsOf(songExport, isSyncAvailable = true)))
    }

    @Test
    fun `an export opened on a format of its own takes it over the stored one, which it leaves as it is`() {
        val stored = PrintSettings(format = PrintSettings.Format.PDF, fontSize = 14)
        val formats = exportFormatsOf(songExport, isSyncAvailable = true)
        assertEquals(stored.copy(format = PrintSettings.Format.LINK), openingSettingsOf(stored, songExport.copy(format = PrintSettings.Format.LINK), pickedFormat = null, formats))
    }

    @Test
    fun `an export opened on no format of its own opens on the stored one`() {
        val stored = PrintSettings(format = PrintSettings.Format.FILES, fontSize = 14)
        assertEquals(stored, openingSettingsOf(stored, songExport, pickedFormat = null, exportFormatsOf(songExport, isSyncAvailable = true)))
    }

    @Test
    fun `a format the export does not offer opens it on the pdf, whoever named it`() {
        val stored = PrintSettings(format = PrintSettings.Format.FILES, fontSize = 14)
        val formats = exportFormatsOf(songExport, isSyncAvailable = false)
        assertEquals(stored.copy(format = PrintSettings.Format.PDF), openingSettingsOf(stored, songExport.copy(format = PrintSettings.Format.LINK), pickedFormat = null, formats))
    }

    @Test
    fun `a format picked on the screen outlives it being composed again over the one it opened on`() {
        val stored = PrintSettings(format = PrintSettings.Format.LINK, fontSize = 14)
        assertEquals(
            stored.copy(format = PrintSettings.Format.PDF),
            openingSettingsOf(stored, songExport.copy(format = PrintSettings.Format.LINK), pickedFormat = PrintSettings.Format.PDF, exportFormatsOf(songExport, isSyncAvailable = true)),
        )
    }

    @Test
    fun `a link shares the song or the setlist by its file, the song reached through a setlist included`() {
        assertEquals(ShareTarget(SharedLinkKind.SONG, "a.cho"), shareTargetOf(songExport.copy(songSetlistFileName = "set.setlist.json")))
        assertEquals(ShareTarget(SharedLinkKind.SETLIST, "set.setlist.json"), shareTargetOf(setlistExport))
        assertNull(shareTargetOf(songsExport))
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
}
