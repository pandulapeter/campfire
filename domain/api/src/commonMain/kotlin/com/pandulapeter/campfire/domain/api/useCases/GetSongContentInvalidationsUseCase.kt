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
     * The file name of every song whose text may have changed on disk since it was read, or null for every song (a
     * rescan, which is also how a sync run ends). Whoever keeps a copy of a text read through [GetSongContentUseCase]
     * re-reads it, or it goes on showing - and building writes on - a version of the file that is not there any more.
     */
    operator fun invoke(): Flow<String?>
}
