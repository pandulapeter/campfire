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

import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.TunerSettings
import com.pandulapeter.campfire.data.source.local.implementation.model.MetronomeSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.PrintSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.TunerSettingsDocument
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument

internal fun UserPreferencesDocument.toModel() = UserPreferences(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    areChordsEnabled = !isLyricsOnlyModeEnabled,
    areSongsEnabled = areSongsEnabled,
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
    isSongDetailsLookupEnabled = isSongDetailsLookupEnabled,
    shouldNumberSections = shouldNumberSections,
    songLayout = SongLayout.entries.firstOrNull { it.id == songLayout } ?: SongLayout.PAGES,
    isSongLayoutChosen = isSongLayoutChosen,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling(
        accidentals = UserPreferences.Accidentals.entries.firstOrNull { it.id == accidentals } ?: UserPreferences.Accidentals.ORIGINAL,
        notation = UserPreferences.Notation.entries.firstOrNull { it.id == notation }
            ?: if (isGermanNotationEnabled) UserPreferences.Notation.GERMAN else UserPreferences.Notation.STANDARD,
    ),
    areChordDiagramsEnabled = areChordDiagramsEnabled,
    areChordDegreesEnabled = areChordDegreesEnabled,
    isChordLibraryEnabled = isChordLibraryEnabled,
    areScalesEnabled = areScalesEnabled,
    areScaleSuggestionsEnabled = areScaleSuggestionsEnabled,
    songCardDetails = UserPreferences.CardDetails(
        isKeyShown = isKeyShown,
        isTempoShown = tempoOnSongCards ?: false.takeIf { !isTempoShown },
        isDurationShown = isDurationShownOnSongCards,
    ),
    setlistCardDetails = UserPreferences.CardDetails(
        isKeyShown = isKeyShownOnSetlistCards ?: isKeyShown,
        isTempoShown = tempoOnSetlistCards ?: false.takeIf { !(isTempoShownOnSetlistCards ?: isTempoShown) },
        isDurationShown = isDurationShownOnSetlistCards,
    ),
    instrument = instrument(),
    tunings = tunings().filterValues { it.isNotBlank() },
    toolCapo = toolCapo.coerceIn(Song.CAPO_RANGE),
    areScaleNoteNamesShown = areScaleNoteNamesShown,
    chordVoicings = chordVoicings.mapValues { (_, shapes) -> shapes.filterValues { it.isNotBlank() } }.filterValues { it.isNotEmpty() },
    transpositions = transpositions,
    tempos = tempos.filterValues { it in MetronomeSettings.TEMPO_RANGE },
    capos = capos.filterValues { it in Song.CAPO_RANGE },
    autoScrollSpeeds = autoScrollSpeeds.filterValues { it in SongLayout.AUTO_SCROLL_SPEED_RANGE },
    beatLevels = beatLevels.mapValues { (_, bySignature) -> bySignature.filterValues { it.isNotEmpty() } }.filterValues { it.isNotEmpty() },
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toSet() }.filterValues { it.isNotEmpty() },
    tagMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == tagMatchMode } ?: UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == languageMatchMode } ?: UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == tagSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == languageSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    tagColors = tagColors.filterValues { it.isNotBlank() },
    printSettings = printSettings.toModel(),
    metronomeSettings = metronomeSettings.toModel(),
    tunerSettings = tunerSettings.toModel(),
    seenWhatsNewVersions = seenWhatsNewVersions,
    isOnboardingPending = isOnboardingPending,
    demoLibraryContentHashes = demoLibraryContentHashes.filterValues { it.isNotBlank() },
    sharedLinkImports = sharedLinkImports.filterKeys { it.isNotBlank() }.mapValues { (_, paths) -> paths.filter { it.isNotBlank() } }.filterValues { it.isNotEmpty() },
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = !areChordsEnabled,
    areSongsEnabled = areSongsEnabled,
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
    isSongDetailsLookupEnabled = isSongDetailsLookupEnabled,
    shouldNumberSections = shouldNumberSections,
    songLayout = songLayout.id,
    isSongLayoutChosen = isSongLayoutChosen,
    language = language.id,
    accidentals = chordSpelling.accidentals.id,
    notation = chordSpelling.notation.id,
    areChordDiagramsEnabled = areChordDiagramsEnabled,
    areChordDegreesEnabled = areChordDegreesEnabled,
    isChordLibraryEnabled = isChordLibraryEnabled,
    areScalesEnabled = areScalesEnabled,
    areScaleSuggestionsEnabled = areScaleSuggestionsEnabled,
    isKeyShown = songCardDetails.isKeyShown,
    isDurationShownOnSongCards = songCardDetails.isDurationShown,
    isKeyShownOnSetlistCards = setlistCardDetails.isKeyShown,
    isDurationShownOnSetlistCards = setlistCardDetails.isDurationShown,
    tempoOnSongCards = songCardDetails.isTempoShown,
    tempoOnSetlistCards = setlistCardDetails.isTempoShown,
    instrument = instrument.id,
    tunings = tunings,
    toolCapo = toolCapo,
    areScaleNoteNamesShown = areScaleNoteNamesShown,
    chordVoicings = chordVoicings,
    transpositions = transpositions,
    tempos = tempos,
    capos = capos,
    autoScrollSpeeds = autoScrollSpeeds,
    beatLevels = beatLevels,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toList() },
    tagMatchMode = tagMatchMode.id,
    languageMatchMode = languageMatchMode.id,
    tagSortingMode = tagSortingMode.id,
    languageSortingMode = languageSortingMode.id,
    tagColors = tagColors,
    printSettings = printSettings.toDocument(),
    metronomeSettings = metronomeSettings.toDocument(),
    tunerSettings = tunerSettings.toDocument(),
    seenWhatsNewVersions = seenWhatsNewVersions,
    isOnboardingPending = isOnboardingPending,
    demoLibraryContentHashes = demoLibraryContentHashes,
    sharedLinkImports = sharedLinkImports,
)

