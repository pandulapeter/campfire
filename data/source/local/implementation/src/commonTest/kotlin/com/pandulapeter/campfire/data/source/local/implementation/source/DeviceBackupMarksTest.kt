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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the iOS device backup copies of the files the local sources write: iOS backs up whole directories, so each
 * source marks what the root `CLAUDE.md`'s privacy section says no backup copies, after every write. Android's backup
 * is an allow-list of the songs, the setlists, and `preferences.json`, and the two have to agree. `exports/` and
 * `imports/` are left out of the backup as folders, and the sync credentials live in the Keychain.
 */
class DeviceBackupMarksTest {

    private val storage = InMemoryFileStorage()

    @Test
    fun `of the preferences folder only the preferences reach the device backup`() = runTest {
        storage.writeText(StorageDirectory.PREFERENCES, "preferences.json", "{ not a document")
        val preferences = UserPreferencesLocalSourceImpl(storage, Logger.Standard)
        preferences.saveUserPreferences(preferences.loadUserPreferences())
        SyncIndexLocalSourceImpl(storage).run {
            saveSyncIndex("{}")
            setForgettingCredentialsOwed(true)
        }
        SharedLinksLocalSourceImpl(storage).run {
            saveSharedLinksMirror("{}")
            saveSharedLinksJournal("{}")
        }
        EditorDraftLocalSourceImpl(storage, Logger.Standard).saveEditorDraft(SongContent(fileName = "a-b.cho", text = "{title: B}"))

        assertEquals(
            setOf(
                "preferences.json.bad",
                "sync-index.json",
                "sync-credentials-forget-pending",
                "shared-links.json",
                "shared-links-journal.json",
                "editor-draft.json",
            ),
            storage.listNames(StorageDirectory.PREFERENCES).toSet() - "preferences.json",
        )
        assertEquals(listOf("preferences.json"), storage.namesInDeviceBackup(StorageDirectory.PREFERENCES))
    }

    @Test
    fun `cover copies and the draft's pictures never reach the device backup`() = runTest {
        CoverArtLocalSourceImpl(storage, Logger.Standard).saveCoverArt(COVER_KEY, byteArrayOf(1))
        ImageLocalSourceImpl(storage, ImportStaging(storage, Logger.Standard)).writeImage(ImageShelf.DRAFT, PICTURE, byteArrayOf(2))

        assertEquals(listOf(COVER_KEY), storage.listNames(StorageDirectory.COVERS))
        assertEquals(emptyList(), storage.namesInDeviceBackup(StorageDirectory.COVERS))
        assertEquals(emptyList(), storage.namesInDeviceBackup(StorageDirectory.DRAFT_IMAGES))
    }

    @Test
    fun `of the library's files only the pictures reach the device backup by whichever source writes them`() = runTest {
        ImageLocalSourceImpl(storage, ImportStaging(storage, Logger.Standard)).run {
            writeImage(ImageShelf.LIBRARY, PICTURE, byteArrayOf(1))
            writeImage(ImageShelf.LIBRARY, ATTACHMENT, byteArrayOf(2))
        }
        LibraryFileLocalSourceImpl(storage).run {
            writeLibraryFile(LibraryCollections.FILES, OTHER_PICTURE, byteArrayOf(3))
            writeLibraryFile(LibraryCollections.FILES, OTHER_ATTACHMENT, byteArrayOf(4))
        }

        assertEquals(setOf(PICTURE, OTHER_PICTURE), storage.namesInDeviceBackup(StorageDirectory.FILES).toSet())
    }

    private companion object {
        const val COVER_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val PICTURE = "0123456789abcdef.png"
        const val OTHER_PICTURE = "0123456789abcdef0.jpg"
        const val ATTACHMENT = "3fa9c2e81b7d4f06.pdf"
        const val OTHER_ATTACHMENT = "3fa9c2e81b7d4f07.mp3"
    }
}
