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
import com.pandulapeter.campfire.domain.api.sync.SynchronizeOnReturnUseCase
import org.koin.core.annotation.Factory

@Factory
internal class SynchronizeOnReturnUseCaseImpl(
    private val syncRepository: SyncRepository,
) : SynchronizeOnReturnUseCase {

    override operator fun invoke() = syncRepository.synchronizeOnReturn()
}
