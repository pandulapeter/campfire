/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.EditedSongs

/** See `SongRepository.editSongs`: the edit of many songs at once that announces itself to the library once. */
interface EditSongsUseCase {

    /** Applies [edit] to each file as it is on disk, writes the ones it changed and announces them together. */
    suspend operator fun invoke(fileNames: List<String>, edit: (text: String) -> String): EditedSongs
}
