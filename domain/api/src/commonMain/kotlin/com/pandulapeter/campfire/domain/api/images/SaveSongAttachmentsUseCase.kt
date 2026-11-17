/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.images

import com.pandulapeter.campfire.chordpro.model.ChordProAttachment

interface SaveSongAttachmentsUseCase {

    /**
     * Makes [attachments] the attachments of the song file [fileName], in their order: the files among them the draft
     * shelf holds ([AddDraftAttachmentUseCase]) are moved into the library and the song is written with its attachment
     * lines set (`ChordProAttachments.setAttachments`), both under one hold of the library's pruning, so that no file is
     * deleted before the song naming it is written. A file that had to take another name in the library is named so in
     * the text. The song is written only over [expectedText], the text the caller read, every other byte of it kept. Once
     * started, it finishes even if the caller is cancelled.
     */
    suspend operator fun invoke(fileName: String, expectedText: String, attachments: List<ChordProAttachment>): SaveSongAttachmentsResult
}

/** What became of the attachments handed to [SaveSongAttachmentsUseCase]. */
sealed interface SaveSongAttachmentsResult {

    /** The song was written as [text]. */
    data class Saved(val text: String) : SaveSongAttachmentsResult

    /**
     * The song's file no longer holds the text the caller read, so it was left alone, for the caller to read it again and
     * retry. Files moved into the library go with the next pruning unless a song names them by then.
     */
    data object SongChanged : SaveSongAttachmentsResult
}
