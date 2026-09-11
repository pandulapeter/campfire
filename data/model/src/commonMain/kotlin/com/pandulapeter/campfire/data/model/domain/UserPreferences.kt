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

data class UserPreferences(
    /**
     * Read only mode, for the app while it is being played from rather than worked in: everything that would change
     * a song, a setlist or the way either is read is taken out of the UI, so that nothing can be edited, exported,
     * deleted or transposed by a tap that was meant to turn a page. It reaches every screen but the settings one,
     * which is where the mode is switched back off and therefore keeps working.
     */
    val isPerformanceModeEnabled: Boolean,
    val shouldShowSongsWithoutChords: Boolean,
    val isLyricsOnlyModeEnabled: Boolean,
    val isHorizontalSectionFlowEnabled: Boolean, // Whether the song sections are read across the columns (then downwards) instead of column by column.
    val fontScale: Float, // Multiplier applied to the text size of the song details screen, 1 being the default.
    val sortingMode: SortingMode,
    val uiMode: UiMode,
    val themeColor: ThemeColor,
    val language: Language,
    val chordSpelling: ChordSpelling, // How the chords of a song are written when it is displayed.
    /** Song file name to semitones, for songs opened from the library rather than from a setlist. */
    val transpositions: Map<String, Int>,
    /**
     * The tags the song lists are narrowed to, empty when every song is shown. A tag no song carries any more is
     * kept rather than pruned, exactly like a transposition of a song that was deleted: the filter ignores it, and
     * it starts working again the moment a song is tagged that way.
     */
    val selectedTags: Set<String>,
    val tagMatchMode: TagMatchMode,
) {

    /** What several selected tags mean together: a song that carries any one of them, or one that carries all. */
    enum class TagMatchMode(val id: String) {
        ANY("any"),
        ALL("all"),
    }

    enum class SortingMode(val id: String) {
        BY_TITLE("by_title"),
        BY_ARTIST("by_artist"),
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
     * [SYSTEM] is the scheme the operating system derives from the user's wallpaper and only exists on Android 12 and
     * above, so it is stored like any other value but offered only where it can be honored; anywhere else it falls
     * back to [CAMPFIRE], the app's own orange.
     */
    enum class ThemeColor(val id: String) {
        CAMPFIRE("campfire"),
        SYSTEM("system"),
        RED("red"),
        YELLOW("yellow"),
        GREEN("green"),
        TEAL("teal"),
        BLUE("blue"),
        PURPLE("purple"),
        PINK("pink"),
    }

    /**
     * How a chord is written on screen, and only there: neither half of this ever reaches a file. The library, and
     * everything that is synced, exported or opened in the editor, stays in the one notation the app writes.
     *
     * They are one value because the viewer needs them as one: it re-parses a song whenever the spelling changes, and
     * two separate flags would mean two things to keep in step at every call site.
     */
    data class ChordSpelling(
        val accidentals: Accidentals,
        /**
         * German notation, where the note written `B` here is written `H`, and the one written `Bb` here is written
         * `B`. It is what a reader in Central Europe or Scandinavia grew up with, and it is applied after the
         * accidentals, on the result they produce.
         */
        val isGermanNotationEnabled: Boolean,
    ) {

        companion object {
            val Default = ChordSpelling(accidentals = Accidentals.ORIGINAL, isGermanNotationEnabled = false)
        }
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

    enum class Language(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu"),
        SYSTEM_DEFAULT("system_default"),
    }
}
