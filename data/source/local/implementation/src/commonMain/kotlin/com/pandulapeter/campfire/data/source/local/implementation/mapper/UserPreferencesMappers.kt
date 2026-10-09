/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.TunerSettings
import com.pandulapeter.campfire.data.source.local.implementation.model.MetronomeSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.PrintSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.TunerSettingsDocument
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument

internal fun UserPreferencesDocument.toModel() = UserPreferences(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    areChordsEnabled = !isLyricsOnlyModeEnabled,
    areSetlistsEnabled = areSetlistsEnabled,
    isMetronomeEnabled = isMetronomeEnabled,
    isTunerEnabled = isTunerEnabled,
    // A hand edit or a newer version's wider range must not reach the screen as it is: a size of 40 is a column per
    // word. Not a number at all is no size, and is the default.
    fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE)
        ?: UserPreferences.DEFAULT_FONT_SCALE,
    sortingMode = UserPreferences.SortingMode.entries.firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    setlistSortingMode = UserPreferences.SetlistSortingMode.entries.firstOrNull { it.id == setlistSortingMode } ?: UserPreferences.SetlistSortingMode.BY_DATE,
    uiMode = UserPreferences.UiMode.entries.firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.entries.firstOrNull { it.id == themeColor } ?: UserPreferences.ThemeColor.CAMPFIRE,
    backgroundWarmth = backgroundWarmth.coerceIn(0, UserPreferences.MAX_BACKGROUND_WARMTH),
    isAppIconThemed = isAppIconThemed,
    isCoverArtEnabled = isCoverArtEnabled,
    shouldNumberSections = shouldNumberSections,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling(
        accidentals = UserPreferences.Accidentals.entries.firstOrNull { it.id == accidentals } ?: UserPreferences.Accidentals.ORIGINAL,
        notation = UserPreferences.Notation.entries.firstOrNull { it.id == notation }
            ?: if (isGermanNotationEnabled) UserPreferences.Notation.GERMAN else UserPreferences.Notation.STANDARD,
    ),
    areChordDiagramsEnabled = areChordDiagramsEnabled,
    chordInstrument = UserPreferences.ChordInstrument.entries.firstOrNull { it.id == chordInstrument } ?: UserPreferences.ChordInstrument.GUITAR,
    chordVoicings = chordVoicings.mapValues { (_, shapes) -> shapes.filterValues { it.isNotBlank() } }.filterValues { it.isNotEmpty() },
    isChordSectionFolded = isChordSectionFolded,
    transpositions = transpositions,
    tempos = tempos.filterValues { it in MetronomeSettings.TEMPO_RANGE },
    capos = capos.filterValues { it in Song.CAPO_RANGE },
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toSet() }.filterValues { it.isNotEmpty() },
    tagMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == tagMatchMode } ?: UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == languageMatchMode } ?: UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == tagSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == languageSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    printSettings = printSettings.toModel(),
    metronomeSettings = metronomeSettings.toModel(),
    tunerSettings = tunerSettings.toModel(),
    seenWhatsNewVersions = seenWhatsNewVersions,
    demoLibraryContentHashes = demoLibraryContentHashes.filterValues { it.isNotBlank() },
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = !areChordsEnabled,
    areSetlistsEnabled = areSetlistsEnabled,
    isMetronomeEnabled = isMetronomeEnabled,
    isTunerEnabled = isTunerEnabled,
    fontScale = fontScale,
    sortingMode = sortingMode.id,
    setlistSortingMode = setlistSortingMode.id,
    uiMode = uiMode.id,
    themeColor = themeColor.id,
    backgroundWarmth = backgroundWarmth,
    isAppIconThemed = isAppIconThemed,
    isCoverArtEnabled = isCoverArtEnabled,
    shouldNumberSections = shouldNumberSections,
    language = language.id,
    accidentals = chordSpelling.accidentals.id,
    notation = chordSpelling.notation.id,
    areChordDiagramsEnabled = areChordDiagramsEnabled,
    chordInstrument = chordInstrument.id,
    chordVoicings = chordVoicings,
    isChordSectionFolded = isChordSectionFolded,
    transpositions = transpositions,
    tempos = tempos,
    capos = capos,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toList() },
    tagMatchMode = tagMatchMode.id,
    languageMatchMode = languageMatchMode.id,
    tagSortingMode = tagSortingMode.id,
    languageSortingMode = languageSortingMode.id,
    printSettings = printSettings.toDocument(),
    metronomeSettings = metronomeSettings.toDocument(),
    tunerSettings = tunerSettings.toDocument(),
    seenWhatsNewVersions = seenWhatsNewVersions,
    demoLibraryContentHashes = demoLibraryContentHashes,
)

