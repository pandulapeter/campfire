/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetPublicationsTest {

    private fun song(fileName: String) = Song(
        fileName = fileName,
        title = fileName,
        artist = "Artist",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )

    private fun library(vararg fileNames: String, isWholeLibrary: Boolean = true) = fileNames.map(::song).let { songs ->
        ScreenData(
            setlists = emptyList(),
            songs = songs,
            songSections = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            unfilteredSongs = songs,
            libraryTags = emptyList(),
            libraryLanguages = emptyList(),
            sortedSongs = songs,
            isWholeLibrary = isWholeLibrary,
        )
    }

    private val build = { library: ScreenData, features: FeatureSwitches ->
        widgetSnapshot(
            screenData = library,
            features = features,
            strings = WidgetTestStrings.strings,
            theme = widgetTheme(uiMode = null, colors = null),
            formatDate = { it.toString() },
            languageName = { it },
        )
    }

    private fun TestScope.publishedSongs(
        screenData: MutableStateFlow<DataState<ScreenData>>,
        preferences: MutableStateFlow<UserPreferences?>,
        changes: () -> Unit,
    ): List<List<String>> {
        val published = mutableListOf<List<String>>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            widgetPublications(screenData = screenData, preferences = preferences, builders = flowOf(build)).collect { publication ->
                published += publication.snapshot.songs.map { it.fileName }
            }
        }
        changes()
        collection.cancel()
        return published
    }

    @Test
    fun `a song deleted from the library reaches the widgets`() = runTest {
        val screenData = MutableStateFlow<DataState<ScreenData>>(DataState.Idle(library("a.cho", "b.cho")))
        val published = publishedSongs(screenData, MutableStateFlow(testPreferences())) {
            screenData.value = DataState.Idle(library("b.cho"))
        }
        assertEquals(listOf(listOf("a.cho", "b.cho"), listOf("b.cho")), published)
    }

    @Test
    fun `nothing is published before the library and the preferences are read whole`() = runTest {
        val screenData = MutableStateFlow<DataState<ScreenData>>(DataState.Loading(null))
        val preferences = MutableStateFlow<UserPreferences?>(null)
        val published = publishedSongs(screenData, preferences) {
            screenData.value = DataState.Idle(library("a.cho", isWholeLibrary = false))
            screenData.value = DataState.Idle(library("a.cho", "b.cho"))
            preferences.value = testPreferences()
        }
        assertEquals(listOf(listOf("a.cho", "b.cho")), published)
    }

    @Test
    fun `a preference the widgets do not show publishes nothing`() = runTest {
        val preferences = MutableStateFlow<UserPreferences?>(testPreferences())
        val published = publishedSongs(MutableStateFlow(DataState.Idle(library("a.cho"))), preferences) {
            preferences.value = preferences.value?.copy(isPerformanceModeEnabled = true)
        }
        assertEquals(listOf(listOf("a.cho")), published)
    }
}
