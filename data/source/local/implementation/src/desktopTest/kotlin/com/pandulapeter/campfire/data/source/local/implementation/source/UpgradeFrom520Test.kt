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
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.TunerSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Loads what Campfire 5.2.0 left on disk (`resources/upgrade-5.2.0`, written by that version's own sources and mappers)
 * through the sources the app reads it with, so that an upgrade that loses or resets what a user set fails here. The
 * expected values are spelled out rather than built by today's code, which would agree with itself whatever it reads.
 */
class UpgradeFrom520Test {

    private val root: File = Files.createTempDirectory("campfire-upgrade").toFile().also { root ->
        File(requireNotNull(javaClass.getResource("/upgrade-5.2.0")).toURI()).copyRecursively(root)
    }
    private val fileStorage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `every song of a 5_2_0 library is read with its text and directives`() = runBlocking {
        val songs = SongLocalSourceImpl(fileStorage, Logger.Standard).loadSongs {}.associateBy { it.fileName }

        assertEquals(SONG_FILE_NAMES, songs.keys)
        SONG_FILE_NAMES.forEach { name ->
            assertEquals(fixtureText("library/songs/$name"), fileStorage.readText(StorageDirectory.SONGS, name), name)
        }
        val hebrew = songs.getValue(HEBREW)
        assertEquals("ירושלים של זהב", hebrew.title)
        assertEquals("נעמי שמר", hebrew.artist)
        assertEquals(listOf("he"), hebrew.languages)
        assertEquals(null, songs.getValue(AMAZING_GRACE).key)
        val hallelujah = songs.getValue(HALLELUJAH)
        assertEquals(listOf("ballad", "wedding"), hallelujah.tags)
        assertEquals(listOf("en"), hallelujah.languages)
        assertEquals("https://upload.wikimedia.org/wikipedia/en/2/2a/Various_Positions.jpg", hallelujah.coverArtUrl)
        assertEquals(56, hallelujah.tempo)
        assertEquals("6/8", hallelujah.time)
        assertEquals(2, songs.getValue(WONDERWALL).capo)
    }

    @Test
    fun `every setlist of a 5_2_0 library keeps its archive flag, date, and entries with their overrides`() = runBlocking {
        val setlists = SetlistLocalSourceImpl(fileStorage, Logger.Standard).loadSetlists().associate { it.setlist.fileName to it.setlist }

        assertEquals(setOf("summer_camp_2024.setlist.json", "wedding.setlist.json", "rehearsal.setlist.json"), setlists.keys)
        setlists.getValue("summer_camp_2024.setlist.json").let {
            assertEquals("Summer camp 2024", it.title)
            assertEquals("The old campfire songs", it.description)
            assertEquals(LocalDate(2024, 7, 12), it.date)
            assertTrue(it.isArchived)
            assertFalse(it.isCountdownShown)
            assertEquals(listOf(Setlist.Entry(WONDERWALL, transposition = -2, capo = 4), Setlist.Entry(AMAZING_GRACE)), it.entries.withoutUnknownFields())
        }
        setlists.getValue("wedding.setlist.json").let {
            assertEquals("Wedding", it.title)
            assertEquals("Anna and Bence, first dance at nine", it.description)
            assertEquals(LocalDate(2026, 12, 19), it.date)
            assertFalse(it.isArchived)
            assertTrue(it.isCountdownShown)
            assertEquals(
                listOf(
                    Setlist.Entry(HALLELUJAH, transposition = 3, tempo = 64, capo = 1),
                    Setlist.Entry(HEBREW, transposition = -1, tempo = 96),
                    Setlist.Entry(SZOL_A_KAKAS, capo = 0),
                ),
                it.entries.withoutUnknownFields(),
            )
        }
        setlists.getValue("rehearsal.setlist.json").let {
            assertEquals(LocalDate(2026, 11, 5), it.date)
            assertFalse(it.isArchived)
            assertEquals(
                listOf(Setlist.Entry("beatles-yesterday.cho", transposition = 2), Setlist.Entry(WONDERWALL, tempo = 90)),
                it.entries.withoutUnknownFields(),
            )
        }
    }

    @Test
    fun `every preference a 5_2_0 user set keeps its value and leaves no unreadable copy`() = runBlocking {
        val preferences = UserPreferencesLocalSourceImpl(fileStorage, Logger.Standard).loadUserPreferences()

        assertEquals(EVERY_PREFERENCE_SET, preferences)
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, "preferences.json.bad"))
    }

    @Test
    fun `a default changed since 5_2_0 applies to a user who never chose it, and nothing else is reset`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, "preferences.json", fixtureText("preferences/preferences-untouched.json"))

        val preferences = UserPreferencesLocalSourceImpl(fileStorage, Logger.Standard).loadUserPreferences()

        assertFalse(preferences.areChordDegreesEnabled)
        assertEquals(UserPreferences.ThemeColor.PURPLE, preferences.themeColor)
        assertEquals(UserPreferencesDocument().toModel().copy(themeColor = UserPreferences.ThemeColor.PURPLE), preferences)
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, "preferences.json.bad"))
    }

    @Test
    fun `the editor draft a 5_2_0 user left unsaved is restored`() = runBlocking {
        assertEquals(
            SongContent(
                fileName = SZOL_A_KAKAS,
                text = "{title: Szól a kakas már}\n{artist: Népdal}\n{key: Dm}\n\n[Dm]Szól a kakas [A]már, majd [Dm]megvirrad már\n",
            ),
            EditorDraftLocalSourceImpl(fileStorage, Logger.Standard).loadEditorDraft(),
        )
    }

    private fun fixtureText(path: String) = File(root, path).readText()

    private fun List<Setlist.Entry>.withoutUnknownFields() = map { it.copy(unknownFields = "") }

    private companion object {
        const val WONDERWALL = "oasis-wonderwall.cho"
        const val HEBREW = "נעמי_שמר-ירושלים_של_זהב.cho"
        const val AMAZING_GRACE = "john_newton-amazing_grace.cho"
        const val HALLELUJAH = "leonard_cohen-hallelujah.cho"
        const val SZOL_A_KAKAS = "nepdal-szol_a_kakas_mar.cho"
        val SONG_FILE_NAMES = setOf(WONDERWALL, HEBREW, AMAZING_GRACE, HALLELUJAH, SZOL_A_KAKAS)

        /** Every value the fixture's `preferences.json` was written with by 5.2.0, each one away from that version's default. */
        val EVERY_PREFERENCE_SET = UserPreferences(
            isPerformanceModeEnabled = true,
            shouldShowArchivedSetlists = true,
            areChordsEnabled = false,
            areSongsEnabled = false,
            areSetlistsEnabled = false,
            isMetronomeEnabled = false,
            isTunerEnabled = false,
            fontScale = 1.4f,
            sortingMode = UserPreferences.SortingMode.BY_TITLE,
            setlistSortingMode = UserPreferences.SetlistSortingMode.BY_TITLE,
            uiMode = UserPreferences.UiMode.DARK,
            themeColor = UserPreferences.ThemeColor.TEAL,
            backgroundWarmth = 3,
            isAppIconThemed = false,
            isCoverArtEnabled = false,
            isSongDetailsLookupEnabled = false,
            songLayout = SongLayout.CONTINUOUS,
            isSongLayoutChosen = true,
            shouldNumberSections = false,
            language = AppLanguage.HUNGARIAN,
            chordSpelling = UserPreferences.ChordSpelling(accidentals = UserPreferences.Accidentals.FLATS, notation = UserPreferences.Notation.GERMAN),
            areChordDiagramsEnabled = false,
            areChordDegreesEnabled = false,
            isChordLibraryEnabled = false,
            areScalesEnabled = false,
            areScaleSuggestionsEnabled = false,
            songCardDetails = UserPreferences.CardDetails(isKeyShown = false, isTempoShown = true, isDurationShown = true),
            setlistCardDetails = UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false),
            instrument = UserPreferences.Instrument.UKULELE,
            tunings = mapOf("guitar" to "guitar_drop_d", "ukulele" to "ukulele_baritone"),
            toolCapo = 3,
            areScaleNoteNamesShown = true,
            chordVoicings = mapOf("guitar" to mapOf("F:0.4.7" to "x x 3 2 1 1")),
            transpositions = mapOf(WONDERWALL to 2, HALLELUJAH to -3),
            tempos = mapOf(WONDERWALL to 92),
            capos = mapOf(HALLELUJAH to 5),
            autoScrollSpeeds = mapOf(AMAZING_GRACE to 160),
            beatLevels = mapOf(HALLELUJAH to mapOf("6/8" to listOf("accent", "muted", "normal", "accent", "muted", "normal"))),
            foldedSections = mapOf(WONDERWALL to setOf("chorus#1")),
            tagMatchMode = UserPreferences.MatchMode.ALL,
            languageMatchMode = UserPreferences.MatchMode.ALL,
            tagSortingMode = UserPreferences.LabelSortingMode.ALPHABETICAL,
            languageSortingMode = UserPreferences.LabelSortingMode.ALPHABETICAL,
            tagColors = mapOf("folk" to "green", "wedding" to "pink"),
            printSettings = PrintSettings(
                format = PrintSettings.Format.PDF,
                paper = PrintSettings.Paper.LETTER,
                isLandscape = true,
                fontSize = 14,
                marginMm = 20,
                columns = 2,
                showChords = false,
                showTabs = false,
                showSheetMusic = false,
                showChordDiagrams = false,
                showScales = false,
                showKey = false,
                showTempo = false,
                showComments = false,
                showMetadata = false,
                showPageNumbers = false,
                startSongsOnNewPage = false,
                includeSetlistOverview = false,
                includeSongSheets = false,
                overviewColumns = 3,
                showOverviewDetails = false,
            ),
            metronomeSettings = MetronomeSettings(
                soundId = "cowbell",
                volume = 0.5f,
                subdivisionId = "triplets",
                isVisualBeatEnabled = false,
                isHapticBeatEnabled = false,
                beatLevels = mapOf("7/8" to listOf("accent", "normal", "accent", "normal", "accent", "normal", "normal")),
                bpm = 132,
                timeSignature = "7/8",
                isSongPanelShown = true,
            ),
            tunerSettings = TunerSettings(mode = TunerSettings.Mode.CHROMATIC, referencePitch = 432, hasTurnedOnMicrophone = true),
            seenWhatsNewVersions = setOf("5.1.0", "5.2.0"),
            isOnboardingPending = true,
            demoLibraryContentHashes = mapOf("songs/demo.cho" to "abc123"),
            sharedLinkImports = mapOf("https://www.dropbox.com/scl/fi/abc/wedding.zip?rlkey=x&dl=0" to listOf("setlists/wedding.setlist.json")),
        )
    }
}
