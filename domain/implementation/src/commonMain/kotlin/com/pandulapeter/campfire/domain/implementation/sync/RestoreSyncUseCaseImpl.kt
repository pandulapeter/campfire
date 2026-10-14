/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.sync

import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.domain.api.sync.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeLibraryUseCase
import org.koin.core.annotation.Factory

@Factory
internal class RestoreSyncUseCaseImpl(
    private val syncRepository: SyncRepository,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
) : RestoreSyncUseCase {

    override suspend operator fun invoke(): Boolean {
        val result = syncRepository.restore()
        // A run that was cut short is reported rather than repeated: an automatic run would overwrite the message
        // before it could be read, and the button that starts one is right under it.
        if (result.isConnected && !result.wasInterrupted) {
            synchronizeLibrary()
        }
        return result.didReturnFromAuthorization
    }
}
