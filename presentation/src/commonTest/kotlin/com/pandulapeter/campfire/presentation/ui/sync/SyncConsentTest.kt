/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingStep
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncConsentTest {

    private val exportOrigin = SyncConsentOrigin.Export(ShareTarget(SharedLinkKind.SETLIST, "set.setlist.json"))

    private val origins = listOf(
        SyncConsentOrigin.Settings,
        SyncConsentOrigin.Sharing,
        SyncConsentOrigin.Onboarding,
        SyncConsentOrigin.OnboardingFromSettings,
        exportOrigin,
        null,
    )

    @Test
    fun `a connect tapped on the settings screen is answered there`() {
        assertEquals(SyncConsentOrigin.Settings, syncConsentOriginOf(CampfireDestination.Settings, visibleDialog = null, settingsTab = SettingsTab.GENERAL))
    }

    @Test
    fun `a connect tapped on the settings screen's sharing tab is answered on that tab`() {
        assertEquals(
            SyncConsentOrigin.Sharing,
            syncConsentOriginOf(CampfireDestination.Settings, visibleDialog = null, settingsTab = SettingsTab.SHARING),
        )
        assertEquals(SyncConsentOrigin.Settings, syncConsentOriginOf(CampfireDestination.Settings, visibleDialog = null, settingsTab = SettingsTab.LIBRARY))
    }

    @Test
    fun `a connect tapped in a first run's onboarding is answered there`() {
        assertEquals(SyncConsentOrigin.Onboarding, syncConsentOriginOf(CampfireDestination.Onboarding(isOpenedFromSettings = false), visibleDialog = null, settingsTab = SettingsTab.GENERAL))
    }

    @Test
    fun `a connect tapped in an onboarding opened from the settings is answered there`() {
        assertEquals(
            SyncConsentOrigin.OnboardingFromSettings,
            syncConsentOriginOf(CampfireDestination.Onboarding(isOpenedFromSettings = true), visibleDialog = null, settingsTab = SettingsTab.GENERAL),
        )
    }

    @Test
    fun `a connect tapped on the export screen is answered there, for the song or setlist it shares`() {
        assertEquals(
            SyncConsentOrigin.Export(ShareTarget(SharedLinkKind.SONG, "a.cho")),
            syncConsentOriginOf(CampfireDestination.Songs, DialogType.Export(song = song("a.cho")), SettingsTab.SHARING),
        )
        assertEquals(exportOrigin, syncConsentOriginOf(CampfireDestination.Songs, DialogType.Export(setlist = setlist("set.setlist.json")), SettingsTab.SHARING))
    }

    @Test
    fun `an answer on the songs or on the settings screen goes where the connect was tapped`() {
        listOf(listOf(CampfireDestination.Songs), listOf(CampfireDestination.Songs, CampfireDestination.Settings)).forEach { backStack ->
            assertEquals(SyncConsentAnswer.ONBOARDING, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.Onboarding))
            assertEquals(SyncConsentAnswer.ONBOARDING_FROM_SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.OnboardingFromSettings))
            assertEquals(SyncConsentAnswer.SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.Settings))
            assertEquals(SyncConsentAnswer.SHARING, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.Sharing))
            assertEquals(SyncConsentAnswer.SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin = null))
            assertEquals(SyncConsentAnswer.EXPORT, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, exportOrigin))
        }
    }

    @Test
    fun `a first run's onboarding still on its first page is moved on to the dropbox page`() {
        val backStack = listOf(CampfireDestination.Songs, CampfireDestination.Onboarding(isOpenedFromSettings = false))
        origins.forEach { origin ->
            assertEquals(SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin))
            OnboardingStep.entries.drop(1).forEach { step -> assertEquals(SyncConsentAnswer.NOTHING, syncConsentAnswerOf(backStack, step, origin)) }
        }
    }

    @Test
    fun `an answer is not shown over a stack the user has built on`() = listOf(
        listOf(CampfireDestination.Songs, CampfireDestination.Settings, CampfireDestination.Onboarding(isOpenedFromSettings = true)),
        listOf(CampfireDestination.Songs, CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)),
        listOf(CampfireDestination.Songs, CampfireDestination.Setlists),
    ).forEach { backStack ->
        (origins - exportOrigin).forEach { origin ->
            assertEquals(SyncConsentAnswer.NOTHING, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin))
        }
    }

    @Test
    fun `the export screen is put back over the screen android restores under it`() {
        val backStack = listOf(CampfireDestination.Songs, CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0))
        assertEquals(SyncConsentAnswer.EXPORT, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, exportOrigin))
    }

    @Test
    fun `an origin kept in the saved state is taken once`() = runTest {
        val handle = SavedStateHandle()
        SyncConsentOrigins(SavedStateStore(handle, backgroundScope)).keep(exportOrigin)
        val restored = SyncConsentOrigins(SavedStateStore(handle, backgroundScope))
        assertEquals(exportOrigin, restored.take())
        assertNull(restored.take())
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    private fun setlist(fileName: String) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0,
    )
}
