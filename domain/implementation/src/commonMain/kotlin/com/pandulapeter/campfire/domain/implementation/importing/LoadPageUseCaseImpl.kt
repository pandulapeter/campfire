/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.LoadedPage
import com.pandulapeter.campfire.data.repository.api.PageRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.importing.LoadPageUseCase
import org.koin.core.annotation.Factory

/** Asks the switch again rather than trusting the caller, so that nothing reaches a host the user switched off. */
@Factory
internal class LoadPageUseCaseImpl(
    private val pageRepository: PageRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LoadPageUseCase {

    override suspend operator fun invoke(address: String) =
        if (userPreferencesRepository.loadUserPreferencesIfNeeded()?.isPageLinkLoadingEnabled == false) {
            LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
        } else {
            pageRepository.load(address)
        }
}