internal fun PrintSettingsDocument.toModel() = PrintSettings(
    format = PrintSettings.Format.entries.firstOrNull { it.id == format } ?: PrintSettings.Format.PDF,
    paper = PrintSettings.Paper.entries.firstOrNull { it.id == paper } ?: PrintSettings.Paper.A4,
    setlistMode = PrintSettings.SetlistMode.entries.firstOrNull { it.id == setlistMode } ?: PrintSettings.SetlistMode.SONG_SHEETS,
    isLandscape = isLandscape,
    fontSize = fontSize,
    marginMm = marginMm,
    columns = columns,
    showChords = showChords,
    showChordDiagrams = showChordDiagrams,
    showKey = showKey ?: showMetadata,
    showTempo = showTempo ?: showMetadata,
    showComments = showComments,
    showMetadata = showMetadata,
    showPageNumbers = showPageNumbers,
    startSongsOnNewPage = startSongsOnNewPage,
    includeSetlistOverview = includeSetlistOverview,
).normalized()

internal fun PrintSettings.toDocument() = normalized().let { settings ->
    PrintSettingsDocument(
        format = settings.format.id,
        paper = settings.paper.id,
        setlistMode = settings.setlistMode.id,
        isLandscape = settings.isLandscape,
        fontSize = settings.fontSize,
        marginMm = settings.marginMm,
        columns = settings.columns,
        showChords = settings.showChords,
        showChordDiagrams = settings.showChordDiagrams,
        showKey = settings.showKey,
        showTempo = settings.showTempo,
        showComments = settings.showComments,
        showMetadata = settings.showMetadata,
        showPageNumbers = settings.showPageNumbers,
        startSongsOnNewPage = settings.startSongsOnNewPage,
        includeSetlistOverview = settings.includeSetlistOverview,
    )
}

internal fun MetronomeSettingsDocument.toModel() = MetronomeSettings(
    soundId = sound,
    volume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
    subdivisionId = subdivision,
    isVisualBeatEnabled = isVisualBeatEnabled,
    isHapticBeatEnabled = isHapticBeatEnabled,
    beatLevels = beatLevels,
    bpm = bpm.coerceIn(MetronomeSettings.TEMPO_RANGE),
    timeSignature = timeSignature,
    isSongPanelShown = isSongPanelShown,
)

internal fun MetronomeSettings.toDocument() = MetronomeSettingsDocument(
    sound = soundId,
    volume = volume,
    subdivision = subdivisionId,
    isVisualBeatEnabled = isVisualBeatEnabled,
    isHapticBeatEnabled = isHapticBeatEnabled,
    beatLevels = beatLevels,
    bpm = bpm,
    timeSignature = timeSignature,
    isSongPanelShown = isSongPanelShown,
)

internal fun TunerSettingsDocument.toModel() = TunerSettings(
    instrumentId = instrument,
    referencePitch = referencePitch.coerceIn(TunerSettings.REFERENCE_PITCH_RANGE),
)

internal fun TunerSettings.toDocument() = TunerSettingsDocument(
    instrument = instrumentId,
    referencePitch = referencePitch,
)
