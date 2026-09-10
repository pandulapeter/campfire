/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase

class GetSongContentUseCaseImpl internal constructor(
    private val songContentRepository: SongContentRepository
) : GetSongContentUseCase {

    override suspend operator fun invoke(fileName: String) = songContentRepository.loadSongContent(fileName)
}
