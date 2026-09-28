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

import kotlinx.coroutines.flow.Flow

interface GetSongContentInvalidationsUseCase {

    /**
     * Changes whenever the text of a song may have changed on disk since it was read - a save, a rescan, a sync run.
     * Whoever keeps copies of texts read through [GetSongContentUseCase] re-reads all of them, or they go on showing -
     * and building writes on - versions of files that are not there any more. It names no file and may skip values in
     * between, see `SongContentRepository.invalidations`.
     */
    operator fun invoke(): Flow<Long>
}
