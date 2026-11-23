/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.data.model.importing.LoadedPage

interface LoadPageUseCase {

    /**
     * Reads the page at [address] once on the device, for a song shared or pasted as its link, and answers its text, or
     * [LoadedPage.NotRead] where it cannot: the platform has no web view ([CanLoadPagesUseCase]), Pages from links is
     * switched off (`UserPreferences.isPageLinkLoadingEnabled`), or the page did not come to anything worth converting;
     * its reason tells a page that never arrived from one that is nothing to read.
     * Nothing of the page is kept. Only a cancellation is thrown.
     */
    suspend operator fun invoke(address: String): LoadedPage
}
