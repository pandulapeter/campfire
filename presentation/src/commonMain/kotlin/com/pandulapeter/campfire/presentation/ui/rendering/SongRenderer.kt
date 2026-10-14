/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.rendering

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabs
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.edit.ChordProLanguages
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.text.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.text.NormalizeTextUseCase
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.mapper.toPreferFlats
import com.pandulapeter.campfire.presentation.ui.tabs.TabReading
import org.koin.core.annotation.Single

/**
 * What a song and its key look like on the screen, in the editor and in the PDF, and the folded text the pickers search:
 * pure functions of their arguments over `:chordpro` and two text use cases, so that a screen needs this rather than
 * the whole view model to draw a key. Everything that depends on the notation the editor's field is written in takes it
 * as a parameter; the view model's `editorNotation` is where it comes from.
 *
 * Public because the public view model's constructor takes it.
 */
@Single
class SongRenderer(
    private val normalizeText: NormalizeTextUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
) {

    /**
     * Parses a song file and applies the file's own `{transpose}` — the one it opens with and the ones further down
     * it — the transposition the user picked and the spelling they prefer, which is what the viewer renders. Build it
     * once for each set of the three rather than on every recomposition, which for a long song would be wasteful. It
     * touches nothing but its arguments and stateless functions, so it may run on a background thread, and it does:
     * see `rememberSongLyricsModel`.
     */
    internal fun renderSong(
        text: String,
        transposition: Int,
        spelling: UserPreferences.ChordSpelling,
        writtenIn: UserPreferences.Notation = UserPreferences.Notation.STANDARD,
        tabs: TabReading? = null,
    ) = notatedSong(transposedSong(text, transposition, spelling, writtenIn, tabs), spelling)

    /**
     * [renderSong] up to its last step: the song moved and spelled as the viewer shows it, but still in the standard
     * notation, which is what its chords are read from for their diagrams — in a numbering the page names a chord by a
     * step of the key, which says nothing about the notes without the stretch of the song it stands in.
     *
     * @param tabs Who the tabs are shown to: of the ones written for several instruments only theirs are kept, every
     *   tab is moved to their strings along with the transposition, and what was decided for them is written above it
     *   (see `ChordProTabs`). Null leaves every tab as the transposition alone moves it, which is all a reader of the
     *   chords alone needs.
     */
    internal fun transposedSong(
        text: String,
        transposition: Int,
        spelling: UserPreferences.ChordSpelling,
        writtenIn: UserPreferences.Notation = UserPreferences.Notation.STANDARD,
        tabs: TabReading? = null,
    ): ChordProSong {
        val parsed = ChordProParser.parse(text, writtenIn.toChordNotation())
        val selected = tabs?.takeIf { it.selects }?.let { ChordProTabs.select(parsed, it.player.selectors) } ?: parsed
        // The file's own {transpose} (the one it opens with), the reader's, and the modulations further down: all
        // three are the transposition's to apply, and it leaves a song none of them move exactly as it is. The tabs of
        // an instrument they are hidden from are not worth moving.
        val transposed = ChordProTransposer.transpose(selected, parsed.metadata.transpose + transposition, spelling.accidentals.toPreferFlats(), tabs?.player?.takeUnless { it.hidesTabs })
        return tabs?.let { ChordProTabs.withNotices(transposed, it.player, it.labels::describe) } ?: transposed
    }

    /** The last step of [renderSong], and on the model only: the file stays in the standard notation, which the transposition works in. */
    fun notatedSong(song: ChordProSong, spelling: UserPreferences.ChordSpelling) = ChordProNotation.toNotation(song, spelling.notation.toChordNotation())

    /**
     * The key a song sounds in once everything that moves it has been applied: the file's own `{transpose}`, the
     * transposition the reader picked for it, the fret it is capoed at and the spelling they read chords in. Null for
     * a file that declares no `{key}`, which the lists then say nothing about.
     *
     * It is what [renderSong] arrives at, worked out without the song's text: the library's metadata is read at
     * startup and its lyrics are not, so a list that had to parse a file to name its key would be reading the whole
     * library a second time to fill in one line of each row. Both steps rewrite the key of whatever song they
     * are handed, so what they are handed here is a song that is nothing but that key.
     *
     * @param capo The fret the song is capoed at, which raises everything fretted above it by that many semitones.
     * The chords on the page are the shapes the player frets and are left where they are, so this is the one of the
     * four that moves the sounding key without moving a single chord: zero is what names the written key instead.
     */
    fun renderKey(song: Song, transposition: Int, capo: Int, spelling: UserPreferences.ChordSpelling) =
        renderKey(key = song.key, transpose = song.transpose, transposition = transposition, capo = capo, spelling = spelling)

    /**
     * [renderKey] for a song known only by the three things it depends on: the [key] its file declares, the
     * [transpose] it opens with and the [capo] it is played at.
     */
    fun renderKey(key: String?, transpose: Int, transposition: Int, capo: Int, spelling: UserPreferences.ChordSpelling) = key?.let {
        val keyOnly = ChordProSong(metadata = ChordProMetadata(key = it), blocks = emptyList())
        ChordProNotation.toNotation(
            ChordProTransposer.transpose(keyOnly, transpose + transposition + capo, spelling.accidentals.toPreferFlats(), null),
            spelling.notation.toChordNotation(),
        ).metadata.key
    }

    /** Formats the current editor draft without saving it or changing its chord notation. */
    fun prettifyText(text: String) = ChordProPrettifier.prettify(text)

    /**
     * Transposes the chords of the editor's text in place, leaving everything else exactly as it was. Unlike the
     * viewer's transposition this rewrites the file: it is what the editor's "transpose text" does. The text is in the
     * editor's [notation], and is transposed in the standard one, which is the only one a semitone means anything in.
     */
    fun transposeText(text: String, semitones: Int, accidentals: UserPreferences.Accidentals, notation: UserPreferences.Notation) =
        ChordProNotation.convertText(
            ChordProTransposer.transposeText(fileTextOf(text, notation), semitones, accidentals.toPreferFlats()),
            UserPreferences.Notation.STANDARD.toChordNotation(),
            notation.toChordNotation(),
        )

    /**
     * The text of a file as the editor shows it: in [notation], a file written before every file was in the standard
     * notation brought into it on the way. The last answer is kept, since the view model's `hasUnsavedEditorChanges`
     * asks again about the same file on every keystroke. It is only asked on the main thread, and the entry is an
     * immutable [EditorText] replaced whole, so a read sees an old or a new entry, never half of one.
     */
    fun editorTextOf(fileText: String, notation: UserPreferences.Notation): String {
        lastEditorText?.takeIf { it.fileText == fileText && it.notation == notation }?.let { return it.text }
        return ChordProNotation.convertText(fileText, UserPreferences.Notation.STANDARD.toChordNotation(), notation.toChordNotation()).also { text ->
            lastEditorText = EditorText(fileText = fileText, notation = notation, text = text)
        }
    }

    private var lastEditorText: EditorText? = null

    /** Follows the editor's text as it is typed, see `ChordProSummaryCache`; its key comes out in the standard notation. */
    fun editorSummaryCache(notation: UserPreferences.Notation) = ChordProSummaryCache(notation.toChordNotation())

    /** A key in the standard notation, as the editor's field written in [notation] would write it. */
    fun editorKeyOf(key: String, notation: UserPreferences.Notation) = ChordProNotation.toNotation(
        song = ChordProSong(metadata = ChordProMetadata(key = key), blocks = emptyList()),
        notation = notation.toChordNotation(),
    ).metadata.key

    /** The editor's text, written in [notation], as the file is to hold it: in the standard notation. */
    fun fileTextOf(editorText: String, notation: UserPreferences.Notation) =
        ChordProNotation.convertText(editorText, notation.toChordNotation(), UserPreferences.Notation.STANDARD.toChordNotation())

    /**
     * Accent and case insensitive text, for a screen that has to sort or search through something the library did
     * not put in order for it - the picker of every language there is, which is ordered by a name that depends on
     * the language the app is set to and so cannot be ordered anywhere below the UI.
     */
    fun normalize(text: String) = normalizeText(text)

    /** What the pickers' search fields and the tag suggestions compare, the same key the two list screens search by. */
    fun normalizeForSearch(text: String) = normalizeSearchText(text)

    /**
     * The language a piece of text names, for the picker's search field: a reader who knows a song is in Hungarian
     * may well type `hun` or `HU` rather than the word the app would show them, and either has to find the one row
     * the library files that language under.
     */
    fun languageCode(value: String) = ChordProLanguages.code(value)

    /** [text] is [fileText] as an editor showing [notation] shows it, see [editorTextOf]. */
    private class EditorText(val fileText: String, val notation: UserPreferences.Notation, val text: String)
}
