/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three things a preferences document can be on disk - absent, damaged, unreadable - and the one of them that
 * is allowed to mean the defaults.
 */
class UserPreferencesLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-preferences").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val localSource = UserPreferencesLocalSourceImpl(fileStorage)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a missing document is the defaults and keeps no copy`() = runBlocking {
        assertEquals(UserPreferencesDocument().toModel(), localSource.loadUserPreferences())
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, FILE_NAME))
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME))
        assertFalse(localSource.hasStoredUserPreferences())
    }

    @Test
    fun `a damaged document keeps what it can and is copied aside`() = runBlocking {
        val text = """{"fontScale": "big", "transpositions": {"a.cho": 2}}"""
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, text)

        val preferences = localSource.loadUserPreferences()

        assertEquals(mapOf("a.cho" to 2), preferences.transpositions)
        assertContentEquals(text.encodeToByteArray(), fileStorage.readBytes(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME))
        assertEquals(text, fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME))
        assertTrue(localSource.hasStoredUserPreferences())
    }

    @Test
    fun `a document that is intact is not copied`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, """{"fontScale": 1.5}""")

        assertEquals(1.5f, localSource.loadUserPreferences().fontScale)
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME))
    }

    @Test
    fun `a text size outside the app's range is loaded as the nearest one inside it`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, """{"fontScale": 40}""")

        assertEquals(2.5f, localSource.loadUserPreferences().fontScale)
    }

    @Test
    fun `an empty document is not copied`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, "")

        assertEquals(UserPreferencesDocument().toModel(), localSource.loadUserPreferences())
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME))
    }

    @Test
    fun `a document that cannot be read is not the defaults`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, """{"fontScale": 1.5}""")
        val file = root.walk().first { it.name == FILE_NAME }
        file.setReadable(false)
        // Permissions mean nothing to a superuser, and the case cannot be set up there.
        if (file.canRead()) return@runBlocking

        assertFailsWith<LibraryStorageException> { localSource.loadUserPreferences() }
        file.setReadable(true)
        assertNull(fileStorage.readBytes(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME))
    }

    private companion object {
        const val FILE_NAME = "preferences.json"
        const val UNREADABLE_FILE_NAME = "preferences.json.bad"
    }
}
