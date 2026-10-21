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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RememberSharedLinkImportUseCaseImplTest {

    private val logged = mutableListOf<String>()

    private val repository = object : UserPreferencesRepositoryStub() {
        var current = TEST_PREFERENCES

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
            current = transform(current)
        }
    }

    private val useCase = RememberSharedLinkImportUseCaseImpl(repository, Logger { logged += it })

    @Test
    fun `a link imported again keeps what it wrote before`() = runTest {
        useCase(linkFileId = "link", songFileNames = listOf("a.cho"), setlistFileNames = emptyList())
        useCase(linkFileId = "link", songFileNames = listOf("b.cho"), setlistFileNames = listOf("gig.setlist.json"))
        assertEquals(setOf("songs/a.cho", "songs/b.cho", "setlists/gig.setlist.json"), repository.current.sharedLinkImports.getValue("link").toSet())
    }

    @Test
    fun `a name written again under another spelling is kept once, as last written`() = runTest {
        useCase(linkFileId = "link", songFileNames = listOf("Riverlight.cho"), setlistFileNames = emptyList())
        useCase(linkFileId = "link", songFileNames = listOf("riverlight.cho"), setlistFileNames = emptyList())
        assertEquals(listOf("songs/riverlight.cho"), repository.current.sharedLinkImports.getValue("link"))
    }

    @Test
    fun `the oldest link is forgotten past the limit, and one imported again is the newest`() = runTest {
        repeat(MAX_SHARED_LINK_IMPORTS) { useCase(linkFileId = "link$it", songFileNames = listOf("a.cho"), setlistFileNames = emptyList()) }
        useCase(linkFileId = "link0", songFileNames = listOf("b.cho"), setlistFileNames = emptyList())
        useCase(linkFileId = "another", songFileNames = listOf("a.cho"), setlistFileNames = emptyList())
        val links = repository.current.sharedLinkImports.keys
        assertEquals(MAX_SHARED_LINK_IMPORTS, links.size)
        assertFalse("link1" in links)
        assertEquals(listOf("link0", "another"), links.toList().takeLast(2))
    }

    @Test
    fun `one link keeps only its most recent names past the limit`() = runTest {
        useCase(linkFileId = "link", songFileNames = (0..MAX_SHARED_LINK_IMPORT_PATHS).map { "song$it.cho" }, setlistFileNames = emptyList())
        val paths = repository.current.sharedLinkImports.getValue("link")
        assertEquals(MAX_SHARED_LINK_IMPORT_PATHS, paths.size)
        assertEquals("songs/song1.cho", paths.first())
    }

    @Test
    fun `a record that cannot be written is logged rather than thrown`() = runTest {
        val failing = object : UserPreferencesRepositoryStub() {
            override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) = throw IllegalStateException("Disk full")
        }
        RememberSharedLinkImportUseCaseImpl(failing, Logger { logged += it })(linkFileId = "link", songFileNames = listOf("a.cho"), setlistFileNames = emptyList())
        assertEquals(1, logged.size)
    }
}
