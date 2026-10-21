/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.library

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.library.GetSharedLinkImportUseCase
import org.koin.core.annotation.Factory

@Factory
internal class GetSharedLinkImportUseCaseImpl(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val logger: Logger,
) : GetSharedLinkImportUseCase {

    override suspend operator fun invoke(linkFileId: String): Set<String> = logger.recovering(
        describe = { "Could not read a shared link's earlier imports: ${it.message}" },
        fallback = { emptySet() },
    ) {
        userPreferencesRepository.loadUserPreferencesIfNeeded()?.sharedLinkImports?.get(linkFileId)?.toSet().orEmpty()
    }
}
