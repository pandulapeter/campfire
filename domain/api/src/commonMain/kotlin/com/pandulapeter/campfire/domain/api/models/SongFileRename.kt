/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.models

/**
 * What [com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase] did: the file is under [fileName] from
 * now on either way, since a move is not undone.
 *
 * @param haveReferencesFollowed False where a setlist or the saved transposition could not be rewritten to the new
 *   name and still points at the old one - a setlist then shows the song as missing until the entry is fixed by hand.
 */
data class SongFileRename(
    val fileName: String,
    val haveReferencesFollowed: Boolean,
)
