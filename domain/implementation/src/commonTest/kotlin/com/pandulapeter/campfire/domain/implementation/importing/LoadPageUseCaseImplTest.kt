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
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadPageUseCaseImplTest {

    @Test
    fun `a page is read while Pages from links is on`() = runTest {
        val pages = RecordingPageRepository()

        assertEquals(PAGE, useCase(pages, isEnabled = true)(ADDRESS))
        assertEquals(listOf(ADDRESS), pages.loaded)
    }

    @Test
    fun `nothing is asked of the page's host while Pages from links is off`() = runTest {
        val pages = RecordingPageRepository()

        assertEquals(LoadedPage.NotRead, useCase(pages, isEnabled = false)(ADDRESS))
        assertEquals(emptyList(), pages.loaded)
    }

    private fun useCase(pages: PageRepository, isEnabled: Boolean) = LoadPageUseCaseImpl(
        pageRepository = pages,
        userPreferencesRepository = object : UserPreferencesRepositoryStub() {
            override suspend fun loadUserPreferencesIfNeeded() = TEST_PREFERENCES.copy(isPageLinkLoadingEnabled = isEnabled)
        },
    )

    private class RecordingPageRepository : PageRepository {
        val loaded = mutableListOf<String>()

        override val canLoadPages = true

        override suspend fun load(address: String): LoadedPage {
            loaded += address
            return PAGE
        }
    }

    private companion object {
        const val ADDRESS = "https://chords.example.com/a"
        val PAGE = LoadedPage.Read(text = "Em G\nToday", title = "Song", address = ADDRESS)
    }
}
