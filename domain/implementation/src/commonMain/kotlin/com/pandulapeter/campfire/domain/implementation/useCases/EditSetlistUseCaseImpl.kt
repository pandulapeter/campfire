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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.EditSetlistUseCase

class EditSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
) : EditSetlistUseCase {

    // The description travels inside the setlist that is handed over, since the rename is only ever about the title.
    override suspend operator fun invoke(setlist: Setlist, title: String, description: String): Setlist =
        setlistRepository.renameSetlist(setlist = setlist.copy(description = description.trim()), title = title.trim())
}
