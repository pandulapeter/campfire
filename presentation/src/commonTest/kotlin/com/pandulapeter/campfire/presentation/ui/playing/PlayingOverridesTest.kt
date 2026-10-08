/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Over a store that keeps whatever is written into it, as the preferences and the setlist files do. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayingOverridesTest {

    private val preferences = MutableStateFlow(preferences())
    private val setlists = MutableStateFlow(listOf(setlist(Setlist.Entry(songFileName = SONG))))
    private lateinit var sink: MessageSink

    @Test
    fun `a library transposition stepped back to zero leaves nothing behind in the preferences`() = runTest {
        val overrides = overrides()
        overrides.stepTransposition(SONG, null, 2)
        settle()
        assertEquals(mapOf(SONG to 2), preferences.value.transpositions)
        overrides.stepTransposition(SONG, null, -2)
        settle()
        assertEquals(emptyMap(), preferences.value.transpositions)
    }

    @Test
    fun `seven semitones up is stored as the five down that name the same chords`() = runTest {
        val overrides = overrides()
        overrides.stepTransposition(SONG, null, 7)
        overrides.stepTransposition(SONG, SETLIST, 7)
        settle()
        assertEquals(mapOf(SONG to -5), preferences.value.transpositions)
        assertEquals(-5, setlists.value.single().entries.single().transposition)
    }

    @Test
    fun `a tempo set back to the song's own is no override`() = runTest {
        val overrides = overrides()
        overrides.setTempo(SONG, null, 130)
        settle()
        assertEquals(mapOf(SONG to 130), preferences.value.tempos)
        overrides.setTempo(SONG, null, SONG_TEMPO)
        settle()
        assertEquals(emptyMap(), preferences.value.tempos)
    }

    @Test
    fun `a tempo for a song a sync run took out of the setlist is reported and let go of, and a reset there is not`() = runTest {
        setlists.value = listOf(setlist())
        val overrides = overrides()
        overrides.setTempo(SONG, SETLIST, 130)
        settle()
        assertEquals(listOf<Message>(Message.OperationFailed), messages())
        assertNull(overrides.tempos.value[SONG, SETLIST])
        assertEquals(emptyList(), setlists.value.single().entries)

        overrides.resetTempo(SONG, SETLIST)
        settle()
        assertEquals(listOf<Message>(Message.OperationFailed), messages())
    }

    @Test
    fun `a capo is kept on the frets a capo can sit on, and the song's own fret is no override`() = runTest {
        val overrides = overrides()
        overrides.stepCapo(SONG, null, 100)
        settle()
        assertEquals(mapOf(SONG to Song.CAPO_RANGE.last), preferences.value.capos)
        overrides.stepCapo(SONG, null, -100)
        settle()
        assertEquals(emptyMap(), preferences.value.capos)
    }

    private fun TestScope.settle() {
        advanceTimeBy(DELAY * 2)
        runCurrent()
    }

    private fun messages() = sink.messageQueue.value.map { it.value }

    private fun TestScope.overrides(): PlayingOverrides {
        sink = MessageSink(backgroundScope)
        return PlayingOverrides(
            scope = backgroundScope,
            userPreferences = preferences,
            setlists = setlists,
            songsByFileName = { mapOf(SONG to song()) },
            updateUserPreferences = object : UpdateUserPreferencesUseCase {
                override suspend fun invoke(update: (UserPreferences) -> UserPreferences) = preferences.update(update)
            },
            updateEditableSetlist = { fileName, transform ->
                setlists.value.firstOrNull { it.fileName == fileName }?.let { if (it.isArchived) it else transform(it) }?.also { updated ->
                    setlists.update { all -> all.map { if (it.fileName == fileName) updated else it } }
                }
            },
            messageSink = sink,
            writeDelayMillis = DELAY,
        )
    }

    private fun song() = Song(
        fileName = SONG, title = "Song", artist = "", key = "C", transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0L, size = 0L, tempo = SONG_TEMPO,
    )

    private fun setlist(vararg entries: Setlist.Entry) = Setlist(
        fileName = SETLIST,
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = entries.toList(),
        size = 0L,
    )

    private companion object {
        const val DELAY = 500L
        const val SONG = "song.cho"
        const val SETLIST = "set.setlist.json"
        const val SONG_TEMPO = 120

        fun preferences() = UserPreferences(
            isPerformanceModeEnabled = false,
            shouldShowArchivedSetlists = false,
            areChordsEnabled = true,
            areSetlistsEnabled = true,
            isMetronomeEnabled = true,
            fontScale = UserPreferences.DEFAULT_FONT_SCALE,
            sortingMode = UserPreferences.SortingMode.BY_TITLE,
            setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
            uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
            themeColor = UserPreferences.ThemeColor.CAMPFIRE,
            isAppIconThemed = true,
            isCoverArtEnabled = true,
            shouldNumberSections = true,
            language = UserPreferences.Language.SYSTEM_DEFAULT,
            chordSpelling = UserPreferences.ChordSpelling.Default,
            transpositions = emptyMap(),
            foldedSections = emptyMap(),
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
            tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
            languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        )
    }
}
