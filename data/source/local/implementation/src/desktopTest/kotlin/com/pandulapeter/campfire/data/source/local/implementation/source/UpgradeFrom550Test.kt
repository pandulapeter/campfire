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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Loads the `preferences.json` Campfire 5.5.0 wrote (`resources/upgrade-5.5.0`, written by that version's own source and
 * mappers, with Pictures from links and Attachments switched off), which predates the Pages from links switch, which
 * starts on.
 */
class UpgradeFrom550Test {

    private val root: File = Files.createTempDirectory("campfire-upgrade").toFile().also { root ->
        File(requireNotNull(javaClass.getResource("/upgrade-5.5.0")).toURI()).copyRecursively(root)
    }
    private val fileStorage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a 5_5_0 user has pages from links on and keeps every preference they set`() = runBlocking {
        val preferences = UserPreferencesLocalSourceImpl(fileStorage, Logger.Standard).loadUserPreferences()

        assertEquals(
            UserPreferencesDocument().toModel().copy(
                fontScale = 1.2f,
                themeColor = UserPreferences.ThemeColor.TEAL,
                seenWhatsNewVersions = setOf("5.4.0", "5.5.0"),
                transpositions = mapOf("oasis-wonderwall.cho" to 2),
                isPictureLinkDownloadEnabled = false,
                isAttachmentsEnabled = false,
                isPageLinkLoadingEnabled = true,
            ),
            preferences,
        )
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, "preferences.json.bad"))
    }
}
