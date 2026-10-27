/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.scales.ScaleGroup
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.chordpro.scales.SongScaleSuggestions
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.ChordKeys
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer

/**
 * The key of the home [SongScaleSuggestions] finds among [groups] (`ScaleGroup.isHome`), in the standard notation and in
 * the frame of the chords it was found in: its root, minor (`Am`) where the home scale has a minor third and no major
 * one (natural minor, Dorian, Phrygian, a riff's minor pentatonic), major otherwise (major, Lydian, Mixolydian, Phrygian
 * dominant). Null for a song with no home, in several keys or in none the engine could tell.
 *
 * It is the engine's rather than the file's `{key}` because the engine has already read the key in force against the
 * chords, a capo chart's included (`{key: G}{capo: 5}` over D shapes is D, sounding G), so it is the key of the chords
 * on the page, and the one the Scales section names.
 */
internal fun homeKeyOf(groups: List<ScaleGroup>): String? {
    val home = groups.firstOrNull { it.isHome } ?: return null
    val intervals = home.scales.firstOrNull()?.intervals ?: return home.rootName
    return if (MINOR_THIRD in intervals && MAJOR_THIRD !in intervals) home.rootName + "m" else home.rootName
}

/** The key [song]'s file names, null where it names none. */
internal fun writtenKeyOf(song: ChordProSong) = song.metadata.key?.takeIf { it.isNotBlank() }

/**
 * The keys the Chords section's cells of [song], in the standard notation as the page plays it, are in, on the screen
 * and in the PDF alike: at home, the home its [analysis] finds ([homeKeyOf]), else the key its `{key}` and its capo say
 * the chords are in (`SongKeys.ofChords`, the one reading of a capo chart's key). The written key is weighed against the
 * shapes only where the walk found no home, and never in a songbook ([isSongbook], which is never walked), too long to
 * weigh: that one is counted against its key as written, with no key changes. Elsewhere a chord first played after a
 * key change, directed or written out, is in the key it moved to ([ChordKeys.moved]), counted from the home's stretch.
 */
internal fun chordKeysOf(song: ChordProSong, analysis: SongScaleAnalysis?, isSongbook: Boolean): ChordKeys? {
    if (isSongbook) return writtenKeyOf(song)?.let { ChordKeys(it, emptyMap()) }
    val homeGroup = analysis?.groups?.firstOrNull { it.isHome }
    val home = analysis?.let { homeKeyOf(it.groups) } ?: SongKeys.ofChords(song) ?: return null
    // The analysis's groups hold its numbered copies of the blocks, so the home's stretch is found among those.
    val walked = if (homeGroup != null && analysis != null) song.copy(blocks = analysis.numbered) else song
    return ChordKeys(home, SongKeys.keysOfChords(walked, homeGroup?.blocks?.firstOrNull(), home))
}

/**
 * The key [song], in the standard notation with its own `{transpose}` applied, is played in where its file names none:
 * its home ([homeKeyOf]), worked out off the main thread as its text is read (see `InferredSongKeys`), for the app bar to
 * name. Null for a song whose file names a key, which is named by that, and for a songbook (over
 * [LayoutBudget.MAX_CUT_SECTION_COUNT] sections), which is no song in one key.
 */
internal fun inferredKeyOf(song: ChordProSong): String? {
    if (writtenKeyOf(song) != null) return null
    if (song.blocks.count { it is ChordProBlock.Section } > LayoutBudget.MAX_CUT_SECTION_COUNT) return null
    return homeKeyOf(SongScaleSuggestions.of(song))
}

/** [inferredKeyOf] the song file [text], read as the screen and the PDF both read it, so that they infer one way. */
internal fun SongRenderer.inferredKeyOfText(text: String) = inferredKeyOf(transposedSong(text = text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default))

private const val MINOR_THIRD = 3
private const val MAJOR_THIRD = 4
