/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.setlists

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.domain.api.setlists.DeleteSetlistUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class DeleteSetlistUseCaseImpl(
    private val setlistRepository: SetlistRepository,
    private val sharedLinksRepository: SharedLinksRepository,
) : DeleteSetlistUseCase {

    /** A setlist shared as a link stops being shared, once the file is gone, whatever happens to the screen meanwhile. */
    override suspend operator fun invoke(fileName: String) {
        setlistRepository.deleteSetlist(fileName)
        withContext(NonCancellable) { sharedLinksRepository.stopSharingDeleted(SharedLinkKind.SETLIST, fileName) }
    }
}
