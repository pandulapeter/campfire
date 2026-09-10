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

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase

class CreateSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository
) : CreateSetlistUseCase {

    /** The newest setlist goes on top, so it gets a priority above every existing one. */
    override suspend operator fun invoke(title: String) = setlistRepository.createSetlist(
        title = title.trim(),
        priority = (setlistRepository.loadSetlistsIfNeeded().orEmpty().maxOfOrNull { it.priority } ?: -1) + 1
    )
}
