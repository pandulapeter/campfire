/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `preferences.json`. Every field is defaulted, so a document written by an older version keeps
 * loading instead of being discarded; the enums are stored by their stable `id` rather than by ordinal.
 */
@Serializable
internal data class UserPreferencesDocument(
    val isPerformanceModeEnabled: Boolean = false,
    // On by default: a song created in the app starts out as a title and an artist, so hiding songs without chords
    // would hide every new song right after it was made.
    val shouldShowArchivedSetlists: Boolean = false,
    // The inverse of UserPreferences.areChordsEnabled, stored under the name the switch had before, so that a document
    // written either way still says the same thing.
    val isLyricsOnlyModeEnabled: Boolean = false,
    val areSongsEnabled: Boolean = true,
    val areSetlistsEnabled: Boolean = true,
    val isMetronomeEnabled: Boolean = true,
    val isTunerEnabled: Boolean = true,
    val fontScale: Float = 1f,
    val sortingMode: String = "",
    val setlistSortingMode: String = "",
    val uiMode: String = "",
    val themeColor: String = "",
    val backgroundWarmth: Int = 0,
    val isAppIconThemed: Boolean = true,
    val isCoverArtEnabled: Boolean = true,
    val isSongDetailsLookupEnabled: Boolean = true,
    val shouldNumberSections: Boolean = true,
    val language: String = "",
    val accidentals: String = "",
    val notation: String = "",
    // What the notation was before there could be more than two: only read, for a document that has no notation yet.
    val isGermanNotationEnabled: Boolean = false,
    val areChordDiagramsEnabled: Boolean = true,
    val areScalesEnabled: Boolean = true,
    val areScaleSuggestionsEnabled: Boolean = true,
    // What the cards of the Songs screen show, under the names of the two switches that once decided the song details
    // screen and the cards of both lists at once; the Setlists screen's cards follow them where a document has no
    // switches of their own yet, since that is what they showed when it was written.
    val isKeyShown: Boolean = true,
    val isTempoShown: Boolean = true,
    val isDurationShownOnSongCards: Boolean = false,
    val isKeyShownOnSetlistCards: Boolean? = null,
    val isTempoShownOnSetlistCards: Boolean? = null,
    val isDurationShownOnSetlistCards: Boolean = true,
    // The tempo picked for each list's cards, null where it has not been and the cards follow the metronome switch. Only
    // these are written now, the two above (true by default, and so never a choice) only read for a document that has
    // neither: a false there was picked, and a true is what the cards showed with the metronome on and only then.
    val tempoOnSongCards: Boolean? = null,
    val tempoOnSetlistCards: Boolean? = null,
    val instrument: String = "",
    val tunings: Map<String, String> = emptyMap(),
    // What the chord diagrams were drawn for before the tuner and the diagrams shared one instrument: only read, for a
    // document that has no instrument yet, and never written again.
    val chordInstrument: String = "",
    val chordTunings: Map<String, String> = emptyMap(),
    val chordVoicings: Map<String, Map<String, String>> = emptyMap(),
    val isChordSectionFolded: Boolean = false,
    val isScaleSectionFolded: Boolean = false,
    val transpositions: Map<String, Int> = emptyMap(),
    val tempos: Map<String, Int> = emptyMap(),
    val capos: Map<String, Int> = emptyMap(),
    val beatLevels: Map<String, Map<String, List<String>>> = emptyMap(),
    val foldedSections: Map<String, List<String>> = emptyMap(),
    val tagMatchMode: String = "",
    val languageMatchMode: String = "",
    val tagSortingMode: String = "",
    val languageSortingMode: String = "",
    val tagColors: Map<String, String> = emptyMap(),
    val printSettings: PrintSettingsDocument = PrintSettingsDocument(),
    val metronomeSettings: MetronomeSettingsDocument = MetronomeSettingsDocument(),
    val tunerSettings: TunerSettingsDocument = TunerSettingsDocument(),
    val seenWhatsNewVersions: Set<String> = emptySet(),
    val isOnboardingPending: Boolean = false,
    val demoLibraryContentHashes: Map<String, String> = emptyMap(),
    val sharedLinkImports: Map<String, List<String>> = emptyMap(),
)

@Serializable
internal data class PrintSettingsDocument(
    val format: String = "link",
    val paper: String = "a4",
    val isLandscape: Boolean = false,
    val fontSize: Int = 12,
    val marginMm: Int = 15,
    val columns: Int = 1,
    val showChords: Boolean = true,
    val showChordDiagrams: Boolean = true,
    val showScales: Boolean = true,
    /**
     * Null in a document from before the key and the tempo had boxes of their own, when [showMetadata] printed them, so
     * that somebody who had unticked that box does not find them printed again.
     */
    val showKey: Boolean? = null,
    /** As [showKey]. */
    val showTempo: Boolean? = null,
    val showComments: Boolean = true,
    val showMetadata: Boolean = true,
    val showPageNumbers: Boolean = true,
    val startSongsOnNewPage: Boolean = true,
    val includeSetlistOverview: Boolean = true,
)

/** The ids are the metronome's own and are checked where they are read into its types, in `:presentation`. */
@Serializable
internal data class MetronomeSettingsDocument(
    val sound: String = "click",
    val volume: Float = 1f,
    val subdivision: String = "none",
    val isVisualBeatEnabled: Boolean = true,
    val isHapticBeatEnabled: Boolean = false,
    val beatLevels: Map<String, List<String>> = emptyMap(),
    val bpm: Int = 120,
    val timeSignature: String = "4/4",
    val isSongPanelShown: Boolean = false,
)

/**
 * [instrument] is what the tuner listened for before it shared the instrument with the chord diagrams, its own tuning
 * id or `chromatic`: only read, for a document that has no instrument of its own, and never written again (`chromatic`
 * names no instrument, so the diagrams' is taken). Its default is blank rather than the `chromatic` it once was, since
 * that default was never written: a document without the key is a tuner nobody set, which opens on the instrument like
 * a fresh installation's.
 *
 * [mode] is not named `isChromatic`, the key of a Chromatic switch that an older version wrote and a later one dropped:
 * what that one still says was left behind by a player who has tuned to their strings since.
 */
@Serializable
internal data class TunerSettingsDocument(
    val instrument: String = "",
    val mode: String = "strings",
    val referencePitch: Int = 440,
    val hasTurnedOnMicrophone: Boolean = false,
)