internal fun PrintSettingsDocument.toModel() = PrintSettings(
    format = PrintSettings.Format.entries.firstOrNull { it.id == format } ?: PrintSettings.Format.PDF,
    paper = PrintSettings.Paper.entries.firstOrNull { it.id == paper } ?: PrintSettings.Paper.A4,
    isLandscape = isLandscape,
    fontSize = fontSize,
    marginMm = marginMm,
    columns = columns,
    showChords = showChords,
    showTabs = showTabs ?: showChords,
    showChordDiagrams = showChordDiagrams,
    showScales = showScales,
    showKey = showKey ?: showMetadata,
    showTempo = showTempo ?: showMetadata,
    showComments = showComments,
    showMetadata = showMetadata,
    showPageNumbers = showPageNumbers,
    startSongsOnNewPage = startSongsOnNewPage,
    includeSetlistOverview = includeSetlistOverview,
    includeSongSheets = includeSongSheets,
    overviewColumns = overviewColumns,
    showOverviewDetails = showOverviewDetails ?: showMetadata,
).normalized()

internal fun PrintSettings.toDocument() = normalized().let { settings ->
    PrintSettingsDocument(
        format = settings.format.id,
        paper = settings.paper.id,
        isLandscape = settings.isLandscape,
        fontSize = settings.fontSize,
        marginMm = settings.marginMm,
        columns = settings.columns,
        showChords = settings.showChords,
        showTabs = settings.showTabs,
        showChordDiagrams = settings.showChordDiagrams,
        showScales = settings.showScales,
        showKey = settings.showKey,
        showTempo = settings.showTempo,
        showComments = settings.showComments,
        showMetadata = settings.showMetadata,
        showPageNumbers = settings.showPageNumbers,
        startSongsOnNewPage = settings.startSongsOnNewPage,
        includeSetlistOverview = settings.includeSetlistOverview,
        includeSongSheets = settings.includeSongSheets,
        overviewColumns = settings.overviewColumns,
        showOverviewDetails = settings.showOverviewDetails,
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

/** Whether this document was written before the tuner and the chord diagrams shared one instrument: it names none. */
private val UserPreferencesDocument.predatesInstrument get() = instrument.isBlank()

/**
 * The instrument in the player's hands. A document written before the tuner and the chord diagrams shared one instrument
 * has none, and it is derived from the two old answers: the tuner's first, since it is the more specific one (a
 * seven-string player told the tuner so and the diagrams Other), otherwise the diagrams' instrument. An instrument this
 * version does not know is the guitar, with every tuning of the document kept.
 */
private fun UserPreferencesDocument.instrument() = if (predatesInstrument) {
    UserPreferences.Instrument.ofTuningId(tunerSettings.instrument)
        ?: UserPreferences.Instrument.fromId(chordInstrument)
        ?: UserPreferences.Instrument.GUITAR
} else {
    UserPreferences.Instrument.fromId(instrument) ?: UserPreferences.Instrument.GUITAR
}

/** The tunings picked for each instrument; on a document from before [instrument] the diagrams', plus the tuner's own. */
private fun UserPreferencesDocument.tunings(): Map<String, String> {
    if (!predatesInstrument) return tunings
    val tunerTuning = tunerSettings.instrument
    val tunerInstrument = UserPreferences.Instrument.ofTuningId(tunerTuning)
    return if (tunerInstrument == null || tunerTuning == tunerInstrument.id) chordTunings else chordTunings + (tunerInstrument.id to tunerTuning)
}

internal fun TunerSettingsDocument.toModel() = TunerSettings(
    mode = TunerSettings.Mode.entries.firstOrNull { it.id == mode } ?: TunerSettings.Mode.STRINGS,
    referencePitch = referencePitch.coerceIn(TunerSettings.REFERENCE_PITCH_RANGE),
    hasTurnedOnMicrophone = hasTurnedOnMicrophone,
)

internal fun TunerSettings.toDocument() = TunerSettingsDocument(
    mode = mode.id,
    referencePitch = referencePitch,
    hasTurnedOnMicrophone = hasTurnedOnMicrophone,
)
