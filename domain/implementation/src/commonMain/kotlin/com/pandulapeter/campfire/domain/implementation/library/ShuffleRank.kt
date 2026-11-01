/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.library

/**
 * Where the song stored as [fileName] goes in the library shuffled for [hour]: the songs are listed by this rank.
 *
 * A rank of each song on its own, rather than one seeded shuffle of the whole list, so that a song added, deleted or
 * renamed within the hour moves only itself, and the rest of the list stays in the order it was found in. The hash is
 * spelled out (FNV-1a over the UTF-16 units, then SplitMix64's finalizer to spread the hour over every bit) instead of
 * `String.hashCode`, whose value no platform promises, so an hour is the same order on every device of a synced library.
 */
internal fun shuffleRank(fileName: String, hour: Int): Long {
    var hash = FNV_OFFSET_BASIS
    fileName.forEach { char ->
        hash = (hash xor char.code.toLong()) * FNV_PRIME
    }
    var mixed = hash xor (hour.toLong() * GOLDEN_GAMMA)
    mixed = (mixed xor (mixed ushr 30)) * MIX_MULTIPLIER_1
    mixed = (mixed xor (mixed ushr 27)) * MIX_MULTIPLIER_2
    return mixed xor (mixed ushr 31)
}

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
private const val MIX_MULTIPLIER_1 = -0x40a7b892e31b1a47L
private const val MIX_MULTIPLIER_2 = -0x6b2fb644ecceee15L
