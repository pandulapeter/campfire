/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * Everything the user chose on this device, written as one document. Every map keyed by a song's file name is listed
 * in [withSongRenamed] and [withoutSongOverrides], so that a new one is added there and a renamed or deleted song never
 * leaves an override behind for the next song written under its name.
 */
data class UserPreferences(
    /**
     * Read only mode, for the app while it is being played from rather than worked in: everything that would change
     * a song, a setlist or the way either is read is taken out of the UI, so that nothing can be edited, exported,
     * deleted or transposed by a tap that was meant to turn a page. It reaches every screen but the settings one,
     * which is where the mode is switched back off and therefore keeps working.
     */
    val isPerformanceModeEnabled: Boolean,
    /**
     * Whether the setlists screen shows the setlists that have been archived ([Setlist.isArchived]) as well as the
     * ones still in use. Off by default, which is the whole point of archiving one.
     */
    val shouldShowArchivedSetlists: Boolean,
    /**
     * Whether songs are shown with their chords and everything that only matters to whoever plays them: the key, the
     * transposition and the capo. Off, the app is a lyrics sheet for a singer.
     */
    val areChordsEnabled: Boolean,
    /**
     * Whether the app has setlists at all: their tab and every way of putting a song into one. Off, the files stay in
     * the library and keep travelling through imports, exports and sync runs; they are only out of sight.
     */
    val areSetlistsEnabled: Boolean,
    /**
     * Whether the app has a metronome: its tab, the song details screen's click and panel, and the tempo and time
     * signature wherever a song is read. The Song defaults sheet leaves them out too; the file keeps them.
     */
    val isMetronomeEnabled: Boolean,
    /**
     * Whether the app has a tuner: its tab, and the song details screen's way to it. Off, nothing in the app can ask for
     * the microphone; [tunerSettings] stays as it was.
     */
    val isTunerEnabled: Boolean = true,
    /**
     * Multiplier applied to the text size of the song details screen, [DEFAULT_FONT_SCALE] being the default, and
     * never outside [MIN_FONT_SCALE] to [MAX_FONT_SCALE].
     */
    val fontScale: Float,
    val sortingMode: SortingMode,
    val setlistSortingMode: SetlistSortingMode,
    val uiMode: UiMode,
    val themeColor: ThemeColor,
    /**
     * How far the backgrounds, the surfaces and the text on them are turned towards the warm brown of old paper, from
     * none at all to [MAX_BACKGROUND_WARMTH], in whichever palette [themeColor] picks, the light theme dimmed a little
     * with it. No contrast ratio of the palette changes with it.
     */
    val backgroundWarmth: Int = 0,
    /**
     * Whether the app icon is in [themeColor] wherever the platform lets the app change it, rather than the app's own
     * icon of [ThemeColor.CAMPFIRE] whatever the theme is.
     */
    val isAppIconThemed: Boolean,
    /**
     * Whether the songs' cover images are shown. Off, nothing is fetched or drawn, which is also what keeps the app from
     * asking any host a song's `{meta: cover …}` names for anything.
     */
    val isCoverArtEnabled: Boolean,
    /**
     * Whether the Edit song details form offers to look the song up in MusicBrainz and the iTunes Search API. Off, the
     * button is gone and nothing is ever asked of either catalogue for a song's details; the cover search is
     * [isCoverArtEnabled]'s.
     */
    val isSongDetailsLookupEnabled: Boolean = true,
    /**
     * Whether the sections a song gives no name of their own are numbered by their kind where it has several of one
     * ("Verse 1", "Verse 2"), the way the files of a library were once written by hand. Off, they are headed by the
     * plain name of their kind.
     */
    val shouldNumberSections: Boolean,
    val language: Language,
    val chordSpelling: ChordSpelling, // How the chords of a song are written when it is displayed.
    /**
     * Whether a song opens with the diagrams of its chords, in a section of their own after the controls of how it is
     * played. Only while [areChordsEnabled]: with the chords gone there is nothing to finger.
     */
    val areChordDiagramsEnabled: Boolean = true,
    /**
     * What the cards of the Songs screen say after the artist. Never the song details screen, which always shows the key
     * and the tempo a song is played in, since that is where they are changed.
     */
    val songCardDetails: CardDetails = CardDetails.SongsDefault,
    /** What the cards of the Setlists screen say after the artist, the twin of [songCardDetails]. */
    val setlistCardDetails: CardDetails = CardDetails.SetlistsDefault,
    /** The instrument the diagrams are drawn for, or [ChordInstrument.OTHER] for none. */
    val chordInstrument: ChordInstrument = ChordInstrument.GUITAR,
    /**
     * The shape the player has chosen for a chord, by [ChordInstrument.id] and then by the chord's id (its notes, the
     * same for every spelling of it), the value the shape as `ChordVoicings.write` writes it. One choice per chord for
     * the whole library, since which F somebody plays is a habit of their hands rather than a reading of one song; a
     * song's own `{define}` still wins in that song. Never exported, but synced, like [transpositions]. An instrument
     * this version does not know is kept as it is, for the version that does.
     */
    val chordVoicings: Map<String, Map<String, String>> = emptyMap(),
    /** Whether the chord diagrams are folded away, one choice for every song rather than one per song like [foldedSections]. */
    val isChordSectionFolded: Boolean = false,
    /**
     * Song file name to semitones, for songs opened from the library rather than from a setlist. Never exported, but
     * synced: with [tempos] and [capos] it is what every device connected to one cloud folder shares through its
     * `preferences.json`, since it is how the band plays the song rather than how one reader reads it.
     */
    val transpositions: Map<String, Int>,
    /**
     * Song file name to beats per minute, for songs opened from the library rather than from a setlist: the twin of
     * [transpositions], and like it never exported but synced. A song opened from a setlist reads its entry's
     * `Setlist.Entry.tempo` instead, never this.
     */
    val tempos: Map<String, Int> = emptyMap(),
    /**
     * Song file name to the fret it is capoed at, for songs opened from the library rather than from a setlist: the
     * third of these, read and written exactly as [tempos] is. A song opened from a setlist reads its entry's
     * `Setlist.Entry.capo` instead, never this.
     */
    val capos: Map<String, Int> = emptyMap(),
    /**
     * Song file name to the accents tapped on its metronome panel, by time signature as written ("7/8") and then one
     * `BeatLevel` id per beat, so that a song whose bar is 3+3+2 keeps it while every other song in 8/8 is clicked as
     * [MetronomeSettings.beatLevels] says. A signature a song has none for, or a list whose length is not its number of
     * beats, falls back on those. One per song wherever it is opened from, since the accents are the song's bar rather
     * than one set's reading of it; never exported, but synced, like [tempos].
     */
    val beatLevels: Map<String, Map<String, List<String>>> = emptyMap(),
    /**
     * Song file name to the sections (and the tabs and grids inside them) the reader has folded away on the song details
     * screen, as the opaque keys that screen names them by. One set per song, wherever the song is opened from: unlike a
     * transposition, which the band plays the song in, how much of it one reader keeps open is their own business, so it
     * is never written into a setlist, exported or synced.
     */
    val foldedSections: Map<String, Set<String>>,
    /**
     * How the tags selected in the song filter combine. The selection itself is not a preference and is never
     * stored — it lives in the presentation layer for as long as the app runs — but which of the two readings the
     * user prefers is a standing choice, like the sorting mode.
     */
    val tagMatchMode: MatchMode,
    /** How the languages selected in the song filter combine, a standing choice of its own like [tagMatchMode]. */
    val languageMatchMode: MatchMode,
    /**
     * The order the library's tags are offered in, by the song filter and by the dialog that puts them on a song alike:
     * one standing choice, since both are the same list looked at from two places.
     */
    val tagSortingMode: LabelSortingMode,
    /** The order the library's languages are offered in, a standing choice of its own like [tagSortingMode]. */
    val languageSortingMode: LabelSortingMode,
    /**
     * The color the user gave a tag, by its [tagKey] and then the color's id, which `:presentation` reads into its
     * palette (an id it does not know, a later version's, is drawn as no color and kept as it is). A tag left at the
     * default has no entry. One color per tag for the whole library, never exported but synced, like [chordVoicings].
     *
     * Nothing is ever dropped from it when the last song loses a tag: a tag only exists while a song carries it, so
     * the library not holding one is as often a moment (the editor between two saves, a sync run halfway done, a library
     * not read yet) as a decision, and a tag that comes back gets its color back.
     */
    val tagColors: Map<String, String> = emptyMap(),
    val printSettings: PrintSettings = PrintSettings(),
    val metronomeSettings: MetronomeSettings = MetronomeSettings(),
    /** Never exported or synced: which instrument one device tunes is that device's business. */
    val tunerSettings: TunerSettings = TunerSettings(),
    /** Versions already introduced here, including the first installed version whose introduction is skipped. */
    val seenWhatsNewVersions: Set<String> = emptySet(),
    /**
     * The content hash of every demo file this device planted, as it was written, keyed by its library path
     * (`songs/<file name>`, `setlists/<file name>`: the folder is `LibraryFileKind.id`). Sync takes the cloud folder's
     * version of a file that still has exactly that content instead of keeping both, see `SyncRepository`. Never
     * exported or synced.
     */
    val demoLibraryContentHashes: Map<String, String> = emptyMap(),
) {

    /**
     * These preferences with everything kept for the song filed as [fileName] moved to [newFileName], or dropped where
     * that is null - every map keyed by a song's file name, so that a new one is added here and nowhere else. A song with
     * nothing kept answers an equal copy, which is a change that writes nothing.
     */
    fun withSongRenamed(fileName: String, newFileName: String?): UserPreferences = copy(
        transpositions = transpositions.movedTo(fileName, newFileName),
        tempos = tempos.movedTo(fileName, newFileName),
        capos = capos.movedTo(fileName, newFileName),
        beatLevels = beatLevels.movedTo(fileName, newFileName),
        foldedSections = foldedSections.movedTo(fileName, newFileName),
    )

    /** These preferences with nothing kept for any song, for a library that has been emptied. */
    fun withoutSongOverrides(): UserPreferences = copy(
        transpositions = emptyMap(),
        tempos = emptyMap(),
        capos = emptyMap(),
        beatLevels = emptyMap(),
        foldedSections = emptyMap(),
    )

    /** The map with the entry of [fileName] put under [newFileName], or dropped where that is null. */
    private fun <T> Map<String, T>.movedTo(fileName: String, newFileName: String?): Map<String, T> {
        val value = this[fileName] ?: return this
        return if (newFileName == null) this - fileName else this - fileName + (newFileName to value)
    }

    companion object {
        /** The text size a song opens at, and the one a stored size that is not a size falls back on. */
        const val DEFAULT_FONT_SCALE = 1f

        /**
         * How small a song is ever read. It bounds the whole song details screen rather than the lyrics alone - the
         * section headers, the chords and the controls of the first section all follow it - so it is the size at which
         * everything on that screen is still worth having on it: smaller lyrics only fit more of a song on the screen
         * at a size nobody reads them at, and its steppers would be too small to hit.
         */
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 2.5f

        /** The warmest [backgroundWarmth], full sepia; the steps below it are what the settings slider stops at. */
        const val MAX_BACKGROUND_WARMTH = 4
    }

    /**
     * What several selected values of one filter group mean together: a song that carries any one of them, or one
     * that carries all.
     */
    enum class MatchMode(val id: String) {
        ANY("any"),
        ALL("all"),
    }

    /**
     * How the values of a filter group are ordered: [BY_USAGE] puts the ones the most songs carry first, which is what a
     * large library is filtered by, and [ALPHABETICAL] is for looking one up by its name.
     */
    enum class LabelSortingMode(val id: String) {
        BY_USAGE("by_usage"),
        ALPHABETICAL("alphabetical"),
    }

    enum class SortingMode(val id: String) {
        BY_TITLE("by_title"),
        BY_ARTIST("by_artist"),
    }

    /**
     * The order the setlists screen lists the setlists in. [BY_DATE] is [Setlist.date], the latest on top and the
     * setlists of one day by their title; an archived setlist comes after every other one whichever of these is
     * picked, since it is only on the screen at all because the user asked to see what has been put away.
     */
    enum class SetlistSortingMode(val id: String) {
        BY_DATE("by_date"),
        BY_TITLE("by_title"),
    }

    enum class UiMode(val id: String) {
        LIGHT("light"),
        DARK("dark"),
        SYSTEM_DEFAULT("system_default"),
    }

    /**
     * Which set of colors the app is painted in, which is a separate question from [UiMode]: every one of these has a
     * light and a dark scheme, and the two choices are combined rather than ranked.
     *
     * [CAMPFIRE] is the app's own, the purple and orange of its icon, and [GRAY] the plainest of the rest. Every
     * stored `campfire` is read as the app's own, including one written while that id stood for the gray - a
     * preference left at the default and one that chose the gray cannot be told apart - so the gray has an id of its
     * own and that one must not be given back to it.
     *
     * [SYSTEM] is the scheme the operating system derives from the user's wallpaper and only exists on Android 12 and
     * above, so it is stored like any other value but offered only where it can be honored; anywhere else it falls
     * back to [CAMPFIRE]. [ORANGE] is the color the app icon is drawn in by hand, and every other icon is generated
     * from.
     */
    enum class ThemeColor(val id: String) {
        CAMPFIRE("campfire"),
        SYSTEM("system"),
        RED("red"),
        ORANGE("orange"),
        YELLOW("yellow"),
        GREEN("green"),
        TEAL("teal"),
        BLUE("blue"),
        PURPLE("purple"),
        PINK("pink"),
        GRAY("gray"),
    }

    /**
     * How a chord is written for the reader, and never in a file: the library, and everything that is synced or
     * exported, stays in [Notation.STANDARD]. The editor shows its text in [notation] too, and writes it back in the
     * standard one.
     *
     * They are one value because the viewer needs them as one: it re-parses a song whenever the spelling changes, and
     * two separate flags would mean two things to keep in step at every call site.
     */
    data class ChordSpelling(
        val accidentals: Accidentals,
        /**
         * Applied after the accidentals, on the result they produce. A numbering ([Notation.isNumbering]) ignores them
         * for the steps it writes, which are always `b2 b3 #4 b6 b7`, and they still spell the key it names in letters.
         */
        val notation: Notation,
    ) {

        companion object {
            val Default = ChordSpelling(accidentals = Accidentals.ORIGINAL, notation = Notation.STANDARD)
        }
    }

    /**
     * What a song card says after the artist, chosen for each list on its own: a setlist's cards are where a set is
     * timed, the library's where a song is found. The key is only ever shown with the chords switched on and the tempo
     * with the metronome, whatever these say, so that what they are set to is kept for when those come back.
     */
    data class CardDetails(
        val isKeyShown: Boolean,
        val isTempoShown: Boolean,
        val isDurationShown: Boolean,
    ) {

        companion object {
            val SongsDefault = CardDetails(isKeyShown = true, isTempoShown = true, isDurationShown = false)
            val SetlistsDefault = CardDetails(isKeyShown = true, isTempoShown = true, isDurationShown = true)
        }
    }

    /**
     * The names the chords are written with. [GERMAN] writes the note [STANDARD] calls `B` as `H`, and its `Bb` as `B`,
     * which is what a reader in Central Europe or Scandinavia grew up with, and [LATIN] names the notes `Do Re Mi Fa Sol
     * La Si`, as Southern Europe and Latin America do. [NASHVILLE] and [ROMAN] count the chords from the song's key
     * instead (`1 4 5 6-`, `I IV V vi`), the way session players and worship teams read a chart.
     */
    enum class Notation(val id: String) {
        STANDARD("standard"),
        GERMAN("german"),
        LATIN("latin"),
        NASHVILLE("nashville"),
        ROMAN("roman"),
        ;

        /**
         * Whether the chords are counted from the song's key rather than named. Such a notation is only ever shown: a
         * song with no key stays in letters, and so does every text that is typed, since a number means nothing until
         * the key it counts from is written — so the editor's field is in [STANDARD] under one.
         */
        val isNumbering get() = this == NASHVILLE || this == ROMAN

        /** The notation a text is typed in where this is the one read in, see [isNumbering]. */
        val forTyping get() = if (isNumbering) STANDARD else this
    }

    /**
     * The spelling of the black keys the viewer writes. [ORIGINAL] leaves it to the song — its key, or the accidentals
     * its chords are already written with — which is what a reader who never thought about it wants; the other two
     * are for the one who always reads the same one, and hold even for a song that is not transposed at all.
     */
    enum class Accidentals(val id: String) {
        ORIGINAL("original"),
        FLATS("flats"),
        SHARPS("sharps"),
    }

    /**
     * The instrument the chord diagrams are drawn for, the twin of `:chordpro`'s, which this module does not see. [OTHER]
     * is an instrument `:chordpro` has no shapes for (a bass, an eight-string guitar, a guitar in another tuning), so no
     * diagram is drawn: one for the six strings of a standard guitar would be wrong on it, which is worse than none.
     */
    enum class ChordInstrument(val id: String) {
        GUITAR("guitar"),
        UKULELE("ukulele"),
        MANDOLIN("mandolin"),
        KEYBOARD("keyboard"),
        OTHER("other"),
    }

    enum class Language(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu"),
        SYSTEM_DEFAULT("system_default"),
    }
}
