/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * These preferences with everything kept for the song filed as [fileName] moved to [newFileName], or dropped where
 * that is null - every map keyed by a song's file name, so that a new one is added here and nowhere else. A song with
 * nothing kept answers an equal copy, which is a change that writes nothing.
 */
fun UserPreferences.withSongRenamed(fileName: String, newFileName: String?): UserPreferences = copy(
    transpositions = transpositions.movedTo(fileName, newFileName),
    tempos = tempos.movedTo(fileName, newFileName),
    capos = capos.movedTo(fileName, newFileName),
    autoScrollSpeeds = autoScrollSpeeds.movedTo(fileName, newFileName),
    beatLevels = beatLevels.movedTo(fileName, newFileName),
    foldedSections = foldedSections.movedTo(fileName, newFileName),
)

/** These preferences with nothing kept for any song, for a library that has been emptied. */
fun UserPreferences.withoutSongOverrides(): UserPreferences = copy(
    transpositions = emptyMap(),
    tempos = emptyMap(),
    capos = emptyMap(),
    autoScrollSpeeds = emptyMap(),
    beatLevels = emptyMap(),
    foldedSections = emptyMap(),
)

/** The map with the entry of [fileName] put under [newFileName], or dropped where that is null. */
private fun <T> Map<String, T>.movedTo(fileName: String, newFileName: String?): Map<String, T> {
    val value = this[fileName] ?: return this
    return if (newFileName == null) this - fileName else this - fileName + (newFileName to value)
}
