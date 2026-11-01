/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.data.model.song.Song

/**
 * The [RANDOM_PICK_COUNT] of [songs] (the ones the list shows, in its order) drawn for [seed], none while there are
 * [RANDOM_PICKS_MIN_SONGS] or fewer: in a short list every song is in sight already. In the order they were drawn.
 *
 * Each song is ranked on its own ([pickRank]) rather than the list shuffled with the seed, so that a song added, edited
 * or deleted by a sync run changes the picks only where it is one of them, or now outranks one.
 */
internal fun randomPicks(songs: List<Song>, seed: Long): List<Song> =
    if (songs.size <= RANDOM_PICKS_MIN_SONGS) emptyList() else songs.sortedBy { pickRank(it.fileName, seed) }.take(RANDOM_PICK_COUNT)

/**
 * Where the song stored as [fileName] ranks among the picks drawn for [seed]: FNV-1a over the name's UTF-16 units,
 * then SplitMix64's finalizer to spread the seed over every bit, spelled out rather than taken from String.hashCode,
 * whose value no platform promises, so that a test can say which songs a seed picks.
 */
internal fun pickRank(fileName: String, seed: Long): Long {
    var hash = FNV_OFFSET_BASIS
    fileName.forEach { char -> hash = (hash xor char.code.toLong()) * FNV_PRIME }
    var mixed = hash xor (seed * GOLDEN_GAMMA)
    mixed = (mixed xor (mixed ushr 30)) * MIX_MULTIPLIER_1
    mixed = (mixed xor (mixed ushr 27)) * MIX_MULTIPLIER_2
    return mixed xor (mixed ushr 31)
}

internal const val RANDOM_PICK_COUNT = 5

/** The list has to show more songs than this for the picks to be drawn from it. */
internal const val RANDOM_PICKS_MIN_SONGS = 20

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
private const val MIX_MULTIPLIER_1 = -0x40a7b892e31b1a47L
private const val MIX_MULTIPLIER_2 = -0x6b2fb644ecceee15L
