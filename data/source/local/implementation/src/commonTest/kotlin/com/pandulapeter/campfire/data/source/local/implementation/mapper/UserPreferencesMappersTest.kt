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
import com.pandulapeter.campfire.data.model.domain.TunerSettings
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.data.source.local.implementation.model.MetronomeSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.TunerSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.PrintSettingsDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocumentFormat
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A value a newer or an older version wrote costs that one setting, never the document it is in. */
internal class UserPreferencesMappersTest {

    @Test
    fun `introduced versions survive other preference changes and reloading`() {
        val legacy = UserPreferencesDocumentFormat.decode("""{"uiMode":"dark"}""").document.toModel()
        assertEquals(emptySet(), legacy.seenWhatsNewVersions)
        val versions = setOf("4.5.1", "4.6.0", "4.6.1")
        val saved = legacy.copy(seenWhatsNewVersions = versions).copy(fontScale = 1.2f).toDocument()
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(saved)).document.toModel()
        assertEquals(versions, reloaded.seenWhatsNewVersions)
        assertEquals(UserPreferences.UiMode.DARK, reloaded.uiMode)
        assertEquals(1.2f, reloaded.fontScale)
    }

    @Test
    fun `the remembered demo files survive saving and reloading`() {
        val legacy = UserPreferencesDocumentFormat.decode("""{"uiMode":"dark"}""").document.toModel()
        assertEquals(emptyMap(), legacy.demoLibraryContentHashes)
        val hashes = mapOf("songs/a.cho" to "0a1b", "setlists/b.setlist.json" to "2c3d")
        val saved = legacy.copy(demoLibraryContentHashes = hashes).toDocument()
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(saved)).document.toModel()
        assertEquals(hashes, reloaded.demoLibraryContentHashes)
        assertEquals(emptyMap(), UserPreferencesDocument(demoLibraryContentHashes = mapOf("songs/a.cho" to " ")).toModel().demoLibraryContentHashes)
    }

    @Test
    fun `print settings survive saving and reloading preferences`() {
        val settings = PrintSettings(format = PrintSettings.Format.FILES, paper = PrintSettings.Paper.LETTER, isLandscape = true, fontSize = 18,
            marginMm = 20, columns = 4, showChords = false, showChordDiagrams = false, showKey = false, showTempo = false, showComments = false, showMetadata = false,
            showPageNumbers = false, startSongsOnNewPage = false, setlistMode = PrintSettings.SetlistMode.RUNNING_ORDER,
            includeSetlistOverview = false)
        val preferences = UserPreferencesDocument().toModel().copy(printSettings = settings)
        val saved = UserPreferencesDocumentFormat.encode(preferences.toDocument())
        assertEquals(settings, UserPreferencesDocumentFormat.decode(saved).document.toModel().printSettings)
        assertEquals(PrintSettings(), UserPreferencesDocumentFormat.decode("{}").document.toModel().printSettings)
    }

    @Test
    fun `the key and the tempo follow the details box that printed them until they are chosen`() {
        fun printSettingsOf(text: String) = UserPreferencesDocumentFormat.decode("""{"printSettings":$text}""").document.toModel().printSettings

        printSettingsOf("""{"showMetadata":false}""").let { assertEquals(false to false, it.showKey to it.showTempo) }
        printSettingsOf("""{"showMetadata":false,"showKey":true}""").let { assertEquals(true to false, it.showKey to it.showTempo) }
        printSettingsOf("""{}""").let { assertEquals(true to true, it.showKey to it.showTempo) }
        val chosen = PrintSettings(showMetadata = false, showKey = true, showTempo = true)
        val saved = UserPreferencesDocumentFormat.encode(UserPreferencesDocument().toModel().copy(printSettings = chosen).toDocument())
        assertEquals(chosen, UserPreferencesDocumentFormat.decode(saved).document.toModel().printSettings)
    }

    @Test
    fun `malformed print sizes and unknown paper fall back without losing other choices`() {
        val preferences = UserPreferencesDocument(printSettings = PrintSettingsDocument(format = "docx", paper = "tabloid", setlistMode = "unknown",
            fontSize = 400, marginMm = -1, columns = 50, showChords = false)).toModel()
        assertEquals(PrintSettings(fontSize = 20, marginMm = 10, columns = 4, showChords = false), preferences.printSettings)
    }

    @Test
    fun `features default to on and chords keep the lyrics only switch they replaced`() {
        val defaults = UserPreferencesDocumentFormat.decode("{}").document.toModel()
        assertEquals(true, defaults.areChordsEnabled)
        assertEquals(true, defaults.areSetlistsEnabled)
        assertEquals(true, defaults.isMetronomeEnabled)
        assertEquals(true, defaults.isTunerEnabled)
        val lyricsOnly = UserPreferencesDocumentFormat.decode("""{"isLyricsOnlyModeEnabled":true}""").document
        assertEquals(false, lyricsOnly.toModel().areChordsEnabled)
        assertEquals(true, lyricsOnly.toModel().toDocument().isLyricsOnlyModeEnabled)
    }

    @Test
    fun `the instrument defaults to the guitar and keeps every chosen shape and tuning`() {
        val defaults = UserPreferencesDocumentFormat.decode("{}").document.toModel()
        assertEquals(true, defaults.areChordDiagramsEnabled)
        assertEquals(UserPreferences.Instrument.GUITAR, defaults.instrument)
        assertEquals(false, defaults.isChordSectionFolded)
        assertEquals(emptyMap(), defaults.tunings)
        assertEquals(false, defaults.tunerSettings.isChromatic)
        val chosen = defaults.copy(
            areChordDiagramsEnabled = false,
            instrument = UserPreferences.Instrument.UKULELE,
            tunings = mapOf("guitar" to "guitar_drop_d", "ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d"),
            chordVoicings = mapOf("guitar" to mapOf("F:0.4.7" to "x x 3 2 1 1"), "banjo" to mapOf("G:0.4.7" to "0 0 0 0 0")),
            isChordSectionFolded = true,
        )
        assertEquals(chosen, UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(chosen.toDocument())).document.toModel())
        val bass = defaults.copy(instrument = UserPreferences.Instrument.BASS_FIVE_STRING)
        assertEquals(bass, UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(bass.toDocument())).document.toModel())
        assertEquals(emptyMap(), UserPreferencesDocument(chordVoicings = mapOf("guitar" to mapOf("F:0.4.7" to " "))).toModel().chordVoicings)
    }

    @Test
    fun `an instrument this version does not know is the guitar`() {
        assertEquals(UserPreferences.Instrument.GUITAR, UserPreferencesDocument(instrument = "theremin").toModel().instrument)
    }

    @Test
    fun `a document written now carries none of the legacy instrument keys`() {
        val preferences = UserPreferencesDocumentFormat.decode("{}").document.toModel().copy(
            instrument = UserPreferences.Instrument.MANDOLIN,
            tunings = mapOf("mandolin" to "mandolin_gdgd"),
            tunerSettings = TunerSettings(isChromatic = true),
        )
        val document = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(preferences.toDocument())).document
        assertEquals("", document.chordInstrument)
        assertEquals(emptyMap(), document.chordTunings)
        assertEquals("", document.tunerSettings.instrument)
        assertEquals(preferences.instrument, document.toModel().instrument)
        assertEquals(preferences.tunings, document.toModel().tunings)
        assertEquals(true, document.toModel().tunerSettings.isChromatic)
    }

    @Test
    fun `a legacy tuner instrument wins over the legacy chord instrument`() {
        val model = UserPreferencesDocumentFormat.decode("""{"chordInstrument": "other", "tunerSettings": {"instrument": "guitar_drop_d"}}""")
            .document.toModel()
        assertEquals(UserPreferences.Instrument.GUITAR, model.instrument)
        assertEquals(mapOf("guitar" to "guitar_drop_d"), model.tunings)
        assertEquals(false, model.tunerSettings.isChromatic)
    }

    @Test
    fun `a legacy chromatic tuner keeps the chord instrument and turns the chromatic switch on`() {
        val model = UserPreferencesDocumentFormat.decode("""{"chordInstrument": "ukulele", "tunerSettings": {"instrument": "chromatic"}}""")
            .document.toModel()
        assertEquals(UserPreferences.Instrument.UKULELE, model.instrument)
        assertEquals(emptyMap(), model.tunings)
        assertEquals(true, model.tunerSettings.isChromatic)
    }

    @Test
    fun `a legacy five-string bass tuning names the five-string bass and its tuning`() {
        val model = UserPreferencesDocumentFormat.decode("""{"tunerSettings": {"instrument": "bass_five_string_high_c"}}""").document.toModel()
        assertEquals(UserPreferences.Instrument.BASS_FIVE_STRING, model.instrument)
        assertEquals(mapOf("bass_five_string" to "bass_five_string_high_c"), model.tunings)
    }

    @Test
    fun `a legacy seven-string guitar in standard tuning names the seven-string guitar with no tuning entry`() {
        val model = UserPreferencesDocumentFormat.decode(
            """{"chordInstrument": "other", "chordTunings": {"ukulele": "ukulele_d"}, "tunerSettings": {"instrument": "guitar_seven_string"}}""",
        ).document.toModel()
        assertEquals(UserPreferences.Instrument.GUITAR_SEVEN_STRING, model.instrument)
        assertEquals(mapOf("ukulele" to "ukulele_d"), model.tunings)
    }

    @Test
    fun `an unknown legacy tuner instrument falls back on the legacy chord instrument and its tunings`() {
        val model = UserPreferencesDocumentFormat.decode(
            """{"chordInstrument": "banjo", "chordTunings": {"banjo": "banjo_open_d"}, "tunerSettings": {"instrument": "theremin"}}""",
        ).document.toModel()
        assertEquals(UserPreferences.Instrument.BANJO, model.instrument)
        assertEquals(mapOf("banjo" to "banjo_open_d"), model.tunings)
        assertEquals(false, model.tunerSettings.isChromatic)
    }

    @Test
    fun `tag colors survive a round trip, a color this version does not know included`() {
        val defaults = UserPreferencesDocumentFormat.decode("{}").document.toModel()
        assertEquals(emptyMap(), defaults.tagColors)
        val chosen = defaults.copy(tagColors = mapOf("christmas" to "red", "folk" to "ultraviolet"))
        assertEquals(chosen, UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(chosen.toDocument())).document.toModel())
        assertEquals(emptyMap(), UserPreferencesDocument(tagColors = mapOf("christmas" to " ")).toModel().tagColors)
    }

    @Test
    fun `an unknown enum ID falls back for that field only`() {
        val preferences = UserPreferencesDocument(
            sortingMode = "by_mood",
            uiMode = UserPreferences.UiMode.DARK.id,
            tagMatchMode = "sometimes",
            languageMatchMode = UserPreferences.MatchMode.ALL.id,
            tagSortingMode = "by_color",
            languageSortingMode = UserPreferences.LabelSortingMode.ALPHABETICAL.id,
        ).toModel()

        assertEquals(UserPreferences.SortingMode.BY_ARTIST, preferences.sortingMode)
        assertEquals(UserPreferences.UiMode.DARK, preferences.uiMode)
        assertEquals(UserPreferences.MatchMode.ANY, preferences.tagMatchMode)
        assertEquals(UserPreferences.MatchMode.ALL, preferences.languageMatchMode)
        assertEquals(UserPreferences.LabelSortingMode.BY_USAGE, preferences.tagSortingMode)
        assertEquals(UserPreferences.LabelSortingMode.ALPHABETICAL, preferences.languageSortingMode)
    }

    @Test
    fun `a stored font scale outside the range is clamped to it`() {
        assertEquals(UserPreferences.MAX_FONT_SCALE, UserPreferencesDocument(fontScale = 40f).toModel().fontScale)
        assertEquals(UserPreferences.MIN_FONT_SCALE, UserPreferencesDocument(fontScale = 0f).toModel().fontScale)
        assertEquals(UserPreferences.MIN_FONT_SCALE, UserPreferencesDocument(fontScale = -1f).toModel().fontScale)
        assertEquals(1.3f, UserPreferencesDocument(fontScale = 1.3f).toModel().fontScale)
    }

    @Test
    fun `a stored background warmth outside the range is clamped to it`() {
        assertEquals(UserPreferences.MAX_BACKGROUND_WARMTH, UserPreferencesDocument(backgroundWarmth = 40).toModel().backgroundWarmth)
        assertEquals(0, UserPreferencesDocument(backgroundWarmth = -1).toModel().backgroundWarmth)
        assertEquals(0, UserPreferencesDocumentFormat.decode("{}").document.toModel().backgroundWarmth)
        val saved = UserPreferencesDocumentFormat.encode(UserPreferencesDocument().toModel().copy(backgroundWarmth = 3).toDocument())
        assertEquals(3, UserPreferencesDocumentFormat.decode(saved).document.toModel().backgroundWarmth)
    }

    @Test
    fun `a stored font scale that is not a number falls back to the default`() {
        assertEquals(UserPreferences.DEFAULT_FONT_SCALE, UserPreferencesDocument(fontScale = Float.NaN).toModel().fontScale)
        assertEquals(UserPreferences.DEFAULT_FONT_SCALE, UserPreferencesDocument(fontScale = Float.POSITIVE_INFINITY).toModel().fontScale)
    }

    @Test
    fun `a document from before the notation keeps its German notation`() {
        assertEquals(UserPreferences.Notation.GERMAN, UserPreferencesDocument(isGermanNotationEnabled = true).toModel().chordSpelling.notation)
        assertEquals(UserPreferences.Notation.STANDARD, UserPreferencesDocument().toModel().chordSpelling.notation)
        assertEquals(
            UserPreferences.Notation.STANDARD,
            UserPreferencesDocument(notation = "standard", isGermanNotationEnabled = true).toModel().chordSpelling.notation,
        )
        assertEquals("german", UserPreferencesDocument(isGermanNotationEnabled = true).toModel().toDocument().notation)
    }

    @Test
    fun `a document from before the setlist cards had switches of their own shows on them what it showed on every card`() {
        val model = UserPreferencesDocument(isKeyShown = false, isTempoShown = false).toModel()
        assertEquals(UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false), model.songCardDetails)
        assertEquals(UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = true), model.setlistCardDetails)
    }

    @Test
    fun `a document from before the tempo could be picked follows the metronome where it showed the tempo`() {
        val model = UserPreferencesDocument(isTempoShown = true, isTempoShownOnSetlistCards = false).toModel()
        assertNull(model.songCardDetails.isTempoShown)
        assertEquals(false, model.setlistCardDetails.isTempoShown)
        assertNull(UserPreferencesDocument().toModel().setlistCardDetails.isTempoShown)
    }

    @Test
    fun `the cards of each list keep their own details through saving and reloading`() {
        val songCards = UserPreferences.CardDetails(isKeyShown = false, isTempoShown = true, isDurationShown = true)
        val setlistCards = UserPreferences.CardDetails(isKeyShown = true, isTempoShown = false, isDurationShown = false)
        val reloaded = UserPreferencesDocument().toModel().copy(songCardDetails = songCards, setlistCardDetails = setlistCards).toDocument().toModel()
        assertEquals(songCards, reloaded.songCardDetails)
        assertEquals(setlistCards, reloaded.setlistCardDetails)
        val unpicked = UserPreferencesDocument().toModel().copy(songCardDetails = songCards.copy(isTempoShown = null)).toDocument().toModel()
        assertNull(unpicked.songCardDetails.isTempoShown)
    }

    @Test
    fun `every notation survives saving and reloading`() {
        UserPreferences.Notation.entries.forEach { notation ->
            assertEquals(notation, UserPreferencesDocument(notation = notation.id).toModel().toDocument().toModel().chordSpelling.notation)
        }
        assertEquals("latin", UserPreferencesDocument(notation = "latin").toModel().toDocument().notation)
        assertEquals(UserPreferences.Notation.ROMAN, UserPreferencesDocument(notation = "roman").toModel().chordSpelling.notation)
        assertEquals(UserPreferences.Notation.STANDARD, UserPreferencesDocument(notation = "solfege").toModel().chordSpelling.notation)
    }

    @Test
    fun `metronome settings and tempos survive saving and reloading`() {
        val settings = MetronomeSettings(soundId = "cowbell", volume = 0.5f, subdivisionId = "triplets", isVisualBeatEnabled = false,
            isHapticBeatEnabled = true, beatLevels = mapOf("7/8" to listOf("accent", "normal", "accent", "normal", "accent", "normal", "normal")),
            bpm = 96, timeSignature = "7/8")
        val preferences = UserPreferencesDocument().toModel().copy(metronomeSettings = settings, tempos = mapOf("a.cho" to 96))
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(preferences.toDocument())).document.toModel()
        assertEquals(settings, reloaded.metronomeSettings)
        assertEquals(mapOf("a.cho" to 96), reloaded.tempos)
        assertEquals(MetronomeSettings(), UserPreferencesDocumentFormat.decode("{}").document.toModel().metronomeSettings)
    }

    @Test
    fun `tuner settings survive saving and reloading and a reference pitch out of range is held within it`() {
        val settings = TunerSettings(isChromatic = true, referencePitch = 432, hasTurnedOnMicrophone = true)
        val preferences = UserPreferencesDocument().toModel().copy(isTunerEnabled = false, tunerSettings = settings)
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(preferences.toDocument())).document.toModel()
        assertEquals(settings, reloaded.tunerSettings)
        assertEquals(false, reloaded.isTunerEnabled)
        assertEquals(TunerSettings(), UserPreferencesDocumentFormat.decode("{}").document.toModel().tunerSettings)
        assertEquals(466, UserPreferencesDocument(tunerSettings = TunerSettingsDocument(referencePitch = 1_000)).toModel().tunerSettings.referencePitch)
    }

    @Test
    fun `an owed onboarding survives saving and reloading, and a document from before it owes none`() {
        val preferences = UserPreferencesDocument().toModel().copy(isOnboardingPending = true)
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(preferences.toDocument())).document.toModel()
        assertEquals(true, reloaded.isOnboardingPending)
        assertEquals(false, UserPreferencesDocumentFormat.decode("{\"seenWhatsNewVersions\": [\"4.8.1\"]}").document.toModel().isOnboardingPending)
    }

    @Test
    fun `a metronome value out of range is held within it`() {
        val preferences = UserPreferencesDocument(metronomeSettings = MetronomeSettingsDocument(volume = 4f, bpm = 1_000), tempos = mapOf("a.cho" to 5, "b.cho" to 96))
            .toModel()
        assertEquals(1f, preferences.metronomeSettings.volume)
        assertEquals(300, preferences.metronomeSettings.bpm)
        assertEquals(mapOf("b.cho" to 96), preferences.tempos)
    }

    @Test
    fun `a tempo of the wrong shape costs only itself`() {
        val preferences = UserPreferencesDocumentFormat.decode("""{"tempos":{"a.cho":96,"b.cho":"fast"},"uiMode":"dark"}""").document.toModel()
        assertEquals(mapOf("a.cho" to 96), preferences.tempos)
        assertEquals(UserPreferences.UiMode.DARK, preferences.uiMode)
    }

    @Test
    fun `a song's accents survive saving and reloading`() {
        val beatLevels = mapOf("a.cho" to mapOf("3/4" to listOf("accent", "muted", "normal")))
        val preferences = UserPreferencesDocument().toModel().copy(beatLevels = beatLevels)
        val reloaded = UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(preferences.toDocument())).document.toModel()
        assertEquals(beatLevels, reloaded.beatLevels)
    }

    @Test
    fun `a song's accents of the wrong shape cost only themselves`() {
        val preferences = UserPreferencesDocumentFormat.decode(
            """{"beatLevels":{"a.cho":{"3/4":["accent","normal","normal"],"4/4":"loud"},"b.cho":[1,2]},"uiMode":"dark"}""",
        ).document.toModel()
        assertEquals(mapOf("a.cho" to mapOf("3/4" to listOf("accent", "normal", "normal"))), preferences.beatLevels)
        assertEquals(UserPreferences.UiMode.DARK, preferences.uiMode)
    }
}
