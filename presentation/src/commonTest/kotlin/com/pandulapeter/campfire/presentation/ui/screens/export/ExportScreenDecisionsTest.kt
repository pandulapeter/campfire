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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportScreenDecisionsTest {

    private val setlist = PrintSource(title = "Set", kind = PrintSource.Kind.SETLIST, songs = listOf(song("a.cho"), song("b.cho")))

    @Test
    fun `a failure shows over everything, a layout failure only for the PDF`() {
        assertEquals(PrintScreenContent.FAILED, exportContentOf(isFailed = true, isLayoutFailed = false, isPdf = false, hasSource = true))
        assertEquals(PrintScreenContent.FAILED, exportContentOf(isFailed = false, isLayoutFailed = true, isPdf = true, hasSource = true))
        assertEquals(PrintScreenContent.LOADED, exportContentOf(isFailed = false, isLayoutFailed = true, isPdf = false, hasSource = true))
        assertEquals(PrintScreenContent.LOADING, exportContentOf(isFailed = false, isLayoutFailed = false, isPdf = true, hasSource = false))
    }

    @Test
    fun `every song of a setlist chosen hands its manifest out as it is`() {
        assertNull(chosenSongFileNames(setlist, selected = setOf(0, 1)))
        assertEquals(setOf("b.cho"), chosenSongFileNames(setlist, selected = setOf(1)))
        assertNull(chosenSongFileNames(PrintSource(title = "Song", songs = listOf(song("a.cho"))), selected = emptySet()))
    }

    @Test
    fun `a setlist's files want a song chosen unless it has none`() {
        assertTrue(canExportFiles(setlist, selected = setOf(0)))
        assertFalse(canExportFiles(setlist, selected = emptySet()))
        assertTrue(canExportFiles(setlist.copy(songs = emptyList()), selected = emptySet()))
        assertFalse(canExportFiles(setlist.copy(kind = PrintSource.Kind.SONGS), selected = emptySet()))
        assertFalse(canExportFiles(null, selected = emptySet()))
    }

    @Test
    fun `a song's file is exported only once it has been read`() {
        assertTrue(canExportFiles(PrintSource(title = "Song", songs = listOf(song("a.cho", text = "{title: A}"))), selected = emptySet()))
        assertFalse(canExportFiles(PrintSource(title = "Song", songs = listOf(song("a.cho"))), selected = setOf(0)))
    }

    @Test
    fun `the options stand beside the preview where there is room`() {
        val roomy = assertNotNull(optionsWidthFor(bodyWidth = 800.dp, bodyHeight = 600.dp))
        val onItsSide = assertNotNull(optionsWidthFor(bodyWidth = 800.dp, bodyHeight = 400.dp))
        assertTrue(onItsSide < roomy)
        assertTrue(roomy < 800.dp / 2)
        val narrowest = assertNotNull(optionsWidthFor(bodyWidth = 520.dp, bodyHeight = 300.dp))
        assertTrue(narrowest <= 520.dp / 2)
        assertNull(optionsWidthFor(bodyWidth = 400.dp, bodyHeight = 800.dp))
    }

    @Test
    fun `the page leaves room where controls float over it`() {
        assertEquals(PageFit(top = FLOATING_CONTROLS_CLEARANCE), pageFitFor(isSideBySide = false, isShortWindow = true, floatingControlsWidth = 100.dp, bottomInset = 10.dp))
        assertEquals(
            PageFit(end = 100.dp + PAGE_MARGIN * 2, bottom = 10.dp + PAGE_MARGIN),
            pageFitFor(isSideBySide = true, isShortWindow = true, floatingControlsWidth = 100.dp, bottomInset = 10.dp),
        )
        assertEquals(
            PageFit(top = FLOATING_CONTROLS_CLEARANCE, bottom = 10.dp + FLOATING_CONTROLS_CLEARANCE),
            pageFitFor(isSideBySide = true, isShortWindow = false, floatingControlsWidth = 100.dp, bottomInset = 10.dp),
        )
    }

    @Test
    fun `the qr code is as large as it reads best where the pane has room`() {
        assertEquals(240.dp, qrCodeSizeFor(paneWidth = 600.dp, paneHeight = 600.dp))
    }

    @Test
    fun `a short pane shrinks the qr code to show it whole`() {
        val size = qrCodeSizeFor(paneWidth = 600.dp, paneHeight = 200.dp)
        assertTrue(size < 240.dp)
        assertEquals(200.dp - SHARED_LINK_VERTICAL_PADDING * 2, size)
    }

    @Test
    fun `a narrow pane keeps the qr code within its width`() {
        assertEquals(200.dp - SHARED_LINK_HORIZONTAL_PADDING * 2, qrCodeSizeFor(paneWidth = 200.dp, paneHeight = 600.dp))
        assertEquals(100.dp - SHARED_LINK_HORIZONTAL_PADDING * 2, qrCodeSizeFor(paneWidth = 100.dp, paneHeight = 50.dp))
    }

    @Test
    fun `a pane too short for a readable qr code scrolls it at its smallest`() {
        assertEquals(QR_CODE_MIN_WIDTH, qrCodeSizeFor(paneWidth = 600.dp, paneHeight = 60.dp))
    }

    @Test
    fun `only the shared link in a short window beside the options takes an end column instead of a bottom band`() {
        assertEquals(
            PaddingValues(end = FLOATING_CONTROLS_CLEARANCE, bottom = 10.dp + PAGE_MARGIN),
            linkPreviewPaddingFor(isSideBySide = true, isShortWindow = true, isShared = true, bottomInset = 10.dp),
        )
        val band = PaddingValues(bottom = 10.dp + FLOATING_CONTROLS_CLEARANCE)
        assertEquals(band, linkPreviewPaddingFor(isSideBySide = true, isShortWindow = true, isShared = false, bottomInset = 10.dp))
        assertEquals(band, linkPreviewPaddingFor(isSideBySide = true, isShortWindow = false, isShared = true, bottomInset = 10.dp))
        assertEquals(PaddingValues(), linkPreviewPaddingFor(isSideBySide = false, isShortWindow = true, isShared = true, bottomInset = 10.dp))
    }

    @Test
    fun `the title says what is exported`() {
        assertEquals(Res.string.songs_export_song, exportTitleOf(DialogType.Export()))
        assertEquals(
            Res.string.setlists_export,
            exportTitleOf(
                DialogType.Export(
                    setlist = Setlist(fileName = "set.setlist.json", title = "Set", description = "", date = LocalDate(2026, 1, 1), isArchived = false, entries = emptyList(), size = 0),
                ),
            ),
        )
    }

    private fun song(fileName: String, text: String? = null) = PrintSong(fileName = fileName, title = fileName, artist = null, song = null, text = text)
}
