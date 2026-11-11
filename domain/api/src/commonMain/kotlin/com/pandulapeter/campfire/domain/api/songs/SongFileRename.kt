/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.songs

/**
 * What [com.pandulapeter.campfire.domain.api.songs.RenameSongFileUseCase] did: the song is under [fileName] from
 * now on either way, since a rename is not undone.
 *
 * @param haveReferencesFollowed False where a setlist or the saved transposition could not be rewritten to the new
 *   name and still points at the old one. The old file is then kept beside the new one, so that what still names it
 *   finds the song: the library has it twice until the user deletes one or fixes the entry by hand.
 */
data class SongFileRename(
    val fileName: String,
    val haveReferencesFollowed: Boolean,
)
