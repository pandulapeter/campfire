/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iOS device backup's allow-list, which has to agree with the root `CLAUDE.md`'s privacy section and with
 * Android's backup rules: the songs, the setlists, the pictures, and `preferences.json`.
 */
class DeviceBackupTest {

    @Test
    fun `the songs and setlists are in the device backup and the folders of this installation are not`() {
        assertEquals(
            setOf(StorageDirectory.SONGS, StorageDirectory.SETLISTS),
            StorageDirectory.entries.filter { isInDeviceBackup(it, "a-b.cho") }.toSet() - StorageDirectory.FILES,
        )
    }

    @Test
    fun `of the preferences folder only the preferences document is in the device backup`() {
        assertTrue(isInDeviceBackup(StorageDirectory.PREFERENCES, "preferences.json"))
        listOf(
            "preferences.json.bad",
            "sync-index.json",
            "sync-credentials-forget-pending",
            "shared-links.json",
            "shared-links-journal.json",
            "editor-draft.json",
            "a-file-nobody-listed.json",
        ).forEach { assertFalse(isInDeviceBackup(StorageDirectory.PREFERENCES, it), it) }
    }

    @Test
    fun `of the library's files the pictures are in the device backup and the attachments are not`() {
        assertTrue(isInDeviceBackup(StorageDirectory.FILES, "0123456789abcdef.webp"))
        assertFalse(isInDeviceBackup(StorageDirectory.FILES, "3fa9c2e81b7d4f06.pdf"))
        assertFalse(isInDeviceBackup(StorageDirectory.FILES, "3fa9c2e81b7d4f06.jpeg"))
    }

    @Test
    fun `a name a picture and an attachment could both have counts as a picture`() {
        assertTrue(isInDeviceBackup(StorageDirectory.FILES, "0123456789abcdef.png"))
    }

    @Test
    fun `the covers and the draft pictures and the imports and exports are out of the device backup`() {
        listOf(StorageDirectory.COVERS, StorageDirectory.DRAFT_IMAGES, StorageDirectory.IMPORTS, StorageDirectory.EXPORTS).forEach {
            assertFalse(isInDeviceBackup(it, "0123456789abcdef.png"), "$it")
        }
    }
}
