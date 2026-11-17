/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class OrphanedPicturesPlaceTest {

    private val pick = MissingImagePick(fileName = "b.cho", missingName = "photo.jpg")
    private val setlistPages = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = "s.setlist.json", initialIndex = 0)

    @Test
    fun `pictures go into the editor on top of the back stack`() {
        assertEquals(
            OrphanedPicturesPlace.Editor("a.cho"),
            orphanedPicturesPlace(top = CampfireDestination.SongEditor("a.cho"), missingImagePick = pick, isReadOnly = false),
        )
    }

    @Test
    fun `a picture goes into the missing picture it was picked for, on any page of the song screen on top holding its song`() {
        assertEquals(OrphanedPicturesPlace.MissingImage(pick), orphanedPicturesPlace(top = setlistPages, missingImagePick = pick, isReadOnly = false))
    }

    @Test
    fun `a picture with no pick under way, or picked for a song the screen on top does not hold, is let go`() {
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = setlistPages, missingImagePick = null, isReadOnly = false))
        assertEquals(
            OrphanedPicturesPlace.Nowhere,
            orphanedPicturesPlace(top = setlistPages, missingImagePick = pick.copy(fileName = "c.cho"), isReadOnly = false),
        )
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = CampfireDestination.Settings, missingImagePick = pick, isReadOnly = false))
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = null, missingImagePick = pick, isReadOnly = false))
    }

    @Test
    fun `an import preview takes no picture, even one naming a file of its own`() {
        val preview = setlistPages.copy(importPreview = ImportPreview(songIndices = listOf(0), planSongCount = 1, isReviewPager = false, reviewId = "review"))
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = preview, missingImagePick = pick, isReadOnly = false))
    }

    @Test
    fun `nothing is put anywhere in read only mode`() {
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = CampfireDestination.SongEditor("a.cho"), missingImagePick = pick, isReadOnly = true))
        assertEquals(OrphanedPicturesPlace.Nowhere, orphanedPicturesPlace(top = setlistPages, missingImagePick = pick, isReadOnly = true))
    }

    private val song = Song(
        fileName = "b.cho", title = "B", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )
    private val songDetails = CampfireDestination.SongDetails(songFileNames = listOf("b.cho"), setlistFileName = null, initialIndex = 0)

    private fun attachmentsSheet(target: SongEditTarget) = DialogType.SongAttachments(song = song, attachments = emptyList(), target = target)

    private fun attachmentPlace(
        top: CampfireDestination?,
        openDialogs: List<DialogType> = emptyList(),
        isReadOnly: Boolean = false,
        areAttachmentsEnabled: Boolean = true,
    ) = orphanedAttachmentPlace(top = top, openDialogs = openDialogs, isReadOnly = isReadOnly, areAttachmentsEnabled = areAttachmentsEnabled)

    @Test
    fun `an attachment goes into the topmost Attachments sheet, over the editor too`() {
        val file = SongEditTarget.File("b.cho")
        val draft = SongEditTarget.EditorDraft("a.cho")
        assertEquals(OrphanedAttachmentPlace.Sheet(file), attachmentPlace(top = songDetails, openDialogs = listOf(DialogType.SongInfo(song, file), attachmentsSheet(file))))
        assertEquals(OrphanedAttachmentPlace.Sheet(draft), attachmentPlace(top = CampfireDestination.SongEditor("a.cho"), openDialogs = listOf(attachmentsSheet(draft))))
    }

    @Test
    fun `an attachment with no Attachments sheet open goes into the editor on top, and nowhere else`() {
        assertEquals(OrphanedAttachmentPlace.Editor("a.cho"), attachmentPlace(top = CampfireDestination.SongEditor("a.cho")))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = songDetails))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = null))
    }

    @Test
    fun `no attachment is put anywhere in read only mode or with the attachments switched off`() {
        val sheet = listOf(attachmentsSheet(SongEditTarget.File("b.cho")))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = songDetails, openDialogs = sheet, isReadOnly = true))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = CampfireDestination.SongEditor("a.cho"), isReadOnly = true))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = songDetails, openDialogs = sheet, areAttachmentsEnabled = false))
        assertEquals(OrphanedAttachmentPlace.Nowhere, attachmentPlace(top = CampfireDestination.SongEditor("a.cho"), areAttachmentsEnabled = false))
    }
}
