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

import com.pandulapeter.campfire.data.model.image.ImageRefusal

interface SaveSongImageUseCase {

    /**
     * Stores [bytes] as the picture the song file [fileName] names [missingName] but the library lacks, and writes the
     * song with every image directive naming [missingName] pointed at the name it was stored under, both in one step, so
     * that the picture is never pruned before the song naming it is written. The song is written only over
     * [expectedText], the text the caller read, every other byte of it kept as it was. Once started, it finishes even if
     * the caller is cancelled.
     */
    suspend operator fun invoke(fileName: String, expectedText: String, missingName: String, bytes: ByteArray): SaveSongImageResult
}

/** What became of a picture handed to [SaveSongImageUseCase]. */
sealed interface SaveSongImageResult {

    /** Stored, and the song written as [text]. */
    data class Added(val text: String) : SaveSongImageResult

    /** Not a picture the library keeps, for [reason]; nothing was written. */
    data class Refused(val reason: ImageRefusal) : SaveSongImageResult

    /**
     * The song's file no longer holds the text the caller read, so it was left alone, for the caller to read it again
     * and retry. The picture may have been stored, and goes with the next pruning unless a song names it by then.
     */
    data object SongChanged : SaveSongImageResult
}
